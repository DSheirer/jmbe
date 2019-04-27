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
import java.util.HashMap;
import java.util.Map;

/**
 * Audio without any accompanying metadata.
 */
public class AudioWithMetadata implements IAudioWithMetadata
{
    private float[] mAudio;
    private Map<String,String> mMetadataMap = new HashMap();

    /**
     * Constructs an instance
     * @param audio samples
     */
    public AudioWithMetadata(float[] audio)
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


    public void addMetadata(String key, String value)
    {
        mMetadataMap.put(key, value);
    }

    /**
     * Indicates if there is any metadata associated with this audio block
     */
    @Override
    public boolean hasMetadata()
    {
        return !mMetadataMap.isEmpty();
    }

    /**
     * Metadata map.
     * @return map of metadata
     */
    @Override
    public Map<String,String> getMetadata()
    {
        return mMetadataMap;
    }

    /**
     * Convenience method to create an instance
     */
    public static AudioWithMetadata create(float[] audio)
    {
        return new AudioWithMetadata(audio);
    }
}
