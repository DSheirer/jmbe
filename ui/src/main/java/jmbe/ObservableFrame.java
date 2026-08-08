/*
 * ******************************************************************************
 * Copyright (C) 2015-2026 Dennis Sheirer
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

package jmbe;

import jmbe.codec.imbe.IMBEFundamentalFrequency;
import jmbe.codec.imbe.IMBEModelParameters;

/**
 * Observable wrapper for voice frame.
 */
public class ObservableFrame
{
    private final VoiceFrame mVoiceFrame;
    private final IMBEModelParameters mModelParameters;

    /**
     * Constructs an instance
     *
     * @param voiceFrame to wrap
     * @param modelParameters for the frame
     */
    public ObservableFrame(VoiceFrame voiceFrame, IMBEModelParameters modelParameters)
    {
        mVoiceFrame = voiceFrame;
        mModelParameters = modelParameters;
    }

    /**
     * Fundamental frequency for this frame.
     *
     * @return fundamental frequency
     */
    public IMBEFundamentalFrequency getFundamentalFrequency()
    {
        return mModelParameters.getIMBEFundamentalFrequency();
    }

    /**
     * Frequency band count
     * @return count
     */
    public int getBandCount()
    {
        return mModelParameters.getVoicingDecisions().length;
    }

    /**
     * Error rate
     * @return error rate
     */
    public float getErrorRate()
    {
        return mModelParameters.getErrorRate();
    }

    /**
     * Total number of detected and corrected errors.
     * @return total error count
     */
    public int getErrorCountTotal()
    {
        return mModelParameters.getErrorCountTotal();
    }

    /**
     * Indicates if the voice frame is encrypted
     * @return
     */
    public boolean isEncrypted()
    {
        return mVoiceFrame.getAlgorithm() != null;
    }


    /**
     * Voice Frame
     * @return
     */
    public VoiceFrame getVoiceFrame()
    {
        return mVoiceFrame;
    }

    public long getTimestamp()
    {
        return mVoiceFrame.getTimestamp();
    }

    public String getFrame()
    {
        return mVoiceFrame.getFrame();
    }
}
