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

import jmbe.codec.oscillator.Oscillator;

/**
 * Tone Generator
 */
public class ToneGenerator
{
    private static final double SAMPLE_RATE = 8000.0;
    private static final int SAMPLE_COUNT = 160;  //20ms of samples at 8000 Hz
    private static final float TWO_CHANNEL_GAIN_REDUCTION = 0.5f;
    private final Oscillator mOscillator1 = new Oscillator(0.0, SAMPLE_RATE);
    private final Oscillator mOscillator2 = new Oscillator(0.0, SAMPLE_RATE);

    /**
     * Constructs an instance
     */
    public ToneGenerator()
    {
    }

    /**
     * Generates 20 ms of PCM audio samples at 8000Hz sample rate using the specified frequency and gain parameters
     *
     * @param toneParameters containing frequency(s) and amplitude
     * @param overallGain in range 0.0 (disabled) to 2.0 (maximum) with 1.0 as the default
     * @return pcm audio samples
     */
    public float[] generate(ToneParameters toneParameters, float overallGain)
    {
        if(!toneParameters.isValidTone())
        {
            throw new IllegalArgumentException("Cannot generate tone audio - INVALID tone");
        }

        //If overall gain is 0 then tone generation is disabled.
        if(overallGain == 0.0f)
        {
            return new float[0];
        }

        Tone tone = toneParameters.getTone();

        //Apply the gain specified in the tone parameters to the requested/argument overall gain
        float gain = ((float) toneParameters.getAmplitude() / 128.0f) * overallGain;

        if(tone.hasFrequency2())
        {
            //Reduce the gain by 1/2 when we're using 2x oscillators
            gain *= TWO_CHANNEL_GAIN_REDUCTION;

            mOscillator1.setFrequency(tone.getFrequency1());
            mOscillator2.setFrequency(tone.getFrequency2());
            float[] samples = mOscillator1.generate(SAMPLE_COUNT, gain);
            float[] samples2 = mOscillator2.generate(SAMPLE_COUNT, gain);

            for(int x = 0; x < SAMPLE_COUNT; x++)
            {
                samples[x] += samples2[x];
            }

            return samples;
        }
        else
        {
            mOscillator1.setFrequency(tone.getFrequency1());
            return mOscillator1.generate(SAMPLE_COUNT, gain);
        }
    }
}
