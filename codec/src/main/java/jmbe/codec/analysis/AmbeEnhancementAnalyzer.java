/*
 * ******************************************************************************
 * Copyright (C) 2015-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * *****************************************************************************
 */

package jmbe.codec.analysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Compares your decoder's spectral amplitudes against a DVSI chip's decoded audio
 * and fits enhancement parameters to the chip's behavior.
 * Requires SpectralAmplitudeEnhancer.java (same directory).
 *
 * USAGE
 *   javac SpectralAmplitudeEnhancer.java AmbeEnhancementAnalyzer.java
 *   java AmbeEnhancementAnalyzer <dir> [--delay N] [--fit-frames N] [--csv out.csv]
 *
 * INPUT (per call, in <dir>)
 *   <call>.params.txt  one line per 20 ms frame, whitespace separated:
 *       frameIdx w0 L fecErrors m1..mL v1..vL
 *     frameIdx : 0-based frame number from the start of the call
 *     w0       : radians/sample (2*PI*f0/8000)
 *     L        : number of harmonics
 *     fecErrors: corrected bit errors for the frame (0 = clean)
 *     m1..mL   : UNENHANCED decoded linear amplitudes
 *     v1..vL   : 1 if that harmonic is voiced, else 0
 *     Lines starting with # are ignored.
 *   <call>.pcm         chip output, raw 16-bit little-endian mono, 8 kHz
 *
 * OUTPUT
 *   delay estimate, implied-gain statistics, baseline errors, fitted parameters
 *   with train/held-out RMS log-amplitude error (dB), and optionally a per-harmonic CSV
 *   (implied gain plus features) for fitting a learned model elsewhere.
 */
public final class AmbeEnhancementAnalyzer
{
    static final int FS = 8000;
    static final int FRAME = 160;
    static final int NFFT = 2048;
    static final double DF = (double) FS / NFFT;
    static final double MAX_HZ = 3800.0;

    static final class Frame {
        String call;
        int callIdx;
        int idx;
        double w0;
        int L;
        int errs;
        double[] m;
        boolean[] v;
        boolean clean;
        double[] target;   // chip harmonic amplitudes, level-normalized to m (null if unusable)
        boolean[] used;    // harmonics included in comparisons

        double f0Hz() { return w0 * FS / (2 * Math.PI); }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AmbeEnhancementAnalyzer <dir> [--delay N] [--fit-frames N] [--csv out.csv]");
            return;
        }
        Path dir = Paths.get(args[0]);
        Integer fixedDelay = null;
        int fitFrames = 2000;
        String csvPath = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--delay": fixedDelay = Integer.parseInt(args[++i]); break;
                case "--fit-frames": fitFrames = Integer.parseInt(args[++i]); break;
                case "--csv": csvPath = args[++i]; break;
                default: System.err.println("ignoring " + args[i]);
            }
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.params.txt")) {
            for (Path p : ds) files.add(p);
        }
        Collections.sort(files);

        List<Frame> all = new ArrayList<>();
        double[] re = new double[NFFT], im = new double[NFFT];
        int callIdx = 0;
        for (Path pf : files) {
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcmPath = dir.resolve(call + ".pcm");
            if (!Files.exists(pcmPath)) {
                System.err.println("skip " + call + ": no " + call + ".pcm");
                continue;
            }
            List<Frame> frames = loadParams(pf, call, callIdx);
            markClean(frames);
            double[] pcm = readPcm(pcmPath);
            int delay = fixedDelay != null ? fixedDelay : estimateDelay(frames, pcm);
            int ok = 0, clean = 0;
            for (Frame f : frames) {
                if (!f.clean) continue;
                clean++;
                if (extractTargets(f, pcm, delay, re, im)) ok++;
            }
            System.out.printf("%s: %d frames, %d clean, %d usable, delay %d samples%n",
                    call, frames.size(), clean, ok, delay);
            all.addAll(frames);
            callIdx++;
        }
        final int nCalls = callIdx;

        List<Frame> usable = new ArrayList<>();
        for (Frame f : all) if (f.target != null) usable.add(f);
        if (usable.isEmpty()) {
            System.err.println("no usable frames (check delay, clean-frame filter, file formats)");
            return;
        }

        // ---- step 5: implied gain statistics
        printGainStats(usable);

        // ---- train / held-out split
        List<Frame> train = new ArrayList<>(), test = new ArrayList<>();
        for (Frame f : usable) {
            boolean isTest = nCalls >= 4 ? (f.callIdx % 4 == 3) : (f.idx % 4 == 3);
            (isTest ? test : train).add(f);
        }
        if (nCalls < 4) {
            System.out.println("\nWARNING: fewer than 4 calls, so the held-out set is every 4th frame of the "
                    + "same calls. Adjacent frames are correlated; treat test error as optimistic.");
        }
        System.out.printf("%ntrain frames %d, held-out frames %d%n", train.size(), test.size());

        // ---- baselines
        System.out.println("\nRMS log-amplitude error vs chip (dB, voiced harmonics, per-frame level matched)");
        System.out.println("model                     train      test     bias(test)");
        report("no enhancement", train, test, fr -> fr.m);
        report("IMBE-style enhance()", train, test,
                fr -> SpectralAmplitudeEnhancer.enhance(fr.m, fr.w0));

        // ---- step 6: fit higher-order postfilter parameters
        List<Frame> fitSet = subsample(train, fitFrames);
        System.out.printf("%nfitting enhanceHighOrder on %d train frames...%n", fitSet.size());
        System.out.println("order  gammaN  gammaD  strength   train      test     bias(test)");
        for (int order : new int[]{2, 4, 6, 8}) {
            Function<double[], Double> cost = x -> {
                if (x[0] < 0.05 || x[1] > 0.98 || x[0] >= x[1] - 0.02 || x[2] < 0 || x[2] > 1.5) return 1e3;
                return evaluate(fitSet, fr -> SpectralAmplitudeEnhancer
                        .enhanceHighOrder(fr.m, fr.w0, order, x[0], x[1], x[2]))[0];
            };
            double[] b = nelderMead(cost, new double[]{0.6, 0.8, 1.0}, 0.1, 80);
            double[] tr = evaluate(train, fr -> SpectralAmplitudeEnhancer
                    .enhanceHighOrder(fr.m, fr.w0, order, b[0], b[1], b[2]));
            double[] te = evaluate(test, fr -> SpectralAmplitudeEnhancer
                    .enhanceHighOrder(fr.m, fr.w0, order, b[0], b[1], b[2]));
            System.out.printf("%5d  %6.3f  %6.3f  %8.3f  %8.3f  %8.3f  %8.3f%n",
                    order, b[0], b[1], b[2], tr[0], te[0], te[1]);
        }

        if (csvPath != null) {
            writeCsv(csvPath, usable);
            System.out.println("\nwrote per-harmonic implied gains to " + csvPath);
        }
    }

    // --------------------------------------------------------------- loading

    static List<Frame> loadParams(Path p, String call, int callIdx) throws IOException {
        List<Frame> out = new ArrayList<>();
        for (String line : Files.readAllLines(p)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            if (t.length < 4) continue;
            Frame f = new Frame();
            f.call = call;
            f.callIdx = callIdx;
            f.idx = Integer.parseInt(t[0]);
            f.w0 = Double.parseDouble(t[1]);
            f.L = Integer.parseInt(t[2]);
            f.errs = Integer.parseInt(t[3]);
            if (f.L < 1 || t.length < 4 + 2 * f.L || f.w0 <= 0) continue;
            f.m = new double[f.L];
            f.v = new boolean[f.L];
            for (int i = 0; i < f.L; i++) {
                f.m[i] = Double.parseDouble(t[4 + i]);
                f.v[i] = t[4 + f.L + i].equals("1");
            }
            out.add(f);
        }
        out.sort((a, b) -> Integer.compare(a.idx, b.idx));
        return out;
    }

    /** A frame is clean if it and both neighbors are consecutive, error-free and non-silent. */
    static void markClean(List<Frame> fr) {
        int n = fr.size();
        boolean[] ok = new boolean[n];
        for (int i = 0; i < n; i++) {
            double e = 0;
            for (double a : fr.get(i).m) e += a * a;
            ok[i] = fr.get(i).errs == 0 && e > 1e-9;
        }
        for (int i = 0; i < n; i++) {
            boolean c = ok[i];
            if (c) {
                c = i > 0 && i < n - 1
                        && ok[i - 1] && ok[i + 1]
                        && fr.get(i - 1).idx == fr.get(i).idx - 1
                        && fr.get(i + 1).idx == fr.get(i).idx + 1;
            }
            fr.get(i).clean = c;
        }
    }

    static double[] readPcm(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        short[] s = new short[b.length / 2];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        double[] d = new double[s.length];
        for (int i = 0; i < s.length; i++) d[i] = s[i];
        return d;
    }

    // ------------------------------------------------- step 3 helper: delay

    /** Finds the sample offset that best aligns frame energies with the chip PCM. */
    static int estimateDelay(List<Frame> frames, double[] pcm) {
        List<Frame> cl = new ArrayList<>();
        for (Frame f : frames) if (f.clean) cl.add(f);
        int stride = Math.max(1, cl.size() / 1500);
        List<Frame> sub = new ArrayList<>();
        for (int i = 0; i < cl.size(); i += stride) sub.add(cl.get(i));

        double best = -2;
        int bestD = 0;
        for (int d = -320; d <= 960; d += 4) {
            double c = energyCorr(sub, pcm, d);
            if (c > best) { best = c; bestD = d; }
        }
        int coarse = bestD;
        for (int d = coarse - 3; d <= coarse + 3; d++) {
            double c = energyCorr(sub, pcm, d);
            if (c > best) { best = c; bestD = d; }
        }
        return bestD;
    }

    static double energyCorr(List<Frame> frames, double[] pcm, int d) {
        double n = 0, sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (Frame f : frames) {
            int s = f.idx * FRAME + d;
            if (s < 0 || s + FRAME > pcm.length) continue;
            double ep = 0;
            for (double a : f.m) ep += a * a;
            double ec = 0;
            for (int i = 0; i < FRAME; i++) ec += pcm[s + i] * pcm[s + i];
            double x = Math.log(ep + 1e-9), y = Math.log(ec / FRAME + 1e-9);
            n++; sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y;
        }
        if (n < 20) return -2;
        double cov = sxy / n - (sx / n) * (sy / n);
        double vx = sxx / n - (sx / n) * (sx / n), vy = syy / n - (sy / n) * (sy / n);
        return cov / Math.sqrt(vx * vy + 1e-12);
    }

    // ------------------------------------------------ step 4: target extraction

    /**
     * Reads the chip's harmonic magnitudes at w0*l from an STFT centered on the frame
     * (window about 4 pitch periods), then scales so the voiced-harmonic energy equals
     * the decoder's own. Returns false if the frame can't be used.
     */
    static boolean extractTargets(Frame f, double[] pcm, int delay, double[] re, double[] im) {
        double period = 2 * Math.PI / f.w0;
        int n = (int) Math.max(240, Math.min(640, Math.round(4 * period)));
        int start = f.idx * FRAME + delay + FRAME / 2 - n / 2;
        if (start < 0 || start + n > pcm.length) return false;

        Arrays.fill(re, 0);
        Arrays.fill(im, 0);
        double sw = 0;
        for (int i = 0; i < n; i++) {
            double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            re[i] = pcm[start + i] * w;
            sw += w;
        }
        fft(re, im);

        double f0 = f.f0Hz(), hw = 0.3 * f0;
        double[] t = new double[f.L];
        boolean[] used = new boolean[f.L];
        double em = 0, et = 0;
        int cnt = 0;
        for (int l = 1; l <= f.L; l++) {
            double fc = l * f0;
            if (fc > MAX_HZ) break;
            int k0 = Math.max(1, (int) Math.round((fc - hw) / DF));
            int k1 = Math.min(NFFT / 2 - 1, (int) Math.round((fc + hw) / DF));
            double pk = 0;
            for (int k = k0; k <= k1; k++) pk = Math.max(pk, Math.hypot(re[k], im[k]));
            t[l - 1] = 2 * pk / sw;
            used[l - 1] = f.v[l - 1] && f.m[l - 1] > 1e-9 && t[l - 1] > 1e-9;
            if (used[l - 1]) {
                em += f.m[l - 1] * f.m[l - 1];
                et += t[l - 1] * t[l - 1];
                cnt++;
            }
        }
        if (cnt < 4 || et <= 0) return false;
        double s = Math.sqrt(em / et);
        for (int i = 0; i < t.length; i++) t[i] *= s;
        f.target = t;
        f.used = used;
        return true;
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, b = i + j + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }

    // ------------------------------------------- step 5: implied gain statistics

    static double db(double x) { return 20 * Math.log10(Math.max(x, 1e-12)); }

    static void printGainStats(List<Frame> frames) {
        final int HB = 18;                       // 1 dB bins from -10 to +8 dB
        long[] hist = new long[HB];
        double[] ps = new double[10], ps2 = new double[10];
        long[] pn = new long[10];
        int[] lEdges = {14, 24, 34, 44, 1000};
        double[] ls = new double[5], ls2 = new double[5];
        long[] ln = new long[5];
        double[] fEdges = {100, 150, 200, 300, 1e9};
        double[] fs = new double[5], fs2 = new double[5];
        long[] fn = new long[5];
        long total = 0;

        for (Frame f : frames) {
            int lb = 0;
            while (f.L > lEdges[lb]) lb++;
            int fb = 0;
            while (f.f0Hz() >= fEdges[fb]) fb++;
            for (int i = 0; i < f.L; i++) {
                if (!f.used[i]) continue;
                double g = db(f.target[i] / f.m[i]);
                int hb = Math.max(0, Math.min(HB - 1, (int) Math.floor(g) + 10));
                hist[hb]++;
                int pb = Math.min(9, (int) (10.0 * (i + 1) / (f.L + 1)));
                ps[pb] += g; ps2[pb] += g * g; pn[pb]++;
                ls[lb] += g; ls2[lb] += g * g; ln[lb]++;
                fs[fb] += g; fs2[fb] += g * g; fn[fb]++;
                total++;
            }
        }

        System.out.printf("%nImplied chip gain g = chip / decoder amplitude (dB), %d voiced harmonics%n", total);
        System.out.println("(IMBE clamps ~ +1.6 dB and -6.0 dB; per-frame energy matching blurs them slightly)");
        System.out.println("histogram:");
        for (int i = 0; i < HB; i++) {
            String label = (i == 0 ? "<" : i == HB - 1 ? ">=" : "") + (i - 10 + (i == 0 ? 1 : 0)) + " dB";
            int bar = (int) Math.round(60.0 * hist[i] / Math.max(1, total));
            System.out.printf("  %8s %5.1f%% %s%n", label, 100.0 * hist[i] / Math.max(1, total), "#".repeat(bar));
        }
        System.out.println("by relative harmonic position l/L (mean +/- std dB):");
        for (int i = 0; i < 10; i++) printRow(String.format("  %d0-%d0%%", i, i + 1), ps[i], ps2[i], pn[i]);
        System.out.println("by harmonic count L:");
        String[] lLab = {"L<=14", "15-24", "25-34", "35-44", "45+"};
        for (int i = 0; i < 5; i++) printRow("  " + lLab[i], ls[i], ls2[i], ln[i]);
        System.out.println("by fundamental f0:");
        String[] fLab = {"<100Hz", "100-150", "150-200", "200-300", "300Hz+"};
        for (int i = 0; i < 5; i++) printRow("  " + fLab[i], fs[i], fs2[i], fn[i]);
    }

    static void printRow(String label, double s, double s2, long n) {
        if (n == 0) { System.out.printf("%-10s      (none)%n", label); return; }
        double mean = s / n, sd = Math.sqrt(Math.max(0, s2 / n - mean * mean));
        System.out.printf("%-10s %+6.2f +/- %4.2f   n=%d%n", label, mean, sd, n);
    }

    // ------------------------------------------------------ step 6: evaluation

    /** Returns {rmsDb, biasDb, count}. Candidate is level-matched per frame on the used harmonics. */
    static double[] evaluate(List<Frame> frames, Function<Frame, double[]> enh) {
        double sse = 0, bias = 0;
        long n = 0;
        for (Frame f : frames) {
            double[] c = enh.apply(f);
            double ec = 0, et = 0;
            for (int i = 0; i < f.L; i++) {
                if (!f.used[i]) continue;
                ec += c[i] * c[i];
                et += f.target[i] * f.target[i];
            }
            if (ec <= 0) continue;
            double s = Math.sqrt(et / ec);
            for (int i = 0; i < f.L; i++) {
                if (!f.used[i]) continue;
                double d = db(c[i] * s) - db(f.target[i]);
                sse += d * d;
                bias += d;
                n++;
            }
        }
        if (n == 0) return new double[]{Double.NaN, Double.NaN, 0};
        return new double[]{Math.sqrt(sse / n), bias / n, n};
    }

    static void report(String name, List<Frame> train, List<Frame> test, Function<Frame, double[]> enh) {
        double[] a = evaluate(train, enh), b = evaluate(test, enh);
        System.out.printf("%-24s %8.3f  %8.3f  %8.3f%n", name, a[0], b[0], b[1]);
    }

    static List<Frame> subsample(List<Frame> in, int max) {
        if (in.size() <= max) return in;
        List<Frame> out = new ArrayList<>();
        double step = (double) in.size() / max;
        for (int i = 0; i < max; i++) out.add(in.get((int) (i * step)));
        return out;
    }

    // ------------------------------------------------------------ optimizer

    static double[] nelderMead(Function<double[], Double> fn, double[] x0, double step, int maxIter) {
        int n = x0.length;
        double[][] p = new double[n + 1][];
        double[] fv = new double[n + 1];
        p[0] = x0.clone();
        for (int i = 0; i < n; i++) {
            p[i + 1] = x0.clone();
            p[i + 1][i] += step;
        }
        for (int i = 0; i <= n; i++) fv[i] = fn.apply(p[i]);

        for (int it = 0; it < maxIter; it++) {
            // sort ascending by value
            for (int i = 1; i <= n; i++) {
                double[] px = p[i];
                double pf = fv[i];
                int j = i - 1;
                while (j >= 0 && fv[j] > pf) { p[j + 1] = p[j]; fv[j + 1] = fv[j]; j--; }
                p[j + 1] = px;
                fv[j + 1] = pf;
            }
            double[] c = new double[n];
            for (int i = 0; i < n; i++) for (int d = 0; d < n; d++) c[d] += p[i][d] / n;

            double[] xr = along(c, p[n], -1.0);
            double fr = fn.apply(xr);
            if (fr < fv[0]) {
                double[] xe = along(c, p[n], -2.0);
                double fe = fn.apply(xe);
                if (fe < fr) { p[n] = xe; fv[n] = fe; } else { p[n] = xr; fv[n] = fr; }
            } else if (fr < fv[n - 1]) {
                p[n] = xr; fv[n] = fr;
            } else {
                double[] xc = fr < fv[n] ? along(c, xr, 0.5) : along(c, p[n], 0.5);
                double fc = fn.apply(xc);
                if (fc < Math.min(fv[n], fr)) {
                    p[n] = xc; fv[n] = fc;
                } else {
                    for (int i = 1; i <= n; i++) {
                        for (int d = 0; d < n; d++) p[i][d] = p[0][d] + 0.5 * (p[i][d] - p[0][d]);
                        fv[i] = fn.apply(p[i]);
                    }
                }
            }
        }
        int best = 0;
        for (int i = 1; i <= n; i++) if (fv[i] < fv[best]) best = i;
        return p[best];
    }

    /** Returns c + t * (x - c). */
    static double[] along(double[] c, double[] x, double t) {
        double[] r = new double[c.length];
        for (int i = 0; i < c.length; i++) r[i] = c[i] + t * (x[i] - c[i]);
        return r;
    }

    // ----------------------------------------------------------------- export

    static void writeCsv(String path, List<Frame> frames) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Paths.get(path)))) {
            w.println("call,frame,l,L,f0Hz,pos,mDb,mPrevDb,mNextDb,gDb");
            for (Frame f : frames) {
                for (int i = 0; i < f.L; i++) {
                    if (!f.used[i]) continue;
                    double prev = f.m[Math.max(0, i - 1)], next = f.m[Math.min(f.L - 1, i + 1)];
                    w.printf("%s,%d,%d,%d,%.2f,%.4f,%.3f,%.3f,%.3f,%.3f%n",
                            f.call, f.idx, i + 1, f.L, f.f0Hz(), (i + 1.0) / f.L,
                            db(f.m[i]), db(prev), db(next), db(f.target[i] / f.m[i]));
                }
            }
        }
    }
}