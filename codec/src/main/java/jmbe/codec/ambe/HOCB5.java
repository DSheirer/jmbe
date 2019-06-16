package jmbe.codec.ambe;


/**
 * Higher order coefficients - b5
 */
public enum HOCB5
{
    V0(new float[] {0.264108f, 0.045976f, -0.200999f, -0.122344f}),
    V1(new float[] {0.479006f, 0.227924f, -0.016114f, -0.006835f}),
    V2(new float[] {0.077297f, 0.080775f, -0.068936f, 0.041733f}),
    V3(new float[] {0.185486f, 0.231840f, 0.182410f, 0.101613f}),
    V4(new float[] {-0.012442f, 0.223718f, -0.277803f, -0.034370f}),
    V5(new float[] {-0.059507f, 0.139621f, -0.024708f, -0.104205f}),
    V6(new float[] {-0.248676f, 0.255502f, -0.134894f, -0.058338f}),
    V7(new float[] {-0.055122f, 0.427253f, 0.025059f, -0.045051f}),
    V8(new float[] {-0.058898f, -0.061945f, 0.028030f, -0.022242f}),
    V9(new float[] {0.084153f, 0.025327f, 0.066780f, -0.180839f}),
    V10(new float[] {-0.193125f, -0.082632f, 0.140899f, -0.089559f}),
    V11(new float[] {0.000000f, 0.033758f, 0.276623f, 0.002493f}),
    V12(new float[] {-0.396582f, -0.049543f, -0.118100f, -0.208305f}),
    V13(new float[] {-0.287112f, 0.096620f, 0.049650f, -0.079312f}),
    V14(new float[] {-0.543760f, 0.171107f, -0.062173f, -0.010483f}),
    V15(new float[] {-0.353572f, 0.227440f, 0.230128f, -0.032089f}),
    V16(new float[] {0.248579f, -0.279824f, -0.209589f, 0.070903f}),
    V17(new float[] {0.377604f, -0.119639f, 0.008463f, -0.005589f}),
    V18(new float[] {0.102127f, -0.093666f, -0.061325f, 0.052082f}),
    V19(new float[] {0.154134f, -0.105724f, 0.099317f, 0.187972f}),
    V20(new float[] {-0.139232f, -0.091146f, -0.275479f, -0.038435f}),
    V21(new float[] {-0.144169f, 0.034314f, -0.030840f, 0.022207f}),
    V22(new float[] {-0.143985f, 0.079414f, -0.194701f, 0.175312f}),
    V23(new float[] {-0.195329f, 0.087467f, 0.067711f, 0.186783f}),
    V24(new float[] {-0.123515f, -0.377873f, -0.209929f, -0.212677f}),
    V25(new float[] {0.068698f, -0.255933f, 0.120463f, -0.095629f}),
    V26(new float[] {-0.106810f, -0.319964f, -0.089322f, 0.106947f}),
    V27(new float[] {-0.158605f, -0.309606f, 0.190900f, 0.089340f}),
    V28(new float[] {-0.489162f, -0.432784f, -0.151215f, -0.005786f}),
    V29(new float[] {-0.370883f, -0.154342f, -0.022545f, 0.114054f}),
    V30(new float[] {-0.742866f, -0.204364f, -0.123865f, -0.038888f}),
    V31(new float[] {-0.573077f, -0.115287f, 0.208879f, -0.027698f});

    private float[] mCoefficients;

    HOCB5(float[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public float[] getCoefficients()
    {
        return mCoefficients;
    }

    public static HOCB5 fromValue(int value)
    {
        if(0 <= value && value <= 31)
        {
            return HOCB5.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-31.  Unsupported value: " + value);
    }
}
