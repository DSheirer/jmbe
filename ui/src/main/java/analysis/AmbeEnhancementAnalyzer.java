package analysis;

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
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Compares your decoder's spectral amplitudes against a DVSI chip's decoded audio
 * and fits enhancement parameters to the chip's behavior.
 * Requires SpectralAmplitudeEnhancer.java (same directory).
 *
 * USAGE
 *   javac SpectralAmplitudeEnhancer.java AmbeEnhancementAnalyzer.java
 *   java AmbeEnhancementAnalyzer <dir> [--delay N] [--fit-frames N] [--csv out.csv] [--selftest]
 *                                      [extraction options]
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
 *   <call>.pcm         chip output, raw 16-bit BIG-endian mono, 8 kHz (use --pcm-endian little for little-endian files)
 *                      (not needed with --selftest)
 *
 * TARGET EXTRACTION (how the chip's harmonic amplitudes are measured)
 *   --extract stft|ls  stft (default): windowed-FFT peak picking near each harmonic over about 4
 *                      pitch periods. Robust to small pitch errors, but blends neighboring frames.
 *                      ls: joint least-squares fit of every harmonic at its known frequency over a
 *                      short window. Sharper in time on clean audio, but FRAGILE: on synthetic
 *                      audio a 1.5% error between the decoded pitch and the chip's real pitch
 *                      doubled its error, and on real chip audio it produced garbage. Use it
 *                      only after --compare shows it agrees with stft on your data.
 *   --min-snr-db X     keep a harmonic only if its measured amplitude is at least X dB above the
 *                      local inter-harmonic noise floor (default 15; 0 = keep everything). A noise
 *                      floor otherwise shows up as a huge positive "gain" that grows with
 *                      frequency, because decoded amplitudes fall off with frequency but the
 *                      floor does not. A harmonic that is really below the floor cannot be
 *                      measured, and the ones that pass by chance look like enormous gains, so a
 *                      lenient gate (10 dB) still lets that artifact through. The summary prints
 *                      how many harmonics the gate dropped; if it is most of them at high
 *                      frequency, the chip audio simply has too little level there to measure.
 *   --compare          run both extractors on the same real frames and print how they differ by
 *                      frequency band. Where they disagree, at least one is wrong.
 *   --align-check      for strong voiced frames, scan time offset (+/-240 samples) and pitch scale
 *                      (0.90-1.10) for the combination that puts the most chip-audio energy on
 *                      your decoded harmonic frequencies, and report the distributions. If the
 *                      best offset is not about 0 or the best scale is not about 1.000, the
 *                      decoded frames and the chip audio are not aligned the way the extractors
 *                      assume, and every amplitude comparison above is wrong. Fix with
 *                      --delay N (add the reported offset to the delay you were using) and/or
 *                      --pitch-scale S. --align-frames N sets frames examined per call (40).
 *   --pcm-check        sanity-check the chip audio before trusting anything else: sample count vs
 *                      frame count, 16-bit little/big-endian, mu-law and A-law readings (lag-1
 *                      autocorrelation), and the audio's own pitch (autocorrelation) against your
 *                      decoded pitch. Run this first when --align-check finds no harmonic structure.
 *   --pcm-endian E     big (default) or little: byte order of the 16-bit .pcm files.
 *   --pitch-scale S    multiply every decoded fundamental by S before anything else.
 *   --pitch-model M    (ls only) const (default): pitch held constant within the window.
 *                      linear: pitch glides linearly between frame centers. Mean fit R2 is NOT a
 *                      reliable way to pick between them when the window has few samples per
 *                      unknown, because an overfitted model also scores a high R2.
 *   --win-periods X    LS window length in pitch periods (default 1.5)
 *   --win-min N        LS window minimum in samples (default 128; a frame is 160)
 *   --win-max N        LS window maximum in samples (default 192)
 *   --taper X          fraction of the LS window that is tapered (default 0.4; 0 = rectangular)
 *   --ridge X          relative ridge regularization (default 1e-3)
 *   --min-r2 X         drop frames whose fit explains less than this fraction of the window
 *                      energy (default 0.5; 0 keeps everything)
 *
 * --selftest synthesizes audio from your own decoded parameters (no enhancement, so the true
 * gain is exactly 0 dB), measures it back with both extractors and prints the error. That is
 * the floor of the measurement itself under your real pitch and amplitude dynamics.
 *
 * OUTPUT
 *   delay estimate, implied-gain statistics, baseline errors, fitted parameters
 *   with train/held-out RMS log-amplitude error (dB), and optionally a per-harmonic CSV
 *   (implied gain plus features) for fitting a learned model elsewhere.
 */
public final class AmbeEnhancementAnalyzer {

    static final int FS = 8000;
    static final int FRAME = 160;
    static final int NFFT = 2048;
    static final double DF = (double) FS / NFFT;
    static final double MAX_HZ = 3800.0;

    // ---- extraction options (set once from the command line, before any extraction runs)
    static boolean USE_LS = false;
    static boolean PITCH_LINEAR = false;
    static int LS_MIN = 128, LS_MAX = 192;
    static double LS_PERIODS = 1.5, TAPER = 0.4, RIDGE = 1e-3, MIN_R2 = 0.5;
    static double MIN_SNR_DB = 15.0;
    static boolean PCM_BIG_ENDIAN = true; // chip output files are big-endian 16-bit (--pcm-endian big|little)
    static double PITCH_SCALE = 1.0;      // multiplies every decoded w0 (use what --align-check recommends)
    static int ALIGN_FRAMES = 40;         // frames per call examined by --align-check
    static final java.util.concurrent.atomic.LongAdder GATE_TOTAL = new java.util.concurrent.atomic.LongAdder();
    static final java.util.concurrent.atomic.LongAdder GATE_DROPPED = new java.util.concurrent.atomic.LongAdder();

    static String gateSummary() {
        long t = GATE_TOTAL.sum(), d = GATE_DROPPED.sum();
        if (MIN_SNR_DB <= 0) return "SNR gate: off";
        return String.format("SNR gate (>= %.0f dB above the inter-harmonic floor): dropped %d of %d voiced harmonics (%.1f%%)",
                MIN_SNR_DB, d, t, t == 0 ? 0.0 : 100.0 * d / t);
    }

    static void resetGate() {
        GATE_TOTAL.reset();
        GATE_DROPPED.reset();
    }

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
        double r2 = Double.NaN;   // LS fit quality, NaN for the STFT method

        double f0Hz() { return w0 * FS / (2 * Math.PI); }
    }

    /** Handles the shared extraction options. Returns true if the key was recognized. */
    static boolean applyExtractOption(String key, String val) {
        switch (key) {
            case "--extract": USE_LS = !val.equalsIgnoreCase("stft"); return true;
            case "--pitch-model": PITCH_LINEAR = !val.equalsIgnoreCase("const"); return true;
            case "--win-min": LS_MIN = Integer.parseInt(val); return true;
            case "--win-max": LS_MAX = Integer.parseInt(val); return true;
            case "--win-periods": LS_PERIODS = Double.parseDouble(val); return true;
            case "--taper": TAPER = Double.parseDouble(val); return true;
            case "--ridge": RIDGE = Double.parseDouble(val); return true;
            case "--min-r2": MIN_R2 = Double.parseDouble(val); return true;
            case "--min-snr-db": MIN_SNR_DB = Double.parseDouble(val); return true;
            case "--pcm-endian": PCM_BIG_ENDIAN = !val.equalsIgnoreCase("little"); return true;
            case "--pitch-scale": PITCH_SCALE = Double.parseDouble(val); return true;
            default: return false;
        }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AmbeEnhancementAnalyzer <dir> [--delay N] [--fit-frames N] [--csv out.csv] [--selftest] [--extract ls|stft] [...]");
            return;
        }
        Path dir = Paths.get(args[0]);
        Integer fixedDelay = null;
        int fitFrames = 2000;
        String csvPath = null;
        boolean selftest = false, compare = false, alignCheck = false, pcmCheck = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--delay": fixedDelay = Integer.parseInt(args[++i]); break;
                case "--fit-frames": fitFrames = Integer.parseInt(args[++i]); break;
                case "--csv": csvPath = args[++i]; break;
                case "--selftest": selftest = true; break;
                case "--compare": compare = true; break;
                case "--align-check": alignCheck = true; break;
                case "--pcm-check": pcmCheck = true; break;
                case "--align-frames": ALIGN_FRAMES = Integer.parseInt(args[++i]); break;
                default:
                    if (i + 1 < args.length && applyExtractOption(args[i], args[i + 1])) i++;
                    else System.err.println("ignoring " + args[i]);
            }
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.params.txt")) {
            for (Path p : ds) files.add(p);
        }
        Collections.sort(files);

        if (selftest) {
            selfTest(files);
            return;
        }
        if (compare) {
            compareExtractors(files, dir, fixedDelay);
            return;
        }
        if (alignCheck) {
            alignCheck(files, dir, fixedDelay);
            return;
        }
        if (pcmCheck) {
            pcmCheck(files, dir, fixedDelay);
            return;
        }

        System.out.println("target extraction: " + (USE_LS
                ? String.format("least squares (window %.1f periods, %d-%d samples, taper %.2f, min R2 %.2f)",
                        LS_PERIODS, LS_MIN, LS_MAX, TAPER, MIN_R2)
                : "STFT peak picking"));

        List<Frame> all = new ArrayList<>();
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
            int clean = 0;
            for (Frame f : frames) if (f.clean) clean++;
            int ok = extractAll(frames, pcm, delay);
            String fit = "";
            if (USE_LS) {
                double r2 = 0;
                for (Frame f : frames) if (f.target != null) r2 += f.r2;
                fit = String.format(", mean fit R2 %.2f", r2 / Math.max(1, ok));
            }
            System.out.printf("%s: %d frames, %d clean, %d usable, delay %d samples%s%n",
                    call, frames.size(), clean, ok, delay, fit);
            all.addAll(frames);
            callIdx++;
        }
        final int nCalls = callIdx;
        System.out.println(gateSummary());

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
            f.w0 = Double.parseDouble(t[1]) * PITCH_SCALE;
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
        ByteBuffer.wrap(b).order(PCM_BIG_ENDIAN ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
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

    static final ThreadLocal<double[][]> STFT_BUF =
            ThreadLocal.withInitial(() -> new double[][]{new double[NFFT], new double[NFFT]});

    /**
     * Extracts targets for every clean frame (in parallel) with the selected method.
     * Frames that cannot be measured keep target == null. Returns the number measured.
     */
    static int extractAll(List<Frame> frames, double[] pcm, int delay) {
        final int n = frames.size();
        final boolean[] ok = new boolean[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            Frame f = frames.get(i);
            f.target = null;
            f.used = null;
            f.r2 = Double.NaN;
            if (!f.clean) return;
            ok[i] = extract(f, frames.get(i - 1), frames.get(i + 1), pcm, delay);
        });
        int c = 0;
        for (boolean b : ok) if (b) c++;
        return c;
    }

    /**
     * Measures one frame: raw harmonic amplitudes by the selected method, then the SNR gate,
     * then level normalization (finishTargets). The windowed spectrum is computed once and
     * serves both the STFT peaks and the inter-harmonic noise floors.
     */
    static boolean extract(Frame f, Frame prev, Frame next, double[] pcm, int delay) {
        double[][] b = STFT_BUF.get();
        double sw = spectrum(f, pcm, delay, b[0], b[1]);
        if (sw <= 0) return false;
        double[] t;
        if (USE_LS) {
            if (!extractTargetsLS(f, prev, next, pcm, delay)) return false;
            t = f.target;                 // raw amplitudes handed over by the LS fit
            f.target = null;
        } else {
            t = stftPeaks(f, b[0], b[1], sw);
        }
        if (MIN_SNR_DB > 0) applyGate(f, t, floors(f, b[0], b[1], sw));
        return finishTargets(f, t);
    }

    /**
     * Hann-windowed FFT of the chip audio around the frame center (about 4 pitch periods, which
     * spans several frames). Fills re/im and returns the window sum for amplitude scaling, or
     * -1 if the frame is too close to the edge of the audio.
     */
    static double spectrum(Frame f, double[] pcm, int delay, double[] re, double[] im) {
        double period = 2 * Math.PI / f.w0;
        int n = (int) Math.max(240, Math.min(640, Math.round(4 * period)));
        int start = f.idx * FRAME + delay + FRAME / 2 - n / 2;
        if (start < 0 || start + n > pcm.length) return -1;

        Arrays.fill(re, 0);
        Arrays.fill(im, 0);
        double sw = 0;
        for (int i = 0; i < n; i++) {
            double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            re[i] = pcm[start + i] * w;
            sw += w;
        }
        fft(re, im);
        return sw;
    }

    /** Peak magnitude within +/-0.3 f0 of each harmonic, as sinusoid amplitude. */
    static double[] stftPeaks(Frame f, double[] re, double[] im, double sw) {
        double f0 = f.f0Hz(), hw = 0.3 * f0;
        double[] t = new double[f.L];
        for (int l = 1; l <= f.L; l++) {
            double fc = l * f0;
            if (fc > MAX_HZ) break;
            int k0 = Math.max(1, (int) Math.round((fc - hw) / DF));
            int k1 = Math.min(NFFT / 2 - 1, (int) Math.round((fc + hw) / DF));
            double pk = 0;
            for (int k = k0; k <= k1; k++) pk = Math.max(pk, Math.hypot(re[k], im[k]));
            t[l - 1] = 2 * pk / sw;
        }
        return t;
    }

    /**
     * Noise floor next to each harmonic, in the same amplitude units as the peaks: the median
     * spectral magnitude in the valley halfway to the next harmonic (or the previous one near
     * the top of the band). Real harmonics with periodic structure leave these valleys nearly
     * empty, so what is left there is noise, leakage and pitch smear.
     */
    static double[] floors(Frame f, double[] re, double[] im, double sw) {
        double f0 = f.f0Hz();
        double[] fl = new double[f.L];
        double[] buf = new double[NFFT / 2];
        for (int l = 1; l <= f.L; l++) {
            if (l * f0 > MAX_HZ) break;
            double fc = (l + 0.5) * f0;
            if (fc > 3950.0) fc = (l - 0.5) * f0;
            int k0 = Math.max(1, (int) Math.round((fc - 0.12 * f0) / DF));
            int k1 = Math.min(NFFT / 2 - 1, (int) Math.round((fc + 0.12 * f0) / DF));
            int c = 0;
            for (int k = k0; k <= k1; k++) buf[c++] = Math.hypot(re[k], im[k]);
            if (c < 3) { fl[l - 1] = Double.NaN; continue; }
            Arrays.sort(buf, 0, c);
            fl[l - 1] = 2 * buf[c / 2] / sw;
        }
        // A single valley holds only a few independent bins, so its median scatters by ~14 dB.
        // Smooth over the 9 nearest valleys (median of medians); noise floors vary slowly.
        double[] sm = new double[f.L];
        double[] tmp = new double[9];
        for (int i = 0; i < f.L; i++) {
            int c = 0;
            for (int j = Math.max(0, i - 4); j <= Math.min(f.L - 1, i + 4); j++)
                if (!Double.isNaN(fl[j]) && fl[j] > 0) tmp[c++] = fl[j];
            if (c < 3) { sm[i] = Double.POSITIVE_INFINITY; continue; }   // cannot judge: treat as noise
            Arrays.sort(tmp, 0, c);
            sm[i] = tmp[c / 2];
        }
        return sm;
    }

    /** Zeroes harmonics that are not at least MIN_SNR_DB above their noise floor. */
    static void applyGate(Frame f, double[] t, double[] floor) {
        double thr = Math.pow(10.0, MIN_SNR_DB / 20.0);
        double f0 = f.f0Hz();
        for (int l = 1; l <= f.L; l++) {
            if (l * f0 > MAX_HZ) break;
            int i = l - 1;
            if (!f.v[i] || f.m[i] <= 1e-9) continue;       // only harmonics that would be compared
            GATE_TOTAL.increment();
            if (t[i] < thr * floor[i]) {
                t[i] = 0;
                GATE_DROPPED.increment();
            }
        }
    }

    /** Marks usable harmonics and scales the measured amplitudes to the decoder's frame energy. */
    static boolean finishTargets(Frame f, double[] t) {
        double f0 = f.f0Hz();
        boolean[] used = new boolean[f.L];
        double em = 0, et = 0;
        int cnt = 0;
        for (int l = 1; l <= f.L; l++) {
            if (l * f0 > MAX_HZ) break;
            int i = l - 1;
            used[i] = f.v[i] && f.m[i] > 1e-9 && t[i] > 1e-9;
            if (used[i]) {
                em += f.m[i] * f.m[i];
                et += t[i] * t[i];
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

    // ---- least-squares harmonic fit

    static final class LsWork {
        double[] A = new double[0], M = new double[0], rhs = new double[0], th = new double[0];
        double[] w = new double[0], x = new double[0];

        void ensure(int n, int k) {
            if (A.length < n * k) A = new double[n * k];
            if (M.length < k * k) M = new double[k * k];
            if (rhs.length < k) { rhs = new double[k]; th = new double[k]; }
            if (w.length < n) { w = new double[n]; x = new double[n]; }
        }
    }

    static final ThreadLocal<LsWork> LS = ThreadLocal.withInitial(LsWork::new);

    /**
     * Fits x[t] = sum_l a_l cos(l*Phi(t)) + b_l sin(l*Phi(t)) over a short window by weighted
     * least squares, where Phi is the fundamental's phase for a pitch that moves linearly
     * between neighboring frame centers. All harmonics are fitted jointly, so closely spaced
     * harmonics do not leak into each other, and the window can stay near one frame long.
     * Amplitude of harmonic l is hypot(a_l, b_l).
     */
    static boolean extractTargetsLS(Frame f, Frame prev, Frame next, double[] pcm, int delay) {
        final int L = f.L, K = 2 * L;
        double period = 2 * Math.PI / f.w0;
        int n = (int) Math.round(Math.max(LS_MIN, Math.min(LS_MAX, LS_PERIODS * period)));
        if (n < 1.3 * K) return false;                       // not enough samples for the unknowns
        int c = f.idx * FRAME + delay + FRAME / 2;
        int start = c - n / 2;
        if (start < 0 || start + n > pcm.length) return false;

        LsWork ws = LS.get();
        ws.ensure(n, K);
        final double[] A = ws.A, M = ws.M, rhs = ws.rhs, th = ws.th, w = ws.w, x = ws.x;

        int taper = (int) Math.round(TAPER * n / 2.0);
        for (int i = 0; i < n; i++) {
            double wt = 1.0;
            if (taper > 0) {
                if (i < taper) wt = 0.5 - 0.5 * Math.cos(Math.PI * (i + 0.5) / taper);
                else if (i >= n - taper) wt = 0.5 - 0.5 * Math.cos(Math.PI * (n - i - 0.5) / taper);
            }
            w[i] = wt;
            x[i] = pcm[start + i];
        }

        // pitch slope before / after the frame center (zero = constant pitch within the window)
        double kPrev = (!PITCH_LINEAR || prev == null) ? 0 : (f.w0 - prev.w0) / FRAME;
        double kNext = (!PITCH_LINEAR || next == null) ? 0 : (next.w0 - f.w0) / FRAME;
        for (int i = 0; i < n; i++) {
            double u = (start + i) - c;
            double k = u < 0 ? kPrev : kNext;
            double phi = f.w0 * u + 0.5 * k * u * u;
            double c1 = Math.cos(phi), s1 = Math.sin(phi);
            double cl = c1, sl = s1;
            int row = i * K;
            for (int l = 0; l < L; l++) {
                A[row + 2 * l] = cl;
                A[row + 2 * l + 1] = sl;
                double nc = cl * c1 - sl * s1;
                sl = sl * c1 + cl * s1;
                cl = nc;
            }
        }

        // weighted normal equations (lower triangle)
        Arrays.fill(M, 0, K * K, 0.0);
        Arrays.fill(rhs, 0, K, 0.0);
        for (int i = 0; i < n; i++) {
            int row = i * K;
            double wi = w[i], xi = x[i];
            if (wi == 0) continue;
            for (int a = 0; a < K; a++) {
                double wa = wi * A[row + a];
                rhs[a] += wa * xi;
                int ma = a * K;
                for (int b = 0; b <= a; b++) M[ma + b] += wa * A[row + b];
            }
        }
        double tr = 0;
        for (int a = 0; a < K; a++) tr += M[a * K + a];
        if (tr <= 0) return false;
        double lam = RIDGE * tr / K;
        for (int a = 0; a < K; a++) M[a * K + a] += lam;

        // Cholesky M = Lc * Lc^T (in place, lower)
        for (int j = 0; j < K; j++) {
            int mj = j * K;
            double s = M[mj + j];
            for (int q = 0; q < j; q++) s -= M[mj + q] * M[mj + q];
            if (s <= 1e-12 * tr / K) return false;
            double d = Math.sqrt(s);
            M[mj + j] = d;
            for (int i = j + 1; i < K; i++) {
                int mi = i * K;
                double t = M[mi + j];
                for (int q = 0; q < j; q++) t -= M[mi + q] * M[mj + q];
                M[mi + j] = t / d;
            }
        }
        for (int i = 0; i < K; i++) {                          // forward: Lc y = rhs
            double t = rhs[i];
            for (int q = 0; q < i; q++) t -= M[i * K + q] * th[q];
            th[i] = t / M[i * K + i];
        }
        for (int i = K - 1; i >= 0; i--) {                     // backward: Lc^T th = y
            double t = th[i];
            for (int q = i + 1; q < K; q++) t -= M[q * K + i] * th[q];
            th[i] = t / M[i * K + i];
        }

        // fit quality
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            int row = i * K;
            double pred = 0;
            for (int a = 0; a < K; a++) pred += A[row + a] * th[a];
            double r = x[i] - pred;
            num += w[i] * r * r;
            den += w[i] * x[i] * x[i];
        }
        if (den <= 0) return false;
        double r2 = 1.0 - num / den;
        if (r2 < MIN_R2) return false;

        double[] t = new double[L];
        for (int l = 0; l < L; l++) t[l] = Math.hypot(th[2 * l], th[2 * l + 1]);
        f.r2 = r2;
        f.target = t;          // raw amplitudes; extract() gates and scales them
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

    // ------------------------------------------------------------- pcm check

    static double[] decode16(byte[] raw, boolean bigEndian) {
        int ns = raw.length / 2;
        double[] x = new double[ns];
        for (int i = 0; i < ns; i++) {
            int a = raw[2 * i], b = raw[2 * i + 1];
            x[i] = bigEndian ? (a << 8) | (b & 0xFF) : (b << 8) | (a & 0xFF);
        }
        return x;
    }

    static double ulaw(int b) {
        int u = ~b & 0xFF;
        int mant = u & 0x0F, exp = (u >> 4) & 7;
        int s = (((mant << 3) + 0x84) << exp) - 0x84;
        return (u & 0x80) != 0 ? -s : s;
    }

    static double alaw(int b) {
        int a = (b ^ 0x55) & 0xFF;
        int mant = a & 0x0F, exp = (a >> 4) & 7;
        int s = exp == 0 ? (mant << 4) + 8 : ((mant << 4) + 0x108) << (exp - 1);
        return (a & 0x80) != 0 ? s : -s;
    }

    /** Lag-1 autocorrelation: about 0.6-0.95 for speech sampled at 8 kHz, about 0 for noise. */
    static double lag1(double[] x, int n) {
        double num = 0, den = 0;
        for (int i = 0; i + 1 < n && i + 1 < x.length; i++) { num += x[i] * x[i + 1]; den += x[i] * x[i]; }
        return den > 0 ? num / den : 0;
    }

    /** Pitch of the audio around sample c by normalized autocorrelation: {f0Hz, strength} or null. */
    static double[] autocorrPitch(double[] pcm, int c) {
        final int n = 480, minLag = 20, maxLag = 160;
        int start = c - n / 2;
        if (start < 0 || start + n + maxLag > pcm.length) return null;
        double mean = 0;
        for (int i = 0; i < n + maxLag; i++) mean += pcm[start + i];
        mean /= (n + maxLag);
        double e1 = 0;
        for (int i = 0; i < n; i++) { double v = pcm[start + i] - mean; e1 += v * v; }
        if (e1 <= 0) return null;
        double[] r = new double[maxLag + 2];
        double best = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double num = 0, e2 = 0;
            for (int i = 0; i < n; i++) {
                double a = pcm[start + i] - mean, b = pcm[start + i + lag] - mean;
                num += a * b;
                e2 += b * b;
            }
            r[lag] = e2 > 0 ? num / Math.sqrt(e1 * e2) : 0;
            best = Math.max(best, r[lag]);
        }
        int lagBest = minLag;
        for (int lag = minLag; lag <= maxLag; lag++) if (r[lag] >= 0.9 * best) { lagBest = lag; break; }
        while (lagBest < maxLag && r[lagBest + 1] > r[lagBest]) lagBest++;
        return new double[]{(double) FS / lagBest, best};
    }

    static double median(List<Double> v) {
        if (v.isEmpty()) return Double.NaN;
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    /**
     * Two independent sanity checks on the chip audio that need no assumption about alignment.
     * (1) File format: sample count against frame count, and lag-1 autocorrelation under the
     * readings little-endian 16-bit, big-endian 16-bit, mu-law and A-law. (2) Pitch measured
     * from the audio by autocorrelation against your decoded pitch, as a histogram of
     * log2(audio f0 / decoded f0): a spike at 0 means the pitches agree, a spike elsewhere is a
     * constant mismatch (an octave is +/-1), and a flat histogram means no frame-by-frame relation.
     */
    static void pcmCheck(List<Path> files, Path dir, Integer fixedDelay) throws IOException {
        final int maxCalls = 300;
        int stepC = Math.max(1, files.size() / maxCalls);
        List<Double> ratio = new ArrayList<>(), rLE = new ArrayList<>(), rBE = new ArrayList<>();
        List<Double> rMu = new ArrayList<>(), rA = new ArrayList<>();
        int wav = 0, calls = 0, callIdx = 0, nStrong = 0, nPeriodic = 0;
        int[] hist = new int[36];
        for (int fi = 0; fi < files.size(); fi += stepC) {
            Path pf = files.get(fi);
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcmPath = dir.resolve(call + ".pcm");
            if (!Files.exists(pcmPath)) continue;
            byte[] raw = Files.readAllBytes(pcmPath);
            if (raw.length < 4000) continue;
            if (raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F') wav++;
            List<Frame> frames = loadParams(pf, call, callIdx++);
            if (frames.isEmpty()) continue;
            calls++;
            int nframes = frames.get(frames.size() - 1).idx + 1;
            ratio.add((raw.length / 2.0) / (nframes * (double) FRAME));

            double[] le = decode16(raw, false), be = decode16(raw, true);
            int nb = Math.min(raw.length, 200000);
            double[] mu = new double[nb], al = new double[nb];
            for (int i = 0; i < nb; i++) { mu[i] = ulaw(raw[i] & 0xFF); al[i] = alaw(raw[i] & 0xFF); }
            double a = lag1(le, 200000), b = lag1(be, 200000);
            rLE.add(a); rBE.add(b); rMu.add(lag1(mu, nb)); rA.add(lag1(al, nb));
            final double[] pcm = b > a ? be : le;                      // use the better reading below

            markClean(frames);
            final int delay = fixedDelay != null ? fixedDelay : estimateDelay(frames, pcm);
            List<Frame> voiced = new ArrayList<>();
            List<Double> en = new ArrayList<>();
            for (Frame f : frames) {
                if (!f.clean || f.L < 4 || !(f.v[0] && f.v[1] && f.v[2] && f.v[3])) continue;
                double e = 0;
                for (double x : f.m) e += x * x;
                voiced.add(f);
                en.add(e);
            }
            if (voiced.size() < 10) continue;
            double med = median(en);
            List<Frame> cand = new ArrayList<>();
            for (int i = 0; i < voiced.size(); i++) if (en.get(i) >= med) cand.add(voiced.get(i));
            int stride = Math.max(1, cand.size() / Math.max(1, ALIGN_FRAMES));
            List<Frame> pick = new ArrayList<>();
            for (int i = 0; i < cand.size() && pick.size() < ALIGN_FRAMES; i += stride) pick.add(cand.get(i));
            double[][] res = new double[pick.size()][];
            IntStream.range(0, pick.size()).parallel().forEach(i ->
                    res[i] = autocorrPitch(pcm, pick.get(i).idx * FRAME + delay + FRAME / 2));
            for (int i = 0; i < res.length; i++) {
                if (res[i] == null) continue;
                nStrong++;
                if (res[i][1] < 0.5) continue;                          // not clearly periodic
                nPeriodic++;
                double lr = Math.log(res[i][0] / pick.get(i).f0Hz()) / Math.log(2.0);
                int bin = (int) Math.floor((lr + 1.5) * 12.0);
                if (bin >= 0 && bin < hist.length) hist[bin]++;
            }
        }
        if (calls == 0) { System.out.println("no calls with both params and pcm"); return; }

        System.out.printf("PCM check on %d calls%n", calls);
        if (wav > 0) System.out.printf("note: %d files start with RIFF, i.e. they have a WAV header (44 bytes), which shifts a raw read slightly.%n", wav);
        double mr = median(ratio), mLE = median(rLE), mBE = median(rBE), mMu = median(rMu), mA = median(rA);
        System.out.printf("samples / (frames x 160): median %.3f   (about 1.0 expected; 2.0 = stereo or 16 kHz, 0.5 = 8-bit)%n", mr);
        System.out.println("lag-1 autocorrelation (speech about 0.6-0.95, noise about 0):");
        System.out.printf("  16-bit little-endian %.3f   16-bit big-endian %.3f   mu-law %.3f   A-law %.3f%n", mLE, mBE, mMu, mA);
        double bestR = Math.max(Math.max(mLE, mBE), Math.max(mMu, mA));
        final double cur = PCM_BIG_ENDIAN ? mBE : mLE;
        boolean formatOk = true;
        if (bestR < 0.2) {
            formatOk = false;
            System.out.println("-> no reading looks like speech (every lag-1 autocorrelation is low): the file may be a different"
                    + " sample rate, stereo, 24/32-bit or float, or not audio at all.");
        } else if ((PCM_BIG_ENDIAN ? mLE - mBE : mBE - mLE) > 0.15 && (PCM_BIG_ENDIAN ? mLE : mBE) >= bestR - 1e-9) {
            formatOk = false;
            System.out.printf("-> the audio is %s-endian 16-bit but the reader is set to %s-endian. Rerun with --pcm-endian %s.%n",
                    PCM_BIG_ENDIAN ? "LITTLE" : "BIG", PCM_BIG_ENDIAN ? "big" : "little", PCM_BIG_ENDIAN ? "little" : "big");
        } else if (mMu - cur > 0.15 && mMu >= bestR - 1e-9 && mr < 0.7) {
            formatOk = false;
            System.out.println("-> the audio looks like 8-bit mu-law. Decode it to 16-bit linear first.");
        } else if (mA - cur > 0.15 && mA >= bestR - 1e-9 && mr < 0.7) {
            formatOk = false;
            System.out.println("-> the audio looks like 8-bit A-law. Decode it to 16-bit linear first.");
        } else {
            System.out.printf("-> sample format OK: 16-bit %s-endian, as the reader assumes.%n", PCM_BIG_ENDIAN ? "big" : "little");
        }
        if (mr < 0.9 || mr > 1.3) {
            formatOk = false;
            System.out.printf("-> the sample count is %.2f x the frame count, so the sample rate, channel count or sample size is not"
                    + " 8 kHz mono 16-bit (or the params do not cover the whole file).%n", mr);
        }

        System.out.printf("%nPitch from the audio vs decoded pitch: %d strong voiced frames, %d clearly periodic%n", nStrong, nPeriodic);
        if (nPeriodic < 30) {
            System.out.println("-> too few periodic frames to judge (the audio is not clearly voiced at the strong decoded frames).");
            return;
        }
        System.out.println("log2(audio f0 / decoded f0), 1/12-octave bins (0 = same pitch, +1 = audio an octave higher):");
        for (int i = 0; i < hist.length; i++) {
            if (hist[i] == 0) continue;
            System.out.printf("  %+5.2f %5.1f%% %s%n", (i + 0.5) / 12.0 - 1.5, 100.0 * hist[i] / nPeriodic,
                    "#".repeat((int) Math.round(60.0 * hist[i] / nPeriodic)));
        }
        int bi = 0;
        double bestShare = -1;
        for (int i = 0; i < hist.length; i++) {
            int s = hist[i] + (i > 0 ? hist[i - 1] : 0) + (i + 1 < hist.length ? hist[i + 1] : 0);
            if (s > bestShare) { bestShare = s; bi = i; }
        }
        double mode = (bi + 0.5) / 12.0 - 1.5, share = bestShare / nPeriodic;
        System.out.printf("modal ratio 2^%+.2f = %.3f, holding %.0f%% of periodic frames (flat would be about %.0f%%)%n",
                mode, Math.pow(2.0, mode), 100 * share, 100.0 * 3 / hist.length);
        if (share < 0.20) {
            System.out.println("-> NO consistent relation between decoded pitch and the audio's pitch, frame by frame. The decoded"
                    + " parameters are not describing this audio: check that frame i of the params is the same 20 ms of speech as audio"
                    + " samples 160*i.., that both come from the same call and bitstream, and that w0 is radians per sample at 8 kHz.");
        } else if (share < 0.60) {
            System.out.printf("-> only a PARTIAL relation: %.0f%% of periodic frames sit at the modal ratio %.3f (a healthy match puts"
                    + " 90%% or more there). The decoded pitch matches the audio only loosely or only part of the time: look for frame"
                    + " drift (dropped or repeated frames, a delay that changes through the call), params from a different decode of"
                    + " the same call, or a w0 that is wrong in some frames.%n", 100 * share, Math.pow(2.0, mode));
        } else if (Math.abs(mode) < 0.09) {
            System.out.println(formatOk
                    ? "-> the pitches agree. If the comb fraction was still at chance, look at timing within the frame or at the decoder's w0 per frame."
                    : "-> the pitches agree under the better reading, so fix the format issue above and rerun.");
        } else if (Math.abs(Math.abs(mode) - 1.0) < 0.12) {
            System.out.printf("-> OCTAVE mismatch: the audio's pitch is %s the decoded pitch. Try --pitch-scale %s.%n",
                    mode > 0 ? "double" : "half", mode > 0 ? "2.0" : "0.5");
        } else {
            System.out.printf("-> constant ratio mismatch: the audio's pitch is %.3f x the decoded pitch. Try --pitch-scale %.3f.%n",
                    Math.pow(2.0, mode), Math.pow(2.0, mode));
        }
    }

    // ------------------------------------------------------------- align check

    /** Fraction of 0-2.6 kHz spectral energy that sits on the comb s*l*f0 (l up to 2.4 kHz). */
    static double combFraction(double[] re, double[] im, double f0, double s, int kc) {
        final int kmax = (int) (2600.0 / DF);
        double tot = 0, sc = 0;
        for (int k = 1; k <= kmax; k++) tot += re[k] * re[k] + im[k] * im[k];
        if (tot <= 0) return Double.NaN;
        for (int l = 1; l <= kc; l++) {
            int k = (int) Math.round(s * l * f0 / DF);
            sc += re[k] * re[k] + im[k] * im[k];
        }
        return sc / tot;
    }

    /** Best pitch scale for one frame at the delay as given (full 0.90-1.10 search), or NaN. */
    static double alignScale(Frame f, double[] pcm, int delay) {
        double f0 = f.f0Hz();
        int kc = (int) Math.floor(2400.0 / (f0 * 1.10));
        if (kc < 6) return Double.NaN;
        double[][] b = STFT_BUF.get();
        if (spectrum(f, pcm, delay, b[0], b[1]) <= 0) return Double.NaN;
        double best = -1, bestS = Double.NaN;
        for (int si = 0; si < 51; si++) {
            double s = 0.90 + si * 0.004;
            double fr = combFraction(b[0], b[1], f0, s, kc);
            if (fr > best) { best = fr; bestS = s; }
        }
        return bestS;
    }

    /**
     * Comb fraction of one frame at each time offset with the pitch scale held at S, followed by
     * the fraction at offset 0 with scale 1.000. Null if any offset is unusable.
     */
    static double[] alignProfile(Frame f, double[] pcm, int delay, int[] offs, double S) {
        double f0 = f.f0Hz();
        int kc = (int) Math.floor(2400.0 / (f0 * Math.max(S, 1.0) * 1.001));
        if (kc < 6) return null;
        double[][] b = STFT_BUF.get();
        double[] out = new double[offs.length + 2];
        out[offs.length + 1] = kc / (2600.0 / DF);                // comb fraction expected by chance
        for (int oi = 0; oi < offs.length; oi++) {
            if (spectrum(f, pcm, delay + offs[oi], b[0], b[1]) <= 0) return null;
            double v = combFraction(b[0], b[1], f0, S, kc);
            if (Double.isNaN(v)) return null;
            out[oi] = v;
            if (offs[oi] == 0) out[offs.length] = combFraction(b[0], b[1], f0, 1.0, kc);
        }
        return out;
    }

    static double pct(List<Double> sorted, double q) {
        return sorted.get((int) Math.min(sorted.size() - 1, Math.floor(q * sorted.size())));
    }

    /**
     * Two stages per call. First the pitch scale: each strong voiced frame's best scale at the
     * delay used, taken as the call's median (per-frame values scatter by about +/-4%). Then, with
     * that scale held fixed, the comb fraction averaged over the frames at each time offset; the
     * peak of that profile is the offset. Letting the scale float per frame in the second stage
     * flattens the profile and makes the offset unreliable.
     */
    static void alignCheck(List<Path> files, Path dir, Integer fixedDelay) throws IOException {
        final int[] offs = new int[25];
        for (int i = 0; i < offs.length; i++) offs[i] = (i - 12) * 20;
        double[] sumFrac = new double[offs.length];
        List<Double> callScales = new ArrayList<>();
        List<Double> frameScales = new ArrayList<>();
        double nomSum = 0, delaySum = 0, chanceSum = 0;
        int n = 0, calls = 0, callIdx = 0;
        for (Path pf : files) {
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcmPath = dir.resolve(call + ".pcm");
            if (!Files.exists(pcmPath)) continue;
            List<Frame> frames = loadParams(pf, call, callIdx++);
            markClean(frames);
            double[] pcm = readPcm(pcmPath);
            final int delay = fixedDelay != null ? fixedDelay : estimateDelay(frames, pcm);

            // clearly voiced frames louder than the call's median (first 4 harmonics voiced)
            List<Frame> voiced = new ArrayList<>();
            List<Double> en = new ArrayList<>();
            for (Frame f : frames) {
                if (!f.clean || f.L < 4 || !(f.v[0] && f.v[1] && f.v[2] && f.v[3])) continue;
                double e = 0;
                for (double a : f.m) e += a * a;
                voiced.add(f);
                en.add(e);
            }
            if (voiced.size() < 10) continue;
            List<Double> sortedE = new ArrayList<>(en);
            Collections.sort(sortedE);
            double med = sortedE.get(sortedE.size() / 2);
            List<Frame> cand = new ArrayList<>();
            for (int i = 0; i < voiced.size(); i++) if (en.get(i) >= med) cand.add(voiced.get(i));
            int stride = Math.max(1, cand.size() / Math.max(1, ALIGN_FRAMES));
            List<Frame> pick = new ArrayList<>();
            for (int i = 0; i < cand.size() && pick.size() < ALIGN_FRAMES; i += stride) pick.add(cand.get(i));

            double[] sc = new double[pick.size()];
            IntStream.range(0, pick.size()).parallel().forEach(i -> sc[i] = alignScale(pick.get(i), pcm, delay));
            List<Double> ok = new ArrayList<>();
            for (double v : sc) if (!Double.isNaN(v)) ok.add(v);
            if (ok.size() < 5) continue;
            Collections.sort(ok);
            final double S = ok.get(ok.size() / 2);
            callScales.add(S);
            frameScales.addAll(ok);

            double[][] prof = new double[pick.size()][];
            IntStream.range(0, pick.size()).parallel().forEach(i -> prof[i] = alignProfile(pick.get(i), pcm, delay, offs, S));
            boolean any = false;
            for (double[] r : prof) {
                if (r == null) continue;
                for (int oi = 0; oi < offs.length; oi++) sumFrac[oi] += r[oi];
                nomSum += r[offs.length];
                chanceSum += r[offs.length + 1];
                n++;
                any = true;
            }
            if (any) { calls++; delaySum += delay; }
        }
        if (n == 0) { System.out.println("no usable frames for the alignment check"); return; }

        int bi = 0;
        for (int i = 1; i < offs.length; i++) if (sumFrac[i] > sumFrac[bi]) bi = i;
        double off = offs[bi];
        if (bi > 0 && bi < offs.length - 1) {                     // parabolic refinement
            double ym = sumFrac[bi - 1], y0 = sumFrac[bi], yp = sumFrac[bi + 1];
            double den = ym - 2 * y0 + yp;
            if (den < 0) off += 20.0 * 0.5 * (ym - yp) / den;
        }
        Collections.sort(callScales);
        Collections.sort(frameScales);
        double msc = pct(callScales, 0.5);

        System.out.printf("Alignment check on %d strong voiced frames from %d calls%n", n, calls);
        System.out.printf("mean delay used: %.0f samples (estimated per call unless --delay was given)%n", delaySum / calls);
        System.out.println("comb-energy fraction (0-2.4 kHz) at the fitted pitch scale, averaged over frames, by time offset:");
        double maxv = sumFrac[bi] / n, minv = Double.MAX_VALUE;
        for (double v : sumFrac) minv = Math.min(minv, v / n);
        for (int i = 0; i < offs.length; i++) {
            double v = sumFrac[i] / n;
            int bar = maxv > minv ? (int) Math.round(40.0 * (v - minv) / (maxv - minv)) : 0;
            System.out.printf("  %+5d  %.4f %s%s%n", offs[i], v, "#".repeat(Math.max(0, bar)), i == bi ? "  <- best" : "");
        }
        System.out.printf("best time offset: %+.0f samples (relative to the delay used)%n", off);
        System.out.printf("pitch scale: median over calls %.3f (calls %.3f..%.3f, frames 10%%..90%% %.3f..%.3f)%n",
                msc, callScales.get(0), callScales.get(callScales.size() - 1), pct(frameScales, 0.1), pct(frameScales, 0.9));
        System.out.printf("comb fraction: as assumed (offset 0, scale 1.000) %.4f, at best offset with fitted scale %.4f%n",
                nomSum / n, sumFrac[bi] / n);
        double chance = chanceSum / n;
        System.out.printf("comb fraction expected from a random spectrum (chance): about %.4f%n", chance);

        System.out.println();
        boolean flagged = false;
        if (sumFrac[bi] / n < 1.5 * chance) {
            System.out.println("-> NO HARMONIC STRUCTURE FOUND: the comb fraction is at chance level at every offset and scale tried, so the"
                    + " chip audio does not have harmonics at the decoded pitches at all. The offset and scale below are not meaningful."
                    + " This is not an alignment problem. Run --pcm-check.");
            return;
        }
        if (sumFrac[bi] / n < 2.2 * chance) {
            flagged = true;
            System.out.printf("-> WEAK harmonic structure: the comb fraction is only %.1f x chance (clean harmonic audio gives about 3x)."
                    + " Alignment, pitch or the audio itself is off for most frames, so run --pcm-check before trusting the"
                    + " offset and scale below.%n", sumFrac[bi] / n / chance);
        }
        if (Math.abs(off) >= 30) {
            flagged = true;
            System.out.printf("-> time: the chip audio lines up %+.0f samples from where the delay used puts it (positive = later,"
                    + " negative = earlier). If every call has the same delay, rerun with --delay %.0f.%n", off, delaySum / calls + off);
        }
        if (Math.abs(msc - 1.0) >= 0.006) {
            flagged = true;
            System.out.printf("-> pitch: rerun with --pitch-scale %.3f.%n", msc);
        }
        if (!flagged) System.out.println("-> offset and scale are both as assumed; alignment is not the problem.");
    }

    // ------------------------------------------------------------- compare

    /**
     * Runs the STFT and least-squares extractors on the same frames of your real chip audio and
     * prints how their amplitudes differ by frequency band (LS minus STFT, per-frame level
     * matched on the harmonics both kept). If both measure the same thing the means sit near
     * 0 dB with a small spread in every band; where they do not, one of them is wrong there.
     */
    static void compareExtractors(List<Path> files, Path dir, Integer fixedDelay) throws IOException {
        final boolean saved = USE_LS;
        double[] s = new double[8], s2 = new double[8];
        long[] n = new long[8];
        long framesBoth = 0;
        int callIdx = 0;
        for (Path pf : files) {
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcmPath = dir.resolve(call + ".pcm");
            if (!Files.exists(pcmPath)) continue;
            List<Frame> frames = loadParams(pf, call, callIdx++);
            markClean(frames);
            double[] pcm = readPcm(pcmPath);
            int delay = fixedDelay != null ? fixedDelay : estimateDelay(frames, pcm);

            USE_LS = false;
            extractAll(frames, pcm, delay);
            int nf = frames.size();
            double[][] a = new double[nf][];
            boolean[][] ua = new boolean[nf][];
            for (int i = 0; i < nf; i++) { a[i] = frames.get(i).target; ua[i] = frames.get(i).used; }
            USE_LS = true;
            extractAll(frames, pcm, delay);

            for (int i = 0; i < nf; i++) {
                Frame f = frames.get(i);
                if (a[i] == null || f.target == null) continue;
                double ea = 0, eb = 0;
                int c = 0;
                boolean[] both = new boolean[f.L];
                for (int l = 0; l < f.L; l++) {
                    both[l] = ua[i][l] && f.used[l];
                    if (both[l]) { ea += a[i][l] * a[i][l]; eb += f.target[l] * f.target[l]; c++; }
                }
                if (c < 4 || eb <= 0) continue;
                double sc = Math.sqrt(ea / eb);
                framesBoth++;
                for (int l = 0; l < f.L; l++) {
                    if (!both[l]) continue;
                    double d = db(f.target[l] * sc) - db(a[i][l]);
                    int band = Math.min(7, (int) ((l + 1) * f.f0Hz() / 500.0));
                    s[band] += d; s2[band] += d * d; n[band]++;
                }
            }
            System.out.printf("%s done (%d frames compared so far)%n", call, framesBoth);
        }
        USE_LS = saved;
        System.out.printf("%nLS minus STFT amplitude, dB, harmonics both kept (%d frames, delay estimated per call)%n", framesBoth);
        System.out.println("band            mean   rms-diff      n");
        double tot2 = 0;
        long totn = 0;
        for (int b = 0; b < 8; b++) {
            tot2 += s2[b];
            totn += n[b];
            if (n[b] == 0) { System.out.printf("%4d-%4dHz     (none)%n", b * 500, (b + 1) * 500); continue; }
            System.out.printf("%4d-%4dHz  %+7.2f   %8.2f   %7d%n", b * 500, (b + 1) * 500,
                    s[b] / n[b], Math.sqrt(s2[b] / n[b]), n[b]);
        }
        if (totn > 0) System.out.printf("overall rms difference %.2f dB%n", Math.sqrt(tot2 / totn));
        System.out.println(gateSummary() + " (counts both passes)");
        System.out.println("Interpretation: near 0 dB mean and a small rms in every band means the two agree.");
        System.out.println("A mean that drifts with frequency, or a large rms, means LS is not trustworthy here.");
    }

    // ------------------------------------------------------------- self-test

    /**
     * Synthesizes audio from the decoded parameters with a deliberately different model from the
     * LS fit: each frame has a constant pitch, phases stay continuous, and neighboring frames are
     * crossfaded over 80 samples. No enhancement is applied, so the true gain is 0 dB.
     */
    static double[] synthesize(List<Frame> frames) {
        final int XF = 40;
        int maxIdx = 0;
        for (Frame f : frames) maxIdx = Math.max(maxIdx, f.idx);
        double[] pcm = new double[(maxIdx + 2) * FRAME + 2 * XF];
        Random rnd = new Random(12345);
        double[] ph = new double[256];
        int prevIdx = Integer.MIN_VALUE;
        for (Frame f : frames) {
            if (f.idx != prevIdx + 1) for (int l = 0; l < ph.length; l++) ph[l] = rnd.nextDouble() * 2 * Math.PI;
            prevIdx = f.idx;
            int start = f.idx * FRAME;
            for (int s = -XF; s < FRAME + XF; s++) {
                int t = start + s;
                if (t < 0 || t >= pcm.length) continue;
                double wl = Math.min(1.0, Math.max(0.0, (s + XF) / (2.0 * XF)));
                double wr = Math.min(1.0, Math.max(0.0, (FRAME + XF - s) / (2.0 * XF)));
                double acc = 0;
                for (int l = 1; l <= f.L; l++) acc += f.m[l - 1] * Math.cos(ph[l] + l * f.w0 * s);
                pcm[t] += Math.min(wl, wr) * acc;
            }
            for (int l = 1; l <= f.L && l < ph.length; l++) ph[l] += l * f.w0 * FRAME;
        }
        return pcm;
    }

    static void selfTest(List<Path> files) throws IOException {
        System.out.println("Self-test: synthesizing audio from your decoded parameters with NO enhancement,");
        System.out.println("then measuring it back. The true gain is 0 dB, so the error below is the floor");
        System.out.println("of the measurement alone under your real pitch and amplitude dynamics.");
        final boolean saved = USE_LS;
        String[] names = {"STFT peak (old)", "least squares (new)"};
        double[] sse = new double[2], bias = new double[2];
        long[] cnt = new long[2], nUse = new long[2];
        double r2sum = 0;
        long clean = 0;
        int calls = 0;
        for (Path pf : files) {
            String name = pf.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            List<Frame> frames = loadParams(pf, call, calls++);
            if (frames.size() > 2000) frames = new ArrayList<>(frames.subList(0, 2000));
            markClean(frames);
            for (Frame f : frames) if (f.clean) clean++;
            double[] pcm = synthesize(frames);
            for (int mth = 0; mth < 2; mth++) {
                USE_LS = mth == 1;
                extractAll(frames, pcm, 0);
                List<Frame> use = new ArrayList<>();
                for (Frame f : frames) if (f.target != null) use.add(f);
                if (mth == 1) for (Frame f : use) r2sum += f.r2;
                double[] r = evaluate(use, fr -> fr.m);
                if (r[2] > 0) {
                    sse[mth] += r[0] * r[0] * r[2];
                    bias[mth] += r[1] * r[2];
                    cnt[mth] += (long) r[2];
                }
                nUse[mth] += use.size();
            }
        }
        USE_LS = saved;
        System.out.printf("%n%d clean frames in %d calls (first 2000 frames of each)%n", clean, calls);
        System.out.println("method                  usable    RMS error(dB)   bias(dB)");
        for (int mth = 0; mth < 2; mth++) {
            if (cnt[mth] == 0) { System.out.printf("%-22s  none%n", names[mth]); continue; }
            System.out.printf("%-22s %7d    %9.3f     %8.3f%n", names[mth], nUse[mth],
                    Math.sqrt(sse[mth] / cnt[mth]), bias[mth] / cnt[mth]);
        }
        if (nUse[1] > 0) System.out.printf("least-squares mean fit R2 %.3f%n", r2sum / nUse[1]);
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
        double[] hs = new double[8], hs2 = new double[8];
        long[] hn = new long[8];
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
                int ab = Math.min(7, (int) ((i + 1) * f.f0Hz() / 500.0));
                hs[ab] += g; hs2[ab] += g * g; hn[ab]++;
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
        System.out.println("by absolute harmonic frequency (a fixed output filter shows up here, not in l/L):");
        for (int i = 0; i < 8; i++) printRow(String.format("  %d-%dHz", i * 500, (i + 1) * 500), hs[i], hs2[i], hn[i]);
        System.out.println("by harmonic count L:");
        String[] lLab = {"L<=14", "15-24", "25-34", "35-44", "45+"};
        for (int i = 0; i < 5; i++) printRow("  " + lLab[i], ls[i], ls2[i], ln[i]);
        System.out.println("by fundamental f0:");
        String[] fLab = {"<100Hz", "100-150", "150-200", "200-300", "300Hz+"};
        for (int i = 0; i < 5; i++) printRow("  " + fLab[i], fs[i], fs2[i], fn[i]);
    }

    static void printRow(String label, double s, double s2, long n) {
        if (n == 0) { System.out.printf("%-12s      (none)%n", label); return; }
        double mean = s / n, sd = Math.sqrt(Math.max(0, s2 / n - mean * mean));
        System.out.printf("%-12s %+6.2f +/- %4.2f   n=%d%n", label, mean, sd, n);
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
            w.println("call,frame,l,L,f0Hz,harmHz,pos,mDb,mPrevDb,mNextDb,gDb");
            for (Frame f : frames) {
                for (int i = 0; i < f.L; i++) {
                    if (!f.used[i]) continue;
                    double prev = f.m[Math.max(0, i - 1)], next = f.m[Math.min(f.L - 1, i + 1)];
                    w.printf("%s,%d,%d,%d,%.2f,%.1f,%.4f,%.3f,%.3f,%.3f,%.3f%n",
                            f.call, f.idx, i + 1, f.L, f.f0Hz(), (i + 1) * f.f0Hz(), (i + 1.0) / f.L,
                            db(f.m[i]), db(prev), db(next), db(f.target[i] / f.m[i]));
                }
            }
        }
    }
}
