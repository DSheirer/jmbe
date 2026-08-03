/*
 * ******************************************************************************
 * Copyright (C) 2015-2026 Dennis Sheirer
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

/**
 * Spectral shaping that the AMBE-3000R (ThumbDV) applies to AMBE+2 voice and JMBE's published-algorithm decode does
 * not, measured with hardware probes (analysis.probe: ProbeGenerator --response, --pitch-sweep and --glide, analyzed by
 * ProbeResponseAnalyzer). Applied to the enhanced spectral amplitudes, after the spec enhancement, so it changes what is
 * synthesized and nothing that feeds the next frame (prediction and local energy use the unenhanced amplitudes).
 *
 * Three parts:
 * <ul>
 *   <li>High-frequency shelf, by harmonic frequency: flat (0 dB) up to ~2.45 kHz, then rising to +3.2 dB at 3.7 kHz.
 *   One curve fits 30 pitches (L 9..56), five spectral tilts and levels from -49 to -13 dBFS. Tone frames come out of
 *   the chip flat, so this is in the voice path, not an output filter.</li>
 *   <li>Low-frequency roll-off, by harmonic frequency: a 3rd order Butterworth high-pass magnitude at 75 Hz (-3 dB at
 *   75 Hz, -1.1 dB at 92 Hz, -0.1 dB at 130 Hz).</li>
 *   <li>Pitch-tracking low-harmonic attenuation: the chip keeps a tracked pitch P that jumps up to f0 at once when the
 *   pitch rises and decays toward f0 by a factor 0.95 per frame when it falls (P = max(f0, f0 + 0.95 (P' - f0)), 160 Hz
 *   after reset). A harmonic below P is attenuated by (l f0 / P)^2, 12 dB per octave, so after a fall in pitch the first
 *   harmonic drops by ~12 dB per octave of the fall and recovers with a ~20 frame time constant; with steady or rising
 *   pitch nothing is attenuated.</li>
 * </ul>
 * The last two replace the earlier per-pitch first harmonic table, which was the pitch sweep's falling-pitch order
 * measured as if it were a static response. Fitted to 1990 steady frames of the pitch glide probe (steps of 1..16
 * pitch steps up and down at 150 and 92 Hz, glides, vibrato): first harmonic residual 1.37 dB rms with no model,
 * 0.145 dB with this one. All are relative to the 0.5..2 kHz band, so mid-band level is unchanged (the chip is ~0.5 dB
 * louder overall, which is not applied). Disable with {@link #setEnabled(boolean)} to get the published-algorithm decode.
 */
public final class AMBEChipResponse
{
    /** High-frequency shelf: harmonic frequency (Hz) and gain (dB), linear interpolation, 0 dB below the first. */
    /** Voiced harmonic level by frequency, dB (see voicedDb): real NXDN and DMR voiced frames, per 500 Hz band. */
    private static final double[][] VOICED = {
        {250, 0.75}, {750, 0.60}, {1250, 0.15}, {1750, 0.10}, {2250, 0.10}, {2750, 0.10}, {3250, 0.00}, {3750, -0.25},
    };
    private static final double[][] SHELF = {
        {2450, 0.00},
        {2562, 0.34},
        {2688, 0.72},
        {2812, 1.18},
        {2938, 1.58},
        {3062, 1.81},
        {3188, 2.05},
        {3312, 2.41},
        {3438, 2.85},
        {3562, 2.97},
        {3688, 3.21},
        {4000, 4.02}, // extrapolated at the measured ~2.6 dB/kHz slope (no harmonic was measured above 3.7 kHz)
    };

    /** Low-frequency roll-off: Butterworth high-pass corner (Hz) and order. */
    public static final double HIGH_PASS_HZ = 75.0;
    public static final int HIGH_PASS_ORDER = 3;

    /** Tracked pitch after reset (Hz) and its per-frame decay toward a lower f0. */
    public static final float TRACKED_PITCH_RESET_HZ = 160.0f;
    public static final float TRACKED_PITCH_DECAY = 0.95f;
    /** Upper limit of the tracked pitch (Hz): fundamentals above it are never attenuated. */
    public static final float TRACKED_PITCH_MAX_HZ = 200.0f;

    /**
     * Noise (unvoiced harmonics) gain relative to voiced: 500 Hz band centre (Hz) and gain (dB), linear interpolation,
     * clamped at both ends. First from the voicing probe (nine pitches, harmonics both decoders call unvoiced vs both
     * call voiced), then corrected by the band errors of steady unvoiced frames on real traffic (b1 15..31, same code
     * as the frame before; 1454 frames of 806 NXDN and DMR calls): -0.46 +0.07 +0.20 +1.13 +0.18 +0.20 +0.33 -0.84
     * dB (JMBE - chip) per band. The probe's 1.5..2 kHz entry (+0.6) was the largest error.
     */
    private static final double[][] NOISE = {
        {250, -0.84},
        {750, -1.17},
        {1250, -1.00},
        {1750, -0.53},
        {2250, -1.08},
        {2750, -0.60},
        {3250, -0.53},
        {3750, -1.16},
    };

    /**
     * Scale of the random phase that MBE synthesis adds to voiced harmonics above L/4 in proportion to the share of
     * unvoiced harmonics (1 = the published algorithm). The chip's voiced harmonics in partly voiced frames are about
     * as steady as this scale gives JMBE's (see the voicing probe).
     */
    private static volatile double sPhaseNoiseScale = 0.3;

    /** Unvoiced noise low-pass: frequency (Hz) and gain (dB), linear interpolation, 0 dB below the first. */
    private static final double[][] NOISE_LOW_PASS = {
        {3550, 0.0},
        {3600, -1.0},
        {3650, -4.0},
        {3700, -10.0},
        {3750, -21.0},
        {3800, -30.0},
        {4000, -40.0},
    };
    private static final float[] NOISE_BIN_GAIN = new float[128];
    private static volatile boolean sNoiseLowPass = true;
    private static volatile double sNoiseNormalizationExponent = 0.85;

    static
    {
        for(int bin = 0; bin < 128; bin++)
        {
            double hz = bin * 8000.0 / 256.0;
            double db = hz <= NOISE_LOW_PASS[0][0] ? 0.0 : interpolate(NOISE_LOW_PASS, hz);
            NOISE_BIN_GAIN[bin] = (float)Math.pow(10.0, db / 20.0);
        }
    }

    /**
     * The AMBE-3000R's error-rate average: rate = 0.99 rate + 0.01 * (corrected C0 + C1 errors) / 47, i.e. a bit error
     * rate over the 47 Golay-coded bits with a ~100-frame time constant (the published Alg #55 uses 0.95 and 0.05,
     * ~20 frames). Fitted to the error-rate probe: fourteen 120-frame loads of 3..6 errors per frame, C0 and C1 counted
     * alike at any bit position; this reproduces the chip's INVALID flag on 3202 of 3204 frames (onset 61..117 frames
     * into a load, release ~21 frames after it), where the published average would mute 5-error loads at frame 45
     * and release at once.
     */
    public static final float CHIP_RATE_DECAY = 0.99f;
    /** Error rate past which the chip mutes (see CHIP_RATE_DECAY): 313 errors' worth of the 0.99 average. */
    public static final float MUTE_ERROR_RATE = 0.0666f;

    /** Next chip error-rate average from the previous one and this frame's corrected C0 + C1 errors. */
    public static float chipErrorRate(float previous, int errors)
    {
        return CHIP_RATE_DECAY * previous + (1.0f - CHIP_RATE_DECAY) * errors / 47.0f;
    }
    private static volatile int sRepeatMemory = 4;

    private static volatile boolean sEnabled = true;

    private AMBEChipResponse()
    {
    }

    /**
     * Turns the chip response on (default) or off (published-algorithm decode).
     */
    public static void setEnabled(boolean enabled)
    {
        sEnabled = enabled;
    }

    public static boolean isEnabled()
    {
        return sEnabled;
    }

    /**
     * Next tracked pitch: rises to f0 at once, decays toward a lower f0 by TRACKED_PITCH_DECAY per frame, limited to
     * TRACKED_PITCH_MAX_HZ.
     *
     * @param previousHz tracked pitch of the previous frame (TRACKED_PITCH_RESET_HZ for the first frame)
     * @param f0 this frame's fundamental frequency, Hz
     */
    public static float trackPitch(float previousHz, float f0)
    {
        return Math.min(TRACKED_PITCH_MAX_HZ, Math.max(f0, f0 + TRACKED_PITCH_DECAY * (previousHz - f0)));
    }

    /** Gain of 256-point DFT bin (0..127) of the unvoiced noise: the chip's low-pass when enabled, else 1. */
    public static float noiseBinGain(int bin)
    {
        return sEnabled && sNoiseLowPass ? NOISE_BIN_GAIN[bin] : 1.0f;
    }

    /** Analysis switches: the noise low-pass, and the noise overlap-add normalization exponent. */
    public static void setNoiseOptions(boolean lowPass, double normalizationExponent)
    {
        sNoiseLowPass = lowPass;
        sNoiseNormalizationExponent = normalizationExponent;
    }

    /**
     * Exponent of the noise overlap-add normalization (MBESynthesizer.getUnvoicedNormalizationExponent): the chip's
     * when enabled (its noise power is flat across the frame), else 1 (published algorithm).
     */
    public static double noiseNormalizationExponent()
    {
        return sEnabled ? sNoiseNormalizationExponent : 1.0;
    }

    /**
     * True when the AMBE-3000R treats a frame as invalid (DCMODE 0x0020, frame repeat): C0 uncorrectable (four or more
     * errors detected), an erasure (b0 120..123) or silence (b0 124, 125) pitch value, or the chip's error-rate
     * average (chipErrorRate) past MUTE_ERROR_RATE. It does not apply the published C0 >= 2 and total >= 6 rule: frames with 3 + 3 errors are used.
     */
    public static boolean isInvalidFrame(jmbe.codec.ambe.ambePlus2.FundamentalFrequency fundamental, int[] errors,
                                         float errorRate)
    {
        jmbe.codec.FrameType type = fundamental.getFrameType();
        return errors[0] >= 4 || type == jmbe.codec.FrameType.ERASURE || type == jmbe.codec.FrameType.SILENCE ||
            errorRate > MUTE_ERROR_RATE;
    }

    /** Voicing code (b1) a silence frame must carry for the chip to play it as comfort noise. */
    public static final int COMFORT_NOISE_B1 = 16;
    /**
     * Level of the chip's comfort noise relative to JMBE's decode of the same frame as unvoiced speech, dB (silence
     * probe: -4.4 median over 9 variants, spread 1.5 dB; the chip's noise also has 4..9 dB less above 2.5 kHz).
     */
    public static final double COMFORT_NOISE_DB = -4.4;

    /**
     * True for a silence frame (b0 124 or 125) the AMBE-3000R plays as comfort noise (DCMODE 0x0000, COMFORT NOISE
     * INSERTED) rather than repeating the previous frame: b1 = COMFORT_NOISE_B1 (all bands unvoiced), C0 correctable
     * and the error rate below the mute threshold (silence probe). The noise is the frame decoded as unvoiced speech,
     * at its gain (b2, 6 dB per log2 step of the gain table) and spectral shape (b3..b8), predicted from the last
     * frame before the comfort-noise run, which stays the prediction memory: the noise holds a steady level through
     * the run and speech resumes at once after it (after a reset the noise is near silent and speech ramps up as from
     * reset). JMBE plays it COMFORT_NOISE_DB below its own decode. Silence frames with any other b1, and b0 120..123
     * and 126 frames, are invalid (0x0020, repeated). The radio's silence frame (NXDN, B9E881526173002A6B) is b1 16
     * with gain b2 1: noise ~45 dB below the speech before it.
     */
    public static boolean isComfortNoiseFrame(jmbe.codec.ambe.ambePlus2.FundamentalFrequency fundamental, int[] b,
                                              int[] errors, float errorRate)
    {
        return fundamental.getFrameType() == jmbe.codec.FrameType.SILENCE && b != null && b.length > 1 &&
            b[1] == COMFORT_NOISE_B1 && errors[0] < 4 && errorRate <= MUTE_ERROR_RATE;
    }

    /**
     * Prediction memory left by a frame repeated for C0 errors. 4 (default): the chip decodes the errored frame's bits
     * as if valid into the gain and log2 amplitude memory while it plays the repeat. Matches the bit error probe (the
     * frames after a C0-error run within 0.6 dB rms of the chip, where clearing both memories was 2.4 dB rms and up
     * to 9 dB off) and real NXDN traffic (no dip after a lone invalid frame, where clearing gave -12..-17 dB).
     * 0..3 for analysis: bit 0 clears the gain memory, bit 1 the log2 spectral amplitudes, 0 keeps the previous frame's.
     */
    public static int repeatMemory()
    {
        return sRepeatMemory;
    }

    /** Analysis use: sets repeatMemory(). */
    public static void setRepeatMemory(int memory)
    {
        sRepeatMemory = memory;
    }

    /** Applies the voice-path shelf alone, in place (a repeated frame gets it once more). */
    public static void applyShelf(float[] amplitudes, float w0)
    {
        double f0 = w0 * 8000.0 / (2.0 * Math.PI);
        for(int l = 1; l < amplitudes.length; l++)
        {
            double db = shelfDb(l * f0);
            if(db != 0.0)
            {
                amplitudes[l] *= (float)Math.pow(10.0, db / 20.0);
            }
        }
    }

    /** Random phase scale for voiced harmonics: the chip's when the response is enabled, else 1. */
    public static double phaseNoiseScale()
    {
        return sEnabled ? sPhaseNoiseScale : 1.0;
    }

    /** Sets the chip's random phase scale (analysis use). */
    public static void setPhaseNoiseScale(double scale)
    {
        sPhaseNoiseScale = scale;
    }

    /**
     * Applies the response in place.
     *
     * @param enhancedSpectralAmplitudes amplitudes, index 1..L (index 0 unused)
     * @param w0 fundamental frequency in radians per sample
     * @param trackedPitchHz this frame's tracked pitch (see {@link #trackPitch})
     * @param voiced voicing decisions, index 1..L (null: all voiced)
     * @param b1 voicing codebook index (for the per-code noise level of codes 15..31)
     */
    public static void apply(float[] enhancedSpectralAmplitudes, float w0, float trackedPitchHz, boolean[] voiced,
                             int b1)
    {
        if(!sEnabled || enhancedSpectralAmplitudes == null || w0 <= 0)
        {
            return;
        }
        double f0 = w0 * 8000.0 / (2.0 * Math.PI);
        double noiseFrameDb = 0.0;
        if(voiced != null)
        {
            int unvoiced = 0;
            int n = 0;
            for(int l = 1; l < enhancedSpectralAmplitudes.length && l < voiced.length; l++)
            {
                unvoiced += voiced[l] ? 0 : 1;
                n++;
            }
            noiseFrameDb = noiseFrameDb(n > 0 ? (double)unvoiced / n : 0.0, b1);
        }
        for(int l = 1; l < enhancedSpectralAmplitudes.length; l++)
        {
            double hz = l * f0;
            double db = shelfDb(hz) + highPassDb(hz) + trackingDb(hz, trackedPitchHz);
            if(voiced != null && l < voiced.length && !voiced[l])
            {
                db += noiseDb(hz) + noiseFrameDb;
            }
            else
            {
                db += voicedDb(hz);
            }
            if(db != 0.0)
            {
                enhancedSpectralAmplitudes[l] *= (float)Math.pow(10.0, db / 20.0);
            }
        }
    }

    /** High-frequency shelf gain (dB) at a harmonic frequency (Hz). */
    public static double shelfDb(double hz)
    {
        if(hz <= SHELF[0][0])
        {
            return 0.0;
        }
        return interpolate(SHELF, hz);
    }

    /**
     * Per-code noise level of the all-unvoiced codes 15..31 (dB, relative to their mean): consistent over nine pitches
     * (standard error ~0.05 dB); 20, 24, 25 and 28 are the codes whose chip output repeats frame to frame.
     */
    private static final double[] NOISE_CODE_DB = {0.23, 0.53, 0.42, 0.02, 0.02, -2.0, 0.24, -0.07, -0.83, -2.6, -2.1,
        0.0, 0.18, -1.9, -0.08, -0.1, -0.54};

    /**
     * Frame-level noise gain (dB): codes 15..31 by code; partly voiced frames by their share of unvoiced harmonics,
     * the chip's noise being louder the less of the spectrum is unvoiced (+1.1..1.8 dB below a 0.3 share, ~0 when all
     * unvoiced; fitted as 1.6 dB * (1 - share)).
     */
    public static double noiseFrameDb(double unvoicedShare, int b1)
    {
        if(b1 >= 15 && b1 <= 31)
        {
            return NOISE_CODE_DB[b1 - 15];
        }
        if(unvoicedShare <= 0.0 || unvoicedShare >= 1.0)
        {
            return 0.0;
        }
        return 1.6 * (1.0 - unvoicedShare);
    }

    /** Noise gain (dB) of an unvoiced harmonic at hz, relative to a voiced one. */
    /**
     * Level of a voiced harmonic relative to JMBE's published decode (with the shelf, high-pass and tracking), dB: the
     * level gap measured on real traffic. JMBE's voiced speech was 0.66 dB (NXDN, 8.0k voiced frames) and 0.55 dB
     * (DMR, 10.7k) below the chip, -0.8 / -0.6 dB in the 0..500 / 500..1000 Hz bands, -0.2..0 from 1 to 3.5 kHz and
     * +0.2..0.3 above (MbeCompareAnalyzer, 806 calls). Unvoiced noise matched already and is not changed.
     */
    public static double voicedDb(double hz)
    {
        return interpolate(VOICED, hz);
    }

    /**
     * Length (samples) of the AMBE-3000R's linear fade of a harmonic whose voicing changes between frames, from the
     * frame boundary (transition probe, data/TRANS_real.mbe): the chip's voiced-to-unvoiced drop starts at the
     * boundary and is complete ~110 samples later, where the published synthesis window fades over samples 55..105.
     * Harmonics voiced in both frames are unchanged: the pitch-step probe (data/PSTEP.mbe) shows the chip handles
     * pitch changes like the published algorithm (alternation level within 0.1..0.3 dB of JMBE at 1.5..14% steps).
     */
    public static final float VOICING_FADE_SAMPLES = 110.0f;

    /** Fade-in weight at sample n (0..159) of a harmonic voiced in this frame but not the previous one. */
    public static float voicingFadeIn(int n)
    {
        return Math.max(0.0f, Math.min(1.0f, n / VOICING_FADE_SAMPLES));
    }

    public static double noiseDb(double hz)
    {
        return interpolate(NOISE, hz);
    }

    /** Low-frequency roll-off gain (dB) at a harmonic frequency (Hz). */
    public static double highPassDb(double hz)
    {
        return -10.0 * Math.log10(1.0 + Math.pow(HIGH_PASS_HZ / hz, 2 * HIGH_PASS_ORDER));
    }

    /** Pitch-tracking attenuation (dB) of a harmonic at hz, given the tracked pitch: 12 dB/octave below it. */
    public static double trackingDb(double hz, double trackedPitchHz)
    {
        return hz >= trackedPitchHz ? 0.0 : 40.0 * Math.log10(hz / trackedPitchHz);
    }

    private static double interpolate(double[][] table, double x)
    {
        if(x <= table[0][0])
        {
            return table[0][1];
        }
        for(int i = 1; i < table.length; i++)
        {
            if(x <= table[i][0])
            {
                double t = (x - table[i - 1][0]) / (table[i][0] - table[i - 1][0]);
                return table[i - 1][1] + t * (table[i][1] - table[i - 1][1]);
            }
        }
        return table[table.length - 1][1];
    }
}
