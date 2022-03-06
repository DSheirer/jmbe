package jmbe;

import jmbe.codec.ambe.AMBEAudioCodec;
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioCodecLibrary;

public class JMBEAudioLibrary implements IAudioCodecLibrary
{
    @Override
    public String getVersion()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("JMBE Audio Conversion Library v");
        sb.append(getMajorVersion());
        sb.append(".");
        sb.append(getMinorVersion());
        sb.append(".");
        sb.append(getBuildVersion());

        return sb.toString();
    }

    @Override
    public int getMajorVersion()
    {
        return 1;
    }

    @Override
    public int getMinorVersion()
    {
        return 0;
    }

    @Override
    public int getBuildVersion()
    {
        return 8;
    }

    @Override
    public boolean supports(String codecName)
    {
        switch (codecName)
        {
            case AMBEAudioCodec.CODEC_NAME:
            case IMBEAudioCodec.CODEC_NAME:
                return true;
            default:
                return false;
        }
    }

    @Override
    public IAudioCodec getAudioConverter(String codec)
    {
        switch (codec)
        {
            case IMBEAudioCodec.CODEC_NAME:
                return new IMBEAudioCodec();
            case AMBEAudioCodec.CODEC_NAME:
                return new AMBEAudioCodec();
            default:
                throw new IllegalArgumentException("Unsupported CODEC:" + (codec != null ? codec : "(null)"));
        }
    }
}
