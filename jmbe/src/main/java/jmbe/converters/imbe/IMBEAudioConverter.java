package jmbe.converters.imbe;

/*******************************************************************************
 * jmbe - Java MBE Library
 * Copyright (C) 2015 Dennis Sheirer
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
import jmbe.iface.IAudioConverter;
import jmbe.iface.IAudioWithMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IMBEAudioConverter implements IAudioConverter
{
    private final static Logger mLog = LoggerFactory.getLogger(IMBEAudioConverter.class);

    public static final String CODEC_NAME = "IMBE";

    private IMBESynthesizer mSynthesizer;

    public IMBEAudioConverter()
    {
        mSynthesizer = new IMBESynthesizer();
    }

    public void dispose()
    {
    }

    @Override
    public void reset()
    {
        mSynthesizer.reset();
    }

    /**
     * Converts imbe frame data into PCM audio samples at 8kHz 16-bit rate
     */
    public float[] getAudio(byte[] frameData)
    {
        IMBEFrame frame = new IMBEFrame(frameData);
        return mSynthesizer.getAudio(frame);
    }

    /**
     * Converts imbe frame data into PCM audio samples at 8kHz 16-bit rate
     *
     * Note: this method is for compatibility with the AMBE synthesizer and does not return any metadata.
     *
     * @param frameData byte array for an audio frame
     * @return audio with empty metadata.
     */
    @Override
    public IAudioWithMetadata getAudioWithMetadata(byte[] frameData)
    {
        IMBEFrame frame = new IMBEFrame(frameData);
        return AudioWithoutMetadata.create(mSynthesizer.getAudio(frame));
    }

    /**
     * CODEC Name
     */
    @Override
    public String getCodecName()
    {
        return CODEC_NAME;
    }
}
