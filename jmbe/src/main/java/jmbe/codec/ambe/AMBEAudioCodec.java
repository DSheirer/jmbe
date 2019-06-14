/*
 * ******************************************************************************
 * Copyright (C) 2015-2019 Dennis Sheirer
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

import jmbe.audio.AudioWithMetadata;
import jmbe.audio.AudioWithoutMetadata;
import jmbe.codec.FrameType;
import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioWithMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio converter for AMBE frames encoded at 3600 bps with 2450 bps data and 1250 bps FEC
 */
public class AMBEAudioCodec implements IAudioCodec
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBEAudioCodec.class);

    public static final String CODEC_NAME = "AMBE 3600 x 2450";
    private static final AMBEModelParameters DEFAULT_VOICE_PARAMETERS = new AMBEModelParameters();

    private AMBESynthesizer mSynthesizer = new AMBESynthesizer();
    private AMBEModelParameters mPreviousAMBEModelParameters = DEFAULT_VOICE_PARAMETERS;

    public AMBEAudioCodec()
    {
    }

    /**
     * Converts the AMBE frame data into PCM audio samples at 8kHz 16-bit rate.
     *
     * @param frameData byte array of AMBE frame data
     */
    public float[] getAudio(byte[] frameData)
    {
        AMBEFrame frame = new AMBEFrame(frameData);

        if(frame.getFrameType() == FrameType.TONE)
        {
            //Reset to use a default previous frame for subsequent voice decoding
            reset();
            return mSynthesizer.getTone(frame.getToneParameters());
        }
        else
        {
            AMBEModelParameters AMBEModelParameters = frame.getVoiceParameters(mPreviousAMBEModelParameters);
            mPreviousAMBEModelParameters = AMBEModelParameters;
            return mSynthesizer.getVoice(AMBEModelParameters);
        }
    }

    /**
     * Converts the AMBE frame data into PCM audio samples at 8kHz 16-bit rate.
     * @param frameData byte array for an audio frame
     * @return decoded audio and any associated metadata such as tones or dtmf/knox codes
     */
    @Override
    public IAudioWithMetadata getAudioWithMetadata(byte[] frameData)
    {
        AMBEFrame frame = new AMBEFrame(frameData);

        if(frame.getFrameType() == FrameType.TONE)
        {
            //Reset to use a default previous frame for subsequent voice decoding
            reset();
            AudioWithMetadata audio = AudioWithMetadata.create(mSynthesizer.getTone(frame.getToneParameters()));
            Tone tone = frame.getToneParameters().getTone();

            if(Tone.CALL_PROGRESS_TONES.contains(tone))
            {
                audio.addMetadata("CALL PROGRESS", tone.toString());
            }
            else if(Tone.DISCRETE_TONES.contains(tone))
            {
                audio.addMetadata("TONE", tone.toString());
            }
            else if(Tone.DTMF_TONES.contains(tone))
            {
                audio.addMetadata("DTMF", tone.toString());
            }
            else if(Tone.KNOX_TONES.contains(tone))
            {
                audio.addMetadata("KNOX", tone.toString());
            }

            return audio;
        }
        else
        {
            AMBEModelParameters AMBEModelParameters = frame.getVoiceParameters(mPreviousAMBEModelParameters);
            mPreviousAMBEModelParameters = AMBEModelParameters;
            return AudioWithoutMetadata.create(mSynthesizer.getVoice(AMBEModelParameters));
        }
    }

    /**
     * Resets the audio converter at the end or beginning of each call so that the starting frame is a default frame.
     */
    @Override
    public void reset()
    {
        mPreviousAMBEModelParameters = DEFAULT_VOICE_PARAMETERS;
    }

    /**
     * CODEC Name constant
     */
    @Override
    public String getCodecName()
    {
        return CODEC_NAME;
    }
}
