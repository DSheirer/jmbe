package jmbe;

import javax.sound.sampled.AudioFormat;

import jmbe.audio.JMBEAudioFormat;
import jmbe.converters.ambe.AMBEAudioConverter;
import jmbe.converters.imbe.IMBEAudioConverter;
import jmbe.iface.IAudioConversionLibrary;
import jmbe.iface.IAudioConverter;

public class JMBEIAudioLibrary implements IAudioConversionLibrary
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
        return 0;
    }

    @Override
    public boolean supports(String codecName)
    {
        switch (codecName)
        {
            case AMBEAudioConverter.CODEC_NAME:
            case IMBEAudioConverter.CODEC_NAME:
                return true;
            default:
                return false;
        }
    }

    @Override
    public IAudioConverter getAudioConverter(String codec, AudioFormat output)
    {
        switch (codec)
        {
            case IMBEAudioConverter.CODEC_NAME:
                if (output.matches(JMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS))
                {
                    return new IMBEAudioConverter();
                }
                else
                {
                    throw new IllegalArgumentException("IMBE audio converter does not support output format [" +
                        output.toString() + "]");
                }
            case AMBEAudioConverter.CODEC_NAME:
                if(output.matches(JMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS))
                {
                    return new AMBEAudioConverter();
                }
                else
                {
                    throw new IllegalArgumentException("AMBE audio converter does not support output format [" +
                        output.toString() + "]");
                }
            default:
                throw new IllegalArgumentException("Unsupported CODEC:" + (codec != null ? codec : "(null)"));
        }
    }
}
