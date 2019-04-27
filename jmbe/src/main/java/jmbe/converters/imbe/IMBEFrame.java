package jmbe.converters.imbe;

import jmbe.binary.BinaryFrame;
import jmbe.converters.Harmonic;
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

	public static final int[] RANDOMIZER_SEED = { 0,1,2,3,4,5,6,7,8,9,10,11 };

	public static final int[] VECTOR_B0 = { 0,1,2,3,4,5,141,142 };
	public static final int[] VECTOR_B1 = { 92,93,94,95,96,97 };


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
	
}
