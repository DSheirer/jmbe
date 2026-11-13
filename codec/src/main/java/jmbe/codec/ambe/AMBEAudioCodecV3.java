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

package jmbe.codec.ambe;

import jmbe.frame.CorrectedAMBEFrame;
import jmbe.frame.ICorrectedFrame;
import jmbe.iface.IAudioCodecV3;
import jmbe.iface.IAudioWithMetadata;

/**
 * AMBE audio codec with version 3 support for synthesizing audio from already error corrected frames.
 */
public class AMBEAudioCodecV3 extends AMBEAudioCodecV2 implements IAudioCodecV3
{
    @Override
    public float[] getAudio(ICorrectedFrame frame)
    {
        return getAudio(toAMBEFrame(frame));
    }

    @Override
    public IAudioWithMetadata getAudioWithMetadata(ICorrectedFrame frame)
    {
        return getAudioWithMetadata(toAMBEFrame(frame));
    }

    private static AMBEFrame toAMBEFrame(ICorrectedFrame frame)
    {
        if(frame instanceof CorrectedAMBEFrame correctedFrame)
        {
            return new AMBEFrame(correctedFrame);
        }

        throw new IllegalArgumentException("Unsupported frame type for AMBE: " +
            (frame != null ? frame.getClass().getName() : "(null)"));
    }
}
