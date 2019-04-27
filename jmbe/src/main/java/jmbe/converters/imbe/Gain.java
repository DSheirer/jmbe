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

package jmbe.converters.imbe;

/**
 * Gain value enumeration from Annex E
 */
public enum Gain
{
    G0(-2.842205f),
    G1(-2.694235f),
    G2(-2.558260f),
    G3(-2.382850f),
    G4(-2.221042f),
    G5(-2.095574f),
    G6(-1.980845f),
    G7(-1.836058f),
    G8(-1.645556f),
    G9(-1.417658f),
    G10(-1.261301f),
    G11(-1.125631f),
    G12(-0.958207f),
    G13(-0.781591f),
    G14(-0.555837f),
    G15(-0.346976f),
    G16(-0.147249f),
    G17(-0.027755f),
    G18(0.211495f),
    G19(0.388380f),
    G20(0.552873f),
    G21(0.737223f),
    G22(0.932197f),
    G23(1.139032f),
    G24(1.320955f),
    G25(1.483433f),
    G26(1.648297f),
    G27(1.801447f),
    G28(1.942731f),
    G29(2.118613f),
    G30(2.321486f),
    G31(2.504443f),
    G32(2.653909f),
    G33(2.780654f),
    G34(2.925355f),
    G35(3.076390f),
    G36(3.220825f),
    G37(3.402869f),
    G38(3.585096f),
    G39(3.784606f),
    G40(3.955521f),
    G41(4.155636f),
    G42(4.314009f),
    G43(4.444150f),
    G44(4.577542f),
    G45(4.735552f),
    G46(4.909493f),
    G47(5.085264f),
    G48(5.254767f),
    G49(5.411894f),
    G50(5.568094f),
    G51(5.738523f),
    G52(5.919215f),
    G53(6.087701f),
    G54(6.280685f),
    G55(6.464201f),
    G56(6.647736f),
    G57(6.834672f),
    G58(7.022583f),
    G59(7.211777f),
    G60(7.471016f),
    G61(7.738948f),
    G62(8.124863f),
    G63(8.695827f);

    private float mGain;

    Gain(float gain)
    {
        mGain = gain;
    }

    public float getGain()
    {
        return mGain;
    }

    public static Gain fromValue(int value)
    {
        if(0 <= value && value <= 63)
        {
            return Gain.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-63.  Unsupported value: " + value);
    }
}
