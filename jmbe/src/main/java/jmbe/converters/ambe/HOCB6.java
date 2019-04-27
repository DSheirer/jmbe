package jmbe.converters.ambe;


public enum HOCB6
{
    V0(new double[] {-0.143886, 0.235528, -0.116707, 0.025541}),
    V1(new double[] {-0.170182, -0.063822, -0.096934, 0.109704}),
    V2(new double[] {0.232915, 0.269793, 0.047064, -0.032761}),
    V3(new double[] {0.153458, 0.068130, -0.033513, 0.126553}),
    V4(new double[] {-0.440712, 0.132952, 0.081378, -0.013210}),
    V5(new double[] {-0.480433, -0.249687, -0.012280, 0.007112}),
    V6(new double[] {-0.088001, 0.167609, 0.148323, -0.119892}),
    V7(new double[] {-0.104628, 0.102639, 0.183560, 0.121674}),
    V8(new double[] {0.047408, -0.000908, -0.214196, -0.109372}),
    V9(new double[] {0.113418, -0.240340, -0.121420, 0.041117}),
    V10(new double[] {0.385609, 0.042913, -0.184584, -0.017851}),
    V11(new double[] {0.453830, -0.180745, 0.050455, 0.030984}),
    V12(new double[] {-0.155984, -0.144212, 0.018226, -0.146356}),
    V13(new double[] {-0.104028, -0.260377, 0.146472, 0.101389}),
    V14(new double[] {0.012376, -0.000267, 0.006657, -0.013941}),
    V15(new double[] {0.165852, -0.103467, 0.119713, -0.075455});

    private double[] mCoefficients;

    HOCB6(double[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public double[] getCoefficients()
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
