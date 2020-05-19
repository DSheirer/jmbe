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

import jmbe.binary.BinaryFrame;
import jmbe.codec.FrameType;
import jmbe.edac.Golay23;
import jmbe.edac.Golay24;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * AMBE 3600 (2450 bits data and 1150 bps FEC) frame decoder
 */
public class AMBEFrame
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBEFrame.class);

    private static final int[] VECTOR_C0 = {0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 68, 1, 5,
        9, 13, 17, 21};
    private static final int[] VECTOR_C1 = {25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 65, 69, 2, 6, 10, 14, 18, 22, 26,
        30, 34, 38, 42};
    private static final int[] VECTOR_C2 = {46, 50, 54, 58, 62, 66, 70, 3, 7, 11, 15};
    private static final int[] VECTOR_C3 = {19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71};
    private static final int[] VECTOR_U0 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
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
    private static final int[] VECTOR_U3_B3_LOW = {5};
    private static final int[] VECTOR_U3_B4_LOW = {6, 7, 8};
    private static final int[] VECTOR_U3_B5_LOW = {9};
    private static final int[] VECTOR_U3_B6_LOW = {10};
    private static final int[] VECTOR_U3_B7_LOW = {11};
    private static final int[] VECTOR_U3_B8_LOW = {12, 13};
    private static final int[] VECTOR_U0_AD_HIGH = {6, 7, 8, 9, 10, 11};
    private static final int[] VECTOR_U3_AD_LOW = {8};
    private static final int[] VECTOR_U1_ID = {0, 1, 2, 3, 4, 5, 6, 7};
    private static final int U0_TONE_FRAME_VALUE = 63;
    private static final int U3_TONE_FRAME_VALUE = 0;

    private BinaryFrame mFrame;
    private AMBEFundamentalFrequency mFundamentalFrequency;
    private FrameType mFrameType;
    private int[] mErrors = new int[2];
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
        mFrame = BinaryFrame.fromBytes(frame, ByteOrder.LITTLE_ENDIAN);
        decode();
    }

    /**
     * Constructs an AMBE voice or tone frame from the hexadecimal string frame representation
     *
     * @param hexString containing the string hexadecimal values of an AMBE frame
     */
    AMBEFrame(String hexString)
    {
        byte[] data = new byte[hexString.length() / 2];
        for(int x = 0; x < hexString.length(); x += 2)
        {
            data[x / 2] = (byte)(0xFF & Integer.parseInt(hexString.substring(x, x + 2), 16));
        }

        mFrame = BinaryFrame.fromBytes(data, ByteOrder.LITTLE_ENDIAN);
        decode();
    }

    /**
     * Decodes the AMBE frame parameters
     */
    private void decode()
    {
        BinaryFrame vectorC0 = getVector(VECTOR_C0);
        BinaryFrame vectorC1 = getVector(VECTOR_C1);
        BinaryFrame vectorC2 = getVector(VECTOR_C2);
        BinaryFrame vectorC3 = getVector(VECTOR_C3);

        //Error check C0, then descramble and error check C1
        mErrors[0] = Golay24.checkAndCorrect(vectorC0, 0);
        BinaryFrame modulationVector = getModulationVector(vectorC0.getInt(VECTOR_U0));
        vectorC1.xor(modulationVector);
        mErrors[1] = Golay23.checkAndCorrect(vectorC1, 0);
        int b0 = (vectorC0.getInt(VECTOR_U0_B0_HIGH) << 3) + vectorC3.getInt(VECTOR_U3_B0_LOW);
        int errorCount = mErrors[0] + mErrors[1];

        mFundamentalFrequency = AMBEFundamentalFrequency.fromValue(b0);

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
                mFundamentalFrequency = AMBEFundamentalFrequency.W120;
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

        if(mFrameType == FrameType.TONE)
        {
            mTone = Tone.fromValue(vectorC1.getInt(VECTOR_U1_ID));
            mToneAmplitude = (vectorC0.getInt(VECTOR_U0_AD_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_AD_LOW);
        }
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
    public AMBEFundamentalFrequency getFundamentalFrequency()
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

        throw new IllegalStateException("Frame type TONE does not provide model parameters");
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

    /**
     * Extracts a bit vector from the underlying voice frame byte array
     *
     * @param indexes to extract for the vector
     * @return binary frame vector
     */
    private BinaryFrame getVector(int[] indexes)
    {
        BinaryFrame vector = new BinaryFrame(indexes.length);

        int pointer = 0;

        for(int bit : indexes)
        {
            if(mFrame.get(bit))
            {
                vector.set(pointer);
            }

            pointer++;
        }

        return vector;
    }

    /**
     * Generates the bit modulation vector that is xor'd to vector C1 using U0 as the seed.
     *
     * @param seed for the modulation vector
     * @return modulation vector bit sequence to descramble vector C1
     */
    private static BinaryFrame getModulationVector(int seed)
    {
        BinaryFrame modulationVector = new BinaryFrame(23);

        //alg 52
        int prX = 16 * seed;

        for(int x = 0; x < 23; x++)
        {
            //alg 53 - simplified [... - 65536 * floor((173 * pr(n-1) + 13849) / 65536)] to modulus operation
            prX = (173 * prX + 13849) % 65536;

            //alg 54 - values 32768 and above are a 1 and below is a 0 (default)
            if(prX >= 32768)
            {
                modulationVector.set(x);
            }
        }

        return modulationVector;
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
