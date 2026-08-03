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
import thumbdv.ThumbDv;
import thumbdv.message.response.DecodeSpeechResponse;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Decodes recorded calls (.mbe JSON call sequences, as sdrtrunk writes them) with the AMBE-3000R (ThumbDV), for
 * comparing the chip's audio with JMBE's on real traffic.
 *
 * For every NAME.mbe in the directory (and, with --recursive, below it) with 9-byte AMBE+2 frames, writes next to it:
 * <ul>
 *   <li>NAME.chip.pcm: the chip's audio, 8 kHz 16-bit mono, one 160-sample block per frame, in frame order (the
 *   whole call is sent in one session, so the chip's decoder state runs through the call as in a radio);</li>
 *   <li>NAME.chip_dcmode.csv: the chip's DCMODE status per frame (voice active, tone, invalid data / frame repeat).</li>
 * </ul>
 * Encrypted calls, calls with 4 frames or fewer, and files whose frames are not 9 bytes (e.g. P25 phase 1 IMBE) are
 * skipped and listed. Existing outputs are kept unless --overwrite is given. Prints one line per call and a summary.
 *
 * usage: java analysis.probe.MbeChipRunner --dir DIR [--protocol NXDN] [--recursive] [--overwrite]
 *        (--protocol: the ThumbDV mode, NXDN or DMR for AMBE+2 3600x2450 frames; default NXDN)
 */
public final class MbeChipRunner
{
    private MbeChipRunner()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Path dir = null;
        ThumbDv.AudioProtocol protocol = ThumbDv.AudioProtocol.NXDN;
        boolean recursive = false;
        boolean overwrite = false;
        for(int i = 0; i < args.length; i++)
        {
            switch(args[i])
            {
                case "--dir" -> dir = Paths.get(args[++i]);
                case "--protocol" -> protocol = ThumbDv.AudioProtocol.valueOf(args[++i]);
                case "--recursive" -> recursive = true;
                case "--overwrite" -> overwrite = true;
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if(dir == null || !Files.isDirectory(dir))
        {
            System.err.println("usage: MbeChipRunner --dir DIR [--protocol NXDN] [--recursive] [--overwrite]");
            System.exit(1);
        }

        List<Path> files;
        try(Stream<Path> stream = recursive ? Files.walk(dir) : Files.list(dir))
        {
            files = stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".mbe"))
                .sorted().toList();
        }

        int decoded = 0, kept = 0, frames = 0;
        List<String> skipped = new ArrayList<>();
        for(Path file : files)
        {
            String base = file.getFileName().toString().replaceAll("\\.mbe$", "");
            Path pcmPath = file.resolveSibling(base + ".chip.pcm");
            Path dcPath = file.resolveSibling(base + ".chip_dcmode.csv");
            if(!overwrite && Files.exists(pcmPath))
            {
                kept++;
                continue;
            }

            MBECallSequence sequence = MBECallSequenceReader.load(file);
            if(sequence == null)
            {
                skipped.add(file.getFileName() + ": could not be read");
                continue;
            }
            if(sequence.isEncrypted())
            {
                skipped.add(file.getFileName() + ": encrypted");
                continue;
            }
            if(!sequence.hasAudio())
            {
                skipped.add(file.getFileName() + ": 4 frames or fewer");
                continue;
            }
            List<byte[]> ambe = new ArrayList<>();
            boolean wrongSize = false;
            for(VoiceFrame vf : sequence.getVoiceFrames())
            {
                byte[] b = vf.getFrameBytes();
                if(b == null || b.length != 9)
                {
                    wrongSize = true;
                    break;
                }
                ambe.add(b);
            }
            if(wrongSize)
            {
                skipped.add(file.getFileName() + ": frames are not 9-byte AMBE+2 (protocol " + sequence.getProtocol() + ")");
                continue;
            }

            List<DecodeSpeechResponse> responses = null;
            for(int attempt = 1; attempt <= 3; attempt++)
            {
                responses = ThumbDv.decodeResponses(ambe, protocol);
                if(responses.size() == ambe.size())
                {
                    break;
                }
                System.out.printf("%s attempt %d: %d of %d frames returned, retrying%n", file.getFileName(), attempt,
                    responses.size(), ambe.size());
                responses = null;
            }
            if(responses == null)
            {
                skipped.add(file.getFileName() + ": the chip did not return every frame after 3 attempts");
                continue;
            }

            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            List<String> status = new ArrayList<>();
            status.add("frame,dcmode_hex,dcmode");
            Map<String, Integer> counts = new TreeMap<>();
            for(int i = 0; i < responses.size(); i++)
            {
                byte[] audio = responses.get(i).getAudioPayload();
                if(audio.length != 2 * ProbeSupport.FRAME)
                {
                    throw new IllegalStateException(file.getFileName() + ": unexpected audio payload length " + audio.length);
                }
                pcm.write(audio);
                String label = responses.get(i).getDCMODE();
                counts.merge(label.isEmpty() ? "(no DCMODE)" : label, 1, Integer::sum);
                status.add(String.format(Locale.ROOT, "%d,%04X,%s", i, Math.max(responses.get(i).getDCMODEValue(), 0),
                    label));
            }
            Files.write(pcmPath, pcm.toByteArray());
            Files.write(dcPath, status);
            decoded++;
            frames += ambe.size();
            System.out.printf("%s: %d frames (%.1f s) %s -> %s%n", file.getFileName(), ambe.size(), ambe.size() * 0.02,
                counts, pcmPath.getFileName());
        }

        System.out.printf("%n%d calls decoded (%d frames, %.1f min), %d already had a .chip.pcm (use --overwrite to " +
            "redo), %d skipped%n", decoded, frames, frames * 0.02 / 60, kept, skipped.size());
        for(String s : skipped)
        {
            System.out.println("   skipped " + s);
        }
    }
}
