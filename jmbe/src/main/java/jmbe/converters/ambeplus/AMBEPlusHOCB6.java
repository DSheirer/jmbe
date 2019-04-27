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
 * Higher order coefficients - b6
 */
public enum AMBEPlusHOCB6
{
    V0(new float[]{-0.429688f, -0.046875f, 0.039063f, 0.000000f}),
    V1(new float[]{-0.296875f, 0.187500f, 0.125000f, 0.015625f}),
    V2(new float[]{-0.203125f, -0.218750f, -0.039063f, -0.007813f}),
    V3(new float[]{-0.179688f, 0.007813f, -0.007813f, 0.000000f}),
    V4(new float[]{-0.171875f, 0.265625f, -0.085938f, -0.039063f}),
    V5(new float[]{-0.046875f, -0.070313f, 0.203125f, -0.023438f}),
    V6(new float[]{-0.023438f, 0.125000f, 0.031250f, -0.023438f}),
    V7(new float[]{-0.007813f, 0.000000f, -0.195313f, -0.007813f}),
    V8(new float[]{0.007813f, -0.046875f, -0.007813f, -0.015625f}),
    V9(new float[]{0.015625f, -0.031250f, 0.039063f, 0.195313f}),
    V10(new float[]{0.031250f, -0.273438f, -0.015625f, -0.007813f}),
    V11(new float[]{0.140625f, 0.257813f, 0.015625f, 0.007813f}),
    V12(new float[]{0.164063f, 0.015625f, 0.007813f, -0.023438f}),
    V13(new float[]{0.210938f, -0.148438f, -0.187500f, 0.039063f}),
    V14(new float[]{0.273438f, -0.179688f, 0.054688f, -0.007813f}),
    V15(new float[]{0.421875f, 0.054688f, -0.039063f, 0.000000f});

    private float[] mCoefficients;

    AMBEPlusHOCB6(float[] coefficients)
    {
        mCoefficients = coefficients;
    }

    public float[] getCoefficients()
    {
        return mCoefficients;
    }

    public static AMBEPlusHOCB6 fromValue(int value)
    {
        if(0 <= value && value <= 15)
        {
            return AMBEPlusHOCB6.values()[value];
        }

        throw new IllegalArgumentException("Value must be in range 0-15.  Unsupported value: " + value);
    }
}
