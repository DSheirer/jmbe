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

import jmbe.codec.FrameType;

import jmbe.codec.MBEModelParameters;
import jmbe.codec.MBESynthesizer;
import jmbe.codec.ambe.tone.ToneGenerator;
import jmbe.codec.ambe.tone.ToneParameters;
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.iface.IAudioCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * AMBE synthesizer implementation
 */
public class AMBESynthesizer extends MBESynthesizer
{
    private final static Logger LOG = LoggerFactory.getLogger(AMBESynthesizer.class);
    private final ToneGenerator mToneGenerator = new ToneGenerator();
    private AMBEModelParameters mPreviousFrame;
    private float mToneGain = 1.0f;

    /**
     * AMBE synthesizer producing 8 kHz 16-bit audio from AMBE audio (voice/tone) frames
     */
    public AMBESynthesizer()
    {
        reset();
    }

    /**
     * Random phase scale for voiced harmonics: the AMBE-3000R's when AMBEChipResponse is enabled.
     */
    @Override
    protected double getPhaseNoiseScale()
    {
        return AMBEChipResponse.phaseNoiseScale();
    }

    /**
     * Voiced-to-unvoiced and unvoiced-to-voiced harmonic fades of the AMBE-3000R (AMBEChipResponse enabled) when the
     * whole frame switches (one of the two frames has no voiced harmonic), except into or out of comfort noise, where
     * the chip's fades follow the published window (silence probe). Band-wise voicing changes between partly voiced
     * frames keep the published window: with the chip fade there, the unvoiced bands of partly voiced codes 8..14
     * came out 0.4..1.0 dB low on 806 real calls.
     */
    @Override
    protected float getVoicingFadeIn(int n, MBEModelParameters previousFrame, MBEModelParameters currentFrame)
    {
        return chipFade(previousFrame, currentFrame) ? AMBEChipResponse.voicingFadeIn(n) :
            super.getVoicingFadeIn(n, previousFrame, currentFrame);
    }

    @Override
    protected float getVoicingFadeOut(int n, MBEModelParameters previousFrame, MBEModelParameters currentFrame)
    {
        return chipFade(previousFrame, currentFrame) ? 1.0f - AMBEChipResponse.voicingFadeIn(n) :
            super.getVoicingFadeOut(n, previousFrame, currentFrame);
    }

    private static boolean chipFade(MBEModelParameters previousFrame, MBEModelParameters currentFrame)
    {
        return AMBEChipResponse.isEnabled() && previousFrame.getFrameType() != FrameType.SILENCE &&
            currentFrame.getFrameType() != FrameType.SILENCE &&
            (!previousFrame.hasVoicedBands() || !currentFrame.hasVoicedBands());
    }

    /** Unvoiced noise low-pass of the AMBE-3000R when AMBEChipResponse is enabled. */
    @Override
    protected float getUnvoicedBinGain(int bin)
    {
        return AMBEChipResponse.noiseBinGain(bin);
    }

    /** The AMBE-3000R's noise overlap-add normalization (AMBEChipResponse enabled): flat noise power across the frame. */
    @Override
    protected double getUnvoicedNormalizationExponent()
    {
        return AMBEChipResponse.noiseNormalizationExponent();
    }

    /**
     * Sets an overall gain value for tone generation.
     *
     * @param gain in range 0.0f (disabled) to 2.0f (maximum) with 1.0f as the default
     */
    public void setToneGain(float gain)
    {
        mToneGain = gain;
    }

    /**
     * Previous AMBE frame parameters
     *
     * @return parameters
     */
    @Override
    public MBEModelParameters getPreviousFrame()
    {
        return mPreviousFrame;
    }

    public void reset()
    {
        super.reset();
        mPreviousFrame = new AMBEModelParameters();
    }

    /**
     * Generates 160 samples (20 ms) of tone audio
     *
     * @param toneParameters to use in generating the tone frame
     * @return samples
     */
    public float[] getTone(ToneParameters toneParameters)
    {
        return mToneGenerator.generate(toneParameters, mToneGain);
    }

    /**
     * Generates 160 samples (20 ms) of audio from the ambe frame.  Can decode both audio and tone frames and handles
     * frame repeats and white noise generation when error rate exceeds thresholds.
     *
     * @param frame of audio
     * @return decoded audio samples
     */
    public float[] getAudio(AMBEFrame frame)
    {
        float[] audio = null;

        if(frame.isToneFrame())
        {
            if(frame.getToneParameters().isValidTone())
            {
                audio = getTone(frame.getToneParameters());
            }
            else
            {
                mPreviousFrame.setRepeatCount(mPreviousFrame.getRepeatCount());

                if(!mPreviousFrame.isMaxFrameRepeat())
                {
                    audio = getVoice(mPreviousFrame);
                }
                else
                {
                    //Frame muting procedure
                    mPreviousFrame = new AMBEModelParameters();
                    audio = getWhiteNoise();
                }
            }
        }
        else
        {
            AMBEModelParameters parameters = frame.getVoiceParameters(mPreviousFrame);

            if(!parameters.isMaxFrameRepeat())
            {
                if(parameters.isErasureFrame())
                {
                    audio = getWhiteNoise();
                }
                else
                {
                    audio = getVoice(parameters);
                }

                mPreviousFrame = parameters;
            }
            else if(AMBEChipResponse.isEnabled())
            {
                //AMBE-3000R muting: silence, keeping the (zeroed) muted frame as the previous one so that muting holds
                //while invalid frames continue and the next valid frame fades in from it
                audio = new float[N_SAMPLES_PER_FRAME];
                mPreviousFrame = parameters;
            }
            else
            {
                //Frame muting procedure
                mPreviousFrame = new AMBEModelParameters();
                audio = getWhiteNoise();
            }
        }

        if(audio == null)
        {
            audio = new float[N_SAMPLES_PER_FRAME];
        }

        return audio;
    }
}
