package jmbe.codec.ambe;

/**
 * Higher order coefficients - b6
 */
public enum HOCB6
{
    V0(new float[] {-0.143886f, 0.235528f, -0.116707f, 0.025541f}),
    V1(new float[] {-0.170182f, -0.063822f, -0.096934f, 0.109704f}),
    V2(new float[] {0.232915f, 0.269793f, 0.047064f, -0.032761f}),
    V3(new float[] {0.153458f, 0.068130f, -0.033513f, 0.126553f}),
    V4(new float[] {-0.440712f, 0.132952f, 0.081378f, -0.013210f}),
    V5(new float[] {-0.480433f, -0.249687f, -0.012280f, 0.007112f}),
    V6(new float[] {-0.088001f, 0.167609f, 0.148323f, -0.119892f}),
    V7(new float[] {-0.104628f, 0.102639f, 0.183560f, 0.121674f}),
    V8(new float[] {0.047408f, -0.000908f, -0.214196f, -0.109372f}),
    V9(new float[] {0.113418f, -0.240340f, -0.121420f, 0.041117f}),
    V10(new float[] {0.385609f, 0.042913f, -0.184584f, -0.017851f}),
    V11(new float[] {0.453830f, -0.180745f, 0.050455f, 0.030984f}),
    V12(new float[] {-0.155984f, -0.144212f, 0.018226f, -0.146356f}),
    V13(new float[] {-0.104028f, -0.260377f, 0.146472f, 0.101389f}),
    V14(new float[] {0.012376f, -0.000267f, 0.006657f, -0.013941f}),
    V15(new float[] {0.165852f, -0.103467f, 0.119713f, -0.075455f});

    private float[] mCoefficients;

    HOCB6(float[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public float[] getCoefficients()
    {
        return mCoefficients;
    }

    public static HOCB6 fromValue(int value)
    {
        if(0 <= value && value <= 15)
        {
            return HOCB6.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-15.  Unsupported value: " + value);
    }
}
