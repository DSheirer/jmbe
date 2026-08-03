package analysis;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Audio-domain comparison of amplitude-enhancement variants against the DVSI chip.
 *
 * For every variant (no enhancement, IMBE-style, fitted postfilter, learned MLP) the decoded parameters are turned
 * into audio with the same sinusoidal synthesizer, and that audio is then measured with the
 * SAME STFT peak picker that measured the chip audio. The two sets of harmonic amplitudes are
 * compared after per-frame level matching, on the harmonics that passed the chip SNR gate.
 *
 * Because both sides go through one measurement pipeline, extraction error (window leakage,
 * pitch smear, neighboring-frame blending) is shared and largely cancels, which the
 * amplitude-domain numbers from AmbeGainTrainer cannot do. (A variant whose gains are exactly
 * the chip's scores near 0 dB here even though its amplitude-domain error is the extraction
 * error, which is what makes the audio-domain figure the more trustworthy ranking.)
 *
 * Usage:
 *   java AmbeAudioCompare <dir> --model gain-model.txt [--delay 80] [--calls test|all]
 *        [--max-calls N] [--pf order,gammaN,gammaD,strength] [--wav-dir out]
 *        [--pcm-endian big|little] [extraction options such as --min-snr-db]
 *
 *   --calls test   (default) only calls with callIdx % 5 == 4, which AmbeGainTrainer held out
 *   --pf           parameters of the fitted postfilter row (default 8,0.834,0.960,1.456, the fit
 *                  from the delay-80 run); use what your own trainer run printed
 *   --wav-dir      write chip.wav and one synthesized wav per variant for the first call, level
 *                  matched to the chip and time aligned, for listening
 *
 * Unvoiced harmonics are synthesized as sinusoids too (no noise model), identically for every
 * variant, and are never scored.
 */
public final class AmbeAudioCompare {

    static final int FRAME = AmbeEnhancementAnalyzer.FRAME;
    static final String[] BAND = {"0-1k", "1-2k", "2-3k", "3-3.8k"};

    interface Enh {
        double[] apply(List<AmbeEnhancementAnalyzer.Frame> fr, int i);
    }

    static final class Acc {
        double sse, bias;
        long n;
        final double[] bs = new double[4];
        final long[] bn = new long[4];

        void add(double d, double hz) {
            sse += d * d;
            bias += d;
            n++;
            int b = hz < 1000 ? 0 : hz < 2000 ? 1 : hz < 3000 ? 2 : 3;
            bs[b] += d * d;
            bn[b]++;
        }

        double rms() { return n == 0 ? Double.NaN : Math.sqrt(sse / n); }
        double mean() { return n == 0 ? Double.NaN : bias / n; }
        double bandRms(int b) { return bn[b] == 0 ? Double.NaN : Math.sqrt(bs[b] / bn[b]); }
    }

    /** Level-matches c to the chip target on the used harmonics, then accumulates the dB errors. */
    static double accumulate(AmbeEnhancementAnalyzer.Frame f, double[] c, Acc acc) {
        double ec = 0, et = 0;
        for (int i = 0; i < f.L; i++) {
            if (!f.used[i]) continue;
            ec += c[i] * c[i];
            et += f.target[i] * f.target[i];
        }
        if (ec <= 0) return Double.NaN;
        double s = Math.sqrt(et / ec), f0 = f.f0Hz();
        for (int i = 0; i < f.L; i++) {
            if (!f.used[i]) continue;
            double d = AmbeEnhancementAnalyzer.db(c[i] * s) - AmbeEnhancementAnalyzer.db(f.target[i]);
            acc.add(d, (i + 1) * f0);
        }
        return 0;
    }

    static AmbeEnhancementAnalyzer.Frame withAmplitudes(AmbeEnhancementAnalyzer.Frame f, double[] m) {
        AmbeEnhancementAnalyzer.Frame g = new AmbeEnhancementAnalyzer.Frame();
        g.call = f.call;
        g.callIdx = f.callIdx;
        g.idx = f.idx;
        g.w0 = f.w0;
        g.L = f.L;
        g.errs = f.errs;
        g.m = m;
        g.v = f.v;
        return g;
    }

    static double[] measure(AmbeEnhancementAnalyzer.Frame f, double[] synth) {
        double[][] b = AmbeEnhancementAnalyzer.STFT_BUF.get();
        double sw = AmbeEnhancementAnalyzer.spectrum(f, synth, 0, b[0], b[1]);
        if (sw <= 0) return null;
        return AmbeEnhancementAnalyzer.stftPeaks(f, b[0], b[1], sw);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: AmbeAudioCompare <dir> --model gain-model.txt [--delay N] [--calls test|all]"
                    + " [--max-calls N] [--pf order,gN,gD,strength] [--wav-dir out] [--pcm-endian big|little]");
            return;
        }
        Path dir = Paths.get(args[0]);
        String modelPath = "m1.txt";
        Integer fixedDelay = null;
        boolean testOnly = true;
        int maxCalls = Integer.MAX_VALUE;
        int pfOrder = 8;
        double[] pf = {0.834, 0.960, 1.456};
        Path wavDir = null;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--model": modelPath = args[++i]; break;
                case "--delay": fixedDelay = Integer.parseInt(args[++i]); break;
                case "--calls": testOnly = !args[++i].equalsIgnoreCase("all"); break;
                case "--max-calls": maxCalls = Integer.parseInt(args[++i]); break;
                case "--wav-dir": wavDir = Paths.get(args[++i]); break;
                case "--pf":
                    String[] t = args[++i].split(",");
                    pfOrder = Integer.parseInt(t[0].trim());
                    for (int k = 0; k < 3; k++) pf[k] = Double.parseDouble(t[k + 1].trim());
                    break;
                default:
                    if (i + 1 < args.length && AmbeEnhancementAnalyzer.applyExtractOption(args[i], args[i + 1])) i++;
                    else System.err.println("ignoring " + args[i]);
            }
        }
        final LearnedEnhancer.Mlp net = LearnedEnhancer.Mlp.load(Paths.get(modelPath));
        final int fOrder = pfOrder;
        final double[] fpf = pf;

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.params.txt")) {
            for (Path p : ds) files.add(p);
        }
        Collections.sort(files);

        final String[] names = {"no enhancement", "IMBE-style enhance()", "fitted postfilter", "learned MLP"};
        final int NV = names.length;
        Enh[] enh = new Enh[NV];
        enh[0] = (fr, i) -> fr.get(i).m;
        enh[1] = (fr, i) -> SpectralAmplitudeEnhancer.enhance(fr.get(i).m, fr.get(i).w0);
        enh[2] = (fr, i) -> SpectralAmplitudeEnhancer.enhanceHighOrder(fr.get(i).m, fr.get(i).w0, fOrder,
                fpf[0], fpf[1], fpf[2]);
        enh[3] = (fr, i) -> {
            AmbeEnhancementAnalyzer.Frame f = fr.get(i);
            double[][] nm = new double[4][];
            double[] nw = new double[4];
            int[] off = {-2, -1, 1, 2};
            for (int k = 0; k < 4; k++) {
                int j = i + off[k];
                if (j < 0 || j >= fr.size() || fr.get(j).idx != f.idx + off[k]) continue;   // gap: no neighbor
                nm[k] = fr.get(j).m;
                nw[k] = fr.get(j).w0;
            }
            return LearnedEnhancer.enhance(net, f.m, f.v, f.w0, nm, nw);
        };

        Acc[] audio = new Acc[NV], amp = new Acc[NV];
        for (int v = 0; v < NV; v++) { audio[v] = new Acc(); amp[v] = new Acc(); }
        List<List<Double>> perCall = new ArrayList<>();
        for (int v = 0; v < NV; v++) perCall.add(new ArrayList<>());

        int callIdx = 0, nCalls = 0, nFrames = 0;
        boolean wavDone = false;
        for (Path pfile : files) {
            if (nCalls >= maxCalls) break;
            String name = pfile.getFileName().toString();
            String call = name.substring(0, name.length() - ".params.txt".length());
            Path pcmPath = dir.resolve(call + ".pcm");
            if (!Files.exists(pcmPath)) continue;
            int myIdx = callIdx++;
            if (testOnly && myIdx % 5 != 4) continue;

            List<AmbeEnhancementAnalyzer.Frame> frames = AmbeEnhancementAnalyzer.loadParams(pfile, call, myIdx);
            if (frames.size() < 10) continue;
            AmbeEnhancementAnalyzer.markClean(frames);
            double[] chip = AmbeEnhancementAnalyzer.readPcm(pcmPath);
            int delay = fixedDelay != null ? fixedDelay : AmbeEnhancementAnalyzer.estimateDelay(frames, chip);
            AmbeEnhancementAnalyzer.extractAll(frames, chip, delay);
            List<AmbeEnhancementAnalyzer.Frame> use = new ArrayList<>();
            for (AmbeEnhancementAnalyzer.Frame f : frames) if (f.target != null) use.add(f);
            if (use.isEmpty()) continue;
            nCalls++;
            nFrames += use.size();

            // enhanced amplitudes for every frame of the call, per variant
            final double[][][] em = new double[NV][frames.size()][];
            for (int v = 0; v < NV; v++) for (int i = 0; i < frames.size(); i++) em[v][i] = enh[v].apply(frames, i);

            final double[][] synthAll = new double[NV][];
            final Acc[] callAudio = new Acc[NV];
            final Acc[] callAmp = new Acc[NV];
            IntStream.range(0, NV).parallel().forEach(v -> {
                List<AmbeEnhancementAnalyzer.Frame> g = new ArrayList<>(frames.size());
                for (int i = 0; i < frames.size(); i++) g.add(withAmplitudes(frames.get(i), em[v][i]));
                double[] synth = AmbeEnhancementAnalyzer.synthesize(g);
                synthAll[v] = synth;
                Acc a = new Acc(), b = new Acc();
                for (int i = 0; i < frames.size(); i++) {
                    AmbeEnhancementAnalyzer.Frame f = frames.get(i);
                    if (f.target == null) continue;
                    double[] t = measure(f, synth);
                    if (t != null) accumulate(f, t, a);
                    accumulate(f, em[v][i], b);
                }
                callAudio[v] = a;
                callAmp[v] = b;
            });
            for (int v = 0; v < NV; v++) {
                merge(audio[v], callAudio[v]);
                merge(amp[v], callAmp[v]);
                perCall.get(v).add(callAudio[v].rms());
            }
            if (nCalls % 10 == 0) System.err.printf("  %d calls done%n", nCalls);

            if (wavDir != null && !wavDone) {
                Files.createDirectories(wavDir);
                writeWav(wavDir.resolve("chip.wav"), chip, 1.0, 0);
                double rc = rms(chip, delay, chip.length);
                String[] tag = {"none", "imbe", "postfilter", "learned"};
                for (int v = 0; v < NV; v++) {
                    double rs = rms(synthAll[v], 0, synthAll[v].length);
                    writeWav(wavDir.resolve(tag[v] + ".wav"), synthAll[v], rs > 0 ? rc / rs : 1.0, delay);
                }
                System.out.println("wrote chip.wav and one wav per variant for call " + call + " to " + wavDir);
                wavDone = true;
            }
        }
        if (nCalls == 0) { System.err.println("no usable calls (try --calls all)"); return; }

        System.out.printf("%nAudio-domain comparison: %d calls (%s), %d frames, %d scored harmonics%n", nCalls,
                testOnly ? "held-out test split" : "all calls", nFrames, audio[0].n);
        System.out.println("RMS log-amplitude error vs chip, dB, voiced harmonics that passed the chip SNR gate, per-frame level matched.");
        System.out.println("audio-domain: synthesized audio measured by the same STFT as the chip audio. amplitude-domain: as AmbeGainTrainer.");
        System.out.printf("%n%-24s %9s %9s %8s   | audio-domain by band:", "variant", "audio-dom", "amp-dom", "bias");
        for (String bn : BAND) System.out.printf("%8s", bn);
        System.out.println();
        for (int v = 0; v < NV; v++) {
            System.out.printf("%-24s %9.3f %9.3f %+8.3f   | %20s", names[v], audio[v].rms(), amp[v].rms(), audio[v].mean(), "");
            for (int b = 0; b < 4; b++) System.out.printf("%8.2f", audio[v].bandRms(b));
            System.out.println();
        }

        int wins = 0, ties = 0, n = perCall.get(1).size();
        for (int c = 0; c < n; c++) {
            double d = perCall.get(3).get(c) - perCall.get(1).get(c);
            if (Math.abs(d) < 0.01) ties++;
            else if (d < 0) wins++;
        }
        System.out.printf("%nlearned MLP beats IMBE-style on %d of %d calls (%d within 0.01 dB)%n", wins, n, ties);
        System.out.printf("learned improvement over IMBE-style: %.3f dB audio-domain, %.3f dB amplitude-domain%n",
                audio[1].rms() - audio[3].rms(), amp[1].rms() - amp[3].rms());
        System.out.println("If the amplitude-domain improvement is much larger than the audio-domain one, part of it is the model");
        System.out.println("fitting the extraction's own artifacts rather than what the chip does; trust the audio-domain figure.");
    }

    static void merge(Acc into, Acc a) {
        into.sse += a.sse;
        into.bias += a.bias;
        into.n += a.n;
        for (int b = 0; b < 4; b++) { into.bs[b] += a.bs[b]; into.bn[b] += a.bn[b]; }
    }

    static double rms(double[] x, int from, int to) {
        double s = 0;
        int n = 0;
        for (int i = Math.max(0, from); i < Math.min(to, x.length); i++) { s += x[i] * x[i]; n++; }
        return n == 0 ? 0 : Math.sqrt(s / n);
    }

    /** 16-bit little-endian mono 8 kHz WAV (the standard container), samples scaled and delayed. */
    static void writeWav(Path p, double[] x, double scale, int delay) throws IOException {
        int n = x.length + delay;
        ByteBuffer b = ByteBuffer.allocate(44 + 2 * n).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + 2 * n).put("WAVE".getBytes()).put("fmt ".getBytes());
        b.putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(2 * n);
        for (int i = 0; i < n; i++) {
            double s = i < delay ? 0 : x[i - delay] * scale;
            b.putShort((short) Math.max(-32768, Math.min(32767, Math.round(s))));
        }
        Files.write(p, b.array());
    }
}
