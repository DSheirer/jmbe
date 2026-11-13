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
import jmbe.edac.Golay24;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * AMBE 3600 (2450 bps data and 1150 bps FEC) voice frame split into vectors c0 through c3, with c0 error checked and
 * corrected, and c1 descrambled, error checked and corrected.  Vectors c2 and c3 are not error protected.
 */
public class CorrectedAMBEFrame implements ICorrectedFrame
{
    /**
     * Length of an AMBE voice frame in bytes (72 bits).
     */
    public static final int FRAME_LENGTH_BYTES = 9;

    private static final int[] VECTOR_C0 = {0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 68, 1, 5,
        9, 13, 17, 21};
    private static final int[] VECTOR_C1 = {25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 65, 69, 2, 6, 10, 14, 18, 22, 26,
        30, 34, 38, 42};
    private static final int[] VECTOR_C2 = {46, 50, 54, 58, 62, 66, 70, 3, 7, 11, 15};
    private static final int[] VECTOR_C3 = {19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71};
    private static final int[] VECTOR_U0 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    private final BinaryFrame mC0;
    private final BinaryFrame mC1;
    private final BinaryFrame mC2;
    private final BinaryFrame mC3;
    private final int[] mErrors = new int[2];

    private CorrectedAMBEFrame(BinaryFrame frame)
    {
        mC0 = getVector(frame, VECTOR_C0);
        mC1 = getVector(frame, VECTOR_C1);
        mC2 = getVector(frame, VECTOR_C2);
        mC3 = getVector(frame, VECTOR_C3);
    }

    /**
     * Splits the 9-byte AMBE frame into vectors c0 through c3, error checks and corrects c0, then descrambles, error
     * checks and corrects c1.
     *
     * @param data 9-byte (72-bit) AMBE frame
     * @return corrected frame
     */
    public static CorrectedAMBEFrame correct(byte[] data)
    {
        CorrectedAMBEFrame frame = new CorrectedAMBEFrame(BinaryFrame.fromBytes(data, ByteOrder.LITTLE_ENDIAN));
        frame.decode();
        return frame;
    }

    private void decode()
    {
        //Error check C0, then descramble and error check C1
        mErrors[0] = Golay24.checkAndCorrect(mC0, 0);
        BinaryFrame modulationVector = getModulationVector(mC0.getInt(VECTOR_U0));
        mC1.xor(modulationVector);
        mErrors[1] = Golay23.checkAndCorrect(mC1, 0);
    }

    /**
     * Extracts a bit vector from the frame
     *
     * @param frame containing the vector bits
     * @param indexes to extract for the vector
     * @return binary frame vector
     */
    private static BinaryFrame getVector(BinaryFrame frame, int[] indexes)
    {
        BinaryFrame vector = new BinaryFrame(indexes.length);

        int pointer = 0;

        for(int bit : indexes)
        {
            if(frame.get(bit))
            {
                vector.set(pointer);
            }

            pointer++;
        }

        return vector;
    }

    /**
     * Generates the bit modulation vector that is xor'd to vector C1 using U0 as the seed.
     *
     * @param seed for the modulation vector
     * @return modulation vector bit sequence to descramble vector C1
     */
    private static BinaryFrame getModulationVector(int seed)
    {
        BinaryFrame modulationVector = new BinaryFrame(23);

        //alg 52
        int prX = 16 * seed;

        for(int x = 0; x < 23; x++)
        {
            //alg 53 - simplified [... - 65536 * floor((173 * pr(n-1) + 13849) / 65536)] to modulus operation
            prX = (173 * prX + 13849) % 65536;

            //alg 54 - values 32768 and above are a 1 and below is a 0 (default)
            if(prX >= 32768)
            {
                modulationVector.set(x);
            }
        }

        return modulationVector;
    }

    /**
     * Golay(24,12) protected vector c0 (24 bits) after error correction.
     */
    public BinaryFrame getC0()
    {
        return mC0;
    }

    /**
     * Golay(23,12) protected vector c1 (23 bits) after descrambling and error correction.
     */
    public BinaryFrame getC1()
    {
        return mC1;
    }

    /**
     * Unprotected vector c2 (11 bits).
     */
    public BinaryFrame getC2()
    {
        return mC2;
    }

    /**
     * Unprotected vector c3 (14 bits).
     */
    public BinaryFrame getC3()
    {
        return mC3;
    }

    /**
     * Error counts for vectors c0 and c1.
     */
    @Override
    public int[] getErrors()
    {
        return mErrors;
    }

    /**
     * Sum of the error counts for vectors c0 and c1.
     */
    @Override
    public int getErrorCountTotal()
    {
        return mErrors[0] + mErrors[1];
    }

    @Override
    public String toString()
    {
        return "AMBE ERRORS:" + Arrays.toString(mErrors);
    }
}
