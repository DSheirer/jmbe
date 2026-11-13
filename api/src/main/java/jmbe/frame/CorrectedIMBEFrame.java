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
import jmbe.edac.Golay23;
import jmbe.edac.Hamming15;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * IMBE 4400 bps (2800 bps data and 1600 bps FEC) voice frame after deinterleaving, error detection and correction,
 * and removal of the randomizer from coset words c1 through c6.
 */
public class CorrectedIMBEFrame
{
    /**
     * Length of an IMBE voice frame in bytes (144 bits).
     */
    public static final int FRAME_LENGTH_BYTES = 18;

    private static final int[] RANDOMIZER_SEED = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    private final BinaryFrame mFrame;
    private final int[] mErrors = new int[7];
    private int mErrorCountTotal;

    private CorrectedIMBEFrame(BinaryFrame frame)
    {
        mFrame = frame;
    }

    /**
     * Deinterleaves the 18-byte IMBE frame, error checks and corrects coset words c0 through c6 and removes the
     * randomizer from coset words c1 through c6.
     *
     * @param data 18-byte (144-bit) IMBE frame as received over the air
     * @return corrected frame
     */
    public static CorrectedIMBEFrame correct(byte[] data)
    {
        CorrectedIMBEFrame frame = new CorrectedIMBEFrame(BinaryFrame.fromBytes(data, ByteOrder.LITTLE_ENDIAN));
        frame.decode();
        return frame;
    }

    private void decode()
    {
        IMBEInterleave.deinterleave(mFrame);

        mErrors[0] = Golay23.checkAndCorrect(mFrame, 0);
        mErrorCountTotal += mErrors[0];

        derandomize();

        mErrors[1] = Golay23.checkAndCorrect(mFrame, 23);
        mErrorCountTotal += mErrors[1];

        mErrors[2] = Golay23.checkAndCorrect(mFrame, 46);
        mErrorCountTotal += mErrors[2];
        mErrors[3] = Golay23.checkAndCorrect(mFrame, 69);
        mErrorCountTotal += mErrors[3];
        mErrors[4] = Hamming15.checkAndCorrect(mFrame, 92);
        mErrorCountTotal += mErrors[4];
        mErrors[5] = Hamming15.checkAndCorrect(mFrame, 107);
        mErrorCountTotal += mErrors[5];
        mErrors[6] = Hamming15.checkAndCorrect(mFrame, 122);
        mErrorCountTotal += mErrors[6];
    }

    /**
     * Removes randomizer by generating a pseudo-random noise sequence from the first 12 bits of coset word c0 and
     * applies (xor) that sequence against message coset words c1 through c6.
     */
    private void derandomize()
    {
        /* Set the offset to the first seed bit plus 23 to point to coset c1 */
        int offset = 23;

        /* Get seed value from first 12 bits of coset c0 */
        int seed = mFrame.getInt(RANDOMIZER_SEED);

        //alg 52
        int prX = 16 * seed;

        for(int x = 0; x < 114; x++)
        {
            //Alg 53 - simplified [... - 65536 * floor((173 * pr(n-1) + 13849) / 65536)] to modulus operation
            prX = (173 * prX + 13849) % 65536;

            //Alg 54 - values 32768 and above are a 1 and below is a 0 (default)
            if(prX >= 32768)
            {
                //This is the same as xor
                mFrame.flip(x + offset);
            }
        }
    }

    /**
     * Corrected 144-bit frame.  Coset words c0 - c3 are Golay(23,12) protected and c4 - c6 are Hamming(15,11)
     * protected, with each word starting at bit index 0, 23, 46, 69, 92, 107 and 122 respectively.
     */
    public BinaryFrame getFrame()
    {
        return mFrame;
    }

    /**
     * Error counts for coset words c0 through c6.  A Golay(23,12) word (c0 - c3) value greater than 3 indicates
     * more errors than can be corrected.  A Hamming(15,11) word (c4 - c6) value of 2 indicates two or more errors
     * that were detected but not corrected.
     */
    public int[] getErrors()
    {
        return mErrors;
    }

    /**
     * Sum of the error counts for coset words c0 through c6.
     */
    public int getErrorCountTotal()
    {
        return mErrorCountTotal;
    }

    @Override
    public String toString()
    {
        return "IMBE ERRORS:" + Arrays.toString(mErrors) + " " + mFrame;
    }
}
