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

import jmbe.MBECallSequence;
import jmbe.MBECallSequenceReader;
import jmbe.VoiceFrame;
import jmbe.codec.FrameType;
import jmbe.codec.ambe.AMBEFrame;
import jmbe.codec.ambe.AMBEModelParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Compares the AMBE-3000R's audio with JMBE's (chip response on) on recorded calls decoded by MbeChipRunner.
 *
 * For every NAME.mbe with a NAME.chip.pcm (and, if present, NAME.chip_dcmode.csv) in the directory (and, with
 * --recursive, below it), JMBE synthesizes the call (AGC off, seeded) and the two are compared frame by frame:
 * <ul>
 *   <li>delay: the chip lag (0..158 samples) at which whole-frame levels scatter least (on speech the
 *   level-envelope correlation is too flat to pin it);</li>
 *   <li>level: JMBE minus chip per frame (dB), over frames above -50 dBFS in both, as median and spread, split into
 *   fully voiced, mixed and unvoiced frames;</li>
 *   <li>spectrum: JMBE minus chip per 500 Hz band (256-sample Hann window centred on the frame), median over the
 *   same frames;</li>
 *   <li>invalid frames: the chip's DCMODE 0x0020 (frame repeat) frames against JMBE's repeat / mute decisions, and
 *   the level difference over the 8 frames after each (the prediction-memory recovery);</li>
 *   <li>the worst frames by level difference.</li>
 * </ul>
 * The overall section adds the chip's DCMODE against JMBE's frame type and action (with the commonest frames of each
 * non-voice pair) and the level per b1 voicing code.
 * Writes DIR/mbe_compare_summary.txt (also printed) and DIR/mbe_compare_frames.csv (one row per frame of every call,
 * with the frame's type, b0, b1 and hex).
 *
 * usage: java analysis.probe.MbeCompareAnalyzer --dir DIR [--recursive] [--worst N]
 */
public final class MbeCompareAnalyzer
{
    private static final int N = ProbeSupport.FRAME;
    private static final int WINDOW = 256;
    private static final int BANDS = 8;
    /** Frames below this power (16-bit units squared, ~-50 dBFS) in either decoder are left out of the statistics. */
    private static final double ACTIVE = 3.0e4;
    private static final double[] HANN = new double[WINDOW];
    private static final double[][] COS = new double[WINDOW / 2 + 1][WINDOW];
    private static final double[][] SIN = new double[WINDOW / 2 + 1][WINDOW];

    static
    {
        for(int n = 0; n < WINDOW; n++)
        {
            HANN[n] = 0.5 - 0.5 * Math.cos(2 * Math.PI * n / WINDOW);
        }
        for(int k = 0; k <= WINDOW / 2; k++)
        {
            for(int n = 0; n < WINDOW; n++)
            {
                COS[k][n] = Math.cos(2 * Math.PI * k * n / WINDOW);
                SIN[k][n] = Math.sin(2 * Math.PI * k * n / WINDOW);
            }
        }
    }

    private MbeCompareAnalyzer()
    {
    }

    /** One compared frame. */
    private record Row(String call, int frame, double chipDb, double jmbeDb, double[] bandDiff, String dcmode,
                       String action, int errors, double f0, int voicedShare, boolean active, String type,
                       int b0, int b1, String hex)
    {
        double diff()
        {
            return jmbeDb - chipDb;
        }
    }

    public static void main(String[] args) throws Exception
    {
        Path dir = null;
        boolean recursive = false;
        int worst = 10;
        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--dir" -> dir = Paths.get(args[++i]);
                case "--recursive" -> recursive = true;
                case "--worst" -> worst = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if(dir == null || !Files.isDirectory(dir))
        {
            System.err.println("usage: MbeCompareAnalyzer --dir DIR [--recursive] [--worst N]");
            System.exit(1);
        }

        List<Path> files;
        try(Stream<Path> stream = recursive ? Files.walk(dir) : Files.list(dir))
        {
            files = stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".mbe"))
                .filter(p -> Files.exists(p.resolveSibling(base(p) + ".chip.pcm"))).sorted().toList();
        }

        StringBuilder out = new StringBuilder();
        List<Row> all = new ArrayList<>();
        List<double[]> recoveries = new ArrayList<>();
        int calls = 0;
        say(out, "%d calls with chip audio in %s (JMBE: chip response on, repeat memory %d)%n", files.size(), dir,
            jmbe.codec.ambe.AMBEChipResponse.repeatMemory());
        for(Path file : files)
        {
            List<Row> rows = compare(file, out, recoveries);
            if(rows != null)
            {
                all.addAll(rows);
                calls++;
            }
        }

        say(out, "%n== all %d calls%n", calls);
        stats(out, all);
        if(!recoveries.isEmpty())
        {
            say(out, "  after chip-invalid frames (JMBE - chip dB, frames +1..+8, median over %d):%n    ",
                recoveries.size());
            for(int k = 0; k < 8; k++)
            {
                final int kk = k;
                say(out, "%+6.1f", median(recoveries.stream().mapToDouble(r -> r[kk]).filter(v -> !Double.isNaN(v))
                    .toArray()));
            }
            say(out, "%n");
        }
        decisions(out, all);
        byVoicingCode(out, all);
        List<Row> ranked = new ArrayList<>(all.stream().filter(Row::active).toList());
        ranked.sort((a, b) -> Double.compare(Math.abs(b.diff()), Math.abs(a.diff())));
        say(out, "  worst %d frames:%n", Math.min(worst, ranked.size()));
        for(Row r : ranked.subList(0, Math.min(worst, ranked.size())))
        {
            say(out, "    %s frame %4d  JMBE - chip %+6.1f dB  (chip %5.1f dB)  f0 %5.1f  voiced %3d%%  errors %d  %s / %s%n",
                r.call(), r.frame(), r.diff(), r.chipDb(), r.f0(), r.voicedShare(), r.errors(), r.dcmode(), r.action());
        }

        Files.writeString(dir.resolve("mbe_compare_summary.txt"), out);
        List<String> csv = new ArrayList<>();
        StringBuilder header = new StringBuilder("call,frame,chip_db,jmbe_db,diff_db,dcmode,jmbe_action,errors,f0,voiced_pct,type,b0,b1,hex");
        for(int b = 0; b < BANDS; b++)
        {
            header.append(",band").append(b).append("_diff");
        }
        csv.add(header.toString());
        for(Row r : all)
        {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s,%d,%.2f,%.2f,%.2f,%s,%s,%d,%.1f,%d,%s,%d,%d,%s",
                r.call(), r.frame(), r.chipDb(), r.jmbeDb(), r.diff(), r.dcmode(), r.action(), r.errors(), r.f0(),
                r.voicedShare(), r.type(), r.b0(), r.b1(), r.hex()));
            for(double v : r.bandDiff())
            {
                s.append(String.format(Locale.ROOT, ",%.2f", v));
            }
            csv.add(s.toString());
        }
        Files.write(dir.resolve("mbe_compare_frames.csv"), csv);
        System.out.println("wrote " + dir.resolve("mbe_compare_summary.txt") + " and " + dir.resolve("mbe_compare_frames.csv"));
    }

    private static String base(Path p)
    {
        return p.getFileName().toString().replaceAll("\\.mbe$", "");
    }

    private static List<Row> compare(Path file, StringBuilder out, List<double[]> recoveries) throws IOException
    {
        String name = base(file);
        MBECallSequence sequence = MBECallSequenceReader.load(file);
        if(sequence == null || sequence.isEncrypted())
        {
            return null;
        }
        List<byte[]> frames = new ArrayList<>();
        for(VoiceFrame vf : sequence.getVoiceFrames())
        {
            byte[] b = vf.getFrameBytes();
            if(b == null || b.length != 9)
            {
                return null;
            }
            frames.add(b);
        }
        double[] chip = ProbeSupport.readPcm(file.resolveSibling(name + ".chip.pcm"), null);
        double[] jmbe = ProbeSupport.synthesize(frames, true);
        List<AMBEModelParameters> parameters = ProbeSupport.decode(frames);
        Map<Integer, String> dcmode = readDcmode(file.resolveSibling(name + ".chip_dcmode.csv"));
        int count = Math.min(frames.size(), chip.length / N);
        if(count < 8)
        {
            return null;
        }

        int delay = delay(chip, jmbe, count);
        List<Row> rows = new ArrayList<>();
        for(int i = 1; i < count - 1; i++)
        {
            int c = i * N + delay;
            if(c + N + WINDOW > chip.length)
            {
                break;
            }
            double chipPower = power(chip, c, N);
            double jmbePower = power(jmbe, i * N, N);
            double[] cb = bands(chip, c - (WINDOW - N) / 2);
            double[] jb = bands(jmbe, i * N - (WINDOW - N) / 2);
            double[] bandDiff = new double[BANDS];
            for(int b = 0; b < BANDS; b++)
            {
                bandDiff[b] = db(jb[b]) - db(cb[b]);
            }
            AMBEModelParameters p = parameters.get(i);
            boolean[] voiced = p.getVoicingDecisions();
            int v = 0;
            for(int l = 1; l < voiced.length; l++)
            {
                v += voiced[l] ? 1 : 0;
            }
            int share = voiced.length > 1 ? (int)Math.round(100.0 * v / (voiced.length - 1)) : 0;
            String action = p.isMaxFrameRepeat() ? "mute" : p.isRepeatFrame() ? "repeat" :
                p.getFrameType() == FrameType.TONE ? "tone" :
                p.getFrameType() == FrameType.SILENCE ? "noise" : "use";
            AMBEFrame frame = new AMBEFrame(frames.get(i));
            int[] b = frame.getB();
            StringBuilder hex = new StringBuilder();
            for(byte x : frames.get(i))
            {
                hex.append(String.format("%02X", x));
            }
            rows.add(new Row(name, i, db(chipPower), db(jmbePower), bandDiff, dcmode.getOrDefault(i, ""), action,
                p.getErrorCountTotal(), p.getFundamentalFrequency() * 8000 / (2 * Math.PI), share,
                chipPower > ACTIVE && jmbePower > ACTIVE, String.valueOf(frame.getFrameType()),
                b != null && b.length > 0 ? b[0] : -1, b != null && b.length > 1 ? b[1] : -1, hex.toString()));
        }

        say(out, "%n== %s: %d frames, chip delay %d samples%n", name, count, delay);
        stats(out, rows);

        //Invalid frame agreement
        int chipInvalid = 0, jmbeInvalid = 0, both = 0;
        Map<Integer, Row> byFrame = new HashMap<>();
        for(Row r : rows)
        {
            byFrame.put(r.frame(), r);
        }
        StringBuilder list = new StringBuilder();
        for(Row r : rows)
        {
            boolean ci = "0020".equals(r.dcmode());
            boolean ji = "repeat".equals(r.action()) || "mute".equals(r.action());
            chipInvalid += ci ? 1 : 0;
            jmbeInvalid += ji ? 1 : 0;
            both += ci && ji ? 1 : 0;
            if(ci || ji)
            {
                list.append(String.format(Locale.ROOT, " %d(%s/%s,e%d)", r.frame(), ci ? "inv" : r.dcmode(), r.action(),
                    r.errors()));
            }
            //Recovery after the last invalid frame of a run
            Row next = byFrame.get(r.frame() + 1);
            if(ci && next != null && !"0020".equals(next.dcmode()))
            {
                double[] rec = new double[8];
                for(int k = 0; k < 8; k++)
                {
                    Row after = byFrame.get(r.frame() + 1 + k);
                    rec[k] = after != null && after.chipDb() > db(ACTIVE) - 20 ? after.diff() : Double.NaN;
                }
                recoveries.add(rec);
                StringBuilder s = new StringBuilder();
                for(double d : rec)
                {
                    s.append(Double.isNaN(d) ? "     -" : String.format(Locale.ROOT, "%+6.1f", d));
                }
                list.append(" [after: ").append(s.toString().trim()).append(']');
            }
        }
        say(out, "  invalid frames: chip %d, JMBE %d, both %d%s%n", chipInvalid, jmbeInvalid, both,
            list.length() > 0 ? ":" + list : "");
        return rows;
    }

    /** Chip DCMODE against JMBE's frame type and action, with the most common frames of each non-voice pair. */
    private static void decisions(StringBuilder out, List<Row> rows)
    {
        Map<String, List<Row>> pairs = new TreeMap<>();
        for(Row r : rows)
        {
            pairs.computeIfAbsent(r.dcmode() + " " + r.type() + " " + r.action(), k -> new ArrayList<>()).add(r);
        }
        say(out, "  chip DCMODE / JMBE frame type / JMBE action:%n");
        for(Map.Entry<String, List<Row>> e : pairs.entrySet())
        {
            List<Row> g = e.getValue();
            say(out, "    %-28s %6d  chip %+6.1f dB  JMBE %+6.1f dB (medians)", e.getKey(), g.size(),
                median(g.stream().mapToDouble(Row::chipDb).toArray()), median(g.stream().mapToDouble(Row::jmbeDb).toArray()));
            if(!e.getKey().startsWith("0002 VOICE use"))
            {
                Map<String, Integer> hexes = new HashMap<>();
                for(Row r : g)
                {
                    hexes.merge(r.hex() + " b0 " + r.b0(), 1, Integer::sum);
                }
                hexes.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
                    .forEach(h -> say(out, "  %s x%d", h.getKey(), h.getValue()));
            }
            say(out, "%n");
        }
    }

    /** Level and spread of active frames per b1 (voicing) code. */
    private static void byVoicingCode(StringBuilder out, List<Row> rows)
    {
        say(out, "  active frames by b1 (voicing code): code  n  voiced%%  level  spread   bands%n");
        for(int code = 0; code < 32; code++)
        {
            final int c = code;
            List<Row> g = rows.stream().filter(Row::active).filter(r -> "VOICE".equals(r.type()) && r.b1() == c).toList();
            if(g.size() >= 20)
            {
                double[] d = g.stream().mapToDouble(Row::diff).toArray();
                double m = median(d);
                StringBuilder s = new StringBuilder();
                for(int b = 0; b < BANDS; b++)
                {
                    final int bb = b;
                    s.append(String.format(Locale.ROOT, "%+5.1f ", median(g.stream().mapToDouble(r -> r.bandDiff()[bb]).toArray())));
                }
                say(out, "    %2d %6d %4.0f %+7.2f %6.2f   %s%n", code, g.size(),
                    median(g.stream().mapToDouble(Row::voicedShare).toArray()), m,
                    1.4826 * median(Arrays.stream(d).map(v -> Math.abs(v - m)).toArray()), s.toString().trim());
            }
        }
    }

    private static void stats(StringBuilder out, List<Row> rows)
    {
        List<Row> active = rows.stream().filter(Row::active).toList();
        say(out, "  %-14s %5s %8s %6s   %s%n", "frames", "n", "level", "spread", "JMBE - chip per 500 Hz band (dB)");
        line(out, "active", active);
        line(out, "voiced", active.stream().filter(r -> r.voicedShare() == 100).toList());
        line(out, "mixed", active.stream().filter(r -> r.voicedShare() > 0 && r.voicedShare() < 100).toList());
        line(out, "unvoiced", active.stream().filter(r -> r.voicedShare() == 0).toList());
        line(out, "with errors", active.stream().filter(r -> r.errors() > 0).toList());
    }

    private static void line(StringBuilder out, String label, List<Row> rows)
    {
        if(rows.isEmpty())
        {
            return;
        }
        double[] d = rows.stream().mapToDouble(Row::diff).toArray();
        double m = median(d);
        double[] dev = Arrays.stream(d).map(v -> Math.abs(v - m)).toArray();
        StringBuilder s = new StringBuilder();
        for(int b = 0; b < BANDS; b++)
        {
            final int bb = b;
            s.append(String.format(Locale.ROOT, "%+5.1f ", median(rows.stream().mapToDouble(r -> r.bandDiff()[bb]).toArray())));
        }
        say(out, "  %-14s %5d %+8.2f %6.2f   %s%n", label, rows.size(), m, 1.4826 * median(dev), s.toString().trim());
    }

    /** Chip lag (0..158, even) with the least spread of the per-frame level difference over active frames. */
    private static int delay(double[] chip, double[] jmbe, int count)
    {
        double[] j = new double[count];
        for(int i = 0; i < count; i++)
        {
            j[i] = power(jmbe, i * N, N);
        }
        int best = 0;
        double bestSpread = Double.MAX_VALUE;
        for(int k = 0; k < N; k += 2)
        {
            double sum = 0, sum2 = 0;
            int n = 0;
            for(int i = 1; i < count - 1; i++)
            {
                if(i * N + k + N > chip.length)
                {
                    break;
                }
                double c = power(chip, i * N + k, N);
                if(c > ACTIVE && j[i] > ACTIVE)
                {
                    double d = db(j[i]) - db(c);
                    sum += d;
                    sum2 += d * d;
                    n++;
                }
            }
            if(n > 10)
            {
                double spread = sum2 / n - (sum / n) * (sum / n);
                if(spread < bestSpread)
                {
                    bestSpread = spread;
                    best = k;
                }
            }
        }
        return best;
    }

    private static double[] bands(double[] x, int start)
    {
        double[] out = new double[BANDS];
        double[] w = new double[WINDOW];
        for(int n = 0; n < WINDOW; n++)
        {
            int k = start + n;
            w[n] = k >= 0 && k < x.length ? x[k] * HANN[n] : 0;
        }
        int perBand = WINDOW / 2 / BANDS;
        for(int k = 0; k < WINDOW / 2; k++)
        {
            double re = 0, im = 0;
            for(int n = 0; n < WINDOW; n++)
            {
                re += w[n] * COS[k][n];
                im -= w[n] * SIN[k][n];
            }
            out[k / perBand] += re * re + im * im;
        }
        return out;
    }

    private static Map<Integer, String> readDcmode(Path path) throws IOException
    {
        Map<Integer, String> map = new HashMap<>();
        if(Files.exists(path))
        {
            for(String line : Files.readAllLines(path))
            {
                String[] f = line.split(",");
                if(f.length >= 2 && f[0].matches("\\d+"))
                {
                    map.put(Integer.parseInt(f[0]), f[1].trim());
                }
            }
        }
        return map;
    }

    private static double power(double[] x, int start, int length)
    {
        double e = 0;
        for(int i = start; i < start + length && i < x.length; i++)
        {
            e += x[i] * x[i];
        }
        return e / length;
    }

    private static double db(double power)
    {
        return 10 * Math.log10(power + 1e-6);
    }

    private static double median(double[] x)
    {
        if(x.length == 0)
        {
            return Double.NaN;
        }
        double[] s = x.clone();
        Arrays.sort(s);
        return s.length % 2 == 1 ? s[s.length / 2] : 0.5 * (s[s.length / 2 - 1] + s[s.length / 2]);
    }

    private static void say(StringBuilder out, String format, Object... args)
    {
        String s = String.format(Locale.ROOT, format, args);
        System.out.print(s);
        out.append(s);
    }
}
