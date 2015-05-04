package jmbe.audio.imbe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Random;

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

public class IMBESynthesizer
{
	private final static Logger mLog = 
			LoggerFactory.getLogger( IMBESynthesizer.class );

	public static final double TWO_PI = Math.PI * 2.0;
	public static final double TWOPI_OVER_256 = 2.0 * Math.PI / 256.0;
	public static final double SCALE_256 = 1.0 / 256.0;

	/* Algorithm 121 - create scaling coefficient (yw) from synthesis window (ws) 
	 * and the initial pitch refinement window (wr) 
	 * 
	 *   sum_wr(n) = 110.01987200000003
	 *   sum_wr(n)squared = 80.683623293024
	 *   sum_ws(n)squared = 143.33999999999997 */
	public static final double UNVOICED_SCALING_COEFFICIENT = 146.64327084433555;

	/* Synthesis window - represents the first 50 and last 50 scaling 
	 * coefficients (inverse) with 156 middle coefficients of unity (1.0) */
	public static final double[] SYNTHESIS_WINDOW = new double[] 
	{
		0.00,0.02,0.04,0.06,0.08,0.10,0.12,0.14,0.16,0.18,
		0.20,0.22,0.24,0.26,0.28,0.30,0.32,0.34,0.36,0.38,
		0.40,0.42,0.44,0.46,0.48,0.50,0.52,0.54,0.56,0.58,
		0.60,0.62,0.64,0.66,0.68,0.70,0.72,0.74,0.76,0.78,
		0.80,0.82,0.84,0.86,0.88,0.90,0.92,0.94,0.96,0.98
	};
	
	private WhiteNoiseGenerator mWhiteNoise = new WhiteNoiseGenerator();
	
	private RandomTwoPiGenerator mRandom2PI = new RandomTwoPiGenerator();

	private IMBEFrame mPreviousFrame = IMBEFrame.getDefault();

	private double[] mPreviousPhaseO = new double[ 57 ];
	private double[] mPreviousPhaseV = new double[ 57 ];
	private double[] mPreviousUw = new double[ 256 ];
	
	/**
	 * Synthesizes 8 kHz 16-bit audio from IMBE audio frames
	 */
	public IMBESynthesizer()
	{
	}

	/**
	 * Synthesizes 20 milliseconds of audio from the imbe frame parameters in 
	 * the following format:
	 * 
	 * Sample Rate: 8 kHz
	 * Sample Size: 16-bits
	 *  Frame Size: 160 samples
	 *  Bit Format: Little Endian
	 *  
	 *  @return ByteBuffer containing the audio sample bytes
	 */
	public ByteBuffer getAudio( IMBEFrame frame )
	{
		/* Little-endian byte buffer with room for 160 x 2-byte short samples */
		ByteBuffer buffer = ByteBuffer.allocate( 320 )
					.order( ByteOrder.LITTLE_ENDIAN );

		ShortBuffer shortBuffer = buffer.asShortBuffer();
		
		double[] unvoiced = getUnvoiced( frame );
		
		double[] voiced = getVoiced( frame );
		
		/* Algorithm #142 - combine voiced and unvoiced audio samples to form
		 * the completed audio samples. */
		for( int x = 0; x < 160; x++ )
		{
			shortBuffer.put( (short)( voiced[ x ] + unvoiced[ x ] ) );
		}

		mPreviousFrame = frame;
		
		return buffer;
	}

	/**
	 * Generates the unvoiced component of the audio signal using a white noise
	 * generator where the frequency components corresponding to the voiced
	 * harmonics are removed from the white noise.
	 * 
	 * @param frame - source IMBE frame
	 * @return - 160 samples of unvoiced audio component
	 */
	public double[] getUnvoiced( IMBEFrame frame )
	{
		double[] samples = mWhiteNoise.getWindowedSamples();

		/* Algorithm #122 and #123 - generate the 256 FFT bins to L frequency 
		 * band mapping from the fundamental frequency */
		int[] fftBinLBandMap = frame.getModelParameters()
						.getFundamentalFrequency().getFFTBinToLBandMap();
		
		boolean[] voicedBands = frame.getModelParameters().getVoicingDecisions();

		double[] amplitudes = frame.getModelParameters()
				.getEnhancedSpectralAmplitudes();

		int[] a_min = frame.getModelParameters()
				.getFundamentalFrequency().getLBandFFTBinMinimums();

		int[] b_max = frame.getModelParameters()
				.getFundamentalFrequency().getLBandFFTBinMaximums();


		/* Algorithm #118 - perform 256-point DFT against samples.
		 * Algorithm #119 and #124 - zeroize the voiced frequency bins and
		 * all of the lowest and highest frequency bins -- we do this by not
		 * calculating the DFT for the corresponding frequency bins that match
		 * these high/low and voiced l bands. */
		double dftUw[] = new double[ 256 ];
		
		for( int y = 0; y < 256; y++ )
		{
			/* Only calculate if this is an unvoiced band DFT frequency bin */
			
			int band = fftBinLBandMap[ y ];
			
			if( band != 0 && !voicedBands[ band ] )
			{
				double m2PiOver256 = TWOPI_OVER_256 * (double)( y - 128 );
				
				for( int x = 0; x < 209; x++ )
				{
					double n = (double)( x - 104 );
					
					dftUw[ y ] += ( ( samples[ x ] * Math.cos( m2PiOver256 * (double)n ) ) -
								    ( samples[ x ] * Math.sin( m2PiOver256 * (double)n ) ) );
				}
			}
		}
		
		double[] Uw = new double[ 256 ];

		for( int bin = 0; bin < 256; bin++ )
		{
			int band = fftBinLBandMap[ bin ];

			/* Algorithm #120 - scale the unvoiced white noise frequency bin */
			if( band != 0 && !voicedBands[ band ] )
			{
				double numerator = 0.0;

				for( int y = a_min[ band ]; y < b_max[ band ]; y++ )
				{
					int index = ( y < 128 ) ? 128 - y : y + 128;
					double value = dftUw[ index ];
					numerator += ( value * value );
				}

				double denominator = (double)( b_max[ band ] - a_min[ band ] );

				
				double scaling_denominator = Math.pow( ( numerator / denominator ), 0.5 );

				
				Uw[ bin ] = ( UNVOICED_SCALING_COEFFICIENT * amplitudes[ band ] * 
						dftUw[ bin ] ) / scaling_denominator;
			}
		}
		
		/* Algorithm #125 - inverse DFT of scaled unvoiced and zeroized voiced 
		 * dft frequency bins from the white noise */
		double[] uw = new double[ 256 ];

		for( int x = 0; x < 256; x++ )
		{
			double n2PiOver256 = TWOPI_OVER_256 * (double)( x - 128 );
			
			for( int y = 0; y < 256; y++ )
			{
				double m = (double)( y - 128 );
				
				uw[ x ] += ( ( Uw[ y ] * Math.cos( n2PiOver256 * (double)m ) ) +
						     ( Uw[ y ] * Math.sin( n2PiOver256 * (double)m ) ) );
			}
			
			uw[ x ] *= SCALE_256;
		}
		
		/* Algorithm #126 - use Weighted Overlap Add algorithm to combine previous 
		 * Uw and the current Uw inverse DFT results to form final unvoiced set */
		double[] unvoiced = new double[ 160 ];

		for( int n = 0; n < 160; n++ )
		{
			double win = synthesisWindow( n );
			double winInverse = synthesisWindow( n - 160 );
			
			unvoiced[ n ] = ( ( win * translateUw( n, mPreviousUw ) ) +
				  ( winInverse * translateUw( n - 160, uw ) ) ) /
				  ( ( win * win ) + ( winInverse * winInverse ) );
		}
		
		mPreviousUw = uw;

		return unvoiced;
	}

	/**
	 * Translates the specified index in the range -160 to 160 to the actual
	 * zero based index of the uw inverse dft samples array.
	 */
	private double translateUw( int index, double[] uw )
	{
		if( index < -128 || index > 127 )
		{
			return 0.0;
		}
		
		return uw[ index + 128 ];
	}

	/**
	 * Resizes the voicing decisions array, as needed, padding the newly 
	 * added indices with a 0-false value 
	 */
	private boolean[] resize( boolean[] voicingDecisions, int size )
	{
		if( voicingDecisions.length != size )
		{
			boolean[] resized = new boolean[ size ];
			
			System.arraycopy( voicingDecisions, 0, resized, 0, voicingDecisions.length );
			
			return resized;
		}
		
		return voicingDecisions;
	}
	
	/**
	 * Reconstructs the voiced audio components using the model parameters from 
	 * both the current and previous imbe frames.
	 * 
	 * @param frame - source IMBE frame
	 * @return - 160 samples of voiced audio component
	 */
	public double[] getVoiced( IMBEFrame currentFrame )
	{
		int maxL = Math.max( currentFrame.getModelParameters().getL(), 
				 mPreviousFrame.getModelParameters().getL() );

		boolean[] currentVoicing = resize( currentFrame.getModelParameters()
				.getVoicingDecisions(), maxL + 1 );
		
		boolean[] previousVoicing = resize( mPreviousFrame.getModelParameters()
				.getVoicingDecisions(), maxL + 1 );
		
		double currentFrequency = currentFrame.getModelParameters()
				.getFundamentalFrequency().getFrequency();
		
		double previousFrequency = mPreviousFrame.getModelParameters()
				.getFundamentalFrequency().getFrequency();

		/* Algorithm #139 - calculate current phase angle for each harmonic */
		double[] currentPhaseV = new double[ 57 ];
		double[] currentPhaseO = new double[ 57 ];
		
		int threshold = (int)Math.floor( (double)currentFrame
					.getModelParameters().getL() / 4.0d );
		
		/* number of unvoiced spectral amplitudes (Luv) in current frame */
		int unvoicedSpectralAmplitudes = 0;
		
		for( int x = 1; x < currentVoicing.length; x++ )
		{
			if( !currentVoicing[ x ] )
			{
				unvoicedSpectralAmplitudes++;
			}
		}
		
		for( int l = 1; l <= 56; l++ )
		{
			/* Algorithm #139 - calculate current phase v values */
			currentPhaseV[ l ] = mPreviousPhaseV[ l ] + 
				( ( previousFrequency + currentFrequency ) * ( (double)l * 80.0 ) );
			
			/* Algorithm #140 - calculate current phase o values */
			if( l <= threshold )
			{
				currentPhaseO[ l ] = currentPhaseV[ l ];
			}
			else if( l <= maxL )
			{
				/* Algorithm #141 - replaced with internal pi random generator */
						
				currentPhaseO[ l ] = currentPhaseV[ l ] +
					( ( (double)unvoicedSpectralAmplitudes * mRandom2PI.next() ) / 
						(double)currentFrame.getModelParameters().getL() );	
			}
		}
		
		double[] currentAmplitudes = currentFrame.getModelParameters()
				.getEnhancedSpectralAmplitudes();
		
		double[] previousAmplitudes = mPreviousFrame.getModelParameters()
				.getEnhancedSpectralAmplitudes();
		
		double[] voiced = new double[ 160 ];
		
		/*
		 * Algorithm #127 - reconstruct 160 voice samples using each of the l 
		 * harmonics that are common between this frame and the previous frame, 
		 * using one of four algorithms selected by the combination of the 
		 * voicing decisions of the current and previous frames for each
		 * harmonic.
		 */
		for( int n = 0; n < 160; n++ )
		{
			for( int l = 1; l <= maxL; l++ )
			{
				if( currentVoicing[ l ] && previousVoicing[ l ] )
				{
					if( l >= 8 || Math.abs( currentFrequency - previousFrequency ) >= 
							( 0.1 * currentFrequency ) )
					{
						/* Algorithm #133 */
						voiced[ n ] += 2.0 * ( synthesisWindow( n ) * 
							previousAmplitudes[ l ] * Math.cos( ( previousFrequency * 
								(double)n * (double)l ) + mPreviousPhaseO[ l ] ) +
							( synthesisWindow( n - 160 ) * currentAmplitudes[ l ] * 
								Math.cos( ( currentFrequency * ( (double)n - 160.0 ) * 
								(double)l ) + currentPhaseO[ l ] ) ) );
					}
					else
					{
						/* Algorithm #135 - amplitude function */
						double amplitude = previousAmplitudes[ l ] + 
							( ( (double)n / 160.0 ) * ( currentAmplitudes[ l ] - 
										previousAmplitudes[ l ] ) );
								
						/* Algorithm #137 */
						double ol = ( currentPhaseO[ l ] - mPreviousPhaseO[ l ] - 
							( ( previousFrequency + currentFrequency ) * 
								( (double)l * 80.0 ) ) );

						/* Algorithm #138 */
						double wl = ( ol - ( TWO_PI * Math.floor( 
							( ol + Math.PI ) / TWO_PI ) ) ) / 160.0d;
						
						/* Algorithm #136 - phase function */
						double phase = mPreviousPhaseO[ l ] + 
							( ( ( previousFrequency * (double)l ) + wl ) * (double)n ) +
							( ( currentFrequency - previousFrequency ) * 
							( ( (double)l * (double)n * (double)n ) / 320.0 ) );	

						/* Algorithm #134 */
						voiced[ n ] += 2.0 * ( amplitude * Math.cos( phase ) );
					}
				}
				else if( !currentVoicing[ l ] && previousVoicing[ l ] )
				{
					/* Algorithm #131 */
					voiced[ n ] += 2.0 * ( synthesisWindow( n ) * 
						previousAmplitudes[ l ] * Math.cos( 
						( previousFrequency * (double)n * (double)l ) + 
							mPreviousPhaseO[ l ] ) );
				}
				else if( currentVoicing[ l ] && !previousVoicing[ l ] )
				{
					/* Algorithm #132 */
					voiced[ n ] += 2.0 * ( synthesisWindow( n - 160 ) *
					currentAmplitudes[ l ] * Math.cos( ( currentFrequency * 
					( (double)n - 160.0 ) * (double)l ) + currentPhaseO[ l ] ) );
				}

				/* Algorithm #130 - harmonics that are unvoiced in both the 
				 * current and previous frames contribute nothing */
			}
		}
		
		mPreviousPhaseV = currentPhaseV;
		mPreviousPhaseO = currentPhaseO;
		
		return voiced;
	}

	/**
	 * Returns the speech synthesis window coefficient for indices in the
	 * range of -160 to 160 using a portion of the window from appendix I
	 */
	public static double synthesisWindow( int n )
	{
		assert( -160 <= n && n <= 160 );
		
		if( n < -105 || n > 105 )
		{
			return 0.0;
		}
		else if( -55 <= n && n <= 55 )
		{
			return 1.0;
		}
		else if( n < 0 ) //-105 to -56
		{
			return SYNTHESIS_WINDOW[ n + 105 ];
		}
		else //56 to 105
		{
			return SYNTHESIS_WINDOW[ 105 - n ];
		}
	}
	
	public static double getUnvoicedScalingCoefficient()
	{
		double sum_wr = 110.01987200000003;
		double sum_wr_squared = 80.683623293024;
		double sum_ws_squared = 0.0;
		
		for( int x = -104; x < 105; x++ )
		{
			sum_ws_squared += ( synthesisWindow( x ) * synthesisWindow( x ) );
		}
		
		double yw = sum_wr * Math.pow( ( sum_ws_squared / sum_wr_squared ) , 0.5 );

		return yw;
	}
	
	/* Algorithm #117 - white noise generator */
	public class WhiteNoiseGenerator
	{
		private int mSample = 3147;
		
		private double[] mCurrentBuffer;

		public WhiteNoiseGenerator()
		{
			mCurrentBuffer = new double[ 209 ];
			
			for( int x = 0; x < 209; x++ )
			{
				mCurrentBuffer[ x ] = nextSample();
			}
		}

		/**
		 * Generates the next white noise sample
		 */
		private int nextSample()
		{
			mSample = 171 * mSample + 11213 -
				( 53125 * (int)( ( 171 * mSample + 11213 ) / 53125 ) );
			
			return mSample;
		}
		
		/**
		 * Generates a 256 element array with indices 0 - 208 filled with 209
		 * windowed samples of white noise, using the synthesis window in 
		 * Annex I.  On each method invocation, the internal buffer is shifted
		 * by 160 samples and 160 new samples are generated..
		 * 
		 * Note: the array is sized to 256 elements to support subsequent
		 * 256-point DFT.
		 * 
		 * @return - 256 element array containing 209 windowed white noise samples
		 */
		public double[] getWindowedSamples()
		{
			double[] nextBuffer = new double[ 209 ];
			
			/* Copy the contents of the current buffer */
			System.arraycopy( mCurrentBuffer, 0, nextBuffer, 0, 209 );
			
			/* Use the final 49 samples to form the beginning of the next 160 
			 * sample frame plus 49 samples of the current frame for windowing.
			 * Copy the 49 samples to the beginning of current array to prepare 
			 * for the next method call */
			System.arraycopy( nextBuffer, 160, mCurrentBuffer, 0, 49 );
			
			/* Generate new samples to prepare for the next call */
			for( int x = 49; x < 209; x++ )
			{
				mCurrentBuffer[ x ] = nextSample();
			}

			/* Apply the window to both ends of the next buffer */
			for( int x = 0; x < SYNTHESIS_WINDOW.length; x++ )
			{
				nextBuffer[ x ] *= SYNTHESIS_WINDOW[ x ];
				nextBuffer[ 208 - x ] *= SYNTHESIS_WINDOW[ x ];
			}
			
			return nextBuffer;
		}
	}
	
	/**
	 * Generates random numbers in the range -PI <> PI
	 */
	public class RandomTwoPiGenerator
	{
		private Random mRandom = new Random();

		public double next()
		{
			/* generate random number between 0 and 1 */
			double random = mRandom.nextDouble();

			/* scale it to the range of 2 PI */
			random *= TWO_PI;
			
			/* shift it down to the range of -pi to pi */
			random -= Math.PI;
			
			return random;
		}
	}
	
	public static void main( String[] args ) 
	{
		double ws = IMBESynthesizer.getUnvoicedScalingCoefficient();
		
		mLog.debug( "ws = " + ws );
	}
}
