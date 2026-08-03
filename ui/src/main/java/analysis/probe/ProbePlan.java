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
import jmbe.codec.ambe.ambePlus2.HOCB5;
import jmbe.codec.ambe.ambePlus2.HOCB6;
import jmbe.codec.ambe.ambePlus2.HOCB7;
import jmbe.codec.ambe.ambePlus2.HOCB8;
import jmbe.codec.ambe.ambePlus2.PRBA24;
import jmbe.codec.ambe.ambePlus2.PRBA58;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The probe schedule: a frame sequence in which one quantizer index at a time departs from a baseline, plus a
 * manifest saying which frames belong to which probe. Run the same frame file through the chip and JMBE and
 * every difference between the two outputs can be pinned to one table entry.
 *
 * Per pitch group (one b0, all bands voiced via b1 = 0):
 *   BASE    baseline frame held 24 frames: both decoders converge to the same state, delay and level reference
 *   GAIN k  one frame with b2 = k, then 8 baseline frames. A single frame keeps every one of the 32 gains in
 *           range (holding b2 would put the level at 2 x gain, a 107 dB spread). The decay over the following
 *           baseline frames also measures the gain memory term (published 0.5)
 *   PRBA24, PRBA58, HOC1..HOC4 k   one shape index set to k and held `hold` frames (default 16). The first
 *           frames are the step response (prediction coefficient rho), the last half are steady state
 *           where log2 amplitudes reach (T - mean T) / (1 - rho) + gain and the table entry is directly visible.
 * The baseline uses the smallest-norm entry of every shape table, so the baseline spectrum is close to flat and
 * the enhancement weights stay away from their clamps.
 */
public final class ProbePlan
{
    public enum Kind
    {
        BASE, GAIN, PRBA24, PRBA58, HOC1, HOC2, HOC3, HOC4,
        /** Response probes (ProbeGenerator --response): tone frame, pitch held, gain held, voicing held. */
        TONE, PITCH, LEVEL, VOICING,
        /** Pitch-glide probe (ProbeGenerator --glide): one segment of a step, glide or vibrato sequence. */
        GLIDE,
        /** Noise probe (ProbeGenerator --noise): one segment of a level, tilt, transition or pitch-tracking sequence. */
        NOISE,
        /** Bit error probe (ProbeGenerator --errors): one segment of clean, errored, erasure or silence frames. */
        ERRORS,
        /** Timing probe (ProbeGenerator --timing): one segment of a level, pitch or voicing step or one-frame event. */
        TIMING;

        /** b index that this kind of probe varies, or -1 for BASE. */
        public int bIndex()
        {
            switch(this)
            {
                case GAIN: return 2;
                case PRBA24: return 3;
                case PRBA58: return 4;
                case HOC1: return 5;
                case HOC2: return 6;
                case HOC3: return 7;
                case HOC4: return 8;
                case PITCH: return 0;
                case VOICING: return 1;
                case LEVEL: return 2;
                default: return -1;
            }
        }

        public int tableSize()
        {
            switch(this)
            {
                case GAIN: return 32;
                case PRBA24: return 512;
                case PRBA58: return 128;
                case HOC1: return 32;
                case HOC2: return 16;
                case HOC3: return 16;
                case HOC4: return 8;
                case VOICING: return 32;
                default: return 1;
            }
        }
    }

    public static final class Probe
    {
        public final int id;
        public final Kind kind;
        public final int group;
        public final int index;
        public final int start;   // first frame of the probe in the whole sequence
        public final int length;  // frames
        public final int[] b;     // indices of the probe's characteristic (first) frame

        Probe(int id, Kind kind, int group, int index, int start, int length, int[] b)
        {
            this.id = id;
            this.kind = kind;
            this.group = group;
            this.index = index;
            this.start = start;
            this.length = length;
            this.b = b.clone();
        }

        public int b0()
        {
            return b[0];
        }

        public int L()
        {
            return b[0] >= 120 ? 0 : FundamentalFrequency.fromValue(b[0]).getL();
        }
    }

    public final List<int[]> frames = new ArrayList<>();
    public final List<Probe> probes = new ArrayList<>();

    /** Baseline b vector per group. */
    public final List<int[]> baselines = new ArrayList<>();

    public static final int BASE_HOLD = 24;
    public static final int GAIN_SETTLE = 8;

    /**
     * @param b0s fundamental frequency indices, one probe group each (must be voice frames, 0..119)
     * @param baseGain b2 used for the baseline of each group (same length as b0s)
     * @param kinds which tables to sweep in each group
     * @param stride sweep every stride-th index of PRBA24 / PRBA58 (1 = all)
     * @param hold frames each shape probe is held
     */
    public static ProbePlan build(int[] b0s, int[] baseGain, List<List<Kind>> kinds, int stride, int hold)
    {
        ProbePlan plan = new ProbePlan();

        for(int g = 0; g < b0s.length; g++)
        {
            int[] base = baseline(b0s[g], baseGain[g]);
            plan.baselines.add(base);
            plan.add(Kind.BASE, g, 0, base, BASE_HOLD);

            for(Kind kind : kinds.get(g))
            {
                if(kind == Kind.BASE)
                {
                    continue;
                }
                int step = (kind == Kind.PRBA24 || kind == Kind.PRBA58) ? stride : 1;
                for(int k = 0; k < kind.tableSize(); k += step)
                {
                    int[] b = base.clone();
                    b[kind.bIndex()] = k;
                    if(kind == Kind.GAIN)
                    {
                        int start = plan.frames.size();
                        plan.frames.add(b);
                        for(int i = 0; i < GAIN_SETTLE; i++)
                        {
                            plan.frames.add(base);
                        }
                        plan.probes.add(new Probe(plan.probes.size(), kind, g, k, start, 1 + GAIN_SETTLE, b));
                    }
                    else
                    {
                        plan.add(kind, g, k, b, hold);
                    }
                }
                // Return to baseline between tables: a repeatability check on the reference level and shape
                plan.add(Kind.BASE, g, 0, base, hold);
            }
        }

        return plan;
    }

    /**
     * One tilt-test group: the baseline with its PRBA24 row replaced by a tilt row, then each of the given kinds
     * swept every stride-th index (all kinds, HOC included), each row held `hold` frames, back to baseline between
     * tables. Every tilt group of the same pitch sweeps the same rows, so a row's chip-vs-JMBE difference can be
     * compared across tilts. Probe group numbers are 0; the caller renumbers when merging.
     */
    public static ProbePlan buildTiltGroup(int b0, int b2, int prba24Row, List<Kind> kinds, int stride, int hold)
    {
        ProbePlan plan = new ProbePlan();
        int[] base = baseline(b0, b2, prba24Row);
        plan.baselines.add(base);
        plan.add(Kind.BASE, 0, 0, base, BASE_HOLD);
        for(Kind kind : kinds)
        {
            for(int k = 0; k < kind.tableSize(); k += stride)
            {
                int[] b = base.clone();
                b[kind.bIndex()] = k;
                plan.add(kind, 0, k, b, hold);
            }
            plan.add(Kind.BASE, 0, 0, base, hold);
        }
        return plan;
    }

    /** The baseline with a chosen PRBA24 row (a negative row keeps the smallest-norm row). */
    public static int[] baseline(int b0, int b2, int prba24Row)
    {
        int[] b = baseline(b0, b2);
        if(prba24Row >= 0)
        {
            b[3] = prba24Row;
        }
        return b;
    }

    /**
     * PRBA24 rows for a tilt test. Target slopes are evenly spaced between the table's G2 quantiles qLow and qHigh; for each target the row minimizing (G2 - target)^2 + G3^2 + G4^2 is picked, so the rows differ
     * mainly in overall slope and carry little curvature. The smallest-norm row (the usual baseline) replaces the
     * middle pick so earlier runs stay comparable. Returns distinct rows ordered by G2.
     */
    public static int[] tiltRows(int n, double qLow, double qHigh)
    {
        PRBA24[] all = PRBA24.values();
        double[] g2 = new double[all.length];
        for(int i = 0; i < all.length; i++)
        {
            g2[i] = all[i].getG2();
        }
        double[] sorted = g2.clone();
        Arrays.sort(sorted);
        List<Integer> picked = new ArrayList<>();
        int minNorm = baseline(0, 0)[3];
        for(int k = 0; k < n; k++)
        {
            if(k == n / 2)
            {
                picked.add(minNorm);
                continue;
            }
            double lo = sorted[(int)Math.round(qLow * (sorted.length - 1))];
            double hi = sorted[(int)Math.round(qHigh * (sorted.length - 1))];
            double target = n == 1 ? 0 : lo + (hi - lo) * k / (n - 1);
            int best = -1;
            double bestCost = Double.MAX_VALUE;
            for(int i = 0; i < all.length; i++)
            {
                double d = g2[i] - target;
                double cost = d * d + all[i].getG3() * all[i].getG3() + all[i].getG4() * all[i].getG4();
                if(cost < bestCost && !picked.contains(i) && i != minNorm)
                {
                    bestCost = cost;
                    best = i;
                }
            }
            picked.add(best);
        }
        picked.sort((x, y) -> Double.compare(g2[x], g2[y]));
        return picked.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Appends a probe holding b for count frames (and records the group baseline on a group's first BASE). */
    public void append(Kind kind, int group, int index, int[] b, int count)
    {
        if(kind == Kind.BASE && baselines.size() == group)
        {
            baselines.add(b.clone());
        }
        add(kind, group, index, b, count);
    }

    private void add(Kind kind, int group, int index, int[] b, int count)
    {
        int start = frames.size();
        for(int i = 0; i < count; i++)
        {
            frames.add(b);
        }
        probes.add(new Probe(probes.size(), kind, group, index, start, count, b));
    }

    /** All bands voiced, smallest-norm shape entries, given pitch and gain. */
    public static int[] baseline(int b0, int b2)
    {
        int[] b = new int[9];
        b[0] = b0;
        b[1] = 0; // VoicingDecision V0: all eight bands voiced
        b[2] = b2;
        b[3] = argMinNorm(Arrays.stream(PRBA24.values()).map(v -> new float[]{v.getG2(), v.getG3(), v.getG4()})
            .toArray(float[][]::new));
        b[4] = argMinNorm(Arrays.stream(PRBA58.values())
            .map(v -> new float[]{v.getG5(), v.getG6(), v.getG7(), v.getG8()}).toArray(float[][]::new));
        b[5] = argMinNorm(Arrays.stream(HOCB5.values()).map(HOCB5::getCoefficients).toArray(float[][]::new));
        b[6] = argMinNorm(Arrays.stream(HOCB6.values()).map(HOCB6::getCoefficients).toArray(float[][]::new));
        b[7] = argMinNorm(Arrays.stream(HOCB7.values()).map(HOCB7::getCoefficients).toArray(float[][]::new));
        b[8] = argMinNorm(Arrays.stream(HOCB8.values()).map(HOCB8::getCoefficients).toArray(float[][]::new));
        return b;
    }

    private static int argMinNorm(float[][] rows)
    {
        int best = 0;
        double bestNorm = Double.MAX_VALUE;
        for(int i = 0; i < rows.length; i++)
        {
            double s = 0;
            for(float v : rows[i])
            {
                s += v * v;
            }
            if(s < bestNorm)
            {
                bestNorm = s;
                best = i;
            }
        }
        return best;
    }

    public List<byte[]> encodedFrames()
    {
        List<byte[]> out = new ArrayList<>(frames.size());
        for(int[] b : frames)
        {
            out.add(AmbeFrameEncoder.encodeAny(b));
        }
        return out;
    }

    /** Per-frame channel error masks {c0Mask, c1Mask} (bit error probe); null or missing entries mean none. */
    public final java.util.Map<Integer, int[]> errorMasks = new java.util.TreeMap<>();

    /** Sets channel errors on frames [start, start + length). */
    public void setErrors(int start, int length, int c0Mask, int c1Mask)
    {
        for(int f = start; f < start + length; f++)
        {
            errorMasks.put(f, new int[]{c0Mask, c1Mask});
        }
    }

    public void write(Path dir) throws IOException
    {
        Files.createDirectories(dir);
        StringBuilder hex = new StringBuilder();
        hex.append("# AMBE+2 probe frames, one 72-bit frame per line, JMBE/ThumbDV byte layout\n");
        for(int f = 0; f < frames.size(); f++)
        {
            byte[] frame = AmbeFrameEncoder.encodeAny(frames.get(f));
            int[] mask = errorMasks.get(f);
            if(mask != null)
            {
                frame = AmbeFrameEncoder.withErrors(frame, mask[0], mask[1]);
            }
            hex.append(AmbeFrameEncoder.toHex(frame)).append('\n');
        }
        Files.writeString(dir.resolve("frames.hex"), hex.toString());
        if(!errorMasks.isEmpty())
        {
            StringBuilder err = new StringBuilder("frame,e0,e1,c0_mask,c1_mask\n");
            for(java.util.Map.Entry<Integer, int[]> e : errorMasks.entrySet())
            {
                err.append(e.getKey()).append(',').append(Integer.bitCount(e.getValue()[0])).append(',')
                    .append(Integer.bitCount(e.getValue()[1])).append(',').append(Integer.toHexString(e.getValue()[0]))
                    .append(',').append(Integer.toHexString(e.getValue()[1])).append('\n');
            }
            Files.writeString(dir.resolve("errors.csv"), err.toString());
        }

        StringBuilder csv = new StringBuilder("probe,kind,group,index,start,length,b0,L,b1,b2,b3,b4,b5,b6,b7,b8\n");
        for(Probe p : probes)
        {
            csv.append(p.id).append(',').append(p.kind).append(',').append(p.group).append(',').append(p.index)
                .append(',').append(p.start).append(',').append(p.length).append(',').append(p.b[0]).append(',')
                .append(p.L());
            for(int i = 1; i < 9; i++)
            {
                csv.append(',').append(p.b[i]);
            }
            csv.append('\n');
        }
        Files.writeString(dir.resolve("manifest.csv"), csv.toString());
    }

    public static ProbePlan read(Path dir) throws IOException
    {
        ProbePlan plan = new ProbePlan();
        for(byte[] frame : ProbeSupport.readFrames(dir.resolve("frames.hex")))
        {
            jmbe.codec.ambe.AMBEFrame decoded = new jmbe.codec.ambe.AMBEFrame(frame);
            if(decoded.isToneFrame())
            {
                // Tone frames have no b vector; the layout variant is not recoverable (the manifest holds it)
                plan.frames.add(new int[]{AmbeFrameEncoder.TONE_B0, decoded.getToneParameters().getTone().getValue() & 0xFF,
                    decoded.getToneParameters().getAmplitude(), 0, 0, 0, 0, 0, 0});
            }
            else
            {
                plan.frames.add(decoded.getB());
            }
        }
        Path errors = dir.resolve("errors.csv");
        if(Files.exists(errors))
        {
            List<String> rows = Files.readAllLines(errors);
            for(int i = 1; i < rows.size(); i++)
            {
                String[] f = rows.get(i).split(",");
                if(f.length >= 5)
                {
                    plan.errorMasks.put(Integer.parseInt(f[0]), new int[]{Integer.parseInt(f[3], 16),
                        Integer.parseInt(f[4], 16)});
                }
            }
        }
        List<String> lines = Files.readAllLines(dir.resolve("manifest.csv"));
        for(int i = 1; i < lines.size(); i++)
        {
            String[] f = lines.get(i).split(",");
            if(f.length < 16)
            {
                continue;
            }
            int[] b = new int[9];
            b[0] = Integer.parseInt(f[6]);
            for(int k = 1; k < 9; k++)
            {
                b[k] = Integer.parseInt(f[7 + k]);
            }
            Probe p = new Probe(Integer.parseInt(f[0]), Kind.valueOf(f[1]), Integer.parseInt(f[2]),
                Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), b);
            plan.probes.add(p);
            if(p.kind == Kind.BASE && plan.baselines.size() == p.group)
            {
                plan.baselines.add(b);
            }
        }
        return plan;
    }
}
