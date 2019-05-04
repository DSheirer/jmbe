package jmbe.converters.ambe;

import jmbe.converters.FrameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMBE frame voice model parameters
 */
public class AMBEModelParameters
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBEModelParameters.class);

    private static final double ONE_OVER_TWO_SQR_TWO = 1.0 / (2.0 * Math.sqrt(2.0));
    private double mFundamentalFrequency;
    private FrameType mFrameType;
    private int mLHarmonics;
    private boolean[] mVoicingDecisions;
    private double[] mALogSpectralMagnitudes;
    private double[] mMSpectralAmplitudes;
    private double mGain;
    private int mFrameRepeatCount;
    private double mErrorRate;

    /**
     * Creates a default set of model parameters to be used as an initial frame
     */
    public AMBEModelParameters()
    {
        AMBEFundamentalFrequency fundamentalFrequency = AMBEFundamentalFrequency.W124;
        mFrameType = fundamentalFrequency.getFrameType();
        mFundamentalFrequency = fundamentalFrequency.getFrequency();
        mLHarmonics = fundamentalFrequency.getHarmonicCount();
        mVoicingDecisions = new boolean[mLHarmonics + 1];
        mALogSpectralMagnitudes = new double[mLHarmonics + 1];
        for(int l = 1; l <= mLHarmonics; l++)
        {
            mALogSpectralMagnitudes[l] = 1.0;
        }

        mGain = 0.0;
    }

    /**
     * Constructs model parameters for frame type VOICE or SILENCE
     */
    public AMBEModelParameters(AMBEFundamentalFrequency fundamental, int b1, int b2, int b3, int b4, int b5, int b6,
                               int b7, int b8, int error0, int error1, AMBEModelParameters previousFrame)
    {
        //alg 55 & 56
        mErrorRate = (0.95 * previousFrame.getErrorRate()) + (0.001064 * (error0 + error1));
        mFrameType = fundamental.getFrameType();

        //alg 57 & 58 determine if this should be a frame repeat due to excessive errors or ERASURE frame type
        if((fundamental.getFrameType() == FrameType.ERASURE) || (error0 >= 4) || (error0 >= 2 && (error0 + error1) >= 6))
        {
            //alg 59-64
            mFrameRepeatCount = previousFrame.getFrameRepeatCount() + 1;
            mFundamentalFrequency = previousFrame.getFundamentalFrequency();
            mLHarmonics = previousFrame.getL();
            mVoicingDecisions = previousFrame.getVoicingDecisions();
            mALogSpectralMagnitudes = previousFrame.getLogSpectralMagnitudes();
        }
        else
        {
            mFundamentalFrequency = fundamental.getFrequency();
            mLHarmonics = fundamental.getHarmonicCount();

            if(fundamental.getFrameType() == FrameType.VOICE)
            {
                setVoicingDecisions(b1);
            }
            else //Silence frame
            {
                mVoicingDecisions = new boolean[mLHarmonics + 1];
            }
            setGain(b2, previousFrame);
            decodePRBAVector(b3, b4, b5, b6, b7, b8, previousFrame);
        }
    }

    /**
     * Frame type for the current frame
     */
    public FrameType getFrameType()
    {
        return mFrameType;
    }

    /**
     * Indicates the number of frame repeats that have occurred, including this frame.  A value of 0 indicates no
     * frame repeats.
     */
    public int getFrameRepeatCount()
    {
        return mFrameRepeatCount;
    }

    /**
     * Bit error rate calculated from the previous frame's bit error rate and the bit errors for this frame
     */
    public double getErrorRate()
    {
        return mErrorRate;
    }

    /**
     * Indicates if this frame should be muted (ie replaced with comfort noise) due to excessive errors or prolonged
     * frame repeats, also due to excessive errors.
     */
    public boolean isFrameMuted()
    {
        return getErrorRate() > 0.096 || getFrameRepeatCount() >= 4;
    }

    public double getFundamentalFrequency()
    {
        return mFundamentalFrequency;
    }

    private void setVoicingDecisions(int b1)
    {
        QuantizationVector quantizationVector = QuantizationVector.fromValue(b1);

        mVoicingDecisions = new boolean[mLHarmonics + 1];

        for(int l = 1; l <= mLHarmonics; l++)
        {
            int voiceIndex = (int)(l * mFundamentalFrequency * 16);
            mVoicingDecisions[l] = quantizationVector.isVoiced(voiceIndex);
        }
    }

    public boolean[] getVoicingDecisions()
    {
        return mVoicingDecisions;
    }

    public int getL()
    {
        return mLHarmonics;
    }

    public double[] getLogSpectralMagnitudes()
    {
        return mALogSpectralMagnitudes;
    }

    public double[] getSpectralAmplitudes()
    {
        return mMSpectralAmplitudes;
    }

    /**
     * Gain level for this frame
     */
    public double getGain()
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
        mGain = differentialGain.getGain() + (0.5 * previousFrame.getGain());
    }

    /**
     * Decodes the predictive residual block average (PRBA) vectors for the current frame
     */
    private void decodePRBAVector(int b3, int b4, int b5, int b6, int b7, int b8, AMBEModelParameters previousFrame)
    {
        int previousL = previousFrame.getL();
        double[] previousA = previousFrame.getLogSpectralMagnitudes();

        double[] G = new double[9];
        G[1] = 0.0;

        try
        {
            PRBA24 prba24 = PRBA24.fromValue(b3);
            G[2] = prba24.getG2();
            G[3] = prba24.getG3();
            G[4] = prba24.getG4();
        }
        catch(Exception e)
        {
            mLog.error("Unable to decode PRBA 2-4 vector from value B3[" + b3 + "]");
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
            mLog.error("Unable to decode PRBA 5-8 vector from value B4[" + b4 + "]");
        }

        double[] R = new double[9];

        //Alg 27 & 28. Inverse DCT of G[]
        for(int i = 1; i <= 8; i++)
        {
            double a = 0.0;

            for(int m = 1; m <= 8; m++)
            {
                a += (m == 1 ? 1.0 : 2.0) * G[m] * Math.cos((Math.PI * ((double)m - 1) * ((double)i - 0.5)) / 8.0);
            }

            R[i] = a;
        }

        double C[][] = new double[5][18];

        //Alg 29,31,33,35
        C[1][1] = 0.5 * (R[1] + R[2]);
        C[2][1] = 0.5 * (R[3] + R[4]);
        C[3][1] = 0.5 * (R[5] + R[6]);
        C[4][1] = 0.5 * (R[7] + R[8]);

        //Alg 30,32,34,36
        C[1][2] = ONE_OVER_TWO_SQR_TWO * (R[1] - R[2]);
        C[2][2] = ONE_OVER_TWO_SQR_TWO * (R[3] - R[4]);
        C[3][2] = ONE_OVER_TWO_SQR_TWO * (R[5] - R[6]);
        C[4][2] = ONE_OVER_TWO_SQR_TWO * (R[7] - R[8]);

        int[] J = LMPRBlockLength.fromValue(mLHarmonics).getBlockLengths();

        //Alg 37
        for(int i = 1; i <= 4; i++)
        {
            if(J[i] > 2)
            {
                double[] coefficients = null;

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
        double[] T = new double[mLHarmonics + 1];

        int lPointer = 1;

        for(int i = 1; i <= 4; i++)
        {
            int ji = J[i];

            for(int j = 1; j <= ji; j++)
            {
                double acc = 0.0;

                for(int k = 1; k <= ji; k++)
                {
                    acc += (k == 1 ? 1.0 : 2.0) * C[i][k] *
                        Math.cos((Math.PI * ((double)k - 1.0) * ((double)j - 0.5)) / (double)ji);
                }

                T[lPointer++] = acc;
            }
        }

        //Alg 40 & 41
        double kappa = (double)previousL / (double)mLHarmonics;

        double[] k = new double[mLHarmonics + 1];
        int[] kFloor = new int[mLHarmonics + 1];
        double[] s = new double[mLHarmonics + 1];

        for(int l = 1; l <= mLHarmonics; l++)
        {
            k[l] = kappa * (double)l;
            kFloor[l] = (int)Math.floor(k[l]);
            s[l] = k[l] - kFloor[l];
        }

        //Alg 42 - precompute summarization
        double lambdaSum = 0.0;

        for(int lambda = 1; lambda <= mLHarmonics; lambda++)
        {
            lambdaSum += T[lambda];
        }

        lambdaSum /= (double)mLHarmonics;

        //Log Spectral Magnitudes
        mALogSpectralMagnitudes = new double[mLHarmonics + 1];

        //Spectral Amplitudes
        mMSpectralAmplitudes = new double[mLHarmonics + 1];

        for(int l = 1; l <= mLHarmonics; l++)
        {
            //Alg 42
            double gain = mGain - (0.5 * (Math.log(mLHarmonics) / Math.log(2.0))) - lambdaSum;

            double x = 0.0;

            for(int l2 = 1; l2 <= mLHarmonics; l2++)
            {
                //Alg 44 & 45
                double aklPrevious = (kFloor[l2] == 0 ? previousA[1] : (kFloor[l2] <= previousL ? previousA[kFloor[l2]] : previousA[previousL]));
                x += (((1.0 - s[l2]) * aklPrevious) + (s[l2] * aklPrevious));
            }

            //Alg 44 & 45
            double aklPrevious = (kFloor[l] == 0 ? previousA[1] : (kFloor[l] <= previousL ? previousA[kFloor[l]] : previousA[previousL]));
            int lPlus1 = l < mLHarmonics ? l + 1 : mLHarmonics;
            double aklPlus1Previous = ((kFloor[lPlus1]) <= previousL ? previousA[kFloor[lPlus1]] : previousA[previousL]);

            //Alg 43
            mALogSpectralMagnitudes[l] = T[l] + (0.65 * ( 1 - s[l]) * aklPrevious)
                + (0.65 * s[l] * aklPlus1Previous)
                - (0.65 / (double)mLHarmonics) * x
                + gain;

            //Alg 46 - spectral magnitude is based on the (l) band's voicing decision
            if(mVoicingDecisions[l])
            {
                mMSpectralAmplitudes[l] = Math.exp(0.693 * mALogSpectralMagnitudes[l]);
            }
            else
            {
                mMSpectralAmplitudes[l] = (0.2046 / Math.sqrt(mFundamentalFrequency)) *
                    Math.exp(0.693 * mALogSpectralMagnitudes[l]);
            }
        }
    }
}
