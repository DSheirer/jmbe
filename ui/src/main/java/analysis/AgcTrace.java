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

package analysis;

import jmbe.MBECallSequence;
import jmbe.MBECallSequenceReader;
import jmbe.VoiceFrame;
import jmbe.codec.AGC;
import jmbe.codec.ambe.AMBEFrame;
import jmbe.codec.ambe.AMBESynthesizer;
import jmbe.codec.imbe.IMBEFrame;
import jmbe.codec.imbe.IMBESynthesizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Decodes .mbe call recordings with the AGC enabled and reports the AGC speech classification, noise floor, speech
 * level and gain, for tuning the AGC thresholds against real calls.
 *
 * usage: java AgcTrace <file.mbe or directory> [--frames]
 *   --frames  print one row per frame in addition to the per-call summary
 */
public class AgcTrace
{
    public static void main(String[] args) throws IOException
    {
        if(args.length < 1)
        {
            System.err.println("usage: AgcTrace <file.mbe or directory> [--frames]");
            return;
        }

        Path path = Paths.get(args[0]);
        boolean frames = args.length > 1 && args[1].equals("--frames");
        List<Path> files = new ArrayList<>();

        if(Files.isDirectory(path))
        {
            try(Stream<Path> stream = Files.list(path))
            {
                stream.filter(p -> p.toString().endsWith(".mbe")).sorted().forEach(files::add);
            }
        }
        else
        {
            files.add(path);
        }

        for(Path file : files)
        {
            trace(file, frames);
        }
    }

    private static void trace(Path file, boolean printFrames)
    {
        MBECallSequence sequence = MBECallSequenceReader.load(file);

        if(sequence == null || sequence.isEncrypted())
        {
            return;
        }

        boolean imbe = "APCO25-PHASE1".equals(sequence.getProtocol());
        IMBESynthesizer imbeSynthesizer = new IMBESynthesizer();
        AMBESynthesizer ambeSynthesizer = new AMBESynthesizer();
        AGC agc = imbe ? imbeSynthesizer.getAGC() : ambeSynthesizer.getAGC();

        System.out.println(file.getFileName() + " (" + sequence.getProtocol() + ")");

        if(printFrames)
        {
            System.out.println("  frame speech  floor dB  speech dB   gain  applied  out dB    peak");
        }

        int frameCount = 0;
        int speechFrames = 0;
        int firstSpeechFrame = -1;
        int clippedSamples = 0;
        double speechOutputDbSum = 0.0;
        float peak = 0.0f;

        for(VoiceFrame voiceFrame : sequence.getVoiceFrames())
        {
            float[] samples = imbe ? imbeSynthesizer.getAudio(new IMBEFrame(voiceFrame.getFrameBytes())) :
                ambeSynthesizer.getAudio(new AMBEFrame(voiceFrame.getFrameBytes()));

            double sum = 0.0;
            float framePeak = 0.0f;

            for(float sample : samples)
            {
                sum += sample * sample;
                framePeak = Math.max(framePeak, Math.abs(sample));

                if(Math.abs(sample) >= 0.949f)
                {
                    clippedSamples++;
                }
            }

            double outputDb = sum > 0 ? 10.0 * Math.log10(sum / samples.length) : -100.0;
            peak = Math.max(peak, framePeak);

            if(agc.isSpeech())
            {
                speechFrames++;
                speechOutputDbSum += outputDb;

                if(firstSpeechFrame < 0)
                {
                    firstSpeechFrame = frameCount;
                }
            }

            if(printFrames)
            {
                System.out.printf("  %5d %6s %9.1f %10.1f %6.2f %8.2f %7.1f %7.3f%n", frameCount,
                    agc.isSpeech() ? "S" : "", agc.getNoiseFloorDb(), agc.getSpeechLevelDb(), agc.getGain(),
                    agc.getAppliedGain(), outputDb, framePeak);
            }

            frameCount++;
        }

        System.out.printf("  frames %d  speech %d (%.0f%%)  first speech %s  speech out %.1f dBFS  final gain %.2f" +
                "  peak %.3f  clipped %d%n", frameCount, speechFrames,
            frameCount > 0 ? 100.0 * speechFrames / frameCount : 0.0,
            firstSpeechFrame < 0 ? "none" : (firstSpeechFrame * 20) + " ms",
            speechFrames > 0 ? speechOutputDbSum / speechFrames : -100.0, agc.getGain(), peak, clippedSamples);
    }
}
