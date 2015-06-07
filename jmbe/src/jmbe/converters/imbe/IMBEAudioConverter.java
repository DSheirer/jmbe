package jmbe.converters.imbe;

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

import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;

import jmbe.audio.JMBEAudioFormat;
import jmbe.iface.AudioConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IMBEAudioConverter implements AudioConverter
{
	private final static Logger mLog = 
			LoggerFactory.getLogger( IMBEAudioConverter.class );
	
	public static final int IMBE_FRAME_LENGTH = 18;
	
	public static final String CODEC_NAME = "IMBE";
	
	private IMBESynthesizer mSynthesizer = new IMBESynthesizer( true );
	
	public IMBEAudioConverter()
	{
	}
	
	public void dispose()
	{
	}
	
	public byte[] convert( byte[] frameData )
	{
		IMBEFrame frame = new IMBEFrame( frameData );
		
		ByteBuffer converted = mSynthesizer.getAudio( frame );
		
		return converted.array();
	}

	/**
	 * CODEC Name
	 */
	@Override
	public String getCodecName()
	{
		return CODEC_NAME;
	}

	@Override
	public AudioFormat getConvertedAudioFormat()
	{
		return JMBEAudioFormat.PCM_SIGNED_48KHZ_16BITS;
	}
}
