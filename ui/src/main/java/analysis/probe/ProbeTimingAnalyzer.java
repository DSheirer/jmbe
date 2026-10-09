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

import jmbe.codec.ambe.ambePlus2.FundamentalFrequency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Timing probe analysis (ProbeGenerator --timing): where in the output a frame's level, pitch and voicing take
 * effect, chip vs JMBE. Each decoder's own output is measured on its own sample clock (the chip's is not shifted by
 * any delay estimate): averaged over the repetitions,
 * <ul>
 *   <li>level: 32-sample power envelope; steps give the time of the half-way point in dB and the 10..90% duration,
 *   one-frame events the centroid and full width at half height of the power change;</li>
 *   <li>pitch: share of a pitch-B harmonic fit minus that of pitch A over 96 samples;</li>
 *   <li>voicing: power left after a pitch-A harmonic fit over 64 samples (noise).</li>
 * </ul>
 * Times are samples from the frame boundary where the change is sent. The chip's absolute times include its I/O
 * latency, so compare its events with each other (the summary gives each relative to the level step up) as well as
 * with JMBE's.
 *
 * usage: ProbeTimingAnalyzer --dir DIR; writes results/timing_summary.txt
 */
public final class ProbeTimingAnalyzer
{
    static final int N = ProbeSupport.FRAME;
    static final int FROM = -240;
    static final int TO = 480;
    static final int STEP = 4;

    final Path dir;
    final ProbePlan plan;
    final double[] chip;
    final double[] jmbe;
    final StringBuilder summary = new StringBuilder();

    ProbeTimingAnalyzer(Path dir) throws Exception
    {
        this.dir = dir;
        plan = ProbePlan.read(dir);
        List<byte[]> frames = ProbeSupport.readFrames(dir.resolve("frames.hex"));
        jmbe = ProbeSupport.synthesize(frames, true);
        chip = ProbeSupport.readPcm(dir.resolve("chip.pcm"), null);
    }

    public static void main(String[] args) throws Exception
    {
        Path dir = null;
        for(int i = 0; i < args.length; i++)
        {
            if(args[i].equals("--dir"))
            {
                dir = Paths.get(args[++i]);
            }
            else
            {
                throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        ProbeTimingAnalyzer a = new ProbeTimingAnalyzer(dir);
        a.run();
        Path out = dir.resolve("results");
        Files.createDirectories(out);
        Files.writeString(out.resolve("timing_summary.txt"), a.summary.toString());
        System.out.println("wrote " + out.resolve("timing_summary.txt"));
    }

    void say(String format, Object... args)
    {
        String s = String.format(Locale.ROOT, format, args);
        System.out.println(s);
        summary.append(s).append('\n');
    }

    List<Integer> starts(int label, int parameter)
    {
        List<Integer> out = new ArrayList<>();
        for(ProbePlan.Probe p : plan.probes)
        {
            if(p.kind == ProbePlan.Kind.TIMING && p.index / 1000 == label && (parameter < 0 || p.index % 1000 == parameter))
            {
                out.add(p.start * N);
            }
        }
        return out;
    }

    interface Measure
    {
        double at(double[] x, int t);
    }

    static double power(double[] x, int t)
    {
        double s = 0;
        for(int i = t - 16; i < t + 16; i++)
        {
            double v = i >= 0 && i < x.length ? x[i] : 0;
            s += v * v;
        }
        return s / 32;
    }

    final java.util.Map<String, ProbeSupport.HarmonicFit> fits = new java.util.HashMap<>();

    double explained(double[] x, int t, int b0, int win)
    {
        FundamentalFrequency f = FundamentalFrequency.fromValue(b0);
        ProbeSupport.HarmonicFit hf = fits.computeIfAbsent(b0 + "/" + win, k -> new ProbeSupport.HarmonicFit(
            f.getFrequency(), Math.min(f.getL(), (int)(Math.PI / f.getFrequency()) - 1), win));
        return hf.explainedFraction(x, t - win / 2);
    }

    /** Mean of a measure over the events, at FROM..TO around each event start. */
    double[] curve(double[] x, List<Integer> events, Measure m)
    {
        int n = (TO - FROM) / STEP;
        double[] c = new double[n];
        for(int e : events)
        {
            for(int k = 0; k < n; k++)
            {
                c[k] += m.at(x, e + FROM + k * STEP);
            }
        }
        for(int k = 0; k < n; k++)
        {
            c[k] /= events.size();
        }
        return c;
    }

    static double t(int k)
    {
        return FROM + k * STEP;
    }

    /** Time where the curve crosses half-way between its first and last 40 samples' means; 10..90% duration. */
    static double[] stepTimes(double[] c)
    {
        int m = 10;
        double a = 0, b = 0;
        for(int i = 0; i < m; i++)
        {
            a += c[i] / m;
            b += c[c.length - 1 - i] / m;
        }
        return new double[]{cross(c, a + 0.5 * (b - a)), cross(c, a + 0.9 * (b - a)) - cross(c, a + 0.1 * (b - a))};
    }

    static double cross(double[] c, double level)
    {
        boolean up = c[c.length - 1] > c[0];
        for(int i = 1; i < c.length; i++)
        {
            if(up ? (c[i - 1] < level && c[i] >= level) : (c[i - 1] > level && c[i] <= level))
            {
                return t(i - 1) + (level - c[i - 1]) / (c[i] - c[i - 1]) * STEP;
            }
        }
        return Double.NaN;
    }

    /** One-frame event: centroid and full width at half height of |curve - baseline|. */
    static double[] pulseTimes(double[] c)
    {
        double base = 0;
        int m = 10;
        for(int i = 0; i < m; i++)
        {
            base += (c[i] + c[c.length - 1 - i]) / (2 * m);
        }
        double peak = 0, sw = 0, st = 0;
        for(int i = 0; i < c.length; i++)
        {
            double d = Math.abs(c[i] - base);
            peak = Math.max(peak, d);
            sw += d;
            st += d * t(i);
        }
        double first = Double.NaN, last = Double.NaN;
        for(int i = 0; i < c.length; i++)
        {
            if(Math.abs(c[i] - base) >= peak / 2)
            {
                if(Double.isNaN(first))
                {
                    first = t(i);
                }
                last = t(i);
            }
        }
        return new double[]{st / sw, last - first};
    }

    /**
     * Lag (samples) that best lines the chip's curve up with JMBE's for one event type: the chip curve is measured at
     * every lag in -300..300 and an affine fit (scale and offset) to JMBE's taken; the lag with the smallest residual
     * wins. Returns {lag, rms residual re JMBE's range}.
     */
    double[] lag(List<Integer> events, Measure m)
    {
        double[] j = curve(jmbe, events, m);
        double range = 0;
        double jm = 0;
        for(double v : j)
        {
            jm += v / j.length;
        }
        double jmin = Double.MAX_VALUE, jmax = -Double.MAX_VALUE;
        for(double v : j)
        {
            jmin = Math.min(jmin, v);
            jmax = Math.max(jmax, v);
        }
        range = jmax - jmin;
        double best = Double.MAX_VALUE;
        int bestLag = 0;
        for(int lag = -300; lag <= 300; lag += 2)
        {
            List<Integer> shifted = new ArrayList<>();
            for(int e : events)
            {
                shifted.add(e + lag);
            }
            double[] c = curve(chip, shifted, m);
            double cm = 0;
            for(double v : c)
            {
                cm += v / c.length;
            }
            double sxy = 0, sxx = 0;
            for(int i = 0; i < c.length; i++)
            {
                sxy += (c[i] - cm) * (j[i] - jm);
                sxx += (c[i] - cm) * (c[i] - cm);
            }
            double a = sxx > 0 ? sxy / sxx : 0;
            double ss = 0;
            for(int i = 0; i < c.length; i++)
            {
                double r = jm + a * (c[i] - cm) - j[i];
                ss += r * r;
            }
            if(ss < best)
            {
                best = ss;
                bestLag = lag;
            }
        }
        return new double[]{bestLag, Math.sqrt(best / j.length) / Math.max(range, 1e-9)};
    }

    void row(String name, List<Integer> events, Measure m)
    {
        double[] l = lag(events, m);
        say("   %-36s chip lag %+5.0f samples   (fit residual %4.1f%% of JMBE's swing, %d events)", name, l[0],
            100 * l[1], events.size());
    }

    void run()
    {
        Measure db = (x, t) -> 10 * Math.log10(power(x, t) + 1);
        Measure pitch = (x, t) -> explained(x, t, ProbeGenerator.TIMING_B0_B, 96) - explained(x, t, ProbeGenerator.TIMING_B0, 96);
        Measure noise = (x, t) -> {
            double p = power(x, t);
            return 10 * Math.log10(p * (1 - explained(x, t, ProbeGenerator.TIMING_B0, 64)) + 1);
        };
        say("Timing: for each event type, the delay of the chip's output against JMBE's that lines up the averaged " +
            "response (both decode the same parameter trajectory). One delay for every type = only I/O latency; " +
            "different delays = the chip puts that kind of change at a different place in the frame");
        for(int dbStep : ProbeGenerator.TIMING_STEPS_DB)
        {
            row("level step up " + dbStep + " dB (envelope dB)", starts(100, dbStep), db);
            row("level step down " + dbStep + " dB", starts(101, dbStep), db);
            row("one frame +" + dbStep + " dB", starts(200, dbStep), db);
            row("one frame -" + dbStep + " dB", starts(201, dbStep), db);
        }
        row("pitch step A -> B (B minus A share)", starts(300, -1), pitch);
        row("pitch step B -> A", starts(301, -1), pitch);
        row("one frame at pitch B", starts(310, -1), pitch);
        row("voiced -> unvoiced (noise dB)", starts(400, -1), noise);
        row("unvoiced -> voiced", starts(401, -1), noise);
        row("one unvoiced frame", starts(410, -1), noise);
        say("   curves on each decoder's own clock, every 16 samples from %d re the frame boundary:", FROM);
        String[][] show = {{"level step up 20 dB (dB)", "100", "20"}, {"one frame +20 dB (dB)", "200", "20"},
            {"pitch step A -> B", "300", "-1"}, {"one frame at pitch B", "310", "-1"},
            {"voiced -> unvoiced (noise dB)", "400", "-1"}, {"one unvoiced frame (noise dB)", "410", "-1"}};
        for(String[] sh : show)
        {
            Measure m = sh[1].startsWith("3") ? pitch : sh[1].startsWith("4") ? noise : db;
            List<Integer> ev = starts(Integer.parseInt(sh[1]), Integer.parseInt(sh[2]));
            say("   %s", sh[0]);
            printCurve("chip", curve(chip, ev, m));
            printCurve("JMBE", curve(jmbe, ev, m));
        }
    }

    void printCurve(String name, double[] c)
    {
        StringBuilder sb = new StringBuilder("      " + name);
        for(int i = 0; i < c.length; i += 16 / STEP)
        {
            sb.append(String.format(Locale.ROOT, "%6.1f", c[i]));
        }
        say("%s", sb);
    }
}
