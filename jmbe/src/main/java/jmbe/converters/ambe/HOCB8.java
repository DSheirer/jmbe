package jmbe.converters.ambe;


public enum HOCB8
{
    V0(new double[] {0.323968, 0.008964, -0.063117, 0.027909}),
    V1(new double[] {0.010900, -0.004030, -0.125016, -0.080818}),
    V2(new double[] {0.109969, 0.256272, 0.042470, 0.000749}),
    V3(new double[] {-0.135446, 0.201769, -0.083426, 0.093888}),
    V4(new double[] {-0.441995, 0.038159, 0.022784, 0.003943}),
    V5(new double[] {-0.155951, 0.032467, 0.145309, -0.041725}),
    V6(new double[] {-0.149182, -0.223356, -0.065793, 0.075016}),
    V7(new double[] {0.096949, -0.096400, 0.083194, 0.049306});

    private double[] mCoefficients;

    HOCB8(double[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public double[] getCoefficients()
    {
        return mCoefficients;
    }

    public static HOCB8 fromValue(int value)
    {
        if(0 <= value && value <= 7)
        {
            return HOCB8.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-7.  Unsupported value: " + value);
    }
}
