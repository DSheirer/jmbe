package jmbe.audio.imbe;

import java.util.Arrays;

import jmbe.audio.imbe.IMBEFrame.FundamentalFrequency;

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


public class IMBEModelParameters
{
	private FundamentalFrequency mFundamentalFrequency;
	
	private boolean[] mVoicingDecisions;
	
	private double[] mLog2SpectralAmplitudes;
	private double[] mSpectralAmplitudes;
	private double[] mEnhancedSpectralAmplitudes;
	
	private double mErrorRate = 0.0;  //ER
	private double mLocalEnergy = 75000.0; //SE default

	private int mErrorCountCoset0 = 0;  //E0
	private int mErrorCountCoset4 = 0;  //E4
	private int mErrorCountTotal = 0;  //ET
	private int mRepeatCount = 0;
	private int mAmplitudeThreshold = 20480;//YM default

	/**
	 * Constructs a default set of model parameters with the voicing 
	 * decisions set to all false and the mSpectralAmplitudes set to all 1.0
	 * and sized to the L argument.
	 * 
	 */
	public IMBEModelParameters( FundamentalFrequency frequency )
	{
		mFundamentalFrequency = frequency;

		int lplus1 = mFundamentalFrequency.getL() + 1;
		
		mVoicingDecisions = new boolean[ lplus1 ];
		
		mSpectralAmplitudes = new double[ lplus1 ];

		/* Initialized to all zeros */
		mEnhancedSpectralAmplitudes = new double[ lplus1 ];
		
		mLog2SpectralAmplitudes = new double[ lplus1 ];
		
		for( int x = 0; x < lplus1; x++ )
		{
			mVoicingDecisions[ x ] = false;
			mSpectralAmplitudes[ x ] = 1.0d;
		}
	}
	
	public IMBEModelParameters()
	{
	}
	
	/**
	 * Indicates if the current error rate exceeds the maximum error rate and
	 * this frame should be represented by frame muting, or white noise
	 */
	public boolean requiresMuting()
	{
		return mErrorRate > 0.0875;
	}

	/**
	 * Indicates that this frame contains an invalid fundamental frequency or
	 * the error rate exceeds the threshold.  Corrective action is to use the
	 * copy() method to copy the previous frame's model parameters to this frame
	 * 
	 * @return - true if a frame repeat is required
	 */
	public boolean repeatRequired()
	{
		return mFundamentalFrequency == null || 
			   mFundamentalFrequency == FundamentalFrequency.W_INVALID ||
			   exceedsErrorThreshold();
	}

	/**
	 * Algorithm #97 and #98 - defined maximum error rates that require a repeat
	 */
	private boolean exceedsErrorThreshold()
	{
		return mErrorCountCoset0 >= 2 &&
			   mErrorCountTotal >= ( 10.0d + ( 40.0d * mErrorRate ) );
	}

	/**
	 * Copies the model parameters from the previous frame's parameters and 
	 * increments the repeat count.  Use this method when a repeat is required
	 * due to high error rates or invalid fundamental frequency value.
	 */
	public void copy( IMBEModelParameters previous )
	{
		mEnhancedSpectralAmplitudes = previous.getEnhancedSpectralAmplitudes();
		mLog2SpectralAmplitudes = previous.getLog2SpectralAmplitudes();
		mFundamentalFrequency = previous.getFundamentalFrequency();
		mSpectralAmplitudes = previous.getSpectralAmplitudes();
		mVoicingDecisions = previous.getVoicingDecisions();
		
		/* Increment the previous repeat count to indicate that this frame
		 * is a repeat, in addition to any previously repeated frames */
		mRepeatCount = previous.getRepeatCount() + 1;
		
		/* Don't copy the error counts */
	}
	
	/**
	 * Sets the error parameters for this frame and updates the total error rate.
	 * 
	 * @param previousErrorRate - running error rate from previous frame
	 * @param errorsCoset0 - error count after error detection/correction of coset word 0
	 * @param errorsTotal - total error count after error detection/correction
	 */
	public void setErrors( double previousErrorRate, int errorsCoset0, int errorsTotal )
	{
		mErrorCountCoset0 = errorsCoset0;
		mErrorCountTotal = errorsTotal;
		mErrorRate = ( 0.95d * previousErrorRate ) + ( 0.000365d * errorsTotal );
	}
	
	/**
	 * Number of harmonics (L) for this frame.
	 */
	public int getL()
	{
		return mFundamentalFrequency.getHarmonic().getL();
	}

	/**
	 * Fundamental frequency for this frame.  Also contains sub-elements
	 * that specify the number of harmonics (L).
	 */
	public FundamentalFrequency getFundamentalFrequency()
	{
		return mFundamentalFrequency;
	}

	public void setFundamentalFrequency( FundamentalFrequency fundamental )
	{
		mFundamentalFrequency = fundamental;
	}

	/**
	 * Voiced / Non-Voiced decisions for each of the harmonics of this frame
	 */
	public boolean[] getVoicingDecisions()
	{
		return mVoicingDecisions;
	}
	
	public void setVoicingDecisions( boolean[] decisions )
	{
		mVoicingDecisions = decisions;
	}

	/**
	 * Reconstructed spectral amplitudes for each harmonic (L) of this frame
	 */
	public double[] getSpectralAmplitudes()
	{
		return mSpectralAmplitudes;
	}

	public void setSpectralAmplitudes( double[] amplitudes )
	{
		mSpectralAmplitudes = amplitudes;
	}
	
	/**
	 * Enhanced spectral amplitudes for each harmonic (L) of this frame
	 */
	public double[] getEnhancedSpectralAmplitudes()
	{
		return mEnhancedSpectralAmplitudes;
	}

	public void setEnhancedSpectralAmplitudes( double[] amplitudes )
	{
		mEnhancedSpectralAmplitudes = amplitudes;
	}

	/**
	 * Log2 version of the reconstructed spectral amplitudes for each 
	 * harmonic (L) of this frame
	 */
	public double[] getLog2SpectralAmplitudes()
	{
		return mLog2SpectralAmplitudes;
	}

	public void setLog2SpectralAmplitudes( double[] log2amplitudes )
	{
		mLog2SpectralAmplitudes = log2amplitudes;
	}
	
	/**
	 * Number of bit errors detected/corrected in coset word 0 of this frame
	 */
	public int getErrorCountCoset0()
	{
		return mErrorCountCoset0;
	}
	
	/**
	 * Number of bit errors detected/corrected in coset word 4 of this frame
	 */
	public int getErrorCountCoset4()
	{
		return mErrorCountCoset4;
	}
	
	public void setErrorCountCoset4( int errors )
	{
		mErrorCountCoset4 = errors;
	}
	
	/**
	 * Total number of bit errors detected/corrected across coset words 0 - 6
	 * of this frame
	 */
	public int getErrorCountTotal()
	{
		return mErrorCountTotal;
	}

	/**
	 * Running error rate that includes errors from this frame.
	 */
	public double getErrorRate()
	{
		return mErrorRate;
	}

	/**
	 * Local energy (SE0) parameter used in adaptive smoothing 
	 */
	public double getLocalEnergy()
	{
		return mLocalEnergy;
	}
	
	public void setLocalEnergy( double energy )
	{
		mLocalEnergy = energy;
	}

	/**
	 * Amplitude threshold (TM) used in adaptive smoothing
	 */
	public int getAmplitudeThreshold()
	{
		return mAmplitudeThreshold;
	}
	
	public void setAmplitudeThreshold( int threshold )
	{
		mAmplitudeThreshold = threshold;
	}

	/**
	 * Running frame repeat count.  A zero value indicates this frame was not repeated.
	 */
	public int getRepeatCount()
	{
		return mRepeatCount;
	}

	/**
	 * Indicates if this frame was repeated from the previous frame's model
	 * parameters.
	 */
	public boolean isRepeat()
	{
		return mRepeatCount > 0;
	}
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append( "IMBE FRAME" );
		sb.append( "\n  Fundamental: " + mFundamentalFrequency.name() );
		sb.append( "\n  Voicing " + Arrays.toString( mVoicingDecisions ) );
		sb.append( "\n  Log 2 Spectral " + Arrays.toString( mLog2SpectralAmplitudes ) );
		sb.append( "\n  Spectral " + Arrays.toString( mSpectralAmplitudes ) );
		sb.append( "\n  Enhanced Spectral " + Arrays.toString( mEnhancedSpectralAmplitudes ) );
		sb.append( "\n  Coset 0 Errors: " + mErrorCountCoset0 );
		sb.append( "\n  Coset 4 Errors: " + mErrorCountCoset4 );
		sb.append( "\n  Total Errors: " + mErrorCountTotal );
		sb.append( "\n  Error Rate: " + mErrorRate );
		sb.append( "\n  Repeat Count: " + mRepeatCount );
		sb.append( "\n  Local Energy: " + mLocalEnergy );
		sb.append( "\n  Ampl Threshold: " + mAmplitudeThreshold );
		
		return sb.toString();
	}
}
