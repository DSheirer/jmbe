package jmbe;

import javax.sound.sampled.AudioFormat;

import jmbe.audio.JMBEAudioFormat;
import jmbe.converters.imbe.IMBEAudioConverter;
import jmbe.iface.AudioConversionLibrary;
import jmbe.iface.AudioConverter;

public class JMBEAudioLibrary implements AudioConversionLibrary
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
        return 0;
    }

    @Override
    public int getMinorVersion()
    {
        return 4;
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
            case IMBEAudioConverter.CODEC_NAME:
                return true;
            default:
                return false;
        }
    }

    @Override
    public AudioConverter getAudioConverter(String codec, AudioFormat output)
    {
        switch (codec)
        {
            case IMBEAudioConverter.CODEC_NAME:
                if (output.matches(JMBEAudioFormat.PCM_SIGNED_48KHZ_16BITS))
                {
                    return new IMBEAudioConverter(JMBEAudioFormat.PCM_48KHZ_RATE);
                }
                else if (output.matches(JMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS))
                {
                    return new IMBEAudioConverter(JMBEAudioFormat.PCM_8KHZ_RATE);
                }
        }

        return null;
    }
}
