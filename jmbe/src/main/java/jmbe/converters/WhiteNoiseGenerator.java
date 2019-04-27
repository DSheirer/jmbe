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

package jmbe.converters;

import java.util.Arrays;
import java.util.Random;

/**
 * White Noise Generator
 *
 * NOTE: replaces Algorithm 117 with a more natural sounding gaussian noise generator.
 */
public class WhiteNoiseGenerator
{
    private static final float GAIN = 26562.5f; //81% of saturation

    private Random mRandom = new Random();
    private float[] mCurrentBuffer = new float[256];

    public WhiteNoiseGenerator()
    {
        nextSample();

        for(int x = 0; x < mCurrentBuffer.length; x++)
        {
            mCurrentBuffer[x] = nextSample();
        }
    }

    /**
     * Generates the next (random) white noise sample
     */
    public float nextSample()
    {
        return (mRandom.nextFloat() * 2.0f - 1.0f);
    }

    /**
     * Generates an array of 256 white noise samples in range of -26,562.5 <> 26,562.5 where each successive buffer
     * overlaps the preceding buffer by 96 samples.
     */
    public float[] nextBuffer()
    {
        float[] copy = Arrays.copyOf(mCurrentBuffer, mCurrentBuffer.length);

        //Shift the end 96 samples to the beginning so that we can generate 160 new samples
        System.arraycopy(mCurrentBuffer, 160, mCurrentBuffer, 0, 96);

        for(int x = 160; x < 256; x++)
        {
            mCurrentBuffer[x] = nextSample() * GAIN;
        }

        return copy;
    }

    public float[] getSamples(int length, float gain)
    {
        float[] samples = new float[length];

        for(int x = 0; x < samples.length; x++)
        {
            samples[x] = (nextSample() * gain);
        }

        return samples;
    }
}
