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

import jmbe.codec.FrameType;
import jmbe.codec.IFundamentalFrequency;

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fundamental frequency enumeration used for decoding the value of the information vector b0 using the formulas
 * detailed in section 6.1 of the vocoder specification.
 *
 * Enumeration entries are constrained to the range 0 to 207.  Values 208 to 255 are reserved for future use.
 *
 * Fundamental Frequency w0 = 4.0 * Pi / ( index + 39.5 )
 * L = floor( .9254 * floor( ( Pi / w0 ) + 0.25 )
 */

public enum IMBEFundamentalFrequency implements IFundamentalFrequency
{
    W0(0),
    W1(1),
    W2(2),
    W3(3),
    W4(4),
    W5(5),
    W6(6),
    W7(7),
    W8(8),
    W9(9),
    W10(10),
    W11(11),
    W12(12),
    W13(13),
    W14(14),
    W15(15),
    W16(16),
    W17(17),
    W18(18),
    W19(19),
    W20(20),
    W21(21),
    W22(22),
    W23(23),
    W24(24),
    W25(25),
    W26(26),
    W27(27),
    W28(28),
    W29(29),
    W30(30),
    W31(31),
    W32(32),
    W33(33),
    W34(34),
    W35(35),
    W36(36),
    W37(37),
    W38(38),
    W39(39),
    W40(40),
    W41(41),
    W42(42),
    W43(43),
    W44(44),
    W45(45),
    W46(46),
    W47(47),
    W48(48),
    W49(49),
    W50(50),
    W51(51),
    W52(52),
    W53(53),
    W54(54),
    W55(55),
    W56(56),
    W57(57),
    W58(58),
    W59(59),
    W60(60),
    W61(61),
    W62(62),
    W63(63),
    W64(64),
    W65(65),
    W66(66),
    W67(67),
    W68(68),
    W69(69),
    W70(70),
    W71(71),
    W72(72),
    W73(73),
    W74(74),
    W75(75),
    W76(76),
    W77(77),
    W78(78),
    W79(79),
    W80(80),
    W81(81),
    W82(82),
    W83(83),
    W84(84),
    W85(85),
    W86(86),
    W87(87),
    W88(88),
    W89(89),
    W90(90),
    W91(91),
    W92(92),
    W93(93),
    W94(94),
    W95(95),
    W96(96),
    W97(97),
    W98(98),
    W99(99),
    W100(100),
    W101(101),
    W102(102),
    W103(103),
    W104(104),
    W105(105),
    W106(106),
    W107(107),
    W108(108),
    W109(109),
    W110(110),
    W111(111),
    W112(112),
    W113(113),
    W114(114),
    W115(115),
    W116(116),
    W117(117),
    W118(118),
    W119(119),
    W120(120),
    W121(121),
    W122(122),
    W123(123),
    W124(124),
    W125(125),
    W126(126),
    W127(127),
    W128(128),
    W129(129),
    W130(130),
    W131(131),
    W132(132),
    W133(133),
    W134(134),
    W135(135),
    W136(136),
    W137(137),
    W138(138),
    W139(139),
    W140(140),
    W141(141),
    W142(142),
    W143(143),
    W144(144),
    W145(145),
    W146(146),
    W147(147),
    W148(148),
    W149(149),
    W150(150),
    W151(151),
    W152(152),
    W153(153),
    W154(154),
    W155(155),
    W156(156),
    W157(157),
    W158(158),
    W159(159),
    W160(160),
    W161(161),
    W162(162),
    W163(163),
    W164(164),
    W165(165),
    W166(166),
    W167(167),
    W168(168),
    W169(169),
    W170(170),
    W171(171),
    W172(172),
    W173(173),
    W174(174),
    W175(175),
    W176(176),
    W177(177),
    W178(178),
    W179(179),
    W180(180),
    W181(181),
    W182(182),
    W183(183),
    W184(184),
    W185(185),
    W186(186),
    W187(187),
    W188(188),
    W189(189),
    W190(190),
    W191(191),
    W192(192),
    W193(193),
    W194(194),
    W195(195),
    W196(196),
    W197(197),
    W198(198),
    W199(199),
    W200(200),
    W201(201),
    W202(202),
    W203(203),
    W204(204),
    W205(205),
    W206(206),
    W207(207),
    DEFAULT(134),  //L = 30 & w0 = 0.2985 * Pi
    INVALID(-1);

    private int mIndex;
    private int mL;
    private float mFrequency;
    private static Map<Integer,IMBEFundamentalFrequency> LOOKUP_MAP = new TreeMap<>();
    private static final EnumSet<IMBEFundamentalFrequency> VALID_VALUES = EnumSet.range(W0, W207);

    IMBEFundamentalFrequency(int index)
    {
        mIndex = index;
        mFrequency = (float)(4.0 * Math.PI / ((double)index + 39.5));
        mL = (int)Math.floor(0.9254 * Math.floor((Math.PI / mFrequency) + 0.25));
    }

    static
    {
        for(IMBEFundamentalFrequency frequency:VALID_VALUES)
        {
            LOOKUP_MAP.put(frequency.mIndex, frequency);
        }
    }

    public int getL()
    {
        return mL;
    }

    @Override
    public FrameType getFrameType()
    {
        return FrameType.VOICE;
    }

    public float getFrequency()
    {
        return mFrequency;
    }

    public static IMBEFundamentalFrequency fromValue(int value)
    {
        IMBEFundamentalFrequency frequency = LOOKUP_MAP.get(value);

        if(frequency != null)
        {
            return frequency;
        }

        return IMBEFundamentalFrequency.INVALID;
    }
}
