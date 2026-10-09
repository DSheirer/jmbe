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
import jmbe.VoiceFrame;
import jmbe.codec.MBEModelParameters;
import jmbe.codec.ambe.AMBEFrame;
import jmbe.codec.ambe.AMBEModelParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the MBE call sequence frame parameters to a file.
 */
public class AnalysisSupport
{
    /**
 *   <call>.params.txt  one line per 20 ms frame, whitespace separated:
 *       frameIdx w0 L fecErrors m1..mL v1..vL
 *     frameIdx : 0-based frame number from the start of the call
 *     w0       : radians/sample (2*PI*f0/8000)
 *     L        : number of harmonics
 *     fecErrors: corrected bit errors for the frame (0 = clean)
 *     m1..mL   : UNENHANCED decoded linear amplitudes
 *     v1..vL   : 1 if that harmonic is voiced, else 0
 *     Lines starting with # are ignored.
     */

    public static void write(List<MBEModelParameters> parameters, Path output) throws IOException
    {
        StringBuilder sb = new StringBuilder();

        for(int frame = 0; frame < parameters.size(); frame++)
        {
            MBEModelParameters p = parameters.get(frame);
            sb.append(frame).append(" ");
            sb.append(p.getFundamentalFrequency()).append(" ");
            sb.append(p.getL()).append(" ");
            sb.append(p.getErrorCountTotal()).append(" ");

            for(int i = 1; i <= p.getL(); i++)
            {
                sb.append(p.getSpectralAmplitudes()[i]).append(" ");
            }

            for(int i = 1; i <= p.getL(); i++)
            {
                sb.append(p.getVoicingDecisions()[i] ? "1" : "0").append(" ");
            }

            sb.append("\n");
        }

        Files.write(output, sb.toString().getBytes());
    }

    public static List<MBEModelParameters> getParameters(MBECallSequence sequence)
    {
        List<MBEModelParameters> parameters = new ArrayList<>();
        AMBEModelParameters previous = new AMBEModelParameters();
//        parameters.add(previous);

        for(VoiceFrame voiceFrame: sequence.getVoiceFrames())
        {
            AMBEFrame frame = new AMBEFrame(voiceFrame.getFrameBytes());
            AMBEModelParameters parameter = frame.getVoiceParameters(previous);
            parameters.add(parameter);
            previous = parameter;
        }

        return parameters;
    }
}
