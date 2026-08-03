package analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds LearnedEnhancer the neighboring frames it needs (up to two before and two after, only if the frame numbers are
 * consecutive) and adapts JMBE's amplitude layout.
 *
 * JMBE layout: float[L+1], index 0 unused, harmonics 1..L (MBEModelParameters.getSpectralAmplitudes()). The enhancer
 * works on zero-based double[L]; use toZeroBased / toJmbe to convert.
 *
 * Offline / batch (a whole call at once):
 *   double[][] out = FrameSequenceEnhancer.enhanceCall(net, frameNumbers, m, voiced, w0);
 *
 * Streaming (decoder loop): output lags the input by net.ctx frames.
 *   FrameSequenceEnhancer.Stream s = new FrameSequenceEnhancer.Stream(net);
 *   for each decoded frame:  for (Output o : s.push(frameNumber, m, voiced, w0)) play(o);
 *   at end of call:          for (Output o : s.flush()) play(o);
 * A skipped frame number (erasure, dropped frame) is treated as a gap: frames across it are not used as neighbors.
 * w0 is in radians per sample, as in the params files.
 */
public final class FrameSequenceEnhancer {

    private FrameSequenceEnhancer() {}

    public static final class Output {
        public final int frameNumber;
        public final double[] amplitudes;   // enhanced, zero-based, same length as the input
        Output(int frameNumber, double[] amplitudes) {
            this.frameNumber = frameNumber;
            this.amplitudes = amplitudes;
        }
    }

    public static double[] toZeroBased(float[] jmbe) {
        double[] d = new double[jmbe.length - 1];
        for (int i = 0; i < d.length; i++) d[i] = jmbe[i + 1];
        return d;
    }

    /** Writes enhanced amplitudes into a JMBE-layout array (index 0 copied from template[0]). */
    public static float[] toJmbe(double[] enhanced, float[] template) {
        float[] f = new float[enhanced.length + 1];
        f[0] = template.length > 0 ? template[0] : 0f;
        for (int i = 0; i < enhanced.length; i++) f[i + 1] = (float) enhanced[i];
        return f;
    }

    private static final int[] OFF = {-2, -1, 1, 2};

    private static double[] enhanceAt(LearnedEnhancer.Mlp net, int p, int[] idx, double[][] m, boolean[][] v, double[] w0,
                                      int from, int to) {
        double[][] nm = new double[4][];
        double[] nw = new double[4];
        for (int k = 0; k < 4; k++) {
            int j = p + OFF[k];
            if (j < from || j >= to || idx[j] != idx[p] + OFF[k]) continue;     // outside the buffer, or a gap
            nm[k] = m[j];
            nw[k] = w0[j];
        }
        return LearnedEnhancer.enhance(net, m[p], v[p], w0[p], nm, nw);
    }

    /** Whole-call enhancement. Frames must be in order; frameNumber[t] only needs to be consecutive where frames are. */
    public static double[][] enhanceCall(LearnedEnhancer.Mlp net, int[] frameNumber, double[][] m, boolean[][] v, double[] w0) {
        double[][] out = new double[m.length][];
        for (int t = 0; t < m.length; t++) out[t] = enhanceAt(net, t, frameNumber, m, v, w0, 0, m.length);
        return out;
    }

    /** Frame-at-a-time version of enhanceCall with net.ctx frames of delay; gives identical output. */
    public static final class Stream {
        private final LearnedEnhancer.Mlp net;
        private final List<int[]> idx = new ArrayList<>();
        private final List<double[]> m = new ArrayList<>();
        private final List<boolean[]> v = new ArrayList<>();
        private final List<Double> w0 = new ArrayList<>();
        private int next;   // buffer position of the next frame to emit

        public Stream(LearnedEnhancer.Mlp net) { this.net = net; }

        public List<Output> push(int frameNumber, double[] amplitudes, boolean[] voiced, double w0Rad) {
            idx.add(new int[]{frameNumber});
            m.add(amplitudes);
            v.add(voiced);
            w0.add(w0Rad);
            List<Output> out = new ArrayList<>();
            while (m.size() - 1 - next >= net.ctx) out.add(emit());
            while (next > 2) { idx.remove(0); m.remove(0); v.remove(0); w0.remove(0); next--; }
            return out;
        }

        /** Emits the frames still waiting for look-ahead, and resets the stream for the next call. */
        public List<Output> flush() {
            List<Output> out = new ArrayList<>();
            while (next < m.size()) out.add(emit());
            idx.clear(); m.clear(); v.clear(); w0.clear();
            next = 0;
            return out;
        }

        private Output emit() {
            int n = m.size();
            int[] fn = new int[n];
            double[][] mm = new double[n][];
            boolean[][] vv = new boolean[n][];
            double[] ww = new double[n];
            for (int i = 0; i < n; i++) { fn[i] = idx.get(i)[0]; mm[i] = m.get(i); vv[i] = v.get(i); ww[i] = w0.get(i); }
            double[] e = enhanceAt(net, next, fn, mm, vv, ww, 0, n);
            return new Output(fn[next++], e);
        }
    }
}
