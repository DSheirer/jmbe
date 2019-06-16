package jmbe.codec;

/**
 * MBE Frame Type enumeration
 */
public enum FrameType
{
    VOICE,
    ERASURE,
    SILENCE,
    TONE;

    public static FrameType fromValue(int value)
    {
        if(0 <= value && value <= 119)
        {
            return VOICE;
        }

        if(120 <= value && value <= 123)
        {
            return ERASURE;
        }

        if(value == 124 || value == 125)
        {
            return SILENCE;
        }

        if(value == 126 || value == 127)
        {
            return TONE;
        }

        throw new IllegalArgumentException("Frame Type value must be in range 0 - 127.  Invalid value: " + value);
    }
}
