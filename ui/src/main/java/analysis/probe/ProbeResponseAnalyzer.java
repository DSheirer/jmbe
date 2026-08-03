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
import jmbe.codec.ambe.AMBEModelParameters;
import jmbe.codec.ambe.ambePlus2.FundamentalFrequency;
import jmbe.codec.ambe.ambePlus2.PRBA24;
import jmbe.codec.ambe.tone.Tone;
import jmbe.codec.ambe.tone.ToneGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Analyzes a {@code ProbeGenerator --response} run. Where the table probes compared spectral amplitudes on steady,
 * fully voiced frames, these look at what is left: the chip's output frequency response, its tone frames, level
 * dependence and voicing.
 *
 *   A. Tones. Which tone frame layout the chip decodes as a tone, its tone frequency response (chip / JMBE per tone,
 *      normalized to 0.5..2 kHz), and its amplitude law over AD. JMBE's tone generator is a plain oscillator with
 *      linear gain, so the tone response is the chip's response for a path with no spectral amplitudes in it.
 *   B. Pitch sweep. Per harmonic chip - JMBE (audio vs audio) on the flat baseline at 30 pitches, fitted as a fixed
 *      response of absolute frequency, H(f), and as a function of harmonic position, G(l/L). Compare H with the tone
 *      response: the same curve means an output filter after synthesis; a curve on voice only means a step in the
 *      spectral amplitude (parameter) path.
 *   C. Level sweep. Overall chip - JMBE offset and the high-band shelf against output level.
 *   D. Voicing. Every b1 at L 24 and 40: per band, the harmonic (voiced) and residual (noise) power of the chip and of
 *      JMBE, whether each band comes out voiced in each, and the unvoiced level difference.
 * JMBE is synthesized group by group from a reset decoder, as the chip runner decodes each group from reset.
 * Writes results/response_summary.txt and CSVs per section.
 *
 * usage: java analysis.probe.ProbeResponseAnalyzer --dir DIR [--pcm chip.pcm] [--pcm-endian auto|big|little]
 *            [--delay N]
 */
public final class ProbeResponseAnalyzer
{
    static final int N = ProbeSupport.FRAME;
    static final double FS = 8000.0;
    static final double CLIP = 32000.0;
    static final double GATE_DB = 12.0;
    static final double BIN_HZ = 250.0;

    final Path dir;
    final ProbePlan plan;
    final List<byte[]> frames;
    final double[] chip;
    final double[] jmbe;
    /** JMBE with AMBEChipResponse off (the published decode), to measure the chip's response itself. */
    final double[] jmbeRaw;
    int delay;
    final Map<Integer, int[]> groupSpan = new TreeMap<>();   // group -> {first frame, end frame}
    final StringBuilder summary = new StringBuilder();
    final Map<String, ProbeSupport.HarmonicFit> fits = new HashMap<>();
    double[] voicedResponse;                                   // H(f) from section B, per BIN_HZ bin, or null

    ProbeResponseAnalyzer(Path dir, Path pcm, Boolean bigEndian, Integer fixedDelay) throws Exception
    {
        this.dir = dir;
        this.plan = ProbePlan.read(dir);
        this.frames = ProbeSupport.readFrames(dir.resolve("frames.hex"));
        this.chip = ProbeSupport.readPcm(pcm, bigEndian);
        if(chip.length < frames.size() * ProbeSupport.FRAME * 0.98)
        {
            throw new IllegalStateException("chip.pcm holds " + chip.length / ProbeSupport.FRAME + " frames, plan has " +
                frames.size());
        }
        this.delay = fixedDelay != null ? fixedDelay : 0;
        for(ProbePlan.Probe p : plan.probes)
        {
            int[] span = groupSpan.computeIfAbsent(p.group, k -> new int[]{Integer.MAX_VALUE, 0});
            span[0] = Math.min(span[0], p.start);
            span[1] = Math.max(span[1], p.start + p.length);
        }
        jmbe = new double[frames.size() * N];
        jmbeRaw = new double[frames.size() * N];
        boolean response = AMBEChipResponse.isEnabled();
        for(int[] span : groupSpan.values())
        {
            double[] x = ProbeSupport.synthesize(frames.subList(span[0], span[1]), true);
            System.arraycopy(x, 0, jmbe, span[0] * N, x.length);
            AMBEChipResponse.setEnabled(false);
            double[] y = ProbeSupport.synthesize(frames.subList(span[0], span[1]), true);
            AMBEChipResponse.setEnabled(response);
            System.arraycopy(y, 0, jmbeRaw, span[0] * N, y.length);
        }
        if(fixedDelay == null)
        {
            delay = estimateDelay();
        }
    }

    /**
     * Delay for a tone-only plan: the shift at which single-tone fits at the sent frequencies explain the most of the
     * chip's energy (a run of equal-level tones gives an energy correlation nothing to lock onto, and its peak moved
     * with JMBE's tone levels). Coarse 4-sample search over -800..1600, then 1-sample refinement; every frame of each
     * tone is scored, transitions included.
     */
    int estimateDelayFromTones()
    {
        List<ProbePlan.Probe> tones = plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.TONE && p.b[1] < 128 &&
            p.b[1] != ProbeGenerator.TONE_AD_SWEEP_ID).limit(40).toList();
        if(tones.isEmpty())
        {
            return 0;
        }
        java.util.function.IntToDoubleFunction score = d -> {
            double s = 0;
            int n = 0;
            for(ProbePlan.Probe p : tones)
            {
                ProbeSupport.HarmonicFit hf = fit(2 * Math.PI * p.b[1] * 31.25 / FS, 1);
                // Every frame of the tone: only the true delay keeps them all inside the tone (the steady frames
                // alone leave a plateau ~3 frames wide)
                for(int f = p.start; f < p.start + p.length; f++)
                {
                    s += hf.explainedFraction(chip, f * N + d);
                    n++;
                }
            }
            return s / n;
        };
        int best = 0;
        double bestScore = -1;
        for(int d = -800; d <= 1600; d += 4)
        {
            double v = score.applyAsDouble(d);
            if(v > bestScore)
            {
                bestScore = v;
                best = d;
            }
        }
        int coarse = best;
        for(int d = coarse - 4; d <= coarse + 4; d++)
        {
            double v = score.applyAsDouble(d);
            if(v > bestScore)
            {
                bestScore = v;
                best = d;
            }
        }
        return best;
    }

    /**
     * Chip output delay from per-frame log energies (block log energies correlated with JMBE's), over the voice groups only when the
     * plan has any: a long run of equal-level tones gives the energy correlation nothing to lock onto (the generic
     * estimate landed at the edge of its range on a plan that starts with the tone group). Search -800..1600 samples.
     */
    int estimateDelay()
    {
        List<int[]> spans = new ArrayList<>();
        for(Map.Entry<Integer, int[]> e : groupSpan.entrySet())
        {
            boolean tones = plan.probes.stream().anyMatch(p -> p.group == e.getKey() && p.kind == ProbePlan.Kind.TONE);
            if(!tones)
            {
                spans.add(e.getValue());
            }
        }
        if(spans.isEmpty())
        {
            return estimateDelayFromTones();
        }
        List<Integer> blocks = new ArrayList<>();
        for(int[] span : spans)
        {
            for(int f = span[0]; f < span[1]; f++)
            {
                blocks.add(f);
            }
        }
        double[] ej = new double[blocks.size()];
        for(int i = 0; i < ej.length; i++)
        {
            double r = rms(jmbe, blocks.get(i), false);
            ej[i] = Math.log10(r * r * N + 1.0);
        }
        int best = 0;
        double bestCorr = -2;
        for(int d = -800; d <= 1600; d++)
        {
            double[] ec = new double[ej.length];
            for(int i = 0; i < ec.length; i++)
            {
                double s = 0;
                int off = blocks.get(i) * N + d;
                for(int k = 0; k < N; k++)
                {
                    int j = off + k;
                    double v = j >= 0 && j < chip.length ? chip[j] : 0;
                    s += v * v;
                }
                ec[i] = Math.log10(s + 1.0);
            }
            double c = ProbeSupport.pearson(ej, ec);
            if(c > bestCorr)
            {
                bestCorr = c;
                best = d;
            }
        }
        return best;
    }

    public static void main(String[] args) throws Exception
    {
        Path dir = null;
        Path pcm = null;
        Boolean bigEndian = null;
        Integer delay = null;
        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--dir" -> dir = Paths.get(args[++i]);
                case "--pcm" -> pcm = Paths.get(args[++i]);
                case "--pcm-endian" -> {
                    String v = args[++i];
                    bigEndian = v.equals("auto") ? null : v.equals("big");
                }
                case "--delay" -> delay = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if(dir == null)
        {
            System.err.println("usage: ProbeResponseAnalyzer --dir DIR [--pcm chip.pcm] [--pcm-endian auto|big|little] " +
                "[--delay N]");
            System.exit(1);
        }
        ProbeResponseAnalyzer r = new ProbeResponseAnalyzer(dir, pcm != null ? pcm : dir.resolve("chip.pcm"), bigEndian,
            delay);
        r.run();
    }

    void run() throws Exception
    {
        Path out = dir.resolve("results");
        Files.createDirectories(out);
        say("frames %d, chip delay %d samples", frames.size(), delay);
        pitch(out);
        firstHarmonic(out);
        tones(out);
        level(out);
        voicing(out);
        glide(out);
        Files.writeString(out.resolve("response_summary.txt"), summary.toString());
        System.out.println("wrote " + out);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    void say(String format, Object... args)
    {
        // Pre-built text (no args) is printed as is: it can hold a literal '%' (e.g. "explained 50%")
        String s = args.length == 0 ? format : String.format(Locale.ROOT, format, args);
        System.out.println(s);
        summary.append(s).append('\n');
    }

    ProbeSupport.HarmonicFit fit(double w0, int L)
    {
        return fits.computeIfAbsent(w0 + "/" + L, k -> new ProbeSupport.HarmonicFit(w0, L, N));
    }

    boolean clipped(int frame)
    {
        for(int i = 0; i < N; i++)
        {
            int k = frame * N + delay + i;
            if(k >= 0 && k < chip.length && Math.abs(chip[k]) >= CLIP)
            {
                return true;
            }
        }
        return false;
    }

    static double db(double v)
    {
        return 10 * Math.log10(Math.max(v, 1e-12));
    }

    /** RMS of a frame (chip offset by the delay when isChip). */
    double rms(double[] x, int frame, boolean isChip)
    {
        double s = 0;
        int off = frame * N + (isChip ? delay : 0);
        for(int i = 0; i < N; i++)
        {
            int k = off + i;
            double v = k >= 0 && k < x.length ? x[k] : 0;
            s += v * v;
        }
        return Math.sqrt(s / N);
    }

    /** Harmonic fit coefficients for a frame: c[0] DC, c[2l-1], c[2l] the cos/sin of harmonic l. */
    double[] coefficients(ProbeSupport.HarmonicFit hf, double[] x, int start)
    {
        double[] proj = new double[hf.M];
        for(int n = 0; n < N; n++)
        {
            int k = start + n;
            double v = (k >= 0 && k < x.length) ? x[k] : 0.0;
            for(int i = 0; i < hf.M; i++)
            {
                proj[i] += hf.basis[i][n] * v;
            }
        }
        double[] c = new double[hf.M];
        for(int i = 0; i < hf.M; i++)
        {
            double s = 0;
            for(int j = 0; j < hf.M; j++)
            {
                s += hf.inverse[i][j] * proj[j];
            }
            c[i] = s;
        }
        return c;
    }

    int steadyFrom(ProbePlan.Probe p)
    {
        return p.start + Math.max(3, p.length / 2);
    }

    /**
     * End (exclusive) of a probe's steady frames: one frame short of the probe's end, so that a chip delay of up to a
     * frame does not pull the next probe's onset into the measurement.
     */
    int steadyTo(ProbePlan.Probe p)
    {
        return p.start + p.length - 1;
    }

    static int bin(double hz)
    {
        return (int)Math.min(Math.max(hz / BIN_HZ, 0), FS / 2 / BIN_HZ - 1);
    }

    static String binHeader()
    {
        StringBuilder sb = new StringBuilder("   kHz  ");
        for(int b = 0; b < FS / 2 / BIN_HZ; b++)
        {
            sb.append(String.format(Locale.ROOT, "%5.2f", b * BIN_HZ / 1000));
        }
        return sb.toString();
    }

    static String binRow(String label, double[] v)
    {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "   %-6s", label));
        for(double x : v)
        {
            sb.append(Double.isNaN(x) ? "    ." : String.format(Locale.ROOT, "%+5.1f", x));
        }
        return sb.toString();
    }

    /** Mean of v over bins whose centre lies in [loHz, hiHz). */
    static double bandMean(double[] v, double loHz, double hiHz)
    {
        double s = 0;
        int n = 0;
        for(int b = 0; b < v.length; b++)
        {
            double c = (b + 0.5) * BIN_HZ;
            if(c >= loHz && c < hiHz && !Double.isNaN(v[b]))
            {
                s += v[b];
                n++;
            }
        }
        return n > 0 ? s / n : Double.NaN;
    }

    /** Per-harmonic chip - JMBE dB (audio vs audio) over a probe's steady frames, gated on the chip's floor. */
    List<double[]> harmonicResidual(ProbePlan.Probe p)
    {
        return harmonicResidual(p, jmbe);
    }

    /** As above against a given JMBE reference (jmbe, or jmbeRaw for the published decode). */
    List<double[]> harmonicResidual(ProbePlan.Probe p, double[] reference)
    {
        List<double[]> out = new ArrayList<>(); // {l, f_hz, chip_db, jmbe_db}
        int L = p.L();
        double w0 = FundamentalFrequency.fromValue(p.b0()).getFrequency();
        ProbeSupport.HarmonicFit hf = fit(w0, L);
        double[] sc = new double[L + 1];
        double[] sj = new double[L + 1];
        int[] n = new int[L + 1];
        for(int f = steadyFrom(p); f < steadyTo(p); f++)
        {
            if(clipped(f))
            {
                continue;
            }
            double[] ac = new double[L + 1];
            double[] aj = new double[L + 1];
            double resid = hf.fit(chip, f * N + delay, ac);
            hf.fit(reference, f * N, aj);
            double floor = 20 * Math.log10(Math.max(resid * 2.0 / Math.sqrt(N), 1e-9));
            for(int l = 1; l <= L; l++)
            {
                double dc = 20 * Math.log10(Math.max(ac[l], 1e-9));
                if(dc < floor + GATE_DB)
                {
                    continue;
                }
                sc[l] += dc;
                sj[l] += 20 * Math.log10(Math.max(aj[l], 1e-9));
                n[l]++;
            }
        }
        for(int l = 1; l <= L; l++)
        {
            if(n[l] > 0)
            {
                out.add(new double[]{l, l * w0 * FS / (2 * Math.PI), sc[l] / n[l], sj[l] / n[l]});
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ B. pitch

    void pitch(Path out) throws Exception
    {
        List<ProbePlan.Probe> probes = plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.PITCH).toList();
        if(probes.isEmpty())
        {
            return;
        }
        List<String> csv = new ArrayList<>();
        csv.add("b0,L,f0_hz,l,f_hz,chip_db,jmbe_db,residual_db");
        List<double[]> rows = new ArrayList<>(); // {probeIndex, f_hz, l/L, residual}
        for(int i = 0; i < probes.size(); i++)
        {
            ProbePlan.Probe p = probes.get(i);
            double f0 = FundamentalFrequency.fromValue(p.b0()).getFrequency() * FS / (2 * Math.PI);
            for(double[] h : harmonicResidual(p))
            {
                rows.add(new double[]{i, h[1], h[0] / p.L(), h[2] - h[3]});
                csv.add(String.format(Locale.ROOT, "%d,%d,%.1f,%d,%.1f,%.3f,%.3f,%.3f", p.b0(), p.L(), f0, (int)h[0],
                    h[1], h[2], h[3], h[2] - h[3]));
            }
        }
        Files.write(out.resolve("response_pitch.csv"), csv);
        if(rows.isEmpty())
        {
            say("B. pitch sweep: no harmonics above the chip's noise floor (check the delay, %d samples)", delay);
            return;
        }

        int nb = (int)(FS / 2 / BIN_HZ);
        double[] hF = fitResponse(rows, probes.size(), nb, r -> bin(r[1]));
        double[] gP = fitResponse(rows, probes.size(), 16, r -> (int)Math.min(15, Math.floor(r[2] * 16 - 1e-9)));
        double rmsF = residualRms(rows, probes.size(), hF, r -> bin(r[1]));
        double rmsP = residualRms(rows, probes.size(), gP, r -> (int)Math.min(15, Math.floor(r[2] * 16 - 1e-9)));
        double rms0 = residualRms(rows, probes.size(), new double[nb], r -> bin(r[1]));
        double level = rows.stream().mapToDouble(r -> r[3]).sorted().toArray()[rows.size() / 2];

        say("B. pitch sweep: %d pitches, %d harmonics. Chip - JMBE level (median) %+.2f dB", probes.size(), rows.size(),
            level);
        say("   per-pitch offsets removed: RMS %.2f dB; with one response of absolute frequency H(f) %.2f dB; with one " +
            "response of harmonic position G(l/L) %.2f dB", rms0, rmsF, rmsP);
        normalize(hF);
        voicedResponse = hF;
        say("   H(f), dB relative to its 0.5..2 kHz mean:");
        say(binHeader());
        say(binRow("H(f)", hF));
        StringBuilder g = new StringBuilder("   G(l/L) in 16ths: ");
        normalizeIndex(gP);
        for(double v : gP)
        {
            g.append(Double.isNaN(v) ? "    ." : String.format(Locale.ROOT, "%+5.1f", v));
        }
        say(g.toString());
    }

    // ------------------------------------------------------------------------------------------------ B2. first harmonic

    /**
     * The chip's low harmonics per pitch, measured against the published decode (AMBEChipResponse off): chip - JMBE
     * for l = 1..3, relative to that pitch's 0.5..2 kHz mean, per PRBA24 row (tilt). Compared with the first harmonic
     * steady-pitch roll-off in AMBEChipResponse (a pitch sweep that steps the pitch down also shows the chip's
     * pitch-tracking attenuation, see section E). Writes results/response_first_harmonic.csv.
     */
    void firstHarmonic(Path out) throws Exception
    {
        List<ProbePlan.Probe> probes = plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.PITCH).toList();
        if(probes.isEmpty())
        {
            return;
        }
        List<String> csv = new ArrayList<>();
        csv.add("tilt_row,b0,L,f0_hz,l1_db,l2_db,l3_db,steady_model_l1_db,l1_vs_model_db");
        double rawSs = 0, modelSs = 0;
        int nl1 = 0;
        // b0 -> tilt row -> {l1, l2, l3}
        Map<Integer, Map<Integer, double[]>> byPitch = new TreeMap<>();
        java.util.TreeSet<Integer> tilts = new java.util.TreeSet<>((x, y) -> Double.compare(
            PRBA24.fromValue(x).getG2(), PRBA24.fromValue(y).getG2()));
        for(ProbePlan.Probe p : probes)
        {
            List<double[]> h = harmonicResidual(p, jmbeRaw);
            double mid = 0;
            int n = 0;
            for(double[] x : h)
            {
                if(x[1] >= 500 && x[1] < 2000)
                {
                    mid += x[2] - x[3];
                    n++;
                }
            }
            if(n == 0)
            {
                continue;
            }
            mid /= n;
            double[] low = {Double.NaN, Double.NaN, Double.NaN};
            for(double[] x : h)
            {
                int l = (int)x[0];
                if(l >= 1 && l <= 3)
                {
                    low[l - 1] = x[2] - x[3] - mid;
                }
            }
            double l1Model = Double.NaN;
            List<double[]> hm = harmonicResidual(p, jmbe);
            double midModel = 0;
            int nm = 0;
            for(double[] x : hm)
            {
                if(x[1] >= 500 && x[1] < 2000)
                {
                    midModel += x[2] - x[3];
                    nm++;
                }
            }
            for(double[] x : hm)
            {
                if((int)x[0] == 1 && nm > 0)
                {
                    l1Model = x[2] - x[3] - midModel / nm;
                }
            }
            if(!Double.isNaN(low[0]) && !Double.isNaN(l1Model))
            {
                rawSs += low[0] * low[0];
                modelSs += l1Model * l1Model;
                nl1++;
            }
            int row = p.b[3];
            tilts.add(row);
            byPitch.computeIfAbsent(p.b0(), k -> new TreeMap<>()).put(row, low);
            double f0 = FundamentalFrequency.fromValue(p.b0()).getFrequency() * FS / (2 * Math.PI);
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%.2f,%.3f,%.3f,%.3f,%.3f,%.3f", row, p.b0(), p.L(), f0, low[0],
                low[1], low[2], AMBEChipResponse.highPassDb(f0), l1Model));
        }
        Files.write(out.resolve("response_first_harmonic.csv"), csv);

        StringBuilder head = new StringBuilder("B2. low harmonics, chip - JMBE (published decode) re 0.5..2 kHz, dB: " +
            "l1 / l2 / l3 per PRBA24 row");
        for(int row : tilts)
        {
            head.append(String.format(Locale.ROOT, "  [%d: G2 %+.2f]", row,
                PRBA24.fromValue(row).getG2()));
        }
        say("%s", head.toString());
        say("    b0   L  f0 Hz  model l1 | per tilt: l1 / l2 / l3");
        int off = 0, total = 0;
        for(Map.Entry<Integer, Map<Integer, double[]>> e : byPitch.entrySet())
        {
            int b0 = e.getKey();
            double f0 = FundamentalFrequency.fromValue(b0).getFrequency() * FS / (2 * Math.PI);
            double table = AMBEChipResponse.highPassDb(f0);
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "   %3d %3d %6.1f  %+5.2f   |", b0,
                FundamentalFrequency.fromValue(b0).getL(), f0, table));
            boolean flag = false;
            for(int row : tilts)
            {
                double[] v = e.getValue().get(row);
                if(v == null)
                {
                    sb.append("        .          ");
                    continue;
                }
                sb.append(String.format(Locale.ROOT, "  %+5.2f/%+5.2f/%+5.2f", v[0], v[1], v[2]));
                if(!Double.isNaN(v[0]))
                {
                    total++;
                    if(Math.abs(v[0] - table) > 0.5)
                    {
                        off++;
                        flag = true;
                    }
                }
            }
            say("%s%s", sb.toString(), flag ? "  *" : "");
        }
        say("   l1 rms over %d probes: %.3f dB vs published decode, %.3f dB vs JMBE with AMBEChipResponse", nl1,
            Math.sqrt(rawSs / Math.max(1, nl1)), Math.sqrt(modelSs / Math.max(1, nl1)));
        say("   l1 more than 0.5 dB from the AMBEChipResponse steady-pitch roll-off in %d of %d (pitch, tilt) cases (marked *)", off, total);
    }

    /** Least squares: residual = offset(probe) + curve(bin); returns the curve (mean over used bins pinned to 0). */
    static double[] fitResponse(List<double[]> rows, int probes, int bins, java.util.function.ToIntFunction<double[]> binOf)
    {
        // Alternate between probe offsets and bin means (converges for this additive two-way layout)
        double[] off = new double[probes];
        double[] curve = new double[bins];
        for(int iter = 0; iter < 200; iter++)
        {
            double[] s = new double[probes];
            int[] n = new int[probes];
            for(double[] r : rows)
            {
                int p = (int)r[0];
                s[p] += r[3] - curve[binOf.applyAsInt(r)];
                n[p]++;
            }
            for(int p = 0; p < probes; p++)
            {
                off[p] = n[p] > 0 ? s[p] / n[p] : 0;
            }
            double[] sb = new double[bins];
            int[] nb = new int[bins];
            for(double[] r : rows)
            {
                int b = binOf.applyAsInt(r);
                sb[b] += r[3] - off[(int)r[0]];
                nb[b]++;
            }
            double mean = 0;
            int used = 0;
            for(int b = 0; b < bins; b++)
            {
                curve[b] = nb[b] > 0 ? sb[b] / nb[b] : Double.NaN;
                if(nb[b] > 0)
                {
                    mean += curve[b];
                    used++;
                }
            }
            mean /= Math.max(used, 1);
            for(int b = 0; b < bins; b++)
            {
                curve[b] = Double.isNaN(curve[b]) ? 0 : curve[b] - mean;
            }
            if(iter == 199)
            {
                for(int b = 0; b < bins; b++)
                {
                    if(nb[b] == 0)
                    {
                        curve[b] = Double.NaN;
                    }
                }
            }
        }
        return curve;
    }

    static double residualRms(List<double[]> rows, int probes, double[] curve,
                              java.util.function.ToIntFunction<double[]> binOf)
    {
        double[] s = new double[probes];
        int[] n = new int[probes];
        for(double[] r : rows)
        {
            double c = curve[binOf.applyAsInt(r)];
            s[(int)r[0]] += r[3] - (Double.isNaN(c) ? 0 : c);
            n[(int)r[0]]++;
        }
        double ss = 0;
        for(double[] r : rows)
        {
            int p = (int)r[0];
            double c = curve[binOf.applyAsInt(r)];
            double e = r[3] - (Double.isNaN(c) ? 0 : c) - s[p] / n[p];
            ss += e * e;
        }
        return Math.sqrt(ss / Math.max(rows.size(), 1));
    }

    /** Shift a BIN_HZ curve so its 0.5..2 kHz mean is 0. */
    static void normalize(double[] curve)
    {
        double m = bandMean(curve, 500, 2000);
        for(int b = 0; b < curve.length; b++)
        {
            curve[b] -= m;
        }
    }

    /** Shift an l/L curve so its 2/16..8/16 mean is 0 (roughly the same band as normalize). */
    static void normalizeIndex(double[] curve)
    {
        double s = 0;
        int n = 0;
        for(int b = 2; b < 8; b++)
        {
            if(!Double.isNaN(curve[b]))
            {
                s += curve[b];
                n++;
            }
        }
        for(int b = 0; b < curve.length; b++)
        {
            curve[b] -= n > 0 ? s / n : 0;
        }
    }

    // ------------------------------------------------------------------------------------------------ A. tones

    /**
     * One tone probe. For a dual tone (ids 128..163), hz/chipDb/jmbeDb are the first frequency and hz2/chipDb2/jmbeDb2
     * the second; explained is the share of the chip's frame energy explained by the tone's sinusoid(s).
     */
    record ToneResult(ProbePlan.Probe p, int id, int ad, double hz, double chipDb, double jmbeDb, double hz2,
                      double chipDb2, double jmbeDb2, double explained, double chipRmsDb, double peakHz,
                      double peakExplained)
    {
        boolean detected()
        {
            return explained >= 0.9;
        }

        boolean dual()
        {
            return hz2 > 0;
        }
    }

    ToneResult tone(ProbePlan.Probe p)
    {
        int id = p.b[1];
        int ad = p.b[2];
        // The frequencies JMBE synthesizes the tone at (for dual tones, the chip's measured pairs)
        double[] fs = ToneGenerator.synthesisFrequencies(Tone.fromValue(id));
        double hz = fs[0];
        double hz2 = fs.length > 1 ? fs[1] : 0;
        ProbeSupport.HarmonicFit f1 = fit(2 * Math.PI * hz / FS, 1);
        ProbeSupport.HarmonicFit f2 = hz2 > 0 ? fit(2 * Math.PI * hz2 / FS, 1) : null;
        double sc = 0, sj = 0, sc2 = 0, sj2 = 0, ex = 0, e = 0;
        int n = 0;
        for(int f = p.start + 3; f < steadyTo(p); f++)
        {
            if(clipped(f))
            {
                continue;
            }
            double[] a = new double[2];
            f1.fit(chip, f * N + delay, a);
            sc += a[1] * a[1];
            f1.fit(jmbe, f * N, a);
            sj += a[1] * a[1];
            double explainedPower = 0;
            double r = rms(chip, f, true);
            double frame = r * r;
            f1.fit(chip, f * N + delay, a);
            explainedPower += a[1] * a[1] / 2;
            if(f2 != null)
            {
                f2.fit(chip, f * N + delay, a);
                sc2 += a[1] * a[1];
                explainedPower += a[1] * a[1] / 2;
                f2.fit(jmbe, f * N, a);
                sj2 += a[1] * a[1];
            }
            ex += frame > 0 ? Math.min(1, explainedPower / frame) : 0;
            e += frame;
            n++;
        }
        if(n == 0)
        {
            return null;
        }
        double explained = ex / n;
        // Where is the chip's energy if not at the expected frequency? Coarse 10 Hz search on one steady frame.
        double peakHz = hz, peakEx = explained;
        if(explained < 0.9)
        {
            int f = p.start + p.length - 2;
            for(double h = 100; h <= 3950; h += 10)
            {
                double x = fit(2 * Math.PI * h / FS, 1).explainedFraction(chip, f * N + delay);
                if(x > peakEx)
                {
                    peakEx = x;
                    peakHz = h;
                }
            }
        }
        return new ToneResult(p, id, ad, hz, db(sc / n), db(sj / n), hz2, f2 != null ? db(sc2 / n) : Double.NaN,
            f2 != null ? db(sj2 / n) : Double.NaN, explained, db(e / n) - db(32768.0 * 32768.0), peakHz, peakEx);
    }

    /** chip_dcmode.csv (written by ProbeChipRunner): frame -> DCMODE label, or empty if the file is missing. */
    Map<Integer, String> dcmode() throws Exception
    {
        Map<Integer, String> m = new HashMap<>();
        Path file = dir.resolve("chip_dcmode.csv");
        if(Files.exists(file))
        {
            List<String> lines = Files.readAllLines(file);
            for(int i = 1; i < lines.size(); i++)
            {
                String[] f = lines.get(i).split(",", 4);
                if(f.length == 4)
                {
                    m.put(Integer.parseInt(f[0]), f[3]);
                }
            }
        }
        return m;
    }

    /** DCMODE label counts over a probe's frames. */
    static Map<String, Integer> labels(Map<Integer, String> dcmode, ProbePlan.Probe p)
    {
        Map<String, Integer> c = new TreeMap<>();
        for(int f = p.start; f < p.start + p.length; f++)
        {
            String l = dcmode.get(f);
            if(l != null)
            {
                c.merge(l.isEmpty() ? "(none)" : l, 1, Integer::sum);
            }
        }
        return c;
    }

    void tones(Path out) throws Exception
    {
        Map<Integer, String> dcmode = dcmode();
        List<ToneResult> results = new ArrayList<>();
        Map<String, Integer> toneLabels = new TreeMap<>();
        for(ProbePlan.Probe p : plan.probes)
        {
            if(p.kind == ProbePlan.Kind.TONE)
            {
                labels(dcmode, p).forEach((k, v) -> toneLabels.merge(k, v, Integer::sum));
                ToneResult r = tone(p);
                if(r != null)
                {
                    results.add(r);
                }
            }
        }
        if(results.isEmpty())
        {
            return;
        }
        List<String> csv = new ArrayList<>();
        csv.add("id,ad,hz,chip_db,jmbe_db,chip_minus_jmbe_db,hz2,chip_db2,jmbe_db2,explained,chip_rms_dbfs,peak_hz," +
            "peak_explained,dcmode");
        for(ToneResult r : results)
        {
            csv.add(String.format(Locale.ROOT, "%d,%d,%.2f,%.3f,%.3f,%.3f,%.2f,%.3f,%.3f,%.4f,%.2f,%.0f,%.4f,%s", r.id(),
                r.ad(), r.hz(), r.chipDb(), r.jmbeDb(), r.chipDb() - r.jmbeDb(), r.hz2(), r.chipDb2(), r.jmbeDb2(),
                r.explained(), r.chipRmsDb(), r.peakHz(), r.peakExplained(), labels(dcmode, r.p()).toString()
                    .replace(',', ';')));
        }
        Files.write(out.resolve("response_tones.csv"), csv);

        say("A. tones (single tones at AD %d, AD sweep at %.0f Hz, dual tones 128..163)", ProbeGenerator.TONE_AD,
            ProbeGenerator.TONE_AD_SWEEP_ID * 31.25);
        if(!toneLabels.isEmpty())
        {
            say("   chip DCMODE over all tone frames: %s", toneLabels);
        }
        List<ToneResult> singles = results.stream().filter(r -> !r.dual() && r.id() != ProbeGenerator.TONE_AD_SWEEP_ID)
            .toList();
        long det = singles.stream().filter(ToneResult::detected).count();
        if(!singles.isEmpty())
        {
            double medRms = singles.stream().mapToDouble(ToneResult::chipRmsDb).sorted().toArray()[singles.size() / 2];
            say("   single tones: chip output a tone at the sent frequency for %d of %d (median chip level %.1f dBFS)",
                det, singles.size(), medRms);
        }
        if(det < singles.size())
        {
            StringBuilder sb = new StringBuilder("      not a clean tone: ");
            int shown = 0;
            for(ToneResult r : singles)
            {
                if(!r.detected() && shown++ < 8)
                {
                    sb.append(String.format(Locale.ROOT, "%.0f Hz (best fit %.0f Hz, %.0f%%, %.1f dBFS)  ", r.hz(),
                        r.peakHz(), 100 * r.peakExplained(), r.chipRmsDb()));
                }
            }
            say(sb.toString());
        }
        if(det >= 5)
        {
            int nb = (int)(FS / 2 / BIN_HZ);
            double[] s = new double[nb];
            int[] n = new int[nb];
            for(ToneResult r : singles)
            {
                if(r.detected())
                {
                    s[bin(r.hz())] += r.chipDb() - r.jmbeDb();
                    n[bin(r.hz())]++;
                }
            }
            double[] resp = new double[nb];
            for(int b = 0; b < nb; b++)
            {
                resp[b] = n[b] > 0 ? s[b] / n[b] : Double.NaN;
            }
            double absolute = bandMean(resp, 500, 2000);
            normalize(resp);
            say("   tone response, dB relative to its 0.5..2 kHz mean (chip - JMBE there: %+.2f dB):", absolute);
            say(binHeader());
            say(binRow("tone", resp));
            if(voicedResponse != null)
            {
                say(binRow("voice", voicedResponse));
                double d = 0;
                int k = 0;
                for(int b = 0; b < nb; b++)
                {
                    if(!Double.isNaN(resp[b]) && !Double.isNaN(voicedResponse[b]))
                    {
                        d += Math.pow(resp[b] - voicedResponse[b], 2);
                        k++;
                    }
                }
                say("   tone vs voice response: RMS difference %.2f dB over %d bins (small: one output filter for both; " +
                    "tone flat: the shelf is in the voice path)", Math.sqrt(d / Math.max(k, 1)), k);
            }
        }

        List<ToneResult> ads = results.stream().filter(r -> r.id() == ProbeGenerator.TONE_AD_SWEEP_ID).toList();
        ToneResult ref = ads.stream().filter(r -> r.ad() == ProbeGenerator.TONE_AD).findFirst().orElse(null);
        if(ref != null && ref.detected())
        {
            StringBuilder sb = new StringBuilder("   AD sweep at 1 kHz, chip dB re AD 64 (JMBE in brackets): ");
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            int k = 0;
            for(ToneResult r : ads)
            {
                sb.append(String.format(Locale.ROOT, "%d:%+.1f(%+.1f) ", r.ad(), r.chipDb() - ref.chipDb(),
                    r.jmbeDb() - ref.jmbeDb()));
                if(r.detected() && r.ad() >= 16)
                {
                    double x = r.ad(), y = r.chipDb() - ref.chipDb();
                    sx += x;
                    sy += y;
                    sxx += x * x;
                    sxy += x * y;
                    k++;
                }
            }
            say(sb.toString());
            if(k >= 3)
            {
                double slope = (k * sxy - sx * sy) / (k * sxx - sx * sx);
                say("   chip amplitude law: %.3f dB per AD step over AD 16..127 (TIA-102.BABA-1: 0.711 dB per step; " +
                    "JMBE's ToneGenerator: %.3f)", slope, ToneGenerator.TONE_DB_PER_AD);
            }
            ToneResult a65 = ads.stream().filter(r -> r.ad() == 65).findFirst().orElse(null);
            if(a65 != null)
            {
                say("   AD 65 vs 64 (the AD(0) bit): chip %+.3f dB, JMBE %+.3f dB", a65.chipDb() - ref.chipDb(),
                    a65.jmbeDb() - ref.jmbeDb());
            }
        }

        List<ToneResult> duals = results.stream().filter(ToneResult::dual).toList();
        if(!duals.isEmpty())
        {
            long dd = duals.stream().filter(ToneResult::detected).count();
            say("   dual tones (128..163): chip output both frequencies for %d of %d", dd, duals.size());
            StringBuilder sb = new StringBuilder("      chip - JMBE per frequency, dB (id: f1 f2): ");
            for(ToneResult r : duals)
            {
                if(r.detected())
                {
                    sb.append(String.format(Locale.ROOT, "%d:%+.1f/%+.1f ", r.id(), r.chipDb() - r.jmbeDb(),
                        r.chipDb2() - r.jmbeDb2()));
                }
            }
            say(sb.toString());
        }
    }

    // ------------------------------------------------------------------------------------------------ E. glide

    /**
     * First harmonic deficit of one frame: chip (l1 - mean(l2, l3)) minus JMBE published decode (same), dB, with the
     * harmonic fit at the frame's own pitch. NaN if the frame is clipped or L < 3.
     */
    double firstHarmonicDeficit(int frame, int b0)
    {
        return firstHarmonicDeficit(frame, b0, jmbeRaw);
    }

    double firstHarmonicDeficit(int frame, int b0, double[] reference)
    {
        FundamentalFrequency ff = FundamentalFrequency.fromValue(b0);
        int L = ff.getL();
        if(L < 3 || clipped(frame))
        {
            return Double.NaN;
        }
        ProbeSupport.HarmonicFit hf = fit(ff.getFrequency(), L);
        double[] c = new double[L + 1];
        double[] j = new double[L + 1];
        hf.fit(chip, frame * N + delay, c);
        hf.fit(reference, frame * N, j);
        double dc = 20 * Math.log10(c[1]) - 10 * Math.log10(c[2] * c[3]);
        double dj = 20 * Math.log10(j[1]) - 10 * Math.log10(j[2] * j[3]);
        return dc - dj;
    }

    /**
     * Pitch-glide probe (ProbeGenerator --glide): how the chip's first harmonic loss builds up and recovers. Every frame
     * of every GLIDE probe gets firstHarmonicDeficit (results/response_glide.csv); the summary shows the deficit over
     * each hold after a step (by step size and direction), during and after each glide rate, and during and after the
     * vibrato. The csv also has the deficit against JMBE with AMBEChipResponse (vs_model_db), and the summary gives the
     * rms of both.
     */
    void glide(Path out) throws Exception
    {
        List<ProbePlan.Probe> probes = plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.GLIDE).toList();
        if(probes.isEmpty())
        {
            return;
        }
        List<String> csv = new ArrayList<>();
        csv.add("frame,group,label,b0,f0_hz,deficit_db,vs_model_db");
        double modelSs = 0, rawSs = 0;
        int modelN = 0;
        Map<String, double[]> series = new LinkedHashMap<>(); // "group/label/b0 #k" -> deficits by frame in probe
        for(ProbePlan.Probe p : probes)
        {
            int label = p.index / 1000;
            double f0 = FundamentalFrequency.fromValue(p.b0()).getFrequency() * FS / (2 * Math.PI);
            double[] d = new double[p.length];
            for(int k = 0; k < p.length; k++)
            {
                d[k] = firstHarmonicDeficit(p.start + k, p.b0());
                double m = firstHarmonicDeficit(p.start + k, p.b0(), jmbe);
                csv.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%.2f,%.3f,%.3f", p.start + k, p.group, label, p.b0(), f0,
                    d[k], m));
                if(k > 0 && k < p.length - 1 && !Double.isNaN(d[k]) && !Double.isNaN(m))
                {
                    rawSs += d[k] * d[k];
                    modelSs += m * m;
                    modelN++;
                }
            }
            series.put(p.group + "/" + label + "/" + p.start, d);
        }
        Files.write(out.resolve("response_glide.csv"), csv);

        int[] at = {0, 1, 2, 5, 10, 20, 40, 59};
        StringBuilder head = new StringBuilder("        frame after change:");
        for(int k : at)
        {
            head.append(String.format(Locale.ROOT, "%6d", k));
        }
        say("E. pitch glides: first harmonic deficit, chip (l1 - mean l2,l3) minus JMBE published decode, dB");
        say("   rms over %d frames (all but each probe's first and last): %.3f dB vs published decode, %.3f dB vs JMBE " +
            "with AMBEChipResponse%s", modelN, Math.sqrt(rawSs / Math.max(1, modelN)), Math.sqrt(modelSs / Math.max(1, modelN)),
            AMBEChipResponse.isEnabled() ? "" : " (disabled)");
        for(int g = 0; g <= 1; g++)
        {
            final int group = g;
            List<ProbePlan.Probe> holds = probes.stream().filter(p -> p.group == group && p.length >= 40).toList();
            if(holds.isEmpty())
            {
                continue;
            }
            int base = holds.get(0).b0();
            double f0 = FundamentalFrequency.fromValue(base).getFrequency() * FS / (2 * Math.PI);
            say("   steps from b0 %d (%.0f Hz; steady-pitch roll-off there %+.2f dB):", base, f0,
                AMBEChipResponse.highPassDb(f0));
            say("%s", head.toString());
            for(ProbePlan.Probe p : holds)
            {
                int label = p.index / 1000;
                String name = label == 0 ? "initial hold (from reset)" : label < 200 ?
                    String.format(Locale.ROOT, "up %2d to b0 %3d", label - 100, p.b0()) :
                    String.format(Locale.ROOT, "down %2d to b0 %3d", label - 200, p.b0());
                say("%s", row(String.format(Locale.ROOT, "      %-24s", name), series.get(p.group + "/" + label + "/" + p.start), at));
            }
        }
        List<ProbePlan.Probe> g2 = probes.stream().filter(p -> p.group == 2).toList();
        if(!g2.isEmpty())
        {
            say("   glides b0 50..80 (186 to 118 Hz): mean deficit during the glide, then over the hold after it");
            say("%s", head.toString().replace("frame after change", "    hold frame after glide"));
            for(int rate : new int[]{1, 2, 4})
            {
                for(int dir = 3; dir <= 4; dir++)
                {
                    final int glideLabel = dir * 100 + rate;
                    final int holdLabel = dir * 100 + 10 + rate;
                    double sum = 0;
                    int n = 0;
                    for(ProbePlan.Probe p : g2)
                    {
                        if(p.index / 1000 == glideLabel)
                        {
                            for(double v : series.get(p.group + "/" + glideLabel + "/" + p.start))
                            {
                                if(!Double.isNaN(v))
                                {
                                    sum += v;
                                    n++;
                                }
                            }
                        }
                    }
                    ProbePlan.Probe hold = g2.stream().filter(p -> p.index / 1000 == holdLabel).findFirst().orElse(null);
                    String name = String.format(Locale.ROOT, "      %s 1 step/%d fr: %+5.2f |", dir == 3 ? "up  " : "down", rate,
                        n > 0 ? sum / n : Double.NaN);
                    say("%s", hold == null ? name : row(name, series.get(hold.group + "/" + holdLabel + "/" + hold.start), at));
                }
            }
        }
        List<ProbePlan.Probe> g3 = probes.stream().filter(p -> p.group == 3).toList();
        if(!g3.isEmpty())
        {
            say("   vibrato around b0 70 (%.0f Hz): mean deficit during, then over the hold after it",
                FundamentalFrequency.fromValue(70).getFrequency() * FS / (2 * Math.PI));
            for(int v = 0; v <= 1; v++)
            {
                final int vibLabel = 500 + v;
                final int holdLabel = 510 + v;
                double sum = 0;
                int n = 0;
                int seen = 0;
                for(ProbePlan.Probe p : g3)
                {
                    if(p.index / 1000 == vibLabel)
                    {
                        for(double x : series.get(p.group + "/" + vibLabel + "/" + p.start))
                        {
                            if(seen++ >= 10 && !Double.isNaN(x)) // skip the onset
                            {
                                sum += x;
                                n++;
                            }
                        }
                    }
                }
                ProbePlan.Probe hold = g3.stream().filter(p -> p.index / 1000 == holdLabel).findFirst().orElse(null);
                String name = String.format(Locale.ROOT, "      %s: %+5.2f |", v == 0 ? "+-2 every frame   " : "+-4 every 2 frames",
                    n > 0 ? sum / n : Double.NaN);
                say("%s", hold == null ? name : row(name, series.get(hold.group + "/" + holdLabel + "/" + hold.start), at));
            }
        }
    }

    static String row(String name, double[] d, int[] at)
    {
        StringBuilder sb = new StringBuilder(name);
        for(int k : at)
        {
            sb.append(k < d.length && !Double.isNaN(d[k]) ? String.format(Locale.ROOT, "%+6.2f", d[k]) : "     .");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------ C. level

    void level(Path out) throws Exception
    {
        List<ProbePlan.Probe> probes = plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.LEVEL).toList();
        if(probes.isEmpty())
        {
            return;
        }
        List<String> csv = new ArrayList<>();
        csv.add("b2,chip_dbfs,jmbe_dbfs,offset_db,shelf_db,harmonics,clipped_frames");
        say("C. level sweep (flat L %d baseline): chip - JMBE offset and high-band shelf (2.9..3.7 kHz minus " +
            "0.5..2 kHz)", probes.get(0).L());
        for(ProbePlan.Probe p : probes)
        {
            List<double[]> h = harmonicResidual(p);
            double s = 0, hi = 0, lo = 0;
            int nh = 0, nl = 0;
            for(double[] x : h)
            {
                double r = x[2] - x[3];
                s += r;
                if(x[1] >= 2900 && x[1] < 3700)
                {
                    hi += r;
                    nh++;
                }
                if(x[1] >= 500 && x[1] < 2000)
                {
                    lo += r;
                    nl++;
                }
            }
            double ec = 0, ej = 0;
            int n = 0, clip = 0;
            for(int f = steadyFrom(p); f < steadyTo(p); f++)
            {
                if(clipped(f))
                {
                    clip++;
                    continue;
                }
                ec += Math.pow(rms(chip, f, true), 2);
                ej += Math.pow(rms(jmbe, f, false), 2);
                n++;
            }
            double cdb = n > 0 ? db(ec / n) - db(32768.0 * 32768.0) : Double.NaN;
            double jdb = n > 0 ? db(ej / n) - db(32768.0 * 32768.0) : Double.NaN;
            double off = h.isEmpty() ? Double.NaN : s / h.size();
            double shelf = nh > 0 && nl > 0 ? hi / nh - lo / nl : Double.NaN;
            say("   b2 %2d: chip %6.1f dBFS, JMBE %6.1f dBFS, offset %+.2f dB, shelf %+.2f dB%s", p.b[2], cdb, jdb, off,
                shelf, clip > 0 ? String.format(Locale.ROOT, "  (%d frames clipped)", clip) : "");
            csv.add(String.format(Locale.ROOT, "%d,%.2f,%.2f,%.3f,%.3f,%d,%d", p.b[2], cdb, jdb, off, shelf, h.size(), clip));
        }
        Files.write(out.resolve("response_level.csv"), csv);
    }

    // ------------------------------------------------------------------------------------------------ D. voicing

    record Band(int b0, int L, int b1, int l, double hz, boolean jmbeVoiced, double chipFrac, double jmbeFrac,
                double chipDb, double jmbeDb)
    {
    }

    void voicing(Path out) throws Exception
    {
        List<ProbePlan.Probe> probes = plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.VOICING).toList();
        if(probes.isEmpty())
        {
            return;
        }
        List<Band> bands = new ArrayList<>();
        for(ProbePlan.Probe p : probes)
        {
            int[] span = groupSpan.get(p.group);
            List<AMBEModelParameters> par = ProbeSupport.decode(frames.subList(span[0], p.start + p.length));
            boolean[] voiced = par.get(par.size() - 1).getVoicingDecisions();
            int L = p.L();
            double w0 = FundamentalFrequency.fromValue(p.b0()).getFrequency();
            double[][] cb = bandPowers(chip, p, w0, L, true);
            double[][] jb = bandPowers(jmbe, p, w0, L, false);
            for(int l = 1; l <= L; l++)
            {
                double ct = cb[0][l] + cb[1][l];
                double jt = jb[0][l] + jb[1][l];
                if(ct > 0 && jt > 0)
                {
                    bands.add(new Band(p.b0(), L, p.b[1], l, l * w0 * FS / (2 * Math.PI), l < voiced.length && voiced[l],
                        cb[0][l] / ct, jb[0][l] / jt, db(ct), db(jt)));
                }
            }
        }

        // A harmonic fit over one frame also captures part of a noise band (more at low pitch, where a band spans
        // fewer DFT bins), so the voiced/unvoiced threshold is set per pitch halfway between the harmonic share of
        // JMBE's own voiced bands and of its unvoiced bands.
        Map<Integer, Double> threshold = new TreeMap<>();
        for(int b0 : bands.stream().map(Band::b0).distinct().toList())
        {
            double v = bands.stream().filter(b -> b.b0() == b0 && b.jmbeVoiced()).mapToDouble(Band::jmbeFrac).average()
                .orElse(1);
            double u = bands.stream().filter(b -> b.b0() == b0 && !b.jmbeVoiced()).mapToDouble(Band::jmbeFrac).average()
                .orElse(0);
            threshold.put(b0, (v + u) / 2);
        }

        List<String> csv = new ArrayList<>();
        csv.add("b0,L,b1,l,f_hz,jmbe_voiced,chip_harm_frac,jmbe_harm_frac,threshold,chip_band_db,jmbe_band_db");
        int nb = (int)(FS / 2 / BIN_HZ);
        double[] uvS = new double[nb];
        int[] uvN = new int[nb];
        double[] vS = new double[nb];
        int[] vN = new int[nb];
        int agree = 0, jmbeOk = 0;
        Map<Integer, int[]> perPitch = new TreeMap<>(); // b0 -> {bands, chip voiced where JMBE unvoiced, reverse}
        Map<Integer, int[]> perCode = new TreeMap<>();  // b1 -> {bands, disagreements}
        for(Band b : bands)
        {
            double t = threshold.get(b.b0());
            boolean chipV = b.chipFrac() > t;
            agree += chipV == b.jmbeVoiced() ? 1 : 0;
            jmbeOk += (b.jmbeFrac() > t) == b.jmbeVoiced() ? 1 : 0;
            int[] c = perPitch.computeIfAbsent(b.b0(), k -> new int[3]);
            c[0]++;
            c[1] += chipV && !b.jmbeVoiced() ? 1 : 0;
            c[2] += !chipV && b.jmbeVoiced() ? 1 : 0;
            int[] k = perCode.computeIfAbsent(b.b1(), x -> new int[2]);
            k[0]++;
            k[1] += chipV != b.jmbeVoiced() ? 1 : 0;
            double d = b.chipDb() - b.jmbeDb();
            if(!chipV && !b.jmbeVoiced())
            {
                uvS[bin(b.hz())] += d;
                uvN[bin(b.hz())]++;
            }
            if(chipV && b.jmbeVoiced())
            {
                vS[bin(b.hz())] += d;
                vN[bin(b.hz())]++;
            }
            csv.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%.1f,%d,%.4f,%.4f,%.4f,%.3f,%.3f", b.b0(), b.L(), b.b1(),
                b.l(), b.hz(), b.jmbeVoiced() ? 1 : 0, b.chipFrac(), b.jmbeFrac(), t, b.chipDb(), b.jmbeDb()));
        }
        Files.write(out.resolve("response_voicing.csv"), csv);

        say("D. voicing: every b1 code; a band counts as voiced when its harmonic share of the band power is above a " +
            "per-pitch threshold set from JMBE's own audio");
        StringBuilder th = new StringBuilder("   thresholds:");
        threshold.forEach((b0, t) -> th.append(String.format(Locale.ROOT, " b0 %d (L %d) %.2f", b0,
            FundamentalFrequency.fromValue(b0).getL(), t)));
        say(th.toString());
        say("   classifier check: JMBE's audio matches its own decisions in %d of %d bands", jmbeOk, bands.size());
        say("   chip matches JMBE's voiced/unvoiced decision in %d of %d bands (%.1f%%)", agree, bands.size(),
            100.0 * agree / Math.max(bands.size(), 1));
        for(Map.Entry<Integer, int[]> e : perPitch.entrySet())
        {
            int[] c = e.getValue();
            say("      b0 %d (L %d): %d bands; chip voiced where JMBE unvoiced %d, chip noise where JMBE voiced %d",
                e.getKey(), FundamentalFrequency.fromValue(e.getKey()).getL(), c[0], c[1], c[2]);
        }
        StringBuilder codes = new StringBuilder("      b1 codes with disagreements (code:bands differing): ");
        int shown = 0;
        for(Map.Entry<Integer, int[]> e : perCode.entrySet())
        {
            if(e.getValue()[1] > 0)
            {
                codes.append(e.getKey()).append(':').append(e.getValue()[1]).append(' ');
                shown++;
            }
        }
        say(shown > 0 ? codes.toString() : "      no b1 code has a disagreeing band");
        double[] uv = new double[nb];
        double[] v = new double[nb];
        for(int b = 0; b < nb; b++)
        {
            uv[b] = uvN[b] > 0 ? uvS[b] / uvN[b] : Double.NaN;
            v[b] = vN[b] > 0 ? vS[b] / vN[b] : Double.NaN;
        }
        say("   band power chip - JMBE (dB), in bands both call unvoiced and in bands both call voiced:");
        say(binHeader());
        say(binRow("noise", uv));
        say(binRow("voiced", v));
        say("   unvoiced minus voiced, 0.5..3.5 kHz: %+.2f dB (0 = the chip's noise/voiced balance matches JMBE's)",
            bandMean(uv, 500, 3500) - bandMean(v, 500, 3500));
    }

    /**
     * Mean per-band powers over a probe's steady frames: [0][l] the harmonic part (fitted sinusoid at l*w0, A^2/2),
     * [1][l] the residual (signal minus the fitted harmonics) in the band (l-1/2..l+1/2)*w0, from a DFT of the
     * residual (Parseval-normalized, so both are mean power per sample).
     */
    double[][] bandPowers(double[] x, ProbePlan.Probe p, double w0, int L, boolean isChip)
    {
        ProbeSupport.HarmonicFit hf = fit(w0, L);
        double[] harm = new double[L + 1];
        double[] noise = new double[L + 1];
        int n = 0;
        int K = 640;
        for(int f = steadyFrom(p); f < steadyTo(p); f++)
        {
            if(clipped(f))
            {
                continue;
            }
            int start = f * N + (isChip ? delay : 0);
            double[] c = coefficients(hf, x, start);
            double[] r = new double[N];
            for(int i = 0; i < N; i++)
            {
                int k = start + i;
                double v = (k >= 0 && k < x.length) ? x[k] : 0.0;
                double m = c[0];
                for(int l = 1; l <= L; l++)
                {
                    m += c[2 * l - 1] * hf.basis[2 * l - 1][i] + c[2 * l] * hf.basis[2 * l][i];
                }
                r[i] = v - m;
            }
            for(int l = 1; l <= L; l++)
            {
                harm[l] += (c[2 * l - 1] * c[2 * l - 1] + c[2 * l] * c[2 * l]) / 2.0;
            }
            // Residual power by DFT bin (zero-padded to K), Parseval: mean power = sum_k |X_k|^2 / (N * K)
            for(int k = 1; k < K / 2; k++)
            {
                double w = 2 * Math.PI * k / K;
                double re = 0, im = 0;
                for(int i = 0; i < N; i++)
                {
                    re += r[i] * Math.cos(w * i);
                    im -= r[i] * Math.sin(w * i);
                }
                double pw = 2 * (re * re + im * im) / ((double)N * K);
                int l = (int)Math.round(w / w0);
                if(l >= 1 && l <= L)
                {
                    noise[l] += pw;
                }
            }
            n++;
        }
        for(int l = 1; l <= L; l++)
        {
            harm[l] /= Math.max(n, 1);
            noise[l] /= Math.max(n, 1);
        }
        return new double[][]{harm, noise};
    }
}
