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

import org.jtransforms.fft.FloatFFT_1D;

import java.util.Random;

/**
 * Base Multi-Band Excitation (MBE) synthesizer
 */
public abstract class MBESynthesizer
{
    private static final float TWO_PI = (float)Math.PI * 2.0f;
    private static final float TWO56_OVER_TWO_PI = 256.0f / TWO_PI;
    private static final float AUDIO_SCALAR = 1.0f / 32768.0f;
    protected static final int SAMPLES_PER_FRAME = 160;

    /* Algorithm 121 - create scaling coefficient (yw) from synthesis window (ws)
     * and the initial pitch refinement window (wr)
     *
     *   sum_wr(n) = 110.01987200000003
     *   sum_wr(n)squared = 80.683623293024
     *   sum_ws(n)squared = 143.33999999999997
     *   yw = sum_wr(n) * sqrt(sum_ws(n)squared / sum_wr(n)squared)*/
    private static final float UNVOICED_SCALING_COEFFICIENT = 146.64327084433555f;

    /**
     * Synthesis window coefficients from Annex I for indices -104 <> 104</>
     *
     * Use synthesisWindow(n) method when accessing elements
     */
    private static final float[] WS = new float[]{0.02f, 0.04f, 0.06f, 0.08f, 0.10f, 0.12f, 0.14f, 0.16f, 0.18f, 0.20f,
        0.22f, 0.24f, 0.26f, 0.28f, 0.30f, 0.32f, 0.34f, 0.36f, 0.38f, 0.40f, 0.42f, 0.44f, 0.46f, 0.48f, 0.50f, 0.52f,
        0.54f, 0.56f, 0.58f, 0.60f, 0.62f, 0.64f, 0.66f, 0.68f, 0.70f, 0.72f, 0.74f, 0.76f, 0.78f, 0.80f, 0.82f, 0.84f,
        0.86f, 0.88f, 0.90f, 0.92f, 0.94f, 0.96f, 0.98f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.98f, 0.96f, 0.94f, 0.92f, 0.90f, 0.88f,
        0.86f, 0.84f, 0.82f, 0.80f, 0.78f, 0.76f, 0.74f, 0.72f, 0.70f, 0.68f, 0.66f, 0.64f, 0.62f, 0.60f, 0.58f, 0.56f,
        0.54f, 0.52f, 0.50f, 0.48f, 0.46f, 0.44f, 0.42f, 0.40f, 0.38f, 0.36f, 0.34f, 0.32f, 0.30f, 0.30f, 0.28f, 0.26f,
        0.24f, 0.22f, 0.20f, 0.18f, 0.16f, 0.14f, 0.12f, 0.10f, 0.08f, 0.06f, 0.04f, 0.02f};
    private WhiteNoiseGenerator mWhiteNoiseGenerator = new WhiteNoiseGenerator();
    private RandomTwoPiGenerator mRandom2PI = new RandomTwoPiGenerator();
    private FloatFFT_1D mFFT = new FloatFFT_1D(256);
    private float[] mPreviousPhaseO = new float[57];
    private float[] mPreviousPhaseV = new float[57];
    private float[] mPreviousUw = new float[256];

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
     * Returns the speech synthesis window coefficient for indices in the
     * range of -160 to 160 using a portion of the window from appendix I
     */
    public static float synthesisWindow(int n)
    {
        if(n < -104 || n > 104)
        {
            return 0.0f;
        }

        return WS[n + 104];
    }

    /**
     * Unused.  Was previously used to develop value for constant UNVOICED_SCALING_COEFFICIENT
     */
    public static float getUnvoicedScalingCoefficient()
    {
        float sum_wr = 110.01987200000003f;
        float sum_wr_squared = 80.683623293024f;
        float sum_ws_squared = 0.0f;

        for(int x = -104; x < 105; x++)
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
        float[] unvoiced = getUnvoiced(parameters);
        float[] voiced = getVoiced(parameters);

        float[] audio = new float[160];

        //Alg #142 - combine voiced and unvoiced audio samples to form the completed audio samples.
        for(int x = 0; x < 160; x++)
        {
            audio[x] = ((voiced[x] + unvoiced[x]) * AUDIO_SCALAR);
        }

        return audio;
    }

    /**
     * Generates 160 samples (20 ms) of white noise
     *
     * @return samples
     */
    public float[] getWhiteNoise()
    {
        return mWhiteNoiseGenerator.getSamples(160, 0.003f);
    }

    /**
     * Applies the synthesis window to the 256-element white noise array by considering the samples of the array to
     * be indexed as -128 <> 127
     * @param whiteNoise samples to window
     * @return windowed white noise samples
     */
    private float[] applyWindow(float[] whiteNoise)
    {
        for(int x = 0; x < whiteNoise.length; x++)
        {
            whiteNoise[x] *= synthesisWindow(x - 128);
        }

        return whiteNoise;
    }

    /**
     * Generates the unvoiced component of the audio signal using a white noise
     * generator where the frequency components corresponding to the voiced
     * harmonics are removed from the white noise.
     *
     * @param parameters from the voice frame
     * @return - 160 samples of unvoiced audio component
     */
    public float[] getUnvoiced(MBEModelParameters parameters)
    {
        //Alg #117 - generate white noise samples.  The original noise sequence generator produced samples in the
        //range 0<>53125 and relied on the DFT to remove the DC offset and yield samples in range -26562.5<>26562.5,
        //attenuated by the enhanced spectral amplitudes specified for each of the L bands.  We'll replace it with
        //a more natural sounding noise generator with samples in range -1.0<>1.0 amplified to a gain of 26563.
        float[] Uw = mWhiteNoiseGenerator.nextBuffer();

        Uw = applyWindow(Uw);

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
        float[] dftBinScalor = new float[128];

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

                float scalor = UNVOICED_SCALING_COEFFICIENT * M[l] / (float)Math.sqrt((numerator / denominator));

                for(int n = a_min[l]; n < b_max[l]; n++)
                {
                    if(n < 128)
                    {
                        dftBinScalor[n] = scalor;
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

            Uw[dftBinIndex] *= dftBinScalor[bin];
            Uw[dftBinIndex + 1] *= dftBinScalor[bin];
        }

        //Alg #125 - calculate inverse DFT of scaled dft bins to recreate the white noise, notched for voiced bands
        mFFT.realInverse(Uw, true);

        //Note: from this point forward, Uw contains the inverse DFT results

        /* Algorithm #126 - use Weighted Overlap Add algorithm to combine previous
         * Uw and the current Uw inverse DFT results to form final unvoiced set */
        float[] unvoiced = new float[SAMPLES_PER_FRAME];

        for(int n = 0; n < SAMPLES_PER_FRAME; n++)
        {
            float previousWindow = (n < 105 ? WS[n + 104] : 0.0f); //n
            float currentWindow = (n >= 56 ? WS[n - 56] : 0.0f); //n - N

            float previousUw = (n < 128 ? mPreviousUw[n + 128] : 0.0f); //n
            float currentUw = (n >= 32 ? Uw[n - 32] : 0.0f);  //n - N

            unvoiced[n] = ((previousWindow * previousUw) + (currentWindow * currentUw)) /
                ((previousWindow * previousWindow) + (currentWindow * currentWindow));
        }

        mPreviousUw = Uw;

        return unvoiced;
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
        //Short circuit if there are no voiced bands and return an array of zeros
        if(!getPreviousFrame().hasVoicedBands() && !currentFrame.hasVoicedBands())
        {
            return new float[160];
        }

        int currentL = currentFrame.getL();
        int previousL = getPreviousFrame().getL();
        int maxL = Math.max(currentL, previousL);

        boolean[] currentVoicing = resize(currentFrame.getVoicingDecisions(), maxL + 1);
        boolean[] previousVoicing = resize(getPreviousFrame().getVoicingDecisions(), maxL + 1);

        //Alg #128 & #129 - enhanced spectral amplitudes for current and previous frames outside range of 1 - L are set
        // to zero.  Below, in the audio generation loop, we control access to these arrays through the voicing
        // decisions array.  Thus, we don't have to resize the enhanced spectral amplitudes arrays to the max L of
        // current or previous.
        float currentFrequency = currentFrame.getFundamentalFrequency();
        float previousFrequency = getPreviousFrame().getFundamentalFrequency();

        //Alg #140 partial - number of unvoiced spectral amplitudes (Luv) in current frame */
        int unvoicedBandCount = 0;

        for(int x = 1; x <= currentL; x++)
        {
            if(!currentVoicing[x])
            {
                unvoicedBandCount++;
            }
        }

        //Alg #139 - calculate current phase angle for each harmonic
        float[] currentPhaseV = new float[57];
        float[] currentPhaseO = new float[57];
        float summedFrequencies = (previousFrequency + currentFrequency) * 80.0f; //160.0 / 2.0;
        int threshold = (int)Math.floor((float)currentL / 4.0f);

        for(int l = 1; l <= 56; l++)
        {
            //Alg #139 - calculate current phase v values
            currentPhaseV[l] = mPreviousPhaseV[l] + (summedFrequencies * (float)l);

            //Alg #140 - calculate current phase o values
            if(l <= threshold)
            {
                currentPhaseO[l] = currentPhaseV[l];
            }
            else if(l <= maxL)
            {
                //Alg #141 - replaced with internal pi random generator
                currentPhaseO[l] = currentPhaseV[l] + (((float)unvoicedBandCount * mRandom2PI.next()) / (float)currentL);
            }
        }

        float[] currentM = currentFrame.getEnhancedSpectralAmplitudes();
        float[] previousM = getPreviousFrame().getEnhancedSpectralAmplitudes();
        float[] voiced = new float[SAMPLES_PER_FRAME];

        //Alg #127 - reconstruct 160 voice samples using each of the l harmonics that are common between this frame and
        // the previous frame, using one of four algorithms selected by the combination of the voicing decisions of the
        // current and previous frames for each harmonic.
        boolean exceedsThreshold = Math.abs(currentFrequency - previousFrequency) >= (0.1 * currentFrequency);

        for(int n = 0; n < SAMPLES_PER_FRAME; n++)
        {
            for(int l = 1; l <= maxL; l++)
            {
                if(currentVoicing[l] && previousVoicing[l])
                {
                    if(l >= 8 || exceedsThreshold)
                    {
                        //Alg #133
                        voiced[n] += (synthesisWindow(n) * previousM[l] *
                            Math.cos((previousFrequency * (float)n * (float)l) + mPreviousPhaseO[l]) +
                            (synthesisWindow(n - SAMPLES_PER_FRAME) * currentM[l] *
                                Math.cos((currentFrequency * (float)(n - SAMPLES_PER_FRAME) * (float)l) + currentPhaseO[l])));
                    }
                    else
                    {
                        //Alg #135 - amplitude function
                        float amplitude = previousM[l] + (((float)n / (float)SAMPLES_PER_FRAME) * (currentM[l] - previousM[l]));

                        //Alg #137
                        float ol = (currentPhaseO[l] - mPreviousPhaseO[l] - (summedFrequencies * (float)l));

                        //Alg #138
                        float wl = (ol - (TWO_PI * (float)Math.floor((ol + (float)Math.PI) / TWO_PI))) / 160.0f;

                        //Alg #136 - phase function
                        float phase = mPreviousPhaseO[l] +
                            (((previousFrequency * (float)l) + wl) * (float)n) +
                            ((currentFrequency - previousFrequency) * ((float)(l *  n * n) / 320.0f));

                        //Alg #134
                        voiced[n] += (amplitude * Math.cos(phase));
                    }
                }
                else if(!currentVoicing[l] && previousVoicing[l])
                {
                    //Alg #131
                    voiced[n] += (synthesisWindow(n) * previousM[l] *
                        (float)Math.cos((previousFrequency * (float)n * (float)l) + mPreviousPhaseO[l]));
                }
                else if(currentVoicing[l] && !previousVoicing[l])
                {
                    //Alg #132
                    voiced[n] += (synthesisWindow(n - SAMPLES_PER_FRAME) * currentM[l] *
                        (float)Math.cos((currentFrequency * (float)(n - SAMPLES_PER_FRAME) * (float)l) + currentPhaseO[l]));
                }

                //Alg #130 - harmonics that are unvoiced in both the current and previous frames contribute nothing
            }

            //Algorithm #127
            voiced[n] *= 2.0f;
        }

        mPreviousPhaseV = currentPhaseV;
        mPreviousPhaseO = currentPhaseO;

        return voiced;
    }

    /**
     * Generates random numbers in the range -PI <> PI
     */
    public class RandomTwoPiGenerator
    {
        private Random mRandom = new Random();

        public float next()
        {
            /* generate random number between 0 and 1 */
            float random = mRandom.nextFloat();

            /* scale it to the range of 0 <> 2 * PI */
            random *= MBESynthesizer.TWO_PI;

            /* shift it down to the range of -PI <> PI */
            random -= Math.PI;

            return random;
        }
    }
}
