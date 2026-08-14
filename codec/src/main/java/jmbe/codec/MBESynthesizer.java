/*
 * ******************************************************************************
 * Copyright (C) 2015-2019 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * *****************************************************************************
 */

package jmbe.codec;

import java.util.Arrays;
import java.util.Random;
import jmbe.codec.imbe.Window;
import org.jtransforms.fft.FloatFFT_1D;

/**
 * Base Multi-Band Excitation (MBE) synthesizer
 */
public abstract class MBESynthesizer
{
    private static final double TWO_PI = Math.PI * 2.0;
    private static final float TWO56_OVER_TWO_PI = 256.0f / (float)TWO_PI;
    private static final float AUDIO_SCALAR_16_BITS_SIGNED = 1.00f / (float)Short.MAX_VALUE;
    private static final float MAXIMUM_AUDIO_AMPLITUDE = Short.MAX_VALUE * 0.95f;
    protected static final int N_SAMPLES_PER_FRAME = 160;
    // Algorithm 121 - unvoiced scaling coefficient (yw) from synthesis window (ws) and pitch refinement window (wr)
    private static final float UNVOICED_SCALING_COEFFICIENT = 146.17696f;
    private final WhiteNoiseGenerator mWhiteNoiseGenerator = new WhiteNoiseGenerator();
    private final MBENoiseSequenceGenerator mMBENoiseSequenceGenerator = new MBENoiseSequenceGenerator();
    private final FloatFFT_1D mFFT = new FloatFFT_1D(256);
    private double[] mCurrentPhaseO = new double[57];
    private double[] mCurrentPhaseV = new double[57];
    private double[] mPreviousPhaseO = new double[57];
    private double[] mPreviousPhaseV = new double[57];
    private float[] mCurrentUw = new float[256];
    private float[] mPreviousUw = new float[256];
    private final float[] mDftBinScalor = new float[128];
    private final float[] mUnvoiced = new float[N_SAMPLES_PER_FRAME];
    private final float[] mVoiced = new float[N_SAMPLES_PER_FRAME];
    private float mNoiseGeneratorGain = 1.0f;
    private final Random mRandomPl = new Random();
    private final AGC mAGC = new AGC();
    private boolean mAGCEnabled = true;

    /**
     * Enables or disables automatic gain control (AGC).
     *
     * @param enabled true to enable, false to disable.  Enabled by default.
     */
    public void setAGC(boolean enabled)
    {
        mAGCEnabled = enabled;
    }

    /**
     * Resets the synthesizer at the end of a call to reset the AGC control.
     */
    public void reset()
    {
        mAGC.reset();

        //Randomize the previous phasors in each of the frequency bands so that the synthesized audio doesn't have
        //the periodic amplitude spikes where each band phasor is marching in unison at multiples of the fundamental
        //frequency
        for(int l = 0; l < 57; l++)
        {
            mPreviousPhaseV[l] = mRandomPl.nextDouble() * TWO_PI - Math.PI;
        }
    }

    /**
     * Sets the gain for white noise generation.
     *
     * @param gain in range 0.0f (disabled) to 2.0f (maximum) with 1.0f as the default
     */
    public void setNoiseGeneratorGain(float gain)
    {
        if(0.0 <= gain && gain <= 2.0)
        {
            mNoiseGeneratorGain = gain;
        }
        else
        {
            throw new IllegalArgumentException("Gain must be in range 0.0 (disabled) to 2.0 (maximum) with 1.0 as the default");
        }
    }


    /**
     * Access previous frame's MBE model parameters
     */
    protected abstract MBEModelParameters getPreviousFrame();

    /**
     * Calculates the minimum 256-point DFT index for each of the L frequency bands
     *
     * Alg #122
     */
    public static int[] getFrequencyBandEdgeMinimums(MBEModelParameters voiceParameters)
    {
        int[] a = new int[voiceParameters.getL() + 1];

        float multiplier = TWO56_OVER_TWO_PI * voiceParameters.getFundamentalFrequency();

        for(int l = 1; l <= voiceParameters.getL(); l++)
        {
            a[l] = (int)Math.ceil(((float)l - 0.5f) * multiplier);
        }

        return a;
    }

    /**
     * Calculates the maximum 256-point DFT index for each of the L frequency bands
     *
     * Alg #123
     */
    public static int[] getFrequencyBandEdgeMaximums(MBEModelParameters voiceParameters)
    {
        int[] b = new int[voiceParameters.getL() + 1];

        float multiplier = TWO56_OVER_TWO_PI * voiceParameters.getFundamentalFrequency();

        for(int x = 1; x <= voiceParameters.getL(); x++)
        {
            b[x] = (int)Math.ceil(((float)x + 0.5f) * multiplier);
        }

        return b;
    }

    /**
     * Returns the speech synthesis window coefficient from appendix I
     */
    public static float synthesisWindow(int n)
    {
        if(n < -105 || n > 105)
        {
            return 0.0f;
        }

        return Window.SYNTHESIS[n + 105];
    }

    /**
     * Returns the pitch refinement window coefficient from appendix C
     */
    public static float pitchRefinementWindow(int n)
    {
        if(n < -110 || n > 110)
        {
            return 0.0f;
        }

        return Window.PITCH_REFINEMENT[n + 110];
    }

    /**
     * Unused.  Was previously used to develop value for constant UNVOICED_SCALING_COEFFICIENT
     */
    public static float getUnvoicedScalingCoefficient()
    {
        float sum_wr = 0.0f;
        float sum_wr_squared = 0.0f;
        float sum_ws_squared = 0.0f;

        for(int x = -110; x <= 110; x++)
        {
            sum_wr += (pitchRefinementWindow(x));
            sum_wr_squared += (pitchRefinementWindow(x) * pitchRefinementWindow(x));
        }

        for(int x = -105; x <= 105; x++)
        {
            sum_ws_squared += (synthesisWindow(x) * synthesisWindow(x));
        }

        float yw = sum_wr * (float)Math.pow((sum_ws_squared / sum_wr_squared), 0.5f);

        return yw;
    }

    /**
     * Generates 160 samples (20 ms) of voice audio using the model parameters
     *
     * @param parameters to use in generating the voice frame
     * @return samples scaled to -1.0 <> 1.0
     */
    public float[] getVoice(MBEModelParameters parameters)
    {
        float[] audio = getVoiced(parameters);

        int x;

        if(mNoiseGeneratorGain > 0.0)
        {
            //Alg #117 - generate white noise samples and populate unvoiced bands.
            float[] unvoiced = getUnvoiced(parameters, mMBENoiseSequenceGenerator.nextBuffer(mNoiseGeneratorGain));

            //Alg #142 - combine voiced and unvoiced audio samples to form the completed audio samples.
            for(x = 0; x < 160; x++)
            {
                audio[x] += (unvoiced[x] * mNoiseGeneratorGain);
            }
        }

        //Apply optional automatic gain control
        if(mAGCEnabled)
        {
            audio = mAGC.process(audio);
        }

        //Constrain or clip audio to prevent clicking
        for(x = 0; x < audio.length; x++)
        {
            audio[x] = clip(audio[x]);
            audio[x] *= AUDIO_SCALAR_16_BITS_SIGNED;
        }

        return audio;
    }

    /**
     * Clips the audio to within -MAX <-> MAX amplitude
     * @param value to clip
     * @return clipped value
     */
    private static float clip(float value)
    {
        if(value > MAXIMUM_AUDIO_AMPLITUDE)
        {
            return MAXIMUM_AUDIO_AMPLITUDE;
        }
        else if(value < -MAXIMUM_AUDIO_AMPLITUDE)
        {
            return -MAXIMUM_AUDIO_AMPLITUDE;
        }

        return value;
    }

    /**
     * Generates 160 samples (20 ms) of white noise
     *
     * @return samples
     */
    public float[] getWhiteNoise()
    {
        //Noise generator is disabled if gain is 0.0
        if(mNoiseGeneratorGain == 0.0)
        {
            return new float[160];
        }

        return mWhiteNoiseGenerator.getSamples(160, 0.003f * mNoiseGeneratorGain);
    }

    /**
     * Applies the synthesis window to the 256-element white noise array by considering the samples of the array to
     * be indexed as -128 <> 127
     * @param whiteNoise samples to window
     * @return windowed white noise samples
     */
    private void applyWindow(float[] whiteNoise, float[] windowed)
    {
        for(int x = 0; x < whiteNoise.length; x++)
        {
            windowed[x] = whiteNoise[x] * synthesisWindow(x - 128);
        }
    }

    /**
     * Generates the unvoiced component of the audio signal using a white noise
     * generator where the frequency components corresponding to the voiced
     * harmonics are removed from the white noise.
     *
     * @param parameters from the voice frame
     * @return - 160 samples of unvoiced audio component
     */
    public float[] getUnvoiced(MBEModelParameters parameters, float[] whiteNoiseSamples)
    {
        float[] Uw = mCurrentUw;
        applyWindow(whiteNoiseSamples, Uw);

        //Alg #122 and #123 - generate the 256 FFT bins to L frequency band mapping from the fundamental frequency
        boolean[] voicedBands = parameters.getVoicingDecisions();
        float[] M = parameters.getEnhancedSpectralAmplitudes();
        int[] a_min = getFrequencyBandEdgeMinimums(parameters);
        int[] b_max = getFrequencyBandEdgeMaximums(parameters);

        //Alg 118 - perform 256-point DFT against samples.  We use the JTransforms library to calculate an FFT against
        // the 256 element sample array that contains zeros for all elements greater than 209
        mFFT.realForward(Uw);
        //NOTE: from this point forward, Uw contains the DFT frequency bins (uw)

        //Alg 120 - determine band-level scaling value for each DFT bin for unvoiced samples and zeroize all voiced and
        // out-of-band bins.  The denominator in this algorithm is the average bin energy per band calculated by summing
        // the squared dft real and the squared dft imaginary values, dividing by the number of bins in the band to get
        // the average, and then taking the square root to get the amplitude average (a^2 + b^2 = c^2).  Calculate this
        // value for each of the unvoiced bands and apply the unvoiced scaling coefficient and the decoded amplitude for
        // the band.
        Arrays.fill(mDftBinScalor, 0.0f);

        for(int l = 1; l <= parameters.getL(); l++)
        {
            if(!voicedBands[l])
            {
                float numerator = 0.0f;

                for(int n = a_min[l]; n < b_max[l]; n++)
                {
                    if(n < 128)
                    {
                        int dftBinIndex = 2 * n;

                        // Real component
                        numerator += (Uw[dftBinIndex] * Uw[dftBinIndex]);

                        dftBinIndex++;

                        // Imaginary component
                        numerator += (Uw[dftBinIndex] * Uw[dftBinIndex]);
                    }
                }

                float denominator = (float)(b_max[l] - a_min[l]);

                float divisor = (float)Math.sqrt((numerator / denominator));

                //Protect against divide by zero
                float scalor = (divisor != 0) ? (UNVOICED_SCALING_COEFFICIENT * M[l] / divisor) : 0.0f;

                for(int n = a_min[l]; n < b_max[l]; n++)
                {
                    if(n < 128)
                    {
                        mDftBinScalor[n] = scalor;
                    }
                }
            }
        }

        // Alg 119, 120 & 124 - scale the DFT bins in the a-b min/max bin ranges.  Since the binScalor array is
        // initialized to zero, this also zeroizes any of lowest and highest frequency DFT bins per Alg 124 that weren't
        // explicitly listed in the a-b DFT bin ranges for each L frequency band.
        for(int bin = 0; bin < 128; bin++)
        {
            int dftBinIndex = 2 * bin;

            Uw[dftBinIndex] *= mDftBinScalor[bin];
            Uw[dftBinIndex + 1] *= mDftBinScalor[bin];
        }

        //Alg #125 - calculate inverse DFT of scaled dft bins to recreate the white noise, notched for voiced bands
        mFFT.realInverse(Uw, true);

        //Note: from this point forward, Uw contains the inverse DFT results

        /* Algorithm #126 - use Weighted Overlap Add algorithm to combine previous
         * Uw and the current Uw inverse DFT results to form final unvoiced set */
        for(int n = 0; n < N_SAMPLES_PER_FRAME; n++)
        {
            float previousWindow = synthesisWindow(n);
            float currentWindow = synthesisWindow(n - N_SAMPLES_PER_FRAME);
            //Uw samples index is in range 0<>255 and must be translated to -128 <> 127 for this algorithm, recognizing
            //that previousUw needs samples for indexes 0<>159 and currentUw needs samples -160<>-1
            float previousUw = (n < 128 ? mPreviousUw[n + 128] : 0.0f); //n
            float currentUw = (n >= 32 ? Uw[n - 32] : 0.0f);  //n - N

            mUnvoiced[n] = ((previousWindow * previousUw) + (currentWindow * currentUw)) /
                ((previousWindow * previousWindow) + (currentWindow * currentWindow));
        }

        float[] previousUw = mPreviousUw;
        mPreviousUw = Uw;
        mCurrentUw = previousUw;

        return mUnvoiced;
    }

    /**
     * Resizes the voicing decisions array, as needed, padding the newly
     * added indices with a 0-false value
     */
    private boolean[] resize(boolean[] voicingDecisions, int size)
    {
        if(voicingDecisions.length != size)
        {
            boolean[] resized = new boolean[size];

            System.arraycopy(voicingDecisions, 0, resized, 0, voicingDecisions.length);

            return resized;
        }

        return voicingDecisions;
    }

    /**
     * Reconstructs the voiced audio components using the model parameters from both the current and previous imbe frames.
     *
     * @param currentFrame - voice parameters
     * @return - 160 samples of voiced audio component
     */
    public float[] getVoiced(MBEModelParameters currentFrame)
    {
        MBEModelParameters previousFrame = getPreviousFrame();
        double currentFrequency = currentFrame.getFundamentalFrequency();
        double previousFrequency = previousFrame.getFundamentalFrequency();
        double averageFrequency = (previousFrequency + currentFrequency) / 2.0f;
        double phaseRotationPerFrame = averageFrequency * N_SAMPLES_PER_FRAME;

        //Alg #139 - calculate current phase angle for each harmonic
        //Update each of the harmonic phase values in the oscillator bank
        for(int l = 1; l <= 56; l++)
        {
            //Alg #139 - calculate current phase v values
            mCurrentPhaseV[l] = mPreviousPhaseV[l] + (phaseRotationPerFrame * l);

            //Limit or unwrap the phase to +/- 2*PI radians
            mCurrentPhaseV[l] %= TWO_PI;
        }

        //Short circuit if there are no voiced bands and return an array of zeros
        if(!previousFrame.hasVoicedBands() && !currentFrame.hasVoicedBands())
        {
            mPreviousPhaseV = mCurrentPhaseV;
            Arrays.fill(mVoiced, 0.0f);
            return mVoiced;
        }

        int currentL = currentFrame.getL();
        int previousL = previousFrame.getL();
        int maxL = Math.max(currentL, previousL);

        boolean[] currentVoicing = resize(currentFrame.getVoicingDecisions(), maxL + 1);
        boolean[] previousVoicing = resize(previousFrame.getVoicingDecisions(), maxL + 1);

        //Alg #128 & #129 - enhanced spectral amplitudes for current and previous frames outside range of 1 - L are set
        // to zero.  Below, in the audio generation loop, we control access to these arrays through the voicing
        // decisions array.  Thus, we don't have to resize the enhanced spectral amplitudes arrays to the max L of
        // current or previous.

        //Alg #140 partial - number of unvoiced spectral amplitudes (Luv) in current frame */
        int unvoicedBandCount = currentFrame.getUnvoicedBandCount();

        //Alg #139 - calculate current phase angle for each harmonic
        int threshold = (int)Math.floor((float)maxL / 4.0f);

        double pl;

        //Update each of the phase values
        for(int l = 1; l <= 56; l++)
        {
            //Alg #140 - calculate current phase o values
            if(l > threshold && l <= maxL)
            {
                //Random number in range -PI to +PI
                pl = (mRandomPl.nextDouble() * TWO_PI) - Math.PI;
                mCurrentPhaseO[l] = mCurrentPhaseV[l] + ((unvoicedBandCount * pl) / currentL);
            }
            else
            {
                mCurrentPhaseO[l] = mCurrentPhaseV[l];
            }
        }

        float[] currentM = currentFrame.getEnhancedSpectralAmplitudes();
        float[] previousM = previousFrame.getEnhancedSpectralAmplitudes();
//        float[] currentM = currentFrame.getSpectralAmplitudes();
//        float[] previousM = previousFrame.getSpectralAmplitudes();
        Arrays.fill(mVoiced, 0.0f);

        //Alg #127 - reconstruct 160 voice samples using each of the l harmonics that are common between this frame and
        // the previous frame, using one of four algorithms selected by the combination of the voicing decisions of the
        // current and previous frames for each harmonic.
        boolean exceedsThreshold = Math.abs(currentFrequency - previousFrequency) >= (0.1 * currentFrequency);

        double previousPhase, currentPhase, ol, wl, phase, previousPhaseRotation, currentPhaseRotation, phaseCurvature;
        float amplitude, previousWindow, currentWindow, interpolation;
        int currentN;

        for(int n = 0; n < N_SAMPLES_PER_FRAME; n++)
        {
            currentN = n - N_SAMPLES_PER_FRAME;
            previousWindow = synthesisWindow(n);
            currentWindow = synthesisWindow(currentN);
            interpolation = (float)n / (float) N_SAMPLES_PER_FRAME;
            previousPhaseRotation = previousFrequency * n;
            currentPhaseRotation = currentFrequency * currentN;
            phaseCurvature = (currentFrequency - previousFrequency) * (n * n / 320.0);

            for(int l = 1; l <= maxL; l++)
            {
                if(currentVoicing[l] && previousVoicing[l])
                {
                    if(l >= 8 || exceedsThreshold)
                    {
                        //Alg #133
                        previousPhase = mPreviousPhaseO[l] + (previousPhaseRotation * l);
                        mVoiced[n] += 2.0f * (previousWindow * previousM[l] * (float)Math.cos(previousPhase));

                        currentPhase = mCurrentPhaseO[l] + (currentPhaseRotation * l);
                        mVoiced[n] += 2.0f * (currentWindow * currentM[l] * (float)Math.cos(currentPhase));
                    }
                    else
                    {
                        //Alg #135 - amplitude function
                        //Performs linear interpolation of the harmonic's amplitude from previous frame to current
                        amplitude = previousM[l] + (interpolation * (currentM[l] - previousM[l]));

                        //Alg #137
                        ol = (mCurrentPhaseO[l] - mPreviousPhaseO[l] - (phaseRotationPerFrame * l));

                        //Alg #138
                        wl = (ol - (TWO_PI * Math.floor((ol + Math.PI) / TWO_PI))) / 160.0;

                        //Alg #136 - phase function
                        phase = mPreviousPhaseO[l] + ((previousPhaseRotation * l) + (wl * n)) + (phaseCurvature * l);

                        //Alg #134
                        mVoiced[n] += 2.0f * (amplitude * (float)Math.cos(phase));
                    }
                }
                else if(!currentVoicing[l] && previousVoicing[l])
                {
                    //Alg #131
                    mVoiced[n] += 2.0f * (previousWindow * previousM[l] *
                        (float)Math.cos(mPreviousPhaseO[l] + (previousPhaseRotation * l)));
                }
                else if(currentVoicing[l] && !previousVoicing[l])
                {

                    //Alg #132
                    mVoiced[n] += 2.0f * (currentWindow * currentM[l] *
                        (float)Math.cos(mCurrentPhaseO[l] + (currentPhaseRotation * l)));
                }

                //Alg #130 - harmonics that are unvoiced in both the current and previous frames contribute nothing
            }
        }

        double[] previousPhaseV = mPreviousPhaseV;
        double[] previousPhaseO = mPreviousPhaseO;
        mPreviousPhaseO = mCurrentPhaseO;
        mPreviousPhaseV = mCurrentPhaseV;
        //Note: not sure why we're doing this, but when we don't, it causes bad audio artifacts.
        mCurrentPhaseV = previousPhaseV;
        mCurrentPhaseO = previousPhaseO;

        return mVoiced;
    }
}
