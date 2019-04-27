package jmbe.converters.ambe;


public enum HOCB7
{
    V0(new double[] {0.182478, 0.271794, -0.057639, 0.026115}),
    V1(new double[] {0.110795, 0.092854, 0.078125, -0.082726}),
    V2(new double[] {0.057964, 0.000833, 0.176048, 0.135404}),
    V3(new double[] {-0.027315, 0.098668, -0.065801, 0.116421}),
    V4(new double[] {-0.222796, 0.062967, 0.201740, -0.089975}),
    V5(new double[] {-0.193571, 0.309225, -0.014101, -0.034574}),
    V6(new double[] {-0.389053, -0.181476, 0.107682, 0.050169}),
    V7(new double[] {-0.345604, 0.064900, -0.065014, 0.065642}),
    V8(new double[] {0.319393, -0.055491, -0.220727, -0.067499}),
    V9(new double[] {0.460572, 0.084686, 0.048453, -0.011050}),
    V10(new double[] {0.201623, -0.068994, -0.067101, 0.108320}),
    V11(new double[] {0.227528, -0.173900, 0.092417, -0.066515}),
    V12(new double[] {-0.016927, 0.047757, -0.177686, -0.102163}),
    V13(new double[] {-0.052553, -0.065689, 0.019328, -0.033060}),
    V14(new double[] {-0.144910, -0.238617, -0.195206, -0.063917}),
    V15(new double[] {-0.024159, -0.338822, 0.003581, 0.060995});

    private double[] mCoefficients;

    HOCB7(double[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public double[] getCoefficients()
    {
        return mCoefficients;
    }

    public static HOCB7 fromValue(int value)
    {
        if(0 <= value && value <= 15)
        {
            return HOCB7.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-15.  Unsupported value: " + value);
    }
}
