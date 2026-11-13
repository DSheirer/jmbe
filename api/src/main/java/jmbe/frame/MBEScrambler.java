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

import jmbe.binary.BinaryFrame;

/**
 * Pseudo-random scrambler sequence (algorithms 52 - 54) shared by IMBE and AMBE frames.
 */
final class MBEScrambler
{
    private MBEScrambler()
    {
    }

    /**
     * Generates the pseudo-random sequence from the seed and applies (xor) it against the frame bits.
     *
     * @param frame to descramble
     * @param seed for the sequence, from the first 12 bits of the first protected vector
     * @param offset to the first frame bit to descramble
     * @param length of the sequence in bits
     */
    static void descramble(BinaryFrame frame, int seed, int offset, int length)
    {
        //alg 52
        int prX = 16 * seed;

        for(int x = 0; x < length; x++)
        {
            //alg 53 - simplified [... - 65536 * floor((173 * pr(n-1) + 13849) / 65536)] to modulus operation
            prX = (173 * prX + 13849) % 65536;

            //alg 54 - values 32768 and above are a 1 and below is a 0 (default)
            if(prX >= 32768)
            {
                frame.flip(x + offset);
            }
        }
    }
}
