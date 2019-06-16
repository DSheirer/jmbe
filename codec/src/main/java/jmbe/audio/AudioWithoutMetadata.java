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

package jmbe.audio;

import jmbe.iface.IAudioWithMetadata;

import java.util.Collections;
import java.util.Map;

/**
 * Audio without any accompanying metadata.
 */
public class AudioWithoutMetadata implements IAudioWithMetadata
{
    private float[] mAudio;

    /**
     * Constructs an instance
     * @param audio samples
     */
    public AudioWithoutMetadata(float[] audio)
    {
        mAudio = audio;
    }

    /**
     * PCM audio samples
     */
    @Override
    public float[] getAudio()
    {
        return mAudio;
    }

    /**
     * Always indicates false
     */
    @Override
    public boolean hasMetadata()
    {
        return false;
    }

    /**
     * Metadata map.
     * @return empty map
     */
    @Override
    public Map<String,String> getMetadata()
    {
        return Collections.emptyMap();
    }

    /**
     * Convenience method to create an instance
     */
    public static AudioWithoutMetadata create(float[] audio)
    {
        return new AudioWithoutMetadata(audio);
    }
}
