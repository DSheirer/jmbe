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

package thumbdv.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * WAVE file utilities
 */
public class WaveUtils
{
    private static final Logger LOG = LoggerFactory.getLogger(WaveUtils.class);
    private static final AudioFormat AUDIO_FORMAT_8KHZ_MONO = new AudioFormat(8000.0f, 16, 1, true, true);
    private static final AudioFormat AUDIO_FORMAT_8KHZ_STEREO = new AudioFormat(8000.0f, 16, 2, true, true);

    /**
     * Writes the list of signed, 16-bit sample arrays to a mono wave file at 8 kHz.
     *
     * @param audio samples list of byte arrays containing 16-bit signed little endian samples
     * @param outputFile for the wave
     */
    public static void writeBE(List<byte[]> audio, Path outputFile) throws Exception
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(byte[] sample : audio)
        {
            baos.write(sample, 0, sample.length);
        }

        writeBE(baos.toByteArray(), AUDIO_FORMAT_8KHZ_MONO, outputFile);
    }

    /**
     * Writes the list of signed, 16-bit left and right channel sample arrays to a stereo wave file at 8 kHz.
     *
     * @param left samples for the left channel
     * @param right samples for the right channel
     * @param outputFile for the wave
     * @throws IllegalArgumentException if there is a size mismatch between the left/right lists or with the individual
     * byte array sizes.
     */
    public static void writeBE(List<byte[]> left, List<byte[]> right, Path outputFile) throws Exception
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if(left.size() != right.size())
        {
            throw new IllegalArgumentException("Left audio samples size [" + left.size() + "] doesn't match right " +
                    "audio sample size [" + right.size() + "]");
        }

        for(int x = 0; x < left.size(); x++)
        {
            byte[] leftData = left.get(x);
            byte[] rightData = right.get(x);

            if(leftData.length != rightData.length)
            {
                throw new IllegalArgumentException("Left audio byte array size [" + leftData.length + "] doesn't match " +
                        "right audio byte array size [" + rightData.length + "]");
            }

            for(int y = 0; y < leftData.length; y+= 2)
            {
                baos.write(leftData, y, 2);
                baos.write(rightData, y, 2);
            }
        }

        writeBE(baos.toByteArray(), AUDIO_FORMAT_8KHZ_STEREO, outputFile);
    }

    /**
     * Writes the byte array to the output file using the specified format
     * @param audio in 16-bit little endian samples
     * @param format to designate in the wave file
     * @param outputFile to write
     */
    public static void writeBE(byte[] audio, AudioFormat format, Path outputFile)
    {
        try(AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(audio), format, audio.length / 2))
        {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile.toFile());
        }
        catch(Exception e)
        {
            LOG.error("Error writing wave file", e);
        }
    }
}
