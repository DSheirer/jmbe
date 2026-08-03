package analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Learned spectral-amplitude enhancement for AMBE+2 frames.
 *
 * A small MLP predicts, for each voiced harmonic, the dB gain the reference (DVSI) decoder
 * applied. Inputs combine a wide local spectral window, pitch / harmonic-count context, the
 * IMBE-style and higher-order-postfilter gains as priors, and up to two neighboring frames on
 * each side (offline decoding can look ahead). Neighbor frames are lined up by frequency, so a
 * pitch change between frames is handled implicitly. Unvoiced harmonics fall back to the
 * IMBE-style gain, because the training signal only exists for voiced harmonics. Frame energy is
 * restored afterwards.
 *
 * Train with AmbeGainTrainer, then:
 *   Mlp net = LearnedEnhancer.Mlp.load(Paths.get("gain-model.txt"));
 *   double[][] nm = { m[t-2], m[t-1], m[t+1], m[t+2] };      // null where a frame does not exist
 *   double[]   nw = { w0[t-2], w0[t-1], w0[t+1], w0[t+2] };  // 0 where a frame does not exist
 *   double[] out = LearnedEnhancer.enhance(net, m, v, w0, nm, nw);
 * Only consecutive frames count as neighbors: pass null / 0 across a gap in the frame numbers.
 */
public final class LearnedEnhancer {

    public static final int NF = 27;
    private static final double FS = 8000.0;
    public static final double MIN_G = -9.0, MAX_G = 6.0;     // output clamp, dB

    /** Local-shape window half-width in harmonics, and the wider envelope window. */
    static final int SHAPE_HW = 5, WIDE_HW = 10;

    /**
     * Unvoiced harmonics have no training signal. true (the setting every evaluation used) applies the IMBE-style
     * gain to them; false leaves them unchanged. Frame energy is restored either way, so the two differ slightly.
     */
    public static boolean unvoicedImbeStyle = true;

    private LearnedEnhancer() {}

    // ---------------------------------------------------------------- features

    public static final class FrameFeatures {
        public final double[][] x;          // [harmonic][NF]
        public final double[] imbeRatio;    // linear IMBE-style gain per harmonic

        FrameFeatures(double[][] x, double[] imbeRatio) {
            this.x = x;
            this.imbeRatio = imbeRatio;
        }
    }

    static double db(double x) { return 20 * Math.log10(Math.max(x, 1e-9)); }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    static int clampIdx(int i, int n) { return Math.max(0, Math.min(n - 1, i)); }

    /** Each harmonic's dB relative to the frame's mean dB, clamped to [-40, +20]. */
    static double[] relDb(double[] m) {
        double[] d = new double[m.length];
        double mean = 0;
        for (int i = 0; i < m.length; i++) {
            d[i] = Math.max(-120.0, db(m[i]));
            mean += d[i];
        }
        mean /= m.length;
        for (int i = 0; i < m.length; i++) d[i] = clamp(d[i] - mean, -40, 20);
        return d;
    }

    /**
     * Feature layout (27):
     *   0 l/L | 1 harmonic freq/4 kHz | 2 f0/300 Hz | 3 L/56
     *   4-14  relative dB at offsets -5..+5 (/10)
     *   15    mean relative dB over +/-10 harmonics (/10) | 16 frame spectral tilt (/20)
     *   17    IMBE-style gain dB (/6) | 18 higher-order postfilter gain dB (/6)
     *   19-22 shape change vs frames t-2, t-1, t+1, t+2 at the same frequency (/10)
     *   23-26 has frame t-2, t-1, t+1, t+2
     *
     * @param nm neighbor amplitudes {t-2, t-1, t+1, t+2}, null where missing (array may be null)
     * @param nw neighbor w0 in the same order, 0 where missing (array may be null)
     */
    public static FrameFeatures features(double[] m, boolean[] v, double w0, double[][] nm, double[] nw) {
        final int L = m.length;
        double[] rel = relDb(m);
        double[][] rn = new double[4][];
        for (int k = 0; k < 4; k++)
            if (nm != null && nm[k] != null && nm[k].length > 0 && nw != null && nw[k] > 0) rn[k] = relDb(nm[k]);
        double[] imbe = SpectralAmplitudeEnhancer.enhance(m, w0);
        double[] ho = SpectralAmplitudeEnhancer.enhanceHighOrder(m, w0, 6, 0.6, 0.8, 1.0);
        double f0 = w0 * FS / (2 * Math.PI);

        // frame tilt: least-squares slope of relative dB against l/L, as total dB across the band
        double tilt = 0;
        if (L > 2) {
            double mx = 0, my = 0;
            for (int i = 0; i < L; i++) { mx += (i + 1.0) / L; my += rel[i]; }
            mx /= L;
            my /= L;
            double sxy = 0, sxx = 0;
            for (int i = 0; i < L; i++) {
                double dx = (i + 1.0) / L - mx;
                sxy += dx * (rel[i] - my);
                sxx += dx * dx;
            }
            tilt = sxx > 1e-12 ? clamp(sxy / sxx, -40, 40) : 0;
        }

        double[][] x = new double[L][NF];
        double[] imbeRatio = new double[L];
        for (int i = 0; i < L; i++) {
            double[] r = x[i];
            r[0] = (i + 1.0) / L;
            r[1] = Math.min(1.0, (i + 1) * f0 / 4000.0);
            r[2] = f0 / 300.0;
            r[3] = L / 56.0;
            for (int k = -SHAPE_HW; k <= SHAPE_HW; k++) r[4 + SHAPE_HW + k] = rel[clampIdx(i + k, L)] / 10.0;

            double wsum = 0;
            int wc = 0;
            for (int k = -WIDE_HW; k <= WIDE_HW; k++) {
                int j = i + k;
                if (j < 0 || j >= L) continue;
                wsum += rel[j];
                wc++;
            }
            r[15] = wc > 0 ? wsum / wc / 10.0 : 0;
            r[16] = tilt / 20.0;

            boolean ok = m[i] > 1e-9;
            double ri = ok ? imbe[i] / m[i] : 1.0;
            double rh = ok ? ho[i] / m[i] : 1.0;
            imbeRatio[i] = ri;
            r[17] = clamp(db(ri), -12, 12) / 6.0;
            r[18] = clamp(db(rh), -12, 12) / 6.0;

            for (int k = 0; k < 4; k++) {
                if (rn[k] == null) continue;
                r[23 + k] = 1;
                int j = (int) Math.round((i + 1.0) * w0 / nw[k]) - 1;       // same frequency in the neighbor
                if (j < 0 || j >= rn[k].length) continue;                    // outside its band: no change signal
                r[19 + k] = clamp(rn[k][j] - rel[i], -20, 20) / 10.0;
            }
        }
        return new FrameFeatures(x, imbeRatio);
    }

    // ------------------------------------------------------------- enhancement

    /** Convenience for one frame of context each side (the outer frames are treated as missing). */
    public static double[] enhance(Mlp net, double[] m, boolean[] v, double w0,
                                   double[] mPrev, double wPrev, double[] mNext, double wNext) {
        return enhance(net, m, v, w0, new double[][]{null, mPrev, mNext, null}, new double[]{0, wPrev, wNext, 0});
    }

    public static double[] enhance(Mlp net, double[] m, boolean[] v, double w0, double[][] nm, double[] nw) {
        final int L = m.length;
        if (net.ctx < 2 && nm != null) {                     // model trained with one frame of context
            nm = new double[][]{null, nm[1], nm[2], null};
        }
        FrameFeatures ff = features(m, v, w0, nm, nw);
        double[] out = new double[L];
        double e0 = 0, e1 = 0;
        for (int i = 0; i < L; i++) {
            e0 += m[i] * m[i];
            if (v[i]) {
                double g = clamp(net.predict(ff.x[i]), MIN_G, MAX_G);
                out[i] = m[i] * Math.pow(10.0, g / 20.0);
            } else {
                out[i] = unvoicedImbeStyle ? m[i] * ff.imbeRatio[i] : m[i];
            }
            e1 += out[i] * out[i];
        }
        if (e1 > 1e-12 && e0 > 1e-12) {
            double s = Math.sqrt(e0 / e1);
            for (int i = 0; i < L; i++) out[i] *= s;
        }
        return out;
    }

    // --------------------------------------------------------------------- MLP

    /** Fully connected net, tanh hidden layers, linear scalar output, with input standardization. */
    public static final class Mlp {
        public final int[] sizes;
        public final double[][][] w;   // [layer][out][in]
        public final double[][] b;     // [layer][out]
        public final double[] mean = new double[NF];
        public final double[] std = new double[NF];
        /** Frames of context each side the model was trained with (1 or 2). */
        public int ctx = 2;

        public Mlp(int[] sizes) {
            this.sizes = sizes.clone();
            int nl = sizes.length - 1;
            w = new double[nl][][];
            b = new double[nl][];
            for (int l = 0; l < nl; l++) {
                w[l] = new double[sizes[l + 1]][sizes[l]];
                b[l] = new double[sizes[l + 1]];
            }
            java.util.Arrays.fill(std, 1.0);
        }

        public double predict(double[] x) {
            double[] a = new double[x.length];
            for (int i = 0; i < x.length; i++) a[i] = (x[i] - mean[i]) / std[i];
            return forwardNormalized(a);
        }

        double forwardNormalized(double[] a) {
            int nl = w.length;
            for (int l = 0; l < nl; l++) {
                double[] out = new double[sizes[l + 1]];
                for (int o = 0; o < out.length; o++) {
                    double s = b[l][o];
                    double[] row = w[l][o];
                    for (int i = 0; i < row.length; i++) s += row[i] * a[i];
                    out[o] = (l == nl - 1) ? s : Math.tanh(s);
                }
                a = out;
            }
            return a[0];
        }

        public Mlp copy() {
            Mlp c = new Mlp(sizes);
            for (int l = 0; l < w.length; l++) {
                for (int o = 0; o < w[l].length; o++) c.w[l][o] = w[l][o].clone();
                c.b[l] = b[l].clone();
            }
            System.arraycopy(mean, 0, c.mean, 0, NF);
            System.arraycopy(std, 0, c.std, 0, NF);
            c.ctx = ctx;
            return c;
        }

        public void save(Path p) throws IOException {
            StringBuilder sb = new StringBuilder("MLP2\n").append(ctx).append('\n').append(sizes.length);
            for (int s : sizes) sb.append(' ').append(s);
            sb.append('\n');
            for (double d : mean) sb.append(d).append(' ');
            sb.append('\n');
            for (double d : std) sb.append(d).append(' ');
            sb.append('\n');
            for (int l = 0; l < w.length; l++) {
                for (double[] row : w[l]) {
                    for (double d : row) sb.append(d).append(' ');
                    sb.append('\n');
                }
                for (double d : b[l]) sb.append(d).append(' ');
                sb.append('\n');
            }
            Files.writeString(p, sb.toString());
        }

        public static Mlp load(Path p) throws IOException {
            return parse(Files.readString(p));
        }

        /** Parses the text form written by save(); used by load() and by generated model classes. */
        public static Mlp parse(String text) throws IOException {
            String[] t = text.trim().split("\\s+");
            if (t[0].equals("MLP1")) throw new IOException("old MLP1 model (21 features): retrain with the current AmbeGainTrainer");
            if (!t[0].equals("MLP2")) throw new IOException("not an MLP2 model file");
            int pos = 1;
            int ctx = Integer.parseInt(t[pos++]);
            int n = Integer.parseInt(t[pos++]);
            int[] sizes = new int[n];
            for (int i = 0; i < n; i++) sizes[i] = Integer.parseInt(t[pos++]);
            if (sizes[0] != NF || sizes[n - 1] != 1) throw new IOException("model does not match feature set");
            Mlp net = new Mlp(sizes);
            net.ctx = ctx;
            for (int i = 0; i < NF; i++) net.mean[i] = Double.parseDouble(t[pos++]);
            for (int i = 0; i < NF; i++) net.std[i] = Double.parseDouble(t[pos++]);
            for (int l = 0; l < net.w.length; l++) {
                for (double[] row : net.w[l]) for (int i = 0; i < row.length; i++) row[i] = Double.parseDouble(t[pos++]);
                for (int o = 0; o < net.b[l].length; o++) net.b[l][o] = Double.parseDouble(t[pos++]);
            }
            return net;
        }
    }
}
