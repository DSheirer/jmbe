package jmbe.codec.imbe;

/*******************************************************************************
 * jmbe - Java MBE Library
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
 ******************************************************************************/

import jmbe.audio.AudioWithoutMetadata;
import jmbe.frame.CorrectedProVoiceFrame;
import jmbe.frame.ICorrectedFrame;
import jmbe.iface.IAudioCodecV3;
import jmbe.iface.IAudioWithMetadata;

/**
 * EDACS ProVoice IMBE 7100 audio codec with version 3 support for synthesizing audio from already error corrected
 * frames.
 */
public class ProVoiceIMBEAudioCodecV3 extends ProVoiceIMBEAudioCodec implements IAudioCodecV3
{
    @Override
    public float[] getAudio(ICorrectedFrame frame)
    {
        if(frame instanceof CorrectedProVoiceFrame correctedFrame)
        {
            return getAudio(new IMBEFrame(correctedFrame));
        }

        throw new IllegalArgumentException("Unsupported frame type for ProVoice: " +
            (frame != null ? frame.getClass().getName() : "(null)"));
    }

    /**
     * Note: this method is for compatibility with the AMBE synthesizer and does not return any metadata.
     */
    @Override
    public IAudioWithMetadata getAudioWithMetadata(ICorrectedFrame frame)
    {
        return AudioWithoutMetadata.create(getAudio(frame));
    }
}
