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

import jmbe.frame.CorrectedProVoiceFrame;

/**
 * EDACS ProVoice IMBE 7100 audio codec.
 *
 * Input is one 7x24 IMBE 7100 bit grid packed into 21 bytes, row-major, MSB first, as extracted by the EDACS
 * ProVoice decoder (after de-interleave and polarity correction).  CorrectedProVoiceFrame error corrects the frame
 * and reorders it into the IMBE 4400 (P25) parameter layout, and the audio is then synthesized with the standard
 * IMBE synthesizer.
 */
public class ProVoiceIMBEAudioCodec extends IMBEAudioCodecV2
{
    public static final String CODEC_NAME = "PROVOICE";

    public ProVoiceIMBEAudioCodec()
    {
    }

    @Override
    public String getCodecName()
    {
        return CODEC_NAME;
    }

    /**
     * Converts ProVoice IMBE 7100 frame data into PCM audio samples at 8kHz 16-bit rate
     *
     * @param frameData 21-byte ProVoice IMBE 7100 frame
     */
    @Override
    public float[] getAudio(byte[] frameData)
    {
        return getAudio(new IMBEFrame(CorrectedProVoiceFrame.correct(frameData)));
    }
}
