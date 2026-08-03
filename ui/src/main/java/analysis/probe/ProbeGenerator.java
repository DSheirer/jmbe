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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the probe experiment's frames.hex and manifest.csv.
 *
 * usage: java analysis.probe.ProbeGenerator --out DIR [--L 56,40,24] [--full 56] [--stride 8] [--hold 16]
 *            [--level-dbfs -30] [--rho-L 10,13,17] [--rho-stride 8] [--rho-only]
 *        java analysis.probe.ProbeGenerator --out DIR --response [--tones-only] [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --pitch-sweep [--sweep-tilts 236,87,266] [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --glide [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --voicing [--voicing-b0 20,40,63,...] [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --noise [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --errors [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --error-rate [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --timing [--level-dbfs -30]
 *        java analysis.probe.ProbeGenerator --out DIR --tilt-test [--tilt-L 17,40] [--tilts 5]
 *            [--tilt-stride 4] [--hold 16] [--level-dbfs -30]
 *
 *   --L        harmonic counts of the pitch groups; each maps to the first b0 with that L
 *   --full     groups (by L) that sweep every PRBA24/PRBA58 entry; the others sweep every --stride-th entry
 *              (gain and HOC tables are always swept in full). Default: the first group only
 *   --level-dbfs  baseline RMS level each group is set to (chosen with JMBE); keeps the +23 dB single-frame
 *              gain probes below clipping
 *   --rho-L    extra pitch groups (by L) that measure only the prediction coefficient: a baseline plus PRBA24 and
 *              PRBA58 step probes every --rho-stride-th entry, no gain or HOC sweeps. They are appended after the
 *              --L groups and were analyzed by the since-removed ProbeAnalyzer (rho per L, including the
 *              low-L range)
 *   --rho-only drop the --L groups and write only the --rho-L groups (a short run for the rho-vs-L question)
 *   --response    instead of the table sweep, write the response probes (analyze with ProbeResponseAnalyzer):
 *              tone frames (TIA-102.BABA-1 layout) swept over frequency and amplitude plus the dual tones, a flat
 *              baseline swept over every 4th pitch and over level, and every voicing code at L 24 and L 40.
 *              --tones-only writes just the tone group
 *   --pitch-sweep instead of the table sweep, write every voice pitch (b0 0..119) on a level-matched baseline, one
 *              group per PRBA24 tilt row (--sweep-tilts; default a falling, the flat and a rising spectrum), each
 *              group led by a short level staircase for the delay estimate. Analyze with ProbeResponseAnalyzer
 *              (section B and the first harmonic table)
 *   --glide       instead of the table sweep, write the pitch-glide probe (how the chip's first harmonic loss builds up
 *              and recovers with pitch changes): single steps of 1..16 b0 up and down from a long hold at two
 *              pitches, glides of 30 b0 at 1 step per 1, 2 and 4 frames, and a vibrato. Analyze with
 *              ProbeResponseAnalyzer (section E)
 *   --voicing     instead of the table sweep, write the voicing probe: every b1 code 0..31 held VOICING_HOLD frames
 *              on the flat baseline, one group per pitch (--voicing-b0), so that harmonics sample every voicing
 *              band edge at many offsets. Analyze with ProbeVoicingAnalyzer
 *   --noise       instead of the table sweep, write the noise probe: unvoiced vs voiced at nine levels and three
 *              spectral tilts, voiced/unvoiced alternation every 1, 2, 4 and 8 frames, and a pitch drop made during
 *              unvoiced frames (does the chip's pitch tracker run through them?). Analyze with ProbeNoiseAnalyzer
 *   --errors      instead of the table sweep, write the bit error probe: single frames with 0..5 channel errors in
 *              C0 and C1, runs of 1..12 uncorrectable frames, 60-frame stretches of correctable errors, and erasure and
 *              silence frames. Analyze with ProbeErrorAnalyzer
 *   --error-rate  instead of the table sweep, write the error-rate probe: 120-frame correctable error loads with
 *              different C0 / C1 counts and bit positions (data or parity bits), each after 100 clean frames, to find
 *              how the chip's error-rate average counts errors. Analyze with ProbeErrorAnalyzer (section H5)
 *   --timing      instead of the table sweep, write the timing probe: level, pitch and voicing steps and one-frame
 *              events, each repeated, to find where in the output a frame's parameters take effect. Analyze with
 *              ProbeTimingAnalyzer
 *   --tilt-test   instead of the table sweep, write the tilt test: one group per (pitch, tilt), where the tilt is
 *              the baseline's PRBA24 row (--tilts rows of increasing slope, the usual flat baseline in the middle),
 *              and every group sweeps the same PRBA58, HOC1 and HOC2 rows (every --tilt-stride-th). It was
 *              analyzed by the since-removed ProbeTiltAnalyzer (one set of table values for every tilt means the
 *              difference is in the tables, values that change with the tilt mean a spectrum-dependent step)
 */
public final class ProbeGenerator
{
    public static void main(String[] args) throws Exception
    {
        Path out = null;
        int[] Ls = {56, 40, 24};
        int[] full = null;
        int stride = 8;
        int hold = 16;
        double levelDbfs = -30.0;
        int[] rhoLs = new int[0];
        int rhoStride = 8;
        boolean rhoOnly = false;
        boolean tiltTest = false;
        boolean response = false;
        boolean tonesOnly = false;
        boolean pitchSweep = false;
        boolean glide = false;
        boolean voicing = false;
        boolean noise = false;
        boolean errors = false;
        boolean errorRate = false;
        boolean timing = false;
        int[] voicingB0 = VOICING_B0;
        int[] sweepTilts = {236, 87, 266};
        int[] tiltLs = {17, 40};
        int tilts = 5;
        int tiltStride = 4;

        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--out" -> out = Paths.get(args[++i]);
                case "--L" -> Ls = parse(args[++i]);
                case "--full" -> full = parse(args[++i]);
                case "--stride" -> stride = Integer.parseInt(args[++i]);
                case "--hold" -> hold = Integer.parseInt(args[++i]);
                case "--level-dbfs" -> levelDbfs = Double.parseDouble(args[++i]);
                case "--rho-L" -> rhoLs = parse(args[++i]);
                case "--rho-stride" -> rhoStride = Integer.parseInt(args[++i]);
                case "--rho-only" -> rhoOnly = true;
                case "--tilt-test" -> tiltTest = true;
                case "--response" -> response = true;
                case "--tones-only" -> tonesOnly = true;
                case "--pitch-sweep" -> pitchSweep = true;
                case "--glide" -> glide = true;
                case "--voicing" -> voicing = true;
                case "--noise" -> noise = true;
                case "--errors" -> errors = true;
                case "--error-rate" -> errorRate = true;
                case "--timing" -> timing = true;
                case "--voicing-b0" -> voicingB0 = parse(args[++i]);
                case "--sweep-tilts" -> sweepTilts = parse(args[++i]);
                case "--tilt-L" -> tiltLs = parse(args[++i]);
                case "--tilts" -> tilts = Integer.parseInt(args[++i]);
                case "--tilt-stride" -> tiltStride = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if(out == null)
        {
            System.err.println("usage: ProbeGenerator --out DIR [--L 56,40,24] [--full 56] [--stride 8] [--hold 16] " +
                "[--level-dbfs -30] [--rho-L 10,13,17] [--rho-stride 8] [--rho-only]");
            System.exit(1);
        }
        if(timing)
        {
            ProbePlan plan = generateTiming(levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(errorRate)
        {
            ProbePlan plan = generateErrorRate(levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(errors)
        {
            ProbePlan plan = generateErrors(levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(noise)
        {
            ProbePlan plan = generateNoise(levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(voicing)
        {
            ProbePlan plan = generateVoicing(voicingB0, levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(glide)
        {
            ProbePlan plan = generateGlide(levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(pitchSweep)
        {
            ProbePlan plan = generatePitchSweep(sweepTilts, levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(response)
        {
            ProbePlan plan = generateResponse(levelDbfs, tonesOnly, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(tiltTest)
        {
            ProbePlan plan = generateTilt(tiltLs, tilts, tiltStride, hold, levelDbfs, true);
            plan.write(out);
            report(plan, out);
            return;
        }
        if(rhoOnly)
        {
            if(rhoLs.length == 0)
            {
                throw new IllegalArgumentException("--rho-only needs --rho-L");
            }
            Ls = new int[0];
        }
        if(full == null)
        {
            full = Ls.length > 0 ? new int[]{Ls[0]} : new int[0];
        }

        ProbePlan plan = generate(Ls, full, stride, hold, levelDbfs, rhoLs, rhoStride, true);
        plan.write(out);
        report(plan, out);
    }

    private static void report(ProbePlan plan, Path out)
    {
        double seconds = plan.frames.size() * 0.02;
        System.out.printf("%d frames (%.0f s of audio, ~%.1f min through the ThumbDV at 20 ms per frame), %d probes%n",
            plan.frames.size(), seconds, seconds / 60.0 * 1.15, plan.probes.size());
        System.out.println("wrote " + out.resolve("frames.hex") + " and " + out.resolve("manifest.csv"));
    }

    public static ProbePlan generate(int[] Ls, int[] full, int stride, int hold, double levelDbfs, boolean verbose)
    {
        return generate(Ls, full, stride, hold, levelDbfs, new int[0], 8, verbose);
    }

    /**
     * @param rhoLs extra groups (by L) holding only a baseline and PRBA24/PRBA58 step probes at rhoStride, for
     * measuring the prediction coefficient per L. Appended after the Ls groups.
     */
    public static ProbePlan generate(int[] Ls, int[] full, int stride, int hold, double levelDbfs, int[] rhoLs,
                                     int rhoStride, boolean verbose)
    {
        int groups = Ls.length + rhoLs.length;
        int[] groupL = new int[groups];
        int[] groupStride = new int[groups];
        List<List<ProbePlan.Kind>> kinds = new ArrayList<>();

        for(int g = 0; g < Ls.length; g++)
        {
            final int L = Ls[g];
            groupL[g] = L;
            groupStride[g] = Arrays.stream(full).anyMatch(x -> x == L) ? 1 : stride;
            kinds.add(new ArrayList<>(EnumSet.range(ProbePlan.Kind.GAIN, ProbePlan.Kind.HOC4)));
        }
        for(int r = 0; r < rhoLs.length; r++)
        {
            int g = Ls.length + r;
            groupL[g] = rhoLs[r];
            groupStride[g] = rhoStride;
            kinds.add(List.of(ProbePlan.Kind.PRBA24, ProbePlan.Kind.PRBA58));
        }

        // Stride applies per group: build groups separately and merge.
        ProbePlan merged = new ProbePlan();
        for(int g = 0; g < groups; g++)
        {
            int b0 = b0ForL(groupL[g]);
            int gain = gainForLevel(b0, levelDbfs);
            if(verbose)
            {
                double f0Hz = FundamentalFrequency.fromValue(b0).getFrequency() * 8000 / (2 * Math.PI);
                System.out.printf("group %d: b0 %d (L %d, f0 %.1f Hz), baseline b2 %d, PRBA stride %d%s%n", g, b0,
                    groupL[g], f0Hz, gain, groupStride[g], g >= Ls.length ? ", rho steps only" : "");
            }
            ProbePlan one = ProbePlan.build(new int[]{b0}, new int[]{gain}, List.of(kinds.get(g)), groupStride[g],
                hold);
            int offset = merged.frames.size();
            merged.frames.addAll(one.frames);
            merged.baselines.add(one.baselines.get(0));
            for(ProbePlan.Probe p : one.probes)
            {
                merged.probes.add(new ProbePlan.Probe(merged.probes.size(), p.kind, g, p.index, p.start + offset,
                    p.length, p.b));
            }
        }
        return merged;
    }

    /**
     * Tilt test plan: for each pitch, one group per PRBA24 tilt row, each sweeping the same PRBA58, HOC1 and HOC2
     * rows. Each group's baseline gain is set for the target level with its own tilt.
     */
    public static ProbePlan generateTilt(int[] Ls, int tilts, int stride, int hold, double levelDbfs, boolean verbose)
    {
        int[] rows = ProbePlan.tiltRows(tilts, 0.1, 0.9);
        List<ProbePlan.Kind> kinds = List.of(ProbePlan.Kind.PRBA58, ProbePlan.Kind.HOC1, ProbePlan.Kind.HOC2);
        ProbePlan merged = new ProbePlan();
        int g = 0;
        for(int L : Ls)
        {
            int b0 = b0ForL(L);
            for(int row : rows)
            {
                int gain = gainForLevel(b0, levelDbfs, row);
                if(verbose)
                {
                    jmbe.codec.ambe.ambePlus2.PRBA24 v = jmbe.codec.ambe.ambePlus2.PRBA24.fromValue(row);
                    System.out.printf(java.util.Locale.ROOT, "group %d: b0 %d (L %d), tilt PRBA24 row %d " +
                        "(G2 %+.3f G3 %+.3f G4 %+.3f), baseline b2 %d%n", g, b0, L, row, v.getG2(), v.getG3(),
                        v.getG4(), gain);
                }
                ProbePlan one = ProbePlan.buildTiltGroup(b0, gain, row, kinds, stride, hold);
                int offset = merged.frames.size();
                merged.frames.addAll(one.frames);
                merged.baselines.add(one.baselines.get(0));
                for(ProbePlan.Probe p : one.probes)
                {
                    merged.probes.add(new ProbePlan.Probe(merged.probes.size(), p.kind, g, p.index, p.start + offset,
                        p.length, p.b));
                }
                g++;
            }
        }
        return merged;
    }

    public static final int TONE_HOLD = 8;
    public static final int TONE_AD = 64;
    public static final int TONE_AD_SWEEP_ID = 32; // 1000 Hz
    public static final int[] TONE_ADS = {4, 8, 16, 24, 32, 40, 48, 56, 64, 65, 72, 80, 88, 96, 104, 112, 120, 127};
    public static final int RESPONSE_HOLD = 20;

    /**
     * Response probes. Groups (each decoded by the chip from reset, each starting with a flat L 24 baseline):
     *   0     tones (TIA-102.BABA-1 Table 10 layout): every single tone id 5..122 at AD 64, an AD sweep at 1 kHz, the
     *         dual tones 128..163 (DTMF, Knox, call progress) at AD 64
     *   1     pitch sweep: the flat baseline at every 4th b0 (0..116), level-matched
     *   2     level sweep: the flat L 24 baseline at gains spanning -50..-10 dBFS
     *   3, 4  voicing sweep: every b1 at L 24 and at L 40
     *
     * @param tonesOnly write only the tone group (to rerun the tones without repeating the voice groups)
     */
    public static ProbePlan generateResponse(double levelDbfs, boolean tonesOnly, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        int b0Ref = b0ForL(24);
        int[] ref = ProbePlan.baseline(b0Ref, gainForLevel(b0Ref, levelDbfs));
        int g = 0;
        plan.append(ProbePlan.Kind.BASE, g, 0, ref, 12);
        for(int id = 5; id <= 122; id++)
        {
            if(id != TONE_AD_SWEEP_ID)
            {
                plan.append(ProbePlan.Kind.TONE, g, id, toneB(id, TONE_AD), TONE_HOLD);
            }
        }
        for(int ad : TONE_ADS)
        {
            plan.append(ProbePlan.Kind.TONE, g, TONE_AD_SWEEP_ID, toneB(TONE_AD_SWEEP_ID, ad), TONE_HOLD);
        }
        for(int id = 128; id <= 163; id++)
        {
            plan.append(ProbePlan.Kind.TONE, g, id, toneB(id, TONE_AD), TONE_HOLD);
        }
        g++;
        if(tonesOnly)
        {
            if(verbose)
            {
                System.out.println("response probes: tone group only");
            }
            return plan;
        }

        plan.append(ProbePlan.Kind.BASE, g, 0, ref, 24);
        for(int b0 = 0; b0 < 120; b0 += 4)
        {
            plan.append(ProbePlan.Kind.PITCH, g, b0, ProbePlan.baseline(b0, gainForLevel(b0, levelDbfs)),
                RESPONSE_HOLD);
        }
        g++;

        plan.append(ProbePlan.Kind.BASE, g, 0, ref, 24);
        java.util.TreeSet<Integer> gains = new java.util.TreeSet<>();
        for(double dbfs = -50; dbfs <= -10; dbfs += 5)
        {
            gains.add(gainForLevel(b0Ref, dbfs));
        }
        for(int b2 : gains)
        {
            plan.append(ProbePlan.Kind.LEVEL, g, b2, ProbePlan.baseline(b0Ref, b2), RESPONSE_HOLD);
        }
        g++;

        for(int L : new int[]{24, 40})
        {
            int b0 = b0ForL(L);
            int[] base = ProbePlan.baseline(b0, gainForLevel(b0, levelDbfs));
            plan.append(ProbePlan.Kind.BASE, g, 0, base, 24);
            for(int b1 = 0; b1 < 32; b1++)
            {
                int[] b = base.clone();
                b[1] = b1;
                plan.append(ProbePlan.Kind.VOICING, g, b1, b, RESPONSE_HOLD);
            }
            g++;
        }
        if(verbose)
        {
            System.out.printf("response probes: %d groups (tones, pitch, level %s, voicing L 24/40)%n", g, gains);
        }
        return plan;
    }

    /**
     * Full pitch sweep: one group per PRBA24 tilt row; each group starts with a level staircase (baseline at
     * -45, -15 and -30 dBFS, 8 frames each: sharp energy steps for the delay estimate, as the level-matched sweep
     * itself is flat in energy), then every voice b0 0..119 held RESPONSE_HOLD frames.
     */
    public static ProbePlan generatePitchSweep(int[] tiltRows, double levelDbfs, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        int b0Ref = b0ForL(24);
        for(int g = 0; g < tiltRows.length; g++)
        {
            int row = tiltRows[g];
            for(double step : new double[]{-45, -15, levelDbfs})
            {
                plan.append(ProbePlan.Kind.BASE, g, 0, ProbePlan.baseline(b0Ref, gainForLevel(b0Ref, step, row), row), 8);
            }
            for(int b0 = 0; b0 < 120; b0++)
            {
                plan.append(ProbePlan.Kind.PITCH, g, b0, ProbePlan.baseline(b0, gainForLevel(b0, levelDbfs, row), row),
                    RESPONSE_HOLD);
            }
            if(verbose)
            {
                System.out.printf(java.util.Locale.ROOT, "group %d: PRBA24 row %d (G2 %+.3f), b0 0..119%n", g, row,
                    jmbe.codec.ambe.ambePlus2.PRBA24.fromValue(row).getG2());
            }
        }
        return plan;
    }

    public static final int GLIDE_HOLD = 60;
    public static final int[] GLIDE_STEPS = {1, 2, 4, 8, 16};

    /**
     * Pitch-glide probe, all on the flat baseline (level-matched per b0), each group decoded from reset and led by a
     * level staircase for the delay estimate. A GLIDE probe's index is glideIndex(label, b0); each held pitch is one
     * probe, each glide or vibrato frame its own short probe.
     *   0, 1  steps from a held base pitch (b0 64 = 150 Hz, b0 96 = 92 Hz): hold 60, then for each step size up to
     *         base + step (hold 60) and back to base (hold 60)
     *   2     glides from b0 50 to 80 and back at 1 step per 1, 2 and 4 frames, each end held 60
     *   3     vibrato around b0 70: +-2 alternating every frame for 100 frames, hold 60, +-4 every 2 frames for 100
     *         frames, hold 60
     */
    public static ProbePlan generateGlide(double levelDbfs, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        Map<Integer, Integer> gains = new HashMap<>();
        java.util.function.IntFunction<int[]> at = b0 -> ProbePlan.baseline(b0,
            gains.computeIfAbsent(b0, k -> gainForLevel(k, levelDbfs)));
        int g = 0;
        for(int base : new int[]{64, 96})
        {
            staircase(plan, g, base, levelDbfs);
            plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(0, base), at.apply(base), GLIDE_HOLD);
            for(int step : GLIDE_STEPS)
            {
                plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(100 + step, base + step), at.apply(base + step), GLIDE_HOLD);
                plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(200 + step, base), at.apply(base), GLIDE_HOLD);
            }
            g++;
        }

        staircase(plan, g, 50, levelDbfs);
        plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(0, 50), at.apply(50), GLIDE_HOLD);
        for(int rate : new int[]{1, 2, 4})
        {
            for(int b0 = 51; b0 <= 80; b0++)
            {
                plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(300 + rate, b0), at.apply(b0), rate);
            }
            plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(310 + rate, 80), at.apply(80), GLIDE_HOLD);
            for(int b0 = 79; b0 >= 50; b0--)
            {
                plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(400 + rate, b0), at.apply(b0), rate);
            }
            plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(410 + rate, 50), at.apply(50), GLIDE_HOLD);
        }
        g++;

        staircase(plan, g, 70, levelDbfs);
        plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(0, 70), at.apply(70), GLIDE_HOLD);
        for(int i = 0; i < 100; i++)
        {
            int b0 = 70 + (i % 2 == 0 ? 2 : -2);
            plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(500, b0), at.apply(b0), 1);
        }
        plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(510, 70), at.apply(70), GLIDE_HOLD);
        for(int i = 0; i < 50; i++)
        {
            int b0 = 70 + (i % 2 == 0 ? 4 : -4);
            plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(501, b0), at.apply(b0), 2);
        }
        plan.append(ProbePlan.Kind.GLIDE, g, glideIndex(511, 70), at.apply(70), GLIDE_HOLD);
        if(verbose)
        {
            System.out.println("glide probe: steps from b0 64 and 96, glides b0 50..80 at 1/1, 1/2, 1/4 frames, vibrato");
        }
        return plan;
    }

    /** Bit error probe: C0 / C1 codeword bit positions flipped for 1..6 errors (spread over data and parity bits). */
    static final int[] ERROR_C0_BITS = {1, 9, 17, 5, 13, 21};
    static final int[] ERROR_C1_BITS = {2, 10, 18, 6, 14, 22};
    /** Base (A) and marker (B) pitch of the bit error probe: a frame decoded rather than repeated sounds at B. */
    static final int ERROR_B0_A = 63;
    static final int ERROR_B0_B = 80;
    static final int[] ERROR_RUNS = {1, 2, 3, 4, 5, 6, 8, 12};
    /** Correctable error loads held 60 frames: {e0, e1}. */
    static final int[][] ERROR_LOADS = {{1, 0}, {1, 1}, {1, 2}, {1, 3}, {2, 3}, {3, 2}};
    static final int[] ERROR_SPECIAL_RUNS = {1, 3, 6};

    public static int c0Mask(int errors)
    {
        int m = 0;
        for(int i = 0; i < errors; i++)
        {
            m |= 1 << (23 - ERROR_C0_BITS[i]);
        }
        return m;
    }

    public static int c1Mask(int errors)
    {
        int m = 0;
        for(int i = 0; i < errors; i++)
        {
            m |= 1 << (22 - ERROR_C1_BITS[i]);
        }
        return m;
    }

    /** Probe index for an ERRORS segment: label * 1000 + parameter. */
    public static int errorIndex(int label, int parameter)
    {
        return label * 1000 + parameter;
    }

    /**
     * Bit error probe, on a voiced baseline at pitch A (b0 63) with error and test frames at pitch B (b0 80), so that
     * a frame the decoder uses sounds at B and a repeated one at A. Four groups, each from reset after a level
     * staircase. Labels (index / 1000):
     *   0     clean frames at A (parameter: a running count)
     *   100   one frame at B with e0 errors in C0 and e1 in C1 (parameter e0 * 10 + e1, 0..5 each), after 8 clean
     *   200   a run of n frames at B with 4 errors in C0 (always detected, never corrected), parameter n, after 20 clean
     *   300   60 frames at A with a correctable load (e0, e1) from ERROR_LOADS (parameter e0 * 10 + e1), then 40 clean
     *   400   a run of n erasure frames (b0 120), 500 a run of n silence frames (b0 124), parameter n, after 20 clean
     */
    public static ProbePlan generateErrors(double levelDbfs, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        int[] a = ProbePlan.baseline(ERROR_B0_A, gainForLevel(ERROR_B0_A, levelDbfs));
        int[] b = ProbePlan.baseline(ERROR_B0_B, gainForLevel(ERROR_B0_B, levelDbfs));
        int g = 0;
        int run = 0;

        staircase(plan, g, ERROR_B0_A, levelDbfs);
        for(int e0 = 0; e0 <= 5; e0++)
        {
            for(int e1 = 0; e1 <= 5; e1++)
            {
                plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 8);
                int start = plan.frames.size();
                plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(100, e0 * 10 + e1), b, 1);
                plan.setErrors(start, 1, c0Mask(e0), c1Mask(e1));
            }
        }
        plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 8);
        g++;

        staircase(plan, g, ERROR_B0_A, levelDbfs);
        for(int n : ERROR_RUNS)
        {
            plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 20);
            int start = plan.frames.size();
            plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(200, n), b, n);
            plan.setErrors(start, n, c0Mask(4), 0);
        }
        plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 20);
        g++;

        staircase(plan, g, ERROR_B0_A, levelDbfs);
        plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 20);
        for(int[] load : ERROR_LOADS)
        {
            int start = plan.frames.size();
            plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(300, load[0] * 10 + load[1]), a, 60);
            plan.setErrors(start, 60, c0Mask(load[0]), c1Mask(load[1]));
            plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 40);
        }
        g++;

        staircase(plan, g, ERROR_B0_A, levelDbfs);
        for(int label = 400; label <= 500; label += 100)
        {
            int[] special = a.clone();
            special[0] = label == 400 ? 120 : 124;
            for(int n : ERROR_SPECIAL_RUNS)
            {
                plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 20);
                plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(label, n), special, n);
            }
        }
        plan.append(ProbePlan.Kind.ERRORS, g, errorIndex(0, run++), a, 20);
        g++;
        if(verbose)
        {
            System.out.println("bit error probe: single errored frames, uncorrectable runs, error loads, erasure and silence");
        }
        return plan;
    }

    /** Timing probe: base pitch, second pitch, level step sizes (dB), repetitions and hold lengths. */
    static final int TIMING_B0 = 63;
    static final int TIMING_B0_B = 75;
    static final int[] TIMING_STEPS_DB = {20, 6};
    static final int TIMING_REPS = 8;
    static final int TIMING_HOLD = 10;

    public static int timingIndex(int label, int parameter)
    {
        return label * 1000 + parameter;
    }

    /**
     * Timing probe at b0 63 and at most -36 dBFS (+20 dB steps stay below the amplitude limiter), one group from reset after a level staircase. Each event type is repeated TIMING_REPS
     * times with TIMING_HOLD frames between changes. Labels (index / 1000; parameter after them):
     *   0     base frames (pitch A, levelDbfs, voiced)
     *   100   level step up by parameter dB (held), 101 the step back down
     *   200   one frame up by parameter dB, 201 one frame down by parameter dB
     *   300   pitch step A -> B (b0 75, level-matched), 301 back to A
     *   310   one frame at pitch B
     *   400   voicing step to all unvoiced (b1 16), 401 back to voiced
     *   410   one unvoiced frame
     */
    public static ProbePlan generateTiming(double levelDbfs, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        levelDbfs = Math.min(levelDbfs, -36); // so that +20 dB stays clear of the amplitude limiter
        int b2 = gainForLevel(TIMING_B0, levelDbfs);
        int[] a = ProbePlan.baseline(TIMING_B0, b2);
        int[] pb = ProbePlan.baseline(TIMING_B0_B, gainForLevel(TIMING_B0_B, levelDbfs));
        int[] uv = a.clone();
        uv[1] = NOISE_B1;
        staircase(plan, 0, TIMING_B0, levelDbfs);
        plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(0, 0), a, 20);
        for(int db : TIMING_STEPS_DB)
        {
            int[] up = ProbePlan.baseline(TIMING_B0, gainForLevel(TIMING_B0, levelDbfs + db));
            int[] down = ProbePlan.baseline(TIMING_B0, gainForLevel(TIMING_B0, levelDbfs - db));
            for(int i = 0; i < TIMING_REPS; i++)
            {
                plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(100, db), up, TIMING_HOLD);
                plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(101, db), a, TIMING_HOLD);
            }
            for(int i = 0; i < TIMING_REPS; i++)
            {
                plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(200, db), up, 1);
                plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(0, 0), a, TIMING_HOLD);
                plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(201, db), down, 1);
                plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(0, 0), a, TIMING_HOLD);
            }
        }
        for(int i = 0; i < TIMING_REPS; i++)
        {
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(300, TIMING_B0_B), pb, TIMING_HOLD);
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(301, TIMING_B0), a, TIMING_HOLD);
        }
        for(int i = 0; i < TIMING_REPS; i++)
        {
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(310, TIMING_B0_B), pb, 1);
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(0, 0), a, TIMING_HOLD);
        }
        for(int i = 0; i < TIMING_REPS; i++)
        {
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(400, NOISE_B1), uv, TIMING_HOLD);
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(401, 0), a, TIMING_HOLD);
        }
        for(int i = 0; i < TIMING_REPS; i++)
        {
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(410, NOISE_B1), uv, 1);
            plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(0, 0), a, TIMING_HOLD);
        }
        plan.append(ProbePlan.Kind.TIMING, 0, timingIndex(0, 0), a, 20);
        if(verbose)
        {
            System.out.println("timing probe: level steps and one-frame events, pitch and voicing steps and events");
        }
        return plan;
    }

    /**
     * Error-rate probe loads: name, C0 codeword bit positions, C1 codeword bit positions (0..11 data, 12..22 parity,
     * C0 bit 23 the overall parity bit).
     */
    static final Object[][] ERROR_RATE_LOADS = {
        {"3+3 standard", new int[]{1, 9, 17}, new int[]{2, 10, 18}},
        {"3+2 standard", new int[]{1, 9, 17}, new int[]{2, 10}},
        {"2+3 standard", new int[]{1, 9}, new int[]{2, 10, 18}},
        {"2+3 C1 data bits", new int[]{1, 9}, new int[]{0, 4, 8}},
        {"2+3 C1 parity bits", new int[]{1, 9}, new int[]{13, 17, 21}},
        {"2+3 C0 parity bits", new int[]{13, 20}, new int[]{2, 10, 18}},
        {"3+2 C0 data bits", new int[]{0, 4, 8}, new int[]{2, 10}},
        {"3+2 C0 parity bits", new int[]{12, 16, 22}, new int[]{2, 10}},
        {"3+2 C0 incl. bit 23", new int[]{1, 9, 23}, new int[]{2, 10}},
        {"3+1", new int[]{1, 9, 17}, new int[]{2}},
        {"1+3", new int[]{1}, new int[]{2, 10, 18}},
        {"0+3", new int[]{}, new int[]{2, 10, 18}},
        {"3+0", new int[]{1, 9, 17}, new int[]{}},
        {"2+2", new int[]{1, 9}, new int[]{2, 10}},
    };
    static final int ERROR_RATE_LOAD = 120;
    static final int ERROR_RATE_GAP = 100;

    static int mask(int[] positions, int width)
    {
        int m = 0;
        for(int p : positions)
        {
            m |= 1 << (width - 1 - p);
        }
        return m;
    }

    /**
     * Error-rate probe: one group (pitch A baseline), then for each ERROR_RATE_LOADS entry 100 clean frames (label 0)
     * and 120 frames with that load (label 600, parameter = load number). The chip's first INVALID frame in a load
     * and its release after it give the error rate it accumulates for each count and bit position.
     */
    public static ProbePlan generateErrorRate(double levelDbfs, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        int[] a = ProbePlan.baseline(ERROR_B0_A, gainForLevel(ERROR_B0_A, levelDbfs));
        staircase(plan, 0, ERROR_B0_A, levelDbfs);
        int run = 0;
        for(int i = 0; i < ERROR_RATE_LOADS.length; i++)
        {
            plan.append(ProbePlan.Kind.ERRORS, 0, errorIndex(0, run++), a, ERROR_RATE_GAP);
            int start = plan.frames.size();
            plan.append(ProbePlan.Kind.ERRORS, 0, errorIndex(600, i), a, ERROR_RATE_LOAD);
            plan.setErrors(start, ERROR_RATE_LOAD, mask((int[])ERROR_RATE_LOADS[i][1], 24),
                mask((int[])ERROR_RATE_LOADS[i][2], 23));
        }
        plan.append(ProbePlan.Kind.ERRORS, 0, errorIndex(0, run++), a, ERROR_RATE_GAP);
        if(verbose)
        {
            System.out.printf("error-rate probe: %d loads of %d frames, %d clean frames before each%n",
                ERROR_RATE_LOADS.length, ERROR_RATE_LOAD, ERROR_RATE_GAP);
        }
        return plan;
    }

    /** Noise probe: all-unvoiced and all-voiced b1 codes. */
    static final int NOISE_B1 = 16;
    static final int[] NOISE_TILT_ROWS = {236, 87, 266};
    static final int[] NOISE_RUNS = {1, 2, 4, 8};
    static final int NOISE_CYCLES = 12;
    static final int[] NOISE_GAPS = {0, 10, 20, 40};

    /** Probe index for a NOISE segment: label * 1000 + parameter. */
    public static int noiseIndex(int label, int parameter)
    {
        return label * 1000 + parameter;
    }

    /**
     * Noise probe, four groups, each from reset after a level staircase. Labels (index / 1000):
     *   0     level: at b0 63, for each level -50..-10 dBFS (parameter = dBFS + 100): 100 voiced then 200 unvoiced
     *         (b1 16), 16 frames each
     *   1     tilt: at b0 84, for each PRBA24 row in NOISE_TILT_ROWS (parameter = row): 300 voiced, 400 unvoiced
     *   2     transitions: at b0 63, NOISE_CYCLES cycles of r voiced then r unvoiced frames for r in NOISE_RUNS
     *         (500 voiced run, 600 unvoiced run, parameter = r)
     *   3     pitch tracking through noise: for each gap k in NOISE_GAPS (parameter = k): 700 voiced at b0 64 (60
     *         frames), 710 unvoiced at b0 96 (k frames, none for k = 0), 720 voiced at b0 96 (40 frames)
     */
    public static ProbePlan generateNoise(double levelDbfs, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        int g = 0;

        staircase(plan, g, 63, levelDbfs);
        for(int dbfs = -50; dbfs <= -10; dbfs += 5)
        {
            int[] v = ProbePlan.baseline(63, gainForLevel(63, dbfs));
            int[] u = v.clone();
            u[1] = NOISE_B1;
            plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(100, dbfs + 100), v, 16);
            plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(200, dbfs + 100), u, 16);
        }
        g++;

        staircase(plan, g, 84, levelDbfs);
        for(int row : NOISE_TILT_ROWS)
        {
            int[] v = ProbePlan.baseline(84, gainForLevel(84, levelDbfs, row), row);
            int[] u = v.clone();
            u[1] = NOISE_B1;
            plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(300, row), v, 16);
            plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(400, row), u, 16);
        }
        g++;

        staircase(plan, g, 63, levelDbfs);
        int[] v63 = ProbePlan.baseline(63, gainForLevel(63, levelDbfs));
        int[] u63 = v63.clone();
        u63[1] = NOISE_B1;
        for(int r : NOISE_RUNS)
        {
            for(int c = 0; c < NOISE_CYCLES; c++)
            {
                plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(500, r), v63, r);
                plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(600, r), u63, r);
            }
        }
        g++;

        staircase(plan, g, 64, levelDbfs);
        int[] v64 = ProbePlan.baseline(64, gainForLevel(64, levelDbfs));
        int[] v96 = ProbePlan.baseline(96, gainForLevel(96, levelDbfs));
        int[] u96 = v96.clone();
        u96[1] = NOISE_B1;
        for(int k : NOISE_GAPS)
        {
            plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(700, k), v64, 60);
            if(k > 0)
            {
                plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(710, k), u96, k);
            }
            plan.append(ProbePlan.Kind.NOISE, g, noiseIndex(720, k), v96, 40);
        }
        g++;
        if(verbose)
        {
            System.out.println("noise probe: levels, tilts, voiced/unvoiced alternation, pitch drop through noise");
        }
        return plan;
    }

    /** Voicing probe pitches: f0 295, 217, 153 Hz and 110..72 Hz in ~8% steps (L 12 to 56). */
    static final int[] VOICING_B0 = {20, 40, 63, 84, 90, 96, 102, 108, 114};
    static final int VOICING_HOLD = 16;

    /**
     * Voicing probe: one group per pitch, each from reset and led by a level staircase, then every b1 code 0..31 on the
     * level-matched flat baseline, VOICING_HOLD frames each. The low pitches put a harmonic every 70..110 Hz, so
     * together they show where the chip's voiced/unvoiced transitions fall to ~15 Hz; the high pitches show whether
     * a transition follows frequency or harmonic number.
     */
    public static ProbePlan generateVoicing(int[] b0s, double levelDbfs, boolean verbose)
    {
        ProbePlan plan = new ProbePlan();
        int g = 0;
        for(int b0 : b0s)
        {
            staircase(plan, g, b0, levelDbfs);
            int[] base = ProbePlan.baseline(b0, gainForLevel(b0, levelDbfs));
            for(int b1 = 0; b1 < 32; b1++)
            {
                int[] b = base.clone();
                b[1] = b1;
                plan.append(ProbePlan.Kind.VOICING, g, b1, b, VOICING_HOLD);
            }
            g++;
        }
        if(verbose)
        {
            System.out.printf("voicing probe: every b1 at b0 %s, %d frames each%n", java.util.Arrays.toString(b0s),
                VOICING_HOLD);
        }
        return plan;
    }

    /**
     * Probe index for a GLIDE segment: label * 1000 + b0. Labels: 0 initial hold; 100 + s step up by s; 200 + s back
     * down after it; 300 + r / 400 + r glide frames up / down at 1 step per r frames; 310 + r / 410 + r the holds after
     * them; 500 / 501 vibrato frames (+-2 every frame / +-4 every 2 frames); 510 / 511 the holds after them.
     */
    public static int glideIndex(int label, int b0)
    {
        return label * 1000 + b0;
    }

    /** Level staircase at the start of a group (sharp energy steps for the delay estimate). */
    static void staircase(ProbePlan plan, int group, int b0, double levelDbfs)
    {
        for(double step : new double[]{-45, -15, levelDbfs})
        {
            plan.append(ProbePlan.Kind.BASE, group, 0, ProbePlan.baseline(b0, gainForLevel(b0, step)), 8);
        }
    }

    static int[] toneB(int id, int ad)
    {
        return new int[]{AmbeFrameEncoder.TONE_B0, id, ad, 0, 0, 0, 0, 0, 0};
    }

    /** First voice b0 whose harmonic count is L. */
    public static int b0ForL(int L)
    {
        for(int b0 = 0; b0 < 120; b0++)
        {
            if(FundamentalFrequency.fromValue(b0).getL() == L)
            {
                return b0;
            }
        }
        throw new IllegalArgumentException("No AMBE+2 voice b0 with L = " + L);
    }

    /** Baseline b2 whose steady-state JMBE output RMS is closest to the target level. */
    static int gainForLevel(int b0, double targetDbfs)
    {
        return gainForLevel(b0, targetDbfs, -1);
    }

    /** As above, for a baseline with the given PRBA24 row (negative: the usual smallest-norm row). */
    static int gainForLevel(int b0, double targetDbfs, int prba24Row)
    {
        int best = 0;
        double bestErr = Double.MAX_VALUE;
        for(int b2 = 0; b2 < 32; b2++)
        {
            int[] b = ProbePlan.baseline(b0, b2, prba24Row);
            List<byte[]> frames = new ArrayList<>();
            for(int i = 0; i < 24; i++)
            {
                frames.add(AmbeFrameEncoder.encode(b));
            }
            double[] pcm = ProbeSupport.synthesize(frames, true);
            double e = 0;
            int n = 0;
            for(int i = pcm.length - 8 * ProbeSupport.FRAME; i < pcm.length; i++)
            {
                e += pcm[i] * pcm[i];
                n++;
            }
            double dbfs = 10 * Math.log10(e / n + 1e-12) - 20 * Math.log10(32768);
            if(Math.abs(dbfs - targetDbfs) < bestErr)
            {
                bestErr = Math.abs(dbfs - targetDbfs);
                best = b2;
            }
        }
        return best;
    }

    static int[] parse(String s)
    {
        return Arrays.stream(s.split(",")).mapToInt(x -> Integer.parseInt(x.trim())).toArray();
    }
}
