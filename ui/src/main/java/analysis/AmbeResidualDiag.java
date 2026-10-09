package analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Residual diagnostic: where does the learned model still disagree with the DVSI chip, and what
 * does that disagreement look like?
 *
 * Residual = (synthesized-and-remeasured amplitude) - (chip amplitude), in dB per voiced harmonic
 * that passed the chip SNR gate, per-frame level matched: exactly the audio-domain error of
 * AmbeAudioCompare, kept per harmonic. It is then
 *
 *  1. broken down by conditions (chip SNR, frame level, position in a voiced run, level and pitch
 *     change, neighboring FEC errors, local spectral contrast, frequency, harmonic number, f0,
 *     distance to an unvoiced harmonic, the gain the model applied), so a concentrated error shows
 *     up as a bin well above the overall RMS;
 *  2. tested for structure: correlation between neighboring harmonics (smooth envelope mismatch
 *     vs independent per-harmonic scatter), correlation across consecutive frames (persistent
 *     chip behavior vs frame-to-frame noise), and how much of each frame's residual a straight
 *     line or a parabola across the harmonics explains;
 *  3. regressed on the chip's measurement noise: squared residual against (noise floor /
 *     amplitude)^2. The intercept is what would remain with a perfect measurement.
 *
 * Usage:
 *   java AmbeResidualDiag <dir> --model gain-model.txt [--delay 80] [--calls test|all]
 *        [--max-calls N] [--variant learned|imbe] [--csv residuals.csv] [--pcm-endian big|little]
 *        [extraction options such as --min-snr-db]
 */
public final class AmbeResidualDiag {

    // ------------------------------------------------------------ covariate definitions

    static final String[] NAME = {
        "chip harmonic SNR (dB above the noise floor)",
        "frame level re the call's loud frames (dB)",
        "frames from the nearest end of a voiced run",
        "frame level change vs neighbors (dB)",
        "pitch change vs neighbors (%)",
        "FEC errors in frames t-2..t+2",
        "local contrast (dB re mean of +/-2 neighboring harmonics)",
        "harmonic level re frame mean (dB)",
        "harmonic frequency (Hz)",
        "harmonic number l",
        "fundamental f0 (Hz)",
        "harmonics to the nearest unvoiced harmonic",
        "gain applied by the variant (dB)"
    };
    static final double INF = 1e9;
    static final double[][] EDGES = {
        {-INF, 20, 25, 30, 35, INF},
        {-INF, -24, -18, -12, -6, INF},
        {0, 1, 2, 3, 5, INF},
        {0, 1, 2, 4, 8, INF},
        {0, 1, 2, 4, 8, INF},
        {0, 1, 2, 4, INF},
        {-INF, -6, -3, 0, 3, 6, INF},
        {-INF, -15, -9, -3, 3, INF},
        {0, 500, 1000, 1500, 2000, 2500, 3000, INF},
        {1, 2, 3, 4, 6, 10, 20, INF},
        {0, 100, 125, 150, 175, 200, INF},
        {1, 2, 3, 5, INF},
        {-INF, -6, -3, -1, 1, 3, INF}
    };
    static final int NC = NAME.length;

    static final class Col {
        double[] a = new double[1 << 16];
        int n;

        void add(double v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }
    }

    // ----------------------------------------------------------------- accumulators

    static final Col R = new Col();
    static final Col[] C = new Col[NC];
    static {
        for (int i = 0; i < NC; i++) C[i] = new Col();
    }

    static final double[] hSxy = new double[4], hSxx = new double[4], hSyy = new double[4];   // harmonic lags 1..3
    static final double[] tSxy = new double[3], tSxx = new double[3], tSyy = new double[3];   // temporal lags 1..2
    static double ssTot, ssLin, ssQuad;
    static long trendFrames;

    static final class CallStat {
        String name;
        double sse;
        long n;
        int frames;
        double snrSum, errSum;
    }
    static final List<CallStat> CALLS = new ArrayList<>();

    // ------------------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AmbeResidualDiag <dir> --model gain-model.txt [--delay N] [--calls test|all]"
                    + " [--max-calls N] [--variant learned|imbe] [--csv out.csv] [--pcm-endian big|little]");
            return;
        }
        Path dir = Paths.get(args[0]);
        String modelPath = "gain-model.txt";
        Integer fixedDelay = null;
        boolean testOnly = true, imbe = false;
        int maxCalls = Integer.MAX_VALUE;
        Path csv = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--model": modelPath = args[++i]; break;
                case "--delay": fixedDelay = Integer.parseInt(args[++i]); break;
                case "--calls": testOnly = !args[++i].equalsIgnoreCase("all"); break;
                case "--max-calls": maxCalls = Integer.parseInt(args[++i]); break;
                case "--variant": imbe = args[++i].equalsIgnoreCase("imbe"); break;
                case "--csv": csv = Paths.get(args[++i]); break;
                default:
                    if (i + 1 < args.length && AmbeEnhancementAnalyzer.applyExtractOption(args[i], args[i + 1])) i++;
                    else System.err.println("ignoring " + args[i]);
            }
        }
        final LearnedEnhancer.Mlp net = imbe ? null : LearnedEnhancer.Mlp.load(Paths.get(modelPath));

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.params.txt")) {
            for (Path p : ds) files.add(p);
        }
        Collections.sort(files);

        BufferedWriter w = null;
        if (csv != null) {
            w = Files.newBufferedWriter(csv);
            w.write("call,frame,harmonic,residual_db");
            for (String n : NAME) w.write("," + n.split(" \\(")[0].replace(',', ' '));
            w.newLine();
        }

        int callIdx = 0, nCalls = 0;
        for (Path pf : files) {
            if (nCalls >= maxCalls) break;
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcmPath = dir.resolve(call + ".pcm");
            if (!Files.exists(pcmPath)) continue;
            int myIdx = callIdx++;
            if (testOnly && myIdx % 5 != 4) continue;
            List<AmbeEnhancementAnalyzer.Frame> frames = AmbeEnhancementAnalyzer.loadParams(pf, call, myIdx);
            if (frames.size() < 10) continue;
            AmbeEnhancementAnalyzer.markClean(frames);
            double[] chip = AmbeEnhancementAnalyzer.readPcm(pcmPath);
            int delay = fixedDelay != null ? fixedDelay : AmbeEnhancementAnalyzer.estimateDelay(frames, chip);
            AmbeEnhancementAnalyzer.extractAll(frames, chip, delay);
            if (processCall(call, frames, chip, delay, net, w)) nCalls++;
            if (nCalls % 10 == 0 && nCalls > 0) System.err.printf("  %d calls done%n", nCalls);
        }
        if (w != null) w.close();
        if (nCalls == 0 || R.n == 0) { System.err.println("no usable calls (try --calls all)"); return; }
        report(nCalls, imbe ? "IMBE-style" : "learned MLP", testOnly);
    }

    // ---------------------------------------------------------------- per-call work

    static boolean voicedFrame(AmbeEnhancementAnalyzer.Frame f) {
        int c = 0;
        for (int k = 0; k < f.L; k++) if (f.v[k]) c++;
        return f.L > 0 && c * 2 >= f.L;
    }

    static boolean consec(List<AmbeEnhancementAnalyzer.Frame> fr, int i, int j) {
        return i >= 0 && j >= 0 && i < fr.size() && j < fr.size() && fr.get(j).idx - fr.get(i).idx == j - i;
    }

    static boolean processCall(String call, List<AmbeEnhancementAnalyzer.Frame> frames, double[] chip, int delay,
                               LearnedEnhancer.Mlp net, BufferedWriter csv) throws IOException {
        final int n = frames.size();
        boolean any = false;
        for (AmbeEnhancementAnalyzer.Frame f : frames) if (f.target != null) { any = true; break; }
        if (!any) return false;

        // enhanced amplitudes for every frame, then the synthesized audio
        double[][] em = new double[n][];
        for (int i = 0; i < n; i++) {
            AmbeEnhancementAnalyzer.Frame f = frames.get(i);
            if (net == null) {
                em[i] = SpectralAmplitudeEnhancer.enhance(f.m, f.w0);
            } else {
                double[][] nm = new double[4][];
                double[] nw = new double[4];
                int[] off = {-2, -1, 1, 2};
                for (int k = 0; k < 4; k++) {
                    int j = i + off[k];
                    if (!consec(frames, i, j)) continue;
                    nm[k] = frames.get(j).m;
                    nw[k] = frames.get(j).w0;
                }
                em[i] = LearnedEnhancer.enhance(net, f.m, f.v, f.w0, nm, nw);
            }
        }
        List<AmbeEnhancementAnalyzer.Frame> g = new ArrayList<>(n);
        for (int i = 0; i < n; i++) g.add(AmbeAudioCompare.withAmplitudes(frames.get(i), em[i]));
        double[] synth = AmbeEnhancementAnalyzer.synthesize(g);

        // per-frame context: level, voiced-run position
        double[] en = new double[n];
        List<Double> loud = new ArrayList<>();
        boolean[] vf = new boolean[n];
        for (int i = 0; i < n; i++) {
            AmbeEnhancementAnalyzer.Frame f = frames.get(i);
            double e = 0;
            for (double x : f.m) e += x * x;
            en[i] = 10 * Math.log10(e + 1e-9);
            vf[i] = voicedFrame(f);
            if (vf[i]) loud.add(en[i]);
        }
        if (loud.isEmpty()) return false;
        Collections.sort(loud);
        double p90 = loud.get((int) Math.min(loud.size() - 1, Math.round(0.9 * (loud.size() - 1))));
        int[] dPrev = new int[n], dNext = new int[n];
        for (int i = 0; i < n; i++) dPrev[i] = vf[i] && consec(frames, i - 1, i) && vf[i - 1] ? dPrev[i - 1] + 1 : 0;
        for (int i = n - 1; i >= 0; i--) dNext[i] = vf[i] && consec(frames, i, i + 1) && vf[i + 1] ? dNext[i + 1] + 1 : 0;

        double[][] res = new double[n][];
        CallStat cs = new CallStat();
        cs.name = call;
        for (int i = 0; i < n; i++) {
            AmbeEnhancementAnalyzer.Frame f = frames.get(i);
            if (f.target == null) continue;
            double[] t = AmbeAudioCompare.measure(f, synth);
            if (t == null) continue;
            double ec = 0, et = 0;
            for (int k = 0; k < f.L; k++) if (f.used[k]) { ec += t[k] * t[k]; et += f.target[k] * f.target[k]; }
            if (ec <= 0) continue;
            double s = Math.sqrt(et / ec);

            double[][] b = AmbeEnhancementAnalyzer.STFT_BUF.get();
            double sw = AmbeEnhancementAnalyzer.spectrum(f, chip, delay, b[0], b[1]);
            if (sw <= 0) continue;
            double[] tr = AmbeEnhancementAnalyzer.stftPeaks(f, b[0], b[1], sw);
            double[] fl = AmbeEnhancementAnalyzer.floors(f, b[0], b[1], sw);

            // frame-level covariates
            double tch = 0, pch = 0, fec = 0;
            for (int d = -2; d <= 2; d++) {
                if (d == 0 || !consec(frames, i, i + d)) continue;
                fec += frames.get(i + d).errs;
                if (Math.abs(d) == 1) {
                    tch = Math.max(tch, Math.abs(en[i] - en[i + d]));
                    pch = Math.max(pch, 100.0 * Math.abs(Math.log(frames.get(i + d).w0 / f.w0)));
                }
            }
            double lvl = en[i] - p90;
            int run = Math.min(9, Math.min(dPrev[i], dNext[i]));
            double[] relm = new double[f.L];
            double mean = 0;
            for (int k = 0; k < f.L; k++) { relm[k] = AmbeEnhancementAnalyzer.db(f.m[k]); mean += relm[k]; }
            mean /= f.L;
            for (int k = 0; k < f.L; k++) relm[k] -= mean;

            res[i] = new double[f.L];
            Arrays.fill(res[i], Double.NaN);
            for (int k = 0; k < f.L; k++) {
                if (!f.used[k]) continue;
                double r = AmbeEnhancementAnalyzer.db(t[k] * s) - AmbeEnhancementAnalyzer.db(f.target[k]);
                res[i][k] = r;
                double snr = AmbeEnhancementAnalyzer.db(tr[k] / fl[k]);
                double con = 0;
                int cc = 0;
                for (int d = -2; d <= 2; d++) {
                    int j = k + d;
                    if (d == 0 || j < 0 || j >= f.L) continue;
                    con += relm[j];
                    cc++;
                }
                con = cc > 0 ? relm[k] - con / cc : 0;
                int uv = 99;
                for (int j = 0; j < f.L; j++) if (!f.v[j]) uv = Math.min(uv, Math.abs(j - k));
                double gain = AmbeEnhancementAnalyzer.db(em[i][k] / f.m[k]);

                double[] cv = {snr, lvl, run, tch, pch, fec, con, relm[k], (k + 1) * f.f0Hz(), k + 1, f.f0Hz(), uv, gain};
                R.add(r);
                for (int c = 0; c < NC; c++) C[c].add(cv[c]);
                cs.sse += r * r;
                cs.n++;
                cs.snrSum += snr;
                if (csv != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(call).append(',').append(f.idx).append(',').append(k + 1).append(',');
                    sb.append(String.format(Locale.ROOT, "%.3f", r));
                    for (double v : cv) sb.append(',').append(String.format(Locale.ROOT, "%.3f", v));
                    csv.write(sb.toString());
                    csv.newLine();
                }
            }
            cs.frames++;
            cs.errSum += fec;
        }
        if (cs.n == 0) return false;
        CALLS.add(cs);
        structure(frames, res);
        return true;
    }

    // ------------------------------------------------------------ structure statistics

    static void structure(List<AmbeEnhancementAnalyzer.Frame> frames, double[][] res) {
        final int n = frames.size();
        for (int i = 0; i < n; i++) {
            double[] r = res[i];
            if (r == null) continue;
            for (int lag = 1; lag <= 3; lag++)
                for (int k = 0; k + lag < r.length; k++) {
                    if (Double.isNaN(r[k]) || Double.isNaN(r[k + lag])) continue;
                    hSxy[lag] += r[k] * r[k + lag];
                    hSxx[lag] += r[k] * r[k];
                    hSyy[lag] += r[k + lag] * r[k + lag];
                }
            for (int lag = 1; lag <= 2; lag++) {
                int j = i + lag;
                if (j >= n || res[j] == null || !consec(frames, i, j)) continue;
                if (Math.abs(Math.log(frames.get(j).w0 / frames.get(i).w0)) > 0.03 * lag) continue;
                int m = Math.min(r.length, res[j].length);
                for (int k = 0; k < m; k++) {
                    if (Double.isNaN(r[k]) || Double.isNaN(res[j][k])) continue;
                    tSxy[lag] += r[k] * res[j][k];
                    tSxx[lag] += r[k] * r[k];
                    tSyy[lag] += res[j][k] * res[j][k];
                }
            }
            // how much of this frame's residual does a line / parabola across the harmonics explain?
            int cnt = 0;
            for (double v : r) if (!Double.isNaN(v)) cnt++;
            if (cnt < 8) continue;
            double[] x = new double[cnt], y = new double[cnt];
            int c = 0;
            double my = 0;
            for (int k = 0; k < r.length; k++) {
                if (Double.isNaN(r[k])) continue;
                x[c] = (k + 1.0) / r.length;
                y[c] = r[k];
                my += y[c++];
            }
            my /= cnt;
            double tot = 0;
            for (double v : y) tot += (v - my) * (v - my);
            double rl = polyResidual(x, y, 1), rq = polyResidual(x, y, 2);
            if (Double.isNaN(rl) || Double.isNaN(rq)) continue;
            ssTot += tot;
            ssLin += rl;
            ssQuad += rq;
            trendFrames++;
        }
    }

    /** Residual sum of squares of a least-squares polynomial fit of the given degree. */
    static double polyResidual(double[] x, double[] y, int deg) {
        int p = deg + 1, n = x.length;
        double[][] a = new double[p][p + 1];
        for (int i = 0; i < n; i++) {
            double[] pw = new double[2 * p];
            pw[0] = 1;
            for (int k = 1; k < 2 * p; k++) pw[k] = pw[k - 1] * x[i];
            for (int r = 0; r < p; r++) {
                for (int c = 0; c < p; c++) a[r][c] += pw[r + c];
                a[r][p] += pw[r] * y[i];
            }
        }
        for (int col = 0; col < p; col++) {
            int piv = col;
            for (int r = col + 1; r < p; r++) if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r;
            if (Math.abs(a[piv][col]) < 1e-12) return Double.NaN;
            double[] tmp = a[col]; a[col] = a[piv]; a[piv] = tmp;
            for (int r = 0; r < p; r++) {
                if (r == col) continue;
                double f = a[r][col] / a[col][col];
                for (int c = col; c <= p; c++) a[r][c] -= f * a[col][c];
            }
        }
        double[] co = new double[p];
        for (int r = 0; r < p; r++) co[r] = a[r][p] / a[r][r];
        double ss = 0;
        for (int i = 0; i < n; i++) {
            double f = 0, pw = 1;
            for (int k = 0; k < p; k++) { f += co[k] * pw; pw *= x[i]; }
            ss += (y[i] - f) * (y[i] - f);
        }
        return ss;
    }

    // ----------------------------------------------------------------------- report

    static String lab(double lo, double hi) {
        if (lo <= -INF / 2) return "< " + f(hi);
        if (hi >= INF / 2) return ">= " + f(lo);
        return f(lo) + " to < " + f(hi);
    }

    static String f(double v) {
        return v == Math.rint(v) ? String.format("%d", (long) v) : String.format("%.1f", v);
    }

    static double corr(double sxy, double sxx, double syy) {
        return sxx > 0 && syy > 0 ? sxy / Math.sqrt(sxx * syy) : Double.NaN;
    }

    static void report(int nCalls, String variant, boolean testOnly) {
        final int n = R.n;
        double sse = 0, sum = 0;
        for (int i = 0; i < n; i++) { sse += R.a[i] * R.a[i]; sum += R.a[i]; }
        final double overall = Math.sqrt(sse / n);
        System.out.printf("%nResidual diagnostic for %s: %d calls (%s), %d scored harmonics%n", variant, nCalls,
                testOnly ? "held-out test split" : "all calls", n);
        System.out.printf("overall audio-domain residual: RMS %.3f dB, bias %+.3f dB%n", overall, sum / n);
        System.out.println("A bin is flagged ^ when its RMS is at least 15% above the overall figure and v when at least 15% below"
                + " (bins with fewer than 500 harmonics are not flagged).");

        List<String> hot = new ArrayList<>();
        for (int c = 0; c < NC; c++) {
            double[] e = EDGES[c];
            int nb = e.length - 1;
            double[] s2 = new double[nb], s1 = new double[nb];
            int[] cnt = new int[nb];
            for (int i = 0; i < n; i++) {
                double v = C[c].a[i];
                int b = 0;
                while (b < nb - 1 && v >= e[b + 1]) b++;
                s2[b] += R.a[i] * R.a[i];
                s1[b] += R.a[i];
                cnt[b]++;
            }
            System.out.printf("%n-- by %s --%n", NAME[c]);
            System.out.printf("   %-16s %8s %7s %8s%n", "range", "n", "rms", "bias");
            for (int b = 0; b < nb; b++) {
                if (cnt[b] == 0) continue;
                double rms = Math.sqrt(s2[b] / cnt[b]);
                String flag = "";
                if (cnt[b] >= 500 && rms >= 1.15 * overall) {
                    flag = "  ^";
                    hot.add(String.format("%.2f x overall (%.2f dB, n=%d): %s = %s", rms / overall, rms, cnt[b], NAME[c].split(" \\(")[0],
                            lab(e[b], e[b + 1])));
                } else if (cnt[b] >= 500 && rms <= 0.85 * overall) {
                    flag = "  v";
                }
                System.out.printf("   %-16s %8d %7.3f %+8.3f%s%n", lab(e[b], e[b + 1]), cnt[b], rms, s1[b] / cnt[b], flag);
            }
        }

        // ---------------------------------------------------------------- structure
        System.out.printf("%n== structure of the residual ==%n");
        double[] hc = new double[4];
        for (int l = 1; l <= 3; l++) hc[l] = corr(hSxy[l], hSxx[l], hSyy[l]);
        System.out.printf("correlation between harmonics l and l+1 / l+2 / l+3 in the same frame: %.3f / %.3f / %.3f%n", hc[1], hc[2], hc[3]);
        double[] tc = new double[3];
        for (int l = 1; l <= 2; l++) tc[l] = corr(tSxy[l], tSxx[l], tSyy[l]);
        System.out.printf("correlation of the same harmonic across frames t and t+1 / t+2 (pitch within 3%%/6%%): %.3f / %.3f%n", tc[1], tc[2]);
        double linFrac = ssTot > 0 ? 1 - ssLin / ssTot : Double.NaN, quadFrac = ssTot > 0 ? 1 - ssQuad / ssTot : Double.NaN;
        System.out.printf("share of each frame's residual variance explained by a straight line across harmonics: %.1f%%, by a parabola: %.1f%%  (%d frames)%n",
                100 * linFrac, 100 * quadFrac, trendFrames);

        // ------------------------------------------------------- noise-floor regression
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double x = Math.pow(10, -C[0].a[i] / 10.0), y = R.a[i] * R.a[i];
            sx += x; sy += y; sxx += x * x; sxy += x * y; syy += y * y;
        }
        double den = n * sxx - sx * sx;
        double slope = den > 0 ? (n * sxy - sx * sy) / den : Double.NaN;
        double icpt = (sy - slope * sx) / n;
        double rr = den > 0 && syy * n - sy * sy > 0 ? Math.pow(n * sxy - sx * sy, 2) / (den * (n * syy - sy * sy)) : Double.NaN;
        System.out.printf("%n== how much is measurement noise? ==%n");
        System.out.printf("regression of squared residual on (noise floor / amplitude)^2: intercept %.2f dB^2 (RMS %.3f dB), slope %.1f, R^2 %.4f%n",
                icpt, Math.sqrt(Math.max(icpt, 0)), slope, rr);
        System.out.printf("  -> at the gate edge (15 dB SNR) the fitted noise term is about %.2f dB RMS; the intercept is what a noise-free measurement would still show.%n",
                Math.sqrt(Math.max(0, slope * Math.pow(10, -1.5))));
        System.out.println("  (the SNR range is narrow because the gate removed everything below 15 dB, so treat this as a rough bound, not a precise split)");
        if (rr < 0.01) System.out.println("  (R^2 is under 0.01: the squared residual barely depends on SNR, so the slope and the gate-edge figure are poorly determined)");

        // ------------------------------------------------------------------ per call
        List<CallStat> byRms = new ArrayList<>(CALLS);
        byRms.sort((a, b) -> Double.compare(Math.sqrt(a.sse / a.n), Math.sqrt(b.sse / b.n)));
        int k = byRms.size();
        System.out.printf("%n== spread across calls ==%n");
        System.out.printf("per-call residual RMS: min %.2f, 10%% %.2f, median %.2f, 90%% %.2f, max %.2f dB%n",
                rmsOf(byRms.get(0)), rmsOf(byRms.get((int) (0.1 * (k - 1)))), rmsOf(byRms.get((k - 1) / 2)),
                rmsOf(byRms.get((int) (0.9 * (k - 1)))), rmsOf(byRms.get(k - 1)));
        System.out.println("worst calls (name, residual RMS, scored frames, mean chip SNR, mean neighbor FEC errors):");
        for (int i = k - 1; i >= Math.max(0, k - 5); i--) {
            CallStat c = byRms.get(i);
            System.out.printf("  %-24s %.2f dB  %5d frames  %.1f dB  %.2f%n", c.name, rmsOf(c), c.frames, c.snrSum / c.n, c.errSum / c.frames);
        }

        // --------------------------------------------------------------- reading
        System.out.printf("%n== reading ==%n");
        if (hot.isEmpty()) System.out.println("no condition stands out: no bin is 15% or more above the overall residual.");
        else {
            System.out.println("conditions with the highest residual:");
            hot.sort((a, b) -> Double.compare(Double.parseDouble(b.substring(0, 4)), Double.parseDouble(a.substring(0, 4))));
            for (int i = 0; i < Math.min(5, hot.size()); i++) System.out.println("  " + hot.get(i));
        }
        if (hc[1] > 0.4) System.out.println("neighboring harmonics err together (correlation " + String.format("%.2f", hc[1])
                + "): the residual is partly a smooth spectral-envelope mismatch, which points at envelope-level processing or dequantization differences.");
        else if (hc[1] < 0.28) System.out.println("neighboring harmonics err independently (correlation " + String.format("%.2f", hc[1])
                + "): the residual is per-harmonic scatter, not a smooth envelope difference.");
        else System.out.println("neighboring harmonics are moderately correlated (" + String.format("%.2f", hc[1]) + "): a mix of envelope-level and per-harmonic error.");
        if (tc[1] > 0.4) System.out.println("the same harmonic errs the same way in consecutive frames (correlation " + String.format("%.2f", tc[1])
                + "): systematic behavior that persists in time, so some of it should be learnable with the right input.");
        else if (tc[1] < 0.28) System.out.println("errors do not persist from frame to frame (correlation " + String.format("%.2f", tc[1])
                + "): frame-to-frame randomness, which the available inputs are unlikely to predict.");
        else System.out.println("errors persist moderately across frames (" + String.format("%.2f", tc[1]) + ").");
        System.out.println("(calibration: purely independent errors still read about 0.15-0.20 in both correlations, because the STFT window blends"
                + " neighboring harmonics and frames; smooth envelope errors read above 0.9 across harmonics and persistent ones above 0.8 across frames.)");
        if (Math.sqrt(Math.max(icpt, 0)) >= 0.85 * overall)
            System.out.println("the noise regression leaves almost all of the residual at infinite SNR, so measurement noise is not the main cause.");
        else
            System.out.printf("the noise regression attributes a noticeable part of the residual to measurement noise: the intercept is %.2f dB against %.2f dB overall.%n",
                    Math.sqrt(Math.max(icpt, 0)), overall);
    }

    static double rmsOf(CallStat c) { return Math.sqrt(c.sse / c.n); }
}
