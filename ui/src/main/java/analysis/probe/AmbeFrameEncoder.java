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

package analysis.probe;

import jmbe.binary.BinaryFrame;
import jmbe.edac.Golay23;
import jmbe.edac.Golay24;

import java.nio.ByteOrder;

/**
 * Builds a 72-bit AMBE+2 (3600 x 2450, the DMR/NXDN full-rate channel format) frame from the quantizer
 * indices b0..b8. This is the exact inverse of {@link jmbe.codec.ambe.AMBEFrame#decode()}: the same bit
 * vectors, Golay(24,12) on C0, Golay(23,12) on C1 scrambled by the U0-seeded modulation vector, and C2/C3
 * left uncoded. A frame built here decodes in JMBE with zero errors and is what the probe experiment sends
 * to the chip, so the chip and JMBE always see identical indices.
 */
public final class AmbeFrameEncoder
{
    private static final int[] VECTOR_C0 = {0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 68, 1, 5,
        9, 13, 17, 21};
    private static final int[] VECTOR_C1 = {25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 65, 69, 2, 6, 10, 14, 18, 22, 26,
        30, 34, 38, 42};
    private static final int[] VECTOR_C2 = {46, 50, 54, 58, 62, 66, 70, 3, 7, 11, 15};
    private static final int[] VECTOR_C3 = {19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71};

    /** Index bit widths: b0 7, b1 5, b2 5, b3 9, b4 7, b5 5, b6 4, b7 4, b8 3 (49 bits). */
    public static final int[] B_BITS = {7, 5, 5, 9, 7, 5, 4, 4, 3};

    private AmbeFrameEncoder()
    {
    }

    /**
     * @param b quantizer indices b0..b8
     * @return 9-byte frame in the byte layout that {@link jmbe.codec.ambe.AMBEFrame} and the ThumbDV decode request use
     */
    public static byte[] encode(int[] b)
    {
        if(b.length != 9)
        {
            throw new IllegalArgumentException("Expected 9 indices b0..b8");
        }

        for(int i = 0; i < 9; i++)
        {
            if(b[i] < 0 || b[i] >= (1 << B_BITS[i]))
            {
                throw new IllegalArgumentException("b" + i + " out of range: " + b[i]);
            }
        }

        // u0 (12 bits): b0[6:3] b1[4:1] b2[4:1]
        int u0 = ((b[0] >> 3) << 8) | ((b[1] >> 1) << 4) | (b[2] >> 1);
        // u1 (12 bits): b3[8:1] b4[6:3]
        int u1 = ((b[3] >> 1) << 4) | (b[4] >> 3);
        // u2 (11 bits): b5[4:1] b6[3:1] b7[3:1] b8[2]
        int u2 = ((b[5] >> 1) << 7) | ((b[6] >> 1) << 4) | ((b[7] >> 1) << 1) | (b[8] >> 2);
        // u3 (14 bits): b1[0] b2[0] b0[2:0] b4[2:0] b3[0] b5[0] b6[0] b7[0] b8[1:0]
        int u3 = ((b[1] & 1) << 13) | ((b[2] & 1) << 12) | ((b[0] & 7) << 9) | ((b[4] & 7) << 6) | ((b[3] & 1) << 5)
            | ((b[5] & 1) << 4) | ((b[6] & 1) << 3) | ((b[7] & 1) << 2) | (b[8] & 3);

        return encodeVectors(u0, u1, u2, u3);
    }

    /** b0 value that marks a tone frame in a probe plan's index vector (mbelib treats b0 126/127 as tone). */
    public static final int TONE_B0 = 126;
    /**
     * Number of tone frame layouts {@link #encodeTone} writes. One: the TIA-102.BABA-1 Table 10 layout. (The first
     * response run tried three guessed layouts; the chip flagged all of them INVALID DATA.)
     */
    public static final int TONE_VARIANTS = 1;

    /**
     * Encodes a probe plan entry: a voice frame from b0..b8, or, when b[0] == TONE_B0, a tone frame with tone id b[1]
     * and amplitude b[2].
     */
    public static byte[] encodeAny(int[] b)
    {
        return b[0] == TONE_B0 ? encodeTone(b[1], b[2]) : encode(b);
    }

    /**
     * AMBE+2 tone frame, TIA-102.BABA-1 Table 10 (bit 0 = LSB of each vector):
     *   u0(11..6) = 63, u0(5..0) = AD(6..1)
     *   u1(11..4) = ID(7..0), u1(3..0) = ID(7..4)
     *   u2(10..7) = ID(3..0), u2(6..0) = ID(7..1)
     *   u3(13) = ID(0), u3(12..5) = ID(7..0), u3(4) = AD(0), u3(3..0) = 0
     *
     * @param id tone index (5..122 single tones at id * 31.25 Hz, 128..163 DTMF/Knox/call progress, 255 silence)
     * @param ad amplitude, 0..127 (spec: 0.711 dB per step, 127 = +3.17 dBm0)
     */
    public static byte[] encodeTone(int id, int ad)
    {
        if(id < 0 || id > 255 || ad < 0 || ad > 127)
        {
            throw new IllegalArgumentException("tone id " + id + ", ad " + ad);
        }
        int u0 = (63 << 6) | (ad >> 1);
        int u1 = (id << 4) | (id >> 4);
        int u2 = ((id & 0xF) << 7) | (id >> 1);
        int u3 = ((id & 1) << 13) | (id << 5) | ((ad & 1) << 4);
        return encodeVectors(u0, u1, u2, u3);
    }

    /**
     * Channel bit errors: flips the C0 codeword bits set in c0Mask (24 bits, MSB = codeword bit 0) and the C1 codeword
     * bits set in c1Mask (23 bits, MSB = codeword bit 0) of an encoded frame. C1 is scrambled by XOR, so a flipped
     * channel bit is a flipped codeword bit after descrambling.
     */
    public static byte[] withErrors(byte[] frame, int c0Mask, int c1Mask)
    {
        byte[] out = frame.clone();
        for(int i = 0; i < 24; i++)
        {
            if(((c0Mask >> (23 - i)) & 1) == 1)
            {
                int bit = VECTOR_C0[i];
                out[bit / 8] ^= (byte)(0x80 >> (bit % 8));
            }
        }
        for(int i = 0; i < 23; i++)
        {
            if(((c1Mask >> (22 - i)) & 1) == 1)
            {
                int bit = VECTOR_C1[i];
                out[bit / 8] ^= (byte)(0x80 >> (bit % 8));
            }
        }
        return out;
    }

    private static byte[] encodeVectors(int u0, int u1, int u2, int u3)
    {
        int c0 = golay24(u0);
        int c1 = golay23(u1) ^ modulationVector(u0);

        BinaryFrame frame = new BinaryFrame(72);
        put(frame, VECTOR_C0, c0);
        put(frame, VECTOR_C1, c1);
        put(frame, VECTOR_C2, u2);
        put(frame, VECTOR_C3, u3);

        byte[] out = new byte[9];
        for(int i = 0; i < 72; i++)
        {
            if(frame.get(i))
            {
                out[i / 8] |= (byte)(0x80 >> (i % 8));
            }
        }

        // Guard: the byte packing must be the inverse of BinaryFrame.fromBytes(LITTLE_ENDIAN).
        if(!BinaryFrame.fromBytes(out, ByteOrder.LITTLE_ENDIAN).equals(frame))
        {
            throw new IllegalStateException("Byte packing does not match BinaryFrame.fromBytes");
        }

        return out;
    }

    /** Writes value MSB-first into the frame bits named by indices (inverse of BinaryFrame.getInt(int[])). */
    private static void put(BinaryFrame frame, int[] indices, int value)
    {
        for(int i = 0; i < indices.length; i++)
        {
            if(((value >> (indices.length - 1 - i)) & 1) == 1)
            {
                frame.set(indices[i]);
            }
        }
    }

    /** 12 data bits -> 23-bit codeword, data in the top 12 bits, matching Golay23.calculateChecksum. */
    static int golay23(int data)
    {
        int checksum = 0;
        for(int i = 0; i < 12; i++)
        {
            if(((data >> (11 - i)) & 1) == 1)
            {
                checksum ^= Golay23.CHECKSUMS[i];
            }
        }
        return (data << 11) | checksum;
    }

    /** 12 data bits -> 24-bit codeword (Golay23 checksum plus even overall parity bit). */
    static int golay24(int data)
    {
        int checksum = 0;
        for(int i = 0; i < 12; i++)
        {
            if(((data >> (11 - i)) & 1) == 1)
            {
                checksum ^= Golay24.CHECKSUMS[i];
            }
        }
        int word = (data << 11) | checksum;
        int parity = Integer.bitCount(word) & 1;
        return (word << 1) | parity;
    }

    /** AMBEFrame.getModulationVector as a 23-bit int, first generated bit is the MSB. */
    static int modulationVector(int seed)
    {
        int pr = 16 * seed;
        int v = 0;
        for(int x = 0; x < 23; x++)
        {
            pr = (173 * pr + 13849) % 65536;
            v = (v << 1) | (pr >= 32768 ? 1 : 0);
        }
        return v;
    }

    public static String toHex(byte[] frame)
    {
        StringBuilder sb = new StringBuilder();
        for(byte value : frame)
        {
            sb.append(String.format("%02X", value & 0xFF));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex)
    {
        byte[] data = new byte[hex.length() / 2];
        for(int x = 0; x < hex.length(); x += 2)
        {
            data[x / 2] = (byte)Integer.parseInt(hex.substring(x, x + 2), 16);
        }
        return data;
    }
}
