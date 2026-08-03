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
import jmbe.codec.ambe.ambePlus2.FundamentalFrequency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bit error probe analysis (ProbeGenerator --errors): what the chip and JMBE do with each errored, erasure or silence
 * frame. Per frame:
 * <ul>
 *   <li>the chip's DCMODE (chip_dcmode.csv, when present);</li>
 *   <li>JMBE's view: the Golay error counts it found, and whether it used the frame, repeated the previous one or
 *   muted (the same logic as AMBESynthesizer.getAudio);</li>
 *   <li>what each one's audio sounds like: power re the steady clean level, and whether pitch B (the errored/test
 *   frames) is heard in this and the next frame or only pitch A (the clean frames): "B" = used, "A" = repeated or
 *   concealed, "-" = muted (more than 20 dB down).</li>
 * </ul>
 * usage: ProbeErrorAnalyzer --dir DIR [--delay D] [--repeat-memory 0..4]; writes results/errors_summary.txt and results/errors_frames.csv
 */
public final class ProbeErrorAnalyzer
{
    static final int N = ProbeResponseAnalyzer.N;
    final ProbeResponseAnalyzer r;
    final Map<Integer, String> dcmode = new HashMap<>();
    final Map<Integer, int[]> jmbeErrors = new HashMap<>();
    final Map<Integer, String> jmbeAction = new HashMap<>();
    double cleanChip;
    double cleanJmbe;
    ProbeSupport.HarmonicFit fitA;
    ProbeSupport.HarmonicFit fitB;

    ProbeErrorAnalyzer(ProbeResponseAnalyzer r) throws Exception
    {
        this.r = r;
        Path dc = r.dir.resolve("chip_dcmode.csv");
        if(Files.exists(dc))
        {
            List<String> lines = Files.readAllLines(dc);
            for(int i = 1; i < lines.size(); i++)
            {
                String[] f = lines.get(i).split(",", 4);
                if(f.length >= 3)
                {
                    dcmode.put(Integer.parseInt(f[0]), f[2]);
                }
            }
        }
        // JMBE's decisions per frame, group by group from reset, as AMBESynthesizer.getAudio makes them
        for(int[] span : r.groupSpan.values())
        {
            AMBEModelParameters previous = new AMBEModelParameters();
            for(int f = span[0]; f < span[1]; f++)
            {
                AMBEFrame frame = new AMBEFrame(r.frames.get(f));
                jmbeErrors.put(f, frame.getErrors().clone());
                if(frame.isToneFrame())
                {
                    jmbeAction.put(f, "tone");
                    continue;
                }
                AMBEModelParameters p = frame.getVoiceParameters(previous);
                String action;
                if(p.isMaxFrameRepeat())
                {
                    action = "mute";
                    previous = jmbe.codec.ambe.AMBEChipResponse.isEnabled() ? p : new AMBEModelParameters();
                }
                else
                {
                    action = p.isErasureFrame() ? "erasure-noise" : p.isRepeatFrame() ? "repeat" :
                        p.getFrameType() == jmbe.codec.FrameType.SILENCE ? "silence" : "use";
                    previous = p;
                }
                jmbeAction.put(f, action);
            }
        }
        double wA = FundamentalFrequency.fromValue(ProbeGenerator.ERROR_B0_A).getFrequency();
        double wB = FundamentalFrequency.fromValue(ProbeGenerator.ERROR_B0_B).getFrequency();
        fitA = new ProbeSupport.HarmonicFit(wA, FundamentalFrequency.fromValue(ProbeGenerator.ERROR_B0_A).getL(), 2 * N);
        fitB = new ProbeSupport.HarmonicFit(wB, FundamentalFrequency.fromValue(ProbeGenerator.ERROR_B0_B).getL(), 2 * N);
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
                case "--repeat-memory" -> jmbe.codec.ambe.AMBEChipResponse.setRepeatMemory(Integer.parseInt(args[++i]));
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        ProbeResponseAnalyzer r = new ProbeResponseAnalyzer(dir, dir.resolve("chip.pcm"), null, delay);
        if(delay == null)
        {
            r.delay = envelopeDelay(r, r.delay);
        }
        r.say("frames %d, chip delay %d samples%s", r.frames.size(), r.delay,
            Files.exists(dir.resolve("chip_dcmode.csv")) ? "" : " (no chip_dcmode.csv: DCMODE columns blank)");
        Path out = dir.resolve("results");
        Files.createDirectories(out);
        ProbeErrorAnalyzer a = new ProbeErrorAnalyzer(r);
        a.reference();
        a.writeFrames(out);
        a.single();
        a.runs();
        a.loads();
        a.special();
        a.errorRate();
        Files.writeString(out.resolve("errors_summary.txt"), r.summary.toString());
        r.say("wrote %s/errors_summary.txt and errors_frames.csv", out);
    }

    /**
     * Delay that best lines up the chip's level envelope (20-sample log power) with JMBE's over the whole run, searched
     * +-240 samples around the energy estimate: the dips and mutes of this probe pin it much better than the steady
     * level staircases do.
     */
    static int envelopeDelay(ProbeResponseAnalyzer r, int around)
    {
        int n = r.jmbe.length / 20;
        double[] j = new double[n];
        for(int i = 0; i < n; i++)
        {
            double s = 0;
            for(int k = 0; k < 20; k++)
            {
                s += r.jmbe[i * 20 + k] * r.jmbe[i * 20 + k];
            }
            j[i] = Math.log10(s / 20 + 1);
        }
        int best = around;
        double bestR = -2;
        for(int d = around - 240; d <= around + 240; d += 2)
        {
            double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
            int m = 0;
            for(int i = 0; i < n; i++)
            {
                int start = i * 20 + d;
                if(start < 0 || start + 20 > r.chip.length)
                {
                    continue;
                }
                double s = 0;
                for(int k = 0; k < 20; k++)
                {
                    s += r.chip[start + k] * r.chip[start + k];
                }
                double c = Math.log10(s / 20 + 1);
                sx += c;
                sy += j[i];
                sxx += c * c;
                syy += j[i] * j[i];
                sxy += c * j[i];
                m++;
            }
            double cov = sxy / m - (sx / m) * (sy / m);
            double rr = cov / Math.sqrt((sxx / m - (sx / m) * (sx / m)) * (syy / m - (sy / m) * (sy / m)));
            if(rr > bestR)
            {
                bestR = rr;
                best = d;
            }
        }
        r.say("chip delay from the level envelope: %d samples (energy estimate %d)", best, around);
        return best;
    }

    List<ProbePlan.Probe> probes(int label)
    {
        return r.plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.ERRORS && p.index / 1000 == label).toList();
    }

    double power(double[] x, int frame, boolean isChip)
    {
        double s = 0;
        for(int i = 0; i < N; i++)
        {
            int k = frame * N + (isChip ? r.delay : 0) + i;
            if(k >= 0 && k < x.length)
            {
                s += x[k] * x[k];
            }
        }
        return s / N;
    }

    /** Steady clean level: the middle frames of the 20-frame clean runs. */
    void reference()
    {
        double c = 0, j = 0;
        int n = 0;
        for(ProbePlan.Probe p : probes(0))
        {
            if(p.length >= 20)
            {
                for(int f = p.start + 8; f < p.start + p.length - 2; f++)
                {
                    c += power(r.chip, f, true);
                    j += power(r.jmbe, f, false);
                    n++;
                }
            }
        }
        cleanChip = c / n;
        cleanJmbe = j / n;
    }

    double levelDb(int frame, boolean isChip)
    {
        return 10 * Math.log10(power(isChip ? r.chip : r.jmbe, frame, isChip) / (isChip ? cleanChip : cleanJmbe) + 1e-12);
    }

    /**
     * Share of pitch B in the audio of frames [frame, frame + 2): explained fraction of a pitch-B harmonic fit over
     * that of A plus B. A frame the decoder uses is about half of that window (~0.3..0.5); a repeated one leaves ~0.
     */
    double shareB(int frame, boolean isChip)
    {
        double[] x = isChip ? r.chip : r.jmbe;
        int start = frame * N + (isChip ? r.delay : 0);
        double a = fitA.explainedFraction(x, start);
        double b = fitB.explainedFraction(x, start);
        return a + b > 0 ? b / (a + b) : 0;
    }

    /** "A" (pitch A only: repeated or concealed), "B" (pitch B present: used) or "-" (muted, > 20 dB down). */
    String pitch(int frame, boolean isChip)
    {
        if(levelDb(frame, isChip) < -20)
        {
            return "-";
        }
        return shareB(frame, isChip) > 0.25 ? "B" : "A";
    }

    String dc(int frame)
    {
        String v = dcmode.get(frame);
        return v == null ? "    " : v;
    }

    String errors(int frame)
    {
        int[] e = r.plan.errorMasks.get(frame);
        return e == null ? "0,0" : Integer.bitCount(e[0]) + "," + Integer.bitCount(e[1]);
    }

    void writeFrames(Path out) throws Exception
    {
        List<String> csv = new ArrayList<>();
        csv.add("frame,group,label,param,sent_b0,errors_sent,jmbe_e0,jmbe_e1,jmbe_action,jmbe_db,jmbe_share_b,chip_dcmode," +
            "chip_db,chip_share_b");
        for(ProbePlan.Probe p : r.plan.probes)
        {
            for(int f = p.start; f < p.start + p.length; f++)
            {
                int[] je = jmbeErrors.getOrDefault(f, new int[2]);
                csv.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,\"%s\",%d,%d,%s,%.2f,%.3f,%s,%.2f,%.3f", f, p.group,
                    p.index / 1000, p.index % 1000, p.b0(), errors(f), je[0], je[1], jmbeAction.get(f),
                    levelDb(f, false), shareB(f, false), dcmode.getOrDefault(f, ""), levelDb(f, true), shareB(f, true)));
            }
        }
        Files.write(out.resolve("errors_frames.csv"), csv);
    }

    /** One frame's summary: JMBE action/pitch/dB vs chip DCMODE/pitch/dB. */
    String cell(int f)
    {
        int[] je = jmbeErrors.getOrDefault(f, new int[2]);
        return String.format(Locale.ROOT, "JMBE %d,%d %-7s %s (B %.2f) %+5.1f dB | chip %s %s (B %.2f) %+5.1f dB", je[0],
            je[1], jmbeAction.get(f), pitch(f, false), shareB(f, false), levelDb(f, false), dc(f), pitch(f, true),
            shareB(f, true), levelDb(f, true));
    }

    void single()
    {
        List<ProbePlan.Probe> probes = probes(100);
        if(probes.isEmpty())
        {
            return;
        }
        r.say("H1. one frame at pitch B with e0 errors in C0 and e1 in C1 between clean frames at pitch A");
        r.say("    sent    JMBE: errors found, action, audio over this and the next frame: B = pitch B present (frame " +
            "used), A = only A (repeated), - = muted, with the B share and dB re clean | chip: DCMODE, the same");
        for(ProbePlan.Probe p : probes)
        {
            int f = p.start;
            r.say("    %d,%d   %s", (p.index % 1000) / 10, p.index % 10, cell(f));
        }
    }

    void runs()
    {
        List<ProbePlan.Probe> probes = probes(200);
        if(probes.isEmpty())
        {
            return;
        }
        r.say("H2. runs of n frames with 4 errors in C0 (uncorrectable), between clean frames; per frame from the last " +
            "clean frame to 8 after the run: pitch / dB re clean (JMBE above, chip below), chip DCMODE");
        for(ProbePlan.Probe p : probes)
        {
            StringBuilder j = new StringBuilder();
            StringBuilder c = new StringBuilder();
            StringBuilder d = new StringBuilder();
            StringBuilder a = new StringBuilder();
            for(int f = p.start - 1; f < p.start + p.length + 8; f++)
            {
                boolean in = f >= p.start && f < p.start + p.length;
                j.append(String.format(Locale.ROOT, " %s%+5.1f%s", pitch(f, false), levelDb(f, false), in ? "*" : " "));
                c.append(String.format(Locale.ROOT, " %s%+5.1f%s", pitch(f, true), levelDb(f, true), in ? "*" : " "));
                d.append(String.format(Locale.ROOT, " %8s", dc(f)));
                a.append(String.format(Locale.ROOT, " %8s", abbreviate(jmbeAction.get(f))));
            }
            r.say("    n = %d (* = errored frame)", p.index % 1000);
            r.say("      JMBE   %s", j);
            r.say("      action %s", a);
            r.say("      chip   %s", c);
            r.say("      DCMODE %s", d);
        }
    }

    static String abbreviate(String action)
    {
        return action == null ? "" : action.length() > 7 ? action.substring(0, 7) : action;
    }

    void loads()
    {
        List<ProbePlan.Probe> probes = probes(300);
        if(probes.isEmpty())
        {
            return;
        }
        r.say("H3. 60 frames with a correctable error load (same parameters as the clean frames), then 40 clean: level re " +
            "clean (dB) per 10-frame block, and actions / DCMODE counts");
        for(ProbePlan.Probe p : probes)
        {
            StringBuilder j = new StringBuilder();
            StringBuilder c = new StringBuilder();
            for(int b = 0; b < 10; b++)
            {
                double sj = 0, sc = 0;
                for(int f = p.start + b * 10; f < p.start + b * 10 + 10; f++)
                {
                    sj += power(r.jmbe, f, false);
                    sc += power(r.chip, f, true);
                }
                j.append(String.format(Locale.ROOT, "%+6.1f", 10 * Math.log10(sj / 10 / cleanJmbe + 1e-12)));
                c.append(String.format(Locale.ROOT, "%+6.1f", 10 * Math.log10(sc / 10 / cleanChip + 1e-12)));
            }
            Map<String, Integer> ja = new java.util.TreeMap<>();
            Map<String, Integer> cd = new java.util.TreeMap<>();
            for(int f = p.start; f < p.start + p.length; f++)
            {
                ja.merge(jmbeAction.get(f), 1, Integer::sum);
                cd.merge(dc(f), 1, Integer::sum);
            }
            int[] je = jmbeErrors.get(p.start);
            r.say("    load %d,%d (JMBE finds %d,%d per frame); blocks: 6 with errors, then 4 clean", (p.index % 1000) / 10,
                p.index % 10, je[0], je[1]);
            r.say("      JMBE %s   %s", j, ja);
            r.say("      chip %s   DCMODE %s", c, cd);
            // first JMBE / chip mute frame
            int jm = -1, cm = -1;
            for(int f = p.start; f < p.start + 100; f++)
            {
                if(jm < 0 && levelDb(f, false) < -20)
                {
                    jm = f - p.start;
                }
                if(cm < 0 && levelDb(f, true) < -20)
                {
                    cm = f - p.start;
                }
            }
            r.say("      first frame more than 20 dB down: JMBE %s, chip %s", jm < 0 ? "none" : "" + jm,
                cm < 0 ? "none" : "" + cm);
        }
    }

    /**
     * Error-rate probe (ProbeGenerator --error-rate): per load, the first frame each decoder treats as invalid
     * (chip: DCMODE 0x0020; JMBE: repeat or mute) and how many clean frames after the load it stays invalid. Then the
     * frame-by-frame agreement of the two over the whole run.
     */
    void errorRate()
    {
        List<ProbePlan.Probe> loads = probes(600);
        if(loads.isEmpty())
        {
            return;
        }
        r.say("H5. error-rate loads (%d frames each after 100 clean): onset = first invalid frame in the load, release = " +
            "clean frames after the load until valid again (chip DCMODE 0x0020; JMBE repeat or mute)", loads.get(0).length);
        r.say("      load                    C0 bits / C1 bits   errors   JMBE onset/release   chip onset/release");
        for(ProbePlan.Probe p : loads)
        {
            int n = p.index % 1000;
            int[] mask = r.plan.errorMasks.get(p.start);
            String bits = positions(mask[0], 24) + " / " + positions(mask[1], 23);
            int[] je = jmbeErrors.get(p.start);
            int[] j = onsetRelease(p, f -> !"use".equals(jmbeAction.get(f)));
            int[] c = onsetRelease(p, f -> "0020".equals(dcmode.get(f)));
            r.say("      %-22s %-20s %d,%d       %5s / %-5s          %5s / %-5s", (String)ProbeGenerator.ERROR_RATE_LOADS[n][0],
                bits, je[0], je[1], j[0] < 0 ? "none" : "" + j[0], j[1] < 0 ? "-" : "" + j[1],
                c[0] < 0 ? "none" : "" + c[0], c[1] < 0 ? "-" : "" + c[1]);
        }
        if(!dcmode.isEmpty())
        {
            int agree = 0, n = 0;
            for(Map.Entry<Integer, String> e : dcmode.entrySet())
            {
                String action = jmbeAction.get(e.getKey());
                if(action != null)
                {
                    agree += ("0020".equals(e.getValue()) == !"use".equals(action)) ? 1 : 0;
                    n++;
                }
            }
            r.say("   valid / invalid agreement, all frames: %d of %d", agree, n);
        }
    }

    int[] onsetRelease(ProbePlan.Probe p, java.util.function.IntPredicate invalid)
    {
        int onset = -1;
        for(int k = 0; k < p.length; k++)
        {
            if(invalid.test(p.start + k))
            {
                onset = k;
                break;
            }
        }
        int release = -1;
        if(onset >= 0)
        {
            for(int m = 0; m < 100 && p.start + p.length + m < r.frames.size(); m++)
            {
                if(!invalid.test(p.start + p.length + m))
                {
                    release = m;
                    break;
                }
            }
        }
        return new int[]{onset, release};
    }

    static String positions(int mask, int width)
    {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < width; i++)
        {
            if(((mask >> (width - 1 - i)) & 1) == 1)
            {
                sb.append(sb.length() > 0 ? "," : "").append(i);
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    void special()
    {
        r.say("H4. erasure (b0 120) and silence (b0 124) frames, valid FEC, between clean frames; per frame from the " +
            "last clean frame to 6 after: pitch / dB re clean, JMBE action, chip DCMODE");
        for(int label = 400; label <= 500; label += 100)
        {
            for(ProbePlan.Probe p : probes(label))
            {
                StringBuilder j = new StringBuilder();
                StringBuilder c = new StringBuilder();
                StringBuilder d = new StringBuilder();
                StringBuilder a = new StringBuilder();
                for(int f = p.start - 1; f < p.start + p.length + 6; f++)
                {
                    boolean in = f >= p.start && f < p.start + p.length;
                    j.append(String.format(Locale.ROOT, " %s%+5.1f%s", pitch(f, false), levelDb(f, false), in ? "*" : " "));
                    c.append(String.format(Locale.ROOT, " %s%+5.1f%s", pitch(f, true), levelDb(f, true), in ? "*" : " "));
                    d.append(String.format(Locale.ROOT, " %8s", dc(f)));
                    a.append(String.format(Locale.ROOT, " %8s", abbreviate(jmbeAction.get(f))));
                }
                r.say("    %s x %d", label == 400 ? "erasure" : "silence", p.index % 1000);
                r.say("      JMBE   %s", j);
                r.say("      action %s", a);
                r.say("      chip   %s", c);
                r.say("      DCMODE %s", d);
            }
        }
    }
}
