package jmbe.audio;

import javax.sound.sampled.AudioFormat;

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

public class JMBEAudioFormat
{
	public static final AudioFormat.Encoding IMBE_ENCODING = 
				new AudioFormat.Encoding( "IMBE" );

	public static final boolean LITTLE_ENDIAN = false;
	public static final boolean BIG_ENDIAN = true;

	public static final float IMBE_FRAME_RATE = 50;
	public static final float IMBE_SAMPLE_RATE = 50;
	public static final float PCM_8KHZ_RATE = 8000;
	public static final float PCM_48KHZ_RATE = 48000;

	public static final int IMBE_FRAME_SIZE_BYTES = 18;
	public static final int IMBE_SAMPLE_SIZE_BITS = 144;
	public static final int ONE_CHANNEL = 1;
	public static final int PCM_SAMPLE_SIZE_BITS = 16;
	public static final int PCM_FRAME_SIZE_BYTES = 2;
	
	public static AudioFormat IMBE_AUDIO_FORMAT = 
				new AudioFormat( IMBE_ENCODING, 
								 IMBE_SAMPLE_RATE,
								 IMBE_SAMPLE_SIZE_BITS,
								 ONE_CHANNEL, 
								 IMBE_FRAME_SIZE_BYTES, 
								 IMBE_FRAME_RATE,
								 LITTLE_ENDIAN );

	public static AudioFormat PCM_SIGNED_8KHZ_16BITS =
						new AudioFormat( AudioFormat.Encoding.PCM_SIGNED, 
								 PCM_8KHZ_RATE,
								 PCM_SAMPLE_SIZE_BITS,
								 ONE_CHANNEL, 
								 PCM_FRAME_SIZE_BYTES, 
								 PCM_8KHZ_RATE,
								 LITTLE_ENDIAN );
	
	public static AudioFormat PCM_SIGNED_48KHZ_16BITS =
			new AudioFormat( AudioFormat.Encoding.PCM_SIGNED, 
					 PCM_48KHZ_RATE,
					 PCM_SAMPLE_SIZE_BITS,
					 ONE_CHANNEL, 
					 PCM_FRAME_SIZE_BYTES, 
					 PCM_48KHZ_RATE,
					 LITTLE_ENDIAN );
}

