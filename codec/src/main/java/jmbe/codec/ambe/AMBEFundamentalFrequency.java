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

package jmbe.codec.ambe;

import jmbe.codec.FrameType;
import jmbe.codec.IFundamentalFrequency;

import java.util.Map;
import java.util.TreeMap;

/**
 * AMBE Fundamental frequency, harmonic count and frame type enumeration
 */
public enum AMBEFundamentalFrequency implements IFundamentalFrequency
{
    W0(0, 0.049971, 9, FrameType.VOICE),
    W1(1, 0.049215,  9, FrameType.VOICE),
    W2(2, 0.048471,  9, FrameType.VOICE),
    W3(3, 0.047739,  9, FrameType.VOICE),
    W4(4, 0.047010,  9, FrameType.VOICE),
    W5(5, 0.046299,  9, FrameType.VOICE),
    W6(6, 0.045601,  10, FrameType.VOICE),
    W7(7, 0.044905,  10, FrameType.VOICE),
    W8(8, 0.044226,  10, FrameType.VOICE),
    W9(9, 0.043558,  10, FrameType.VOICE),
    W10(10, 0.042900, 10, FrameType.VOICE),
    W11(11, 0.042246, 10, FrameType.VOICE),
    W12(12, 0.041609, 11, FrameType.VOICE),
    W13(13, 0.040979, 11, FrameType.VOICE),
    W14(14, 0.040356, 11, FrameType.VOICE),
    W15(15, 0.039747, 11, FrameType.VOICE),
    W16(16, 0.039148, 11, FrameType.VOICE),
    W17(17, 0.038559, 11, FrameType.VOICE),
    W18(18, 0.037971, 12, FrameType.VOICE),
    W19(19, 0.037399, 12, FrameType.VOICE),
    W20(20, 0.036839, 12, FrameType.VOICE),
    W21(21, 0.036278, 12, FrameType.VOICE),
    W22(22, 0.035732, 12, FrameType.VOICE),
    W23(23, 0.035198, 13, FrameType.VOICE),
    W24(24, 0.034672, 13, FrameType.VOICE),
    W25(25, 0.034145, 13, FrameType.VOICE),
    W26(26, 0.033636, 13, FrameType.VOICE),
    W27(27, 0.033133, 13, FrameType.VOICE),
    W28(28, 0.032635, 14, FrameType.VOICE),
    W29(29, 0.032148, 14, FrameType.VOICE),
    W30(30, 0.031670, 14, FrameType.VOICE),
    W31(31, 0.031122, 14, FrameType.VOICE),
    W32(32, 0.030647, 15, FrameType.VOICE),
    W33(33, 0.030184, 15, FrameType.VOICE),
    W34(34, 0.029728, 15, FrameType.VOICE),
    W35(35, 0.029272, 15, FrameType.VOICE),
    W36(36, 0.028831, 16, FrameType.VOICE),
    W37(37, 0.028395, 16, FrameType.VOICE),
    W38(38, 0.027966, 16, FrameType.VOICE),
    W39(39, 0.027538, 16, FrameType.VOICE),
    W40(40, 0.027122, 17, FrameType.VOICE),
    W41(41, 0.026712, 17, FrameType.VOICE),
    W42(42, 0.026304, 17, FrameType.VOICE),
    W43(43, 0.025906, 17, FrameType.VOICE),
    W44(44, 0.025515, 18, FrameType.VOICE),
    W45(45, 0.025129, 18, FrameType.VOICE),
    W46(46, 0.024746, 18, FrameType.VOICE),
    W47(47, 0.024372, 18, FrameType.VOICE),
    W48(48, 0.024002, 19, FrameType.VOICE),
    W49(49, 0.023636, 19, FrameType.VOICE),
    W50(50, 0.023279, 19, FrameType.VOICE),
    W51(51, 0.022926, 20, FrameType.VOICE),
    W52(52, 0.022581, 20, FrameType.VOICE),
    W53(53, 0.022236, 20, FrameType.VOICE),
    W54(54, 0.021900, 21, FrameType.VOICE),
    W55(55, 0.021570, 21, FrameType.VOICE),
    W56(56, 0.021240, 21, FrameType.VOICE),
    W57(57, 0.020920, 22, FrameType.VOICE),
    W58(58, 0.020605, 22, FrameType.VOICE),
    W59(59, 0.020294, 22, FrameType.VOICE),
    W60(60, 0.019983, 23, FrameType.VOICE),
    W61(61, 0.019685, 23, FrameType.VOICE),
    W62(62, 0.019386, 23, FrameType.VOICE),
    W63(63, 0.019094, 24, FrameType.VOICE),
    W64(64, 0.018805, 24, FrameType.VOICE),
    W65(65, 0.018520, 24, FrameType.VOICE),
    W66(66, 0.018242, 25, FrameType.VOICE),
    W67(67, 0.017965, 25, FrameType.VOICE),
    W68(68, 0.017696, 26, FrameType.VOICE),
    W69(69, 0.017431, 26, FrameType.VOICE),
    W70(70, 0.017170, 26, FrameType.VOICE),
    W71(71, 0.016911, 27, FrameType.VOICE),
    W72(72, 0.016657, 27, FrameType.VOICE),
    W73(73, 0.016409, 28, FrameType.VOICE),
    W74(74, 0.016163, 28, FrameType.VOICE),
    W75(75, 0.015923, 29, FrameType.VOICE),
    W76(76, 0.015686, 29, FrameType.VOICE),
    W77(77, 0.015411, 30, FrameType.VOICE),
    W78(78, 0.015177, 30, FrameType.VOICE),
    W79(79, 0.014946, 30, FrameType.VOICE),
    W80(80, 0.014721, 31, FrameType.VOICE),
    W81(81, 0.014496, 31, FrameType.VOICE),
    W82(82, 0.014277, 32, FrameType.VOICE),
    W83(83, 0.014061, 32, FrameType.VOICE),
    W84(84, 0.013847, 33, FrameType.VOICE),
    W85(85, 0.013636, 33, FrameType.VOICE),
    W86(86, 0.013430, 34, FrameType.VOICE),
    W87(87, 0.013227, 34, FrameType.VOICE),
    W88(88, 0.013025, 35, FrameType.VOICE),
    W89(89, 0.012829, 36, FrameType.VOICE),
    W90(90, 0.012634, 36, FrameType.VOICE),
    W91(91, 0.012444, 37, FrameType.VOICE),
    W92(92, 0.012253, 37, FrameType.VOICE),
    W93(93, 0.012068, 38, FrameType.VOICE),
    W94(94, 0.011887, 38, FrameType.VOICE),
    W95(95, 0.011703, 39, FrameType.VOICE),
    W96(96, 0.011528, 40, FrameType.VOICE),
    W97(97, 0.011353, 40, FrameType.VOICE),
    W98(98, 0.011183, 41, FrameType.VOICE),
    W99(99, 0.011011, 42, FrameType.VOICE),
    W100(100, 0.010845, 42, FrameType.VOICE),
    W101(101, 0.010681, 43, FrameType.VOICE),
    W102(102, 0.010517, 43, FrameType.VOICE),
    W103(103, 0.010359, 44, FrameType.VOICE),
    W104(104, 0.010202, 45, FrameType.VOICE),
    W105(105, 0.010050, 46, FrameType.VOICE),
    W106(106, 0.009895, 46, FrameType.VOICE),
    W107(107, 0.009747, 47, FrameType.VOICE),
    W108(108, 0.009600, 48, FrameType.VOICE),
    W109(109, 0.009453, 48, FrameType.VOICE),
    W110(110, 0.009312, 49, FrameType.VOICE),
    W111(111, 0.009172, 50, FrameType.VOICE),
    W112(112, 0.009033, 51, FrameType.VOICE),
    W113(113, 0.008896, 52, FrameType.VOICE),
    W114(114, 0.008762, 52, FrameType.VOICE),
    W115(115, 0.008633, 53, FrameType.VOICE),
    W116(116, 0.008501, 54, FrameType.VOICE),
    W117(117, 0.008375, 55, FrameType.VOICE),
    W118(118, 0.008249, 56, FrameType.VOICE),
    W119(119, 0.008125, 56, FrameType.VOICE),
    W120(120, 0, 9, FrameType.ERASURE),
    W121(121, 0, 9, FrameType.ERASURE),
    W122(122, 0, 9, FrameType.ERASURE),
    W123(123, 0, 9, FrameType.ERASURE),
    W124(124, Math.PI / 32.0, 15, FrameType.SILENCE),
    W125(125, Math.PI / 32.0, 14, FrameType.SILENCE),
    W126(126, 0, 9, FrameType.TONE),
    W127(127, 0, 9, FrameType.TONE);

    private int mIndex;
    private float mFrequency;
    private int mL;
    private FrameType mFrameType;
    private static Map<Integer,AMBEFundamentalFrequency> LOOKUP_MAP = new TreeMap<>();

    AMBEFundamentalFrequency(int index, double frequency, int l, FrameType frameType)
    {
        mIndex = index;
        mFrequency = (float)(frequency * 2.0 * Math.PI);
        mL = l;
        mFrameType = frameType;
    }

    static
    {
        for(AMBEFundamentalFrequency frequency: AMBEFundamentalFrequency.values())
        {
            LOOKUP_MAP.put(frequency.mIndex, frequency);
        }
    }

    public float getFrequency()
    {
        return mFrequency;
    }

    public int getL()
    {
        return mL;
    }
    
    public FrameType getFrameType()
    {
        return mFrameType;
    }

    public static AMBEFundamentalFrequency fromValue(int value)
    {
        AMBEFundamentalFrequency frequency = LOOKUP_MAP.get(value);

        if(frequency == null)
        {
            throw new IllegalArgumentException("Fundamental frequency value must be in the range 0 - 127.  Unrecognized: " + value);
        }

        return frequency;
    }
}
