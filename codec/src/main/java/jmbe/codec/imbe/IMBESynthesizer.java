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

package jmbe.codec.imbe;

import jmbe.codec.MBEModelParameters;
import jmbe.codec.MBESynthesizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * IMBE synthesizer for IMBE audio frames
 */
public class IMBESynthesizer extends MBESynthesizer
{
    private final static Logger mLog = LoggerFactory.getLogger(IMBESynthesizer.class);
    private IMBEModelParameters mPreviousParameters = new IMBEModelParameters();

    /**
     * Synthesizes 8 kHz 16-bit audio from IMBE audio frames
     */
    public IMBESynthesizer()
    {
    }

    @Override
    public MBEModelParameters getPreviousFrame()
    {
        return mPreviousParameters;
    }

    public void reset()
    {
        mPreviousParameters = new IMBEModelParameters();
    }

    /**
     * Synthesizes 20 milliseconds of audio from the imbe frame parameters in
     * the following format:
     *
     * Sample Rate: 8 kHz
     * Sample Size: 16-bits
     * Frame Size: 160 samples
     * Bit Format: Little Endian
     *
     * @return ByteBuffer containing the audio sample bytes
     */
    public float[] getAudio(IMBEFrame frame)
    {
        IMBEModelParameters parameters = frame.getModelParameters(mPreviousParameters);

        float[] audio = null;

        if(parameters.isMaxFrameRepeat() || parameters.requiresMuting())
        {
            audio = getWhiteNoise();
        }
        else
        {
            audio = getVoice(parameters);
        }

        mPreviousParameters = parameters;

        return audio;
    }

    public static void main(String[] args)
    {
        String[] frames = {
            "7C57B79E016C72542611A1E329DDE3A3DCFE",
            "103395EC150222288DE7B35977025308C4E8"
        };

        IMBESynthesizer synthesizer = new IMBESynthesizer();
        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        DataLine.Info datalineinfo = new DataLine.Info(SourceDataLine.class, audioFormat);

        if(AudioSystem.isLineSupported(datalineinfo))
        {
            ByteBuffer buffer = ByteBuffer.allocate(320 * frames.length);

            try
            {
                SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
                sourceDataLine.open(audioFormat);

                boolean started = false;

                IMBEModelParameters current = new IMBEModelParameters();

                float[] allSamples = new float[frames.length * 160];
                int allSamplesPointer = 0;

                for(String frame : frames)
                {
                    byte[] data = new byte[frame.length() / 2];
                    for(int x = 0; x < frame.length(); x += 2)
                    {
                        data[x / 2] = (byte)(0xFF & Integer.parseInt(frame.substring(x, x + 2), 16));
                    }

                    IMBEFrame imbe = new IMBEFrame(data);
                    current = imbe.getModelParameters(current);
                    float[] samples = synthesizer.getAudio(imbe);
                    System.arraycopy(samples, 0, allSamples, allSamplesPointer, samples.length);
                    allSamplesPointer += 160;
                    ByteBuffer converted = ByteBuffer.allocate(samples.length * 2);
                    converted = converted.order(ByteOrder.LITTLE_ENDIAN);

                    for(float sample : samples)
                    {
                        converted.putShort((short)(sample * Short.MAX_VALUE));
                    }

                    byte[] bytes = converted.array();
                    sourceDataLine.write(bytes, 0, bytes.length);

                    buffer.put(bytes);

                    if(!started)
                    {
                        sourceDataLine.start();
                        started = true;
                    }
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            byte[] bufferBytes = buffer.array();

            AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(bufferBytes), audioFormat, bufferBytes.length);

            try
            {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File("/home/denny/SDRTrunk/recordings/imbe-synthesizer-output.wav"));
            }
            catch(Exception e)
            {
                mLog.error("Error", e);
            }
        }
        else
        {
            System.out.println("Audio Format Not Supported by Host Audio System: " + audioFormat);
        }
    }
}
