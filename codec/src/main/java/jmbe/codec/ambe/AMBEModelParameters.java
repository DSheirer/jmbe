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

package jmbe.codec.ambe;

import jmbe.codec.FrameType;
import jmbe.codec.MBEModelParameters;
import jmbe.codec.ambe.ambePlus2.DifferentialGain;
import jmbe.codec.ambe.ambePlus2.FundamentalFrequency;
import jmbe.codec.ambe.ambePlus2.HOCB5;
import jmbe.codec.ambe.ambePlus2.HOCB6;
import jmbe.codec.ambe.ambePlus2.HOCB7;
import jmbe.codec.ambe.ambePlus2.HOCB8;
import jmbe.codec.ambe.ambePlus2.VoicingDecision;
import jmbe.codec.ambe.ambePlus2.LMPRBlockLength;
import jmbe.codec.ambe.ambePlus2.PRBA24;
import jmbe.codec.ambe.ambePlus2.PRBA58;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMBE frame voice model parameters
 */
public class AMBEModelParameters extends MBEModelParameters
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBEModelParameters.class);

    private static final float ONE_OVER_TWO_SQR_TWO = 1.0f / (2.0f * (float)Math.sqrt(2.0f));
    private static final float TWO_PI = 2.0f * (float)Math.PI;
    public static final float RHO = 0.642f; //Prediction residual - ICD uses .65
    private float mGain;
    public int[] mB;
    /** The AMBE-3000R's bit error rate average (AMBEChipResponse.chipErrorRate). */
    private float mChipErrorRate;
    /** Comfort-noise frames only: the frame whose gain and log2 amplitudes the following frame predicts from. */
    private AMBEModelParameters mPredictionMemory;
    /** Tracked pitch for AMBEChipResponse (Hz): the previous frame's until this frame's amplitudes are set. */
    private float mTrackedPitchHz = AMBEChipResponse.TRACKED_PITCH_RESET_HZ;

    /**
     * Creates a default set of model parameters to be used as an initial frame
     */
    public AMBEModelParameters()
    {
        super(FundamentalFrequency.W124);
        setDefaults(FrameType.VOICE);
    }

    /** Bare parameters for a pitch value, decoded by the caller (decodeMemory). */
    private AMBEModelParameters(FundamentalFrequency fundamental)
    {
        super(fundamental);
    }

    /**
     * Constructs model parameters for frame type VOICE or SILENCE
     */
    public AMBEModelParameters(FundamentalFrequency fundamental, int[] b, int[] errors, AMBEModelParameters previous)
    {
        super(fundamental);

        mB = b;
        mTrackedPitchHz = previous.getTrackedPitchHz();

        //Alg 55 & 56
        setErrorCountTotal(errors[0] + errors[1]);
        setErrorRate((0.95f * previous.getErrorRate()) + (0.001064f * getErrorCountTotal()));
        mChipErrorRate = AMBEChipResponse.chipErrorRate(previous.getChipErrorRate(), getErrorCountTotal());

        //Prediction memory: the previous frame, or in chip mode the frame before a comfort-noise run
        AMBEModelParameters memory = AMBEChipResponse.isEnabled() ? previous.predictionMemory() : previous;

        if(AMBEChipResponse.isEnabled())
        {
            if(AMBEChipResponse.isComfortNoiseFrame(fundamental, b, errors, mChipErrorRate))
            {
                //Decoded as usual (all unvoiced) against the memory, which it leaves unchanged for the next frame
                mPredictionMemory = memory;
            }
            else if(AMBEChipResponse.isInvalidFrame(fundamental, errors, mChipErrorRate))
            {
                setChipRepeat(previous, mChipErrorRate > AMBEChipResponse.MUTE_ERROR_RATE,
                    errors[0] >= 4 ? decodeMemory(fundamental, b, memory) : null);
                return;
            }
        }
        //Alg 57 & 58 determine if this should be a frame repeat due to excessive errors or ERASURE frame type
        else if(fundamental.getFrameType() == FrameType.ERASURE)
        {
            setDefaults(FrameType.ERASURE);
            return;
        }

        if(!AMBEChipResponse.isEnabled() && ((errors[0] >= 4) || (errors[0] >= 2 && getErrorCountTotal() >= 6)))
        {
            //Alg 59-64
            setRepeatCount(previous.getRepeatCount() + 1);
            setMBEFundamentalFrequency(previous.getAMBEFundamentalFrequency());
            mGain = previous.getGain();
            setVoicingDecisions(previous.getVoicingDecisions());
            setLog2SpectralAmplitudes(previous.getLog2SpectralAmplitudes());
            setSpectralAmplitudes(previous.getSpectralAmplitudes(), previous.getLocalEnergy(), previous.getAmplitudeThreshold());
            setLocalEnergy(previous.getLocalEnergy());
        }
        else
        {
            if(fundamental.getFrameType() == FrameType.VOICE)
            {
                setVoicingDecisions(b[1]);
            }
            else //Silence frame
            {
                setVoicingDecisions(new boolean[getL() + 1]);
            }

            setGain(b[2], memory);
            decodePRBAVector(b[3], b[4], b[5], b[6], b[7], b[8], memory);

            if(mPredictionMemory != null)
            {
                float scale = (float)Math.pow(10.0, AMBEChipResponse.COMFORT_NOISE_DB / 20.0);
                float[] enhanced = getEnhancedSpectralAmplitudes();
                for(int l = 1; l < enhanced.length; l++)
                {
                    enhanced[l] *= scale;
                }
            }
        }
    }

    /**
     * The frame the next frame's gain and log2 amplitudes are predicted from: this one, or for a comfort-noise frame
     * (AMBEChipResponse.isComfortNoiseFrame) the frame before the comfort-noise run.
     */
    AMBEModelParameters predictionMemory()
    {
        return mPredictionMemory != null ? mPredictionMemory : this;
    }

    /**
     * Sets the spectral amplitudes and computes the enhanced amplitudes (base class), then applies the AMBE-3000R
     * chip response to the enhanced amplitudes (see AMBEChipResponse; off restores the published-algorithm decode).
     */
    @Override
    public void setSpectralAmplitudes(float[] spectralAmplitudes, float previousLocalEnergy, int previousAmplitudeThreshold)
    {
        super.setSpectralAmplitudes(spectralAmplitudes, previousLocalEnergy, previousAmplitudeThreshold);
        float f0 = (float)(getFundamentalFrequency() * 8000.0 / (2.0 * Math.PI));
        if(!AMBEChipResponse.isEnabled() || hasVoicedBands())
        {
            //The AMBE-3000R's pitch tracker holds through frames with no voiced harmonic (noise probe)
            mTrackedPitchHz = AMBEChipResponse.trackPitch(mTrackedPitchHz, f0);
        }
        AMBEChipResponse.apply(getEnhancedSpectralAmplitudes(), getFundamentalFrequency(), mTrackedPitchHz,
            getVoicingDecisions(), mB != null ? mB[1] : 0);
    }

    /**
     * With AMBEChipResponse enabled, the amplitude threshold (Alg #114-116) measures unvoiced harmonics as if voiced
     * (without the unvoiced scaling coefficient), as the AMBE-3000R does: at the top gains, where the threshold limits,
     * the chip's noise frames are limited by the same factor as voiced ones (noise probe: the published measure left
     * JMBE's noise 4.4 dB louder relative to voiced at b2 31, the chip's unchanged).
     */
    @Override
    protected float getAmplitudeMeasureWeight(int l)
    {
        boolean[] voiced = getVoicingDecisions();

        if(AMBEChipResponse.isEnabled() && voiced != null && l < voiced.length && !voiced[l])
        {
            return (float)Math.sqrt(getFundamentalFrequency()) / 0.2046f;
        }

        return 1.0f;
    }

    /**
     * The AMBE-3000R's handling of an invalid frame (bit error probe): the previous frame's parameters are repeated,
     * its final (enhanced, chip-shaped) amplitudes played again with the voice-path shelf applied once more (so the
     * top band grows ~1.5..3.5 dB per repeat), and the prediction memory the next frame decodes against is cleared
     * as AMBEChipResponse.repeatMemory() says. From the fourth repeat on, or at once when the error rate is past
     * AMBEChipResponse.MUTE_ERROR_RATE, the frame is muted (silence, no comfort noise) and stays muted while invalid
     * frames continue.
     *
     * @param previous frame
     * @param errorRateMute true to mute at once (error rate past the mute threshold)
     */
    private void setChipRepeat(AMBEModelParameters previous, boolean errorRateMute, AMBEModelParameters memorySource)
    {
        setRepeatCount(errorRateMute ? Math.max(previous.getRepeatCount() + 1, 4) : previous.getRepeatCount() + 1);
        setMBEFundamentalFrequency(previous.getAMBEFundamentalFrequency());
        setVoicingDecisions(previous.getVoicingDecisions().clone());
        mTrackedPitchHz = previous.getTrackedPitchHz();
        setLocalEnergy(previous.getLocalEnergy());
        setAmplitudeThreshold(previous.getAmplitudeThreshold());

        int memory = AMBEChipResponse.repeatMemory();
        float[] log2 = previous.getLog2SpectralAmplitudes().clone();
        mGain = previous.getGain();
        if(memorySource == null && previous.mPredictionMemory != null)
        {
            //Repeating comfort noise: the memory stays at the frame before the comfort-noise run
            mPredictionMemory = previous.mPredictionMemory;
        }
        if(memory == 4 && memorySource != null)
        {
            //The chip decodes the invalid frame's (uncorrected) bits into the prediction memory while it plays the
            //repeat: resample that frame's log2 amplitudes onto this frame's L
            mGain = memorySource.getGain();
            float[] src = memorySource.getLog2SpectralAmplitudes();
            int srcL = src.length - 1;
            for(int l = 1; l < log2.length; l++)
            {
                int k = Math.min(srcL, Math.max(1, Math.round((float)l * srcL / (log2.length - 1))));
                log2[l] = src[k];
            }
        }
        else if(memory != 4 && memorySource != null)
        {
            if((memory & 1) != 0)
            {
                mGain = 0.0f;
            }
            if((memory & 2) != 0)
            {
                java.util.Arrays.fill(log2, 0.0f);
            }
        }
        setLog2SpectralAmplitudes(log2);
        mSpectralAmplitudes = previous.getSpectralAmplitudes().clone();

        float[] enhanced = previous.getEnhancedSpectralAmplitudes().clone();
        if(isMaxFrameRepeat())
        {
            java.util.Arrays.fill(enhanced, 0.0f);
        }
        else
        {
            AMBEChipResponse.applyShelf(enhanced, getFundamentalFrequency());
        }
        setEnhancedSpectralAmplitudes(enhanced);
    }

    /**
     * Decodes a frame's quantizer values as if valid, for the prediction memory only (see setChipRepeat), or null when
     * its pitch value is not a voice or silence one.
     */
    private static AMBEModelParameters decodeMemory(FundamentalFrequency fundamental, int[] b,
                                                    AMBEModelParameters previous)
    {
        if(b == null || (fundamental.getFrameType() != FrameType.VOICE && fundamental.getFrameType() != FrameType.SILENCE))
        {
            return null;
        }
        AMBEModelParameters m = new AMBEModelParameters(fundamental);
        m.mB = b;
        if(fundamental.getFrameType() == FrameType.VOICE)
        {
            m.setVoicingDecisions(b[1]);
        }
        else
        {
            m.setVoicingDecisions(new boolean[m.getL() + 1]);
        }
        m.setGain(b[2], previous);
        m.decodePRBAVector(b[3], b[4], b[5], b[6], b[7], b[8], previous);
        return m;
    }

    /** The AMBE-3000R's bit error rate average after this frame (see AMBEChipResponse.chipErrorRate). */
    public float getChipErrorRate()
    {
        return mChipErrorRate;
    }

    /** Pitch tracked by the AMBE-3000R for its low-harmonic attenuation (see AMBEChipResponse), Hz. */
    public float getTrackedPitchHz()
    {
        return mTrackedPitchHz;
    }

    /**
     * Sets default parameters for the frame type
     * @param frameType to set
     */
    private void setDefaults(FrameType frameType)
    {
        setFrameType(frameType);

        setVoicingDecisions(new boolean[getL() + 1]);
        float[] log2SpectralAmplitudes = new float[getL() + 1];
        setLog2SpectralAmplitudes(log2SpectralAmplitudes);
        mSpectralAmplitudes = new float[getL() + 1];

        for(int l = 0; l < mSpectralAmplitudes.length; l++)
        {
            mSpectralAmplitudes[l] = 1.0f;
        }

        mEnhancedSpectralAmplitudes = mSpectralAmplitudes;

        mGain = 0.0f;
    }

    /**
     * AMBE fundamental frequency enumeration value
     * @return fundamental frequency
     */
    public FundamentalFrequency getAMBEFundamentalFrequency()
    {
        return (FundamentalFrequency)getMBEFundamentalFrequency();
    }

    /**
     * Indicates if this is an ERASURE frame type.
     */
    public boolean isErasureFrame()
    {
        return getFrameType() == FrameType.ERASURE;
    }

    /**
     * Indicates if this frame should be muted (ie replaced with comfort noise) due to excessive errors or prolonged
     * frame repeats.
     */
    public boolean isFrameMuted()
    {
        return getErrorRate() > 0.096 || getRepeatCount() >= 4;
    }

    /**
     * Sets the voiced/not voiced frequency band decisions based on the value of the b1 parameter.
     *
     * A harmonic at f Hz is voiced when the 500 Hz band holding f is voiced, or the band holding f + 250 Hz is: a
     * voiced band also voices the upper half of an unvoiced band below it, while a voiced-to-unvoiced edge going up
     * stays on the band edge. This is the AMBE-3000R's mapping, measured with the voicing probe (an unvoiced band
     * between voiced bands is noise from its lower edge to its middle only, at every pitch from 72 to 295 Hz, so the
     * shift is in frequency, not harmonics). AMBEChipResponse.setEnabled(false) restores the published mapping
     * (the band holding f only).
     *
     * @param b1 parameter
     */
    private void setVoicingDecisions(int b1)
    {
        VoicingDecision voicingDecision = VoicingDecision.fromValue(b1);

        boolean[] voicingDecisions = new boolean[getL() + 1];
        boolean halfBand = AMBEChipResponse.isEnabled();

        for(int l = 1; l <= getL(); l++)
        {
            double band = l * getFundamentalFrequency() * 16 / TWO_PI; //harmonic frequency / 500 Hz
            int voiceIndex = Math.min((int)band, 7);
            boolean voiced = voicingDecision.isVoiced(voiceIndex);

            if(halfBand && !voiced)
            {
                int upperIndex = (int)(band + 0.5);

                if(upperIndex <= 7)
                {
                    voiced = voicingDecision.isVoiced(upperIndex);
                }
            }

            voicingDecisions[l] = voiced;
        }

        setVoicingDecisions(voicingDecisions);
    }

    /**
     * Gain level for this frame
     */
    public float getGain()
    {
        return mGain;
    }

    /**
     * Decodes the differential gain level for this frame
     */
    private void setGain(int b2, AMBEModelParameters previousFrame)
    {
        //Alg 26
        mGain = DifferentialGain.fromValue(b2).getGain() + (0.5f * previousFrame.getGain());
    }

    /**
     * Decodes the predictive residual block average (PRBA) vectors for the current frame
     */
    private void decodePRBAVector(int b3, int b4, int b5, int b6, int b7, int b8, AMBEModelParameters previousParameters)
    {
        float[] G = new float[9];
        G[1] = 0.0f;

        try
        {
            PRBA24 prba24 = PRBA24.fromValue(b3);
            G[2] = prba24.getG2();
            G[3] = prba24.getG3();
            G[4] = prba24.getG4();
        }
        catch(Exception e)
        {
            mLog.error("Unable to getAudio PRBA 2-4 vector from value B3[" + b3 + "]");
        }

        try
        {
            PRBA58 prba58 = PRBA58.fromValue(b4);
            G[5] = prba58.getG5();
            G[6] = prba58.getG6();
            G[7] = prba58.getG7();
            G[8] = prba58.getG8();
        }
        catch(Exception e)
        {
            mLog.error("Unable to getAudio PRBA 5-8 vector from value B4[" + b4 + "]");
        }

        float[] R = new float[9];

        //Alg 27 & 28. Inverse DCT of G[]
        for(int i = 1; i <= 8; i++)
        {
            R[i] = G[1];

            for(int m = 2; m <= 8; m++)
            {
                R[i] += (2.0f * G[m] * (float)Math.cos(((float)Math.PI * (float)(m - 1) * ((float)i - 0.5f)) / 8.0f));
            }
        }

        float[][] C = new float[5][18];

        //Alg 29,31,33,35
        C[1][1] = 0.5f * (R[1] + R[2]);
        C[2][1] = 0.5f * (R[3] + R[4]);
        C[3][1] = 0.5f * (R[5] + R[6]);
        C[4][1] = 0.5f * (R[7] + R[8]);

        //Alg 30,32,34,36
        C[1][2] = ONE_OVER_TWO_SQR_TWO * (R[1] - R[2]);
        C[2][2] = ONE_OVER_TWO_SQR_TWO * (R[3] - R[4]);
        C[3][2] = ONE_OVER_TWO_SQR_TWO * (R[5] - R[6]);
        C[4][2] = ONE_OVER_TWO_SQR_TWO * (R[7] - R[8]);

        int[] J = LMPRBlockLength.fromValue(getL()).getBlockLengths();

        float[] coefficients = null;

        //Alg 37
        for(int i = 1; i <= 4; i++)
        {
            if(J[i] > 2)
            {
                switch(i)
                {
                    case 1:
                        coefficients = HOCB5.fromValue(b5).getCoefficients();
                        break;
                    case 2:
                        coefficients = HOCB6.fromValue(b6).getCoefficients();
                        break;
                    case 3:
                        coefficients = HOCB7.fromValue(b7).getCoefficients();
                        break;
                    case 4:
                        coefficients = HOCB8.fromValue(b8).getCoefficients();
                        break;
                }

                switch(J[i])
                {
                    case 3:
                        C[i][3] = coefficients[0];
                        break;
                    case 4:
                        C[i][3] = coefficients[0];
                        C[i][4] = coefficients[1];
                        break;
                    case 5:
                        C[i][3] = coefficients[0];
                        C[i][4] = coefficients[1];
                        C[i][5] = coefficients[2];
                        break;
                    default:
                        C[i][3] = coefficients[0];
                        C[i][4] = coefficients[1];
                        C[i][5] = coefficients[2];
                        C[i][6] = coefficients[3];
                        break;
                }
            }
        }

        //Alg 38, 39. Inverse DCT of C to produce c(i,k) which is rearranged as T
        float[] T = new float[getL() + 1];

        int lPointer = 1;

        for(int i = 1; i <= 4; i++)
        {
            for(int j = 1; j <= J[i]; j++)
            {
                float acc = C[i][1];

                for(int k = 2; k <= J[i]; k++)
                {
                    acc += 2.0f * C[i][k] *
                        (float)Math.cos(((float)Math.PI * (float)(k - 1) * ((float)j - 0.5f)) / (float)J[i]);
                }

                T[lPointer++] = acc;
            }
        }

        int previousL = previousParameters.getL();

        //Alg 40 & 41
        float kappa = (float)previousL / (float)getL();

        float[] k = new float[getL() + 1];
        int[] kFloor = new int[getL() + 1];
        float[] s = new float[getL() + 1];

        float[] previousA = previousParameters.getLog2SpectralAmplitudes();

        //Alg 44
        previousA[0] = previousA[1];

        for(int l = 1; l <= getL(); l++)
        {
            k[l] = kappa * (float)l;
            kFloor[l] = (int)Math.floor(k[l]);
            s[l] = k[l] - kFloor[l];
        }

        //Alg 42 & 43 - pre-compute sum
        float summation43 = 0.0f;
        float lambdaSum = 0.0f;

        for(int l = 1; l <= getL(); l++)
        {
            float aklPrevious = kFloor[l] <= previousL ? previousA[kFloor[l]] : previousA[previousL];
//            int plus1 = l < getL() ? l + 1 : getL();
//            float aklPlus1Previous = kFloor[plus1] <= previousL ? previousA[kFloor[plus1]] : previousA[previousL];
            float aklPlus1Previous = previousA[Math.min(kFloor[l] + 1, previousL)];

            summation43 += (((1.0f - s[l]) * aklPrevious) + (s[l] * aklPlus1Previous));
            lambdaSum += T[l];
        }

        lambdaSum /= (float)getL();

        //Alg 42
        float gain = mGain - (0.5f * (float)(Math.log(getL()) / Math.log(2.0))) - lambdaSum;

        //Log Spectral Amplitudes
        float[] logSpectralAmplitudes = new float[getL() + 1];
        logSpectralAmplitudes[0] = 1.0f;

        //Spectral Amplitudes
        float[] spectralAmplitudes = new float[getL() + 1];

        boolean[] voicingDecisions = getVoicingDecisions();

        float aklPrevious;
        int lPlus1;
        float aklPlus1Previous;

        float rho = RHO;

        float unvoicedCoefficient = 0.2046f / (float)Math.sqrt(getFundamentalFrequency());

        summation43 *= (rho / (float)getL());

        for(int l = 1; l <= getL(); l++)
        {
            //Alg 44 & 45
            aklPrevious = (kFloor[l] == 0 ? previousA[1] : (kFloor[l] <= previousL ? previousA[kFloor[l]] : previousA[previousL]));
//            lPlus1 = l < getL() ? (l + 1) : getL();
//            aklPlus1Previous = ((kFloor[lPlus1]) <= previousL ? previousA[kFloor[lPlus1]] : previousA[previousL]);
            aklPlus1Previous = previousA[Math.min(kFloor[l] + 1, previousL)];

            //Alg 43
            logSpectralAmplitudes[l] = T[l] + (rho * (1.0f - s[l]) * aklPrevious)
                + (rho * s[l] * aklPlus1Previous)
                - summation43
                + gain;

            //Alg 46 - spectral magnitude is based on the (l) band's voicing decision
            if(voicingDecisions[l])
            {
                spectralAmplitudes[l] = (float)Math.exp(0.693f * logSpectralAmplitudes[l]);
            }
            else
            {
                spectralAmplitudes[l] = unvoicedCoefficient * (float)Math.exp(0.693f * logSpectralAmplitudes[l]);
            }
        }

        setLog2SpectralAmplitudes(logSpectralAmplitudes);
        setSpectralAmplitudes(spectralAmplitudes, previousParameters.getLocalEnergy(),
            previousParameters.getAmplitudeThreshold());
    }

    /**
     * Pretty output of this frame's parameters
     * @return frame output
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getFrameType()).append(" FRAME ");
        sb.append(" FUND:").append(getMBEFundamentalFrequency());
        sb.append(" HARM:").append(getL());
        sb.append(" ERRATE:").append(getErrorRate());
        sb.append(" GAIN:").append(mGain);

        return sb.toString();
    }
}
