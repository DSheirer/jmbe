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
 * Higher order coefficients - b8
 */
public enum HOCB8
{
    V0(new float[] {0.323968f, 0.008964f, -0.063117f, 0.027909f}),
    V1(new float[] {0.010900f, -0.004030f, -0.125016f, -0.080818f}),
    V2(new float[] {0.109969f, 0.256272f, 0.042470f, 0.000749f}),
    V3(new float[] {-0.135446f, 0.201769f, -0.083426f, 0.093888f}),
    V4(new float[] {-0.441995f, 0.038159f, 0.022784f, 0.003943f}),
    V5(new float[] {-0.155951f, 0.032467f, 0.145309f, -0.041725f}),
    V6(new float[] {-0.149182f, -0.223356f, -0.065793f, 0.075016f}),
    V7(new float[] {0.096949f, -0.096400f, 0.083194f, 0.049306f});

    private final float[] mCoefficients;

    HOCB8(float[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public float[] getCoefficients()
    {
        return mCoefficients;
    }

    public static HOCB8 fromValue(int value)
    {
        if(0 <= value && value <= 7)
        {
            return HOCB8.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-7.  Unsupported value: " + value);
    }
}
