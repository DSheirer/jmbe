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

package jmbe.codec.ambe.tone;

import jmbe.codec.oscillator.Oscillator;

/**
 * Tone Generator
 */
public class ToneGenerator
{
    private static final double SAMPLE_RATE = 8000.0;
    private static final int SAMPLE_COUNT = 160;  //20ms of samples at 8000 Hz

    /**
     * Tone level, measured against the AMBE-3000R (ThumbDV): the peak amplitude in dB re full scale is
     * TONE_DB_AT_AD_64 + TONE_DB_PER_AD * (AD - 64). TIA-102.BABA-1 specifies 0.711 dB per AD step with AD 127 the
     * maximum sinusoidal level; the chip measured 0.708 dB per step and AD 127 within 0.3 dB of full scale. (The
     * previous linear law, AD / 675, matched the chip only near AD 104: 24 dB loud at AD 64, 15 dB quiet at AD 127.)
     */
    public static final double TONE_DB_PER_AD = 0.708;
    public static final double TONE_DB_AT_AD_64 = -44.33;

    /**
     * Dual tone frequencies as the AMBE-3000R synthesizes them: every pair is two harmonics (l1, l2) of a common
     * fundamental, so each frequency lands up to ~20 Hz from the nominal TIA-102.BABA-1 Table 9 value. Measured from
     * the chip's output (per-frame phase advance, 0.01 Hz precision); each component is at the full single tone
     * level for the AD value (the chip does not halve either one). {id, f1, f2}
     */
    private static final double[][] DUAL_TONE_FREQUENCIES = {
        {128, 1334.62, 942.09}, // nominal 1336 / 941 Hz = 17 and 12 x 78.51 Hz
        {129, 1214.34, 693.91}, // nominal 1209 / 697 Hz = 7 and 4 x 173.48 Hz
        {130, 1330.14, 700.06}, // nominal 1336 / 697 Hz = 19 and 10 x 70.01 Hz
        {131, 1479.10, 696.04}, // nominal 1477 / 697 Hz = 17 and 8 x 87.01 Hz
        {132, 1209.53, 769.70}, // nominal 1209 / 770 Hz = 11 and 7 x 109.96 Hz
        {133, 1341.77, 766.73}, // nominal 1336 / 770 Hz = 7 and 4 x 191.68 Hz
        {134, 1473.68, 771.93}, // nominal 1477 / 770 Hz = 21 and 11 x 70.18 Hz
        {135, 1208.02, 852.72}, // nominal 1209 / 852 Hz = 17 and 12 x 71.06 Hz
        {136, 1337.41, 851.08}, // nominal 1336 / 852 Hz = 11 and 7 x 121.58 Hz
        {137, 1484.04, 848.02}, // nominal 1477 / 852 Hz = 7 and 4 x 212.01 Hz
        {138, 1629.75, 698.47}, // nominal 1633 / 697 Hz = 7 and 3 x 232.82 Hz
        {139, 1634.50, 769.18}, // nominal 1633 / 770 Hz = 17 and 8 x 96.15 Hz
        {140, 1633.00, 852.00}, // nominal 1633 / 852 Hz = 23 and 12 x 71.00 Hz
        {141, 1639.76, 937.01}, // nominal 1633 / 941 Hz = 7 and 4 x 234.25 Hz
        {142, 1209.44, 940.68}, // nominal 1209 / 941 Hz = 9 and 7 x 134.38 Hz
        {143, 1477.90, 940.48}, // nominal 1477 / 941 Hz = 11 and 7 x 134.35 Hz
        {144, 1161.55, 819.90}, // nominal 1162 / 820 Hz = 17 and 12 x 68.33 Hz
        {145, 1055.62, 603.21}, // nominal 1052 / 606 Hz = 7 and 4 x 150.80 Hz
        {146, 1153.14, 610.48}, // nominal 1162 / 606 Hz = 17 and 9 x 67.83 Hz
        {147, 1297.05, 605.29}, // nominal 1279 / 606 Hz = 15 and 7 x 86.47 Hz
        {148, 1053.76, 670.57}, // nominal 1052 / 672 Hz = 11 and 7 x 95.80 Hz
        {149, 1168.41, 667.66}, // nominal 1162 / 672 Hz = 7 and 4 x 166.92 Hz
        {150, 1286.34, 677.03}, // nominal 1279 / 672 Hz = 19 and 10 x 67.70 Hz
        {151, 1046.32, 747.37}, // nominal 1052 / 743 Hz = 7 and 5 x 149.47 Hz
        {152, 1164.81, 741.24}, // nominal 1162 / 743 Hz = 11 and 7 x 105.89 Hz
        {153, 1298.84, 742.20}, // nominal 1279 / 743 Hz = 7 and 4 x 185.55 Hz
        {154, 1421.67, 609.29}, // nominal 1430 / 606 Hz = 7 and 3 x 203.10 Hz
        {155, 1428.24, 672.11}, // nominal 1430 / 672 Hz = 17 and 8 x 84.01 Hz
        {156, 1424.42, 746.13}, // nominal 1430 / 743 Hz = 21 and 11 x 67.83 Hz
        {157, 1432.14, 818.36}, // nominal 1430 / 820 Hz = 7 and 4 x 204.59 Hz
        {158, 1053.02, 819.01}, // nominal 1052 / 820 Hz = 9 and 7 x 117.00 Hz
        {159, 1292.42, 822.45}, // nominal 1279 / 820 Hz = 11 and 7 x 117.49 Hz
        {160, 438.92, 351.14}, // nominal 440 / 350 Hz = 5 and 4 x 87.78 Hz
        {161, 495.82, 424.99}, // nominal 480 / 440 Hz = 7 and 6 x 70.83 Hz
        {162, 609.97, 487.98}, // nominal 620 / 480 Hz = 5 and 4 x 121.99 Hz
        {163, 490.05, 350.03}, // nominal 490 / 350 Hz = 7 and 5 x 70.01 Hz
    };
    private static final java.util.Map<Integer, double[]> DUAL_TONES = new java.util.HashMap<>();

    static
    {
        for(double[] t : DUAL_TONE_FREQUENCIES)
        {
            DUAL_TONES.put((int)t[0], new double[]{t[1], t[2]});
        }
    }
    private final Oscillator mOscillator1 = new Oscillator(0.0, SAMPLE_RATE);
    private final Oscillator mOscillator2 = new Oscillator(0.0, SAMPLE_RATE);

    /**
     * Constructs an instance
     */
    public ToneGenerator()
    {
    }

    /**
     * Generates 20 ms of PCM audio samples at 8000Hz sample rate using the specified frequency and gain parameters
     *
     * @param toneParameters containing frequency(s) and amplitude
     * @param overallGain in range 0.0 (disabled) to 2.0 (maximum) with 1.0 as the default
     * @return pcm audio samples
     */
    public float[] generate(ToneParameters toneParameters, float overallGain)
    {
        if(!toneParameters.isValidTone())
        {
            throw new IllegalArgumentException("Cannot generate tone audio - INVALID tone");
        }

        //If overall gain is 0 then tone generation is disabled.
        if(overallGain == 0.0f)
        {
            return new float[0];
        }

        Tone tone = toneParameters.getTone();

        //Apply the gain specified in the tone parameters to the requested/argument overall gain
        float gain = amplitude(toneParameters.getAmplitude()) * overallGain;
        double[] frequencies = synthesisFrequencies(tone);

        if(frequencies.length == 2)
        {
            //Each component at the full tone level, as the chip does
            mOscillator1.setFrequency(frequencies[0]);
            mOscillator2.setFrequency(frequencies[1]);
            float[] samples = mOscillator1.generate(SAMPLE_COUNT, gain);
            float[] samples2 = mOscillator2.generate(SAMPLE_COUNT, gain);

            for(int x = 0; x < SAMPLE_COUNT; x++)
            {
                samples[x] += samples2[x];
            }

            return samples;
        }
        else
        {
            mOscillator1.setFrequency(frequencies[0]);
            return mOscillator1.generate(SAMPLE_COUNT, gain);
        }
    }

    /**
     * Peak amplitude (fraction of full scale) of a tone, or of each component of a dual tone, for amplitude AD.
     */
    public static float amplitude(int ad)
    {
        return (float)Math.pow(10.0, (TONE_DB_AT_AD_64 + TONE_DB_PER_AD * (ad - 64)) / 20.0);
    }

    /**
     * Frequencies the tone is synthesized at: the chip's measured pair for dual tones (ids 128..163), otherwise the
     * tone's nominal frequency.
     */
    public static double[] synthesisFrequencies(Tone tone)
    {
        double[] dual = DUAL_TONES.get(tone.getValue() & 0xFF);
        if(dual != null)
        {
            return dual.clone();
        }
        return tone.hasFrequency2() ? new double[]{tone.getFrequency1(), tone.getFrequency2()} :
            new double[]{tone.getFrequency1()};
    }
}
