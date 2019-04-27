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

package jmbe.converters.oscillator;

public class Oscillator
{
    private Complex mAnglePerSample;
    private Complex mCurrentAngle = new Complex(0.0f, -1.0f);
    private double mFrequency;
    private double mSampleRate;

    public Oscillator(double frequency, double sampleRate)
    {
        mFrequency = frequency;
        mSampleRate = sampleRate;
        update();
    }

    /**
     * Frequency of the tone being generated by this oscillator
     */
    public double getFrequency()
    {
        return mFrequency;
    }

    /**
     * Sets or changes the frequency of this oscillator
     */
    public void setFrequency(double frequency)
    {
        mFrequency = frequency;
        update();
    }

    /**
     * Sample rate of this oscillator
     */
    public double getSampleRate()
    {
        return mSampleRate;
    }

    /**
     * Sets or changes the sample rate of this oscillator
     */
    public void setSampleRate(double sampleRate)
    {
        mSampleRate = sampleRate;
        update();
    }

    /**
     * Generates an array of real samples from this oscillator.
     *
     * @param sampleCount number of samples to generate and length of the resulting float array.
     * @param gain value
     * @return samples array
     */
    public float[] generate(int sampleCount, float gain)
    {
        float[] samples = new float[sampleCount];

        if(mFrequency != 0.0)
        {
            for(int x = 0; x < sampleCount; x++)
            {
                rotate();
                samples[x] = quadrature() * gain;
            }
        }

        return samples;
    }


    /**
     * Steps the current angle by the angle per sample amount
     */
    public void rotate()
    {
        mCurrentAngle.multiply(mAnglePerSample);
        mCurrentAngle.fastNormalize();
    }

    public float inphase()
    {
        return mCurrentAngle.inphase();
    }

    public float quadrature()
    {
        return mCurrentAngle.quadrature();
    }

    /**
     * Updates the internal values after a frequency or sample rate change
     */
    protected void update()
    {
        float anglePerSample = (float)(2.0d * Math.PI * getFrequency() / getSampleRate());
        mAnglePerSample = Complex.fromAngle(anglePerSample);
    }
}
