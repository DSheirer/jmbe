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

package jmbe.frame;

/**
 * Voice frame that has been error detected and corrected and can be passed to an IAudioCodecV3 for audio synthesis
 * without the codec repeating the error detection and correction.
 */
public interface ICorrectedFrame
{
    /**
     * Error counts for each of the error protected words in the frame, starting with c0.
     */
    int[] getErrors();

    /**
     * Sum of the error counts for all error protected words in the frame.
     */
    int getErrorCountTotal();
}
