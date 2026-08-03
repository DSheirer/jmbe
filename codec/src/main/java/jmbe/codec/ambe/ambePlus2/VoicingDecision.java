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
 * AMBE Voice/Unvoiced Quantization Vector enumeration: voiced (true) or unvoiced per 500 Hz band, 0..4 kHz.
 */
public enum VoicingDecision
{
    V0(new boolean[]{true, true, true, true, true, true, true, true}),
    V1(new boolean[]{true, true, true, true, true, true, true, false}), //Different from published ICD
    V2(new boolean[]{true, true, true, true, true, true, true, false}),
    V3(new boolean[]{true, true, true, true, true, false, true, true}), //Different from published ICD
    V4(new boolean[]{true, true, true, true, true, true, false, false}),
    V5(new boolean[]{true, true, false, true, true, true, true, true}),
    V6(new boolean[]{true, true, true, false, true, true, true, true}),
    V7(new boolean[]{true, true, true, true, true, false, true, true}),
    V8(new boolean[]{true, true, true, true, false, false, false, false}),
    V9(new boolean[]{true, true, true, true, true, false, false, false}),
    V10(new boolean[]{true, true, true, false, false, false, false, false}),
    V11(new boolean[]{true, true, true, false, false, false, false, false}), //Different from published ICD
    V12(new boolean[]{true, true, false, false, false, false, false, false}),
    V13(new boolean[]{true, true, false, false, false, false, false, false}), //Different from published ICD
    V14(new boolean[]{true, false, false, false, false, false, false, false}),
    V15(new boolean[]{false, false, false, false, false, false, false, false}), //Different from published ICD
    V16(new boolean[]{false, false, false, false, false, false, false, false}),
    V17(new boolean[]{false, false, false, false, false, false, false, false}),
    V18(new boolean[]{false, false, false, false, false, false, false, false}),
    V19(new boolean[]{false, false, false, false, false, false, false, false}),
    V20(new boolean[]{false, false, false, false, false, false, false, false}),
    V21(new boolean[]{false, false, false, false, false, false, false, false}),
    V22(new boolean[]{false, false, false, false, false, false, false, false}),
    V23(new boolean[]{false, false, false, false, false, false, false, false}),
    V24(new boolean[]{false, false, false, false, false, false, false, false}),
    V25(new boolean[]{false, false, false, false, false, false, false, false}),
    V26(new boolean[]{false, false, false, false, false, false, false, false}),
    V27(new boolean[]{false, false, false, false, false, false, false, false}),
    V28(new boolean[]{false, false, false, false, false, false, false, false}),
    V29(new boolean[]{false, false, false, false, false, false, false, false}),
    V30(new boolean[]{false, false, false, false, false, false, false, false}),
    V31(new boolean[]{false, false, false, false, false, false, false, false});

    private final boolean[] mVoiceDecisions;

    VoicingDecision(boolean[] voiceDecisions)
    {
        mVoiceDecisions = voiceDecisions;
    }

    public boolean isVoiced(int index)
    {
        if(0 <= index && index <= 7)
        {
            return mVoiceDecisions[index];
        }

        throw new IllegalArgumentException("Voice decision index must be in range 0-7.  Unsupported index: " + index);
    }

    public static VoicingDecision fromValue(int value)
    {
        if(0 <= value && value <= 31)
        {
            return VoicingDecision.values()[value];
        }

        throw new IllegalArgumentException("Quantization vector values must be in range 0-31.  Unsupported value: " + value);
    }
}

