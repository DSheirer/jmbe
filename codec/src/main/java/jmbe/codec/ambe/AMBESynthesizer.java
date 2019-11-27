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

import jmbe.codec.MBEModelParameters;
import jmbe.codec.MBESynthesizer;
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.iface.IAudioCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class AMBESynthesizer extends MBESynthesizer
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBESynthesizer.class);

    private ToneGenerator mToneGenerator = new ToneGenerator();
    private AMBEModelParameters mPreviousFrame = new AMBEModelParameters();

    /**
     * AMBE synthesizer producing 8 kHz 16-bit audio from AMBE audio (voice/tone) frames
     */
    public AMBESynthesizer()
    {
    }

    /**
     * Previous AMBE frame parameters
     *
     * @return parameters
     */
    @Override
    public MBEModelParameters getPreviousFrame()
    {
        return mPreviousFrame;
    }

    public void reset()
    {
        mPreviousFrame = new AMBEModelParameters();
    }

    /**
     * Generates 160 samples (20 ms) of tone audio
     *
     * @param toneParameters to use in generating the tone frame
     * @return samples
     */
    public float[] getTone(ToneParameters toneParameters)
    {
        return mToneGenerator.generate(toneParameters);
    }

    /**
     * Generates 160 samples (20 ms) of audio from the ambe frame.  Can decode both audio and tone frames and handles
     * frame repeats and white noise generation when error rate exceeds thresholds.
     *
     * @param frame of audio
     * @return decoded audio samples
     */
    public float[] getAudio(AMBEFrame frame)
    {
        float[] audio = null;

        if(frame.isToneFrame())
        {
            if(frame.getToneParameters().isValidTone())
            {
                audio = getTone(frame.getToneParameters());
            }
            else
            {
                mPreviousFrame.setRepeatCount(mPreviousFrame.getRepeatCount());

                if(!mPreviousFrame.isMaxFrameRepeat())
                {
                    audio = getVoice(mPreviousFrame);
                }
                else
                {
                    //Frame muting procedure
                    mPreviousFrame = new AMBEModelParameters();
                    audio = getWhiteNoise();
                }
            }
        }
        else
        {
            AMBEModelParameters parameters = frame.getVoiceParameters(mPreviousFrame);

            if(!parameters.isMaxFrameRepeat())
            {
                if(parameters.isErasureFrame())
                {
                    audio = getWhiteNoise();
                }
                else
                {
                    audio = getVoice(parameters);
                }

                mPreviousFrame = parameters;
            }
            else
            {
                //Frame muting procedure
                mPreviousFrame = new AMBEModelParameters();
                audio = getWhiteNoise();
            }
        }

        if(audio == null)
        {
            audio = new float[SAMPLES_PER_FRAME];
        }

        return audio;
    }

    /**
     * Debug method for generating and testing AMBE recordings
     * @param frames of AMBE encoded audio
     * @param outputFile for generated audio
     * @throws IOException for IO errors
     */
    public static void makeAMBEWaves(List<byte[]> frames, File outputFile) throws IOException
    {
        IAudioCodec audioCodec = new AMBEAudioCodec();

        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        ByteBuffer byteBuffer = ByteBuffer.allocate(frames.size() * 320);

        int frameCounter = 0;

        for(byte[] frame : frames)
        {
            frameCounter++;

            float[] samples = audioCodec.getAudio(frame);

            ByteBuffer converted = ByteBuffer.allocate(samples.length * 2);
            converted = converted.order(ByteOrder.LITTLE_ENDIAN);

            for(float sample : samples)
            {
                converted.putShort((short)(sample * Short.MAX_VALUE));
            }

            byte[] bytes = converted.array();
            byteBuffer.put(bytes);
        }

        AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(byteBuffer.array()), audioFormat, byteBuffer.array().length);

        if(!outputFile.exists())
        {
            outputFile.createNewFile();
        }

        AudioSystem.write(ais,AudioFileFormat.Type.WAVE, outputFile);
    }

    /**
     * Debug method for generating and testing IMBE recordings
     * @param frames of IMBE encoded audio
     * @param outputFile for generated audio
     * @throws IOException for IO errors
     */
    public static void makeIMBEWaves(List<byte[]> frames, File outputFile) throws IOException
    {
        IAudioCodec audioCodec = new IMBEAudioCodec();

        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        ByteBuffer byteBuffer = ByteBuffer.allocate(frames.size() * 320);

        int frameCounter = 0;

        for(byte[] frame : frames)
        {
            float[] samples = audioCodec.getAudio(frame);

            ByteBuffer converted = ByteBuffer.allocate(samples.length * 2);
            converted = converted.order(ByteOrder.LITTLE_ENDIAN);

            for(float sample : samples)
            {
                converted.putShort((short)(sample * Short.MAX_VALUE));
            }

            byte[] bytes = converted.array();
            byteBuffer.put(bytes);
        }

        AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(byteBuffer.array()), audioFormat, byteBuffer.array().length);

        if(!outputFile.exists())
        {
            outputFile.createNewFile();
        }

        AudioSystem.write(ais,AudioFileFormat.Type.WAVE, outputFile);
    }

    /**
     * Debug method for generating 20ms label tracks for Audacity
     * @param sourceFile to use in naming the label track file.
     * @param frameCount number of 20ms frame labels to generate
     */
    public static void makeLabelTrack(Path sourceFile, int frameCount)
    {
        Path output = Paths.get(sourceFile.toString().replace("_frames.txt", "_labels.txt"));

        DecimalFormat df = new DecimalFormat("0.000000");
        double frameMultiplier = 160.0 / 8000.0;

        try
        {
            if(Files.exists(output))
            {
                Files.delete(output);
            }

            StringBuilder sb = new StringBuilder();

            for(int x = 0; x < frameCount; x++)
            {
                if(x != 0)
                {
                    sb.append("\n");
                }
                double start = (double)x * frameMultiplier;
                double end = (double)(x + 1) * frameMultiplier;
                sb.append(df.format(start)).append("\t").append(df.format(end)).append("\t").append((x + 1));
            }

            Files.write(output, sb.toString().getBytes());
        }
        catch(IOException ioe)
        {
            mLog.error("Error writing tracks to [" + output.toString() + "]");
        }
    }

    /**
     * Test harness
     *
     * @param args not used
     */
    public static void main(String[] args)
    {
        File directory = new File("/home/denny/Documents/TMR/APCO25/AMBE Codec/MBE Dongle Recordings");
//        File directory = new File("/home/denny/Documents/TMR/APCO25/IMBE Codec/MBE Dongle Generated Recordings");

        File[] files = directory.listFiles(new FileFilter()
        {
            @Override
            public boolean accept(File pathname)
            {
//                return pathname.toString().endsWith("_9_TS1_65062_6591008_frames.txt");
                return pathname.toString().endsWith("_1_TS1_65040_frames.txt");

//                return pathname.toString().endsWith("_2_TS0_65074_6544820_frames.txt");
//                return pathname.toString().endsWith("_1_TS0_65084_6570511_frames.txt");
//                return pathname.toString().endsWith("_1_TS0_65062_6571751_frames.txt");
//                return pathname.toString().endsWith("_1_TS0_65083_6591001_frames.txt");
//                return pathname.toString().endsWith("_1_TS0_65035_frames.txt");
//                return pathname.toString().endsWith("_4_TS0_65084_6570451_frames.txt");
//                return pathname.toString().endsWith("_7_TS1_65084_6591001_frames.txt");
//                return pathname.toString().endsWith("_frames.txt");
            }
        });

        for(File file: files)
        {
            System.out.println("File:" + file.toString());

            try
            {
                String contents = new String(Files.readAllBytes(file.toPath()));
                String[] split = contents.split(",");
                List<byte[]> frames = new ArrayList<>();

                makeLabelTrack(file.toPath(), split.length);

                for(String splitFrame : split)
                {
                    String frame = splitFrame.replace("\"", "");
                    byte[] data = new byte[frame.length() / 2];
                    for(int x = 0; x < frame.length(); x += 2)
                    {
                        data[x / 2] = (byte)(0xFF & Integer.parseInt(frame.substring(x, x + 2), 16));
                    }
                    frames.add(data);
                }

                File outputFile = new File(file.toString().replace("_frames.txt", "_synthesized.wav"));
                makeAMBEWaves(frames, outputFile);
//                makeIMBEWaves(frames, outputFile);
            }
            catch(IOException ioe)
            {
                System.out.println("Error:" + ioe.getLocalizedMessage());
            }
        }
    }
}
