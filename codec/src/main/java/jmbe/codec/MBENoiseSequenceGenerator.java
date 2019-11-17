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

public class MBENoiseSequenceGenerator
{
    private float mSample = 3147;
    private float[] mCurrentBuffer = new float[256];

    public MBENoiseSequenceGenerator()
    {
    }

    public float next()
    {
        float next = mSample;
        mSample = ((171.0f * next) + 11213.0f) % 53125;
        return next;
    }

    /**
     * Generates an array of 256 white noise samples.
     */
    public float[] nextBuffer()
    {
        float[] copy = Arrays.copyOf(mCurrentBuffer, mCurrentBuffer.length);

        //Shift the end 96 samples to the beginning so that we can generate 160 new samples
        System.arraycopy(mCurrentBuffer, 160, mCurrentBuffer, 0, 96);

        for(int x = 96; x < 256; x++)
        {
            mCurrentBuffer[x] = next();
        }

        return copy;
    }
}
