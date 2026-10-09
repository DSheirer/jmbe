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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Silence probe: which b0 124 frames does the AMBE-3000R treat as silence (DCMODE 0x0000, silent output at once) and
 * which as invalid (0x0020, repeated)? On real NXDN calls the radio's silence frame (b0..b8 = 124 16 1 52 79 18 14 12
 * 1, hex B9E881526173002A6B) comes back 0x0000 and silent, while the bit error probe's b0 124 frames (the clean
 * frame's b1..b8) came back 0x0020 and were repeated.
 *
 * Writes one call file (.mbe JSON, as sdrtrunk writes them) per variant into DIR, so each runs in a fresh chip
 * session with MbeChipRunner: 20 clean voice frames, 4 variant frames, 12 clean frames. The START_* files lead with
 * the 4 variant frames instead (as at the start of a real call). Variants change one field of the radio's silence
 * frame at a time (b0, b1, b2, b3, b4..b8), plus the bit error probe's frame and a gain sweep.
 *
 * usage: java analysis.probe.SilenceProbe --out DIR
 *        java analysis.probe.MbeChipRunner --dir DIR
 *        (then upload DIR's *.chip.pcm and *.chip_dcmode.csv)
 */
public final class SilenceProbe
{
    /** The bit error probe's clean voice frame (pitch b0 63). */
    static final int[] CLEAN = {63, 0, 25, 87, 78, 8, 14, 13, 1};
    /** The radio's silence frame, B9E881526173002A6B. */
    static final int[] RADIO = {124, 16, 1, 52, 79, 18, 14, 12, 1};

    private SilenceProbe()
    {
    }

    static int[] with(int[] base, int... indexValue)
    {
        int[] b = base.clone();
        for(int i = 0; i < indexValue.length; i += 2)
        {
            b[indexValue[i]] = indexValue[i + 1];
        }
        return b;
    }

    static Map<String, int[]> variants()
    {
        Map<String, int[]> v = new LinkedHashMap<>();
        v.put("01_radio", RADIO);
        v.put("02_errorprobe", with(CLEAN, 0, 124));
        v.put("03_radio_b1_0", with(RADIO, 1, 0));
        v.put("04_radio_b1_24", with(RADIO, 1, 24));
        v.put("05_radio_b1_15", with(RADIO, 1, 15));
        v.put("06_radio_b2_4", with(RADIO, 2, 4));
        v.put("07_radio_b2_8", with(RADIO, 2, 8));
        v.put("08_radio_b2_16", with(RADIO, 2, 16));
        v.put("09_radio_b2_25", with(RADIO, 2, 25));
        v.put("10_radio_b3_87", with(RADIO, 3, 87));
        v.put("11_radio_b4to8_clean", with(RADIO, 4, 78, 5, 8, 6, 14, 7, 13, 8, 1));
        v.put("12_radio_b0_125", with(RADIO, 0, 125));
        v.put("13_radio_b0_126", with(RADIO, 0, 126));
        v.put("14_radio_b0_120", with(RADIO, 0, 120));
        v.put("15_radio_b0_63", with(RADIO, 0, 63));
        v.put("16_errorprobe_b2_1", with(CLEAN, 0, 124, 2, 1));
        v.put("17_errorprobe_b1_16", with(CLEAN, 0, 124, 1, 16));
        return v;
    }

    public static void main(String[] args) throws Exception
    {
        Path out = null;
        for(int i = 0; i < args.length; i++)
        {
            if("--out".equals(args[i]))
            {
                out = Paths.get(args[++i]);
            }
            else
            {
                throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if(out == null)
        {
            System.err.println("usage: SilenceProbe --out DIR");
            System.exit(1);
        }
        Files.createDirectories(out);
        String clean = AmbeFrameEncoder.toHex(AmbeFrameEncoder.encode(CLEAN));
        int files = 0;
        for(Map.Entry<String, int[]> e : variants().entrySet())
        {
            String variant = AmbeFrameEncoder.toHex(AmbeFrameEncoder.encode(e.getValue()));
            List<String> mid = new ArrayList<>();
            add(mid, clean, 20);
            add(mid, variant, 4);
            add(mid, clean, 12);
            write(out.resolve("SILENCE_" + e.getKey() + ".mbe"), mid);
            files++;
            if(e.getKey().startsWith("01_") || e.getKey().startsWith("02_"))
            {
                List<String> start = new ArrayList<>();
                add(start, variant, 4);
                add(start, clean, 20);
                write(out.resolve("START_" + e.getKey() + ".mbe"), start);
                files++;
            }
            System.out.printf("%-24s b = %s  %s%n", e.getKey(), java.util.Arrays.toString(e.getValue()), variant);
        }
        System.out.printf("%d call files in %s; run MbeChipRunner --dir %s%n", files, out, out);
    }

    private static void add(List<String> frames, String hex, int count)
    {
        for(int i = 0; i < count; i++)
        {
            frames.add(hex);
        }
    }

    private static void write(Path file, List<String> frames) throws Exception
    {
        StringBuilder s = new StringBuilder("{\"protocol\":\"NXDN\",\"version\":2,\"call_type\":\"PROBE\",\"from\":\"0\",")
            .append("\"to\":\"0\",\"encrypted\":false,\"system\":\"probe\",\"site\":\"probe\",\"frames\":[");
        long time = 1_700_000_000_000L;
        for(int i = 0; i < frames.size(); i++)
        {
            s.append(i == 0 ? "" : ",").append("{\"time\":").append(time + 20L * i).append(",\"hex\":\"")
                .append(frames.get(i)).append("\"}");
        }
        s.append("]}");
        Files.writeString(file, s);
    }
}
