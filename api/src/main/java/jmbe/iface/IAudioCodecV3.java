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

package jmbe.iface;

import jmbe.frame.ICorrectedFrame;

/**
 * Audio codec that can also synthesize audio from frames that the host application has already error corrected using
 * CorrectedIMBEFrame (IMBE codec) or CorrectedAMBEFrame (AMBE codec), so that the codec doesn't repeat the error
 * detection and correction.
 */
public interface IAudioCodecV3 extends IAudioCodecV2
{
    /**
     * Converts the corrected frame into 8 kHz 16-bit PCM audio.
     *
     * @param frame that was error corrected by the host application
     * @return audio samples
     * @throws IllegalArgumentException if the frame type isn't supported by this codec
     */
    float[] getAudio(ICorrectedFrame frame);

    /**
     * Converts the corrected frame into 8 kHz 16-bit PCM audio and provides optional decoded metadata like Tones or
     * DTMF.
     *
     * @param frame that was error corrected by the host application
     * @return audio data with optional metadata
     * @throws IllegalArgumentException if the frame type isn't supported by this codec
     */
    IAudioWithMetadata getAudioWithMetadata(ICorrectedFrame frame);
}
