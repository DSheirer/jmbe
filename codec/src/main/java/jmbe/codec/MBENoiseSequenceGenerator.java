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

package jmbe.codec;

import java.util.Arrays;

/**
 * Implements the white noise generator in TIA 102-BABA algorithm 117.
 */
public class MBENoiseSequenceGenerator
{
    private float mSample = 3147;
    private final float[] mCurrentBuffer = new float[256];

    public MBENoiseSequenceGenerator()
    {
        //Preload the buffer with samples
        float next = mSample;
        for(int x = 0; x < mCurrentBuffer.length; x++)
        {
            mCurrentBuffer[x] = next;
            next = ((171.0f * next) + 11213.0f) % 53125;
        }

        mSample = next;
    }

    /**
     * Generates an array of 256 white noise samples.
     */
    public float[] nextBuffer(float gain)
    {
        float[] copy = Arrays.copyOf(mCurrentBuffer, mCurrentBuffer.length);

        //Shift the end 96 samples to the beginning so that we can generate 160 new samples
        System.arraycopy(mCurrentBuffer, 160, mCurrentBuffer, 0, 96);

        int x;

        float next = mSample;

        for(x = 96; x < 256; x++)
        {
            mCurrentBuffer[x] = next;
            next = ((171.0f * next) + 11213.0f) % 53125;
        }

        mSample = next;

        for(x = 0; x < copy.length; x++)
        {
            copy[x] *= gain;
        }

        return copy;
    }
}
