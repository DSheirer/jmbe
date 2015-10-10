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
		
		sb.append( "JMBE Audio Conversion Library v" );
		sb.append( getMajorVersion() );
		sb.append( "." );
		sb.append( getMinorVersion() );
		sb.append( "." );
		sb.append( getBuildVersion() );

		return sb.toString();
	}

	@Override
	public int getMajorVersion()
	{
		//TODO: read version values from the jar file?
		return 0;
	}

	@Override
	public int getMinorVersion()
	{
		return 3;
	}

	@Override
	public int getBuildVersion()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean supports( String codecName )
	{
		switch( codecName )
		{
			case IMBEAudioConverter.CODEC_NAME:
				return true;
			default:
				return false;
		}
	}

	@Override
	public AudioConverter getAudioConverter( String codec, AudioFormat output )
	{
		switch( codec )
		{
			case IMBEAudioConverter.CODEC_NAME:
				if( output.matches( JMBEAudioFormat.PCM_SIGNED_48KHZ_16BITS ) )
				{
					return new IMBEAudioConverter();
				}
		}

		return null;
	}
}
