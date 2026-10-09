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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dry run without hardware: writes a fake chip.pcm by decoding the probe frames with JMBE, then applying a known
 * output delay, gain, optional output filter, 16-bit rounding and a little noise. Running the response analyzers on it
 * should recover the delay and level, which validates the pipeline (encoder, extraction, alignment).
 *
 * usage: java analysis.probe.ProbeSimulator --dir DIR [--delay 37] [--gain-db 2.4] [--noise-db -80] [--no-enhance]
 *            [--shelf-db 0]
 *
 *   --shelf-db    the fake chip's output passes through a high-shelf filter of this gain at 3 kHz (RBJ biquad, slope
 *               1): an output filter after synthesis, which affects voice, noise and tones alike (a check for
 *               ProbeResponseAnalyzer)
 *   --no-enhance  the fake chip skips the spectral amplitude enhancement: a decoder that differs from JMBE in a
 *               spectrum-dependent step
 */
public final class ProbeSimulator
{
    /** RBJ cookbook high shelf (slope 1) at fc, gain dB, 8 kHz sampling. */
    static double[] highShelf(double[] x, double fc, double gainDb)
    {
        double A = Math.pow(10, gainDb / 40);
        double w = 2 * Math.PI * fc / 8000.0;
        double cw = Math.cos(w);
        double alpha = Math.sin(w) / 2 * Math.sqrt(2);
        double sa = 2 * Math.sqrt(A) * alpha;
        double b0 = A * ((A + 1) + (A - 1) * cw + sa);
        double b1 = -2 * A * ((A - 1) + (A + 1) * cw);
        double b2 = A * ((A + 1) + (A - 1) * cw - sa);
        double a0 = (A + 1) - (A - 1) * cw + sa;
        double a1 = 2 * ((A - 1) - (A + 1) * cw);
        double a2 = (A + 1) - (A - 1) * cw - sa;
        double[] y = new double[x.length];
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for(int n = 0; n < x.length; n++)
        {
            double v = (b0 * x[n] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
            x2 = x1;
            x1 = x[n];
            y2 = y1;
            y1 = v;
            y[n] = v;
        }
        return y;
    }

    public static void main(String[] args) throws Exception
    {
        Path dir = null;
        int delay = 37;
        double gainDb = 2.4;
        double noiseDbfs = -80;
        boolean enhance = true;
        double shelfDb = 0;
        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--dir" -> dir = Paths.get(args[++i]);
                case "--delay" -> delay = Integer.parseInt(args[++i]);
                case "--gain-db" -> gainDb = Double.parseDouble(args[++i]);
                case "--no-enhance" -> enhance = false;
                case "--shelf-db" -> shelfDb = Double.parseDouble(args[++i]);
                case "--noise-db" -> noiseDbfs = Double.parseDouble(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if(dir == null)
        {
            System.err.println("usage: ProbeSimulator --dir DIR [--delay 37] [--gain-db 2.4] [--noise-db -80] [--no-enhance] [--shelf-db 0]");
            System.exit(1);
        }

        Random random = new Random(7);
        List<byte[]> frames = ProbeSupport.readFrames(dir.resolve("frames.hex"));
        // A different phase seed from the analyzer's JMBE reference, as a real chip would have its own phases
        // Group by group from a reset decoder, as ProbeChipRunner has the chip decode each group
        ProbePlan plan = ProbePlan.read(dir);
        double[] pcm = new double[frames.size() * ProbeSupport.FRAME];
        for(int g = 0; g < plan.baselines.size(); g++)
        {
            int start = ProbeChipRunner.groupStart(plan, g);
            int end = g + 1 < plan.baselines.size() ? ProbeChipRunner.groupStart(plan, g + 1) : frames.size();
            double[] x = ProbeSupport.synthesize(frames.subList(start, end), enhance, 999L + g);
            System.arraycopy(x, 0, pcm, start * ProbeSupport.FRAME, x.length);
        }
        if(shelfDb != 0)
        {
            pcm = highShelf(pcm, 3000.0, shelfDb);
            System.out.printf(Locale.ROOT, "fake chip output high shelf %+.1f dB at 3 kHz%n", shelfDb);
        }

        double scale = Math.pow(10, gainDb / 20);
        double noise = 32768 * Math.pow(10, noiseDbfs / 20);
        double[] out = new double[pcm.length];
        for(int i = 0; i < out.length; i++)
        {
            int k = i - delay;
            double v = (k >= 0 && k < pcm.length ? pcm[k] * scale : 0) + noise * random.nextGaussian();
            out[i] = Math.round(v);
        }
        ProbeSupport.writePcmBigEndian(out, dir.resolve("chip.pcm"));
        System.out.printf(Locale.ROOT, "wrote simulated chip.pcm (delay %d, %+.1f dB)%n", delay, gainDb);
        if(!enhance)
        {
            System.out.println("fake chip skips the enhancement (--no-enhance)");
        }
    }
}
