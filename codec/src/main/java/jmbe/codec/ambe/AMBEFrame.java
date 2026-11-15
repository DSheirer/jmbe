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

package jmbe.codec.ambe;

import jmbe.binary.BinaryFrame;
import jmbe.codec.FrameType;
import jmbe.codec.ambe.ambePlus2.FundamentalFrequency;
import jmbe.codec.ambe.tone.Tone;
import jmbe.codec.ambe.tone.ToneParameters;
import jmbe.frame.CorrectedAMBEFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * AMBE 3600 (2450 bits data and 1150 bps FEC) frame decoder
 */
public class AMBEFrame
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBEFrame.class);

    private static final int[] VECTOR_U0_TONE_CHECK = {0, 1, 2, 3, 4, 5};
    private static final int[] VECTOR_U3_TONE_CHECK = {10, 11, 12, 13};
    private static final int[] VECTOR_U0_B0_HIGH = {0, 1, 2, 3};
    private static final int[] VECTOR_U0_B1_HIGH = {4, 5, 6, 7};
    private static final int[] VECTOR_U0_B2_HIGH = {8, 9, 10, 11};
    private static final int[] VECTOR_U1_B3_HIGH = {0, 1, 2, 3, 4, 5, 6, 7};
    private static final int[] VECTOR_U1_HIGH_TONE_VERIFY = {0, 1, 2, 3};
    private static final int[] VECTOR_U1_B4_HIGH = {8, 9, 10, 11};
    private static final int[] VECTOR_U1_LOW_TONE_VERIFY = {8, 9, 10, 11};
    private static final int[] VECTOR_U2_B5_HIGH = {0, 1, 2, 3};
    private static final int[] VECTOR_U2_B6_HIGH = {4, 5, 6};
    private static final int[] VECTOR_U2_B7_HIGH = {7, 8, 9};
    private static final int[] VECTOR_U2_B8_HIGH = {10};
    private static final int[] VECTOR_U3_B1_LOW = {0};
    private static final int[] VECTOR_U3_B2_LOW = {1};
    private static final int[] VECTOR_U3_B0_LOW = {2, 3, 4};
    private static final int[] VECTOR_U3_B4_LOW = {5, 6, 7}; //ICD uses incorrect bit indices
    private static final int[] VECTOR_U3_B3_LOW = {8}; //ICD uses incorrect bit indices
    private static final int[] VECTOR_U3_B5_LOW = {9};
    private static final int[] VECTOR_U3_B6_LOW = {10};
    private static final int[] VECTOR_U3_B7_LOW = {11};
    private static final int[] VECTOR_U3_B8_LOW = {12, 13};
    private static final int[] VECTOR_U0_AD_HIGH = {6, 7, 8, 9, 10, 11};
    //Tone frame AD(0) is u3 bit 4 (TIA-102.BABA-1 Table 10), index 9 counting from the MSB. Index 8 holds ID(0).
    private static final int[] VECTOR_U3_AD_LOW = {9};
    private static final int[] VECTOR_U1_ID = {0, 1, 2, 3, 4, 5, 6, 7};
    //Redundant copies of the tone ID (TIA-102.BABA-1 Table 10): u1(3..0) = ID(7..4), u2(10..7) = ID(3..0),
    //u2(6..0) = ID(7..1), u3(13) = ID(0), u3(12..5) = ID(7..0)
    private static final int[] VECTOR_U1_ID_HIGH_COPY = {8, 9, 10, 11};
    private static final int[] VECTOR_U2_ID_LOW_COPY = {0, 1, 2, 3};
    private static final int[] VECTOR_U2_ID_SHIFTED_COPY = {4, 5, 6, 7, 8, 9, 10};
    private static final int[] VECTOR_U3_ID_LSB_COPY = {0};
    private static final int[] VECTOR_U3_ID_COPY = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int U0_TONE_FRAME_VALUE = 63;
    private static final int U3_TONE_FRAME_VALUE = 0;

    private FundamentalFrequency mFundamentalFrequency;
    private FrameType mFrameType;
    private final int[] mErrors;
    private Tone mTone;
    private int mToneAmplitude;
    private int[] mB;

    /**
     * Constructs an AMBE voice or tone frame
     *
     * @param frame byte array containing frame bits.  This should be a 9-element byte array equivalent to 72 bits.
     */
    public AMBEFrame(byte[] frame)
    {
        this(CorrectedAMBEFrame.correct(frame));
    }

    /**
     * Constructs an AMBE voice or tone frame from a frame that has already been error corrected.
     *
     * @param correctedFrame with error corrected vectors c0 and c1
     */
    public AMBEFrame(CorrectedAMBEFrame correctedFrame)
    {
        mErrors = correctedFrame.getErrors();
        decode(correctedFrame);
    }

    /**
     * Constructs an AMBE voice or tone frame from the hexadecimal string frame representation
     *
     * @param hexString containing the string hexadecimal values of an AMBE frame
     */
    AMBEFrame(String hexString)
    {
        this(fromHex(hexString));
    }

    private static byte[] fromHex(String hexString)
    {
        byte[] data = new byte[hexString.length() / 2];
        for(int x = 0; x < hexString.length(); x += 2)
        {
            data[x / 2] = (byte)(0xFF & Integer.parseInt(hexString.substring(x, x + 2), 16));
        }

        return data;
    }

    /**
     * Decodes the AMBE frame parameters
     */
    private void decode(CorrectedAMBEFrame correctedFrame)
    {
        BinaryFrame vectorC0 = correctedFrame.getC0();
        BinaryFrame vectorC1 = correctedFrame.getC1();
        BinaryFrame vectorC2 = correctedFrame.getC2();
        BinaryFrame vectorC3 = correctedFrame.getC3();

        int b0 = (vectorC0.getInt(VECTOR_U0_B0_HIGH) << 3) + vectorC3.getInt(VECTOR_U3_B0_LOW);
        int errorCount = mErrors[0] + mErrors[1];

        mFundamentalFrequency = FundamentalFrequency.fromValue(b0);

        //Process as either a tone frame or a voice frame.
        if(errorCount < 6 &&
           vectorC0.getInt(VECTOR_U0_TONE_CHECK) == U0_TONE_FRAME_VALUE &&
          (vectorC3.getInt(VECTOR_U3_TONE_CHECK) == U3_TONE_FRAME_VALUE ||
           vectorC1.getInt(VECTOR_U1_HIGH_TONE_VERIFY) == vectorC1.getInt(VECTOR_U1_LOW_TONE_VERIFY)))
        {
            mFrameType = FrameType.TONE;
        }
        else
        {
            mFrameType = mFundamentalFrequency.getFrameType();

            //We should only have non-tone frames at this point.  If the frame type decodes as a tone here, then it's
            // an error and occasionally happens when there's a very high bit error rate.  Override the fundamental
            // frequency to W120 (erasure) which will cause a frame repeat sequence.
            if(mFrameType == FrameType.TONE)
            {
                mFundamentalFrequency = FundamentalFrequency.W120;
                mFrameType = mFundamentalFrequency.getFrameType();
            }

            mB = new int[9];
            mB[0] = b0;
            mB[1] = (vectorC0.getInt(VECTOR_U0_B1_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B1_LOW);
            mB[2] = (vectorC0.getInt(VECTOR_U0_B2_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B2_LOW);
            mB[3] = (vectorC1.getInt(VECTOR_U1_B3_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B3_LOW);
            mB[4] = (vectorC1.getInt(VECTOR_U1_B4_HIGH) << 3) + vectorC3.getInt(VECTOR_U3_B4_LOW);
            mB[5] = (vectorC2.getInt(VECTOR_U2_B5_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B5_LOW);
            mB[6] = (vectorC2.getInt(VECTOR_U2_B6_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B6_LOW);
            mB[7] = (vectorC2.getInt(VECTOR_U2_B7_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B7_LOW);
            mB[8] = (vectorC2.getInt(VECTOR_U2_B8_HIGH) << 2) + vectorC3.getInt(VECTOR_U3_B8_LOW);

        }

        if(mFrameType == FrameType.TONE && AMBEChipResponse.isEnabled() && !isToneIdConsistent(vectorC1, vectorC2, vectorC3))
        {
            //The AMBE-3000R rejects a tone frame whose redundant tone ID copies disagree (INVALID DATA, repeat): with
            //the chip response on, decode it as an erasure, which the chip model repeats
            mFundamentalFrequency = FundamentalFrequency.W120;
            mFrameType = mFundamentalFrequency.getFrameType();
            mB = new int[]{120, 0, 0, 0, 0, 0, 0, 0, 0};
        }

        if(mFrameType == FrameType.TONE)
        {
            mTone = Tone.fromValue(vectorC1.getInt(VECTOR_U1_ID));
            mToneAmplitude = (vectorC0.getInt(VECTOR_U0_AD_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_AD_LOW);
        }
    }

    /**
     * True when every redundant copy of the tone ID in u1, u2 and u3 equals the ID in u1(11..4). u2 and u3 are not
     * error protected, so a bit error there leaves the copies disagreeing. Real DMR traffic: the chip flagged the one
     * tone frame (of 31) with a disagreeing copy (u3, one bit) INVALID DATA and played the other 30 as tones.
     */
    private static boolean isToneIdConsistent(BinaryFrame u1, BinaryFrame u2, BinaryFrame u3)
    {
        int id = u1.getInt(VECTOR_U1_ID);
        return u1.getInt(VECTOR_U1_ID_HIGH_COPY) == (id >> 4) &&
            u2.getInt(VECTOR_U2_ID_LOW_COPY) == (id & 0xF) &&
            u2.getInt(VECTOR_U2_ID_SHIFTED_COPY) == (id >> 1) &&
            u3.getInt(VECTOR_U3_ID_LSB_COPY) == (id & 1) &&
            u3.getInt(VECTOR_U3_ID_COPY) == id;
    }

    public int[] getB()
    {
        return mB;
    }

    /**
     * Frame type for this frame
     */
    public FrameType getFrameType()
    {
        return mFrameType;
    }

    /**
     * Fundamental frequency enumeration entry
     */
    public FundamentalFrequency getFundamentalFrequency()
    {
        return mFundamentalFrequency;
    }

    /**
     * Error array with error counts for blocks b0 and b1
     */
    public int[] getErrors()
    {
        return mErrors;
    }

    /**
     * Indicates if this is a tone frame
     */
    public boolean isToneFrame()
    {
        return getFrameType() == FrameType.TONE;
    }

    /**
     * AMBE frame model parameters for non-TONE frames.  @see getFrameType()
     *
     * @param previous non-TONE voice parameters.
     * @return voice model parameters
     * @throws IllegalStateException on accessing this method for a TONE frame
     */
    public AMBEModelParameters getVoiceParameters(AMBEModelParameters previous)
    {
        if(getFrameType() != FrameType.TONE)
        {
            return new AMBEModelParameters(mFundamentalFrequency, mB, mErrors, previous);
        }
        else
        {
            return new AMBEModelParameters();
        }
    }

    /**
     * AMBE frame tone model parameters.  @see getFrameType()
     *
     * @return tone parameters
     * @throws IllegalStateException if this is not a TONE frame type
     */
    public ToneParameters getToneParameters()
    {
        if(getFrameType() == FrameType.TONE)
        {
            return new ToneParameters(mTone, mToneAmplitude);
        }

        throw new IllegalStateException("Frame type [" + getFrameType() + "] does not provide tone model parameters");
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getFrameType()).append(" FRAME ");

        if(getFrameType() == FrameType.TONE)
        {
            ToneParameters toneParameters = getToneParameters();
            sb.append(" TONE:").append(toneParameters.getTone());
            sb.append(" AMPLITUDE:").append(toneParameters.getAmplitude());
        }
        else
        {
            sb.append(" FUND:").append(getFundamentalFrequency());
        }

        sb.append(" ERRORS:").append(Arrays.toString(getErrors()));

        return sb.toString();
    }
}
