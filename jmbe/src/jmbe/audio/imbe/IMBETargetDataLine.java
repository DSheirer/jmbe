package jmbe.audio.imbe;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.Control.Type;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

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


public class IMBETargetDataLine implements TargetDataLine
{
	private final static Logger mLog = 
			LoggerFactory.getLogger( IMBETargetDataLine.class );

	/* Buffer sized for 5 seconds x 50 frames */
	public static final int BUFFER_FRAMES_SIZE = 250;
	
	private ArrayBlockingQueue<byte[]> mBuffer = 
						new ArrayBlockingQueue<byte[]>( BUFFER_FRAMES_SIZE );

	private CopyOnWriteArrayList<LineListener> mListeners = 
			new CopyOnWriteArrayList<LineListener>();
	
	private byte[] mCurrentFrame;
	private int mCurrentFramePointer = 0;

	private long mFrameCounter;
	
	private AtomicBoolean mOpen = new AtomicBoolean();
	private AtomicBoolean mStarted = new AtomicBoolean();
	private AtomicBoolean mRunning = new AtomicBoolean();
	
	/**
	 * IMBE target dataline provides a java audio system compatible interface to 
	 * ingest and buffer raw IMBE audio frames and expose a standard audio
	 * system interface for follow-on audio processors to consume the output audio 
	 * stream provided by this interface.
	 */
	public IMBETargetDataLine()
	{
		mLog.debug( "Constructing instance of TDL" );
	}

	@Override
	public void open( AudioFormat format, int bufferSize )
			throws LineUnavailableException
	{
		if( format == IMBEAudioFormat.IMBE_AUDIO_FORMAT )
		{
			if( mOpen.compareAndSet( false, true ) )
			{
				mBuffer = new ArrayBlockingQueue<byte[]>( bufferSize );
			}
			else
			{
				throw new LineUnavailableException( "Line is already open" );
			}
			mLog.debug( "We have now set the state - open:" + mOpen.get() );
		}
		else
		{
			throw new LineUnavailableException( "Unsupported format" );
 		}
	}
	
	@Override
	public void open() throws LineUnavailableException
	{
		mLog.debug( "open() invoked" );
		
		open( IMBEAudioFormat.IMBE_AUDIO_FORMAT, BUFFER_FRAMES_SIZE );
	}

	@Override
	public void close()
	{
		mLog.debug( "Closing the IMBE target data line" );
		mOpen.set( false );
		mRunning.set( false );
		
		if( mStarted.compareAndSet( true, false ) )
		{
			broadcast( new LineEvent( this, LineEvent.Type.STOP, 
					mCurrentFramePointer ) );
		}

		mBuffer.clear();
		
		mCurrentFrame = null;
	}

	@Override
	public boolean isOpen()
	{
		return mOpen.get();
	}

	@Override
	public void open( AudioFormat format ) throws LineUnavailableException
	{
		open( format, BUFFER_FRAMES_SIZE );
	}

	@Override
	public int read( byte[] buffer, int offset, int length )
	{
		mLog.debug( "IMBE TDL - read() invoked" );
		
		int transferred = 0;

		if( !mOpen.get() )
		{
			mLog.debug( "IMBE TDL - NOT open - returning 0" );
			return 0;
		}
		
		while( mRunning.get() && transferred < length )
		{
			int available = getBytesAvailable();

			/* The quantity of bytes available is less than requested */
			if( available > 0 && available <= ( length - transferred ) )
			{
				System.arraycopy( mCurrentFrame, mCurrentFramePointer, 
				buffer, offset + transferred, mCurrentFrame.length - mCurrentFramePointer );

				transferred += available;
				
				getFrame();
			}
			/* The quantity of bytes available is more than requested */
			else if( available > 0 )
			{
				int toTransfer = available - ( length - transferred );
				
				System.arraycopy( mCurrentFrame, mCurrentFramePointer, 
				buffer, offset + transferred, toTransfer );

				transferred += toTransfer;
				
				mCurrentFramePointer += toTransfer;
			}
			/* There is no data available -- getFrame() will fetch more data
			 * or switch mStarted to false and broadcast a stop event */
			else
			{
				getFrame();
			}
		}
		
		return transferred;
	}
	
	private int getBytesAvailable()
	{
		if( mCurrentFrame != null )
		{
			return mCurrentFrame.length - mCurrentFramePointer;
		}
		
		return 0;
	}

	/**
	 * Fetches a new frame from the buffer.  If no more frames are available,
	 * broadcasts a stop line event
	 */
	private void getFrame()
	{
		mCurrentFrame = mBuffer.poll();

		if( mCurrentFrame == null )
		{
			if( mStarted.compareAndSet( true, false ) )
			{
				broadcast( new LineEvent( this, LineEvent.Type.STOP, 
						mCurrentFramePointer ) );
			}
		}
		else
		{
			mFrameCounter++;
		}
		
		mCurrentFramePointer = 0;
	}

	/**
	 * Primary inject point for submitting IMBE frame data into the audio
	 * system via this data line
	 */
	public void receive( byte[] data )
	{
//		mLog.debug( "receive()ing data - State - open: " + mOpen.get() + " running:" + mRunning.get() + " started:" + mStarted.get() );
		/* The line must be open before we'll accept data */
		if( mOpen.get() )
		{
			mBuffer.offer( data );

			/* If we're running and not yet started, broadcast a start event */
			if( mRunning.get() && mStarted.compareAndSet( false, true ) )
			{
				broadcast( new LineEvent( this, LineEvent.Type.START, 
						mCurrentFramePointer ) );
			}
		}
		
		mLog.debug( "TDL - data received - buffer size is now: " + mBuffer.size() );
	}

	/**
	 * Not implemented
	 */
	@Override
	public void drain()
	{
	}

	/**
	 * Flushes (deletes) any buffered audio frames.  If a frame is currently 
	 * being processed it will continue and a stop event will be broadcast 
	 * after the current frame has been dispatched.
	 */
	@Override
	public void flush()
	{
		mBuffer.clear();
	}

	/**
	 * If the line is open, sets the running state to true and attempts to 
	 * retrieve a frame from the buffer.  If successful, a line start event is
	 * broadcast.  If no frames are currently buffered, then a line start event
	 * will be broadcast once the first frame is received.
	 * 
	 * If the line is closed, or if the running state is already set to true,
	 * then the start() is ignored.
	 */
	@Override
	public void start()
	{
		mLog.debug( "start() invoked" );
		
		if( mOpen.get() && mRunning.compareAndSet( false, true ) )
		{
			getFrame();
		}
	}

	/**
	 * Stops this data line, sets running and active states to false and purges
	 * any currently buffered data frames.  Note: the line will remain open 
	 * until close() is invoked and incoming frame data will continue to buffer.
	 */
	@Override
	public void stop()
	{
		if( mOpen.get() && mStarted.compareAndSet( true, false ) )
		{
			mRunning.set( false );
			mBuffer.clear();
			mCurrentFrame = null;
		}
	}

	/**
	 * Indicates if this line us currently opened, but does not reflect if the
	 * line is currently streaming data.  Use the isActive() method to determine
	 * streaming status.
	 */
	@Override
	public boolean isRunning()
	{
		return mRunning.get();
	}

	/**
	 * Indicates if this line is currently streaming data
	 */
	@Override
	public boolean isActive()
	{
		return mStarted.get();
	}

	/**
	 * IMBE AudioFormat produced by this target data line.
	 */
	@Override
	public AudioFormat getFormat()
	{
		return IMBEAudioFormat.IMBE_AUDIO_FORMAT;
	}

	/**
	 * Size of the internal buffer in bytes.  This method will not return the 
	 * true size of the buffer if this data line was constructed with a buffer
	 * size other than the default (250 frames) buffer size.
	 */
	@Override
	public int getBufferSize()
	{
		return BUFFER_FRAMES_SIZE * IMBEAudioFormat.IMBE_AUDIO_FORMAT.getFrameSize();
	}

	/**
	 * Number of bytes of imbe frame data available.  Value will be the number
	 * of buffered frames times 18 bytes.
	 */
	@Override
	public int available()
	{
		return mBuffer.size() * IMBEAudioFormat.IMBE_AUDIO_FORMAT.getFrameSize();
	}

	/**
	 * Current number of frames provided by this data line since it was opened.
	 * Counter rolls over once it exceeds the max integer value.
	 */
	@Override
	public int getFramePosition()
	{
		return (int)getLongFramePosition();
	}

	/**
	 * Current number of frames provided by this data line since it was opened
	 */
	@Override
	public long getLongFramePosition()
	{
		return mFrameCounter;
	}

	/**
	 * Number of microseconds worth of data that has been sourced by this
	 * target data line.  Returns the frame counter times 20,000 microseconds.
	 */
	@Override
	public long getMicrosecondPosition()
	{
		return mFrameCounter * 20000;
	}

	/**
	 * Not implemented
	 */
	@Override
	public float getLevel()
	{
		return AudioSystem.NOT_SPECIFIED;
	}

	@Override
	public javax.sound.sampled.Line.Info getLineInfo()
	{
		return new DataLine.Info( IMBETargetDataLine.class, 
								  IMBEAudioFormat.IMBE_AUDIO_FORMAT );
	}

	/**
	 * No controls are implemented for this data line
	 */
	@Override
	public Control[] getControls()
	{
		return null;
	}

	/**
	 * No controls are implemented for this data line
	 */
	@Override
	public boolean isControlSupported( Type control )
	{
		return false;
	}

	/**
	 * No controls are implemented for this data line
	 */
	@Override
	public Control getControl( Type control )
	{
		return null;
	}
	
	/**
	 * Broadcasts a line event (e.g. start or stop) to any registered listeners
	 */
	private void broadcast( LineEvent event )
	{
		for( LineListener listener: mListeners )
		{
			listener.update( event );
		}
	}

	/**
	 * Adds the listener to receive start and stop line events 
	 */
	@Override
	public void addLineListener( LineListener listener )
	{
		mListeners.add( listener );
	}

	/**
	 * Removes the listener from receiving start and stop line events 
	 */
	@Override
	public void removeLineListener( LineListener listener )
	{
		mListeners.remove( listener );
	}
}
