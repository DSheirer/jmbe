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

/*
 * *****************************************************************************
 * Copyright (C) 2014-2022 Dennis Sheirer
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Utilities for converting to/from signed 16-bit sample byte arrays and float buffers
 */
public class Utils
{
    /**
     * Sleep the calling thread quietly and suppress any interruption.
     * @param millis time to sleep
     */
    public static void sleepQuietly(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            System.out.println("Sleep quietly interrupted.");
        }
    }
    /**
     * Converts the byte array containing 16-bit samples into a float array
     *
     * @param bytes containing 16-bit samples
     * @return samples.
     */
    public static float[] convertFromSigned16BitSamples(byte[] bytes)
    {
        return convertFromSigned16BitSamples(ByteBuffer.wrap(bytes));
    }


    /**
     * Converts the byte buffer containing 16-bit samples into a float array
     */
    public static float[] convertFromSigned16BitSamples(ByteBuffer buffer)
    {
        ShortBuffer byteBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

        float[] samples = new float[buffer.limit() / 2];

        for(int x = 0; x < samples.length; x++)
        {
            samples[x] = (float)byteBuffer.get() / (float)Short.MAX_VALUE;
        }

        return samples;
    }

    /**
     * Converts the float samples into a little-endian 16-bit sample byte buffer.
     *
     * @param samples - float array of sample data
     * @return - little-endian 16-bit sample byte buffer
     */
    public static ByteBuffer convertToSigned16BitSamples(float[] samples)
    {
        ByteBuffer converted = ByteBuffer.allocate(samples.length * 2);
        converted.order(ByteOrder.LITTLE_ENDIAN);

        for(float sample : samples)
        {
            if(sample > 1.0f)
            {
                converted.putShort(Short.MAX_VALUE);
            }
            else if(sample < -1.0f)
            {
                converted.putShort((short)-Short.MAX_VALUE);
            }
            else
            {
                converted.putShort((short)(sample * Short.MAX_VALUE));
            }
        }

        return converted;
    }

    /**
     * Converts the float samples into a little-endian 32-bit sample byte buffer.
     *
     * @param samples - float array of sample data
     * @return - little-endian 32-bit sample byte buffer
     */
    public static ByteBuffer convertToSigned32BitSamples(float[] samples)
    {
        ByteBuffer converted = ByteBuffer.allocate(samples.length * 4);
        converted.order(ByteOrder.LITTLE_ENDIAN);

        for(float sample : samples)
        {
            if(sample > 1.0f)
            {
                converted.putInt(Integer.MAX_VALUE);
            }
            else if(sample < -1.0f)
            {
                converted.putInt(-Integer.MAX_VALUE);
            }
            else
            {
                converted.putInt((int)(sample * Integer.MAX_VALUE));
            }
        }

        return converted;
    }
}
