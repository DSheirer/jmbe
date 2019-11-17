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

package jmbe.codec.imbe;

import java.util.Map;
import java.util.TreeMap;

/**
 * Message bit indexes for the encoded b2 gain value.
 *
 * Bits 2 and 1 are dynamic based on the value of the Kx band voiced/unvoiced decision indexes in vector b1, the length
 * of which is K.  The comment following each entry indicates the indexes allocated to vector b1 and bits 2/1 for
 * vector b2 are the next two consecutive bits following vector b1.
 */
public enum GainIndexes
{
    L09(9, new int[] {6, 7, 8, 95, 96, 140}), //K3 92,93,94
    L10(10, new int[] {6, 7, 8, 96, 97, 140}), //K4 92,93,94,95
    L11(11, new int[] {6, 7, 8, 96, 97, 140}), //K4 92,93,94,95
    L12(12, new int[] {6, 7, 8, 96, 97, 140}), //K4 92,93,94,95
    L13(13, new int[] {6, 7, 8, 97, 98, 140}), //K5 92,93,94,95,96
    L14(14, new int[] {6, 7, 8, 97, 98, 140}), //K5 92,93,94,95,96
    L15(15, new int[] {6, 7, 8, 97, 98, 140}), //K5 92,93,94,95,96
    L16(16, new int[] {6, 7, 8, 98, 99, 140}), //K6 92,93,94,95,96,97
    L17(17, new int[] {6, 7, 8, 98, 99, 140}), //K6 92,93,94,95,96,97
    L18(18, new int[] {6, 7, 8, 98, 99, 140}), //K6 92,93,94,95,96,97
    L19(19, new int[] {6, 7, 8, 99, 100, 140}), //K7 92,93,94,95,96,97,98
    L20(20, new int[] {6, 7, 8, 99, 100, 140}), //K7 92,93,94,95,96,97,98
    L21(21, new int[] {6, 7, 8, 99, 100, 140}), //K7 92,93,94,95,96,97,98
    L22(22, new int[] {6, 7, 8, 100, 101, 140}), //K8 92,93,94,95,96,97,98,99
    L23(23, new int[] {6, 7, 8, 100, 101, 140}), //K8 92,93,94,95,96,97,98,99
    L24(24, new int[] {6, 7, 8, 100, 101, 140}), //K8 92,93,94,95,96,97,98,99
    L25(25, new int[] {6, 7, 8, 101, 102, 140}), //K9 92,93,94,95,96,97,98,99,100
    L26(26, new int[] {6, 7, 8, 101, 102, 140}), //K9 92,93,94,95,96,97,98,99,100
    L27(27, new int[] {6, 7, 8, 101, 102, 140}), //K9 92,93,94,95,96,97,98,99,100
    L28(28, new int[] {6, 7, 8, 102, 107, 140}), //K10 92,93,94,95,96,97,98,99,100,101
    L29(29, new int[] {6, 7, 8, 102, 107, 140}), //K10 92,93,94,95,96,97,98,99,100,101
    L30(30, new int[] {6, 7, 8, 102, 107, 140}), //K10 92,93,94,95,96,97,98,99,100,101
    L31(31, new int[] {6, 7, 8, 107, 108, 140}), //K11 92,93,94,95,96,97,98,99,100,101,102
    L32(32, new int[] {6, 7, 8, 107, 108, 140}), //K11 92,93,94,95,96,97,98,99,100,101,102
    L33(33, new int[] {6, 7, 8, 107, 108, 140}), //K11 92,93,94,95,96,97,98,99,100,101,102
    L34(34, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L35(35, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L36(36, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L37(37, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L38(38, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L39(39, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L40(40, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L41(41, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L42(42, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L43(43, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L44(44, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L45(45, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L46(46, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L47(47, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L48(48, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L49(49, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L50(50, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L51(51, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L52(52, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L53(53, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L54(54, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L55(55, new int[] {6, 7, 8, 108, 109, 140}), //K12 92,93,94,95,96,97,98,99,100,101,102,107
    L56(56, new int[] {6, 7, 8, 108, 109, 140}); //K12 92,93,94,95,96,97,98,99,100,101,102,107

    private int mL;
    private int[] mIndexes;

    private static final Map<Integer,GainIndexes> LOOKUP_MAP = new TreeMap<>();

    static
    {
        for(GainIndexes indexes : GainIndexes.values())
        {
            LOOKUP_MAP.put(indexes.mL, indexes);
        }
    }

    GainIndexes(int L, int[] indexes)
    {
        mL = L;
        mIndexes = indexes;
    }

    public int[] getIndexes()
    {
        return mIndexes;
    }

    public static GainIndexes fromL(int L)
    {
        if(9 <= L && L <= 56)
        {
            return LOOKUP_MAP.get(L);
        }

        throw new IllegalArgumentException("Invalid value of L [" + L + "].  Valid values are in range 9 <> 56");
    }
}
