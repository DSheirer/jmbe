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

package jmbe.codec.ambe.ambePlus2;

/**
 * Differential gain value enumeration.
 */
public enum DifferentialGain
{
    G0(-2.00000f),
    G1(-0.67000f),
    G2(0.297941f),
    G3(0.663728f),
    G4(1.036829f),
    G5(1.438136f),
    G6(1.890077f),
    G7(2.227970f),
    G8(2.478289f),
    G9(2.667544f),
    G10(2.793619f),
    G11(2.893261f),
    G12(3.020630f),
    G13(3.138586f),
    G14(3.237579f),
    G15(3.322570f),
    G16(3.432367f),
    G17(3.571863f),
    G18(3.696650f),
    G19(3.814917f),
    G20(3.920932f),
    G21(4.022503f),
    G22(4.123569f),
    G23(4.228291f),
    G24(4.370569f),
    G25(4.543700f),
    G26(4.707695f),
    G27(4.848879f),
    G28(5.056757f),
    G29(5.326468f),
    G30(5.777581f),
    G31(6.874496f);

    private final float mGain;

    DifferentialGain(float gain)
    {
        mGain = gain;
    }

    public float getGain()
    {
        return mGain;
    }

    public static DifferentialGain fromValue(int value)
    {
        if(0 <= value && value <= 31)
        {
            return DifferentialGain.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-31.  Unsupported value: " + value);
    }
}
