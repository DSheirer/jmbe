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

package jmbe.codec.imbe;

import jmbe.codec.MBEModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * IMBE audio frame model parameters used for synthesizing audio sample data.
 */
public class IMBEModelParameters extends MBEModelParameters
{
    private final static Logger mLog = LoggerFactory.getLogger(IMBEModelParameters.class);

    private static final int MAX_HEADROOM_THRESHOLD = 3;

    private int mErrorCountCoset0 = 0;  //E0
    private int mErrorCountCoset4 = 0;  //E4

    /**
     * Constructs a default set of model parameters with the voicing
     * decisions set to all false and the mSpectralAmplitudes set to all 1.0
     * and sized to the L argument.
     */
    public IMBEModelParameters(IMBEFundamentalFrequency frequency)
    {
        super(frequency);

        int lplus1 = getL() + 1;

        setVoicingDecisions(new boolean[lplus1]);
        setLog2SpectralAmplitudes(new float[lplus1]);

        float[] spectralAmplitudes = new float[lplus1];

        for(int x = 0; x < lplus1; x++)
        {
            spectralAmplitudes[x] = 1.0f;
        }

        setSpectralAmplitudes(spectralAmplitudes, getLocalEnergy(), getAmplitudeThreshold());
    }

    public IMBEModelParameters()
    {
        this(IMBEFundamentalFrequency.DEFAULT);
    }

    /**
     * Fundamental frequency for this frame.  Also contains sub-elements
     * that specify the number of harmonics (L).
     */
    public IMBEFundamentalFrequency getIMBEFundamentalFrequency()
    {
        return (IMBEFundamentalFrequency)getMBEFundamentalFrequency();
    }

    /**
     * Indicates if the current error rate exceeds the maximum error rate and
     * this frame should be represented by frame muting, or white noise
     */
    public boolean requiresMuting()
    {
        return getErrorRate() > 0.0875f;
    }

    /**
     * Indicates that this frame contains an invalid fundamental frequency or
     * the error rate exceeds the threshold.  Corrective action is to use the
     * copy() method to copy the previous frame's model parameters to this frame
     *
     * @return - true if a frame repeat is required
     */
    public boolean repeatRequired()
    {
        return getIMBEFundamentalFrequency() == IMBEFundamentalFrequency.INVALID || exceedsErrorThreshold();
    }

    /**
     * Algorithm #97 and #98 - defined maximum error rates that require a repeat
     */
    private boolean exceedsErrorThreshold()
    {
        return mErrorCountCoset0 >= 2 && getErrorCountTotal() >= (10.0f + (40.0f * getErrorRate()));
    }

    /**
     * Copies the model parameters from the previous frame's parameters and
     * increments the repeat count.  Use this method when a repeat is required
     * due to high error rates or invalid fundamental frequency value.
     */
    public void copy(IMBEModelParameters previous)
    {
        /* Avoid continuously repeating speech sounds - reset to defaults */
        if(previous.getRepeatCount() > MAX_HEADROOM_THRESHOLD)
        {
            setMBEFundamentalFrequency(IMBEFundamentalFrequency.DEFAULT);
            int lplus1 = getL() + 1;

            setVoicingDecisions(new boolean[lplus1]);
            setLog2SpectralAmplitudes(new float[lplus1]);

            float[] spectralAmplitudes = new float[lplus1];
            for(int x = 0; x < lplus1; x++)
            {
                spectralAmplitudes[x] = 1.0f;
            }

            setSpectralAmplitudes(spectralAmplitudes, getLocalEnergy(), getAmplitudeThreshold());
        }
        else
        {
            setMBEFundamentalFrequency(previous.getIMBEFundamentalFrequency());
            setVoicingDecisions(previous.getVoicingDecisions());
            setLog2SpectralAmplitudes(previous.getLog2SpectralAmplitudes());
            setSpectralAmplitudes(previous.getSpectralAmplitudes(), previous.getLocalEnergy(), previous.getAmplitudeThreshold());
            setAmplitudeThreshold(previous.getAmplitudeThreshold());
            setLocalEnergy(previous.getLocalEnergy());
            mErrorCountCoset0 = previous.getErrorCountCoset0();
            mErrorCountCoset4 = previous.getErrorCountCoset4();
            setErrorCountTotal(previous.getErrorCountTotal());
            setErrorRate(previous.getErrorRate());

            /* Increment the previous repeat count to indicate that this frame
             * is a repeat, in addition to any previously repeated frames */
            setRepeatCount(previous.getRepeatCount() + 1);
        }
    }

    /**
     * Sets the error parameters for this frame and updates the total error rate.
     *
     * @param previousErrorRate - running error rate from previous frame
     * @param errorsCoset0 - error count after error detection/correction of coset word 0
     * @param errorsCoset4 - error count after error detection/correction of coset word 4
     * @param errorsTotal - total error count after error detection/correction
     */
    public void setErrors(float previousErrorRate, int errorsCoset0, int errorsCoset4, int errorsTotal)
    {
        mErrorCountCoset0 = errorsCoset0;
        mErrorCountCoset4 = errorsCoset4;
        setErrorCountTotal(errorsTotal);
        setErrorRate((0.95f * previousErrorRate) + (0.000365f * errorsTotal));
    }

    /**
     * Number of bit errors detected/corrected in coset word 0 of this frame
     */
    public int getErrorCountCoset0()
    {
        return mErrorCountCoset0;
    }

    /**
     * Number of bit errors detected/corrected in coset word 4 of this frame
     */
    public int getErrorCountCoset4()
    {
        return mErrorCountCoset4;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("IMBE FRAME");
        sb.append("\n  Fundamental: " + getIMBEFundamentalFrequency().name() + " " + getIMBEFundamentalFrequency().getFrequency() + " " + (8000 * getIMBEFundamentalFrequency().getFrequency() + "Hz"));
        sb.append("\n  L Harmonic Count: " + getIMBEFundamentalFrequency().getL() + " Bandwidth: " + (getIMBEFundamentalFrequency().getFrequency() * 8000.0 * getIMBEFundamentalFrequency().getL()));
        sb.append("\n  Voicing " + Arrays.toString(getVoicingDecisions()));
        sb.append("\n  Log 2 Spectral " + Arrays.toString(getLog2SpectralAmplitudes()));
        sb.append("\n  Spectral " + Arrays.toString(getSpectralAmplitudes()));
        sb.append("\n  Enhanced Spectral " + Arrays.toString(getEnhancedSpectralAmplitudes()));
        sb.append("\n  Coset 0 Errors: " + mErrorCountCoset0);
        sb.append("\n  Coset 4 Errors: " + mErrorCountCoset4);
        sb.append("\n  Total Errors: " + getErrorCountTotal());
        sb.append("\n  Error Rate: " + getErrorRate());
        sb.append("\n  Repeat Count: " + getRepeatCount());
        sb.append("\n  Local Energy: " + getLocalEnergy());
        sb.append("\n  Ampl Threshold: " + getAmplitudeThreshold());

        return sb.toString();
    }
}
