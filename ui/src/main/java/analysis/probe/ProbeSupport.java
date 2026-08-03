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

package analysis.probe;

import jmbe.codec.ambe.AMBEFrame;
import jmbe.codec.ambe.AMBEModelParameters;
import jmbe.codec.ambe.AMBESynthesizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared pieces of the hardware probe experiment: JMBE decode/synthesis of a frame list, PCM I/O, and the
 * fixed-pitch least-squares harmonic amplitude extractor that is applied identically to chip and JMBE audio.
 */
public final class ProbeSupport
{
    public static final int FRAME = 160;
    public static final double LOG2_TO_DB = 20.0 * Math.log10(2.0);

    private ProbeSupport()
    {
    }

    /** Decodes the frames with JMBE (parameters only, no synthesis), starting from a reset decoder. */
    public static List<AMBEModelParameters> decode(List<byte[]> frames)
    {
        List<AMBEModelParameters> out = new ArrayList<>(frames.size());
        AMBEModelParameters previous = new AMBEModelParameters();
        for(byte[] frame : frames)
        {
            AMBEModelParameters p = new AMBEFrame(frame).getVoiceParameters(previous);
            out.add(p);
            previous = p;
        }
        return out;
    }

    /**
     * Synthesizes the frames with JMBE, AGC off, returning samples in 16-bit units (as the chip outputs). The phase
     * generator is seeded, so the same frames always give the same audio; otherwise JMBE's random initial and
     * upper-harmonic phases move each harmonic's measured amplitude by up to ~1 dB from run to run, which turns
     * any fit that re-synthesizes into a noisy objective.
     */
    public static double[] synthesize(List<byte[]> frames, boolean enhance)
    {
        return synthesize(frames, enhance, 12345L);
    }

    public static double[] synthesize(List<byte[]> frames, boolean enhance, long seed)
    {
        AMBESynthesizer synthesizer = new AMBESynthesizer();
        synthesizer.setRandomSeed(seed);
        synthesizer.reset();
        synthesizer.setAGC(false);
        synthesizer.setEnhanceSpectralAmplitudes(enhance);
        double[] pcm = new double[frames.size() * FRAME];
        int offset = 0;
        for(byte[] frame : frames)
        {
            float[] audio = synthesizer.getAudio(new AMBEFrame(frame));
            for(int i = 0; i < FRAME; i++)
            {
                pcm[offset + i] = audio[i] * 32767.0;
            }
            offset += FRAME;
        }
        return pcm;
    }

    public static List<byte[]> readFrames(Path hexFile) throws IOException
    {
        List<byte[]> frames = new ArrayList<>();
        for(String line : Files.readAllLines(hexFile))
        {
            line = line.trim();
            if(!line.isEmpty() && !line.startsWith("#"))
            {
                frames.add(AmbeFrameEncoder.fromHex(line));
            }
        }
        return frames;
    }

    public static double[] readPcm(Path file, Boolean bigEndian) throws IOException
    {
        byte[] raw = Files.readAllBytes(file);
        if(bigEndian == null)
        {
            // The wrong byte order turns the low byte into the high byte: near full-scale noise. So the right order is
            // the one with less energy. (A smoothness test picked the wrong order on a plan full of 3 kHz tones.)
            bigEndian = energy(raw, true) < energy(raw, false);
        }
        return pcm16(raw, bigEndian);
    }

    static double[] pcm16(byte[] raw, boolean bigEndian)
    {
        double[] out = new double[raw.length / 2];
        for(int i = 0; i < out.length; i++)
        {
            int hi = bigEndian ? raw[2 * i] : raw[2 * i + 1];
            int lo = bigEndian ? raw[2 * i + 1] : raw[2 * i];
            out[i] = (short)((hi << 8) | (lo & 0xFF));
        }
        return out;
    }

    /** Mean square of the samples read in the given byte order. */
    private static double energy(byte[] raw, boolean bigEndian)
    {
        double[] x = pcm16(raw, bigEndian);
        double e = 0;
        for(double v : x)
        {
            e += v * v;
        }
        return e / Math.max(x.length, 1);
    }

    /** First-difference to signal energy ratio; the correct byte order gives the smoother (lower) value. */
    private static double smoothness(byte[] raw, boolean bigEndian)
    {
        double[] x = pcm16(raw, bigEndian);
        double d = 0, e = 1e-9;
        for(int i = 1; i < x.length; i++)
        {
            d += (x[i] - x[i - 1]) * (x[i] - x[i - 1]);
            e += x[i] * x[i];
        }
        return d / e;
    }

    public static void writePcmBigEndian(double[] pcm, Path file) throws IOException
    {
        byte[] raw = new byte[pcm.length * 2];
        for(int i = 0; i < pcm.length; i++)
        {
            int v = (int)Math.max(-32768, Math.min(32767, Math.round(pcm[i])));
            raw[2 * i] = (byte)(v >> 8);
            raw[2 * i + 1] = (byte)v;
        }
        Files.write(file, raw);
    }

    /**
     * Least-squares fit of a DC term plus cos/sin pairs at l * w0 (l = 1..L) over a fixed-length window. The Gram
     * matrix depends only on (w0, L, N) because each window uses its own local time origin, so it is inverted once
     * and every frame costs one projection. Exact frequencies make windowing unnecessary for stationary input.
     */
    public static final class HarmonicFit
    {
        final int L;
        final int N;
        final int M;
        final double[][] basis;   // [M][N]
        final double[][] inverse; // [M][M]

        public HarmonicFit(double w0, int L, int N)
        {
            this.L = L;
            this.N = N;
            this.M = 2 * L + 1;
            basis = new double[M][N];
            for(int n = 0; n < N; n++)
            {
                double t = n - (N - 1) / 2.0;
                basis[0][n] = 1.0;
                for(int l = 1; l <= L; l++)
                {
                    basis[2 * l - 1][n] = Math.cos(w0 * l * t);
                    basis[2 * l][n] = Math.sin(w0 * l * t);
                }
            }
            double[][] gram = new double[M][M];
            for(int i = 0; i < M; i++)
            {
                for(int j = i; j < M; j++)
                {
                    double s = 0;
                    for(int n = 0; n < N; n++)
                    {
                        s += basis[i][n] * basis[j][n];
                    }
                    gram[i][j] = s;
                    gram[j][i] = s;
                }
            }
            inverse = invert(gram);
        }

        /**
         * @param x signal
         * @param start first sample of the window (samples outside x count as zero)
         * @param amplitudes output, amplitudes[l] for l = 1..L (length L + 1)
         * @return residual RMS of the fit
         */
        public double fit(double[] x, int start, double[] amplitudes)
        {
            double[] proj = new double[M];
            double energy = 0;
            for(int n = 0; n < N; n++)
            {
                int k = start + n;
                double v = (k >= 0 && k < x.length) ? x[k] : 0.0;
                energy += v * v;
                for(int i = 0; i < M; i++)
                {
                    proj[i] += basis[i][n] * v;
                }
            }
            double[] c = new double[M];
            double explained = 0;
            for(int i = 0; i < M; i++)
            {
                double s = 0;
                for(int j = 0; j < M; j++)
                {
                    s += inverse[i][j] * proj[j];
                }
                c[i] = s;
                explained += s * proj[i];
            }
            for(int l = 1; l <= L; l++)
            {
                amplitudes[l] = Math.hypot(c[2 * l - 1], c[2 * l]);
            }
            return Math.sqrt(Math.max(energy - explained, 0) / N);
        }

        /** Fraction of window energy explained by the harmonic model. */
        public double explainedFraction(double[] x, int start)
        {
            double[] a = new double[L + 1];
            double r = fit(x, start, a);
            double e = 0;
            for(int n = 0; n < N; n++)
            {
                int k = start + n;
                double v = (k >= 0 && k < x.length) ? x[k] : 0.0;
                e += v * v;
            }
            return e <= 0 ? 0 : 1.0 - (r * r * N) / e;
        }
    }

    static double[][] invert(double[][] a)
    {
        int n = a.length;
        double[][] m = new double[n][2 * n];
        for(int i = 0; i < n; i++)
        {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n + i] = 1.0;
        }
        for(int c = 0; c < n; c++)
        {
            int p = c;
            for(int r = c + 1; r < n; r++)
            {
                if(Math.abs(m[r][c]) > Math.abs(m[p][c]))
                {
                    p = r;
                }
            }
            double[] t = m[c];
            m[c] = m[p];
            m[p] = t;
            double d = m[c][c];
            if(Math.abs(d) < 1e-12)
            {
                throw new IllegalStateException("Singular matrix in harmonic fit");
            }
            for(int j = 0; j < 2 * n; j++)
            {
                m[c][j] /= d;
            }
            for(int r = 0; r < n; r++)
            {
                if(r != c && m[r][c] != 0)
                {
                    double f = m[r][c];
                    for(int j = 0; j < 2 * n; j++)
                    {
                        m[r][j] -= f * m[c][j];
                    }
                }
            }
        }
        double[][] out = new double[n][n];
        for(int i = 0; i < n; i++)
        {
            System.arraycopy(m[i], n, out[i], 0, n);
        }
        return out;
    }

    /** Pearson correlation of two equal-length series. */
    public static double pearson(double[] x, double[] y)
    {
        double mx = java.util.Arrays.stream(x).average().orElse(0), my = java.util.Arrays.stream(y).average().orElse(0);
        double sxy = 0, sxx = 0, syy = 0;
        for(int i = 0; i < x.length; i++)
        {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        return sxy / Math.sqrt(sxx * syy + 1e-30);
    }

    public static double db(double amplitude)
    {
        return 20.0 * Math.log10(Math.max(amplitude, 1e-9));
    }
}
