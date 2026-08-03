package analysis;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * Trains the LearnedEnhancer MLP to reproduce the DVSI chip's per-harmonic gain.
 * Reuses the loading, delay estimation and target extraction of AmbeEnhancementAnalyzer,
 * so the input files are the same (<call>.params.txt + <call>.pcm).
 *
 *   javac SpectralAmplitudeEnhancer.java AmbeEnhancementAnalyzer.java LearnedEnhancer.java AmbeGainTrainer.java
 *   java AmbeGainTrainer <dir> [--out gain-model.txt] [--epochs 80] [--hidden 32,16]
 *                              [--lr 0.003] [--delay N] [--seed 1]
 *                              [--extract ls|stft] [--win-periods X] [--win-min N] [--win-max N]
 *                              [--taper X] [--ridge X] [--min-r2 X] [--min-snr-db X]
 * The extraction options are documented in AmbeEnhancementAnalyzer.
 *
 * Split: with 5+ calls, by call (calls 0-2 of every 5 train, 3 validation, 4 test).
 * With fewer calls, by blocks of 100 frames, which is optimistic because blocks from the
 * same call share a speaker and a channel.
 */
public final class AmbeGainTrainer {

    static final int NF = LearnedEnhancer.NF;
    static final double GMIN = LearnedEnhancer.MIN_G, GMAX = LearnedEnhancer.MAX_G;

    static final class Data {
        double[][] x;
        double[] y;
        int n() { return y.length; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AmbeGainTrainer <dir> [--out f] [--epochs N] [--hidden a,b] [--lr x] [--delay N] [--seed S] [--context 1|2]");
            return;
        }
        Path dir = Paths.get(args[0]);
        String outPath = "gain-model.txt";
        int epochs = 80;
        int[] hidden = {32, 16};
        double lr = 0.003;
        Integer fixedDelay = null;
        long seed = 1;
        int context = 2;                      // frames of context each side (1 or 2)
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--out": outPath = args[++i]; break;
                case "--epochs": epochs = Integer.parseInt(args[++i]); break;
                case "--hidden":
                    String[] h = args[++i].split(",");
                    hidden = new int[h.length];
                    for (int k = 0; k < h.length; k++) hidden[k] = Integer.parseInt(h[k].trim());
                    break;
                case "--lr": lr = Double.parseDouble(args[++i]); break;
                case "--delay": fixedDelay = Integer.parseInt(args[++i]); break;
                case "--seed": seed = Long.parseLong(args[++i]); break;
                case "--context": context = Math.max(1, Math.min(2, Integer.parseInt(args[++i]))); break;
                default:
                    if (i + 1 < args.length && AmbeEnhancementAnalyzer.applyExtractOption(args[i], args[i + 1])) i++;
                    else System.err.println("ignoring " + args[i]);
            }
        }
        System.out.println("target extraction: " + (AmbeEnhancementAnalyzer.USE_LS ? "least squares" : "STFT peak picking"));

        // ---------------------------------------------------------------- load
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.params.txt")) {
            for (Path p : ds) files.add(p);
        }
        Collections.sort(files);

        Map<AmbeEnhancementAnalyzer.Frame, AmbeEnhancementAnalyzer.Frame[]> nb = new IdentityHashMap<>();   // {t-2, t-1, t+1, t+2}, null where missing
        List<AmbeEnhancementAnalyzer.Frame> usable = new ArrayList<>();
        final int ctxN = context;
        int callIdx = 0;
        for (Path pf : files) {
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcmPath = dir.resolve(call + ".pcm");
            if (!Files.exists(pcmPath)) { System.err.println("skip " + call + ": no pcm"); continue; }
            List<AmbeEnhancementAnalyzer.Frame> frames = AmbeEnhancementAnalyzer.loadParams(pf, call, callIdx);
            AmbeEnhancementAnalyzer.markClean(frames);
            double[] pcm = AmbeEnhancementAnalyzer.readPcm(pcmPath);
            int delay = fixedDelay != null ? fixedDelay : AmbeEnhancementAnalyzer.estimateDelay(frames, pcm);
            int ok = AmbeEnhancementAnalyzer.extractAll(frames, pcm, delay);
            double r2 = 0;
            for (int i = 0; i < frames.size(); i++) {
                AmbeEnhancementAnalyzer.Frame f = frames.get(i);
                if (f.target == null) continue;
                AmbeEnhancementAnalyzer.Frame p2 = null, n2 = null;
                if (ctxN >= 2) {
                    if (i >= 2 && frames.get(i - 2).idx == f.idx - 2) p2 = frames.get(i - 2);
                    if (i + 2 < frames.size() && frames.get(i + 2).idx == f.idx + 2) n2 = frames.get(i + 2);
                }
                nb.put(f, new AmbeEnhancementAnalyzer.Frame[]{p2, frames.get(i - 1), frames.get(i + 1), n2});
                usable.add(f);
                if (!Double.isNaN(f.r2)) r2 += f.r2;
            }
            System.out.printf("%s: %d frames, %d usable, delay %d%s%n", call, frames.size(), ok, delay,
                    AmbeEnhancementAnalyzer.USE_LS ? String.format(", mean fit R2 %.2f", r2 / Math.max(1, ok)) : "");
            callIdx++;
        }
        final int nCalls = callIdx;
        System.out.println(AmbeEnhancementAnalyzer.gateSummary());
        if (usable.isEmpty()) { System.err.println("no usable frames"); return; }

        List<AmbeEnhancementAnalyzer.Frame> trF = new ArrayList<>(), vaF = new ArrayList<>(), teF = new ArrayList<>();
        for (AmbeEnhancementAnalyzer.Frame f : usable) {
            int k = nCalls >= 5 ? f.callIdx % 5 : (f.idx / 100) % 5;
            (k < 3 ? trF : k == 3 ? vaF : teF).add(f);
        }
        if (nCalls < 5) {
            System.out.println("\nWARNING: fewer than 5 calls, so the split is by 100-frame blocks inside the same calls."
                    + " Held-out numbers are optimistic.");
        }
        Data tr = build(trF, nb), va = build(vaF, nb), te = build(teF, nb);
        System.out.printf("%nframes train/val/test: %d/%d/%d   harmonic samples: %d/%d/%d%n",
                trF.size(), vaF.size(), teF.size(), tr.n(), va.n(), te.n());
        if (tr.n() < 2000 || va.n() < 200) {
            System.err.println("too little data to train reliably (need more calls)");
            if (tr.n() < 200 || va.n() < 20) return;
        }

        // --------------------------------------------------------------- train
        int[] sizes = new int[hidden.length + 2];
        sizes[0] = NF;
        System.arraycopy(hidden, 0, sizes, 1, hidden.length);
        sizes[sizes.length - 1] = 1;
        LearnedEnhancer.Mlp net = train(sizes, tr, va, epochs, lr, seed);
        net.ctx = context;
        System.out.println("context: " + context + " frame(s) each side");
        net.save(Paths.get(outPath));
        System.out.println("\nsaved model to " + outPath);

        // ----------------------------------------------------------- evaluation
        System.out.println("\nRMS log-amplitude error vs chip (dB, voiced harmonics, per-frame level matched)");
        System.out.println("model                     train       val      test   bias(test)");

        compare("no enhancement", trF, vaF, teF, fr -> fr.m);
        compare("IMBE-style enhance()", trF, vaF, teF, fr -> SpectralAmplitudeEnhancer.enhance(fr.m, fr.w0));

        List<AmbeEnhancementAnalyzer.Frame> fitSet = AmbeEnhancementAnalyzer.subsample(trF, 1500);
        Function<double[], Double> cost = x -> {
            if (x[0] < 0.05 || x[1] > 0.98 || x[0] >= x[1] - 0.02 || x[2] < 0 || x[2] > 1.5) return 1e3;
            return AmbeEnhancementAnalyzer.evaluate(fitSet, fr -> SpectralAmplitudeEnhancer
                    .enhanceHighOrder(fr.m, fr.w0, 6, x[0], x[1], x[2]))[0];
        };
        double[] hb = AmbeEnhancementAnalyzer.nelderMead(cost, new double[]{0.6, 0.8, 1.0}, 0.1, 60);
        compare("fitted postfilter (ord 6)", trF, vaF, teF, fr -> SpectralAmplitudeEnhancer
                .enhanceHighOrder(fr.m, fr.w0, 6, hb[0], hb[1], hb[2]));

        final LearnedEnhancer.Mlp fnet = net;
        compare("learned MLP", trF, vaF, teF, fr -> {
            AmbeEnhancementAnalyzer.Frame[] n = nb.get(fr);
            return LearnedEnhancer.enhance(fnet, fr.m, fr.v, fr.w0, nbM(n), nbW(n));
        });

        // --------------------------------------------------------- importance
        System.out.println("\nPermutation importance on validation set (rise in RMS gain error, dB):");
        importance(net, va, seed);

        System.out.println("\nUsage:");
        System.out.println("  LearnedEnhancer.Mlp net = LearnedEnhancer.Mlp.load(Paths.get(\"" + outPath + "\"));");
        System.out.println("  double[] out = LearnedEnhancer.enhance(net, m, v, w0, nm, nw);");
        System.out.println("  // nm = {m[t-2], m[t-1], m[t+1], m[t+2]}, nw = the matching w0 values; null / 0 where a frame is missing");
    }

    // ---------------------------------------------------------------- dataset

    static Data build(List<AmbeEnhancementAnalyzer.Frame> frames,
                      Map<AmbeEnhancementAnalyzer.Frame, AmbeEnhancementAnalyzer.Frame[]> nb) {
        List<double[]> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (AmbeEnhancementAnalyzer.Frame f : frames) {
            AmbeEnhancementAnalyzer.Frame[] n = nb.get(f);
            LearnedEnhancer.FrameFeatures ff = LearnedEnhancer.features(f.m, f.v, f.w0, nbM(n), nbW(n));
            for (int i = 0; i < f.L; i++) {
                if (!f.used[i]) continue;
                double g = AmbeEnhancementAnalyzer.db(f.target[i] / f.m[i]);
                xs.add(ff.x[i]);
                ys.add(Math.max(GMIN, Math.min(GMAX, g)));
            }
        }
        Data d = new Data();
        d.x = xs.toArray(new double[0][]);
        d.y = new double[ys.size()];
        for (int i = 0; i < d.y.length; i++) d.y[i] = ys.get(i);
        return d;
    }

    static double[][] nbM(AmbeEnhancementAnalyzer.Frame[] n) {
        double[][] r = new double[4][];
        for (int k = 0; k < 4; k++) if (n[k] != null) r[k] = n[k].m;
        return r;
    }

    static double[] nbW(AmbeEnhancementAnalyzer.Frame[] n) {
        double[] r = new double[4];
        for (int k = 0; k < 4; k++) if (n[k] != null) r[k] = n[k].w0;
        return r;
    }

    // --------------------------------------------------------------- training

    static LearnedEnhancer.Mlp train(int[] sizes, Data tr, Data va, int epochs, double lr0, long seed) {
        Random rnd = new Random(seed);
        LearnedEnhancer.Mlp net = new LearnedEnhancer.Mlp(sizes);
        final int nl = net.w.length;

        // standardization from the training set
        for (int j = 0; j < NF; j++) {
            double s = 0, s2 = 0;
            for (double[] x : tr.x) { s += x[j]; s2 += x[j] * x[j]; }
            double m = s / tr.n();
            double sd = Math.sqrt(Math.max(1e-12, s2 / tr.n() - m * m));
            net.mean[j] = m;
            net.std[j] = sd < 1e-6 ? 1.0 : sd;          // constant feature: pass through unscaled
        }
        // init
        for (int l = 0; l < nl; l++) {
            double sc = (l == nl - 1 ? 0.1 : 1.0) / Math.sqrt(sizes[l]);
            for (double[] row : net.w[l]) for (int i = 0; i < row.length; i++) row[i] = rnd.nextGaussian() * sc;
        }
        // standardized copies
        double[][] xt = standardize(net, tr.x), xv = standardize(net, va.x);

        double[][][] mw = zerosLike(net.w), vw = zerosLike(net.w), gw = zerosLike(net.w);
        double[][] mb = zerosLike(net.b), vb = zerosLike(net.b), gb = zerosLike(net.b);
        double[][] act = new double[nl + 1][];
        for (int l = 0; l <= nl; l++) act[l] = new double[sizes[l]];
        double[][] delta = new double[nl + 1][];
        for (int l = 0; l <= nl; l++) delta[l] = new double[sizes[l]];

        final int batch = 256;
        final double b1 = 0.9, b2 = 0.999, eps = 1e-8, wd = 1e-5;
        int n = xt.length;
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;

        LearnedEnhancer.Mlp best = net.copy();
        double bestVal = Double.MAX_VALUE;
        int sinceBest = 0, step = 0;
        System.out.println("\nepoch  train-rms   val-rms");
        for (int ep = 1; ep <= epochs; ep++) {
            for (int i = n - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
            }
            double lr = lr0 * (0.5 * (1 + Math.cos(Math.PI * (ep - 1) / epochs)) * 0.95 + 0.05);
            for (int s = 0; s < n; s += batch) {
                int e = Math.min(n, s + batch);
                zero(gw, gb);
                for (int k = s; k < e; k++) {
                    int id = idx[k];
                    System.arraycopy(xt[id], 0, act[0], 0, NF);
                    for (int l = 0; l < nl; l++) {
                        for (int o = 0; o < sizes[l + 1]; o++) {
                            double z = net.b[l][o];
                            double[] row = net.w[l][o];
                            for (int i = 0; i < row.length; i++) z += row[i] * act[l][i];
                            act[l + 1][o] = (l == nl - 1) ? z : Math.tanh(z);
                        }
                    }
                    delta[nl][0] = 2.0 * (act[nl][0] - tr.y[id]) / (e - s);
                    for (int l = nl - 1; l >= 0; l--) {
                        java.util.Arrays.fill(delta[l], 0);
                        for (int o = 0; o < sizes[l + 1]; o++) {
                            double d = delta[l + 1][o];
                            gb[l][o] += d;
                            double[] row = net.w[l][o], grow = gw[l][o];
                            for (int i = 0; i < row.length; i++) {
                                grow[i] += d * act[l][i];
                                delta[l][i] += d * row[i];
                            }
                        }
                        if (l > 0) for (int i = 0; i < sizes[l]; i++) delta[l][i] *= (1 - act[l][i] * act[l][i]);
                    }
                }
                step++;
                double c1 = 1 - Math.pow(b1, step), c2 = 1 - Math.pow(b2, step);
                for (int l = 0; l < nl; l++) {
                    for (int o = 0; o < sizes[l + 1]; o++) {
                        for (int i = 0; i < sizes[l]; i++) {
                            double g = gw[l][o][i] + wd * net.w[l][o][i];
                            mw[l][o][i] = b1 * mw[l][o][i] + (1 - b1) * g;
                            vw[l][o][i] = b2 * vw[l][o][i] + (1 - b2) * g * g;
                            net.w[l][o][i] -= lr * (mw[l][o][i] / c1) / (Math.sqrt(vw[l][o][i] / c2) + eps);
                        }
                        double g = gb[l][o];
                        mb[l][o] = b1 * mb[l][o] + (1 - b1) * g;
                        vb[l][o] = b2 * vb[l][o] + (1 - b2) * g * g;
                        net.b[l][o] -= lr * (mb[l][o] / c1) / (Math.sqrt(vb[l][o] / c2) + eps);
                    }
                }
            }
            double trR = rmsNorm(net, xt, tr.y), vaR = rmsNorm(net, xv, va.y);
            if (ep == 1 || ep % 5 == 0 || ep == epochs) System.out.printf("%5d  %9.4f  %9.4f%n", ep, trR, vaR);
            if (vaR < bestVal - 1e-4) {
                bestVal = vaR;
                best = net.copy();
                sinceBest = 0;
            } else if (++sinceBest >= 12) {
                System.out.printf("early stop at epoch %d (best val %.4f)%n", ep, bestVal);
                break;
            }
        }
        return best;
    }

    static double[][] standardize(LearnedEnhancer.Mlp net, double[][] x) {
        double[][] r = new double[x.length][NF];
        for (int i = 0; i < x.length; i++)
            for (int j = 0; j < NF; j++) r[i][j] = (x[i][j] - net.mean[j]) / net.std[j];
        return r;
    }

    static double rmsNorm(LearnedEnhancer.Mlp net, double[][] xn, double[] y) {
        double s = 0;
        for (int i = 0; i < xn.length; i++) {
            double p = Math.max(GMIN, Math.min(GMAX, net.forwardNormalized(xn[i])));
            double d = p - y[i];
            s += d * d;
        }
        return Math.sqrt(s / Math.max(1, xn.length));
    }

    static double[][][] zerosLike(double[][][] a) {
        double[][][] r = new double[a.length][][];
        for (int l = 0; l < a.length; l++) {
            r[l] = new double[a[l].length][];
            for (int o = 0; o < a[l].length; o++) r[l][o] = new double[a[l][o].length];
        }
        return r;
    }

    static double[][] zerosLike(double[][] a) {
        double[][] r = new double[a.length][];
        for (int l = 0; l < a.length; l++) r[l] = new double[a[l].length];
        return r;
    }

    static void zero(double[][][] gw, double[][] gb) {
        for (double[][] a : gw) for (double[] b : a) java.util.Arrays.fill(b, 0);
        for (double[] b : gb) java.util.Arrays.fill(b, 0);
    }

    // ----------------------------------------------------------------- reports

    static void compare(String name, List<AmbeEnhancementAnalyzer.Frame> tr,
                        List<AmbeEnhancementAnalyzer.Frame> va, List<AmbeEnhancementAnalyzer.Frame> te,
                        Function<AmbeEnhancementAnalyzer.Frame, double[]> enh) {
        double[] a = AmbeEnhancementAnalyzer.evaluate(tr, enh);
        double[] b = AmbeEnhancementAnalyzer.evaluate(va, enh);
        double[] c = AmbeEnhancementAnalyzer.evaluate(te, enh);
        System.out.printf("%-24s %8.3f  %8.3f  %8.3f  %8.3f%n", name, a[0], b[0], c[0], c[1]);
    }

    static void importance(LearnedEnhancer.Mlp net, Data va, long seed) {
        double[][] xn = standardize(net, va.x);
        double base = rmsNorm(net, xn, va.y);
        int[][] groups = {{0, 1, 2, 3}, {4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}, {15, 16}, {17}, {18},
                {20, 21, 24, 25}, {19, 22, 23, 26}};
        String[] names = {"position / pitch / L", "local spectral shape", "wide envelope / tilt", "IMBE-style prior",
                "postfilter prior", "temporal +/-1 frame", "temporal +/-2 frames"};
        Random rnd = new Random(seed + 7);
        int n = xn.length;
        System.out.printf("  baseline val RMS %.4f dB%n", base);
        for (int g = 0; g < groups.length; g++) {
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) perm[i] = i;
            for (int i = n - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
            }
            double[][] xp = new double[n][];
            for (int i = 0; i < n; i++) {
                xp[i] = xn[i].clone();
                for (int c : groups[g]) xp[i][c] = xn[perm[i]][c];
            }
            System.out.printf("  %-24s +%.4f%n", names[g], rmsNorm(net, xp, va.y) - base);
        }
    }
}
