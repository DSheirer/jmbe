package jmbe.audio.imbe;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.FormatConversionProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*******************************************************************************
 *     jmbe - Java MBE Library 
 *     Copyright (C) 2015 Dennis Sheirer
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/

/**
 * Converts IMBE encoded audio to PCM format
 */
public class IMBEAudioConverter extends FormatConversionProvider
{
	private final static Logger mLog = LoggerFactory
			.getLogger( IMBEAudioConverter.class );

	public IMBEAudioConverter()
	{
	}
	
	@Override
	public Encoding[] getSourceEncodings()
	{
		return new Encoding[] { IMBEAudioFormat.IMBE_ENCODING };
	}

	@Override
	public Encoding[] getTargetEncodings()
	{
		return new Encoding[] { AudioFormat.Encoding.PCM_SIGNED };
	}

	@Override
	public Encoding[] getTargetEncodings( AudioFormat sourceFormat )
	{
		if( sourceFormat.matches( IMBEAudioFormat.IMBE_AUDIO_FORMAT ) )
		{
			return getTargetEncodings();
		}
		
		return null;
	}

	@Override
	public AudioFormat[] getTargetFormats( Encoding targetEncoding,
			AudioFormat sourceFormat )
	{
		if( compare( AudioFormat.Encoding.PCM_SIGNED, targetEncoding ) &&
			IMBEAudioFormat.IMBE_AUDIO_FORMAT.matches( sourceFormat ) )
		{
			return new AudioFormat[] { IMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS,
									   IMBEAudioFormat.PCM_SIGNED_48KHZ_16BITS };
		}
		
		return null;
	}

	@Override
	public AudioInputStream getAudioInputStream( Encoding targetEncoding,
			AudioInputStream sourceStream )
	{
		if( compare( AudioFormat.Encoding.PCM_SIGNED, targetEncoding ) &&
			IMBEAudioFormat.IMBE_AUDIO_FORMAT.matches( sourceStream.getFormat() ) )
		{
			return getAudioInputStream( IMBEAudioFormat.PCM_SIGNED_48KHZ_16BITS, 
					sourceStream );
		}
		
		return null;
	}

	@Override
	public AudioInputStream getAudioInputStream( AudioFormat targetFormat,
			AudioInputStream sourceStream )
	{
		if( IMBEAudioFormat.IMBE_AUDIO_FORMAT.matches( sourceStream.getFormat() ) &&
			( IMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS.matches( targetFormat ) ||
			  IMBEAudioFormat.PCM_SIGNED_48KHZ_16BITS.matches( targetFormat ) ) )
		{
			return new IMBEConverterAudioInputStream( sourceStream, targetFormat );
		}
		
		return null;
	}
	
	private static boolean compare( Encoding encoding1, Encoding encoding2 )
	{
		return encoding1.toString() != null &&
			   encoding2.toString() != null &&
			   encoding1.toString().contentEquals( encoding2.toString() );
	}
}
