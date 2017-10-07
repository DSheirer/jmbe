package jmbe.converters.imbe;

import jmbe.binary.BinaryFrame;
import jmbe.edac.Golay23;
import jmbe.edac.Hamming15;

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

public class IMBEFrame
{
	private final static Logger mLog = 
			LoggerFactory.getLogger( IMBEFrame.class );

	public static final double LOG_2 = Math.log( 2.0 );
	
	public static final double TWOPI_UNDER_256 = 256.0 / ( 2.0 * Math.PI );
	public static final double PI_OVER_6 = Math.PI / 6.0;

	public static final int[] RANDOMIZER_SEED = { 0,1,2,3,4,5,6,7,8,9,10,11 };

	public static final int[] VECTOR_B0 = { 0,1,2,3,4,5,141,142 };
	public static final int[] VECTOR_B1 = { 92,93,94,95,96,97 };
	public static final int[] VECTOR_B2 = { 6,7,8,98,99,140 };
	
	public static final double[] B2_GAIN_LEVELS = new double[]
	{ -2.842205, -2.694235, -2.558260, -2.382850, -2.221042, -2.095574, 
	  -1.980845, -1.836058, -1.645556, -1.417658, -1.261301, -1.125631, 
	  -0.958207, -0.781591, -0.555837, -0.346976, -0.147249, -0.027755, 
	   0.211495, 0.388380, 0.552873, 0.737223, 0.932197, 1.139032, 
	   1.320955, 1.483433, 1.648297, 1.801447, 1.942731, 2.118613, 
	   2.321486, 2.504443, 2.653909, 2.780654, 2.925355, 3.076390, 
	   3.220825, 3.402869, 3.585096, 3.784606, 3.955521, 4.155636, 
	   4.314009, 4.444150, 4.577542, 4.735552, 4.909493, 5.085264, 
	   5.254767, 5.411894, 5.568094, 5.738523, 5.919215, 6.087701, 
	   6.280685, 6.464201, 6.647736, 6.834672, 7.022583, 7.211777, 
	   7.471016, 7.738948, 8.124863, 8.695827
	};

	/**
	 * Algorithm #55 - prediction coefficients (p) for all values of L, 1-56.  
	 * The first index (0) is unused.
	 */
	public static final double[] PREDICTION_COEFFICIENT =
	{ 0.0,0.40,0.40,0.40,0.40,0.40,0.40,0.40,0.40,0.40,0.40,
		  0.40,0.40,0.40,0.40,0.40,0.43,0.46,0.49,0.52,0.55,
          0.58,0.61,0.64,0.67,0.70,0.70,0.70,0.70,0.70,0.70,
          0.70,0.70,0.70,0.70,0.70,0.70,0.70,0.70,0.70,0.70,
          0.70,0.70,0.70,0.70,0.70,0.70,0.70,0.70,0.70,0.70,
          0.70,0.70,0.70,0.70,0.70,0.70 
     };

	/**
	 * Message frame bit index of the voiced/unvoiced decision for all values 
	 * of L harmonics. On encoding, a voicing decision is made for each of the 
	 * K frequency bands and recorded in the b1 information vector.
	 * On decoding, each of the L harmonics are flagged as voiced or unvoiced 
	 * according to the harmonic's location within each K frequency band.
	 */
	public static final int[] VOICE_DECISION_INDEX = new int[] 
	{ 
		0, //UNUSED
		92,92,92, //L1-L3, K=1
		93,93,93, //L4-L6, K=2
		94,94,94, //L7-L9, K=3
		95,95,95, //L10-L12, K=4
		96,96,96, //L13-L15, K=5
		97,97,97, //L16-L18, K=6
		98,98,98, //L19-L21, K=7
		99,99,99, //L22-L24, K=8
		100,100,100, //L25-L27, K=9
		101,101,101, //L28-L30, K=10
		102,102,102, //L31-L33, K=11
		107,107,107,107,107,107,107,107,107,107,107,107,
		107,107,107,107,107,107,107,107,107,107,107 //L34 - L56, K=12
	};
	
	/**
	 * Equation #68 and #71 - quantized coefficient value offset.  The quantized 
	 * value of each coefficient is an unsigned value.  In order to normalize 
	 * the quantized value about the zero axis, we add a negative offset.  Each 
	 * offset is calculated from -2 ^ ( Bm - 1 ) + 0.5, where Bm is the number
	 * of bits in the quantized value.
	 * 
	 * Contains offsets for bit lengths of zero through ten.
	 */
	public static final double[] QUANTIZER_OFFSET = new double[]
	{ 0.0, -0.5, -1.5, -3.5, -7.5, -15.5, -31.5, -63.5, -127.5, -255.5, -511.5 };
	

	private IMBEModelParameters mModelParameters;
	private BinaryFrame mFrame;
	private int mErrorCountCoset0;
	private int mErrorCountTotal;

	/**
	 * Constructs an IMBE frame from a binary message containing an 18-byte or
	 * 144-bit message frame, and a previous IMBE frame.  Performs error detection
	 * and correction.
	 * 
	 * After construction, use the setPrevious() method to the previous imbe
	 * frame so that model parameters can be generated.
	 * 
	 * Use the getModelParameters() method to access the parameters for speech
	 * synthesis.
	 * 
	 * Use the .getDefault() method to generate the first (default) IMBE frame
	 * to use at the start of a sequence.
	 */
	public IMBEFrame( byte[] data )
	{
		mFrame = BinaryFrame.fromBytes( data );

		mModelParameters = new IMBEModelParameters();
		
		IMBEInterleave.deinterleave( mFrame );

		mErrorCountCoset0 = Golay23.checkAndCorrect( mFrame, 0 );
		
		derandomize();

		mErrorCountTotal = mErrorCountCoset0 + detectAndCorrectErrors();
		
		mModelParameters.setFundamentalFrequency( FundamentalFrequency
				.fromValue( mFrame.getInt( VECTOR_B0 ) ) );
	}
	
	public void dispose()
	{
		mFrame = null;
		mModelParameters = null;
	}
	

	/**
	 * Extracts parameters from the previous frame.  After executing this
	 * method, the previous frame can be disposed.
	 */
	public void setPreviousFrameParameters( IMBEFrame previous )
	{
		mModelParameters.setErrors( previous.getModelParameters().getErrorRate(), 
				mErrorCountCoset0, mErrorCountTotal );

		/* If we have too many errors and/or the fundamental frequency is invalid 
		 * perform a repeat by copying the model parameters from previous frame  */
		if( mModelParameters.repeatRequired() )
		{
			mModelParameters.copy( previous.getModelParameters() );
		}
		else
		{
			int L = mModelParameters.getL();
			
			mModelParameters.setVoicingDecisions( getVoicingDecisions( mFrame, L ) );
			
			calculateSpectralAmplitudes( previous );
			
			enhanceSpectralAmplitudes( previous.getModelParameters()
					.getLocalEnergy() );
			
			adaptiveSmoothing( previous.getModelParameters()
					.getAmplitudeThreshold() );
		}
	}

	/**
	 * Empty protected constructor to use with getDefault() methods
	 */
	protected IMBEFrame( FundamentalFrequency fundamental )
	{
		mModelParameters = new IMBEModelParameters( fundamental );
	}
	
	/**
	 * Constructs a default IMBE frame with a default set of model parameters.
	 * 
	 * Use this method to construct a default instance of the imbe frame to use
	 * as the first frame in a sequence.  Use this frame to construct the first
	 * real instance of the imbe frame.
	 */
	public static IMBEFrame getDefault()
	{
		return new IMBEFrame( FundamentalFrequency.W_DEFAULT );
	}
	
	/**
	 * Model parameters calculated for this frame.  
	 */
	public IMBEModelParameters getModelParameters()
	{
		return mModelParameters;
	}
	
	/**
	 * Raw binary message source for this frame
	 */
	public BinaryFrame getFrame()
	{
		return mFrame;
	}
	
	private void enhanceSpectralAmplitudes( double previousSE )
	{
		int lplus1 = mModelParameters.getL() + 1;
		double w0 = mModelParameters.getFundamentalFrequency().getFrequency();
		
		double[] amplitudes = mModelParameters.getSpectralAmplitudes();

		/* Algorithm #105 and #106 - calculate RM0 and RM1 from amplitudes */
		double RM0 = 0.0;
		double RM1 = 0.0;
		
		for( int l = 1; l < lplus1; l++ )
		{
			double amplitudesSquared = amplitudes[ l ] * amplitudes[ l ];
			
			RM0 += amplitudesSquared;
			RM1 += ( amplitudesSquared * Math.cos( w0 * (double)l ) );
		}

		double[] W = new double[ lplus1 ];

		double rm0squared = RM0 * RM0;
		double rm1squared = RM1 * RM1;
		double RM0plusRM1squared = rm0squared + rm1squared;
		double twoRM0RM1 = 2.0 * RM0 * RM1;

		double scale = ( Math.PI * 0.96d ) / 
				( w0 * RM0 * ( rm0squared - rm1squared ) );

		/* Algorithm #107 - calculate enhancement weights (W) */
		for( int l = 1; l < lplus1; l++ )
		{
			W[ l ] = Math.sqrt( amplitudes[ l ] ) * Math.pow( ( scale * 
			( RM0plusRM1squared - ( twoRM0RM1 * Math.cos( w0 * (double)l ) ) ) ), 0.25 );
		}

		double[] enhancedAmplitudes = new double[ lplus1 ];
		
		int L = mModelParameters.getL();

		/* Algorithm #108 - apply weights to produce enhanced amplitudes */
		for( int l = 1; l < lplus1; l++ )
		{
			if( 8 * l <= L )
			{
				enhancedAmplitudes[ l ] = amplitudes[ l ];
			}
			else if( W[ l ] > 1.2d )
			{
				enhancedAmplitudes[ l ] = amplitudes[ l ] * 1.2d;
			}
			else if( W[ l ] < 0.5d )
			{
				enhancedAmplitudes[ l ] = amplitudes[ l ] * 0.5d;
			}
			else
			{
				enhancedAmplitudes[ l ] = amplitudes[ l ] * W[ l ];
			}
		}
		
		/* Algorithm #109 - remove energy differential of enhanced amplitudes */
		double denominator = 0.0;
		
		for( int l = 1; l < lplus1; l++ )
		{
			denominator += ( enhancedAmplitudes[ l ] * enhancedAmplitudes[ l ] );
		}

		double scaleFactor = Math.pow( ( RM0 / denominator ), 0.5 );
		
		/* Algorithm #110 - scale enhanced amplitudes to remove energy differential */
		for( int l = 1; l < lplus1; l++ )
		{
			enhancedAmplitudes[ l ] = enhancedAmplitudes[ l ] * scaleFactor;
		}

		mModelParameters.setEnhancedSpectralAmplitudes( enhancedAmplitudes );

		/* Algorithm #111 - calculate local energy */
		double localEnergy = ( 0.95 * previousSE ) + ( 0.05 * RM0 );
		
		if( localEnergy >= 10000.0 )
		{
			mModelParameters.setLocalEnergy( localEnergy );
		}
		else
		{
			mModelParameters.setLocalEnergy( 10000.0d );
		}
	}

	/**
	 * Performs adaptive smoothing on enhanced spectral amplitudes and the
	 * voice/no-voice decisions
	 */
	private void adaptiveSmoothing( int previousTM )
	{
		double VM;

		/* Algorithm #112 - calculate adaptive threshold */
		if( mModelParameters.getErrorRate() <= 0.005 && 
			mModelParameters.getErrorCountTotal() <= 4 )
		{
			VM = Double.MAX_VALUE;
		}
		else
		{
			double energy = Math.pow( mModelParameters.getLocalEnergy(), 0.375 );

			if( mModelParameters.getErrorRate() <= 0.0125 &&
			mModelParameters.getErrorCountTotal() == 0 )
			{
				VM = ( 45.255 * energy ) / 
						Math.exp( 277.26 * mModelParameters.getErrorRate() );
			}
			else
			{
				VM = 1.414 * energy;
			}
		}

		double amplitudeMeasure = 0.0;
		
		for( int l = 1; l <= mModelParameters.getL(); l++ )
		{
			/* Algorithm #113 - apply adaptive threshold to voice/no voice decisions */
			mModelParameters.getVoicingDecisions()[ l ] = 
				( ( mModelParameters.getEnhancedSpectralAmplitudes()[ l ] > VM ) ? 
					true : mModelParameters.getVoicingDecisions()[ l ] ); 
			
			/* Algorithm #114 - calculate amplitude measure */
			amplitudeMeasure += mModelParameters.getEnhancedSpectralAmplitudes()[ l ];
		}

		/* Algorithm #115 - calculate amplitude threshold */
		if( mModelParameters.getErrorRate() <= 0.005 && 
			mModelParameters.getErrorCountTotal() <= 6 )
		{
			mModelParameters.setAmplitudeThreshold( 20480 );
		}
		else
		{
			mModelParameters.setAmplitudeThreshold( 
					(int)( 6000 - ( 300 * mModelParameters.getErrorCountTotal() )
							+ previousTM ) );
		}

		/* Algorithm #116 - scale enhanced spectral amplitudes if amplitude
		 * measure is greater than amplitude threshold */
		if( mModelParameters.getAmplitudeThreshold() <= amplitudeMeasure )
		{
			double scale = (double)mModelParameters.getAmplitudeThreshold() / 
					amplitudeMeasure;
			
			for( int l = 1; l < mModelParameters.getL() + 1; l++ )
			{
				mModelParameters.getEnhancedSpectralAmplitudes()[ l ] *= scale;
			}
		}
	}
	
	/**
	 * Performs error detection and correction against coset words 1 - 6. 
	 * 
	 * @return - error count total from coset words 1 - 6
	 */
	private int detectAndCorrectErrors()
	{
		int errors = 0;
		
		errors += Golay23.checkAndCorrect( mFrame, 23 );
		errors += Golay23.checkAndCorrect( mFrame, 46 );
		errors += Golay23.checkAndCorrect( mFrame, 69 );

		int E4 = Hamming15.checkAndCorrect( mFrame, 92 );
		mModelParameters.setErrorCountCoset4( E4 );
		errors += E4;
		
		errors += Hamming15.checkAndCorrect( mFrame, 107 );
		errors += Hamming15.checkAndCorrect( mFrame, 122 );
		
		return errors;
	}
	
	/**
	 * Removes randomizer by generating a pseudo-random noise sequence from the 
	 * first 12 bits of coset word c0 and applies (xor) that sequence against 
	 * message coset words c1 through c6.
	 * 
	 * @param seedBits - first 12 bit indexes of coset word c0
	 */
	private void derandomize()
	{
		/* Set the offset to the first seed bit plus 23 to point to coset c1 */
		int offset = 23;
		
		/* Get seed value from first 12 bits of coset c0 */
		int seed = mFrame.getInt( RANDOMIZER_SEED );


		/* Left shift 4 places (multiply by 16) */
		seed <<= 4;
		
		for( int x = 0; x < 114; x++ )
		{
			seed = ( ( 173 * seed ) + 13849 ) & 65535; //Trim back to 16-bits
			
			/* If seed bit 15 is set, toggle the corresponding message bit */
			if( ( seed & 32768 ) == 32768 )
			{
				mFrame.flip( x + offset );
			}
		}
	}

	/**
	 * Supports Algorithm #75 and #77 assumptions - resize the previous frame's 
	 * spectral amplitudes to match the current frame's L size.
	 * 
	 * Resizes elements array to ensure a minimum length of nextL + 1.  When 
	 * increasing the length, the highest index element value is copied to any 
	 * newly added indices.  
	 * 
	 * Index 0 element is set to 1.0.
	 * 
	 * @param elements - current set of elements with an overall length of 
	 * L + 1.
	 * 
	 * @param nextL - requested new size of L.  returned array will be nextL + 1
	 * elements in length.
	 * 
	 * @return properly (re)sized array
	 */
	public static double[] resize( double[] elements, int nextL )
	{
		if( nextL > elements.length - 1 )
		{
			double[] resized = new double[ nextL + 1 ];

			/* Copy all but index 0 */
			System.arraycopy( elements, 1, resized, 1, elements.length - 1 );

			/* Copy the highest index value to the newly added indexes */
			double highest = elements[ elements.length - 1 ];

			/* Algorithm #79 - set all new indexes to previous highest index */
			for( int x = elements.length; x < resized.length; x++ )
			{
				resized[ x ] = highest;
			}

			/* Algorithm #78 - set previous index 0 to 1.0 */
			resized[ 0 ] = 1.0;
			
			return resized;
		}
		else
		{
			/* Set index 0 to 1.0 */
			elements[ 0 ] = 1.0;
			
			return elements;
		}
	}

	/**
	 * Algorithms 75, 76, 77, 78, and 79 - calculate the current frame's 
	 * log2M spectral amplitudes using the current frame's spectral amplitude 
	 * residual values (T) and the previous frame's log2M spectral amplitudes
	 * with appropriate scaling to account for differences in L between the 
	 * two frames.
	 * 
	 * @param previousFrame - previous imbe audio frame
	 */
	public void calculateSpectralAmplitudes( IMBEFrame previousFrame )
	{
		int Lplus1 = mModelParameters.getL() + 1;

		double[] log2MSpectralAmplitudes = new double[ Lplus1 ]; 
		double[] spectralAmplitudes = new double[ Lplus1 ]; 

		int previousL = previousFrame.getModelParameters().getL();

		/* Get previous frame's log2M entries and resize them to 1 greater than
		 * the max of the current L, or the previous L.  Set index 0 to 1.0 and
		 * any newly expanded indexes to the value of the previously highest
		 * numbered index */
		double[] previousLog2M = resize( previousFrame.getModelParameters()
			.getLog2SpectralAmplitudes(), 
				Math.max( mModelParameters.getL(), previousL ) + 1 );
		
		/* Current frame spectral amplitude prediction residuals (T) */
		double[] T = mModelParameters.getFundamentalFrequency().getHarmonic()
				.getSpectralAmplitudePredictionResiduals( mFrame );
		
		double scale = (double)previousL / (double)mModelParameters.getL();

		double[] kl = new double[ Lplus1 ];
		int[] kl_floor = new int[ Lplus1 ];
		double[] sl = new double[ Lplus1 ];
		double sum = 0.0;

		for( int l = 1; l < Lplus1; l++ )
		{
			/* Algorithm #75 - calculate kl */
			kl[ l ] = (double)l * scale;
			
			kl_floor[ l ] = (int)Math.floor( kl[ l ] );

			/* Algorithm #76 - calculate sl */
			sl[ l ] = kl[ l ] - (double)kl_floor[ l ];

			/* Algorithm #77 partial - summation */
			sum += ( ( 1.0d - sl[ l ] ) * previousLog2M[ kl_floor[ l ] ] ) +
				            ( sl[ l ] * previousLog2M[ kl_floor[ l ] + 1 ] );
		}

		/* Algorithm #77 - log2M spectral amplitudes of current frame */
		for( int l = 1; l < Lplus1; l++ )
		{
			double p = PREDICTION_COEFFICIENT[ l ];

			log2MSpectralAmplitudes[ l ] = T[ l ]
				+ ( p * ( 1.0 - sl[ l ] ) * previousLog2M[ kl_floor[ l ] ] )
				+ ( p * sl[ l ] * previousLog2M[ kl_floor[ l ] + 1 ] ) 
			    - ( ( p / (double)mModelParameters.getL() ) * sum );
			
			spectralAmplitudes[ l ] = Math.pow( 2.0, log2MSpectralAmplitudes[ l ] );
		}
		
		mModelParameters.setSpectralAmplitudes( spectralAmplitudes );
		mModelParameters.setLog2SpectralAmplitudes( log2MSpectralAmplitudes );
	}

	/**
	 * Calculates the log base 2 of the value.
	 * 
	 * log2(x) = log( x ) / log ( 2 );
	 */
	public static double log2( double value )
	{
		return Math.log( value ) / LOG_2;
	}
	
	/**
	 * Returns the voiced (true) / unvoiced (false) status for each of the
	 * L harmonics.  Each of the 'l' harmonics is voiced if the K frequency
	 * band to which it belongs is flagged as voiced.  The K frequency band
	 * voiced/unvoiced flags are contained in each of the bit vector b1
	 * bits of the imbe frame, which are variable length depending on the 
	 * value of K. 
	 * 
	 * @return boolean array containing L voicing decisions in array indexes
	 * 1 through L, with array index 0 unused.
	 */
	public static boolean[] getVoicingDecisions( BinaryFrame frame, int L )
	{
		boolean[] decisions = new boolean[ L + 1 ];

		for( int x = 1; x <= L; x++ )
		{
			decisions[ x ] = frame.get( VOICE_DECISION_INDEX[ x ] );
		}
		
		return decisions;
	}
	
	/**
	 * Fundamental frequency enumeration used for decoding the value of the
	 * information vector b0 using the formulas detailed in section 6.1 of the 
	 * vocoder specification.
	 * 
	 * Enumeration entries are constrained to the range 0 to 207.  Values 208 to 
	 * 255 are reserved for future use.
	 * 
	 * Fundamental Frequency w0 = 4.0 * Pi / ( index + 39.5 )
	 * L = floor( .9254 * floor( ( Pi / w0 ) + 0.25 )
	 */
	
	public enum FundamentalFrequency
	{
		W0( Harmonic.L09, 0.31813596492048540000 ),
		W1( Harmonic.L09, 0.31028075591010300000 ),
		W2( Harmonic.L09, 0.30280411118937767000 ),
		W3( Harmonic.L09, 0.29567930857315700000 ),
		W4( Harmonic.L10, 0.28888208308871660000 ),
		W5( Harmonic.L10, 0.28239035088447580000 ),
		W6( Harmonic.L10, 0.27618396954635543000 ),
		W7( Harmonic.L10, 0.27024452934105747000 ),
		W8( Harmonic.L11, 0.26455517082861420000 ),
		W9( Harmonic.L11, 0.25910042503833347000 ),
		W10( Harmonic.L11, 0.2538660730173570300 ),
		W11( Harmonic.L11, 0.2488390220665182600 ),
		W12( Harmonic.L12, 0.2440071963953237300 ),
		W13( Harmonic.L12, 0.2393594402735080600 ),
		W14( Harmonic.L12, 0.2348854320440966800 ),
		W15( Harmonic.L12, 0.2305756076029206000 ),
		W16( Harmonic.L12, 0.2264210921506157300 ),
		W17( Harmonic.L12, 0.2224136391921977500 ),
		W18( Harmonic.L12, 0.2185455759018986600 ),
		W19( Harmonic.L12, 0.2148097540916098000 ),
		W20( Harmonic.L13, 0.2111995061236835700 ),
		W21( Harmonic.L13, 0.2077086051960193900 ),
		W22( Harmonic.L13, 0.2043312295017751500 ),
		W23( Harmonic.L13, 0.2010619298297467700 ),
		W24( Harmonic.L14, 0.1978956002261287000 ),
		W25( Harmonic.L14, 0.1948274513854135200 ),
		W26( Harmonic.L14, 0.1918529864787660000 ),
		W27( Harmonic.L14, 0.1889679791632958300 ),
		W28( Harmonic.L15, 0.1861684535460618200 ),
		W29( Harmonic.L15, 0.1834506659030536200 ),
		W30( Harmonic.L15, 0.1808110879763909800 ),
		W31( Harmonic.L15, 0.1782463916930379000 ),
		W32( Harmonic.L16, 0.1757534351658625600 ),
		W33( Harmonic.L16, 0.1733292498532299800 ),
		W34( Harmonic.L16, 0.1709710287667914600 ),
		W35( Harmonic.L16, 0.1686761156289822000 ),
		W36( Harmonic.L17, 0.1664419948921744800 ),
		W37( Harmonic.L17, 0.1642662825406427700 ),
		W38( Harmonic.L17, 0.1621467176046344800 ),
		W39( Harmonic.L17, 0.1600811543230468000 ),
		W40( Harmonic.L18, 0.1580675548975996600 ),
		W41( Harmonic.L18, 0.1561039827870704700 ),
		W42( Harmonic.L18, 0.1541885964952045800 ),
		W43( Harmonic.L18, 0.1523196438104142000 ),
		W44( Harmonic.L19, 0.1504954564593912700 ),
		W45( Harmonic.L19, 0.1487144451403452400 ),
		W46( Harmonic.L19, 0.1469750949047856400 ),
		W47( Harmonic.L19, 0.1452759608596436200 ),
		W48( Harmonic.L20, 0.1436156641641048200 ),
		W49( Harmonic.L20, 0.1419928882978437600 ),
		W50( Harmonic.L20, 0.1404063755794320900 ),
		W51( Harmonic.L20, 0.1388549239155709700 ),
		W52( Harmonic.L21, 0.1373373837634882200 ),
		W53( Harmonic.L21, 0.1358526552903694400 ),
		W54( Harmonic.L21, 0.1343996857150713500 ),
		W55( Harmonic.L21, 0.1329774668186155800 ),
		W56( Harmonic.L22, 0.1315850326110908000 ),
		W57( Harmonic.L22, 0.1302214571436183800 ),
		W58( Harmonic.L22, 0.1288858524549658700 ),
		W59( Harmonic.L22, 0.1275773666432403200 ),
		W60( Harmonic.L23, 0.1262951820538610300 ),
		W61( Harmonic.L23, 0.1250385135757131600 ),
		W62( Harmonic.L23, 0.1238066070380214000 ),
		W63( Harmonic.L23, 0.1225987377010651000 ),
		W64( Harmonic.L24, 0.1214142088343881400 ),
		W65( Harmonic.L24, 0.1202523503766428000 ),
		W66( Harmonic.L24, 0.1191125176716509300 ),
		W67( Harmonic.L24, 0.1179940902756729800 ),
		W68( Harmonic.L24, 0.1168964708312481100 ),
		W69( Harmonic.L24, 0.1158190840033103400 ),
		W70( Harmonic.L24, 0.1147613754735997600 ),
		W71( Harmonic.L24, 0.1137228109896757700 ),
		W72( Harmonic.L25, 0.1127028754651046800 ),
		W73( Harmonic.L25, 0.1117010721276371000 ),
		W74( Harmonic.L25, 0.1107169217124156200 ),
		W75( Harmonic.L25, 0.1097499616974600300 ),
		W76( Harmonic.L26, 0.1087997455788672900 ),
		W77( Harmonic.L26, 0.1078658421833405400 ),
		W78( Harmonic.L26, 0.1069478350158227500 ),
		W79( Harmonic.L26, 0.1060453216401618000 ),
		W80( Harmonic.L27, 0.1051579130908717300 ),
		W81( Harmonic.L27, 0.1042852333141840000 ),
		W82( Harmonic.L27, 0.1034269186367010000 ),
		W83( Harmonic.L27, 0.1025826172600748800 ),
		W84( Harmonic.L28, 0.1017519887802362100 ),
		W85( Harmonic.L28, 0.1009347037297925500 ),
		W86( Harmonic.L28, 0.1001304431423041700 ),
		W87( Harmonic.L28, 0.0993388981372266600 ),
		W88( Harmonic.L29, 0.0985597695243856700 ),
		W89( Harmonic.L29, 0.0977927674269196300 ),
		W90( Harmonic.L29, 0.0970376109216924500 ),
		W91( Harmonic.L29, 0.0962940276962388800 ),
		W92( Harmonic.L30, 0.0955617537213625200 ),
		W93( Harmonic.L30, 0.0948405329385597900 ),
		W94( Harmonic.L30, 0.0941301169614919300 ),
		W95( Harmonic.L30, 0.0934302647907745100 ),
		W96( Harmonic.L31, 0.0927407425413961100 ),
		W97( Harmonic.L31, 0.0920613231821184700 ),
		W98( Harmonic.L31, 0.0913917862862485300 ),
		W99( Harmonic.L31, 0.0907319177932070200 ),
		W100( Harmonic.L32, 0.090081509780352500 ),
		W101( Harmonic.L32, 0.089440360244549270 ),
		W102( Harmonic.L32, 0.088808272892997680 ),
		W103( Harmonic.L32, 0.088185056942871390 ),
		W104( Harmonic.L33, 0.087570526929332220 ),
		W105( Harmonic.L33, 0.086964502521516760 ),
		W106( Harmonic.L33, 0.086366808346111160 ),
		W107( Harmonic.L33, 0.085777273818151350 ),
		W108( Harmonic.L34, 0.085195732978706250 ),
		W109( Harmonic.L34, 0.084622024339119000 ),
		W110( Harmonic.L34, 0.084055990731499480 ),
		W111( Harmonic.L34, 0.083497479165177230 ),
		W112( Harmonic.L35, 0.082946340688839420 ),
		W113( Harmonic.L35, 0.082402430258092930 ),
		W114( Harmonic.L35, 0.081865606608203080 ),
		W115( Harmonic.L35, 0.081335732131774590 ),
		W116( Harmonic.L36, 0.080812672761152240 ),
		W117( Harmonic.L36, 0.080296297855330170 ),
		W118( Harmonic.L36, 0.079786480091169340 ),
		W119( Harmonic.L36, 0.079283095358732940 ),
		W120( Harmonic.L37, 0.078786022660559080 ),
		W121( Harmonic.L37, 0.078295144014698890 ),
		W122( Harmonic.L37, 0.077810344361357100 ),
		W123( Harmonic.L37, 0.077331511472979520 ),
		W124( Harmonic.L37, 0.076858535867640200 ),
		W125( Harmonic.L37, 0.076391310725587680 ),
		W126( Harmonic.L37, 0.075929731808816750 ),
		W127( Harmonic.L37, 0.075473697383538570 ),
		W128( Harmonic.L38, 0.075023108145427900 ),
		W129( Harmonic.L38, 0.074577867147532190 ),
		W130( Harmonic.L38, 0.074137879730732580 ),
		W131( Harmonic.L38, 0.073703053456652040 ),
		W132( Harmonic.L39, 0.073273298042910620 ),
		W133( Harmonic.L39, 0.072848525300632880 ),
		W134( Harmonic.L39, 0.072428649074116270 ),
		W135( Harmonic.L39, 0.072013585182574060 ),
		W136( Harmonic.L40, 0.071603251363869930 ),
		W137( Harmonic.L40, 0.071197567220165280 ),
		W138( Harmonic.L40, 0.070796454165403780 ),
		W139( Harmonic.L40, 0.070399835374561200 ),
		W140( Harmonic.L41, 0.070007635734591480 ),
		W141( Harmonic.L41, 0.069619781797003730 ),
		W142( Harmonic.L41, 0.069236201732006470 ),
		W143( Harmonic.L41, 0.068856825284159840 ),
		W144( Harmonic.L42, 0.068481583729477780 ),
		W145( Harmonic.L42, 0.068110409833925050 ),
		W146( Harmonic.L42, 0.067743237813257000 ),
		W147( Harmonic.L42, 0.067380003294151060 ),
		W148( Harmonic.L43, 0.067020643276582250 ),
		W149( Harmonic.L43, 0.066665096097396140 ),
		W150( Harmonic.L43, 0.066313301395035200 ),
		W151( Harmonic.L43, 0.065965200075376230 ),
		W152( Harmonic.L44, 0.065620734278637970 ),
		W153( Harmonic.L44, 0.065279847347320380 ),
		W154( Harmonic.L44, 0.064942483795137840 ),
		W155( Harmonic.L44, 0.064608589276910920 ),
		W156( Harmonic.L45, 0.064278110559381960 ),
		W157( Harmonic.L45, 0.063950995492921990 ),
		W158( Harmonic.L45, 0.063627192984097080 ),
		W159( Harmonic.L45, 0.063306652969063850 ),
		W160( Harmonic.L46, 0.062989326387765280 ),
		W161( Harmonic.L46, 0.062675165158898620 ),
		W162( Harmonic.L46, 0.062364122155628650 ),
		W163( Harmonic.L46, 0.062056151182020604 ),
		W164( Harmonic.L47, 0.061751206950167926 ),
		W165( Harmonic.L47, 0.061449245057991060 ),
		W166( Harmonic.L47, 0.061150221967684534 ),
		W167( Harmonic.L47, 0.060854094984790184 ),
		W168( Harmonic.L48, 0.060560822237875530 ),
		W169( Harmonic.L48, 0.060270362658796990 ),
		W170( Harmonic.L48, 0.059982675963528270 ),
		W171( Harmonic.L48, 0.059697722633535260 ),
		W172( Harmonic.L49, 0.059415463897679300 ),
		W173( Harmonic.L49, 0.059135861714631400 ),
		W174( Harmonic.L49, 0.058858878755780670 ),
		W175( Harmonic.L49, 0.058584478388620850 ),
		W176( Harmonic.L49, 0.058312624660599410 ),
		W177( Harmonic.L49, 0.058043282283414190 ),
		W178( Harmonic.L49, 0.057776416617743320 ),
		W179( Harmonic.L49, 0.057511993658394385 ),
		W180( Harmonic.L50, 0.057249980019859560 ),
		W181( Harmonic.L50, 0.056990342922263820 ),
		W182( Harmonic.L50, 0.056733050177693780 ),
		W183( Harmonic.L50, 0.056478070176895157 ),
		W184( Harmonic.L51, 0.056225371876327396 ),
		W185( Harmonic.L51, 0.055974924785564240 ),
		W186( Harmonic.L51, 0.055726698955029590 ),
		W187( Harmonic.L51, 0.055480664964058160 ),
		W188( Harmonic.L52, 0.055236793909271090 ),
		W189( Harmonic.L52, 0.054995057393256774 ),
		W190( Harmonic.L52, 0.054755427513547596 ),
		W191( Harmonic.L52, 0.054517876851883610 ),
		W192( Harmonic.L53, 0.054282378463754530 ),
		W193( Harmonic.L53, 0.054048905868211500 ),
		W194( Harmonic.L53, 0.053817433037940780 ),
		W195( Harmonic.L53, 0.053587934389591356 ),
		W196( Harmonic.L54, 0.053360384774348930 ),
		W197( Harmonic.L54, 0.053134759468749140 ),
		W198( Harmonic.L54, 0.052911034165722834 ),
		W199( Harmonic.L54, 0.052689184965866550 ),
		W200( Harmonic.L55, 0.052469188368931830 ),
		W201( Harmonic.L55, 0.052251021265526706 ),
		W202( Harmonic.L55, 0.052034660929023490 ),
		W203( Harmonic.L55, 0.051820085007666690 ),
		W204( Harmonic.L56, 0.051607271516875450 ),
		W205( Harmonic.L56, 0.051396198831734860 ),
		W206( Harmonic.L56, 0.051186845679670766 ),
		W207( Harmonic.L56, 0.050979191133302930 ),
		W_DEFAULT( Harmonic.L30, 0.0937765407097 ),
		
		W_INVALID( Harmonic.SILENCE, 0.0 );
		
		private Harmonic mHarmonics;
		private double mFrequency;

		private FundamentalFrequency( Harmonic harmonics, double frequency )
		{
			mHarmonics = harmonics;
			mFrequency = frequency;
		}
		
		public int getL()
		{
			return mHarmonics.getL();
		}
		
		public Harmonic getHarmonic()
		{
			return mHarmonics;
		}
		
		public double getFrequency()
		{
			return mFrequency;
		}

		/**
		 * Produces a map of 256 FFT bins and their mapping to each of the L 
		 * bands.  This is aligned with the output of the JTransforms dft 
		 * calculation where:
		 * 
		 * 0, 0, 1, 1, ...
		 */
		public int[] getFFTBinToLBandMap()
		{
			int[] bins = new int[ 256 ];

			int[] a = getLBandFFTBinMinimums();
			int[] b = getLBandFFTBinMaximums();
			
			for( int l = 1; l <= getL(); l++ )
			{
				for( int x = a[ l ]; x < b[ l ]; x++ )
				{
					int index = 2 * x;
					bins[ index ] = l;
					bins[ index + 1 ] = l;
				}
			}
			
			return bins;
		}

		/**
		 * Algorithm #122 - calculate the minimum FFT bin for each frequency band
		 */
		public int[] getLBandFFTBinMinimums()
		{
			int[] a = new int[ getL() + 1 ];
			
			for( int l = 1; l <= getL(); l++ )
			{
				a[ l ] = (int)Math.ceil( TWOPI_UNDER_256 * 
						( (double)l - 0.5 ) * mFrequency );
			}
			
			return a;
		}
		
		/**
		 * Algorithm #123 - calculate the maximum FFT bin for each frequency band
		 */
		public int[] getLBandFFTBinMaximums()
		{
			int[] b = new int[ getL() + 1 ];
			
			for( int l = 1; l <= getL(); l++ )
			{
				b[ l ] = (int)Math.ceil( TWOPI_UNDER_256 * 
						( (double)l + 0.5 ) * mFrequency );
			}
			
			return b;
		}

		public static FundamentalFrequency fromValue( int value )
		{
			if( 0 <= value && value <= 207 )
			{
				return FundamentalFrequency.values()[ value ];
			}
			
			return FundamentalFrequency.W_INVALID;
		}
	}
	
	/**
	 * Defines the harmonics (L) associated with the fundamental frequency.
	 * The harmonics are evenly distributed across 6 J blocks.  This enumeration
	 * contains the message indexes to extract the value for each harmonic and
	 * the step size multiplier for each harmonic, used to reconstruct the 
	 * spectral amplitude values resulting from the DCT applied against each of
	 * the 6 J blocks.  
	 * 
	 * Each enumeration entry specifies:
	 * 
	 * 	a) number of voiced or unvoiced K frequency bands
	 *  b) J-block harmonic (L) allocations
	 *  c) Harmonic coefficient quantized value bit indices for coefficients b3 - L+1 
	 *  i) Harmonic coefficient step sizes for b3 - L+1
	 *  
	 *  Note: the b2 message bit indices are contained in the VECTOR_B2 constant.
	 *  
	 *  Index arrays are defined in Annex F and G.  See util.BitAllocation
	 *  class for setting up the indexes.
	 *  
	 *  Step sizes are defined in Annex F and also Tables 3 and 4 using the
	 *  indexes from Annex G.  See util.Gains class for setting up higher order
	 *  dct step sizes (b8 - L+1).
	 */
	public enum Harmonic
	{
		L09( Bands.K03, new int[][] { { 2 }, { 3 }, { 4 }, { 5,8 }, { 6,9 }, { 7,10 } }, 
				new int[][] { { 9,10,27,34,53,72,80,108,116,128 }, { 11,28,46,54,73,97,109,117,129 }, { 23,29,47,55,74,98,110,122,130 }, { 24,30,48,56,75,99,111,123,131 }, { 25,31,49,57,76,100,112,124,132 }, { 26,32,50,69,77,101,113,125,137 }, { 33,51,70,78,102,114,126,138 }, { 52,71,79,107,115,127,139 } }, 
				new double[] { 0.003100, 0.004020, 0.003360, 0.002900, 0.002640, 0.006140, 0.012280, 0.024560 } ),
		L10( Bands.K04, new int[][] { { 2 }, { 3 }, { 4,8 }, { 5,9 }, { 6,10 }, { 7,11 } }, 
				new int[][] { { 9,23,29,47,55,75,101,114,127 }, { 10,24,30,48,56,76,102,115,128 }, { 25,31,49,57,77,107,116,129 }, { 26,32,50,69,78,108,117,130 }, { 27,33,51,70,79,109,122,131 }, { 11,28,34,52,71,80,110,123,132 }, { 46,53,72,98,111,124,137 }, { 54,73,99,112,125,138 }, { 74,100,113,126,139 } }, 
				new double[] { 0.006200, 0.004020, 0.006720, 0.005800, 0.005280, 0.006140, 0.024560, 0.046050, 0.085960 } ),
		L11( Bands.K04, new int[][] { { 2 }, { 3,8 }, { 4,9 }, { 5,10 }, { 6,11 }, { 7,12 } }, 
				new int[][] { { 10,25,32,51,71,98,112,126 }, { 11,26,33,52,72,99,113,127 }, { 23,27,34,53,73,100,114,128 }, { 28,46,54,74,101,115,129 }, { 29,47,55,75,102,116,130 }, { 9,24,30,48,56,76,107,117,131 }, { 31,49,57,77,108,122,132 }, { 50,69,78,109,123,137 }, { 70,79,110,124,138 }, { 80,111,125,139 } }, 
				new double[] { 0.012400, 0.008040, 0.006720, 0.011600, 0.010560, 0.006140, 0.024560, 0.046050, 0.085960, 0.122800 } ),
		L12( Bands.K04, new int[][] { { 2,8 }, { 3,9 }, { 4,10 }, { 5,11 }, { 6,12 }, { 7,13 } }, 
				new int[][] { { 9,11,29,48,57,78,110,125 }, { 23,30,49,69,79,111,126 }, { 24,31,50,70,80,112,127 }, { 25,32,51,71,98,113,128 }, { 26,33,52,72,99,114,129 }, { 10,27,34,53,73,100,115,130 }, { 28,46,54,74,101,116,131 }, { 47,55,75,102,117,132 }, { 56,76,107,122,137 }, { 77,108,123,138 }, { 109,124,139 } }, 
				new double[] { 0.012400, 0.016080, 0.013440, 0.011600, 0.010560, 0.012280, 0.024560, 0.046050, 0.085960, 0.122800, 0.199550 } ),
		L13( Bands.K05, new int[][] { { 2,8 }, { 3,9 }, { 4,10 }, { 5,11 }, { 6,12 }, { 7,13,14 } }, 
				new int[][] { { 9,25,33,53,74,108,124 }, { 10,26,34,54,75,109,125 }, { 11,27,46,55,76,110,126 }, { 28,47,56,77,111,127 }, { 29,48,57,78,112,128 }, { 23,30,49,69,79,113,129 }, { 24,31,50,70,80,114,130 }, { 32,51,71,99,115,131 }, { 52,72,100,116,132 }, { 73,101,117,137 }, { 102,122,138 }, { 107,123,139 } }, 
				new double[] { 0.024800, 0.016080, 0.013440, 0.021750, 0.019800, 0.024560, 0.024560, 0.046050, 0.085960, 0.122800, 0.199550, 0.156650 } ),
		L14( Bands.K05, new int[][] { { 2,8 }, { 3,9 }, { 4,10 }, { 5,11 }, { 6,12,13 }, { 7,14,15 } }, 
				new int[][] { { 9,23,30,49,71,102,123 }, { 24,31,50,72,107,124 }, { 25,32,51,73,108,125 }, { 26,33,52,74,109,126 }, { 27,34,53,75,110,127 }, { 10,28,46,54,76,111,128 }, { 11,29,47,55,77,112,129 }, { 48,56,78,113,130 }, { 57,79,114,131 }, { 69,80,115,132 }, { 99,116,137 }, { 70,100,117,138 }, { 101,122,139 } }, 
				new double[] { 0.024800, 0.030150, 0.025200, 0.021750, 0.019800, 0.024560, 0.024560, 0.085960, 0.122800, 0.122800, 0.156650, 0.122800, 0.156650 } ),
		L15( Bands.K05, new int[][] { { 2,8 }, { 3,9 }, { 4,10 }, { 5,11,12 }, { 6,13,14 }, { 7,15,16 } }, 
				new int[][] { { 9,11,28,47,57,100,122 }, { 23,29,48,69,101,123 }, { 24,30,49,70,102,124 }, { 25,31,50,71,107,125 }, { 32,51,72,108,126 }, { 26,33,52,73,109,127 }, { 10,27,34,53,74,110,128 }, { 46,54,75,111,129 }, { 55,76,112,130 }, { 56,77,113,131 }, { 78,114,132 }, { 79,115,137 }, { 80,116,138 }, { 99,117,139 } }, 
				new double[] { 0.024800, 0.030150, 0.025200, 0.021750, 0.036960, 0.046050, 0.024560, 0.085960, 0.122800, 0.096400, 0.199550, 0.156650, 0.199550, 0.156650 } ),
		L16( Bands.K06, new int[][] { { 2,8 }, { 3,9 }, { 4,10,11 }, { 5,12,13 }, { 6,14,15 }, { 7,16,17 } }, 
				new int[][] { { 9,25,33,54,79,117 }, { 10,26,34,55,80,122 }, { 11,27,46,56,100,123 }, { 28,47,57,101,124 }, { 29,48,69,102,125 }, { 23,30,49,70,107,126 }, { 24,31,50,71,108,127 }, { 32,51,72,109,128 }, { 52,73,110,129 }, { 53,74,111,130 }, { 75,112,131 }, { 76,113,132 }, { 77,114,137 }, { 78,115,138 }, { 116,139 } }, 
				new double[] { 0.046500, 0.030150, 0.025200, 0.040600, 0.036960, 0.046050, 0.046050, 0.085960, 0.096400, 0.122800, 0.156650, 0.199550, 0.156650, 0.199550, 0.204850 } ),
		L17( Bands.K06, new int[][] { { 2,8 }, { 3,9,10 }, { 4,11,12 }, { 5,13,14 }, { 6,15,16 }, { 7,17,18 } }, 
				new int[][] { { 9,11,30,52,77,116 }, { 10,23,31,53,78,117 }, { 24,32,54,79,122 }, { 25,33,55,80,123 }, { 26,34,56,100,124 }, { 27,46,57,101,125 }, { 28,47,69,102,126 }, { 29,48,70,107,127 }, { 49,71,108,128 }, { 50,72,109,129 }, { 51,73,110,130 }, { 74,111,131 }, { 75,112,132 }, { 113,137 }, { 76,114,138 }, { 115,139 } }, 
				new double[] { 0.046500, 0.030150, 0.047040, 0.040600, 0.036960, 0.085960, 0.085960, 0.067480, 0.122800, 0.096400, 0.122800, 0.156650, 0.199550, 0.204850, 0.199550, 0.204850 } ),
		L18( Bands.K06, new int[][] { { 2,8,9 }, { 3,10,11 }, { 4,12,13 }, { 5,14,15 }, { 6,16,17 }, { 7,18,19 } }, 
				new int[][] { { 9,10,29,50,75,115 }, { 11,30,51,76,116 }, { 23,31,52,77,117 }, { 24,32,53,78,122 }, { 25,33,54,79,123 }, { 26,34,55,80,124 }, { 46,56,100,125 }, { 27,47,57,101,126 }, { 28,48,69,102,127 }, { 49,70,107,128 }, { 71,108,129 }, { 72,109,130 }, { 73,110,131 }, { 74,111,132 }, { 112,137 }, { 113,138 }, { 114,139 } }, 
				new double[] { 0.046500, 0.056280, 0.047040, 0.040600, 0.036960, 0.085960, 0.096400, 0.085960, 0.067480, 0.122800, 0.156650, 0.199550, 0.156650, 0.199550, 0.204850, 0.260950, 0.204850 } ),
		L19( Bands.K07, new int[][] { { 2,8,9 }, { 3,10,11 }, { 4,12,13 }, { 5,14,15 }, { 6,16,17 }, { 7,18,19,20 } }, 
				new int[][] { { 9,10,26,47,73,114 }, { 11,27,48,74,115 }, { 23,28,49,75,116 }, { 29,50,76,117 }, { 30,51,77,122 }, { 24,31,52,78,123 }, { 32,53,79,124 }, { 25,33,54,80,125 }, { 34,55,101,126 }, { 46,56,102,127 }, { 57,107,128 }, { 69,108,129 }, { 70,109,130 }, { 71,110,131 }, { 111,132 }, { 72,112,137 }, { 113,138 }, { 139 } }, 
				new double[] { 0.046500, 0.056280, 0.047040, 0.058000, 0.052800, 0.085960, 0.096400, 0.085960, 0.096400, 0.122800, 0.156650, 0.199550, 0.156650, 0.199550, 0.204850, 0.199550, 0.204850, 0.248400 } ),
		L20( Bands.K07, new int[][] { { 2,8,9 }, { 3,10,11 }, { 4,12,13 }, { 5,14,15 }, { 6,16,17,18 }, { 7,19,20,21 } }, 
				new int[][] { { 9,10,26,47,72,113 }, { 11,27,48,73,114 }, { 23,28,49,74,115 }, { 29,50,75,116 }, { 30,51,76,117 }, { 24,31,52,77,122 }, { 32,53,78,123 }, { 25,33,54,79,124 }, { 34,55,80,125 }, { 46,56,101,126 }, { 57,102,127 }, { 69,107,128 }, { 108,129 }, { 70,109,130 }, { 110,131 }, { 132 }, { 71,111,137 }, { 112,138 }, { 139 } }, 
				new double[] { 0.046500, 0.056280, 0.047040, 0.058000, 0.052800, 0.085960, 0.096400, 0.085960, 0.096400, 0.122800, 0.156650, 0.199550, 0.204850, 0.199550, 0.204850, 0.248400, 0.199550, 0.204850, 0.248400 } ),
		L21( Bands.K07, new int[][] { { 2,8,9 }, { 3,10,11 }, { 4,12,13 }, { 5,14,15,16 }, { 6,17,18,19 }, { 7,20,21,22 } }, 
				new int[][] { { 9,24,34,70,112 }, { 10,25,46,71,113 }, { 11,26,47,72,114 }, { 27,48,73,115 }, { 28,49,74,116 }, { 29,50,75,117 }, { 30,51,76,122 }, { 23,31,52,77,123 }, { 32,53,78,124 }, { 33,54,79,125 }, { 55,80,126 }, { 56,101,127 }, { 102,128 }, { 107,129 }, { 57,108,130 }, { 109,131 }, { 132 }, { 69,110,137 }, { 111,138 }, { 139 } }, 
				new double[] { 0.086800, 0.056280, 0.047040, 0.058000, 0.052800, 0.122800, 0.096400, 0.085960, 0.096400, 0.122800, 0.156650, 0.199550, 0.204850, 0.175950, 0.199550, 0.204850, 0.248400, 0.199550, 0.204850, 0.248400 } ),
		L22( Bands.K08, new int[][] { { 2,8,9 }, { 3,10,11 }, { 4,12,13,14 }, { 5,15,16,17 }, { 6,18,19,20 }, { 7,21,22,23 } }, 
				new int[][] { { 9,11,32,56,111 }, { 10,23,33,57,112 }, { 24,34,69,113 }, { 25,46,70,114 }, { 26,47,71,115 }, { 27,48,72,116 }, { 28,49,73,117 }, { 29,50,74,122 }, { 30,51,75,123 }, { 31,52,76,124 }, { 53,77,125 }, { 78,126 }, { 54,79,127 }, { 80,128 }, { 102,129 }, { 55,107,130 }, { 108,131 }, { 132 }, { 109,137 }, { 110,138 }, { 139 } }, 
				new double[] { 0.086800, 0.056280, 0.067200, 0.058000, 0.052800, 0.122800, 0.096400, 0.122800, 0.096400, 0.122800, 0.156650, 0.175950, 0.199550, 0.204850, 0.175950, 0.199550, 0.204850, 0.248400, 0.260950, 0.204850, 0.248400 } ),
		L23( Bands.K08, new int[][] { { 2,8,9 }, { 3,10,11,12 }, { 4,13,14,15 }, { 5,16,17,18 }, { 6,19,20,21 }, { 7,22,23,24 } }, 
				new int[][] { { 9,10,30,54,110 }, { 11,31,55,111 }, { 23,32,56,112 }, { 24,33,57,113 }, { 25,34,69,114 }, { 26,46,70,115 }, { 47,71,116 }, { 27,48,72,117 }, { 28,49,73,122 }, { 50,74,123 }, { 29,51,75,124 }, { 52,76,125 }, { 77,126 }, { 53,78,127 }, { 79,128 }, { 80,129 }, { 102,130 }, { 107,131 }, { 132 }, { 108,137 }, { 109,138 }, { 139 } }, 
				new double[] { 0.086800, 0.080400, 0.067200, 0.058000, 0.052800, 0.122800, 0.156650, 0.122800, 0.096400, 0.134550, 0.122800, 0.156650, 0.175950, 0.199550, 0.204850, 0.175950, 0.260950, 0.204850, 0.248400, 0.260950, 0.204850, 0.248400 } ),
		L24( Bands.K08, new int[][] { { 2,8,9,10 }, { 3,11,12,13 }, { 4,14,15,16 }, { 5,17,18,19 }, { 6,20,21,22 }, { 7,23,24,25 } }, 
				new int[][] { { 9,10,28,53,109 }, { 11,29,54,110 }, { 23,30,55,111 }, { 24,31,56,112 }, { 25,32,57,113 }, { 26,33,69,114 }, { 34,70,115 }, { 46,71,116 }, { 27,47,72,117 }, { 48,73,122 }, { 49,74,123 }, { 50,75,124 }, { 51,76,125 }, { 77,126 }, { 52,78,127 }, { 79,128 }, { 129 }, { 80,130 }, { 102,131 }, { 132 }, { 107,137 }, { 108,138 }, { 139 } }, 
				new double[] { 0.086800, 0.080400, 0.067200, 0.058000, 0.052800, 0.122800, 0.156650, 0.134550, 0.122800, 0.156650, 0.134550, 0.199550, 0.156650, 0.175950, 0.199550, 0.204850, 0.248400, 0.260950, 0.204850, 0.248400, 0.260950, 0.204850, 0.248400 } ),
		L25( Bands.K09, new int[][] { { 2,8,9,10 }, { 3,11,12,13 }, { 4,14,15,16 }, { 5,17,18,19 }, { 6,20,21,22 }, { 7,23,24,25,26 } }, 
				new int[][] { { 9,10,27,52,108 }, { 11,28,53,109 }, { 23,29,54,110 }, { 24,30,55,111 }, { 31,56,112 }, { 25,32,57,113 }, { 33,69,114 }, { 34,70,115 }, { 26,46,71,116 }, { 47,72,117 }, { 48,73,122 }, { 49,74,123 }, { 50,75,124 }, { 76,125 }, { 51,77,126 }, { 78,127 }, { 128 }, { 79,129 }, { 80,130 }, { 131 }, { 107,132 }, { 137 }, { 138 }, { 139 } }, 
				new double[] { 0.086800, 0.080400, 0.067200, 0.058000, 0.085800, 0.122800, 0.156650, 0.134550, 0.122800, 0.156650, 0.134550, 0.199550, 0.156650, 0.175950, 0.199550, 0.204850, 0.248400, 0.260950, 0.204850, 0.248400, 0.260950, 0.289200, 0.248400, 0.228000 } ),
		L26( Bands.K09, new int[][] { { 2,8,9,10 }, { 3,11,12,13 }, { 4,14,15,16 }, { 5,17,18,19 }, { 6,20,21,22,23 }, { 7,24,25,26,27 } }, 
				new int[][] { { 9,10,26,50,107 }, { 11,27,51,108 }, { 23,28,52,109 }, { 29,53,110 }, { 30,54,111 }, { 24,31,55,112 }, { 32,56,113 }, { 33,57,114 }, { 25,34,69,115 }, { 46,70,116 }, { 47,71,117 }, { 48,72,122 }, { 73,123 }, { 74,124 }, { 49,75,125 }, { 76,126 }, { 127 }, { 77,128 }, { 78,129 }, { 130 }, { 131 }, { 79,132 }, { 80,137 }, { 138 }, { 139 } }, 
				new double[] { 0.086800, 0.080400, 0.067200, 0.094250, 0.085800, 0.122800, 0.156650, 0.134550, 0.122800, 0.156650, 0.134550, 0.199550, 0.204850, 0.175950, 0.199550, 0.204850, 0.248400, 0.260950, 0.204850, 0.248400, 0.228000, 0.260950, 0.204850, 0.248400, 0.228000 } ),
		L27( Bands.K09, new int[][] { { 2,8,9,10 }, { 3,11,12,13 }, { 4,14,15,16 }, { 5,17,18,19,20 }, { 6,21,22,23,24 }, { 7,25,26,27,28 } }, 
				new int[][] { { 9,10,26,48,80 }, { 11,27,49,107 }, { 23,28,50,108 }, { 29,51,109 }, { 30,52,110 }, { 24,31,53,111 }, { 32,54,112 }, { 55,113 }, { 25,33,56,114 }, { 34,57,115 }, { 69,116 }, { 46,70,117 }, { 71,122 }, { 72,123 }, { 47,73,124 }, { 74,125 }, { 75,126 }, { 127 }, { 76,128 }, { 77,129 }, { 130 }, { 131 }, { 78,132 }, { 79,137 }, { 138 }, { 139 } }, 
				new double[] { 0.086800, 0.080400, 0.067200, 0.094250, 0.085800, 0.122800, 0.156650, 0.175950, 0.122800, 0.156650, 0.175950, 0.199550, 0.204850, 0.175950, 0.199550, 0.204850, 0.175950, 0.228000, 0.260950, 0.204850, 0.248400, 0.228000, 0.260950, 0.204850, 0.248400, 0.228000 } ),
		L28( Bands.K10, new int[][] { { 2,8,9,10 }, { 3,11,12,13 }, { 4,14,15,16,17 }, { 5,18,19,20,21 }, { 6,22,23,24,25 }, { 7,26,27,28,29 } }, 
				new int[][] { { 9,25,47,78 }, { 10,26,48,79 }, { 11,27,49,80 }, { 28,50,108 }, { 29,51,109 }, { 23,30,52,110 }, { 31,53,111 }, { 54,112 }, { 24,32,55,113 }, { 33,56,114 }, { 57,115 }, { 34,69,116 }, { 70,117 }, { 71,122 }, { 72,123 }, { 46,73,124 }, { 74,125 }, { 126 }, { 127 }, { 75,128 }, { 76,129 }, { 130 }, { 131 }, { 77,132 }, { 137 }, { 138 }, { 139 } }, 
				new double[] { 0.124000, 0.080400, 0.067200, 0.094250, 0.085800, 0.122800, 0.156650, 0.175950, 0.122800, 0.156650, 0.175950, 0.199550, 0.204850, 0.175950, 0.161500, 0.199550, 0.204850, 0.248400, 0.228000, 0.260950, 0.204850, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000 } ),
		L29( Bands.K10, new int[][] { { 2,8,9,10 }, { 3,11,12,13,14 }, { 4,15,16,17,18 }, { 5,19,20,21,22 }, { 6,23,24,25,26 }, { 7,27,28,29,30 } }, 
				new int[][] { { 9,24,46,77 }, { 10,25,47,78 }, { 11,26,48,79 }, { 27,49,80 }, { 28,50,108 }, { 29,51,109 }, { 30,52,110 }, { 53,111 }, { 23,31,54,112 }, { 32,55,113 }, { 56,114 }, { 57,115 }, { 33,69,116 }, { 70,117 }, { 71,122 }, { 72,123 }, { 34,73,124 }, { 74,125 }, { 126 }, { 127 }, { 75,128 }, { 129 }, { 130 }, { 131 }, { 76,132 }, { 137 }, { 138 }, { 139 } }, 
				new double[] { 0.124000, 0.080400, 0.067200, 0.094250, 0.085800, 0.199550, 0.156650, 0.175950, 0.122800, 0.156650, 0.175950, 0.161500, 0.199550, 0.204850, 0.175950, 0.161500, 0.199550, 0.204850, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000 } ),
		L30( Bands.K10, new int[][] { { 2,8,9,10,11 }, { 3,12,13,14,15 }, { 4,16,17,18,19 }, { 5,20,21,22,23 }, { 6,24,25,26,27 }, { 7,28,29,30,31 } }, 
				new int[][] { { 9,23,34,76 }, { 10,24,46,77 }, { 11,25,47,78 }, { 26,48,79 }, { 27,49,80 }, { 28,50,108 }, { 29,51,109 }, { 52,110 }, { 53,111 }, { 30,54,112 }, { 31,55,113 }, { 56,114 }, { 57,115 }, { 32,69,116 }, { 70,117 }, { 71,122 }, { 123 }, { 33,72,124 }, { 73,125 }, { 126 }, { 127 }, { 74,128 }, { 129 }, { 130 }, { 131 }, { 75,132 }, { 137 }, { 138 }, { 139 } }, 
				new double[] { 0.124000, 0.080400, 0.067200, 0.094250, 0.085800, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.204850, 0.175950, 0.228000, 0.199550, 0.204850, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000 } ),
		L31( Bands.K11, new int[][] { { 2,8,9,10,11 }, { 3,12,13,14,15 }, { 4,16,17,18,19 }, { 5,20,21,22,23 }, { 6,24,25,26,27 }, { 7,28,29,30,31,32 } }, 
				new int[][] { { 9,11,32,74 }, { 10,23,33,75 }, { 24,34,76 }, { 25,46,77 }, { 26,47,78 }, { 27,48,79 }, { 28,49,80 }, { 50,109 }, { 51,110 }, { 29,52,111 }, { 30,53,112 }, { 54,113 }, { 55,114 }, { 31,56,115 }, { 57,116 }, { 69,117 }, { 122 }, { 70,123 }, { 71,124 }, { 125 }, { 126 }, { 72,127 }, { 128 }, { 129 }, { 130 }, { 73,131 }, { 132 }, { 137 }, { 138 }, { 139 } }, 
				new double[] { 0.124000, 0.080400, 0.109200, 0.094250, 0.085800, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.204850, 0.175950, 0.228000, 0.260950, 0.204850, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800 } ),
		L32( Bands.K11, new int[][] { { 2,8,9,10,11 }, { 3,12,13,14,15 }, { 4,16,17,18,19 }, { 5,20,21,22,23 }, { 6,24,25,26,27,28 }, { 7,29,30,31,32,33 } }, 
				new int[][] { { 9,11,32,74 }, { 10,23,33,75 }, { 24,34,76 }, { 25,46,77 }, { 26,47,78 }, { 27,48,79 }, { 28,49,80 }, { 50,109 }, { 51,110 }, { 29,52,111 }, { 30,53,112 }, { 54,113 }, { 55,114 }, { 31,56,115 }, { 57,116 }, { 69,117 }, { 122 }, { 70,123 }, { 71,124 }, { 125 }, { 126 }, { 72,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { 73,132 }, { 137 }, { 138 }, { 139 }, { } }, 
				new double[] { 0.124000, 0.080400, 0.109200, 0.094250, 0.085800, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.204850, 0.175950, 0.228000, 0.260950, 0.204850, 0.248400, 0.228000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000 } ),
		L33( Bands.K11, new int[][] { { 2,8,9,10,11 }, { 3,12,13,14,15 }, { 4,16,17,18,19 }, { 5,20,21,22,23,24 }, { 6,25,26,27,28,29 }, { 7,30,31,32,33,34 } }, 
				new int[][] { { 9,10,31,72 }, { 11,32,73 }, { 23,33,74 }, { 24,34,75 }, { 25,46,76 }, { 26,47,77 }, { 27,48,78 }, { 49,79 }, { 50,80 }, { 28,51,109 }, { 29,52,110 }, { 53,111 }, { 54,112 }, { 30,55,113 }, { 56,114 }, { 115 }, { 116 }, { 57,117 }, { 69,122 }, { 123 }, { 124 }, { 125 }, { 70,126 }, { 127 }, { 128 }, { 129 }, { 130 }, { 71,131 }, { 132 }, { 137 }, { 138 }, { 139 } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.085800, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.156650, 0.175950, 0.161500, 0.199550, 0.204850, 0.248400, 0.228000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800 } ),
		L34( Bands.K12, new int[][] { { 2,8,9,10,11 }, { 3,12,13,14,15 }, { 4,16,17,18,19,20 }, { 5,21,22,23,24,25 }, { 6,26,27,28,29,30 }, { 7,31,32,33,34,35 } }, 
				new int[][] { { 9,10,29,71 }, { 11,30,72 }, { 23,31,73 }, { 24,32,74 }, { 25,33,75 }, { 26,34,76 }, { 46,77 }, { 47,78 }, { 48,79 }, { 27,49,80 }, { 50,110 }, { 51,111 }, { 52,112 }, { 28,53,113 }, { 54,114 }, { 55,115 }, { 116 }, { 117 }, { 56,122 }, { 57,123 }, { 124 }, { 125 }, { 126 }, { 69,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { 70,132 }, { 137 }, { 138 }, { 139 }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.085800, 0.199550, 0.204850, 0.175950, 0.161500, 0.199550, 0.204850, 0.175950, 0.161500, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000 } ),
		L35( Bands.K12, new int[][] { { 2,8,9,10,11 }, { 3,12,13,14,15,16 }, { 4,17,18,19,20,21 }, { 5,22,23,24,25,26 }, { 6,27,28,29,30,31 }, { 7,32,33,34,35,36 } }, 
				new int[][] { { 9,10,29,71 }, { 11,30,72 }, { 23,31,73 }, { 24,32,74 }, { 25,33,75 }, { 26,34,76 }, { 46,77 }, { 47,78 }, { 48,79 }, { 27,49,80 }, { 50,110 }, { 51,111 }, { 52,112 }, { 53,113 }, { 28,54,114 }, { 55,115 }, { 116 }, { 117 }, { 122 }, { 56,123 }, { 57,124 }, { 125 }, { 126 }, { 127 }, { 69,128 }, { 129 }, { 130 }, { 131 }, { }, { 70,132 }, { 137 }, { 138 }, { 139 }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.085800, 0.199550, 0.204850, 0.175950, 0.161500, 0.199550, 0.204850, 0.175950, 0.161500, 0.152150, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000 } ),
		L36( Bands.K12, new int[][] { { 2,8,9,10,11,12 }, { 3,13,14,15,16,17 }, { 4,18,19,20,21,22 }, { 5,23,24,25,26,27 }, { 6,28,29,30,31,32 }, { 7,33,34,35,36,37 } }, 
				new int[][] { { 9,10,29,70 }, { 11,30,71 }, { 23,31,72 }, { 24,32,73 }, { 25,33,74 }, { 26,34,75 }, { 46,76 }, { 47,77 }, { 48,78 }, { 79 }, { 27,49,80 }, { 50,110 }, { 51,111 }, { 52,112 }, { 113 }, { 28,53,114 }, { 54,115 }, { 116 }, { 117 }, { 122 }, { 55,123 }, { 56,124 }, { 125 }, { 126 }, { 127 }, { 57,128 }, { 129 }, { 130 }, { 131 }, { }, { 69,132 }, { 137 }, { 138 }, { 139 }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.085800, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000 } ),
		L37( Bands.K12, new int[][] { { 2,8,9,10,11,12 }, { 3,13,14,15,16,17 }, { 4,18,19,20,21,22 }, { 5,23,24,25,26,27 }, { 6,28,29,30,31,32 }, { 7,33,34,35,36,37,38 } }, 
				new int[][] { { 9,10,28,69 }, { 11,29,70 }, { 23,30,71 }, { 24,31,72 }, { 32,73 }, { 25,33,74 }, { 34,75 }, { 46,76 }, { 47,77 }, { 78 }, { 26,48,79 }, { 49,80 }, { 50,110 }, { 51,111 }, { 52,112 }, { 27,53,113 }, { 54,114 }, { 115 }, { 116 }, { 117 }, { 55,122 }, { 123 }, { 124 }, { 125 }, { 126 }, { 56,127 }, { 128 }, { 129 }, { 130 }, { }, { 57,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.112200, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.175950, 0.161500, 0.152150, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000 } ),
		L38( Bands.K12, new int[][] { { 2,8,9,10,11,12 }, { 3,13,14,15,16,17 }, { 4,18,19,20,21,22 }, { 5,23,24,25,26,27 }, { 6,28,29,30,31,32,33 }, { 7,34,35,36,37,38,39 } },  
				new int[][] { { 9,10,28,57 }, { 11,29,69 }, { 23,30,70 }, { 24,31,71 }, { 32,72 }, { 25,33,73 }, { 34,74 }, { 46,75 }, { 47,76 }, { 77 }, { 26,48,78 }, { 49,79 }, { 50,80 }, { 51,110 }, { 111 }, { 27,52,112 }, { 53,113 }, { 114 }, { 115 }, { 116 }, { 54,117 }, { 122 }, { 123 }, { 124 }, { 125 }, { 55,126 }, { 127 }, { 128 }, { 129 }, { 130 }, { }, { 56,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.112200, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000 } ),
		L39( Bands.K12, new int[][] { { 2,8,9,10,11,12 }, { 3,13,14,15,16,17 }, { 4,18,19,20,21,22 }, { 5,23,24,25,26,27,28 }, { 6,29,30,31,32,33,34 }, { 7,35,36,37,38,39,40 } }, 
				new int[][] { { 9,10,28,69 }, { 11,29,70 }, { 23,30,71 }, { 24,31,72 }, { 32,73 }, { 25,33,74 }, { 34,75 }, { 46,76 }, { 47,77 }, { 78 }, { 26,48,79 }, { 49,80 }, { 50,110 }, { 51,111 }, { 112 }, { 27,52,113 }, { 53,114 }, { 115 }, { 116 }, { 117 }, { 54,122 }, { 55,123 }, { 124 }, { 125 }, { 126 }, { }, { 56,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { 57,132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.112200, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000 } ),
		L40( Bands.K12, new int[][] { { 2,8,9,10,11,12 }, { 3,13,14,15,16,17 }, { 4,18,19,20,21,22,23 }, { 5,24,25,26,27,28,29 }, { 6,30,31,32,33,34,35 }, { 7,36,37,38,39,40,41 } }, 
				new int[][] { { 9,10,28,57 }, { 11,29,69 }, { 23,30,70 }, { 24,31,71 }, { 32,72 }, { 25,33,73 }, { 34,74 }, { 46,75 }, { 47,76 }, { 77 }, { 26,48,78 }, { 49,79 }, { 50,80 }, { 110 }, { 111 }, { 27,51,112 }, { 52,113 }, { 114 }, { 115 }, { 116 }, { 117 }, { 53,122 }, { 54,123 }, { 124 }, { 125 }, { 126 }, { }, { 55,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { 56,132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.094250, 0.112200, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000 } ),
		L41( Bands.K12, new int[][] { { 2,8,9,10,11,12 }, { 3,13,14,15,16,17,18 }, { 4,19,20,21,22,23,24 }, { 5,25,26,27,28,29,30 }, { 6,31,32,33,34,35,36 }, { 7,37,38,39,40,41,42 } }, 
				new int[][] { { 9,10,27,56 }, { 11,28,57 }, { 23,29,69 }, { 30,70 }, { 31,71 }, { 24,32,72 }, { 33,73 }, { 34,74 }, { 75 }, { 76 }, { 25,46,77 }, { 47,78 }, { 48,79 }, { 49,80 }, { 110 }, { 111 }, { 26,50,112 }, { 51,113 }, { 114 }, { 115 }, { 116 }, { 117 }, { 52,122 }, { 53,123 }, { 124 }, { 125 }, { 126 }, { }, { 54,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { 55,132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.207600, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000 } ),
		L42( Bands.K12, new int[][] { { 2,8,9,10,11,12,13 }, { 3,14,15,16,17,18,19 }, { 4,20,21,22,23,24,25 }, { 5,26,27,28,29,30,31 }, { 6,32,33,34,35,36,37 }, { 7,38,39,40,41,42,43 } }, 
				new int[][] { { 9,10,26,56 }, { 11,27,57 }, { 23,28,69 }, { 29,70 }, { 30,71 }, { 24,31,72 }, { 32,73 }, { 33,74 }, { 34,75 }, { 76 }, { 77 }, { 25,46,78 }, { 47,79 }, { 48,80 }, { 49,110 }, { 111 }, { 112 }, { 50,113 }, { 51,114 }, { 115 }, { 116 }, { 117 }, { 122 }, { 52,123 }, { 53,124 }, { 125 }, { 126 }, { 127 }, { }, { 54,128 }, { 129 }, { 130 }, { 131 }, { }, { }, { 55,132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.207600, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000 } ),
		L43( Bands.K12, new int[][] { { 2,8,9,10,11,12,13 }, { 3,14,15,16,17,18,19 }, { 4,20,21,22,23,24,25 }, { 5,26,27,28,29,30,31 }, { 6,32,33,34,35,36,37 }, { 7,38,39,40,41,42,43,44 } }, 
				new int[][] { { 9,10,26,55 }, { 11,27,56 }, { 23,28,57 }, { 29,69 }, { 30,70 }, { 24,31,71 }, { 32,72 }, { 33,73 }, { 34,74 }, { 75 }, { 76 }, { 25,46,77 }, { 47,78 }, { 48,79 }, { 49,80 }, { 110 }, { 111 }, { 50,112 }, { 51,113 }, { 114 }, { 115 }, { 116 }, { 117 }, { 52,122 }, { 123 }, { 124 }, { 125 }, { 126 }, { }, { 53,127 }, { 128 }, { 129 }, { 130 }, { }, { }, { 54,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.207600, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000 } ),
		L44( Bands.K12, new int[][] { { 2,8,9,10,11,12,13 }, { 3,14,15,16,17,18,19 }, { 4,20,21,22,23,24,25 }, { 5,26,27,28,29,30,31 }, { 6,32,33,34,35,36,37,38 }, { 7,39,40,41,42,43,44,45 } }, 
				new int[][] { { 9,10,26,54 }, { 11,27,55 }, { 23,28,56 }, { 29,57 }, { 30,69 }, { 24,31,70 }, { 32,71 }, { 33,72 }, { 73 }, { 74 }, { 75 }, { 25,34,76 }, { 46,77 }, { 47,78 }, { 48,79 }, { 80 }, { 110 }, { 49,111 }, { 50,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 51,117 }, { 122 }, { 123 }, { 124 }, { 125 }, { }, { 52,126 }, { 127 }, { 128 }, { 129 }, { 130 }, { }, { }, { 53,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.199550, 0.204850, 0.175950, 0.161500, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000 } ),
		L45( Bands.K12, new int[][] { { 2,8,9,10,11,12,13 }, { 3,14,15,16,17,18,19 }, { 4,20,21,22,23,24,25 }, { 5,26,27,28,29,30,31,32 }, { 6,33,34,35,36,37,38,39 }, { 7,40,41,42,43,44,45,46 } }, 
				new int[][] { { 9,10,26,54 }, { 11,27,55 }, { 23,28,56 }, { 29,57 }, { 30,69 }, { 24,31,70 }, { 32,71 }, { 33,72 }, { 73 }, { 74 }, { 75 }, { 25,34,76 }, { 46,77 }, { 47,78 }, { 79 }, { 80 }, { 110 }, { 48,111 }, { 49,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 50,117 }, { 51,122 }, { 123 }, { 124 }, { 125 }, { }, { }, { 52,126 }, { 127 }, { 128 }, { 129 }, { 130 }, { }, { }, { 53,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.124000, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000 } ),
		L46( Bands.K12, new int[][] { { 2,8,9,10,11,12,13 }, { 3,14,15,16,17,18,19 }, { 4,20,21,22,23,24,25,26 }, { 5,27,28,29,30,31,32,33 }, { 6,34,35,36,37,38,39,40 }, { 7,41,42,43,44,45,46,47 } }, 
				new int[][] { { 9,25,53 }, { 10,26,54 }, { 11,27,55 }, { 28,56 }, { 29,57 }, { 23,30,69 }, { 31,70 }, { 32,71 }, { 72 }, { 73 }, { 74 }, { 24,33,75 }, { 34,76 }, { 46,77 }, { 78 }, { 79 }, { 80 }, { 47,110 }, { 48,111 }, { 112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 49,117 }, { 50,122 }, { 123 }, { 124 }, { 125 }, { }, { }, { 51,126 }, { 127 }, { 128 }, { 129 }, { 130 }, { }, { }, { 52,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000 } ),
		L47( Bands.K12, new int[][] { { 2,8,9,10,11,12,13 }, { 3,14,15,16,17,18,19,20 }, { 4,21,22,23,24,25,26,27 }, { 5,28,29,30,31,32,33,34 }, { 6,35,36,37,38,39,40,41 }, { 7,42,43,44,45,46,47,48 } }, 
				new int[][] { { 9,25,53 }, { 10,26,54 }, { 11,27,55 }, { 28,56 }, { 29,57 }, { 23,30,69 }, { 31,70 }, { 32,71 }, { 72 }, { 73 }, { 74 }, { 24,33,75 }, { 34,76 }, { 46,77 }, { 78 }, { 79 }, { 80 }, { 110 }, { 47,111 }, { 48,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 117 }, { 49,122 }, { 50,123 }, { 124 }, { 125 }, { 126 }, { }, { }, { 51,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { }, { 52,132 }, { 137 }, { 138 }, { 139 }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000 } ),
		L48( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14 }, { 3,15,16,17,18,19,20,21 }, { 4,22,23,24,25,26,27,28 }, { 5,29,30,31,32,33,34,35 }, { 6,36,37,38,39,40,41,42 }, { 7,43,44,45,46,47,48,49 } }, 
				new int[][] { { 9,25,53 }, { 10,26,54 }, { 11,27,55 }, { 28,56 }, { 29,57 }, { 23,30,69 }, { 31,70 }, { 32,71 }, { 72 }, { 73 }, { 74 }, { 75 }, { 24,33,76 }, { 34,77 }, { 46,78 }, { 79 }, { 80 }, { 110 }, { 111 }, { 47,112 }, { 48,113 }, { 114 }, { 115 }, { 116 }, { 117 }, { 122 }, { 49,123 }, { 50,124 }, { 125 }, { 126 }, { 127 }, { }, { }, { 51,128 }, { 129 }, { 130 }, { 131 }, { }, { }, { }, { 52,132 }, { 137 }, { 138 }, { 139 }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000 } ),
		L49( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14 }, { 3,15,16,17,18,19,20,21 }, { 4,22,23,24,25,26,27,28 }, { 5,29,30,31,32,33,34,35 }, { 6,36,37,38,39,40,41,42 }, { 7,43,44,45,46,47,48,49,50 } }, 
				new int[][] { { 9,25,53 }, { 10,26,54 }, { 11,27,55 }, { 28,56 }, { 29,57 }, { 23,30,69 }, { 31,70 }, { 32,71 }, { 72 }, { 73 }, { 74 }, { 75 }, { 24,33,76 }, { 34,77 }, { 46,78 }, { 79 }, { 80 }, { 110 }, { 111 }, { 47,112 }, { 48,113 }, { 114 }, { 115 }, { 116 }, { 117 }, { }, { 49,122 }, { 50,123 }, { 124 }, { 125 }, { 126 }, { }, { }, { 51,127 }, { 128 }, { 129 }, { 130 }, { }, { }, { }, { 52,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000 } ),
		L50( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14 }, { 3,15,16,17,18,19,20,21 }, { 4,22,23,24,25,26,27,28 }, { 5,29,30,31,32,33,34,35 }, { 6,36,37,38,39,40,41,42,43 }, { 7,44,45,46,47,48,49,50,51 } }, 
				new int[][] { { 9,25,53 }, { 10,26,54 }, { 11,27,55 }, { 28,56 }, { 29,57 }, { 23,30,69 }, { 31,70 }, { 32,71 }, { 72 }, { 73 }, { 74 }, { 75 }, { 24,33,76 }, { 34,77 }, { 46,78 }, { 79 }, { 80 }, { 110 }, { 111 }, { 47,112 }, { 48,113 }, { 114 }, { 115 }, { 116 }, { 117 }, { }, { 49,122 }, { 50,123 }, { 124 }, { 125 }, { 126 }, { }, { }, { 51,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { }, { }, { 52,132 }, { 137 }, { 138 }, { 139 }, { }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000, 0.000000 } ),
		L51( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14 }, { 3,15,16,17,18,19,20,21 }, { 4,22,23,24,25,26,27,28 }, { 5,29,30,31,32,33,34,35,36 }, { 6,37,38,39,40,41,42,43,44 }, { 7,45,46,47,48,49,50,51,52 } }, 
				new int[][] { { 9,25,52 }, { 10,26,53 }, { 11,27,54 }, { 28,55 }, { 29,56 }, { 23,30,57 }, { 31,69 }, { 32,70 }, { 71 }, { 72 }, { 73 }, { 74 }, { 24,33,75 }, { 34,76 }, { 77 }, { 78 }, { 79 }, { 80 }, { 110 }, { 46,111 }, { 47,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { }, { 48,117 }, { 49,122 }, { 123 }, { 124 }, { 125 }, { }, { }, { }, { 50,126 }, { 127 }, { 128 }, { 129 }, { 130 }, { }, { }, { }, { 51,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.109200, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000 } ),
		L52( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14 }, { 3,15,16,17,18,19,20,21 }, { 4,22,23,24,25,26,27,28,29 }, { 5,30,31,32,33,34,35,36,37 }, { 6,38,39,40,41,42,43,44,45 }, { 7,46,47,48,49,50,51,52,53 } }, 
				new int[][] { { 9,24,51 }, { 10,25,52 }, { 26,53 }, { 27,54 }, { 28,55 }, { 11,29,56 }, { 30,57 }, { 69 }, { 70 }, { 71 }, { 72 }, { 73 }, { 23,31,74 }, { 32,75 }, { 33,76 }, { 77 }, { 78 }, { 79 }, { 80 }, { 34,110 }, { 46,111 }, { 112 }, { 113 }, { 114 }, { 115 }, { 116 }, { }, { 47,117 }, { 48,122 }, { 123 }, { 124 }, { 125 }, { }, { }, { }, { 49,126 }, { 127 }, { 128 }, { 129 }, { 130 }, { }, { }, { }, { 50,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.142800, 0.123250, 0.112200, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000 } ),
		L53( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14 }, { 3,15,16,17,18,19,20,21,22 }, { 4,23,24,25,26,27,28,29,30 }, { 5,31,32,33,34,35,36,37,38 }, { 6,39,40,41,42,43,44,45,46 }, { 7,47,48,49,50,51,52,53,54 } }, 
				new int[][] { { 9,24,51 }, { 10,25,52 }, { 26,53 }, { 27,54 }, { 28,55 }, { 11,29,56 }, { 30,57 }, { 69 }, { 70 }, { 71 }, { 72 }, { 73 }, { 23,31,74 }, { 32,75 }, { 33,76 }, { 77 }, { 78 }, { 79 }, { 80 }, { 110 }, { 34,111 }, { 46,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 117 }, { }, { 47,122 }, { 48,123 }, { 124 }, { 125 }, { 126 }, { }, { }, { }, { 49,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { }, { }, { 50,132 }, { 137 }, { 138 }, { 139 }, { }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.142800, 0.123250, 0.112200, 0.199550, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.204000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000, 0.000000 } ),
		L54( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14,15 }, { 3,16,17,18,19,20,21,22,23 }, { 4,24,25,26,27,28,29,30,31 }, { 5,32,33,34,35,36,37,38,39 }, { 6,40,41,42,43,44,45,46,47 }, { 7,48,49,50,51,52,53,54,55 } }, 
				new int[][] { { 9,24,52 }, { 10,25,53 }, { 26,54 }, { 27,55 }, { 28,56 }, { 11,29,57 }, { 30,69 }, { 31,70 }, { 71 }, { 72 }, { 73 }, { 74 }, { }, { 23,32,75 }, { 33,76 }, { 34,77 }, { 78 }, { 79 }, { 80 }, { 110 }, { }, { 46,111 }, { 47,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 117 }, { }, { 48,122 }, { 49,123 }, { 124 }, { 125 }, { 126 }, { }, { }, { }, { 50,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { }, { }, { 51,132 }, { 137 }, { 138 }, { 139 }, { }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.142800, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000, 0.000000 } ),
		L55( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14,15 }, { 3,16,17,18,19,20,21,22,23 }, { 4,24,25,26,27,28,29,30,31 }, { 5,32,33,34,35,36,37,38,39 }, { 6,40,41,42,43,44,45,46,47 }, { 7,48,49,50,51,52,53,54,55,56 } }, 
				new int[][] { { 9,24,52 }, { 10,25,53 }, { 26,54 }, { 27,55 }, { 28,56 }, { 11,29,57 }, { 30,69 }, { 31,70 }, { 71 }, { 72 }, { 73 }, { 74 }, { }, { 23,32,75 }, { 33,76 }, { 34,77 }, { 78 }, { 79 }, { 80 }, { 110 }, { }, { 46,111 }, { 47,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 117 }, { }, { 48,122 }, { 49,123 }, { 124 }, { 125 }, { 126 }, { }, { }, { }, { 50,127 }, { 128 }, { 129 }, { 130 }, { }, { }, { }, { }, { 51,131 }, { 132 }, { 137 }, { 138 }, { 139 }, { }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.142800, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.000000 } ),
		L56( Bands.K12, new int[][] { { 2,8,9,10,11,12,13,14,15 }, { 3,16,17,18,19,20,21,22,23 }, { 4,24,25,26,27,28,29,30,31 }, { 5,32,33,34,35,36,37,38,39 }, { 6,40,41,42,43,44,45,46,47,48 }, { 7,49,50,51,52,53,54,55,56,57 } }, 
				new int[][] { { 9,24,52 }, { 10,25,53 }, { 26,54 }, { 27,55 }, { 28,56 }, { 11,29,57 }, { 30,69 }, { 31,70 }, { 71 }, { 72 }, { 73 }, { 74 }, { }, { 23,32,75 }, { 33,76 }, { 34,77 }, { 78 }, { 79 }, { 80 }, { 110 }, { }, { 46,111 }, { 47,112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 117 }, { }, { 48,122 }, { 49,123 }, { 124 }, { 125 }, { 126 }, { }, { }, { }, { 50,127 }, { 128 }, { 129 }, { 130 }, { 131 }, { }, { }, { }, { }, { 51,132 }, { 137 }, { 138 }, { 139 }, { }, { }, { }, { }, { } }, 
				new double[] { 0.201500, 0.130650, 0.142800, 0.123250, 0.112200, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.199550, 0.204850, 0.175950, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.207600, 0.198000, 0.000000, 0.260950, 0.204850, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.214800, 0.000000, 0.000000, 0.000000, 0.000000, 0.260950, 0.289200, 0.248400, 0.228000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000 } ),
		SILENCE( Bands.K03, new int[][] {}, new int[][] {}, new double[] {});
		
		private Bands mBands;
		private int[][] mBlockHarmonicAllocations;
		private int[][] mQuantizedValueIndices;
		private double[] mStepSizes;
		
		private Harmonic( Bands bands, int[][] blockHarmonics, 
				int[][] quantizedValueIndices, double[] stepSizes )
		{
			mBands = bands;
			mBlockHarmonicAllocations = blockHarmonics;
			mQuantizedValueIndices = quantizedValueIndices;
			mStepSizes = stepSizes;
		}
		
		public Bands getBands()
		{
			return mBands;
		}

		public int[][] getBlockHarmonics()
		{
			return mBlockHarmonicAllocations;
		}
		
		public int[][] getQuantizedValueIndices()
		{
			return mQuantizedValueIndices;
		}
		
		public double[] getStepSizes()
		{
			return mStepSizes;
		}
		
		public int getL()
		{
			return mStepSizes.length + 1;
		}
		
		/**
		 * Reconstructs the spectral amplitude prediction residual set (T) for 
		 * all values of L using inverse DCT first against the gain vector and 
		 * then against each of the 6 j-block sets of coefficients.  Index 0 is 
		 * set to zero and the residuals ( 1 <> L ) are placed in indexes 1 - L.
		 */
		public double[] getSpectralAmplitudePredictionResiduals( BinaryFrame frame )
		{
			double[] residuals = new double[ getL() + 1 ];
			
			double[][] coefficients = getCoefficients( frame );
			
			/* Algorithm #69 & #70 - perform 6-point inverse DCT on gain vector */
			double[] R = new double[ 6 ];
			
			for( int i = 0; i < 6; i++ )
			{
				/* Cosine of 0 is 1, so handle index 0 more efficiently */
				R[ i ] += coefficients[ 0 ][ 0 ];
				
				for( int m = 1; m < 6; m++ )
				{
					R[ i ] += 2.0 * coefficients[ m ][ 0 ] * 
						Math.cos( PI_OVER_6 * (double)m * ( (double)i + 0.5 ) );
				}
			}

			/* Transfer results back to the coefficients array */
			for( int i = 0; i < 6; i++ )
			{
				coefficients[ i ][ 0 ] = R[ i ];
			}

			/* Algorithm #73 & #74 - perform inverse DCT on each of the J-block 
			 * sets of coefficients.  Place inverse DCT results in the 
			 * residuals array */
			int pointer = 0;

			for( int i = 0; i < 6; i++ ) /* J-Block index */
			{
				int Ji = mBlockHarmonicAllocations[ i ].length;
				
				for( int j = 0; j < Ji; j++ )
				{
					pointer++;
					
					/* Cosine of 0 is 1, so handle index 0 more efficiently */
					residuals[ pointer ] = coefficients[ i ][ 0 ];

					for( int k = 1; k < Ji; k++ )
					{
						residuals[ pointer ] += 2.0 * coefficients[ i ][ k ] *
						Math.cos( ( Math.PI * (double)k * ( (double)j + 0.5 ) ) / 
								(double)Ji );
					}
				}
			}
			
			return residuals;
		}

		/**
		 * Reconstructs 6 J-block sets of L coefficients with the gain vector 
		 * in index 0 of each J-block.  The first dimension of the array
		 * corresponds to J blocks 1-6 in indexes 0 - 5
		 * 
		 * The second dimension contains one sixth of the L harmonics 
		 * corresponding to the J-block with allocations detailed in Annex G.
		 * 
		 * @param frame - imbe audio frame
		 * 
		 * @return - multi-dimensional [j][x] array of coefficients with the
		 * j-dimension containing blocks 1 - 6 (index 0 - 5) and the x dimension
		 * containing the set of coefficients assigned to each j-block
		 */
		public double[][] getCoefficients( BinaryFrame frame )
		{
			/* J block 6 (index 5) will always contain the max number of 
			 * harmonics - use this value to dimension the max array size */
			double[][] coefficients = new double[ 6 ][ mBlockHarmonicAllocations[ 5 ].length ];

			/* Algorithm #68 & #71 - get the quantized coefficients for each of 
			 * the L harmonics in the gain vector and the higher-order DCT
			 * coefficients organized across the 6 J-blocks */
			for( int j = 0; j < 6; j++ )
			{
					coefficients[ j ] = new double[ mBlockHarmonicAllocations[ j ].length ];
					
					for( int x = 0; x < mBlockHarmonicAllocations[ j ].length; x++ )
					{
						coefficients[ j ][ x ] = getCoefficient( frame, 
								mBlockHarmonicAllocations[ j ][ x ] );
					}
			}
			
			return coefficients;
		}
		
		/*
		 * Calculates the coefficient for gain vectors b2-b7 and 
		 * harmonics b8 - (L+1).
		 * 
		 * The quantized value is contained in the imbe frame bits identified 
		 * in the mQuantizedValueIndices array.  The step size multiplier is 
		 * contained in the mStepSizes array.  The product of these two 
		 * quantities is the coefficient.
		 * 
		 * Gain vector b2 value and step size is handled separately.
		 */
		private double getCoefficient( BinaryFrame frame, int harmonic )
		{
			if( harmonic == 2 )
			{
				int quantizedValue = frame.getInt( VECTOR_B2 );
				
				return B2_GAIN_LEVELS[ quantizedValue ];
			}
			else
			{
				/* Arrays are 0-indexed starting with harmonic b3 */
				int index = harmonic - 3;
				
				if( mQuantizedValueIndices[ index ].length == 0 )
				{
					return 0.0;
				}
				
				int value = frame.getInt( mQuantizedValueIndices[ index ] );

				double quantizedValue = (double)value + 
					QUANTIZER_OFFSET[ mQuantizedValueIndices[ index ].length ];
				
				double coefficient = quantizedValue * mStepSizes[ index ];
				
				return coefficient;
			}
		}
	}

	/**
	 * K - Frequency Bands
	 * 
	 * The audio spectrum can be split into 3 to 12 frequency bands with
	 * each band classified as a voiced or unvoiced band during encoding.  The 
	 * bit vector b1 conveys the number of bands and the status 
	 * (voiced/unvoiced) of each band.
	 */
	public enum Bands
	{
		K03,K04,K05,K06,K07,K08,K09,K10,K11,K12;
	}
}
