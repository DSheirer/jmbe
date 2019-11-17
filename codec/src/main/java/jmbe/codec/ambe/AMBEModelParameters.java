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

import jmbe.codec.FrameType;
import jmbe.codec.MBEModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMBE frame voice model parameters
 */
public class AMBEModelParameters extends MBEModelParameters
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBEModelParameters.class);

    private static final float ONE_OVER_TWO_SQR_TWO = 1.0f / (2.0f * (float)Math.sqrt(2.0f));
    private static final float TWO_PI = 2.0f * (float)Math.PI;
    private float mGain;
    public int[] mB;

    /**
     * Creates a default set of model parameters to be used as an initial frame
     */
    public AMBEModelParameters()
    {
        super(AMBEFundamentalFrequency.W124);
        setDefaults(FrameType.VOICE);
    }

    /**
     * Constructs model parameters for frame type VOICE or SILENCE
     */
    public AMBEModelParameters(AMBEFundamentalFrequency fundamental, int[] b, int[] errors, AMBEModelParameters previous)
    {
        super(fundamental);

        mB = b;

        //Alg 55 & 56
        setErrorCountTotal(errors[0] + errors[1]);
        setErrorRate((0.95f * previous.getErrorRate()) + (0.001064f * getErrorCountTotal()));

        //Alg 57 & 58 determine if this should be a frame repeat due to excessive errors or ERASURE frame type
        if(fundamental.getFrameType() == FrameType.ERASURE)
        {
            setDefaults(FrameType.ERASURE);
        }
        else if((errors[0] >= 4) || (errors[0] >= 2 && getErrorCountTotal() >= 6))
        {
            //Alg 59-64
            setRepeatCount(previous.getRepeatCount() + 1);
            setMBEFundamentalFrequency(previous.getAMBEFundamentalFrequency());
            mGain = previous.getGain();
            setVoicingDecisions(previous.getVoicingDecisions());
            setLog2SpectralAmplitudes(previous.getLog2SpectralAmplitudes());
            setSpectralAmplitudes(previous.getSpectralAmplitudes(), previous.getLocalEnergy(), previous.getAmplitudeThreshold());
            setLocalEnergy(previous.getLocalEnergy());
        }
        else
        {
            if(fundamental.getFrameType() == FrameType.VOICE)
            {
                setVoicingDecisions(b[1]);
            }
            else //Silence frame
            {
                setVoicingDecisions(new boolean[getL() + 1]);
            }

            setGain(b[2], previous);
            decodePRBAVector(b[3], b[4], b[5], b[6], b[7], b[8], previous);
        }
    }

    /**
     * Sets default parameters for the frame type
     * @param frameType to set
     */
    private void setDefaults(FrameType frameType)
    {
        setFrameType(frameType);

        setVoicingDecisions(new boolean[getL() + 1]);
        float[] log2SpectralAmplitudes = new float[getL() + 1];
        setLog2SpectralAmplitudes(log2SpectralAmplitudes);
        mSpectralAmplitudes = new float[getL() + 1];

        for(int l = 0; l < mSpectralAmplitudes.length; l++)
        {
            mSpectralAmplitudes[l] = 1.0f;
        }

        mEnhancedSpectralAmplitudes = mSpectralAmplitudes;

        mGain = 0.0f;
    }

    /**
     * AMBE fundamental frequency enumeration value
     * @return fundamental frequency
     */
    public AMBEFundamentalFrequency getAMBEFundamentalFrequency()
    {
        return (AMBEFundamentalFrequency)getMBEFundamentalFrequency();
    }

    /**
     * Indicates if this is an ERASURE frame type.
     */
    public boolean isErasureFrame()
    {
        return getFrameType() == FrameType.ERASURE;
    }

    /**
     * Indicates if this frame should be muted (ie replaced with comfort noise) due to excessive errors or prolonged
     * frame repeats.
     */
    public boolean isFrameMuted()
    {
        return getErrorRate() > 0.096 || getRepeatCount() >= 4;
    }

    /**
     * Sets the voiced/not voiced frequency band decisions based on the value of the b1 parameter
     * @param b1 parameter
     */
    private void setVoicingDecisions(int b1)
    {
        AMBEVoicingDecision voicingDecision = AMBEVoicingDecision.fromValue(b1);

        boolean[] voicingDecisions = new boolean[getL() + 1];

        for(int l = 1; l <= getL(); l++)
        {
            int voiceIndex = (int)(l * getFundamentalFrequency() * 16 / TWO_PI);
            voicingDecisions[l] = voicingDecision.isVoiced(voiceIndex);
        }

        setVoicingDecisions(voicingDecisions);
    }

    /**
     * Gain level for this frame
     */
    public float getGain()
    {
        return mGain;
    }

    /**
     * Decodes the differential gain level for this frame
     */
    private void setGain(int b2, AMBEModelParameters previousFrame)
    {
        DifferentialGain differentialGain = DifferentialGain.fromValue(b2);

        //Alg 26.
        mGain = differentialGain.getGain() + (0.5f * previousFrame.getGain());
    }

    /**
     * Decodes the predictive residual block average (PRBA) vectors for the current frame
     */
    private void decodePRBAVector(int b3, int b4, int b5, int b6, int b7, int b8, AMBEModelParameters previousParameters)
    {
        float[] G = new float[9];
        G[1] = 0.0f;

        try
        {
            PRBA24 prba24 = PRBA24.fromValue(b3);
            G[2] = prba24.getG2();
            G[3] = prba24.getG3();
            G[4] = prba24.getG4();
        }
        catch(Exception e)
        {
            mLog.error("Unable to getAudio PRBA 2-4 vector from value B3[" + b3 + "]");
        }

        try
        {
            PRBA58 prba58 = PRBA58.fromValue(b4);
            G[5] = prba58.getG5();
            G[6] = prba58.getG6();
            G[7] = prba58.getG7();
            G[8] = prba58.getG8();
        }
        catch(Exception e)
        {
            mLog.error("Unable to getAudio PRBA 5-8 vector from value B4[" + b4 + "]");
        }

        float[] R = new float[9];

        //Alg 27 & 28. Inverse DCT of G[]
        for(int i = 1; i <= 8; i++)
        {
            R[i] = G[1];

            for(int m = 2; m <= 8; m++)
            {
                R[i] += (2.0 * G[m] * (float)Math.cos(((float)Math.PI * (float)(m - 1) * ((float)i - 0.5f)) / 8.0f));
            }
        }

        float C[][] = new float[5][18];

        //Alg 29,31,33,35
        C[1][1] = 0.5f * (R[1] + R[2]);
        C[2][1] = 0.5f * (R[3] + R[4]);
        C[3][1] = 0.5f * (R[5] + R[6]);
        C[4][1] = 0.5f * (R[7] + R[8]);

        //Alg 30,32,34,36
        C[1][2] = ONE_OVER_TWO_SQR_TWO * (R[1] - R[2]);
        C[2][2] = ONE_OVER_TWO_SQR_TWO * (R[3] - R[4]);
        C[3][2] = ONE_OVER_TWO_SQR_TWO * (R[5] - R[6]);
        C[4][2] = ONE_OVER_TWO_SQR_TWO * (R[7] - R[8]);

        int[] J = LMPRBlockLength.fromValue(getL()).getBlockLengths();

        float[] coefficients = null;

        //Alg 37
        for(int i = 1; i <= 4; i++)
        {
            if(J[i] > 2)
            {
                switch(i)
                {
                    case 1:
                        coefficients = HOCB5.fromValue(b5).getCoefficients();
                        break;
                    case 2:
                        coefficients = HOCB6.fromValue(b6).getCoefficients();
                        break;
                    case 3:
                        coefficients = HOCB7.fromValue(b7).getCoefficients();
                        break;
                    case 4:
                        coefficients = HOCB8.fromValue(b8).getCoefficients();
                        break;
                }

                switch(J[i])
                {
                    case 3:
                        C[i][3] = coefficients[0];
                        break;
                    case 4:
                        C[i][3] = coefficients[0];
                        C[i][4] = coefficients[1];
                        break;
                    case 5:
                        C[i][3] = coefficients[0];
                        C[i][4] = coefficients[1];
                        C[i][5] = coefficients[2];
                        break;
                    default:
                        C[i][3] = coefficients[0];
                        C[i][4] = coefficients[1];
                        C[i][5] = coefficients[2];
                        C[i][6] = coefficients[3];
                        break;
                }
            }
        }

        //Alg 38, 39. Inverse DCT of C to produce c(i,k) which is rearranged as T
        float[] T = new float[getL() + 1];

        int lPointer = 1;

        for(int i = 1; i <= 4; i++)
        {
            for(int j = 1; j <= J[i]; j++)
            {
                float acc = C[i][1];

                for(int k = 2; k <= J[i]; k++)
                {
                    acc += 2.0f * C[i][k] *
                        (float)Math.cos(((float)Math.PI * (float)(k - 1) * ((float)j - 0.5f)) / (float)J[i]);
                }

                T[lPointer++] = acc;
            }
        }

        int previousL = previousParameters.getL();

        //Alg 40 & 41
        float kappa = (float)previousL / (float)getL();

        float[] k = new float[getL() + 1];
        int[] kFloor = new int[getL() + 1];
        float[] s = new float[getL() + 1];

        float[] previousA = previousParameters.getLog2SpectralAmplitudes();

        //Alg 44
        previousA[0] = previousA[1];

        for(int l = 1; l <= getL(); l++)
        {
            k[l] = kappa * (float)l;
            kFloor[l] = (int)Math.floor(k[l]);
            s[l] = k[l] - kFloor[l];
        }

        //Alg 42 & 43 - pre-compute sum
        float summation43 = 0.0f;
        float lambdaSum = 0.0f;

        for(int l = 1; l <= getL(); l++)
        {
            float aklPrevious = kFloor[l] <= previousL ? previousA[kFloor[l]] : previousA[previousL];

            int plus1 = l < getL() ? l + 1 : getL();
            float aklPlus1Previous = kFloor[plus1] <= previousL ? previousA[kFloor[plus1]] : previousA[previousL];

            summation43 += (((1.0f - s[l]) * aklPrevious) + (s[l] * aklPlus1Previous));
            lambdaSum += T[l];
        }

        lambdaSum /= (float)getL();

        //Alg 42
        float gain = mGain - (0.5f * (float)(Math.log(getL()) / Math.log(2.0))) - lambdaSum;

        //Log Spectral Amplitudes
        float[] logSpectralAmplitudes = new float[getL() + 1];
        logSpectralAmplitudes[0] = 1.0f;

        //Spectral Amplitudes
        float[] spectralAmplitudes = new float[getL() + 1];

        boolean[] voicingDecisions = getVoicingDecisions();

        float aklPrevious;
        int lPlus1;
        float aklPlus1Previous;

        float unvoicedCoefficient = 0.2046f / (float)Math.sqrt(getFundamentalFrequency());

        summation43 *= (0.65f / (float)getL());

        for(int l = 1; l <= getL(); l++)
        {
            //Alg 44 & 45
            aklPrevious = (kFloor[l] == 0 ? previousA[1] : (kFloor[l] <= previousL ? previousA[kFloor[l]] : previousA[previousL]));
            lPlus1 = l < getL() ? (l + 1) : getL();
            aklPlus1Previous = ((kFloor[lPlus1]) <= previousL ? previousA[kFloor[lPlus1]] : previousA[previousL]);

            //Alg 43
            logSpectralAmplitudes[l] = T[l] + (0.65f * (1.0f - s[l]) * aklPrevious)
                + (0.65f * s[l] * aklPlus1Previous)
                - summation43
                + gain;

            //Alg 46 - spectral magnitude is based on the (l) band's voicing decision
            if(voicingDecisions[l])
            {
                spectralAmplitudes[l] = (float)Math.exp(0.693f * logSpectralAmplitudes[l]);
            }
            else
            {
                spectralAmplitudes[l] = unvoicedCoefficient * (float)Math.exp(0.693f * logSpectralAmplitudes[l]);
            }
        }

        setLog2SpectralAmplitudes(logSpectralAmplitudes);
        setSpectralAmplitudes(spectralAmplitudes, previousParameters.getLocalEnergy(),
            previousParameters.getAmplitudeThreshold());
    }

    /**
     * Pretty output of this frame's parameters
     * @return frame output
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getFrameType()).append(" FRAME ");
        sb.append(" FUND:").append(getMBEFundamentalFrequency());
        sb.append(" HARM:").append(getL());
        sb.append(" ERRATE:").append(getErrorRate());
        sb.append(" GAIN:").append(mGain);

        return sb.toString();
    }
}
