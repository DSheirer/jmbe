/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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
 * ****************************************************************************
 */

package jmbe.codec;

import java.util.Arrays;
import org.apache.commons.math3.util.FastMath;

/**
 * Speech-gated automatic gain control.
 *
 * Each 20 ms frame is classified as speech or not-speech using the decoded MBE model parameters (frame type, repeat
 * and error status, voicing decisions and pitch continuity) together with the frame level relative to a tracked noise
 * floor.  Only speech frames update the speech level estimate, and the gain is derived from that estimate so that the
 * average speech level approaches a target level.  The gain is held during pauses and noise, so background noise is
 * not pumped up between words or before the first word of the call.  A per-frame peak limiter prevents the applied
 * gain from clipping speech peaks.
 *
 * Levels are tracked in dB relative to 16-bit full scale (dBFS) using the frame RMS measured before gain is applied.
 */
public class AGC
{
    private static final float MINIMUM_GAIN = 1.0f;
    private static final float DEFAULT_GAIN = 1.0f;
    private static final float MAXIMUM_GAIN = 30.0f;
    private static final float MAXIMUM_AMPLITUDE = Short.MAX_VALUE * 0.95f;

    /** Target (log-averaged) speech frame RMS level */
    private static final float TARGET_SPEECH_LEVEL_DB = -20.0f;
    /** Frames quieter than this are never treated as speech */
    private static final float MINIMUM_SPEECH_LEVEL_DB = -60.0f;
    /** Required level above the noise floor for a frame to be treated as speech */
    private static final float SPEECH_THRESHOLD_DB = 9.0f;
    /** Floor for any measured frame level, to avoid log of zero */
    private static final float MINIMUM_LEVEL_DB = -100.0f;

    /** Noise floor is a low percentile of the usable frame levels over this many frames (2 seconds) */
    private static final int NOISE_FLOOR_WINDOW_FRAMES = 100;
    /** Percentile of the window used as the noise floor, so that a few outlier frames (e.g. digital silence) are ignored */
    private static final float NOISE_FLOOR_PERCENTILE = 0.05f;
    /** Upper limit for the noise floor until the noise floor window has filled */
    private static final float NOISE_FLOOR_INITIAL_MAXIMUM_DB = -50.0f;

    /** Number of initial speech frames that use the fast speech level smoothing rate */
    private static final int FAST_CONVERGENCE_FRAMES = 5;
    private static final float SPEECH_LEVEL_FAST_RATE = 0.5f;
    /** Speech level smoothing when speech gets quieter (gain increases): ~1 second */
    private static final float SPEECH_LEVEL_RISE_GAIN_RATE = 0.02f;
    /** Speech level smoothing when speech gets louder (gain decreases) */
    private static final float SPEECH_LEVEL_REDUCE_GAIN_RATE = 0.03f;
    /** Speech level smoothing when speech is much louder than the current estimate */
    private static final float SPEECH_LEVEL_LOUD_RATE = 0.5f;
    private static final float SPEECH_LEVEL_LOUD_THRESHOLD_DB = 6.0f;

    /** Maximum number of bit errors in a frame for the frame to be used for level tracking */
    private static final int MAXIMUM_FRAME_ERRORS = 4;
    /** Minimum fraction of voiced bands in the lower half of the spectrum for a frame to be treated as speech */
    private static final float MINIMUM_VOICED_FRACTION = 0.3f;
    /** Maximum relative change in fundamental frequency between consecutive frames to be treated as speech */
    private static final float PITCH_CONTINUITY_TOLERANCE = 0.2f;

    private float mGain;
    private float mAppliedGain;
    private float mNoiseFloorDb;
    private float mSpeechLevelDb;
    private int mSpeechFrameCount;
    private float mPreviousFundamental;
    private final float[] mNoiseFloorWindow = new float[NOISE_FLOOR_WINDOW_FRAMES];
    private final float[] mNoiseFloorSorted = new float[NOISE_FLOOR_WINDOW_FRAMES];
    private int mNoiseFloorWindowCount;
    private boolean mSpeech;

    /**
     * Constructs an instance
     */
    public AGC()
    {
        reset();
    }

    /**
     * Resets this gain control to prepare for the next audio call/segment.
     */
    public void reset()
    {
        mGain = DEFAULT_GAIN;
        mAppliedGain = DEFAULT_GAIN;
        mNoiseFloorDb = NOISE_FLOOR_INITIAL_MAXIMUM_DB;
        mSpeechLevelDb = MINIMUM_LEVEL_DB;
        mSpeechFrameCount = 0;
        mPreviousFundamental = 0.0f;
        mNoiseFloorWindowCount = 0;
        mSpeech = false;
    }

    /**
     * Process a frame of audio samples and apply gain.
     * @param samples to adjust, scaled to 16-bit range.
     * @param parameters for the MBE frame that produced the samples.
     */
    public void process(float[] samples, MBEModelParameters parameters)
    {
        float peak = 0.0f;
        double sumSquared = 0.0;

        for(float sample: samples)
        {
            peak = FastMath.max(peak, Math.abs(sample));
            sumSquared += sample * sample;
        }

        float levelDb = toDb((float)Math.sqrt(sumSquared / samples.length));

        update(levelDb, parameters);

        //Limit the gain so that the frame peak stays within the maximum amplitude
        float limit = peak > 0.0f ? MAXIMUM_AMPLITUDE / peak : MAXIMUM_GAIN;
        float startGain = FastMath.min(mAppliedGain, limit);
        float endGain = FastMath.min(mGain, limit);

        //Interpolate the gain across the frame to avoid gain steps at frame boundaries
        float step = (endGain - startGain) / samples.length;

        for(int x = 0; x < samples.length; x++)
        {
            samples[x] *= startGain + (step * (x + 1));
            samples[x] = FastMath.min(samples[x], MAXIMUM_AMPLITUDE);
            samples[x] = FastMath.max(samples[x], -MAXIMUM_AMPLITUDE);
        }

        mAppliedGain = endGain;
    }

    /**
     * Classifies the frame and updates the noise floor, speech level and gain.
     * @param levelDb of the frame before gain is applied
     * @param parameters for the frame
     */
    private void update(float levelDb, MBEModelParameters parameters)
    {
        mSpeech = false;

        //Repeated, errored and non-voice frames don't tell us anything reliable about the speaker's level.
        if(!isUsable(parameters))
        {
            mPreviousFundamental = 0.0f;
            return;
        }

        updateNoiseFloor(levelDb);

        boolean voiced = isVoiced(parameters);
        boolean pitchContinuous = voiced && isPitchContinuous(parameters.getFundamentalFrequency());
        mPreviousFundamental = voiced ? parameters.getFundamentalFrequency() : 0.0f;

        mSpeech = pitchContinuous && levelDb >= MINIMUM_SPEECH_LEVEL_DB &&
            levelDb >= mNoiseFloorDb + SPEECH_THRESHOLD_DB;

        if(mSpeech)
        {
            updateSpeechLevel(levelDb);
            float gain = (float)FastMath.pow(10.0, (TARGET_SPEECH_LEVEL_DB - mSpeechLevelDb) / 20.0);
            mGain = FastMath.max(MINIMUM_GAIN, FastMath.min(MAXIMUM_GAIN, gain));
        }
    }

    /**
     * Updates the noise floor as a low percentile of the frame levels over the noise floor window (minimum
     * statistics).  Speech has pauses and quiet frames between syllables, so the low percentile tracks the background
     * level and isn't pulled up by loud voiced or unvoiced speech frames, while a few outlier frames such as digital
     * silence can't pull it down.  Until the window fills, the floor is capped so that a call that starts with speech
     * doesn't treat the speech level as the noise floor.
     */
    private void updateNoiseFloor(float levelDb)
    {
        mNoiseFloorWindow[mNoiseFloorWindowCount % NOISE_FLOOR_WINDOW_FRAMES] = levelDb;
        mNoiseFloorWindowCount++;

        int count = FastMath.min(mNoiseFloorWindowCount, NOISE_FLOOR_WINDOW_FRAMES);
        System.arraycopy(mNoiseFloorWindow, 0, mNoiseFloorSorted, 0, count);
        Arrays.sort(mNoiseFloorSorted, 0, count);
        float floor = mNoiseFloorSorted[(int)(count * NOISE_FLOOR_PERCENTILE)];

        if(mNoiseFloorWindowCount < NOISE_FLOOR_WINDOW_FRAMES)
        {
            floor = FastMath.min(floor, NOISE_FLOOR_INITIAL_MAXIMUM_DB);
        }

        mNoiseFloorDb = floor;
    }

    /**
     * Updates the speech level estimate from a speech frame level.
     */
    private void updateSpeechLevel(float levelDb)
    {
        if(mSpeechFrameCount == 0)
        {
            mSpeechLevelDb = levelDb;
        }
        else
        {
            float rate;

            //Speech much louder than the estimate (often a quiet word onset followed by the vowel) reduces gain quickly
            if(levelDb > mSpeechLevelDb + SPEECH_LEVEL_LOUD_THRESHOLD_DB)
            {
                rate = SPEECH_LEVEL_LOUD_RATE;
            }
            else if(mSpeechFrameCount < FAST_CONVERGENCE_FRAMES)
            {
                rate = SPEECH_LEVEL_FAST_RATE;
            }
            else if(levelDb > mSpeechLevelDb)
            {
                rate = SPEECH_LEVEL_REDUCE_GAIN_RATE;
            }
            else
            {
                rate = SPEECH_LEVEL_RISE_GAIN_RATE;
            }

            mSpeechLevelDb += (levelDb - mSpeechLevelDb) * rate;
        }

        mSpeechFrameCount++;
    }

    /**
     * Indicates if the frame is a voice frame that was decoded without repeats or excessive bit errors.
     */
    private static boolean isUsable(MBEModelParameters parameters)
    {
        return parameters.getFrameType() == FrameType.VOICE && !parameters.isRepeatFrame() &&
            parameters.getErrorCountTotal() <= MAXIMUM_FRAME_ERRORS;
    }

    /**
     * Indicates if enough of the bands in the lower half of the spectrum are voiced for the frame to be voiced speech.
     */
    private static boolean isVoiced(MBEModelParameters parameters)
    {
        boolean[] decisions = parameters.getVoicingDecisions();

        if(decisions == null)
        {
            return false;
        }

        //Voicing decisions are 1-indexed
        int bands = FastMath.min(FastMath.max(1, parameters.getL() / 2), decisions.length - 1);

        if(bands < 1)
        {
            return false;
        }

        int voiced = 0;

        for(int l = 1; l <= bands; l++)
        {
            if(decisions[l])
            {
                voiced++;
            }
        }

        return (float)voiced / bands >= MINIMUM_VOICED_FRACTION;
    }

    /**
     * Indicates if the fundamental frequency is close to the previous voiced frame's fundamental frequency.  Speech
     * has a smooth pitch contour while noise produces erratic pitch estimates.
     */
    private boolean isPitchContinuous(float fundamental)
    {
        return mPreviousFundamental > 0.0f &&
            Math.abs(fundamental - mPreviousFundamental) <= mPreviousFundamental * PITCH_CONTINUITY_TOLERANCE;
    }

    private static float toDb(float rms)
    {
        if(rms <= 0.0f)
        {
            return MINIMUM_LEVEL_DB;
        }

        return FastMath.max(MINIMUM_LEVEL_DB, (float)(20.0 * FastMath.log10(rms / Short.MAX_VALUE)));
    }

    /**
     * Current gain target.
     */
    public float getGain()
    {
        return mGain;
    }

    /**
     * Gain applied at the end of the most recently processed frame, after peak limiting.
     */
    public float getAppliedGain()
    {
        return mAppliedGain;
    }

    /**
     * Current noise floor estimate in dBFS.
     */
    public float getNoiseFloorDb()
    {
        return mNoiseFloorDb;
    }

    /**
     * Current speech level estimate in dBFS, or -100 if no speech has been detected.
     */
    public float getSpeechLevelDb()
    {
        return mSpeechLevelDb;
    }

    /**
     * Indicates if the most recently processed frame was classified as speech.
     */
    public boolean isSpeech()
    {
        return mSpeech;
    }
}
