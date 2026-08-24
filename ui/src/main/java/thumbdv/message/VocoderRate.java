/*
 *
 *  * ******************************************************************************
 *  * Copyright (C) 2014-2019 Dennis Sheirer
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *  * *****************************************************************************
 *
 *
 */

package thumbdv.message;

public enum VocoderRate
{
    //AMBE-1000 Standard Rates
    RATE_0(0x00, 2400,0),
    RATE_1(0x01, 3600,0),
    RATE_2(0x02, 3600,1200),
    RATE_3(0x03, 4800,0),
    RATE_4(0x04, 9600,0),
    RATE_5(0x05, 2350,50),
    RATE_6(0x06, 4850,4750),
    RATE_7(0x07, 4550,250),
    RATE_8(0x08, 3100,1700),
    RATE_9(0x09, 4400,2800),
    RATE_10(0x0A, 4150,2250),
    RATE_11(0x0B, 3350,250),
    RATE_12(0x0C, 7750,250),
    RATE_13(0x0D, 4650,3350),
    RATE_14(0x0E, 3750,250),
    RATE_15(0x0F, 4000,0),

    //AMBE-2000 Standard Rates
    RATE_16(0x10, 3600,0),
    RATE_17(0x11, 4000,0),
    RATE_18(0x12, 4800,0),
    RATE_19(0x13, 6400,0),
    RATE_20(0x14, 8000,0),
    RATE_21(0x15, 9600,0),
    RATE_22(0x16, 2400,1600),
    RATE_23(0x17, 3600,1200),
    RATE_24(0x18, 4000,800),
    RATE_25(0x19, 2400,2400),
    RATE_26(0x1A, 4000,2400),
    RATE_27(0x1B, 4400,2800), //Is this compatible with APCO25 Phase 1?
    RATE_28(0x1C, 4000,4000),
    RATE_29(0x1D, 2400,7200),
    RATE_30(0x1E, 3600,6000),
    RATE_31(0x1F, 2000,0),
    RATE_32(0x20, 3600,2800),

    //AMBE-3000 Standard Rates (AMBE+2)
    RATE_33(0x21, 2450,1150), //APCO25 HR (Phase 2), DMR, NXDN, DSTAR
    RATE_34(0x22, 2450,0),
    RATE_35(0x23, 2250,1150),
    RATE_36(0x24, 2250,0),
    RATE_37(0x25, 2400,0),
    RATE_38(0x26, 3000,0),
    RATE_39(0x27, 3600,0),
    RATE_40(0x28, 4000,0),
    RATE_41(0x29, 4400,0),
    RATE_42(0x2A, 4800,0),
    RATE_43(0x2B, 6400,0),
    RATE_44(0x2C, 7200,0),
    RATE_45(0x2D, 8000,0),
    RATE_46(0x2E, 9600,0),
    RATE_47(0x2F, 2450,250),
    RATE_48(0x30, 3350,250),
    RATE_49(0x31, 3750,250),
    RATE_50(0x32, 4550,250),
    RATE_51(0x33, 2450,1950),
    RATE_52(0x34, 2450,2350),
    RATE_53(0x35, 2450,3550),
    RATE_54(0x36, 2450,4750),
    RATE_55(0x37, 2600,1400),
    RATE_56(0x38, 3600,1200),
    RATE_57(0x39, 4000,800),
    RATE_58(0x3A, 4000,2400),
    RATE_59(0x3B, 4400,2800), //Is this compatible with APCO25 Phase 1?
    RATE_60(0x3C, 4000,4000),
    RATE_61(0x3D, 3600,6000);

    private final int mCode;
    private final int mSpeechRate;
    private final int mFecRate;

    /**
     * Constructs an instance of VocoderRate using the specified code, speech rate and forward error correction rate.
     *
     * @param code binary value of the vocoder rate
     * @param speechRate in bits per second (bps)
     * @param fecRate in bits per second (bps)
     */
    VocoderRate(int code, int speechRate, int fecRate)
    {
        mCode = code;
        mSpeechRate = speechRate;
        mFecRate = fecRate;
    }

    /**
     * Byte code value for the vocoder rate
     */
    public byte getCode()
    {
        return (byte)mCode;
    }

    /**
     * Speech rate in bits per second (bps)
     */
    public int getSpeechRate()
    {
        return mSpeechRate;
    }

    /**
     * Forward Error Correction rate in bits per second (bps)
     */
    public int getFecRate()
    {
        return mFecRate;
    }
}
