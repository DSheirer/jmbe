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
 * Differential gain value enumeration.
 */
public enum AMBEPlusDifferentialGain
{
    G0(0.000000f),
    G1(0.118200f),
    G2(0.215088f),
    G3(0.421167f),
    G4(0.590088f),
    G5(0.749075f),
    G6(0.879395f),
    G7(0.996388f),
    G8(1.092285f),
    G9(1.171577f),
    G10(1.236572f),
    G11(1.313450f),
    G12(1.376465f),
    G13(1.453342f),
    G14(1.516357f),
    G15(1.600346f),
    G16(1.669189f),
    G17(1.742847f),
    G18(1.803223f),
    G19(1.880234f),
    G20(1.943359f),
    G21(2.025067f),
    G22(2.092041f),
    G23(2.178042f),
    G24(2.248535f),
    G25(2.331718f),
    G26(2.399902f),
    G27(2.492343f),
    G28(2.568115f),
    G29(2.658677f),
    G30(2.732910f),
    G31(2.816496f),
    G32(2.885010f),
    G33(2.956386f),
    G34(3.014893f),
    G35(3.078890f),
    G36(3.131348f),
    G37(3.206615f),
    G38(3.268311f),
    G39(3.344785f),
    G40(3.407471f),
    G41(3.484885f),
    G42(3.548340f),
    G43(3.623339f),
    G44(3.684814f),
    G45(3.764509f),
    G46(3.829834f),
    G47(3.915298f),
    G48(3.985352f),
    G49(4.072560f),
    G50(4.144043f),
    G51(4.231251f),
    G52(4.302734f),
    G53(4.399066f),
    G54(4.478027f),
    G55(4.572883f),
    G56(4.650635f),
    G57(4.760785f),
    G58(4.851074f),
    G59(4.972361f),
    G60(5.071777f),
    G61(5.226203f),
    G62(5.352783f),
    G63(5.352783f);

    private float mGain;

    AMBEPlusDifferentialGain(float gain)
    {
        mGain = gain;
    }

    public float getGain()
    {
        return mGain;
    }

    public static AMBEPlusDifferentialGain fromValue(int value)
    {
        if(0 <= value && value <= 63)
        {
            return AMBEPlusDifferentialGain.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-63.  Unsupported value: " + value);
    }
}
