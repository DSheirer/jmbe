package jmbe;

import jmbe.codec.ambe.AMBEAudioCodec;
import jmbe.codec.ambe.AMBEAudioCodecV2;
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.codec.imbe.IMBEAudioCodecV2;
import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioCodecLibrary;
import jmbe.iface.IAudioCodecLibraryV2;
import jmbe.iface.IAudioCodecV2;

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
        return 2;
    }

    @Override
    public int getMinorVersion()
    {
        return 1;
    }

    @Override
    public int getBuildVersion()
    {
        return 0;
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
            //Return the V2 codec for all cases - V2 is backward compatible with V1
            case IMBEAudioCodec.CODEC_NAME:
                return new IMBEAudioCodecV2();
            case AMBEAudioCodec.CODEC_NAME:
                return new AMBEAudioCodecV2();
            default:
                throw new IllegalArgumentException("Unsupported CODEC:" + (codec != null ? codec : "(null)"));
        }
    }
}
