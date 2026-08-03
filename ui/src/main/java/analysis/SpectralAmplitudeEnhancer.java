package analysis;

/**
 * Spectral amplitude enhancement for IMBE / AMBE voice frames.
 *
 * Conventions:
 *   m[i]  = decoded spectral amplitude of harmonic l = i + 1  (linear, non-negative)
 *   w0    = fundamental frequency in radians/sample (2*PI*f0/fs, fs = 8000 Hz)
 *
 * Neither method mutates its input; both return a new array of the same length.
 */
public final class SpectralAmplitudeEnhancer {

    private static final double EPS = 1e-12;
    private static final double MAX_GAIN = 1.2;
    private static final double MIN_GAIN = 0.5;

    private SpectralAmplitudeEnhancer() {}

    /**
     * IMBE-style enhancement (TIA-102.BABA / mbelib style).
     *
     * A first-order envelope model is fit from R0 = sum(M^2) and
     * R1 = sum(M^2 * cos(w0*l)). Each harmonic above L/8 is weighted by that
     * model, the weight is clamped to [0.5, 1.2], and total energy is restored.
     */
    public static double[] enhance(double[] m, double w0) {
        final int L = m.length;
        double[] out = m.clone();
        if (L == 0 || w0 <= 0) return out;

        double r0 = 0, r1 = 0;
        for (int l = 1; l <= L; l++) {
            double p = m[l - 1] * m[l - 1];
            r0 += p;
            r1 += p * Math.cos(w0 * l);
        }
        double denom = w0 * r0 * (r0 * r0 - r1 * r1);
        if (r0 < EPS || denom < EPS) return out;

        double num0 = r0 * r0 + r1 * r1;
        double k = 0.96 * Math.PI;

        for (int l = 1; l <= L; l++) {
            if (8 * l <= L) continue;                       // low harmonics untouched
            double a = m[l - 1];
            double t = k * (num0 - 2.0 * r0 * r1 * Math.cos(w0 * l)) / denom;
            double w = Math.sqrt(a) * Math.pow(Math.max(t, 0.0), 0.25);
            if (w > MAX_GAIN)      out[l - 1] = a * MAX_GAIN;
            else if (w < MIN_GAIN) out[l - 1] = a * MIN_GAIN;
            else                   out[l - 1] = a * w;
        }
        return normalizeEnergy(out, r0);
    }

    /**
     * Higher-order envelope postfilter (offline-friendly generalization).
     *
     * Treats M^2 sampled at w0*l as a power spectrum, fits an all-pole model of
     * the given order via autocorrelation + Levinson-Durbin, then applies the
     * classic formant postfilter |A(z/gammaN)| / |A(z/gammaD)| at each harmonic
     * (gammaN < gammaD sharpens peaks relative to valleys).
     *
     * @param order    LPC order (e.g. 4-10); clamped to L-1
     * @param gammaN   numerator bandwidth expansion, e.g. 0.6
     * @param gammaD   denominator bandwidth expansion, e.g. 0.8
     * @param strength exponent on the gain, 0 = off, 1 = full
     */
    public static double[] enhanceHighOrder(double[] m, double w0, int order,
                                            double gammaN, double gammaD,
                                            double strength) {
        final int L = m.length;
        double[] out = m.clone();
        order = Math.min(order, L - 1);
        if (L < 2 || order < 1 || w0 <= 0) return out;

        // Autocorrelation of the power spectrum sampled at the harmonics.
        double[] r = new double[order + 1];
        for (int k = 0; k <= order; k++) {
            double s = 0;
            for (int l = 1; l <= L; l++) {
                double p = m[l - 1] * m[l - 1];
                s += p * Math.cos(k * w0 * l);
            }
            r[k] = s;
        }
        if (r[0] < EPS) return out;
        final double e0 = r[0];
        r[0] *= 1.0001;                                     // white-noise correction

        double[] a = levinson(r, order);                    // a[0] = 1
        if (a == null) return out;

        for (int l = 1; l <= L; l++) {
            double w = w0 * l;
            double g = magA(a, gammaN, w) / Math.max(magA(a, gammaD, w), EPS);
            g = Math.pow(g, strength);
            g = Math.min(Math.max(g, MIN_GAIN), MAX_GAIN);
            out[l - 1] = m[l - 1] * g;
        }
        return normalizeEnergy(out, e0);
    }

    // ---------------------------------------------------------------- helpers

    /** Rescale so sum(out^2) equals the original energy. */
    private static double[] normalizeEnergy(double[] out, double targetEnergy) {
        double s = 0;
        for (double v : out) s += v * v;
        if (s < EPS) return out;
        double gamma = Math.sqrt(targetEnergy / s);
        for (int i = 0; i < out.length; i++) out[i] *= gamma;
        return out;
    }

    /** Levinson-Durbin. Returns a[0..order] with a[0] = 1, or null if unstable. */
    private static double[] levinson(double[] r, int order) {
        double[] a = new double[order + 1];
        double[] tmp = new double[order + 1];
        a[0] = 1.0;
        double err = r[0];
        for (int i = 1; i <= order; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) acc += a[j] * r[i - j];
            double refl = -acc / err;
            if (Math.abs(refl) >= 1.0) return null;
            System.arraycopy(a, 0, tmp, 0, i);
            for (int j = 1; j < i; j++) a[j] = tmp[j] + refl * tmp[i - j];
            a[i] = refl;
            err *= (1.0 - refl * refl);
            if (err < EPS) return null;
        }
        return a;
    }

    /** |A(e^{jw} / gamma)| = |sum a[k] * gamma^k * e^{-jkw}|. */
    private static double magA(double[] a, double gamma, double w) {
        double re = 0, im = 0, gk = 1.0;
        for (int k = 0; k < a.length; k++) {
            re += a[k] * gk * Math.cos(k * w);
            im -= a[k] * gk * Math.sin(k * w);
            gk *= gamma;
        }
        return Math.hypot(re, im);
    }
}
