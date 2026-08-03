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
import jmbe.codec.MBEModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thumbdv.ThumbDv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Converts MBE recording files into output products for the enhancement analyzer
 */
public class MBEConverter
{
    private static final Logger LOG = LoggerFactory.getLogger(MBEConverter.class);

    public static void main(String[] args)
    {
        Path path = Paths.get("C:\\Users\\sheirerd\\temp\\AMBE Research\\NXDN");

        if(Files.exists(path) && Files.isDirectory(path))
        {
            try (Stream<Path> stream = Files.list(path))
            {
                stream.filter(Files::isRegularFile).forEach(file ->
                {
                    System.out.println("File: " + file.getFileName().toString());
                    if(file.toString().endsWith(".mbe"))
                    {
                        Path paramsPath = file.getParent().resolve(file.getFileName().toString().replace(".mbe", ".params.txt"));
                        MBECallSequence sequence = MBECallSequenceReader.load(file);

                        if(sequence != null)
                        {
                            List<MBEModelParameters> parameters = AnalysisSupport.getParameters(sequence);

                            try
                            {
                                AnalysisSupport.write(parameters, paramsPath);
                                System.out.println("WROT: " + paramsPath);
                                List<AmbeEnhancementAnalyzer.Frame> frames = AmbeEnhancementAnalyzer.loadParams(paramsPath, "ya!", 1);
                                int a = 0;
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }

//                            List<byte[]> ambeFrameData = new ArrayList<>();
//                            for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
//                            {
//                                ambeFrameData.add(voiceFrame.getFrameBytes());
//                            }
//
//                            Path pcmPath = file.getParent().resolve(file.getFileName().toString().replace(".mbe", ".pcm"));
//
//                            List<byte[]> ambeAudioData = new ArrayList<>();
//
//                            int attempt = 0;
//
//                            while(attempt++ < 3)
//                            {
//                                ambeAudioData.addAll(ThumbDv.decode(ambeFrameData, ThumbDv.AudioProtocol.NXDN));
//
//                                if(ambeAudioData.size() == ambeFrameData.size())
//                                {
//                                    attempt = 3;
//                                }
//                                else
//                                {
//                                    ambeAudioData.clear();
//                                }
//                            }

//                            if(!ambeAudioData.isEmpty())
//                            {
//                                byte[] audio = new byte[ambeAudioData.size() * 320];
//
//                                for(int i = 0; i < ambeAudioData.size(); i++)
//                                {
//                                    System.arraycopy(ambeAudioData.get(i), 0, audio, i * 320, 320);
//                                }
//
//                                try
//                                {
//                                    Files.write(pcmPath, audio);
//                                }
//                                catch(IOException e)
//                                {
//                                    e.printStackTrace();
//                                }
//                            }
                        }
                    }
                });
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            LOG.error("Directory does not exist or is not a directory: " + path);
        }
    }
}
