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

package jmbe.converters.imbe;

import jmbe.binary.BinaryFrame;
import jmbe.edac.Golay23;
import jmbe.edac.Hamming15;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;


public class IMBEFrame
{
    private final static Logger mLog = LoggerFactory.getLogger(IMBEFrame.class);

    public static final float LOG_2 = (float)Math.log(2.0);

    public static final int[] RANDOMIZER_SEED = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    public static final int[] VECTOR_B0 = {0, 1, 2, 3, 4, 5, 141, 142};
    public static final int[] VECTOR_B2 = {6, 7, 8, 98, 99, 140};

    /**
     * Message frame bit index of the voiced/unvoiced decision for all values
     * of L harmonics. On encoding, a voicing decision is made for each of the
     * K frequency bands and recorded in the b1 information vector.
     * On decoding, each of the L harmonics are flagged as voiced or unvoiced
     * according to the harmonic's location within each K frequency band.
     */
    public static final int[] VOICE_DECISION_INDEX = new int[]{0, 92, 92, 92, 93, 93, 93, 94, 94, 94, 95, 95, 95, 96,
        96, 96, 97, 97, 97, 98, 98, 98, 99, 99, 99, 100, 100, 100, 101, 101, 101, 102, 102, 102, 107, 107, 107, 107,
        107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107, 107};

    private BinaryFrame mFrame;
    private IMBEFundamentalFrequency mFundamentalFrequency;
    private int mErrorCountCoset0;
    private int mErrorCountCoset4;
    private int mErrorCountTotal;

    /**
     * Constructs an IMBE frame from a binary message containing an 18-byte or
     * 144-bit message frame, and a previous IMBE frame.  Performs error detection
     * and correction.
     *
     * After construction, use the setPrevious() method to the previous imbe
     * frame so that model parameters can be generated.
     *
     * Use the getModelParameters() method to access the parameters for speech
     * synthesis.
     *
     * Use the .getDefault() method to generate the first (default) IMBE frame
     * to use at the start of a sequence.
     */
    public IMBEFrame(byte[] data)
    {
        mFrame = BinaryFrame.fromBytes(data, ByteOrder.BIG_ENDIAN);
        decode();
    }

    private void decode()
    {
        IMBEInterleave.deinterleave(mFrame);

        mErrorCountCoset0 = Golay23.checkAndCorrect(mFrame, 0);

        derandomize();

        mErrorCountTotal += mErrorCountCoset0;
        mErrorCountTotal += Golay23.checkAndCorrect(mFrame, 23);
        mErrorCountTotal += Golay23.checkAndCorrect(mFrame, 46);
        mErrorCountTotal += Golay23.checkAndCorrect(mFrame, 69);
        mErrorCountCoset4 = Hamming15.checkAndCorrect(mFrame, 92);
        mErrorCountTotal += mErrorCountCoset4;
        mErrorCountTotal += Hamming15.checkAndCorrect(mFrame, 107);
        mErrorCountTotal += Hamming15.checkAndCorrect(mFrame, 122);

        mFundamentalFrequency = IMBEFundamentalFrequency.fromValue(mFrame.getInt(VECTOR_B0));
    }

    public IMBEFundamentalFrequency getFundamentalFrequency()
    {
        return mFundamentalFrequency;
    }

    /**
     * Model parameters calculated for this frame.
     */
    public IMBEModelParameters getModelParameters(IMBEModelParameters previous)
    {
        IMBEModelParameters parameters = new IMBEModelParameters(getFundamentalFrequency());
        parameters.setErrors(previous.getErrorRate(), mErrorCountCoset0, mErrorCountCoset4, mErrorCountTotal);

        /* If we have too many errors and/or the fundamental frequency is invalid
         * perform a repeat by copying the model parameters from previous frame  */
        if(parameters.repeatRequired())
        {
            parameters.copy(previous);
        }
        else
        {
            parameters.setVoicingDecisions(getVoicingDecisions());
            float[] log2SpectralAmplitudes = getLog2SpectralAmplitudes(previous);
            parameters.setLog2SpectralAmplitudes(log2SpectralAmplitudes);
            parameters.setSpectralAmplitudes(getSpectralAmplitudes(log2SpectralAmplitudes), previous.getLocalEnergy(),
                previous.getAmplitudeThreshold());
        }

        return parameters;
    }

    /**
     * Raw binary message source for this frame
     */
    public BinaryFrame getFrame()
    {
        return mFrame;
    }

    /**
     * Removes randomizer by generating a pseudo-random noise sequence from the first 12 bits of coset word c0 and
     * applies (xor) that sequence against message coset words c1 through c6.
     */
    private void derandomize()
    {
        /* Set the offset to the first seed bit plus 23 to point to coset c1 */
        int offset = 23;

        /* Get seed value from first 12 bits of coset c0 */
        int seed = mFrame.getInt(RANDOMIZER_SEED);

        //alg 52
        int prX = 16 * seed;

        for(int x = 0; x < 114; x++)
        {
            //Alg 53 - simplified [... - 65536 * floor((173 * pr(n-1) + 13849) / 65536)] to modulus operation
            prX = (173 * prX + 13849) % 65536;

            //Alg 54 - values 32768 and above are a 1 and below is a 0 (default)
            if(prX >= 32768)
            {
                //This is the same as xor
                mFrame.flip(x + offset);
            }
        }
    }

    /**
     * Reconstructs the spectral amplitude prediction residual set (T) for all values of L
     */
    public float[] getSpectralAmplitudePredictionResiduals()
    {
        int L = getFundamentalFrequency().getL();

        float[] G = new float[7];
        Gain gain = Gain.fromValue(mFrame.getInt(VECTOR_B2));
        G[1] = gain.getGain();

        StepSizes stepSizes = StepSizes.fromL(L);
        QuantizedValueIndexes indexes = QuantizedValueIndexes.fromL(L);

        //Alg 68 - Decoding gain vector G
        for(int m = 3; m <= 7; m++)
        {
            //Note: both the step sizes and quantized value indexes arrays are zero-based indexes so we have to
            // subtract 3 from m to align with the arrays
            int[] indexSet = indexes.getIndexes()[m - 3];

            if(indexSet.length > 0)
            {
                int bm = mFrame.getInt(indexSet);
                G[m - 1] = stepSizes.getStepSizes()[m - 3] * ((float)bm - ((float)Math.pow(2, indexSet.length - 1) + 0.5f));
            }
        }

        int[][] harmonicAllocations = HarmonicAllocation.fromL(L).getAllocations();
        //Harmonic allocation for i = 6 (index 5) will always have the largest allocation - use it to dimension C array

        float[][] C = new float[7][harmonicAllocations[5].length + 1];

        //Alg 69 & 70 - Construct gain vector R as inverse DCT of G and transfer Ri to C[i][1]
        for(int i = 1; i <= 6; i++)
        {
            for(int m = 1; m <= 6; m++)
            {
                C[i][1] += ((m == 1 ? 1.0f: 2.0f) * G[m] * Math.cos((Math.PI * (float)(m - 1) * ((float)i - 0.5f)) / 6.0f));
            }
        }

        //Alg 71 and 72 - Decode the higher order DCT Coefficients
        int m;
        int[] indexSet;
        float bm;

        for(int i = 1; i <= 6; i++)
        {
            int[] harmonics = harmonicAllocations[i - 1];

            if(harmonics.length > 1)
            {
                for(int j = 2; j <= harmonics.length; j++)
                {
                    m = harmonics[j - 1];
                    indexSet = indexes.getIndexes()[m - 3];

                    if(indexSet.length > 0)
                    {
                        bm = mFrame.getInt(indexSet);
                        C[i][j] = stepSizes.getStepSizes()[m - 3] * (bm - (float)Math.pow(2, indexSet.length - 1) + 0.5f);
                    }
                }
            }
        }

        //Alg 73 & 74 - inverse DCT of C to produce c and transfer results to Tl
        float[] T = new float[L + 1];

        /* Algorithm #73 & #74 - perform inverse DCT on each of the J-block
         * sets of coefficients.  Place inverse DCT results in the
         * residuals array */
        int l = 1;

        for(int i = 1; i <= 6; i++) /* J-Block index */
        {
            int Ji = harmonicAllocations[i - 1].length;

            for(int j = 1; j <= Ji; j++)
            {
                for(int k = 1; k <= Ji; k++)
                {
                    T[l] += (k == 1 ? 1.0f : 2.0f) * C[i][k] * Math.cos((Math.PI * (float)(k - 1) * ((float)j - 0.5f)) / (float)Ji);
                }

                l++;
            }
        }

        return T;
    }

    /**
     * Supports Algorithm #75 and #77 assumptions - resize the previous frame's
     * spectral amplitudes to match the current frame's L size.
     *
     * Resizes elements array to ensure a minimum length of nextL + 1.  When
     * increasing the length, the highest index element value is copied to any
     * newly added indices.
     *
     * Index 0 element is set to 1.0.
     *
     * @param elements - current set of elements with an overall length of
     * L + 1.
     * @param nextL - requested new size of L.  returned array will be nextL + 1
     * elements in length.
     * @return properly (re)sized array
     */
    public static float[] resize(float[] elements, int nextL)
    {
        if(nextL > elements.length - 1)
        {
            float[] resized = new float[nextL + 1];

            /* Copy all but index 0 */
            System.arraycopy(elements, 1, resized, 1, elements.length - 1);

            /* Copy the highest index value to the newly added indexes */
            float highest = elements[elements.length - 1];

            /* Algorithm #79 - set all new indexes to previous highest index */
            for(int x = elements.length; x < resized.length; x++)
            {
                resized[x] = highest;
            }

            /* Algorithm #78 - set previous index 0 to 1.0 */
            resized[0] = 1.0f;

            return resized;
        }
        else
        {
            /* Set index 0 to 1.0 */
            elements[0] = 1.0f;

            return elements;
        }
    }

    /**
     * Algorithms 75, 76, 77, 78, and 79 - calculate the current frame's
     * log2M spectral amplitudes using the current frame's spectral amplitude
     * residual values (T) and the previous frame's log2M spectral amplitudes
     * with appropriate scaling to account for differences in L between the
     * two frames.
     *
     * @param previousParameters - previous imbe audio frame
     */
    public float[] getLog2SpectralAmplitudes(IMBEModelParameters previousParameters)
    {
        float L = (float)getFundamentalFrequency().getL();
        int Lplus1 = getFundamentalFrequency().getL() + 1;

        int previousL = previousParameters.getL();

        /* Get previous frame's log2M entries and resize them to 1 greater than
         * the max of the current L, or the previous L.  Set index 0 to 1.0 and
         * any newly expanded indexes to the value of the previously highest
         * numbered index */
        float[] previousLog2M = resize(previousParameters.getLog2SpectralAmplitudes(),
            Math.max(getFundamentalFrequency().getL(), previousL) + 1);

        //Current frame spectral amplitude prediction residuals
        float[] T = getSpectralAmplitudePredictionResiduals();

        float scale = (float)previousL / L;

        float[] kl = new float[Lplus1];
        int[] klFloor = new int[Lplus1];
        float[] sl = new float[Lplus1];
        float sum = 0.0f;

        for(int l = 1; l < Lplus1; l++)
        {
            /* Algorithm #75 - calculate kl */
            kl[l] = (float)l * scale;

            klFloor[l] = (int)Math.floor(kl[l]);

            /* Algorithm #76 - calculate sl */
            sl[l] = kl[l] - (float)klFloor[l];

            /* Algorithm #77 partial - summation */
            sum += ((1.0f - sl[l]) * previousLog2M[klFloor[l]]) + (sl[l] * previousLog2M[klFloor[l] + 1]);
        }

        /* Algorithm #77 - log2M spectral amplitudes of current frame */
        float[] log2M = new float[Lplus1];

        //Alg 55 - Prediction coefficient
        float p;

        if(L <= 15)
        {
            p = 0.4f;
        }
        else if(L <= 24)
        {
            p = 0.03f * L - 0.05f;
        }
        else
        {
            p = 0.7f;
        }

        for(int l = 1; l < Lplus1; l++)
        {
            log2M[l] = T[l]
                + (p * (1.0f - sl[l]) * previousLog2M[klFloor[l]])
                + (p * sl[l] * previousLog2M[klFloor[l] + 1])
                - ((p / L) * sum);
        }

        return log2M;
    }

    /**
     * Creates (M) spectral amplitudes by applying the inverse log2 (ie 2 to the power of value) to each log2M
     */
    private float[] getSpectralAmplitudes(float[] log2SpectralAmplitudes)
    {
        float[] spectralAmplitudes = new float[log2SpectralAmplitudes.length];

        for(int l = 1; l < log2SpectralAmplitudes.length; l++)
        {
            spectralAmplitudes[l] = (float)Math.pow(2.0f, log2SpectralAmplitudes[l]);
        }

        return spectralAmplitudes;
    }

    /**
     * Calculates the log base 2 of the value.
     *
     * log2(x) = log( x ) / log ( 2 );
     */
    public static float log2(float value)
    {
        return (float)Math.log(value) / LOG_2;
    }

    /**
     * Returns the voiced (true) / unvoiced (false) status for each of the
     * L harmonics.  Each of the 'l' harmonics is voiced if the K frequency
     * band to which it belongs is flagged as voiced.  The K frequency band
     * voiced/unvoiced flags are contained in each of the bit vector b1
     * bits of the imbe frame, which are variable length depending on the
     * value of K.
     *
     * @return boolean array containing L voicing decisions in array indexes
     * 1 through L, with array index 0 unused.
     */
    public boolean[] getVoicingDecisions()
    {
        int L = getFundamentalFrequency().getL();

        boolean[] decisions = new boolean[L + 1];

        for(int x = 1; x <= L; x++)
        {
            decisions[x] = mFrame.get(VOICE_DECISION_INDEX[x]);
        }

        return decisions;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("VOICE FRAME FUND:");
        sb.append(getFundamentalFrequency());
        sb.append(" ERROR-0:").append(mErrorCountCoset0);
        sb.append(" TOTAL:").append(mErrorCountTotal);
        sb.append(" ").append(mFrame.toString());

        return sb.toString();
    }
}
