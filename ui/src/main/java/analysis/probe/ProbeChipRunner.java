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

import thumbdv.ThumbDv;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Sends the probe frames to the ThumbDV / AMBE-3000 in packet mode and writes the decoded audio to chip.pcm
 * (raw 16-bit as returned by the chip, big-endian). Each pitch group is a separate device session: every group
 * starts with a 24-frame baseline, so resetting the chip between groups loses nothing, and a group whose
 * response count comes back short is retried (up to 3 times) instead of shifting all later audio.
 *
 * Also writes chip_dcmode.csv: the chip's DCMODE status per frame (voice active, tone frame, or invalid data /
 * frame repeat), so frames the chip rejected are visible.
 *
 * usage: java analysis.probe.ProbeChipRunner --dir DIR [--protocol NXDN]
 */
public final class ProbeChipRunner
{
    public static void main(String[] args) throws Exception
    {
        Path dir = null;
        ThumbDv.AudioProtocol protocol = ThumbDv.AudioProtocol.NXDN;
        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--dir" -> dir = Paths.get(args[++i]);
                case "--protocol" -> protocol = ThumbDv.AudioProtocol.valueOf(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if(dir == null)
        {
            System.err.println("usage: ProbeChipRunner --dir DIR [--protocol NXDN]");
            System.exit(1);
        }

        ProbePlan plan = ProbePlan.read(dir);
        List<byte[]> frames = ProbeSupport.readFrames(dir.resolve("frames.hex"));
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        List<String> status = new java.util.ArrayList<>();
        status.add("frame,group,dcmode_hex,dcmode");

        int groups = plan.baselines.size();
        for(int g = 0; g < groups; g++)
        {
            int start = groupStart(plan, g);
            int end = g + 1 < groups ? groupStart(plan, g + 1) : frames.size();
            List<byte[]> chunk = frames.subList(start, end);

            List<thumbdv.message.response.DecodeSpeechResponse> audio = null;
            for(int attempt = 1; attempt <= 3; attempt++)
            {
                audio = ThumbDv.decodeResponses(chunk, protocol);
                if(audio.size() == chunk.size())
                {
                    break;
                }
                System.out.printf("group %d attempt %d: %d of %d frames returned, retrying%n", g, attempt,
                    audio.size(), chunk.size());
                audio = null;
            }
            if(audio == null)
            {
                throw new IllegalStateException("Group " + g + " did not return a complete decode after 3 attempts");
            }
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for(int i = 0; i < audio.size(); i++)
            {
                byte[] frameAudio = audio.get(i).getAudioPayload();
                if(frameAudio.length != 2 * ProbeSupport.FRAME)
                {
                    throw new IllegalStateException("Unexpected audio payload length " + frameAudio.length);
                }
                pcm.write(frameAudio);
                String label = audio.get(i).getDCMODE();
                counts.merge(label.isEmpty() ? "(no DCMODE)" : label, 1, Integer::sum);
                status.add(String.format(java.util.Locale.ROOT, "%d,%d,%04X,%s", start + i, g,
                    Math.max(audio.get(i).getDCMODEValue(), 0), label));
            }
            System.out.printf("group %d: %d frames decoded %s%n", g, chunk.size(), counts);
        }

        Files.write(dir.resolve("chip.pcm"), pcm.toByteArray());
        Files.write(dir.resolve("chip_dcmode.csv"), status);
        System.out.println("wrote " + dir.resolve("chip_dcmode.csv"));
        System.out.println("wrote " + dir.resolve("chip.pcm"));
    }

    static int groupStart(ProbePlan plan, int group)
    {
        for(ProbePlan.Probe p : plan.probes)
        {
            if(p.group == group)
            {
                return p.start;
            }
        }
        throw new IllegalArgumentException("No probes in group " + group);
    }
}
