package jmbe.converters.ambe;

/**
 * AMBE Voice/Unvoiced Quantization Vector enumeration
 */
public enum QuantizationVector
{
    V0(new boolean[]{true, true, true, true, true, true, true, true}),
    V1(new boolean[]{true, true, true, true, true, true, true, true}),
    V2(new boolean[]{true, true, true, true, true, true, true, false}),
    V3(new boolean[]{true, true, true, true, true, true, true, true}),
    V4(new boolean[]{true, true, true, true, true, true, false, false}),
    V5(new boolean[]{true, true, false, true, true, true, true, true}),
    V6(new boolean[]{true, true, true, false, true, true, true, true}),
    V7(new boolean[]{true, true, true, true, true, false, true, true}),
    V8(new boolean[]{true, true, true, true, false, false, false, false}),
    V9(new boolean[]{true, true, true, true, true, false, false, false}),
    V10(new boolean[]{true, true, true, false, false, false, false, false}),
    V11(new boolean[]{true, true, true, false, false, false, false, true}),
    V12(new boolean[]{true, true, false, false, false, false, false, false}),
    V13(new boolean[]{true, true, true, false, false, false, false, false}),
    V14(new boolean[]{true, false, false, false, false, false, false, false}),
    V15(new boolean[]{true, true, true, false, false, false, false, false}),
    V16(new boolean[]{false, false, false, false, false, false, false, false}),
    V17(new boolean[]{false, false, false, false, false, false, false, false}),
    V18(new boolean[]{false, false, false, false, false, false, false, false}),
    V19(new boolean[]{false, false, false, false, false, false, false, false}),
    V20(new boolean[]{false, false, false, false, false, false, false, false}),
    V21(new boolean[]{false, false, false, false, false, false, false, false}),
    V22(new boolean[]{false, false, false, false, false, false, false, false}),
    V23(new boolean[]{false, false, false, false, false, false, false, false}),
    V24(new boolean[]{false, false, false, false, false, false, false, false}),
    V25(new boolean[]{false, false, false, false, false, false, false, false}),
    V26(new boolean[]{false, false, false, false, false, false, false, false}),
    V27(new boolean[]{false, false, false, false, false, false, false, false}),
    V28(new boolean[]{false, false, false, false, false, false, false, false}),
    V29(new boolean[]{false, false, false, false, false, false, false, false}),
    V30(new boolean[]{false, false, false, false, false, false, false, false}),
    V31(new boolean[]{false, false, false, false, false, false, false, false});

    private boolean[] mVoiceDecisions;

    QuantizationVector(boolean[] voiceDecisions)
    {
        mVoiceDecisions = voiceDecisions;
    }

    public boolean isVoiced(int index)
    {
        if(0 <= index && index <= 7)
        {
            return mVoiceDecisions[index];
        }

        throw new IllegalArgumentException("Voice decision index must be in range 0-7.  Unsupported index: " + index);
    }

    public static QuantizationVector fromValue(int value)
    {
        if(0 <= value && value <= 31)
        {
            return QuantizationVector.values()[value];
        }

        throw new IllegalArgumentException("Quantization vector values must be in range 0-31.  Unsupported value: " + value);
    }
}

