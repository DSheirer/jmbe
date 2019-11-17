package jmbe.codec.ambe;

/**
 * Differential gain value enumeration.
 *
 * Note: first parameter is ICD stated gain table.  Second parameter is an adjustment to increase gain level of
 * generated audio that more closely matches output gain of hardware generated audio.
 */
public enum DifferentialGain
{
    G0(-2.00000f, 1.00f),
    G1(-0.67000f, 1.10f),
    G2(0.297941f, 1.25f),
    G3(0.663728f, 1.55f),
    G4(1.036829f, 1.50f),
    G5(1.438136f, 1.40f),
    G6(1.890077f, 1.40f),
    G7(2.227970f, 1.40f),
    G8(2.478289f, 1.40f),
    G9(2.667544f, 1.40f),
    G10(2.793619f, 1.40f),
    G11(2.893261f, 1.40f),
    G12(3.020630f, 1.50f),
    G13(3.138586f, 1.50f),
    G14(3.237579f, 1.50f),
    G15(3.322570f, 1.50f),
    G16(3.432367f, 1.50f),
    G17(3.571863f, 1.50f),
    G18(3.696650f, 1.50f),
    G19(3.814917f, 1.45f),
    G20(3.920932f, 1.45f),
    G21(4.022503f, 1.40f),
    G22(4.123569f, 1.40f),
    G23(4.228291f, 1.35f),
    G24(4.370569f, 1.25f),
    G25(4.543700f, 1.25f),
    G26(4.707695f, 1.25f),
    G27(4.848879f, 1.20f),
    G28(5.056757f, 1.20f),
    G29(5.326468f, 1.20f),
    G30(5.777581f, 1.10f),
    G31(6.874496f, 1.0f);

    private float mGain;
    public float mAdjustment;

    DifferentialGain(float gain, float adjustment)
    {
        mGain = gain;
        mAdjustment = adjustment;
    }

    public float getGain()
    {
        return mGain + mAdjustment;
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
