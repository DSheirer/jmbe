package jmbe.frame;

/*******************************************************************************
 * jmbe - Java MBE Library
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
 ******************************************************************************/

import jmbe.binary.BinaryFrame;
import jmbe.edac.Golay23;

import java.util.Arrays;

/**
 * EDACS ProVoice IMBE 7100 bps voice frame after error detection and correction, removal of the randomizer and
 * reordering of the 88-bit parameter vector into the IMBE 4400 bps (P25) layout.
 *
 * Input is one 7x24 IMBE 7100 bit grid packed into 21 bytes, row-major, MSB first, as extracted by the EDACS
 * ProVoice decoder (after de-interleave and polarity correction).  The channel decoding follows mbelib
 * imbe7100x4400.c: correct coset C0, demodulate (de-randomize), extract and correct the 88-bit parameter vector and
 * reorder it into the IMBE 4400 parameter layout.
 */
public class CorrectedProVoiceFrame implements ICorrectedFrame
{
    /**
     * Length of a ProVoice IMBE 7100 voice frame in bytes (7 x 24 bits).
     */
    public static final int FRAME_LENGTH_BYTES = 21;

    private static final int GRID_ROWS = 7;
    private static final int GRID_COLUMNS = 24;

    /**
     * Number of randomized bits: grid row 1 (24), rows 2 - 3 (23 each) and rows 4 - 5 (15 each)
     */
    private static final int RANDOMIZER_LENGTH = 100;

    private static final int[] GOLAY_DATA_CORRECTION = buildGolayDataCorrectionTable();
    private static final int[] IMBE7100_HAMMING_GENERATOR = {0x7AC8, 0x3D64, 0x1EB2, 0x7591};
    private static final int[] HAMMING_MATRIX = {0x0, 0x1, 0x2, 0x4, 0x8, 0x10, 0x20, 0x40, 0x80, 0x100, 0x200, 0x400,
        0x800, 0x1000, 0x2000, 0x4000};

    /**
     * Start offset and length of each IMBE 4400 parameter vector (u0-u7) within the 88-bit vector and the coset
     * data offset where it is placed in the 144-bit IMBE frame.
     */
    private static final int[][] VECTOR_TO_COSET = {{0, 0, 12}, {12, 23, 12}, {24, 46, 12}, {36, 69, 12},
        {48, 92, 11}, {59, 107, 11}, {70, 122, 11}, {81, 137, 7}};

    private final BinaryFrame mFrame;
    private final int[] mErrors;

    private CorrectedProVoiceFrame(BinaryFrame frame, int[] errors)
    {
        mFrame = frame;
        mErrors = errors;
    }

    /**
     * Error checks and corrects the 21-byte ProVoice IMBE 7100 frame, removes the randomizer and reorders the
     * parameter vector into the IMBE 4400 layout.
     *
     * @param data 21-byte (7 x 24 bit grid, row-major, MSB first) ProVoice IMBE 7100 frame
     * @return corrected frame
     * @throws IllegalArgumentException if the data is null or isn't 21 bytes long
     */
    public static CorrectedProVoiceFrame correct(byte[] data)
    {
        boolean[][] grid = unpackGrid(data);
        int[] errors = new int[7];
        errors[0] = correctC0(grid);
        demodulate(grid);
        boolean[] imbe4400 = convert7100To4400(extractData(grid, errors));

        BinaryFrame frame = new BinaryFrame(144);

        for(int[] vector : VECTOR_TO_COSET)
        {
            for(int x = 0; x < vector[2]; x++)
            {
                if(imbe4400[vector[0] + x])
                {
                    frame.set(vector[1] + x);
                }
            }
        }

        return new CorrectedProVoiceFrame(frame, errors);
    }

    /**
     * Corrected frame as a 144-bit frame in the IMBE 4400 coset layout, the same layout as CorrectedIMBEFrame.  Only
     * the data bit positions of each coset word are used: c0 0-11, c1 23-34, c2 46-57, c3 69-80, c4 92-102,
     * c5 107-117, c6 122-132 and c7 137-143.  The parity bit positions are not set.
     */
    public BinaryFrame getFrame()
    {
        return mFrame;
    }

    /**
     * Error counts for the IMBE 7100 coset words c0 through c5 in indexes 0 through 5.  The counts for the
     * Golay(23,12) words (c0 - c3) are the number of data bits that were corrected.  The count for a Hamming(15,11)
     * word (c4 - c5) is 1 when an error was detected.  Index 6 is always 0: the seventh word isn't error protected.
     */
    @Override
    public int[] getErrors()
    {
        return mErrors;
    }

    /**
     * Sum of the error counts for coset words c0 through c5.
     */
    @Override
    public int getErrorCountTotal()
    {
        int total = 0;

        for(int errors : mErrors)
        {
            total += errors;
        }

        return total;
    }

    @Override
    public String toString()
    {
        return "PROVOICE ERRORS:" + Arrays.toString(mErrors) + " " + mFrame;
    }

    private static boolean[][] unpackGrid(byte[] frameData)
    {
        if(frameData == null || frameData.length != FRAME_LENGTH_BYTES)
        {
            throw new IllegalArgumentException("ProVoice IMBE 7100 frame must contain 21 bytes");
        }

        boolean[][] grid = new boolean[GRID_ROWS][GRID_COLUMNS];

        for(int bit = 0; bit < GRID_ROWS * GRID_COLUMNS; bit++)
        {
            grid[bit / GRID_COLUMNS][bit % GRID_COLUMNS] = (frameData[bit / 8] & (0x80 >> (bit % 8))) != 0;
        }

        return grid;
    }

    /**
     * Corrects coset C0 (grid row 0, columns 1-18 with columns 18-22 of the Golay word zero padded).
     *
     * @return number of corrected data bits
     */
    private static int correctC0(boolean[][] grid)
    {
        boolean[] input = new boolean[23];

        for(int column = 0; column < 18; column++)
        {
            input[column] = grid[0][column + 1];
        }

        boolean[] output = correctGolay2312(input);

        int errors = 0;

        for(int x = 22; x > 10; x--)
        {
            if(output[x] != input[x])
            {
                errors++;
            }
        }

        for(int column = 0; column < 18; column++)
        {
            grid[0][column + 1] = output[column];
        }

        return errors;
    }

    /**
     * Removes the pseudo-random modulation from rows 1-5 using a seed derived from the corrected C0 bits.
     */
    private static void demodulate(boolean[][] grid)
    {
        int seed = 0;

        for(int column = 18; column > 11; column--)
        {
            seed = (seed << 1) | (grid[0][column] ? 1 : 0);
        }

        //Descrambling an empty frame leaves the pseudo-random sequence in the frame
        BinaryFrame sequence = new BinaryFrame(RANDOMIZER_LENGTH);
        MBEScrambler.descramble(sequence, seed, 0, RANDOMIZER_LENGTH);

        int k = 0;

        for(int column = 23; column >= 0; column--)
        {
            grid[1][column] ^= sequence.get(k++);
        }

        for(int row = 2; row < 4; row++)
        {
            for(int column = 22; column >= 0; column--)
            {
                grid[row][column] ^= sequence.get(k++);
            }
        }

        for(int row = 4; row < 6; row++)
        {
            for(int column = 14; column >= 0; column--)
            {
                grid[row][column] ^= sequence.get(k++);
            }
        }
    }

    /**
     * Extracts and error corrects the 88-bit IMBE 7100 parameter vector.
     *
     * @param errors receives per-row error counts in indexes 1-5
     */
    private static boolean[] extractData(boolean[][] grid, int[] errors)
    {
        boolean[] data = new boolean[88];
        int offset = 0;

        //C0 data bits - already corrected
        for(int column = 18; column > 11; column--)
        {
            data[offset++] = grid[0][column];
        }

        errors[1] = extractGolay(grid[1], 1, data, offset);
        offset += 12;
        errors[2] = extractGolay(grid[2], 0, data, offset);
        offset += 12;
        errors[3] = extractGolay(grid[3], 0, data, offset);
        offset += 12;
        errors[4] = extractHamming(grid[4], data, offset);
        offset += 11;
        errors[5] = extractHamming(grid[5], data, offset);
        offset += 11;

        //Row 6 is uncoded
        for(int column = 22; column >= 0; column--)
        {
            data[offset++] = grid[6][column];
        }

        return data;
    }

    private static int extractGolay(boolean[] row, int firstColumn, boolean[] data, int offset)
    {
        boolean[] input = new boolean[23];

        for(int x = 0; x < 23; x++)
        {
            input[x] = row[firstColumn + x];
        }

        boolean[] output = correctGolay2312(input);

        int errors = 0;

        for(int x = 22; x > 10; x--)
        {
            if(output[x] != input[x])
            {
                errors++;
            }

            data[offset++] = output[x];
        }

        return errors;
    }

    /**
     * Golay(23,12) correction matching mbelib: only the 12 data bits (indexes 11-22) are corrected.
     */
    private static boolean[] correctGolay2312(boolean[] input)
    {
        int received = 0;

        for(int x = 22; x >= 0; x--)
        {
            received = (received << 1) | (input[x] ? 1 : 0);
        }

        int data = received >> 11;
        int parity = received & 0x7FF;
        int syndrome = golayParity(data) ^ parity;
        int correctedData = data ^ GOLAY_DATA_CORRECTION[syndrome];

        boolean[] output = input.clone();

        for(int x = 22; x >= 11; x--)
        {
            output[x] = (correctedData & (1 << (x - 11))) != 0;
        }

        return output;
    }

    private static int[] buildGolayDataCorrectionTable()
    {
        int[] table = new int[2048];
        Arrays.fill(table, -1);
        table[0] = 0;

        for(int weight = 1; weight <= 3; weight++)
        {
            enumerateGolayErrors(table, 0, 0, 0, weight);
        }

        for(int x = 0; x < table.length; x++)
        {
            if(table[x] < 0)
            {
                table[x] = 0;
            }
        }

        return table;
    }

    private static void enumerateGolayErrors(int[] table, int startBit, int selected, int errorPattern, int weight)
    {
        if(selected == weight)
        {
            int syndrome = golayParity(errorPattern >> 11) ^ (errorPattern & 0x7FF);

            if(table[syndrome] < 0)
            {
                table[syndrome] = errorPattern >> 11;
            }

            return;
        }

        for(int bit = startBit; bit < 23; bit++)
        {
            enumerateGolayErrors(table, bit + 1, selected + 1, errorPattern | (1 << bit), weight);
        }
    }

    /**
     * Parity for the 12 data bits, from the generator rows that Golay23 uses for its data bits.
     */
    private static int golayParity(int data)
    {
        int parity = 0;

        for(int bit = 0; bit < 12; bit++)
        {
            if((data & (0x800 >> bit)) != 0)
            {
                parity ^= Golay23.CHECKSUMS[bit];
            }
        }

        return parity;
    }

    /**
     * IMBE 7100 Hamming(15,11) with columns read in reverse order (mbelib mbe_7100x4400hamming1511).
     */
    private static int extractHamming(boolean[] row, boolean[] data, int offset)
    {
        int block = 0;

        for(int column = 14; column >= 0; column--)
        {
            block = (block << 1) | (row[column] ? 1 : 0);
        }

        int syndrome = 0;

        for(int generator : IMBE7100_HAMMING_GENERATOR)
        {
            syndrome = (syndrome << 1) | (Integer.bitCount(block & generator) & 1);
        }

        if(syndrome > 0)
        {
            block ^= HAMMING_MATRIX[syndrome];
        }

        for(int x = 14; x >= 4; x--)
        {
            data[offset++] = (block & (1 << x)) != 0;
        }

        return syndrome > 0 ? 1 : 0;
    }

    /**
     * Reorders the IMBE 7100 parameter vector into the IMBE 4400 layout (mbelib mbe_convertImbe7100to4400Data).
     */
    private static boolean[] convert7100To4400(boolean[] imbe7100)
    {
        boolean[] imbe4400 = new boolean[88];
        int b0 = 0;

        for(int x = 1; x <= 6; x++)
        {
            b0 = (b0 << 1) | (imbe7100[x] ? 1 : 0);
        }

        b0 = (b0 << 1) | (imbe7100[86] ? 1 : 0);
        b0 = (b0 << 1) | (imbe7100[87] ? 1 : 0);

        double w0 = (4.0 * Math.PI) / (b0 + 39.5);
        int l = (int)(0.9254 * (int)((Math.PI / w0) + 0.25));
        int k = l < 37 ? (l + 2) / 3 : 12;

        imbe4400[87] = imbe7100[0];
        imbe4400[48 + k] = imbe7100[42];
        imbe4400[49 + k] = imbe7100[43];

        int source = 44;
        int destination = 48;

        for(int x = 0; x < k; x++)
        {
            imbe4400[destination++] = imbe7100[source++];
        }

        destination = 0;
        source = 1;

        while(destination < 87)
        {
            imbe4400[destination] = imbe7100[source];

            if(++destination == 48)
            {
                destination += k + 2;
            }

            if(++source == 42)
            {
                source += k + 2;
            }
        }

        return imbe4400;
    }
}
