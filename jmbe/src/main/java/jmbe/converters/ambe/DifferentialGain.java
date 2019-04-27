package jmbe.converters.ambe;

/**
 * Differential gain value enumeration.
 */
public enum DifferentialGain
{
    G0(-2.00000),
    G1(-0.67000),
    G2(0.297941),
    G3(0.663728),
    G4(1.036829),
    G5(1.438136),
    G6(1.890077),
    G7(2.227970),
    G8(2.478289),
    G9(2.667544),
    G10(2.793619),
    G11(2.893261),
    G12(3.020630),
    G13(3.138586),
    G14(3.237579),
    G15(3.322570),
    G16(3.432367),
    G17(3.571863),
    G18(3.696650),
    G19(3.814917),
    G20(3.920932),
    G21(4.022503),
    G22(4.123569),
    G23(4.228291),
    G24(4.370569),
    G25(4.543700),
    G26(4.707695),
    G27(4.848879),
    G28(5.056757),
    G29(5.326468),
    G30(5.777581),
    G31(6.874496);

    private double mGain;

    DifferentialGain(double gain)
    {
        mGain = gain;
    }

    public double getGain()
    {
        return mGain;
    }

    public static DifferentialGain fromValue(int value)
    {
        if(0 <= value && value <= 31)
        {
            return DifferentialGain.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-31.  Unsupported value: " + value);
    }
}
