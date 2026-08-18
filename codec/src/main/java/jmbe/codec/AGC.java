/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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
 * ****************************************************************************
 */

package jmbe.codec;

import org.apache.commons.math3.util.FastMath;

/**
 * Automatic gain control.  Dynamically increases gain to achieve an objective amplitude value against the maximum
 * observed amplitude value from the sample stream.  Once objective is achieved, holds gain at that level until it
 * needs to reduce the gain to stay at or below the objective, or reset.
 */
public class AGC
{
    private static final float MINIMUM_GAIN = 2.4f;
    private static final float DEFAULT_GAIN = 3.0f;
    private static final float MAXIMUM_GAIN = 8.5f;
    private static final float ATTACK_GAIN_LOOP_BANDWIDTH = 0.0015f;
    private static final float DECAY_GAIN_LOOP_BANDWIDTH = 0.05f;
    private static final float OBJECTIVE_AMPLITUDE = Short.MAX_VALUE * 0.85f;
    private static final float MAXIMUM_AMPLITUDE = Short.MAX_VALUE * 0.95f;
    private float mCurrentGain;
    private float mMaxObservedAmplitude;

    /**
     * Constructs an instance
     */
    public AGC()
    {
        reset();
    }

    /**
     * Resets this gain control to prepare for the next audio call/segment.
     */
    public void reset()
    {
        mMaxObservedAmplitude = 0.0f;
        mCurrentGain = DEFAULT_GAIN;
    }

    /**
     * Process a buffer of audio samples and apply gain.
     * @param samples to adjust.
     */
    public void process(float[] samples)
    {
        float currentAmplitude;

        //TODO: max observed should start out as a primitive and then pick greater of observed or 95% of previous observed
        for(float sample: samples)
        {
            currentAmplitude = FastMath.min(Math.abs(sample), MAXIMUM_AMPLITUDE);

            if(currentAmplitude > mMaxObservedAmplitude)
            {
                mMaxObservedAmplitude = currentAmplitude;
            }
        }

        float objective = OBJECTIVE_AMPLITUDE / mMaxObservedAmplitude;

        System.out.println("Unconstrained Objective Gain: " + objective + " Max Observed: " + mMaxObservedAmplitude);

        objective = Math.min(objective, MAXIMUM_GAIN);
        objective = Math.max(objective, MINIMUM_GAIN);

        float gain = mCurrentGain;

        //Stop updating gain once we've reached the objective
        if(Math.abs(objective - gain) < 0.001)
        {
            for(int x = 0; x < samples.length; x++)
            {
                samples[x] *= gain;
                samples[x] = FastMath.min(samples[x], MAXIMUM_AMPLITUDE);
                samples[x] = FastMath.max(samples[x], -MAXIMUM_AMPLITUDE);
            }
        }
        else
        {
            for(int x = 0; x < samples.length; x++)
            {
                if(gain > objective)
                {
                    gain += ((objective - gain) * DECAY_GAIN_LOOP_BANDWIDTH);
                }
                else
                {
                    gain += ((objective - gain) * ATTACK_GAIN_LOOP_BANDWIDTH);
                }

                samples[x] *= gain;
                samples[x] = FastMath.min(samples[x], MAXIMUM_AMPLITUDE);
                samples[x] = FastMath.max(samples[x], -MAXIMUM_AMPLITUDE);
            }

            mCurrentGain = gain;
        }

        System.out.println("Gain: " + gain + " Objective: " + objective);
    }
}
