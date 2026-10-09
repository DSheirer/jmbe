package analysis;

import java.nio.file.*;
import java.util.*;

/**
 * Tests whether the chip's spectral-amplitude decode differs from the JMBE classic decode, using only the
 * params files and chip PCM (no bitstreams).
 *
 * For every scored voiced harmonic the audio-domain residual d = chip - JMBE(unenhanced), in log2 units
 * (1 log2 unit = 6.02 dB), is regressed (per-frame demeaned, so frame gain/level drops out) on:
 *   P    prediction term: previous frame's log amplitudes interpolated to this frame's harmonics (as JMBE does)
 *   Tlo  smooth part of the decoded innovation T = logA - 0.65 P (moving average +/-3 harmonics: PRBA-like)
 *   Thi  fine part of T (T - Tlo: HOC-like detail)
 *   dI   P(spec interpolation) - P(JMBE code interpolation); nonzero only when L changes
 * If the chip decodes exactly like JMBE every coefficient is 0 and R^2 is 0.
 *   coef(P)  = chip rho - 0.65        coef(Thi) = chip HOC scale - 1        coef(Tlo) = chip PRBA scale - 1
 *   coef(dI) = 0 if the chip matches the JMBE code, rho if it follows the spec formula
 * usage: java AmbeDecodeRegress <dir> [--delay 80] [--calls all|test] [--max-calls N] [--pcm-endian big|little]
 */
public final class AmbeDecodeRegress {
    static final double LOG2DB = 6.0206;
    static final double RHO = 0.65;
    static final int NR = 4;
    static final String[] RN = {"P (prediction)", "Tlo (smooth innovation)", "Thi (fine innovation)", "dI (spec interp)"};

    static final class Ls {
        final double[][] xtx = new double[NR][NR];
        final double[] xty = new double[NR];
        double yty, n;
        void add(double[] x, double y) {
            for (int i = 0; i < NR; i++) { xty[i] += x[i] * y; for (int j = 0; j < NR; j++) xtx[i][j] += x[i] * x[j]; }
            yty += y * y; n++;
        }
        void merge(Ls o) {
            for (int i = 0; i < NR; i++) { xty[i] += o.xty[i]; for (int j = 0; j < NR; j++) xtx[i][j] += o.xtx[i][j]; }
            yty += o.yty; n += o.n;
        }
        double[][] inv() {
            double[][] a = new double[NR][2 * NR];
            for (int i = 0; i < NR; i++) { for (int j = 0; j < NR; j++) a[i][j] = xtx[i][j]; a[i][NR + i] = 1; }
            for (int c = 0; c < NR; c++) {
                int p = c;
                for (int r = c + 1; r < NR; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
                double[] t = a[c]; a[c] = a[p]; a[p] = t;
                double d = a[c][c];
                if (Math.abs(d) < 1e-12) return null;
                for (int j = 0; j < 2 * NR; j++) a[c][j] /= d;
                for (int r = 0; r < NR; r++) if (r != c) { double f = a[r][c]; for (int j = 0; j < 2 * NR; j++) a[r][j] -= f * a[c][j]; }
            }
            double[][] o = new double[NR][NR];
            for (int i = 0; i < NR; i++) for (int j = 0; j < NR; j++) o[i][j] = a[i][NR + j];
            return o;
        }
    }

    static double[] interp(double[] lp, int pl, int L, boolean spec) {
        // lp[1..pl] previous log2 amplitudes; JMBE: kappa = pl/L, k = floor(kappa*l), delta = kappa*l - k
        double[] out = new double[L + 1];
        double kappa = (double) pl / L;
        for (int l = 1; l <= L; l++) {
            double kl = kappa * l;
            int k = (int) Math.floor(kl);
            double del = kl - k;
            double a = lp[Math.max(1, Math.min(pl, k))];
            int kp;
            if (spec) kp = k + 1;
            else { double k1 = kappa * (l + 1); kp = (int) Math.floor(k1); }   // JMBE code: kFloor[l+1]
            double b = lp[Math.max(1, Math.min(pl, kp))];
            out[l] = (1 - del) * a + del * b;
        }
        return out;
    }

    static void frameRegress(List<AmbeEnhancementAnalyzer.Frame> fr, int i, double[] measured, Ls ls, boolean[] okOut) {
        AmbeEnhancementAnalyzer.Frame f = fr.get(i);
        if (i == 0 || f.target == null || measured == null) return;
        AmbeEnhancementAnalyzer.Frame p = fr.get(i - 1);
        if (p.idx != f.idx - 1) return;
        int L = f.L, pl = p.L;
        double[] lp = new double[pl + 1];
        for (int l = 1; l <= pl; l++) lp[l] = Math.log(Math.max(1e-6, p.m[l - 1])) / Math.log(2);
        double[] pc = interp(lp, pl, L, false), ps = interp(lp, pl, L, true);
        double[] la = new double[L + 1], tt = new double[L + 1];
        double mp = 0, ml = 0;
        for (int l = 1; l <= L; l++) { la[l] = Math.log(Math.max(1e-6, f.m[l - 1])) / Math.log(2); mp += pc[l]; ml += la[l]; }
        mp /= L; ml /= L;
        double mt = 0;
        for (int l = 1; l <= L; l++) { tt[l] = (la[l] - ml) - RHO * (pc[l] - mp); mt += tt[l]; }
        mt /= L;
        double[] tlo = new double[L + 1], thi = new double[L + 1];
        for (int l = 1; l <= L; l++) {
            double s = 0; int c = 0;
            for (int k = Math.max(1, l - 3); k <= Math.min(L, l + 3); k++) { s += tt[k] - mt; c++; }
            tlo[l] = s / c; thi[l] = (tt[l] - mt) - tlo[l];
        }
        List<Integer> idx = new ArrayList<>();
        for (int l = 1; l <= L; l++) if (f.used[l - 1] && f.v[l - 1]) idx.add(l);
        if (idx.size() < 4) return;
        int n = idx.size();
        double[][] x = new double[n][NR];
        double[] y = new double[n];
        double[] mean = new double[NR + 1];
        for (int k = 0; k < n; k++) {
            int l = idx.get(k);
            x[k][0] = pc[l] - mp; x[k][1] = tlo[l]; x[k][2] = thi[l]; x[k][3] = ps[l] - pc[l];
            y[k] = (AmbeEnhancementAnalyzer.db(f.target[l - 1]) - AmbeEnhancementAnalyzer.db(measured[l - 1])) / LOG2DB;
            for (int j = 0; j < NR; j++) mean[j] += x[k][j];
            mean[NR] += y[k];
        }
        for (int j = 0; j <= NR; j++) mean[j] /= n;
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < NR; j++) x[k][j] -= mean[j];
            ls.add(x[k], y[k] - mean[NR]);
        }
        okOut[0] = true;
    }

    static void report(String title, Ls ls) {
        double[][] inv = ls.inv();
        if (inv == null) { System.out.println(title + ": singular (a regressor is identically 0, e.g. L never changed)"); return; }
        double[] b = new double[NR];
        for (int i = 0; i < NR; i++) for (int j = 0; j < NR; j++) b[i] += inv[i][j] * ls.xty[j];
        double sse = ls.yty;
        for (int i = 0; i < NR; i++) sse -= b[i] * ls.xty[i];
        double s2 = sse / Math.max(1, ls.n - NR);
        System.out.printf("%n%s  (%d harmonics; residual RMS before %.3f dB, after %.3f dB, R^2 %.4f)%n", title, (long) ls.n,
                Math.sqrt(ls.yty / ls.n) * LOG2DB, Math.sqrt(Math.max(0, sse) / ls.n) * LOG2DB, 1 - sse / ls.yty);
        System.out.printf("  %-26s %9s %8s   %s%n", "regressor", "coef", "+/-se", "meaning");
        String[] meaning = {"chip rho - 0.65 (0 = same prediction)", "chip smooth-part scale - 1", "chip fine-part (HOC) scale - 1", "0 = JMBE code interp, rho = spec interp"};
        for (int i = 0; i < NR; i++)
            System.out.printf("  %-26s %+9.4f %8.4f   %s%n", RN[i], b[i], Math.sqrt(s2 * inv[i][i]), meaning[i]);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AmbeDecodeRegress <dir> [--delay 80] [--calls all|test] [--max-calls N] [--pcm-endian big|little]");
            return;
        }
        Path dir = Paths.get(args[0]);
        int delay = 80, maxCalls = Integer.MAX_VALUE;
        boolean testOnly = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--delay": delay = Integer.parseInt(args[++i]); break;
                case "--calls": testOnly = !args[++i].equalsIgnoreCase("all"); break;
                case "--max-calls": maxCalls = Integer.parseInt(args[++i]); break;
                default:
                    if (i + 1 < args.length && AmbeEnhancementAnalyzer.applyExtractOption(args[i], args[i + 1])) i++;
                    else System.err.println("ignoring " + args[i]);
            }
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.params.txt")) { for (Path p : ds) files.add(p); }
        Collections.sort(files);
        Ls all = new Ls(), even = new Ls(), odd = new Ls();
        int callIdx = 0, nc = 0;
        for (Path pf : files) {
            if (nc >= maxCalls) break;
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcm = dir.resolve(call + ".pcm");
            if (!Files.exists(pcm)) continue;
            int my = callIdx++;
            if (testOnly && my % 5 != 4) continue;
            List<AmbeEnhancementAnalyzer.Frame> fr = AmbeEnhancementAnalyzer.loadParams(pf, call, my);
            if (fr.size() < 10) continue;
            AmbeEnhancementAnalyzer.markClean(fr);
            double[] chip = AmbeEnhancementAnalyzer.readPcm(pcm);
            AmbeEnhancementAnalyzer.extractAll(fr, chip, delay);
            double[] synth = AmbeEnhancementAnalyzer.synthesize(fr);
            Ls ls = new Ls();
            boolean[] ok = new boolean[1];
            for (int i = 0; i < fr.size(); i++) {
                AmbeEnhancementAnalyzer.Frame f = fr.get(i);
                if (f.target == null) continue;
                frameRegress(fr, i, AmbeAudioCompare.measure(f, synth), ls, ok);
            }
            all.merge(ls);
            ((my & 1) == 0 ? even : odd).merge(ls);
            nc++;
        }
        System.out.println("calls used: " + nc);
        report("ALL calls", all);
        report("even-numbered calls", even);
        report("odd-numbered calls", odd);
        System.out.println("\nReading: coefficients near 0 with R^2 near 0 mean the chip agrees with the JMBE decode to within what this test can see.\n"
                + "A coefficient several se from 0 and stable between the even and odd halves is a real decode difference; the size gives the factor.\n"
                + "(se assumes independent harmonics, so treat it as optimistic by ~2x; the even/odd agreement is the stronger check.)");
    }
}
