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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AMBESynthesizer extends MBESynthesizer
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBESynthesizer.class);

    private ToneGenerator mToneGenerator = new ToneGenerator();
    private AMBEModelParameters mPreviousFrame = new AMBEModelParameters();
    private int mDebugFrameCounter = 0;

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
        mDebugFrameCounter++;

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

            System.out.println(mDebugFrameCounter + " ENR:" + parameters.getLocalEnergy() + "\tGain:" + parameters.getGain());
            System.out.println(mDebugFrameCounter + " SPC:" + Arrays.toString(parameters.getSpectralAmplitudes()));
            System.out.println(mDebugFrameCounter + " ENH:" + Arrays.toString(parameters.getEnhancedSpectralAmplitudes()));

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

    public static void makeAMBEWaves(List<byte[]> frames, File outputFile) throws IOException
    {
        IAudioCodec audioCodec = new AMBEAudioCodec();
        audioCodec.setAudioGain(6.0f);

        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        ByteBuffer byteBuffer = ByteBuffer.allocate(frames.size() * 320);

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

    public static void makeIMBEWaves(List<byte[]> frames, File outputFile) throws IOException
    {
        IAudioCodec audioCodec = new IMBEAudioCodec();

        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        ByteBuffer byteBuffer = ByteBuffer.allocate(frames.size() * 320);

        int frameCounter = 0;

        for(byte[] frame : frames)
        {
            System.out.println("Frame: " + ++frameCounter);
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
     * Test harness
     *
     * @param args not used
     */
    public static void main(String[] args)
    {
//        String[] frames = {"843CEE369CADA444BC","C8F5A07775103C87AB","DB2980324500D8E733","A8A2EEB7723A390B39","885886142BF5EF0EE6","A06DC25330CDD77D8E","C20C841352A1706B66","8531A46116F40D87B1","9601846015F53CD2E7","8600A40034F60A87C1","0BAAE62624941F2163","3B8BC44042B4686372","1BABE64003953C4353","C22C843633C1030F36","3B98E65737851C6463","84F983443228FEDBD5","A2EF8660138689C509","B1CE866415B0FCA13F","F6F284506593C32E9E","BA91E31570B78A9BBC","4D06788E07C21DCC72","2B056D6A4C8C368D93","3B541C24BCA62B188D","3D707803A4D0CABB7A","4B1319617AE92761D8","5F051E503E01624F44","2A7D0AC04C51713108","5B0E4EC4192B8507D0","3B0A8304749C60FB85","804DE30432E9B24DBA","A17D806279030283A3","6B787EEB5821359915","5DE14B2A1F429F7E32","2DA228C32A05D264D4","1EA3488167FE05DD9F","4F976CFC2C06C3C9BF","E3ACE02323E90EC681","84DB83636269A9BBB0","8E42F17E0FF2F04F7E","391BDEB42BA973C369","F11DC5501C49F4F00A","A37DE22430CBD16CBE","8400A47122F11B90C4","4C86E67616E1678F92","5FA7E572579721DDE3","95C8A124105B8ACBD2","E4C3877016D0836EFA","C5D3A74215C7932CFF","7A497CCA193331DC45","0A5C1AA47D2721102F","0B4A2D4F83D2B5BFD6","186F0C2B7B48DB7852","590E683A0C625F7DC9","7A1F0CB44283509DDB","0D3BE450A7418A8BA1","7E7F6AF81C9D30F4BE","096C0AF46B0500722E","5F285AB039C6A0281D","1A7D0D5B380BCE3E16","0EB34D4C51E4FC87F7","58D46BE27FE7416AE2","4DF35EC76E7C57646D","3F822F2B7C780C08EA","7ED26DE3295E44664F","5BD55AC62AB7031CC3","3A962B0E09B20A7051","6EF12809009D7DE549","5DE229296488289748","2F830BC30904A044C7","39A71EC47FFBD77C39","59D46F2C3ACF9F479A","36D246811293001597"};

//        File directory = new File("/home/denny/Documents/TMR/APCO25/AMBE Codec/MBE Dongle Recordings");
        File directory = new File("/home/denny/Documents/TMR/APCO25/IMBE Codec/MBE Dongle Generated Recordings");

        File[] files = directory.listFiles(new FileFilter()
        {
            @Override
            public boolean accept(File pathname)
            {
                return pathname.toString().endsWith("10328_2319830_frames.txt");
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
//                makeAMBEWaves(frames, outputFile);
                makeIMBEWaves(frames, outputFile);
            }
            catch(IOException ioe)
            {
                System.out.println("Error:" + ioe.getLocalizedMessage());
            }
        }

//        IAudioCodec audioCodec = new AMBEAudioCodec();
//        audioCodec.setAudioGain(6.0f);
//
//        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
//            8000.0f, 16, 1, 2, 8000.0f, false);
//        DataLine.Info datalineinfo = new DataLine.Info(SourceDataLine.class, audioFormat);
//
//        ByteBuffer byteBuffer = ByteBuffer.allocate(frames.length * 320);
//
//        if(AudioSystem.isLineSupported(datalineinfo))
//        {
//            try
//            {
//                SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
//                sourceDataLine.open(audioFormat);
//
//                boolean started = false;
//
//                for(String frame : frames)
//                {
//                    byte[] data = new byte[frame.length() / 2];
//                    for(int x = 0; x < frame.length(); x += 2)
//                    {
//                        data[x / 2] = (byte)(0xFF & Integer.parseInt(frame.substring(x, x + 2), 16));
//                    }
//
//                    float[] samples = audioCodec.getAudio(data);
//
//                    ByteBuffer converted = ByteBuffer.allocate(samples.length * 2);
//                    converted = converted.order(ByteOrder.LITTLE_ENDIAN);
//
//                    for(float sample : samples)
//                    {
//                        converted.putShort((short)(sample * Short.MAX_VALUE));
//                    }
//
//                    byte[] bytes = converted.array();
//                    byteBuffer.put(bytes);
//                    sourceDataLine.write(bytes, 0, bytes.length);
//
//                    if(!started)
//                    {
//                        sourceDataLine.start();
//                        started = true;
//                    }
//                }
//
//                AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(byteBuffer.array()), audioFormat, byteBuffer.array().length);
//
//                File file = new File("/home/denny/Documents/TMR/APCO25/AMBE Codec/MBE Dongle Recordings/0_AMBE Output.wav");
//                if(!file.exists())
//                {
//                    file.createNewFile();
//                }
//
//                AudioSystem.write(ais,AudioFileFormat.Type.WAVE, file);
//
//                sourceDataLine.close();
//            }
//            catch(Exception e)
//            {
//                e.printStackTrace();
//            }
//        }
//        else
//        {
//            System.out.println("Audio Format Not Supported by Host Audio System: " + audioFormat);
//        }
    }
}
