package jmbe.audio.imbe;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

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

public class IMBEConverterAudioInputStream extends AudioInputStream
{
	private final static Logger mLog = LoggerFactory
			.getLogger( IMBEConverterAudioInputStream.class );

	private AudioInputStream mSourceStream;
	private IMBESynthesizer mSynthesizer = new IMBESynthesizer();
	private IMBEFrame mPreviousFrame = IMBEFrame.getDefault();
	private ByteBuffer mBuffer;

	/**
	 * Package private constructor.  Use the IMBEAudioConverter class to get
	 * an instance of this converter.
	 * 
	 * @param stream - source stream providing IMBE frame byte data
	 * @param format - IMBE.  See IMBEAudioFormat class.
	 * @param length - use AudioSystem.NOT_SPECIFIED.
	 */
	IMBEConverterAudioInputStream( AudioInputStream stream )
	{
		super( stream, 
			   IMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS, 
			   AudioSystem.NOT_SPECIFIED );

		mSourceStream = stream;
	}
	
	private void getFrame()
	{
		byte[] frame = new byte[ 18 ];
		
		try
		{
			int read = mSourceStream.read( frame, 0, 18 );
			
			if( read == 18 )
			{
				IMBEFrame imbe = new IMBEFrame( frame, mPreviousFrame );
				
				mPreviousFrame = imbe;
				
				mBuffer = mSynthesizer.getAudio( imbe );
			}
			else
			{
				mBuffer = null;
			}
		}
		catch( Exception e )
		{
			mLog.error( "exception while reading from the source stream", e );
			mBuffer = null;
		}
	}

	@Override
	public AudioFormat getFormat()
	{
		return IMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS;
	}

	@Override
	public long getFrameLength()
	{
		return IMBEAudioFormat.PCM_SIGNED_8KHZ_16BITS.getFrameSize();
	}

	@Override
	public int read() throws IOException
	{
		throw new IOException( "This method is unsupported.  Audio samples are "
			+ "two bytes in length and this method is designed to return a "
			+ "single byte" );
	}

	@Override
	public int read( byte[] b ) throws IOException
	{
		if( mBuffer == null || mBuffer.remaining() == 0 )
		{
			getFrame();
		}

		int transferred = 0;
		
		while( mBuffer != null )
		{
			int available = mBuffer.remaining();

			if( available >= b.length - transferred )
			{
				mBuffer.get( b, transferred, b.length - transferred );

				return b.length;
			}
			else
			{
				mBuffer.get( b, transferred, available );

				transferred += available;
				
				getFrame();
			}
		}
		
		return transferred;
	}

	@Override
	public int read( byte[] b, int off, int length ) throws IOException
	{
		if( mBuffer == null || mBuffer.remaining() == 0 )
		{
			getFrame();
		}

		int transferred = 0;
		
		while( mBuffer != null )
		{
			int available = mBuffer.remaining();

			if( available >= length - transferred )
			{
				mBuffer.get( b, off + transferred, length - transferred );

				return length;
			}
			else
			{
				mBuffer.get( b, off + transferred, available );

				transferred += available;
				
				getFrame();
			}
		}
		
		return transferred;
	}

	@Override
	public long skip( long n ) throws IOException
	{
		if( mBuffer == null || mBuffer.remaining() == 0 )
		{
			getFrame();
		}

		int skipped = 0;
		
		while( mBuffer != null )
		{
			int available = mBuffer.remaining();
			
			if( available >= n - skipped )
			{
				mBuffer.position( mBuffer.position() + (int)( n - skipped ) );
				
				return n;
			}
			else
			{
				skipped += available;
				
				getFrame();
			}
		}
		
		return skipped;
	}

	@Override
	public void close() throws IOException
	{
		mSourceStream.close();
		
		super.close();
	}

	@Override
	public void reset() throws IOException
	{
		mPreviousFrame = IMBEFrame.getDefault();
		
		getFrame();
	}

	@Override
	public boolean markSupported()
	{
		return false;
	}
	
	@Override
	public void mark( int readlimit )
	{
		throw new IllegalArgumentException( "Mark is not supported by this"
				+ " converting audio input stream" );
	}
}
