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

import jmbe.codec.ambe.AMBEModelParameters;
import jmbe.codec.ambe.ambePlus2.FundamentalFrequency;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-harmonic voicing measurements for every VOICING probe (ProbeGenerator --response groups 3/4, or --voicing):
 * for the chip and for JMBE (with its own known decisions), each harmonic's
 * <ul>
 *   <li>share: harmonic power (sinusoid fitted at l*w0 over one frame) over that plus the residual power in the
 *   harmonic's band, averaged over the probe's steady frames;</li>
 *   <li>cv: frame-to-frame coefficient of variation of the fitted amplitude (a held voiced harmonic is steady, noise
 *   is Rayleigh, cv ~0.5);</li>
 *   <li>band power (dB).</li>
 * </ul>
 * A harmonic is classed voiced (V) when cv < 0.2 and share > 0.7, noise (.) when cv > 0.33, otherwise mixed (+).
 * The summary pools all pitches into 100 Hz bins per code (a bin is V or . when 60% of its harmonics are) (JMBE's decisions vs the chip's classes), lists codes
 * whose chip output repeats frame to frame, and gives the chip - JMBE level of noise relative to voiced harmonics
 * per 500 Hz band.
 *
 * Also: the frame-to-frame stability (cv) of voiced harmonics above L/4, chip vs JMBE, by the frame's share of
 * unvoiced harmonics (MBE synthesis adds random phase there in proportion to that share; --phase-scale sets
 * AMBEChipResponse's scale for the JMBE side).
 *
 * Writes results/voicing_harmonics.csv and results/voicing_summary.txt;
 * ProbeVoicingAnalyzer --dir DIR [--delay D] [--phase-scale S].
 */
public final class ProbeVoicingAnalyzer
{
    final ProbeResponseAnalyzer r;

    ProbeVoicingAnalyzer(ProbeResponseAnalyzer r)
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
                case "--phase-scale" -> jmbe.codec.ambe.AMBEChipResponse.setPhaseNoiseScale(Double.parseDouble(args[++i]));
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        ProbeResponseAnalyzer r = new ProbeResponseAnalyzer(dir, dir.resolve("chip.pcm"), null, delay);
        r.say("frames %d, chip delay %d samples", r.frames.size(), r.delay);
        Path out = dir.resolve("results");
        Files.createDirectories(out);
        new ProbeVoicingAnalyzer(r).run(out);
    }

    static final double BIN = 100.0;

    static char classify(double share, double cv)
    {
        if(cv < 0.2 && share > 0.7)
        {
            return 'V';
        }
        return cv > 0.33 ? '.' : '+';
    }

    /** Pooled class of a bin from its V, noise and mixed counts: V or . when 60% of the harmonics agree. */
    static char pooled(int v, int u, int m)
    {
        int n = v + u + m;
        return n == 0 ? ' ' : v >= 0.6 * n ? 'V' : u >= 0.6 * n ? '.' : '+';
    }

    /** Mean correlation of adjacent steady frames of the chip output (1 = the same waveform every frame). */
    double frameCorrelation(int from, int to)
    {
        int N = ProbeResponseAnalyzer.N;
        double sum = 0;
        int n = 0;
        for(int f = from; f + 1 < to; f++)
        {
            double sxy = 0, sxx = 0, syy = 0;
            for(int i = 0; i < N; i++)
            {
                int a = f * N + r.delay + i;
                int b = a + N;
                if(a < 0 || b >= r.chip.length)
                {
                    continue;
                }
                sxy += r.chip[a] * r.chip[b];
                sxx += r.chip[a] * r.chip[a];
                syy += r.chip[b] * r.chip[b];
            }
            if(sxx > 0 && syy > 0)
            {
                sum += sxy / Math.sqrt(sxx * syy);
                n++;
            }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    /** Per-harmonic measurements over a probe's steady frames: {share, cv, band power} for l = 1..L. */
    record Harmonics(double[] share, double[] cv, double[] powerDb)
    {
    }

    Harmonics measure(double[] x, ProbePlan.Probe p, double w0, int L, boolean isChip, int from, int to)
    {
        ProbeSupport.HarmonicFit hf = r.fit(w0, L);
        int N = ProbeResponseAnalyzer.N;
        int K = 640;
        double[] harm = new double[L + 1];
        double[] noise = new double[L + 1];
        double[] aSum = new double[L + 1];
        double[] aSq = new double[L + 1];
        int n = 0;
        for(int f = from; f < to; f++)
        {
            if(isChip && r.clipped(f))
            {
                continue;
            }
            int start = f * N + (isChip ? r.delay : 0);
            double[] c = r.coefficients(hf, x, start);
            double[] res = new double[N];
            for(int i = 0; i < N; i++)
            {
                int k = start + i;
                double v = (k >= 0 && k < x.length) ? x[k] : 0.0;
                double m = c[0];
                for(int l = 1; l <= L; l++)
                {
                    m += c[2 * l - 1] * hf.basis[2 * l - 1][i] + c[2 * l] * hf.basis[2 * l][i];
                }
                res[i] = v - m;
            }
            for(int l = 1; l <= L; l++)
            {
                double p2 = c[2 * l - 1] * c[2 * l - 1] + c[2 * l] * c[2 * l];
                harm[l] += p2 / 2.0;
                double a = Math.sqrt(p2);
                aSum[l] += a;
                aSq[l] += a * a;
            }
            for(int k = 1; k < K / 2; k++)
            {
                double w = 2 * Math.PI * k / K;
                double re = 0, im = 0;
                for(int i = 0; i < N; i++)
                {
                    re += res[i] * Math.cos(w * i);
                    im -= res[i] * Math.sin(w * i);
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
        double[] share = new double[L + 1];
        double[] cv = new double[L + 1];
        double[] db = new double[L + 1];
        for(int l = 1; l <= L; l++)
        {
            double t = harm[l] + noise[l];
            share[l] = t > 0 ? harm[l] / t : Double.NaN;
            double mean = aSum[l] / Math.max(n, 1);
            double var = aSq[l] / Math.max(n, 1) - mean * mean;
            cv[l] = mean > 0 ? Math.sqrt(Math.max(var, 0)) / mean : Double.NaN;
            db[l] = t > 0 ? 10 * Math.log10(t / Math.max(n, 1)) : Double.NaN;
        }
        return new Harmonics(share, cv, db);
    }

    void run(Path out) throws Exception
    {
        List<ProbePlan.Probe> probes = r.plan.probes.stream().filter(p -> p.kind == ProbePlan.Kind.VOICING).toList();
        List<String> csv = new ArrayList<>();
        csv.add("group,b0,L,f0_hz,b1,l,f_hz,jmbe_voiced,chip_share,jmbe_share,chip_cv,jmbe_cv,chip_db,jmbe_db,frames");
        int bins = (int)(ProbeResponseAnalyzer.FS / 2 / BIN);
        // code -> bin -> {jmbe voiced, jmbe unvoiced, chip V, chip ., chip +, JMBE audio V, ., +}
        int[][][] map = new int[32][bins][8];
        java.util.Map<Integer, List<String>> repeating = new java.util.TreeMap<>();
        int nb = 8;
        java.util.Map<Integer, List<double[]>> jitter = new java.util.TreeMap<>(); // unvoiced share x10 -> {chip, jmbe}
        double[] uvS = new double[nb], vS = new double[nb];
        int[] uvN = new int[nb], vN = new int[nb];
        for(ProbePlan.Probe p : probes)
        {
            int[] span = r.groupSpan.get(p.group);
            List<AMBEModelParameters> par = ProbeSupport.decode(r.frames.subList(span[0], p.start + p.length));
            boolean[] voiced = par.get(par.size() - 1).getVoicingDecisions();
            int L = p.L();
            int code = p.b[1];
            double w0 = FundamentalFrequency.fromValue(p.b0()).getFrequency();
            double f0 = w0 * ProbeResponseAnalyzer.FS / (2 * Math.PI);
            int from = p.start + Math.min(3, p.length - 2);
            int to = r.steadyTo(p);
            Harmonics c = measure(r.chip, p, w0, L, true, from, to);
            Harmonics j = measure(r.jmbe, p, w0, L, false, from, to);
            double corr = frameCorrelation(from, to);
            int unvoiced = 0;
            for(int l = 1; l <= L; l++)
            {
                unvoiced += l < voiced.length && voiced[l] ? 0 : 1;
            }
            int share10 = (int)Math.round(10.0 * unvoiced / L);
            for(int l = Math.max(8, L / 4 + 1); l <= L; l++)
            {
                if(l < voiced.length && voiced[l] && unvoiced > 0 && corr <= 0.5)
                {
                    jitter.computeIfAbsent(share10, k -> new ArrayList<>()).add(new double[]{c.cv()[l], j.cv()[l]});
                }
            }
            if(corr > 0.5)
            {
                repeating.computeIfAbsent(code, k -> new ArrayList<>()).add(String.format(Locale.ROOT, "b0 %d (%.2f)",
                    p.b0(), corr));
            }
            for(int l = 1; l <= L; l++)
            {
                boolean jv = l < voiced.length && voiced[l];
                double hz = l * f0;
                char cc = classify(c.share()[l], c.cv()[l]);
                char jc = classify(j.share()[l], j.cv()[l]);
                int bin = (int)Math.min(hz / BIN, bins - 1);
                map[code][bin][jv ? 0 : 1]++;
                map[code][bin][cc == 'V' ? 2 : cc == '.' ? 3 : 4]++;
                map[code][bin][jc == 'V' ? 5 : jc == '.' ? 6 : 7]++;
                double d = c.powerDb()[l] - j.powerDb()[l];
                int band = (int)Math.min(hz / 500, nb - 1);
                if(corr <= 0.5 && !Double.isNaN(d))
                {
                    if(!jv && cc == '.')
                    {
                        uvS[band] += d;
                        uvN[band]++;
                    }
                    else if(jv && cc == 'V')
                    {
                        vS[band] += d;
                        vN[band]++;
                    }
                }
                csv.add(String.format(Locale.ROOT, "%d,%d,%d,%.3f,%d,%d,%.1f,%d,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f,%d", p.group,
                    p.b0(), L, f0, code, l, hz, jv ? 1 : 0, c.share()[l], j.share()[l], c.cv()[l], j.cv()[l],
                    c.powerDb()[l], j.powerDb()[l], to - from));
            }
        }
        Files.write(out.resolve("voicing_harmonics.csv"), csv);

        r.say("F. voicing map, all pitches pooled in %.0f Hz bins, 0..4 kHz; 500 Hz band edges marked |", BIN);
        r.say("   JMBE: JMBE's decisions; audio: JMBE's audio through the same classifier as the chip (a voiced harmonic " +
            "with JMBE's per-frame phase noise can read +); chip: the chip's audio. V voiced, . noise, + mixed (under " +
            "60%% of the bin's harmonics, over all pitches, agree), blank no harmonic. ^ chip and audio disagree V vs " +
            "noise, ~ one of them reads +");
        int differing = 0;
        for(int code = 0; code < 32; code++)
        {
            StringBuilder jm = new StringBuilder();
            StringBuilder au = new StringBuilder();
            StringBuilder ch = new StringBuilder();
            StringBuilder diff = new StringBuilder();
            boolean any = false;
            for(int b = 0; b < bins; b++)
            {
                if(b > 0 && b % 5 == 0)
                {
                    jm.append('|');
                    au.append('|');
                    ch.append('|');
                    diff.append(' ');
                }
                int[] m = map[code][b];
                char j = m[0] + m[1] == 0 ? ' ' : m[1] == 0 ? 'V' : m[0] == 0 ? '.' : '+';
                char c = pooled(m[2], m[3], m[4]);
                char a = pooled(m[5], m[6], m[7]);
                jm.append(j);
                au.append(a);
                ch.append(c);
                char d = c == a ? ' ' : (c == '+' || a == '+') ? '~' : '^';
                diff.append(d);
                any |= d == '^';
            }
            differing += any ? 1 : 0;
            r.say("   b1 %2d  JMBE %s", code, jm);
            if(!au.toString().equals(jm.toString()))
            {
                r.say("         audio %s", au);
            }
            r.say("          chip %s", ch);
            if(!diff.toString().isBlank())
            {
                r.say("               %s", diff);
            }
        }
        r.say("   %d of 32 codes have bins where the chip and JMBE's audio disagree voiced vs noise (^)", differing);
        r.say("   chip output repeating frame to frame (adjacent-frame correlation > 0.5):");
        if(repeating.isEmpty())
        {
            r.say("      none");
        }
        repeating.forEach((code, list) -> r.say("      b1 %2d: %s", code, String.join(", ", list)));
        StringBuilder h = new StringBuilder("   per 500 Hz band:          ");
        StringBuilder u = new StringBuilder("   noise   chip - JMBE, dB:  ");
        StringBuilder v = new StringBuilder("   voiced  chip - JMBE, dB:  ");
        StringBuilder bal = new StringBuilder("   noise minus voiced, dB:   ");
        for(int b = 0; b < nb; b++)
        {
            h.append(String.format(Locale.ROOT, "%7d", b * 500));
            double un = uvN[b] > 0 ? uvS[b] / uvN[b] : Double.NaN;
            double vo = vN[b] > 0 ? vS[b] / vN[b] : Double.NaN;
            u.append(String.format(Locale.ROOT, "%+7.2f", un));
            v.append(String.format(Locale.ROOT, "%+7.2f", vo));
            bal.append(String.format(Locale.ROOT, "%+7.2f", un - vo));
        }
        r.say("   voiced harmonics above L/4 in partly unvoiced frames, median frame-to-frame cv (random phase), " +
            "phase scale %.2f:", jmbe.codec.ambe.AMBEChipResponse.phaseNoiseScale());
        double ss = 0;
        int sn = 0;
        for(java.util.Map.Entry<Integer, List<double[]>> e : jitter.entrySet())
        {
            double[] cc = e.getValue().stream().mapToDouble(x -> x[0]).sorted().toArray();
            double[] jj = e.getValue().stream().mapToDouble(x -> x[1]).sorted().toArray();
            double mc = cc[cc.length / 2];
            double mj = jj[jj.length / 2];
            r.say("      unvoiced share %.1f: chip %.3f, JMBE %.3f (%d harmonics)", e.getKey() / 10.0, mc, mj, cc.length);
            ss += cc.length * Math.pow(Math.log(mc / mj), 2);
            sn += cc.length;
        }
        r.say("      rms log ratio chip/JMBE: %.3f", Math.sqrt(ss / Math.max(sn, 1)));
        r.say("   level of harmonics both call noise vs both call voiced (codes whose output does not repeat):");
        r.say("%s", h);
        r.say("%s", u);
        r.say("%s", v);
        r.say("%s", bal);
        Files.writeString(out.resolve("voicing_summary.txt"), r.summary.toString());
        r.say("wrote %s/voicing_harmonics.csv and voicing_summary.txt", out);
    }
}
