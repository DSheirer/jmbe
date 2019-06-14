package jmbe.codec.ambe;

/**
 * Higher order coefficients - b7
 */
public enum HOCB7
{
    V0(new float[] {0.182478f, 0.271794f, -0.057639f, 0.026115f}),
    V1(new float[] {0.110795f, 0.092854f, 0.078125f, -0.082726f}),
    V2(new float[] {0.057964f, 0.000833f, 0.176048f, 0.135404f}),
    V3(new float[] {-0.027315f, 0.098668f, -0.065801f, 0.116421f}),
    V4(new float[] {-0.222796f, 0.062967f, 0.201740f, -0.089975f}),
    V5(new float[] {-0.193571f, 0.309225f, -0.014101f, -0.034574f}),
    V6(new float[] {-0.389053f, -0.181476f, 0.107682f, 0.050169f}),
    V7(new float[] {-0.345604f, 0.064900f, -0.065014f, 0.065642f}),
    V8(new float[] {0.319393f, -0.055491f, -0.220727f, -0.067499f}),
    V9(new float[] {0.460572f, 0.084686f, 0.048453f, -0.011050f}),
    V10(new float[] {0.201623f, -0.068994f, -0.067101f, 0.108320f}),
    V11(new float[] {0.227528f, -0.173900f, 0.092417f, -0.066515f}),
    V12(new float[] {-0.016927f, 0.047757f, -0.177686f, -0.102163f}),
    V13(new float[] {-0.052553f, -0.065689f, 0.019328f, -0.033060f}),
    V14(new float[] {-0.144910f, -0.238617f, -0.195206f, -0.063917f}),
    V15(new float[] {-0.024159f, -0.338822f, 0.003581f, 0.060995f});

    private float[] mCoefficients;

    HOCB7(float[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public float[] getCoefficients()
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
