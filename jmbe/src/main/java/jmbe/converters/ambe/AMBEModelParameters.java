package jmbe.converters.ambe;

public class AMBEModelParameters
{
    private static final double ONE_OVER_TWO_SQR_TWO = 1.0 / 2.0 * Math.sqrt(2.0);
    private AMBEFundamentalFrequency mFundamentalFrequency;
    private QuantizationVector mQuantizationVector;
    private boolean[] mVoicingDecisions;
    private double mGain;

    public AMBEModelParameters(int b0, int b1, int b2, int b3, int b4, int b5, int b6, int b7, int b8,
                               int error0, int error1, AMBEModelParameters previousFrame)
    {
        mFundamentalFrequency = AMBEFundamentalFrequency.fromValue(b0);
        mQuantizationVector = QuantizationVector.fromValue(b1);
        setGain(b2, previousFrame);
    }

    public boolean[] getVoicingDecisions()
    {
        if(mVoicingDecisions == null)
        {
            int L = mFundamentalFrequency.getHarmonic().getL();

            mVoicingDecisions = new boolean[L];

            for(int l = 1; l <= L; l++)
            {
                int voiceIndex = (int)(l * mFundamentalFrequency.getFrequency() * 16);
                mVoicingDecisions[l] = mQuantizationVector.isVoiced(voiceIndex);
            }
        }

        return mVoicingDecisions;
    }

    public double getGain()
    {
        return mGain;
    }

    private void setGain(int b2, AMBEModelParameters previousFrame)
    {
        DifferentialGain differentialGain = DifferentialGain.fromValue(b2);
        mGain = differentialGain.getGain() + (0.5 * previousFrame.getGain());
    }

    private void decodePRBAVector(int l, int b3, int b4, int b5, int b6, int b7, int b8)
    {
        double[] G = new double[9];
        G[1] = 0.0;

        //TODO: wrap this in a try/catch block
        PRBA24 prba24 = PRBA24.fromValue(b3);
        G[2] = prba24.getG2();
        G[3] = prba24.getG3();
        G[4] = prba24.getG4();

        //TODO: wrap this in a try/catch block, or maybe do it in the constructor so we can default to a repeate frame?
        PRBA58 prba58 = PRBA58.fromValue(b4);
        G[5] = prba58.getG5();
        G[6] = prba58.getG6();
        G[7] = prba58.getG7();
        G[8] = prba58.getG8();

        double[] R = new double[9];

        //Inverse DCT of G[]
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

        C[1][1] = 0.5 * (R[1] + R[2]);
        C[2][1] = 0.5 * (R[3] + R[4]);
        C[3][1] = 0.5 * (R[5] + R[6]);
        C[4][1] = 0.5 * (R[7] + R[8]);

        C[1][2] = ONE_OVER_TWO_SQR_TWO * (R[1] - R[2]);
        C[2][2] = ONE_OVER_TWO_SQR_TWO * (R[3] - R[4]);
        C[3][2] = ONE_OVER_TWO_SQR_TWO * (R[5] - R[6]);
        C[4][2] = ONE_OVER_TWO_SQR_TWO * (R[7] - R[8]);

        int[] J = LMPRBlockLength.fromValue(l).getBlockLengths();

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

        //Inverse DCT of C to produce c(i,k) which is arranged as T
        double[] T = new double[l];

        int lCounter = 1;

        for(int i = 1; i <= 4; i++)
        {
            int ji = J[i];

            for(int j = 1; j <= ji; j++)
            {
                double acc = 0.0;

                for(int k = 1; k <= ji; k++)
                {
                    acc += (k == 1 ? 1.0 : 2.0) * C[i][k] * Math.cos((Math.PI * ((double)k - 1.0) * ((double)j - 0.5)) / (double)ji);
                }

                T[l] = acc;
                l++;
            }
        }



    }


}
