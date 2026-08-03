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

import jmbe.codec.ambe.AMBEChipResponse;
import jmbe.codec.ambe.ambePlus2.FundamentalFrequency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Noise probe analysis (ProbeGenerator --noise), chip vs JMBE (with AMBEChipResponse):
 * <ul>
 *   <li>G1 level: unvoiced minus voiced power, chip minus JMBE, at nine levels (is the noise level linear?);</li>
 *   <li>G2 tilt: the same per 500 Hz band for three spectral tilts (does the noise follow the amplitudes?);</li>
 *   <li>G3 transitions: the power envelope around voiced-to-unvoiced and back, and the mean power of short
 *   alternating runs;</li>
 *   <li>G4 pitch tracking: the first harmonic after a pitch drop made during 0, 10, 20 or 40 unvoiced frames, against
 *   a tracker that runs through unvoiced frames (JMBE), one that holds, and one that forgets.</li>
 * </ul>
 * usage: ProbeNoiseAnalyzer --dir DIR [--delay D]; writes results/noise_summary.txt
 */
public final class ProbeNoiseAnalyzer
{
    static final int N = ProbeResponseAnalyzer.N;
    final ProbeResponseAnalyzer r;

    ProbeNoiseAnalyzer(ProbeResponseAnalyzer r)
    {
        this.r = r;
    }

    public static void main(String[] args) throws Exception
    {
        Path dir = null;
        Integer delay = null;
        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--dir" -> dir = Paths.get(args[++i]);
                case "--delay" -> delay = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        ProbeResponseAnalyzer r = new ProbeResponseAnalyzer(dir, dir.resolve("chip.pcm"), null, delay);
        r.say("frames %d, chip delay %d samples", r.frames.size(), r.delay);
        Path out = dir.resolve("results");
        Files.createDirectories(out);
        ProbeNoiseAnalyzer a = new ProbeNoiseAnalyzer(r);
        a.level();
        a.tilt();
        a.transitions();
        a.tracking();
        Files.writeString(out.resolve("noise_summary.txt"), r.summary.toString());
        r.say("wrote %s/noise_summary.txt", out);
    }

    List<ProbePlan.Probe> probes(int label)
    {
        return r.plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.NOISE && p.index / 1000 == label).toList();
    }

    /** Mean power of x over samples [from, to) (chip: shifted by the delay). */
    double power(double[] x, int from, int to, boolean isChip)
    {
        double s = 0;
        int n = 0;
        for(int k = from; k < to; k++)
        {
            int i = k + (isChip ? r.delay : 0);
            if(i >= 0 && i < x.length)
            {
                s += x[i] * x[i];
                n++;
            }
        }
        return n > 0 ? s / n : Double.NaN;
    }

    /** Power of a probe's steady frames (from frame 3 to one before the end). */
    double steady(double[] x, ProbePlan.Probe p, boolean isChip)
    {
        return power(x, (p.start + 3) * N, (p.start + p.length - 1) * N, isChip);
    }

    static double db(double x)
    {
        return 10 * Math.log10(x);
    }

    void level()
    {
        List<ProbePlan.Probe> v = probes(100);
        List<ProbePlan.Probe> u = probes(200);
        if(v.isEmpty())
        {
            return;
        }
        r.say("G1. level (b0 63): chip - JMBE, dB");
        r.say("      level dBFS   voiced   unvoiced   unvoiced minus voiced");
        for(int i = 0; i < v.size() && i < u.size(); i++)
        {
            double dv = db(steady(r.chip, v.get(i), true)) - db(steady(r.jmbe, v.get(i), false));
            double du = db(steady(r.chip, u.get(i), true)) - db(steady(r.jmbe, u.get(i), false));
            r.say("      %6d       %+6.2f   %+6.2f     %+6.2f", v.get(i).index % 1000 - 100, dv, du, du - dv);
        }
    }

    /** Power per 500 Hz band over a probe's steady frames, from a Hann-windowed 160-sample DFT per frame. */
    double[] bands(double[] x, ProbePlan.Probe p, boolean isChip)
    {
        double[] b = new double[8];
        for(int f = p.start + 3; f < p.start + p.length - 1; f++)
        {
            int start = f * N + (isChip ? r.delay : 0);
            for(int k = 1; k < N / 2; k++)
            {
                double re = 0, im = 0;
                for(int i = 0; i < N; i++)
                {
                    int j = start + i;
                    double s = j >= 0 && j < x.length ? x[j] : 0;
                    double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N);
                    re += s * w * Math.cos(2 * Math.PI * k * i / N);
                    im -= s * w * Math.sin(2 * Math.PI * k * i / N);
                }
                b[Math.min(k * 50 / 500, 7)] += re * re + im * im;
            }
        }
        return b;
    }

    void tilt()
    {
        List<ProbePlan.Probe> v = probes(300);
        List<ProbePlan.Probe> u = probes(400);
        if(v.isEmpty())
        {
            return;
        }
        r.say("G2. tilt (b0 84): unvoiced minus voiced, chip - JMBE, dB per 500 Hz band");
        r.say("      PRBA24 row      0    500   1000   1500   2000   2500   3000   3500");
        for(int i = 0; i < v.size() && i < u.size(); i++)
        {
            double[] cv = bands(r.chip, v.get(i), true);
            double[] jv = bands(r.jmbe, v.get(i), false);
            double[] cu = bands(r.chip, u.get(i), true);
            double[] ju = bands(r.jmbe, u.get(i), false);
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "      %4d      ", v.get(i).index % 1000));
            for(int b = 0; b < 8; b++)
            {
                sb.append(String.format(Locale.ROOT, " %+6.2f", (db(cu[b]) - db(ju[b])) - (db(cv[b]) - db(jv[b]))));
            }
            r.say("%s", sb);
        }
    }

    void transitions()
    {
        List<ProbePlan.Probe> v = probes(500);
        List<ProbePlan.Probe> u = probes(600);
        if(v.isEmpty())
        {
            return;
        }
        // Reference levels from the 8-frame runs
        double cv = 0, jv = 0, cu = 0, ju = 0;
        int nv = 0, nu = 0;
        for(ProbePlan.Probe p : v)
        {
            if(p.length == 8)
            {
                cv += steady(r.chip, p, true);
                jv += steady(r.jmbe, p, false);
                nv++;
            }
        }
        for(ProbePlan.Probe p : u)
        {
            if(p.length == 8)
            {
                cu += steady(r.chip, p, true);
                ju += steady(r.jmbe, p, false);
                nu++;
            }
        }
        cv /= nv;
        jv /= nv;
        cu /= nu;
        ju /= nu;
        r.say("G3. voiced/unvoiced transitions (b0 63, b1 0 and 16); steady unvoiced re voiced: chip %+.2f dB, JMBE %+.2f",
            db(cu / cv), db(ju / jv));
        int slot = 20;
        for(int dir = 0; dir < 2; dir++)
        {
            List<ProbePlan.Probe> to = dir == 0 ? u : v;
            double[] c = new double[16];
            double[] j = new double[16];
            int n = 0;
            for(ProbePlan.Probe p : to)
            {
                if(p.length != 8 || p.start < 8)
                {
                    continue;
                }
                int b = p.start * N;
                for(int s = 0; s < 16; s++)
                {
                    c[s] += power(r.chip, b - 80 + s * slot, b - 80 + (s + 1) * slot, true);
                    j[s] += power(r.jmbe, b - 80 + s * slot, b - 80 + (s + 1) * slot, false);
                }
                n++;
            }
            StringBuilder hc = new StringBuilder("        chip ");
            StringBuilder hj = new StringBuilder("        JMBE ");
            for(int s = 0; s < 16; s++)
            {
                hc.append(String.format(Locale.ROOT, "%+6.1f", db(c[s] / n / cv)));
                hj.append(String.format(Locale.ROOT, "%+6.1f", db(j[s] / n / jv)));
            }
            r.say("   %s, power re steady voiced (dB), 20-sample slots from -80 to +240 samples (frame boundary at 0; " +
                "the chip's synthesis delay is removed), mean of %d:", dir == 0 ? "voiced -> unvoiced" : "unvoiced -> voiced", n);
            r.say("%s", hc);
            r.say("%s", hj);
        }
        r.say("   alternating runs: mean power over the cycles, chip - JMBE (dB)");
        for(int run : ProbeGenerator.NOISE_RUNS)
        {
            int from = Integer.MAX_VALUE, to = 0;
            for(ProbePlan.Probe p : v)
            {
                if(p.length == run)
                {
                    from = Math.min(from, p.start);
                }
            }
            for(ProbePlan.Probe p : u)
            {
                if(p.length == run)
                {
                    to = Math.max(to, p.start + p.length);
                }
            }
            double c = power(r.chip, (from + 2) * N, (to - 1) * N, true);
            double j = power(r.jmbe, (from + 2) * N, (to - 1) * N, false);
            r.say("      run %d frames: %+5.2f dB", run, db(c) - db(j));
        }
    }

    void tracking()
    {
        List<ProbePlan.Probe> after = probes(720);
        if(after.isEmpty())
        {
            return;
        }
        int[] span = r.groupSpan.get(after.get(0).group);
        // Tracker hypotheses over the group's frames: 0 runs through unvoiced frames, 1 holds, 2 forgets (P = f0)
        double[][] tracked = new double[3][span[1]];
        double[] prev = {AMBEChipResponse.TRACKED_PITCH_RESET_HZ, AMBEChipResponse.TRACKED_PITCH_RESET_HZ,
            AMBEChipResponse.TRACKED_PITCH_RESET_HZ};
        boolean wasUnvoiced = false;
        for(ProbePlan.Probe p : r.plan.probes)
        {
            if(p.group != after.get(0).group)
            {
                continue;
            }
            float f0 = (float)(FundamentalFrequency.fromValue(p.b0()).getFrequency() * 8000 / (2 * Math.PI));
            boolean unvoiced = p.b[1] == ProbeGenerator.NOISE_B1;
            for(int f = p.start; f < p.start + p.length; f++)
            {
                prev[0] = AMBEChipResponse.trackPitch((float)prev[0], f0);
                if(!unvoiced)
                {
                    prev[1] = AMBEChipResponse.trackPitch((float)prev[1], f0);
                    prev[2] = wasUnvoiced ? f0 : AMBEChipResponse.trackPitch((float)prev[2], f0);
                }
                for(int h = 0; h < 3; h++)
                {
                    tracked[h][f] = prev[h];
                }
                wasUnvoiced = unvoiced;
            }
        }
        int[] at = {1, 2, 5, 10, 20, 38};
        r.say("G4. pitch drop b0 64 -> 96 (150 -> 92 Hz) made during k unvoiced frames: first harmonic deficit in the " +
            "voiced frames after it (chip - published decode, dB) and three tracker hypotheses");
        StringBuilder head = new StringBuilder("      frame after the noise:          ");
        for(int k : at)
        {
            head.append(String.format(Locale.ROOT, "%7d", k));
        }
        r.say("%s", head);
        String[] names = {"runs through noise", "holds during noise (JMBE)", "forgets after noise"};
        double[] ss = new double[3];
        int n = 0;
        for(ProbePlan.Probe p : after)
        {
            double f0 = FundamentalFrequency.fromValue(p.b0()).getFrequency() * 8000 / (2 * Math.PI);
            double hp = AMBEChipResponse.highPassDb(f0) - (AMBEChipResponse.highPassDb(2 * f0) +
                AMBEChipResponse.highPassDb(3 * f0)) / 2;
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "      k = %2d chip                    ",
                p.index % 1000));
            double[] m = new double[p.length];
            for(int k = 0; k < p.length; k++)
            {
                m[k] = r.firstHarmonicDeficit(p.start + k, p.b0());
            }
            for(int k : at)
            {
                sb.append(String.format(Locale.ROOT, "%+7.2f", k < m.length ? m[k] : Double.NaN));
            }
            r.say("%s", sb);
            for(int h = 0; h < 3; h++)
            {
                StringBuilder hb = new StringBuilder(String.format(Locale.ROOT, "             %-27s", names[h]));
                for(int k : at)
                {
                    double pred = AMBEChipResponse.trackingDb(f0, tracked[h][p.start + k]) + hp;
                    hb.append(String.format(Locale.ROOT, "%+7.2f", pred));
                }
                for(int k = 1; k < p.length - 1; k++)
                {
                    if(!Double.isNaN(m[k]))
                    {
                        double pred = AMBEChipResponse.trackingDb(f0, tracked[h][p.start + k]) + hp;
                        ss[h] += (m[k] - pred) * (m[k] - pred);
                        if(h == 0)
                        {
                            n++;
                        }
                    }
                }
                r.say("%s", hb);
            }
        }
        for(int h = 0; h < 3; h++)
        {
            r.say("      %-27s rms %.2f dB over %d frames", names[h], Math.sqrt(ss[h] / Math.max(n, 1)), n);
        }
    }
}
