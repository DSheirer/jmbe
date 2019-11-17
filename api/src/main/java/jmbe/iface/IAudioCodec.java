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

package jmbe.iface;

/**
 * Audio converter interface.  Defines methods for a stand-alone converter that
 * can convert byte data from one audio format into byte data of another.
 */
public interface IAudioCodec
{
    /**
     * Name of the CODEC for this audio converter
     */
    String getCodecName();

    /**
     * Converts frameData into the audio format specified by the getConvertedAudioFormat() method
     */
    float[] getAudio(byte[] frameData);

    /**
     * Converts frameData to 8 kHz 16-bit PCM audio and provides optional decoded metadata like Tones or DTMF
     * @param frameData byte array for an audio frame
     * @return audio data with optional metadata
     */
    IAudioWithMetadata getAudioWithMetadata(byte[] frameData);

    /**
     * Resets the audio converter for a new call.  This causes the stored previous frame to be reset to a default
     * audio frame.
     */
    void reset();
}
