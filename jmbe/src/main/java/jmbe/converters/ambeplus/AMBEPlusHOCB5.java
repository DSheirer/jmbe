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
 * Higher order coefficients - b5
 */
public enum AMBEPlusHOCB5
{
    V0(new float[]{-0.617188f, -0.015625f, 0.015625f, -0.023438f}),
    V1(new float[]{-0.507813f, -0.382813f, -0.312500f, -0.117188f}),
    V2(new float[]{-0.328125f, 0.046875f, 0.007813f, -0.015625f}),
    V3(new float[]{-0.320313f, -0.281250f, -0.023438f, -0.023438f}),
    V4(new float[]{-0.171875f, 0.140625f, -0.179688f, -0.007813f}),
    V5(new float[]{-0.148438f, 0.226563f, 0.039063f, -0.039063f}),
    V6(new float[]{-0.140625f, -0.007813f, -0.007813f, -0.015625f}),
    V7(new float[]{-0.109375f, -0.101563f, 0.179688f, -0.062500f}),
    V8(new float[]{-0.109375f, -0.109375f, -0.031250f, 0.187500f}),
    V9(new float[]{-0.109375f, -0.218750f, -0.273438f, -0.140625f}),
    V10(new float[]{0.007813f, -0.007813f, -0.015625f, -0.015625f}),
    V11(new float[]{0.078125f, -0.265625f, -0.007813f, 0.007813f}),
    V12(new float[]{0.101563f, 0.054688f, -0.210938f, -0.007813f}),
    V13(new float[]{0.164063f, 0.242188f, 0.093750f, 0.039063f}),
    V14(new float[]{0.179688f, -0.023438f, 0.007813f, -0.007813f}),
    V15(new float[]{0.460938f, 0.015625f, -0.015625f, 0.007813f});

    private float[] mCoefficients;

    AMBEPlusHOCB5(float[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public float[] getCoefficients()
    {
        return mCoefficients;
    }

    public static AMBEPlusHOCB5 fromValue(int value)
    {
        if(0 <= value && value <= 15)
        {
            return AMBEPlusHOCB5.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-15.  Unsupported value: " + value);
    }
}
