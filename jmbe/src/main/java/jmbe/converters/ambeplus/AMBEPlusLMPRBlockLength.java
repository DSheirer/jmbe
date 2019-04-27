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

package jmbe.converters.ambeplus;

/**
 * Log Magnitude Prediction Residual Block Lengths
 */
public enum AMBEPlusLMPRBlockLength
{
    L0(new int[] {0, 0, 0, 0, 0}),
    L1(new int[] {0, 0, 0, 0, 0}),
    L2(new int[] {0, 0, 0, 0, 0}),
    L3(new int[] {0, 0, 0, 0, 0}),
    L4(new int[] {0, 0, 0, 0, 0}),
    L5(new int[] {0, 0, 0, 0, 0}),
    L6(new int[] {0, 0, 0, 0, 0}),
    L7(new int[] {0, 0, 0, 0, 0}),
    L8(new int[] {0, 0, 0, 0, 0}),
    L9(new int[] {0, 2, 2, 2, 3}),
    L10(new int[] {0, 2, 2, 3, 3}),
    L11(new int[] {0, 2, 3, 3, 3}),
    L12(new int[] {0, 2, 3, 3, 4}),
    L13(new int[] {0, 3, 3, 3, 4}),
    L14(new int[] {0, 3, 3, 4, 4}),
    L15(new int[] {0, 3, 3, 4, 5}),
    L16(new int[] {0, 3, 4, 4, 5}),
    L17(new int[] {0, 3, 4, 5, 5}),
    L18(new int[] {0, 4, 4, 5, 5}),
    L19(new int[] {0, 4, 4, 5, 6}),
    L20(new int[] {0, 4, 4, 6, 6}),
    L21(new int[] {0, 4, 5, 6, 6}),
    L22(new int[] {0, 4, 5, 6, 7}),
    L23(new int[] {0, 5, 5, 6, 7}),
    L24(new int[] {0, 5, 5, 7, 7}),
    L25(new int[] {0, 5, 6, 7, 7}),
    L26(new int[] {0, 5, 6, 7, 8}),
    L27(new int[] {0, 5, 6, 8, 8}),
    L28(new int[] {0, 6, 6, 8, 8}),
    L29(new int[] {0, 6, 6, 8, 9}),
    L30(new int[] {0, 6, 7, 8, 9}),
    L31(new int[] {0, 6, 7, 9, 9}),
    L32(new int[] {0, 6, 7, 9, 10}),
    L33(new int[] {0, 7, 7, 9, 10}),
    L34(new int[] {0, 7, 8, 9, 10}),
    L35(new int[] {0, 7, 8, 10, 10}),
    L36(new int[] {0, 7, 8, 10, 11}),
    L37(new int[] {0, 8, 8, 10, 11}),
    L38(new int[] {0, 8, 9, 10, 11}),
    L39(new int[] {0, 8, 9, 11, 11}),
    L40(new int[] {0, 8, 9, 11, 12}),
    L41(new int[] {0, 8, 9, 11, 13}),
    L42(new int[] {0, 8, 9, 12, 13}),
    L43(new int[] {0, 8, 10, 12, 13}),
    L44(new int[] {0, 9, 10, 12, 13}),
    L45(new int[] {0, 9, 10, 12, 14}),
    L46(new int[] {0, 9, 10, 13, 14}),
    L47(new int[] {0, 9, 11, 13, 14}),
    L48(new int[] {0, 10, 11, 13, 14}),
    L49(new int[] {0, 10, 11, 13, 15}),
    L50(new int[] {0, 10, 11, 14, 15}),
    L51(new int[] {0, 10, 12, 14, 15}),
    L52(new int[] {0, 10, 12, 14, 16}),
    L53(new int[] {0, 11, 12, 14, 16}),
    L54(new int[] {0, 11, 12, 15, 16}),
    L55(new int[] {0, 11, 12, 15, 17}),
    L56(new int[] {0, 11, 13, 15, 17});

    private int[] mBlockLengths;

    AMBEPlusLMPRBlockLength(int[] blockLengths)
    {
        mBlockLengths = blockLengths;
    }

    public int[] getBlockLengths()
    {
        return mBlockLengths;
    }

    public static AMBEPlusLMPRBlockLength fromValue(int value)
    {
        if(0 <= value && value <= 56)
        {
            return AMBEPlusLMPRBlockLength.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-56.  Unrecognized value: " + value);
    }
}
