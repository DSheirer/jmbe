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

package jmbe.converters.ambe;

import jmbe.converters.oscillator.Oscillator;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Tone Generator
 */
public class ToneGenerator
{
    private static final double SAMPLE_RATE = 8000.0;
    private static final int SAMPLE_COUNT = 160;  //20ms of samples at 8000 Hz
    private static final float TWO_CHANNEL_GAIN_REDUCTION = 0.5f;

    private Oscillator mOscillator1 = new Oscillator(0.0, SAMPLE_RATE);
    private Oscillator mOscillator2 = new Oscillator(0.0, SAMPLE_RATE);

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
     * @return pcm audio samples
     */
    public float[] generate(ToneParameters toneParameters)
    {
        if(!toneParameters.isValidTone())
        {
            throw new IllegalArgumentException("Cannot generate tone audio - INVALID tone");
        }

        Tone tone = toneParameters.getTone();
        float gain = ((float)toneParameters.getAmplitude() / 127.0f);

        if(tone.hasFrequency2())
        {
            gain *= TWO_CHANNEL_GAIN_REDUCTION;

            mOscillator1.setFrequency(tone.getFrequency1());
            mOscillator2.setFrequency(tone.getFrequency2());

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
            mOscillator1.setFrequency(tone.getFrequency1());
            return mOscillator1.generate(SAMPLE_COUNT, gain);
        }
    }

    /**
     * Test harness
     * @param args not used
     */
    public static void main(String[] args)
    {
        ToneGenerator toneGenerator = new ToneGenerator();

        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        DataLine.Info datalineinfo = new DataLine.Info(SourceDataLine.class, audioFormat);

        if(AudioSystem.isLineSupported(datalineinfo))
        {
            try
            {
                SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
                sourceDataLine.open(audioFormat);

                for(Tone tone: Tone.DTMF_TONES)
//                for(Tone tone: Tone.KNOX_TONES)
//                for(Tone tone: Tone.CALL_PROGRESS_TONES)
//                for(Tone tone: Tone.DISCRETE_TONES)
//                for(Tone tone: Tone.values())
                {
                    for(int x = 0; x < 128; x++) //Amplitude levels 0 - 127
                    {
                        System.out.print("\rTONE [" + tone.name() + "]: " + tone + " " + tone.getFrequency1() +
                            (tone.hasFrequency2() ? " PLUS " + tone.getFrequency2() : "") + " AMPLITUDE:" + x);

                        ToneParameters toneParameters = new ToneParameters(tone, x);

                        float[] samples = toneGenerator.generate(toneParameters);

                        ByteBuffer converted = ByteBuffer.allocate(samples.length * 2);
                        converted.order(ByteOrder.LITTLE_ENDIAN);

                        for(float sample : samples)
                        {
                            converted.putShort((short)(sample * Short.MAX_VALUE));
                        }

                        byte[] bytes = converted.array();
                        sourceDataLine.write(bytes, 0, bytes.length);

                        if(x == 0)
                        {
                            sourceDataLine.start();
                        }
                    }

                    System.out.println("\rTONE [" + tone.name() + "]: " + tone + " " + tone.getFrequency1() +
                        (tone.hasFrequency2() ? " PLUS " + tone.getFrequency2() : ""));
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println("Audio Format Not Supported by Host Audio System: " + audioFormat);
        }
    }
}
