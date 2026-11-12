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

package thumbdv.message.type;

/**
 * AMBE-3000R vocoder rates and rate control words (RCW0 - RCW5) for each predefined rate, from Table
 * 115 (Rate Control Words and Pin Settings) of the AMBE-3000R Vocoder Chip Users Manual.  Use the words with the
 * PKT_RATEP packet field to configure the vocoder and FEC rate.
 *
 * Rate codeword structure:
 *
 * RCW0: vocoder and speech rate
 * Bits (15-8): Vocoder family (0=AMBE, 1=AMBE+, 2=AMBE+ 2400bps DSTAR, 4=AMBE+2)
 * Bits  (7-0): Speech bits per frame.
 *
 * RCW1: unknown vocoder parameters
 *
 * RCW2: FEC layout
 * Bits (15-13): Number of Golay(24,12) codewords.  PN scrambling applies when this is the first codeword.
 * Bits (12-10): Number of Golay(23,12) codewords
 * Bits   (9-7): Number of Hamming(16,11) codewords
 * Bits   (6-4): Number of Hamming(15,11) codewords
 * Bits   (3-2): Convolutional mode, block described by RCW3 (block 1, sent in clear)
 * Bits   (1-0): Convolutional mode, block described by RCW4 (only block or block 2, PN-scrambled)
 *
 * Convolutional Mode (value, mother code, rate)
 * 0 = no block
 * 1 = (171,133), Rate 1/2
 * 2 = (173,155,113), Rate 1/3
 * 3 = (163,147,135,135-repeated), Rate 1/4
 *
 * Convolutional Mode combinations Bits 3-0:
 * 1 = -, 1/2
 * 2 = -, 1/3
 * 3 = -, 1/4
 * 5 = 1/2, 1/2
 * 9 = 1/3, 1/2
 * A = 1/3, 1/3
 * E = 1/4, 1/3
 *
 * RCW3/RCW4: convolutional block lengths
 * Bits (15-8): Coded output bits for the block
 * Bits  (7-0): Encoder input bits = info bits + 6 tail bits
 *
 * RCW5: frame length
 * Bits (15-8): reserved
 * Bits  (7-0): total channel bits per frame
 *
 * Entries are named for the vocoder family (selected by the RCW0 high byte), the raw (speech) rate and the FEC rate.
 * <p>
 * Each entry documents the FEC frame layout.  Bit numbering:
 * <ul>
 * <li>Speech bit n is bit n of the chip's speech-only frame for the same vocoder (RCW0 and RCW1 unchanged, RCW2 - RCW4
 *     = 0, RCW5 = speech bits per frame), counting from 0 at the first bit sent.</li>
 * <li>Channel bit n is bit n of the FEC frame, counting from 0 at the first bit sent (MSB first in the PKT_CHAND
 *     field).</li>
 * <li>{@code a-b/s} means a, a+s, a+2s, ... b (counting down when a &gt; b); {@code a-b} means step 1.</li>
 * </ul>
 * Block codewords are listed MSB first: info bits in codeword order, then parity bits in codeword order (Golay parity
 * follows the standard P25 / DMR Golay(23,12) table, with the Golay(24,12) overall parity bit last).  Codeword c0 is
 * never scrambled.  When c0 is a Golay(24,12), its 12 info bits (first bit = MSB) seed the DMR / P25 PN sequence
 * (p0 = 16 * seed, p(n) = (173 * p(n-1) + 13849) mod 65536, PN bit = MSB) and each later codeword bit is XORed with
 * the PN bit shown, consumed in codeword bit order.
 * <p>
 * Convolutional blocks use constraint length 7 generators (octal, MSB = tap on the newest bit), start from the zero
 * state and end with 6 zero tail bits.  At each encoder step the listed generators' outputs are sent in the order
 * shown, except those punctured; a repeated generator sends its output twice.  Outputs with identical windows (for
 * example every generator at step 0) carry the same value, so their order among themselves is arbitrary.
 */
public enum VocoderRate
{
    //2000 bps total rate
    /**
     * AMBE+ vocoder, 2000 bps = 2000 bps speech + 0 bps FEC. 40 channel bits per 20 ms frame. Rate index 31 (0x1F).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 40 bits.
     */
    AMBE_PLUS_2000_0(0x1F, 2000, 0, 0x0128, 0x0663, 0x0000, 0x0000, 0x0000, 0x6428),

    //2250 bps total rate
    /**
     * AMBE+2 vocoder, 2250 bps = 2250 bps speech + 0 bps FEC. 45 channel bits per 20 ms frame. Rate index 36
     * (0x24).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 45 bits.
     */
    AMBE_PLUS2_2250_0(0x24, 2250, 0, 0x042D, 0x0754, 0x0000, 0x0000, 0x0000, 0x722D),

    //2400 bps total rate
    /**
     * AMBE vocoder, 2400 bps = 2400 bps speech + 0 bps FEC. 48 channel bits per 20 ms frame. Rate index 0 (0x00).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 48 bits.
     */
    AMBE_2400_0(0x00, 2400, 0, 0x0030, 0x0763, 0x0000, 0x0000, 0x0000, 0x4330),
    /**
     * AMBE vocoder, 2400 bps = 2350 bps speech + 50 bps FEC. 48 channel bits per 20 ms frame. Rate index 5 (0x05).
     * <p>
     * No error correction: 47 speech bits at channel bits 0-46, then channel bit 47, which is always 0.
     */
    AMBE_2350_50(0x05, 2350, 50, 0x002F, 0x0763, 0x0000, 0x0000, 0x0000, 0x6930),
    /**
     * AMBE+2 vocoder, 2400 bps = 2400 bps speech + 0 bps FEC. 48 channel bits per 20 ms frame. Rate index 37
     * (0x25).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 48 bits.
     */
    AMBE_PLUS2_2400_0(0x25, 2400, 0, 0x0430, 0x0754, 0x0000, 0x0000, 0x0000, 0x6930),

    //2450 bps total rate
    /**
     * AMBE+2 vocoder, 2450 bps = 2450 bps speech + 0 bps FEC. 49 channel bits per 20 ms frame. Rate index 34
     * (0x22).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 49 bits.
     */
    AMBE_PLUS2_2450_0(0x22, 2450, 0, 0x0431, 0x0754, 0x0000, 0x0000, 0x0000, 0x7031),

    //2700 bps total rate
    /**
     * AMBE+2 vocoder, 2700 bps = 2450 bps speech + 250 bps FEC. 54 channel bits per 20 ms frame. Rate index 47
     * (0x2F).
     * <p>
     * FEC: Hamming(16,11), with 38 unprotected speech bits. 49 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0-30/3 at channel bits 0-30/3. Parity at channel bits
     *     33-45/3 (in channel order).</li>
     * <li>Unprotected speech bits 1, 2, 4, 5, 7, 8, 10, 11, 13, 14, 16, 17, 19, 20, 22, 23, 25, 26, 28, 29, 31-48
     *     at channel bits 16, 17, 19, 20, 22, 23, 25, 26, 28, 29, 31, 32, 34, 35, 37, 38, 40, 41, 43, 44, 46-53, 1,
     *     2, 4, 5, 7, 8, 10, 11, 13, 14.</li>
     * </ul>
     */
    AMBE_PLUS2_2450_250(0x2F, 2450, 250, 0x0431, 0x0754, 0x0080, 0x0000, 0x0000, 0x3936),

    //3000 bps total rate
    /**
     * AMBE+2 vocoder, 3000 bps = 3000 bps speech + 0 bps FEC. 60 channel bits per 20 ms frame. Rate index 38
     * (0x26).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 60 bits.
     */
    AMBE_PLUS2_3000_0(0x26, 3000, 0, 0x043C, 0x0766, 0x0000, 0x0000, 0x0000, 0x673C),

    //3400 bps total rate
    /**
     * AMBE+2 vocoder, 3400 bps = 2250 bps speech + 1150 bps FEC. 68 channel bits per 20 ms frame. Rate index 35
     * (0x23).
     * <p>
     * FEC: Golay(24,12) + Golay(23,12) (PN), with 21 unprotected speech bits. 45 speech bits + 23 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-27/3, 29, 31 at channel bits 0-44/4. Parity at channel bits
     *     48-56/4, 59-65/3, 1-21/4.</li>
     * <li>c1 Golay(23,12), PN[0..22]. Speech bits 33-43/2, 1-16/3 at channel bits 25-57/4, 60-66/3. Parity at
     *     channel bits 2-42/4.</li>
     * <li>Unprotected speech bits 2-17/3, 19, 20, 22, 23, 25, 26-44/2 at channel bits 23-43/4, 46, 47, 50, 51, 54,
     *     55-67/3, 3-19/4.</li>
     * </ul>
     */
    AMBE_PLUS2_2250_1150(0x23, 2250, 1150, 0x042D, 0x0754, 0x2400, 0x0000, 0x0000, 0x7944),

    //3600 bps total rate
    /**
     * AMBE vocoder, 3600 bps = 3600 bps speech + 0 bps FEC. 72 channel bits per 20 ms frame. Rate index 1 (0x01).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 72 bits.
     */
    AMBE_3600_0(0x01, 3600, 0, 0x0048, 0x0767, 0x0000, 0x0000, 0x0000, 0x6F48),
    /**
     * AMBE vocoder, 3600 bps = 3350 bps speech + 250 bps FEC. 72 channel bits per 20 ms frame. Rate index 11
     * (0x0B).
     * <p>
     * FEC: Hamming(16,11), with 56 unprotected speech bits. 67 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0-42/6, 47-57/5 at channel bits 0-60/6. Parity at channel
     *     bits 1-19/6, 66 (in channel order).</li>
     * <li>Unprotected speech bits 1-5, 7-11, 13-17, 19-23, 25-29, 31-35, 37-41, 43-46, 48-51, 53-56, 58-66 at
     *     channel bits 31-35, 37-41, 43-47, 49-53, 55-59, 61-65, 67-71, 2-5, 8-11, 14-17, 20-23, 25-29.</li>
     * </ul>
     */
    AMBE_3350_250(0x0B, 3350, 250, 0x0043, 0x0765, 0x0080, 0x0000, 0x0000, 0x5348),
    /**
     * AMBE+ vocoder, 3600 bps = 2400 bps speech + 1200 bps FEC. 72 channel bits per 20 ms frame. No rate index:
     * select this rate with its rate control words (PKT_RATEP).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 42 -> 66 bits, with 6 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 42 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-44/4, 1-45/4, 2-46/4, 3-23/4. Coded bits in encoder order (step 0 first, generators in the
     *     order listed) at channel bits 0-66/6, 1-67/6, 2-68/6, 3-69/6, 4-70/6, 5-35/6. Sent outputs (steps 0-47):
     *     171 all steps; 133 steps 0, 2-8/3, 10-16/3, 18-24/3, 26-32/3, 34-40/3, 42, 45.</li>
     * <li>Unprotected speech bits 27-47/4 at channel bits 41-71/6.</li>
     * </ul>
     */
    AMBE_PLUS_2400_1200_CONVOLUTIONAL(0xFF, 2400, 1200, 0x0130, 0x0763, 0x0001, 0x0000, 0x4230, 0x0048),
    /**
     * AMBE+ vocoder, 3600 bps = 2400 bps speech + 1200 bps FEC. 72 channel bits per 20 ms frame. No rate index:
     * select this rate with its rate control words (PKT_RATEP). Interoperable with D-STAR.
     * <p>
     * FEC: Golay(24,12) + Golay(24,12) (PN), with 24 unprotected speech bits. 48 speech bits + 24 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-44/4 at channel bits 0-66/6. Parity at channel bits
     *     1-67/6.</li>
     * <li>c1 Golay(24,12), PN[0..23]. Speech bits 1-45/4 at channel bits 2-68/6. Parity at channel bits
     *     3-69/6.</li>
     * <li>Unprotected speech bits 2, 3, 6, 7, 10, 11, 14, 15, 18, 19, 22, 23, 26, 27, 30, 31, 34, 35, 38, 39, 42,
     *     43, 46, 47 at channel bits 4, 5, 10, 11, 16, 17, 22, 23, 28, 29, 34, 35, 40, 41, 46, 47, 52, 53, 58, 59,
     *     64, 65, 70, 71.</li>
     * </ul>
     */
    AMBE_PLUS_2400_1200_DSTAR(0xFF, 2400, 1200, 0x0130, 0x0763, 0x4000, 0x0000, 0x0000, 0x0048),
    /**
     * AMBE vocoder, 3600 bps = 2400 bps speech + 1200 bps FEC. 72 channel bits per 20 ms frame. No rate index:
     * select this rate with its rate control words (PKT_RATEP).
     * <p>
     * FEC: Golay(24,12) + Golay(24,12) (PN), with 24 unprotected speech bits. 48 speech bits + 24 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-44/4 at channel bits 0-66/6. Parity at channel bits
     *     1-67/6.</li>
     * <li>c1 Golay(24,12), PN[0..23]. Speech bits 1-45/4 at channel bits 2-68/6. Parity at channel bits
     *     3-69/6.</li>
     * <li>Unprotected speech bits 2, 3, 6, 7, 10, 11, 14, 15, 18, 19, 22, 23, 26, 27, 30, 31, 34, 35, 38, 39, 42,
     *     43, 46, 47 at channel bits 4, 5, 10, 11, 16, 17, 22, 23, 28, 29, 34, 35, 40, 41, 46, 47, 52, 53, 58, 59,
     *     64, 65, 70, 71.</li>
     * </ul>
     */
    AMBE_2400_1200(0xFF, 2400, 1200, 0x0030, 0x0664, 0x4000, 0x0000, 0x0000, 0x0048),
    /**
     * AMBE+ vocoder, 3600 bps = 3600 bps speech + 0 bps FEC. 72 channel bits per 20 ms frame. Rate index 16 (0x10).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 72 bits.
     */
    AMBE_PLUS_3600_0(0x10, 3600, 0, 0x0248, 0x0763, 0x0000, 0x0000, 0x0000, 0x3948),
    /**
     * AMBE+2 vocoder, 3600 bps = 2450 bps speech + 1150 bps FEC. 72 channel bits per 20 ms frame. Rate index 33
     * (0x21). Interoperable with APCO25 Phase 2 half rate and DMR / dPMR.
     * <p>
     * FEC: Golay(24,12) + Golay(23,12) (PN), with 25 unprotected speech bits. 49 speech bits + 23 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-33/3 at channel bits 0-44/4. Parity at channel bits
     *     48-68/4, 1-21/4.</li>
     * <li>c1 Golay(23,12), PN[0..22]. Speech bits 36, 39-47/2, 1-16/3 at channel bits 25-69/4. Parity at channel
     *     bits 2-42/4.</li>
     * <li>Unprotected speech bits 2-17/3, 19, 20, 22, 23, 25, 26, 28, 29, 31, 32, 34, 35, 37, 38-48/2 at channel
     *     bits 23-43/4, 46, 47, 50, 51, 54, 55, 58, 59, 62, 63, 66, 67, 70, 71, 3-19/4.</li>
     * </ul>
     */
    AMBE_PLUS2_2450_1150(0x21, 2450, 1150, 0x0431, 0x0754, 0x2400, 0x0000, 0x0000, 0x6F48),
    /**
     * AMBE+2 vocoder, 3600 bps = 3600 bps speech + 0 bps FEC. 72 channel bits per 20 ms frame. Rate index 39
     * (0x27).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 72 bits.
     */
    AMBE_PLUS2_3600_0(0x27, 3600, 0, 0x0448, 0x0766, 0x0000, 0x0000, 0x0000, 0x6848),
    /**
     * AMBE+2 vocoder, 3600 bps = 3350 bps speech + 250 bps FEC. 72 channel bits per 20 ms frame. Rate index 48
     * (0x30).
     * <p>
     * FEC: Hamming(16,11), with 56 unprotected speech bits. 67 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0-40/4 at channel bits 0-40/4. Parity at channel bits
     *     44-60/4 (in channel order).</li>
     * <li>Unprotected speech bits 1-3, 5-7, 9-11, 13-15, 17-19, 21-23, 25-27, 29-31, 33-35, 37-39, 41-66 at channel
     *     bits 21-23, 25-27, 29-31, 33-35, 37-39, 41-43, 45-47, 49-51, 53-55, 57-59, 61-71, 1-3, 5-7, 9-11, 13-15,
     *     17-19.</li>
     * </ul>
     */
    AMBE_PLUS2_3350_250(0x30, 3350, 250, 0x0443, 0x0766, 0x0080, 0x0000, 0x0000, 0x3948),

    //4000 bps total rate
    /**
     * AMBE vocoder, 4000 bps = 4000 bps speech + 0 bps FEC. 80 channel bits per 20 ms frame. Rate index 15 (0x0F).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 80 bits.
     */
    AMBE_4000_0(0x0F, 4000, 0, 0x0050, 0x0887, 0x0000, 0x0000, 0x0000, 0x3950),
    /**
     * AMBE vocoder, 4000 bps = 3750 bps speech + 250 bps FEC. 80 channel bits per 20 ms frame. Rate index 14
     * (0x0E).
     * <p>
     * FEC: Hamming(16,11), with 64 unprotected speech bits. 75 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0-21/7, 27-63/6 at channel bits 0-56/7, 62, 68. Parity at
     *     channel bits 1-22/7, 74 (in channel order).</li>
     * <li>Unprotected speech bits 1-6, 8-13, 15-20, 22-26, 28-32, 34-38, 40-44, 46-50, 52-56, 58-62, 64-74 at
     *     channel bits 36-41, 43-48, 50-55, 57-61, 63-67, 69-73, 75-79, 2-6, 9-13, 16-20, 23-27, 29-34.</li>
     * </ul>
     */
    AMBE_3750_250(0x0E, 3750, 250, 0x004B, 0x0767, 0x0080, 0x0000, 0x0000, 0x3950),
    /**
     * AMBE+ vocoder, 4000 bps = 4000 bps speech + 0 bps FEC. 80 channel bits per 20 ms frame. Rate index 17 (0x11).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 80 bits.
     */
    AMBE_PLUS_4000_0(0x11, 4000, 0, 0x0250, 0x0765, 0x0000, 0x0000, 0x0000, 0x4150),
    /**
     * AMBE+ vocoder, 4000 bps = 2400 bps speech + 1600 bps FEC. 80 channel bits per 20 ms frame. Rate index 22
     * (0x16).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 20 -> 52 bits, with 28 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 20 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-44/4, 1-29/4. Coded bits in encoder order (step 0 first, generators in the order listed) at
     *     channel bits 0-56/7, 62-74/6, 1-57/7, 63-75/6, 2-58/7, 64-76/6, 3-59/7, 65-77/6, 4-25/7. Sent outputs
     *     (steps 0-25): 171 all steps; 133 all steps.</li>
     * <li>Unprotected speech bits 2, 3, 6, 7, 10, 11, 14, 15, 18, 19, 22, 23, 26, 27, 30, 31, 33-35, 37-39, 41-43,
     *     45-47 at channel bits 60, 61, 66, 67, 72, 73, 78, 79, 5, 6, 12, 13, 19, 20, 26, 27, 32-34, 39-41, 46-48,
     *     53-55.</li>
     * </ul>
     */
    AMBE_PLUS_2400_1600(0x16, 2400, 1600, 0x0130, 0x0763, 0x0001, 0x0000, 0x341A, 0x6750),
    /**
     * AMBE+2 vocoder, 4000 bps = 4000 bps speech + 0 bps FEC. 80 channel bits per 20 ms frame. Rate index 40
     * (0x28).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 80 bits.
     */
    AMBE_PLUS2_4000_0(0x28, 4000, 0, 0x0450, 0x0986, 0x0000, 0x0000, 0x0000, 0x7450),
    /**
     * AMBE+2 vocoder, 4000 bps = 3750 bps speech + 250 bps FEC. 80 channel bits per 20 ms frame. Rate index 49
     * (0x31).
     * <p>
     * FEC: Hamming(16,11), with 64 unprotected speech bits. 75 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0-15/5, 19-43/4 at channel bits 0-40/5, 44, 48. Parity at
     *     channel bits 52-68/4 (in channel order).</li>
     * <li>Unprotected speech bits 1-4, 6-9, 11-14, 16-18, 20-22, 24-26, 28-30, 32-34, 36-38, 40-42, 44-74 at
     *     channel bits 26-29, 31-34, 36-39, 41-43, 45-47, 49-51, 53-55, 57-59, 61-63, 65-67, 69-79, 1-4, 6-9,
     *     11-14, 16-19, 21-24.</li>
     * </ul>
     */
    AMBE_PLUS2_3750_250(0x31, 3750, 250, 0x044B, 0x0766, 0x0080, 0x0000, 0x0000, 0x4150),
    /**
     * AMBE+2 vocoder, 4000 bps = 2600 bps speech + 1400 bps FEC. 80 channel bits per 20 ms frame. Rate index 55
     * (0x37).
     * <p>
     * FEC: Golay(24,12) + Golay(23,12) (PN) + Hamming(16,11) (PN), with 17 unprotected speech bits. 52 speech bits
     * + 28 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-33/3 at channel bits 0-40/5, 44-52/4. Parity at channel
     *     bits 56-76/4, 1-26/5.</li>
     * <li>c1 Golay(23,12), PN[0..22]. Speech bits 36-48/3, 50, 1-16/3 at channel bits 31-41/5, 45-77/4. Parity at
     *     channel bits 2-42/5, 46, 50.</li>
     * <li>c2 Hamming(16,11), PN[23..38]. Speech bits 19-49/3 at channel bits 54-78/4, 3-18/5. Parity at channel
     *     bits 23-43/5.</li>
     * <li>Unprotected speech bits 2-47/3, 51 at channel bits 51-79/4, 4-39/5, 47.</li>
     * </ul>
     */
    AMBE_PLUS2_2600_1400(0x37, 2600, 1400, 0x0434, 0x0754, 0x2480, 0x0000, 0x0000, 0x6850),

    //4400 bps total rate
    /**
     * AMBE+2 vocoder, 4400 bps = 4400 bps speech + 0 bps FEC. 88 channel bits per 20 ms frame. Rate index 41
     * (0x29).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 88 bits.
     */
    AMBE_PLUS2_4400_0(0x29, 4400, 0, 0x0458, 0x0986, 0x0000, 0x0000, 0x0000, 0x4458),
    /**
     * AMBE+2 vocoder, 4400 bps = 2450 bps speech + 1950 bps FEC. 88 channel bits per 20 ms frame. Rate index 51
     * (0x33).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 27 -> 66 bits, with 22 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 27 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-39/3, 41-47/2, 1-25/3. Coded bits in encoder order (step 0 first, generators in the order
     *     listed) at channel bits 0-80/5, 84, 1-81/5, 85, 2-82/5, 86, 3-58/5. Sent outputs (steps 0-32): 171 all
     *     steps; 133 all steps.</li>
     * <li>Unprotected speech bits 2-26/3, 28, 29, 31, 32, 34, 35, 37, 38-48/2 at channel bits 19-59/5, 63, 64, 68,
     *     69, 73, 74, 78, 79-87/4, 4-14/5.</li>
     * </ul>
     */
    AMBE_PLUS2_2450_1950(0x33, 2450, 1950, 0x0431, 0x0754, 0x0001, 0x0000, 0x4221, 0x6C58),

    //4800 bps total rate
    /**
     * AMBE vocoder, 4800 bps = 4800 bps speech + 0 bps FEC. 96 channel bits per 20 ms frame. Rate index 3 (0x03).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 96 bits.
     */
    AMBE_4800_0(0x03, 4800, 0, 0x0060, 0x0887, 0x0000, 0x0000, 0x0000, 0x7960),
    /**
     * AMBE vocoder, 4800 bps = 4550 bps speech + 250 bps FEC. 96 channel bits per 20 ms frame. Rate index 7 (0x07).
     * <p>
     * FEC: Hamming(16,11), with 80 unprotected speech bits. 91 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0-56/8, 63-77/7 at channel bits 0-80/8. Parity at channel
     *     bits 1-25/8, 88 (in channel order).</li>
     * <li>Unprotected speech bits 1-7, 9-15, 17-23, 25-31, 33-39, 41-47, 49-55, 57-62, 64-69, 71-76, 78-90 at
     *     channel bits 41-47, 49-55, 57-63, 65-71, 73-79, 81-87, 89-95, 2-7, 10-15, 18-23, 26-31, 33-39.</li>
     * </ul>
     */
    AMBE_4550_250(0x07, 4550, 250, 0x005B, 0x0887, 0x0080, 0x0000, 0x0000, 0x6860),
    /**
     * AMBE vocoder, 4800 bps = 3600 bps speech + 1200 bps FEC. 96 channel bits per 20 ms frame. Rate index 2
     * (0x02).
     * <p>
     * FEC: Golay(24,12) + 3 x Hamming(15,11) (PN), with 27 unprotected speech bits. 72 speech bits + 24 parity
     * bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-66/6 at channel bits 0-88/8. Parity at channel bits
     *     1-89/8.</li>
     * <li>c1 Hamming(15,11), PN[0..14]. Speech bits 1-61/6 at channel bits 2-82/8. Parity at channel bits 90,
     *     3-19/8.</li>
     * <li>c2 Hamming(15,11), PN[15..29]. Speech bits 67, 2-56/6 at channel bits 27-91/8, 4, 12. Parity at channel
     *     bits 20-44/8.</li>
     * <li>c3 Hamming(15,11), PN[30..44]. Speech bits 62, 68, 3-51/6 at channel bits 52-92/8, 5-37/8. Parity at
     *     channel bits 45-69/8.</li>
     * <li>Unprotected speech bits 4, 5, 10, 11, 16, 17, 22, 23, 28, 29, 34, 35, 40, 41, 46, 47, 52, 53, 57-59,
     *     63-65, 69-71 at channel bits 6, 7, 14, 15, 22, 23, 30, 31, 38, 39, 46, 47, 54, 55, 62, 63, 70, 71, 77-79,
     *     85-87, 93-95.</li>
     * </ul>
     */
    AMBE_3600_1200(0x02, 3600, 1200, 0x0048, 0x0767, 0x2030, 0x0000, 0x0000, 0x7060),
    /**
     * AMBE vocoder, 4800 bps = 3100 bps speech + 1700 bps FEC. 96 channel bits per 20 ms frame. Rate index 8
     * (0x08).
     * <p>
     * FEC: Golay(24,12) + 2 x Golay(23,12) (PN), with 26 unprotected speech bits. 62 speech bits + 34 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-12/6, 17-57/5 at channel bits 0-88/8. Parity at channel
     *     bits 1-89/8.</li>
     * <li>c1 Golay(23,12), PN[0..22]. Speech bits 1-13/6, 18-58/5 at channel bits 2-90/8. Parity at channel bits
     *     3-83/8.</li>
     * <li>c2 Golay(23,12), PN[23..45]. Speech bits 2-14/6, 19-59/5 at channel bits 91, 4-84/8. Parity at channel
     *     bits 92, 5-77/8.</li>
     * <li>Unprotected speech bits 3-5, 9-11, 15, 16, 20, 21, 25, 26, 30, 31, 35, 36, 40, 41, 45, 46, 50, 51, 55,
     *     56, 60, 61 at channel bits 85-87, 93-95, 6, 7, 14, 15, 22, 23, 30, 31, 38, 39, 46, 47, 54, 55, 62, 63,
     *     70, 71, 78, 79.</li>
     * </ul>
     */
    AMBE_3100_1700(0x08, 3100, 1700, 0x003E, 0x0765, 0x2800, 0x0000, 0x0000, 0x7460),
    /**
     * AMBE+ vocoder, 4800 bps = 4800 bps speech + 0 bps FEC. 96 channel bits per 20 ms frame. Rate index 18 (0x12).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 96 bits.
     */
    AMBE_PLUS_4800_0(0x12, 4800, 0, 0x0260, 0x0767, 0x0000, 0x0000, 0x0000, 0x6C60),
    /**
     * AMBE+ vocoder, 4800 bps = 4000 bps speech + 800 bps FEC. 96 channel bits per 20 ms frame. Rate index 24
     * (0x18).
     * <p>
     * FEC: Golay(24,12) + Hamming(15,11) (PN), with 57 unprotected speech bits. 80 speech bits + 16 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-56/7, 62-74/6 at channel bits 0-88/8. Parity at channel
     *     bits 1-89/8.</li>
     * <li>c1 Hamming(15,11), PN[0..14]. Speech bits 1-57/7, 63, 69 at channel bits 2-82/8. Parity at channel bits
     *     90, 3-19/8.</li>
     * <li>Unprotected speech bits 2-6, 9-13, 16-20, 23-27, 30-34, 37-41, 44-48, 51-55, 58-61, 64-67, 70-73, 75-79
     *     at channel bits 35-39, 43-47, 51-55, 59-63, 67-71, 75-79, 83-87, 91-95, 4-7, 12-15, 20-23, 27-31.</li>
     * </ul>
     */
    AMBE_PLUS_4000_800(0x18, 4000, 800, 0x0250, 0x0765, 0x2010, 0x0000, 0x0000, 0x7460),
    /**
     * AMBE+ vocoder, 4800 bps = 3600 bps speech + 1200 bps FEC. 96 channel bits per 20 ms frame. Rate index 23
     * (0x17).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 12 -> 36 bits, with 60 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 12 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-66/6. Coded bits in encoder order (step 0 first, generators in the order listed) at channel
     *     bits 0-88/8, 1-89/8, 2-90/8. Sent outputs (steps 0-17): 171 all steps; 133 all steps.</li>
     * <li>Unprotected speech bits 1-5, 7-11, 13-17, 19-23, 25-29, 31-35, 37-41, 43-47, 49-53, 55-59, 61-65, 67-71
     *     at channel bits 3-7, 11-15, 19-23, 27-31, 35-39, 43-47, 51-55, 59-63, 67-71, 75-79, 83-87, 91-95.</li>
     * </ul>
     */
    AMBE_PLUS_3600_1200(0x17, 3600, 1200, 0x0248, 0x0763, 0x0001, 0x0000, 0x2412, 0x6860),
    /**
     * AMBE+ vocoder, 4800 bps = 2400 bps speech + 2400 bps FEC. 96 channel bits per 20 ms frame. Rate index 25
     * (0x19).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 6 -> 24 bits + (171, 133) rate 1/2, 18 -> 48 bits (PN), with 24
     * unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 6 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-20/4. Coded bits in encoder order (step 0 first, generators in the order listed) at channel
     *     bits 0-88/8, 1-89/8. Sent outputs (steps 0-11): 171 all steps; 133 all steps.</li>
     * <li>Block 2 (171, 133) rate 1/2, 18 info bits + 6 tail bits, coded bit n XOR PN[n], seeded with block 1's 6
     *     info bits in input order, first bit = MSB. Encoder input, first bit first: speech bits 24-44/4, 1-45/4.
     *     Coded bits in encoder order (step 0 first, generators in the order listed) at channel bits 2-90/8,
     *     3-91/8, 4-92/8, 5-93/8. Sent outputs (steps 0-23): 171 all steps; 133 all steps.</li>
     * <li>Unprotected speech bits 2, 3, 6, 7, 10, 11, 14, 15, 18, 19, 22, 23, 26, 27, 30, 31, 34, 35, 38, 39, 42,
     *     43, 46, 47 at channel bits 6, 7, 14, 15, 22, 23, 30, 31, 38, 39, 46, 47, 54, 55, 62, 63, 70, 71, 78, 79,
     *     86, 87, 94, 95.</li>
     * </ul>
     */
    AMBE_PLUS_2400_2400(0x19, 2400, 2400, 0x0130, 0x0763, 0x0005, 0x180C, 0x3018, 0x7360),
    /**
     * AMBE+2 vocoder, 4800 bps = 4800 bps speech + 0 bps FEC. 96 channel bits per 20 ms frame. Rate index 42
     * (0x2A).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 96 bits.
     */
    AMBE_PLUS2_4800_0(0x2A, 4800, 0, 0x0460, 0x0986, 0x0000, 0x0000, 0x0000, 0x5660),
    /**
     * AMBE+2 vocoder, 4800 bps = 4550 bps speech + 250 bps FEC. 96 channel bits per 20 ms frame. Rate index 50
     * (0x32).
     * <p>
     * FEC: Hamming(16,11), with 80 unprotected speech bits. 91 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0, 6-51/5 at channel bits 0-36/6, 41-56/5. Parity at
     *     channel bits 61-81/5 (in channel order).</li>
     * <li>Unprotected speech bits 1-5, 7-10, 12-15, 17-20, 22-25, 27-30, 32-35, 37-40, 42-45, 47-50, 52-90 at
     *     channel bits 31-35, 37-40, 42-45, 47-50, 52-55, 57-60, 62-65, 67-70, 72-75, 77-80, 82-95, 1-5, 7-11,
     *     13-17, 19-23, 25-29.</li>
     * </ul>
     */
    AMBE_PLUS2_4550_250(0x32, 4550, 250, 0x045B, 0x0986, 0x0080, 0x0000, 0x0000, 0x6C60),
    /**
     * AMBE+2 vocoder, 4800 bps = 2450 bps speech + 2350 bps FEC. 96 channel bits per 20 ms frame. Rate index 52
     * (0x34).
     * <p>
     * FEC: K=7 convolutional, (173, 155, 113) rate 1/3, 24 -> 71 bits, with 25 unprotected speech bits.
     * <ul>
     * <li>Block 1 (173, 155, 113) rate 1/3, 24 info bits + 6 tail bits, not scrambled. Encoder input, first bit
     *     first: speech bits 0-39/3, 41-47/2, 1-16/3. Coded bits in encoder order (step 0 first, generators in the
     *     order listed) at channel bits 0-36/6, 41-91/5, 1-37/6, 42-92/5, 2-38/6, 43-93/5, 3-39/6, 44-89/5. Sent
     *     outputs (steps 0-29): 173 all steps; 155 all steps; 113 steps 0, 2-8/3, 10-19/3, 21-27/3.</li>
     * <li>Unprotected speech bits 2-17/3, 19, 20, 22, 23, 25, 26, 28, 29, 31, 32, 34, 35, 37, 38-48/2 at channel
     *     bits 65-90/5, 94, 95, 4, 5, 10, 11, 16, 17, 22, 23, 28, 29, 34, 35-60/5.</li>
     * </ul>
     */
    AMBE_PLUS2_2450_2350(0x34, 2450, 2350, 0x0431, 0x0754, 0x0002, 0x0000, 0x471E, 0x5260),
    /**
     * AMBE+2 vocoder, 4800 bps = 3600 bps speech + 1200 bps FEC. 96 channel bits per 20 ms frame. Rate index 56
     * (0x38).
     * <p>
     * FEC: Golay(24,12) + Golay(24,12) (PN), with 48 unprotected speech bits. 72 speech bits + 24 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-44/4 at channel bits 0-36/6, 41-61/5. Parity at channel
     *     bits 66-91/5, 1-31/6.</li>
     * <li>c1 Golay(24,12), PN[0..23]. Speech bits 48-68/4, 1-21/4 at channel bits 37-92/5. Parity at channel bits
     *     2-38/6, 43-63/5.</li>
     * <li>Unprotected speech bits 2, 3, 6, 7, 10, 11, 14, 15, 18, 19, 22, 23, 25-27, 29-31, 33-35, 37-39, 41-43,
     *     45-47, 49-51, 53-55, 57-59, 61-63, 65-67, 69-71 at channel bits 39, 40, 44, 45, 49, 50, 54, 55, 59, 60,
     *     64, 65, 68-70, 73-75, 78-80, 83-85, 88-90, 93-95, 3-5, 9-11, 15-17, 21-23, 27-29, 33-35.</li>
     * </ul>
     */
    AMBE_PLUS2_3600_1200(0x38, 3600, 1200, 0x0448, 0x0766, 0x4000, 0x0000, 0x0000, 0x7460),
    /**
     * AMBE+2 vocoder, 4800 bps = 4000 bps speech + 800 bps FEC. 96 channel bits per 20 ms frame. Rate index 57
     * (0x39).
     * <p>
     * FEC: Golay(24,12) + Hamming(15,11) (PN), with 57 unprotected speech bits. 80 speech bits + 16 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-40/5, 44-52/4 at channel bits 0-36/6, 41-61/5. Parity at
     *     channel bits 66-91/5, 1-31/6.</li>
     * <li>c1 Hamming(15,11), PN[0..14]. Speech bits 56-76/4, 1-21/5 at channel bits 37-87/5. Parity at channel bits
     *     92, 2-14/6.</li>
     * <li>Unprotected speech bits 2-4, 7-9, 12-14, 17-19, 22-24, 26-29, 31-34, 36-39, 41-43, 45-47, 49-51, 53-55,
     *     57-59, 61-63, 65-67, 69-71, 73-75, 77-79 at channel bits 88-90, 93-95, 3-5, 9-11, 15-17, 20-23, 26-29,
     *     32-35, 38-40, 43-45, 48-50, 53-55, 58-60, 63-65, 68-70, 73-75, 78-80, 83-85.</li>
     * </ul>
     */
    AMBE_PLUS2_4000_800(0x39, 4000, 800, 0x0450, 0x0986, 0x2010, 0x0000, 0x0000, 0x7360),

    //6000 bps total rate
    /**
     * AMBE+2 vocoder, 6000 bps = 2450 bps speech + 3550 bps FEC. 120 channel bits per 20 ms frame. Rate index 53
     * (0x35).
     * <p>
     * FEC: K=7 convolutional, (173, 155, 113) rate 1/3, 31 -> 102 bits, with 18 unprotected speech bits.
     * <ul>
     * <li>Block 1 (173, 155, 113) rate 1/3, 31 info bits + 6 tail bits, not scrambled. Encoder input, first bit
     *     first: speech bits 0-39/3, 41-47/2, 1-37/3. Coded bits in encoder order (step 0 first, generators in the
     *     order listed) at channel bits 0-84/7, 90-114/6, 1-85/7, 91-115/6, 2-86/7, 92-116/6, 3-87/7, 93-117/6,
     *     4-88/7, 94-118/6, 5-82/7. Sent outputs (steps 0-36): 173 all steps; 155 all steps; 113 all steps except
     *     4-36/4.</li>
     * <li>Unprotected speech bits 2-38/3, 40-48/2 at channel bits 119, 6-83/7, 89-113/6.</li>
     * </ul>
     */
    AMBE_PLUS2_2450_3550(0x35, 2450, 3550, 0x0431, 0x0754, 0x0002, 0x0000, 0x6625, 0x6978),

    //6400 bps total rate
    /**
     * AMBE vocoder, 6400 bps = 4150 bps speech + 2250 bps FEC. 128 channel bits per 20 ms frame. Rate index 10
     * (0x0A).
     * <p>
     * FEC: Golay(24,12) + 3 x Golay(23,12) (PN), with 35 unprotected speech bits. 83 speech bits + 45 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-77/7 at channel bits 0-88/11, 98-118/10. Parity at channel
     *     bits 1-89/11, 99-119/10.</li>
     * <li>c1 Golay(23,12), PN[0..22]. Speech bits 1-78/7 at channel bits 2-90/11, 100-120/10. Parity at channel
     *     bits 3-91/11, 101, 111.</li>
     * <li>c2 Golay(23,12), PN[23..45]. Speech bits 2-79/7 at channel bits 121, 4-92/11, 102, 112. Parity at channel
     *     bits 122, 5-93/11, 103.</li>
     * <li>c3 Golay(23,12), PN[46..68]. Speech bits 3-80/7 at channel bits 113, 123, 6-94/11, 104. Parity at channel
     *     bits 114, 124, 7-95/11.</li>
     * <li>Unprotected speech bits 4-6, 11-13, 18-20, 25-27, 32-34, 39-41, 46-48, 53-55, 60-62, 67-69, 74-76, 81, 82
     *     at channel bits 105-107, 115-117, 125-127, 8-10, 19-21, 30-32, 41-43, 52-54, 63-65, 74-76, 85-87, 96,
     *     97.</li>
     * </ul>
     */
    AMBE_4150_2250(0x0A, 4150, 2250, 0x0053, 0x0887, 0x2C00, 0x0000, 0x0000, 0x5680),
    /**
     * AMBE+ vocoder, 6400 bps = 6400 bps speech + 0 bps FEC. 128 channel bits per 20 ms frame. Rate index 19
     * (0x13).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 128 bits.
     */
    AMBE_PLUS_6400_0(0x13, 6400, 0, 0x0280, 0x0887, 0x0000, 0x0000, 0x0000, 0x6C80),
    /**
     * AMBE+ vocoder, 6400 bps = 4000 bps speech + 2400 bps FEC. 128 channel bits per 20 ms frame. Rate index 26
     * (0x1A).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 36 -> 84 bits, with 44 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 36 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-56/7, 62-74/6, 1-57/7, 63-75/6, 2-58/7, 64-76/6. Coded bits in encoder order (step 0 first,
     *     generators in the order listed) at channel bits 0-88/11, 98-118/10, 1-89/11, 99-119/10, 2-90/11,
     *     100-120/10, 3-91/11, 101-121/10, 4-92/11, 102-122/10, 5-93/11, 103-123/10, 6-94/11, 104-124/10. Sent
     *     outputs (steps 0-41): 171 all steps; 133 all steps.</li>
     * <li>Unprotected speech bits 3-6, 10-13, 17-20, 24-27, 31-34, 38-41, 45-48, 52-55, 59-61, 65-67, 71-73, 77-79
     *     at channel bits 7-10, 18-21, 29-32, 40-43, 51-54, 62-65, 73-76, 84-87, 95-97, 105-107, 115-117,
     *     125-127.</li>
     * </ul>
     */
    AMBE_PLUS_4000_2400(0x1A, 4000, 2400, 0x0250, 0x0765, 0x0001, 0x0000, 0x542A, 0x5280),
    /**
     * AMBE+ vocoder, 6400 bps = 3600 bps speech + 2800 bps FEC. 128 channel bits per 20 ms frame. Rate index 32
     * (0x20).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 54 -> 110 bits, with 18 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 54 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-66/6, 1-67/6, 2-68/6, 3-69/6, 4-34/6. Coded bits in encoder order (step 0 first, generators
     *     in the order listed) at channel bits 0-88/11, 98-118/10, 1-89/11, 99-119/10, 2-90/11, 100-120/10,
     *     3-91/11, 101-121/10, 4-92/11, 102-122/10, 5-93/11, 103-123/10, 6-94/11, 104-124/10, 7-95/11, 105-125/10,
     *     8-96/11, 106-126/10, 9, 20. Sent outputs (steps 0-59): 171 all steps; 133 all steps except 5-59/6.</li>
     * <li>Unprotected speech bits 5-35/6, 40, 41, 46, 47, 52, 53, 58, 59, 64, 65, 70, 71 at channel bits 97-127/10,
     *     10, 21, 31, 32, 42, 43, 53, 54, 64, 65, 75, 76, 86, 87.</li>
     * </ul>
     */
    AMBE_PLUS_3600_2800(0x20, 3600, 2800, 0x0248, 0x0763, 0x0001, 0x0000, 0x6E3C, 0x4380),
    /**
     * AMBE+2 vocoder, 6400 bps = 6400 bps speech + 0 bps FEC. 128 channel bits per 20 ms frame. Rate index 43
     * (0x2B).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 128 bits.
     */
    AMBE_PLUS2_6400_0(0x2B, 6400, 0, 0x0480, 0x0986, 0x0000, 0x0000, 0x0000, 0x5380),
    /**
     * AMBE+2 vocoder, 6400 bps = 4000 bps speech + 2400 bps FEC. 128 channel bits per 20 ms frame. Rate index 58
     * (0x3A).
     * <p>
     * FEC: Golay(24,12) + 3 x Golay(24,12) (PN), with 32 unprotected speech bits. 80 speech bits + 48 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-40/5, 44-52/4 at channel bits 0-16/8, 23-79/7. Parity at
     *     channel bits 86-121/7, 1-17/8, 24-38/7.</li>
     * <li>c1 Golay(24,12), PN[0..23]. Speech bits 56-76/4, 1-26/5 at channel bits 45-122/7. Parity at channel bits
     *     2-18/8, 25-81/7.</li>
     * <li>c2 Golay(24,12), PN[24..47]. Speech bits 31-41/5, 45-77/4 at channel bits 88-123/7, 3-19/8, 26-40/7.
     *     Parity at channel bits 47-124/7.</li>
     * <li>c3 Golay(24,12), PN[48..71]. Speech bits 2-42/5, 46-54/4 at channel bits 4-20/8, 27-83/7. Parity at
     *     channel bits 90-125/7, 5-21/8, 28-42/7.</li>
     * <li>Unprotected speech bits 3, 4, 8, 9, 13, 14, 18, 19, 23, 24, 28, 29, 33, 34, 38, 39-55/4, 58, 59, 62, 63,
     *     66, 67, 70, 71, 74, 75, 78, 79 at channel bits 91, 92, 98, 99, 105, 106, 112, 113, 119, 120, 126, 127, 6,
     *     7, 14, 15-43/7, 49, 50, 56, 57, 63, 64, 70, 71, 77, 78, 84, 85.</li>
     * </ul>
     */
    AMBE_PLUS2_4000_2400(0x3A, 4000, 2400, 0x0450, 0x0986, 0x8000, 0x0000, 0x0000, 0x5280),

    //7200 bps total rate
    /**
     * AMBE vocoder, 7200 bps = 4400 bps speech + 2800 bps FEC. 144 channel bits per 20 ms frame. Rate index 9
     * (0x09).
     * <p>
     * FEC: Golay(24,12) + 4 x Golay(23,12) (PN), with 28 unprotected speech bits. 88 speech bits + 56 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-32/8, 39-81/7 at channel bits 0-132/12. Parity at channel
     *     bits 1-133/12.</li>
     * <li>c1 Golay(23,12), PN[0..22]. Speech bits 1-33/8, 40-82/7 at channel bits 2-134/12. Parity at channel bits
     *     3-123/12.</li>
     * <li>c2 Golay(23,12), PN[23..45]. Speech bits 2-34/8, 41-83/7 at channel bits 135, 4-124/12. Parity at channel
     *     bits 136, 5-113/12.</li>
     * <li>c3 Golay(23,12), PN[46..68]. Speech bits 3-35/8, 42-84/7 at channel bits 125, 137, 6-114/12. Parity at
     *     channel bits 126, 138, 7-103/12.</li>
     * <li>c4 Golay(23,12), PN[69..91]. Speech bits 4-36/8, 43-85/7 at channel bits 115-139/12, 8-104/12. Parity at
     *     channel bits 116-140/12, 9-93/12.</li>
     * <li>Unprotected speech bits 5-7, 13-15, 21-23, 29-31, 37, 38, 44, 45, 51, 52, 58, 59, 65, 66, 72, 73, 79, 80,
     *     86, 87 at channel bits 105-107, 117-119, 129-131, 141-143, 10, 11, 22, 23, 34, 35, 46, 47, 58, 59, 70,
     *     71, 82, 83, 94, 95.</li>
     * </ul>
     */
    AMBE_4400_2800(0x09, 4400, 2800, 0x0058, 0x0887, 0x3000, 0x0000, 0x0000, 0x4490),
    /**
     * AMBE+ vocoder, 7200 bps = 4400 bps speech + 2800 bps FEC. 144 channel bits per 20 ms frame. Rate index 27
     * (0x1B).
     * <p>
     * FEC: K=7 convolutional, (173, 155, 113) rate 1/3, 6 -> 30 bits + (171, 133) rate 1/2, 33 -> 65 bits (PN),
     * with 49 unprotected speech bits.
     * <ul>
     * <li>Block 1 (173, 155, 113) rate 1/3, 6 info bits + 6 tail bits, not scrambled. Encoder input, first bit
     *     first: speech bits 0-32/8, 39. Coded bits in encoder order (step 0 first, generators in the order listed)
     *     at channel bits 0-132/12, 1-25/12, 73, 37-61/12, 85-133/12, 2-62/12. Sent outputs (steps 0-11): 173 all
     *     steps except 7; 155 all steps; 113 steps 0-10/2.</li>
     * <li>Block 2 (171, 133) rate 1/2, 33 info bits + 6 tail bits, coded bit n XOR PN[n], seeded with block 1's 6
     *     info bits in input order, first bit = MSB. Encoder input, first bit first: speech bits 46-81/7, 1-33/8,
     *     40-82/7, 2-34/8, 41-83/7, 3-19/8. Coded bits in encoder order (step 0 first, generators in the order
     *     listed) at channel bits 74-134/12, 3-135/12, 4-136/12, 5-137/12, 6-138/12, 7-127/12. Sent outputs (steps
     *     0-38): 171 all steps; 133 all steps except 2-38/3.</li>
     * <li>Unprotected speech bits 4-7, 12-15, 20-23, 27-31, 35-38, 42-45, 49-52, 56-59, 63-66, 70-73, 77-80, 84-87
     *     at channel bits 104-107, 116-119, 128-131, 139-143, 8-11, 20-23, 32-35, 44-47, 56-59, 68-71, 80-83,
     *     92-95.</li>
     * </ul>
     */
    AMBE_PLUS_4400_2800(0x1B, 4400, 2800, 0x0258, 0x0765, 0x0009, 0x1E0C, 0x4127, 0x7390),
    /**
     * AMBE+2 vocoder, 7200 bps = 7200 bps speech + 0 bps FEC. 144 channel bits per 20 ms frame. Rate index 44
     * (0x2C).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 144 bits.
     */
    AMBE_PLUS2_7200_0(0x2C, 7200, 0, 0x0490, 0x0986, 0x0000, 0x0000, 0x0000, 0x4990),
    /**
     * AMBE+2 vocoder, 7200 bps = 2450 bps speech + 4750 bps FEC. 144 channel bits per 20 ms frame. Rate index 54
     * (0x36).
     * <p>
     * FEC: K=7 convolutional, (163, 147, 135, 135) rate 1/4, 31 -> 126 bits, with 18 unprotected speech bits.
     * <ul>
     * <li>Block 1 (163, 147, 135, 135) rate 1/4, 31 info bits + 6 tail bits, not scrambled. Encoder input, first
     *     bit first: speech bits 0-39/3, 41-47/2, 1-37/3. Coded bits in encoder order (step 0 first, generators in
     *     the order listed) at channel bits 0-136/8, 1-137/8, 2-138/8, 3-139/8, 4-140/8, 5-141/8, 6-142/8. Sent
     *     outputs (steps 0-36): 163 all steps; 147 all steps; 135 (copy 1) all steps; 135 (copy 2) steps 0-4/2, 7,
     *     9, 12, 14, 17, 19, 22, 24, 27, 29, 32, 34.</li>
     * <li>Unprotected speech bits 2-38/3, 40-48/2 at channel bits 47-143/8, 7-39/8.</li>
     * </ul>
     */
    AMBE_PLUS2_2450_4750(0x36, 2450, 4750, 0x0431, 0x0754, 0x0003, 0x0000, 0x7E25, 0x6790),
    /**
     * AMBE+2 vocoder, 7200 bps = 4400 bps speech + 2800 bps FEC. 144 channel bits per 20 ms frame. Rate index 59
     * (0x3B).
     * <p>
     * FEC: Golay(24,12) + 3 x Golay(24,12) (PN) + 2 x Hamming(15,11) (PN), with 18 unprotected speech bits. 88
     * speech bits + 56 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-55/5 at channel bits 0-88/8. Parity at channel bits
     *     96-136/8, 1-41/8.</li>
     * <li>c1 Golay(24,12), PN[0..23]. Speech bits 60-80/5, 84, 1-26/5 at channel bits 49-137/8. Parity at channel
     *     bits 2-90/8.</li>
     * <li>c2 Golay(24,12), PN[24..47]. Speech bits 31-81/5, 85 at channel bits 98-138/8, 3-43/8. Parity at channel
     *     bits 51-139/8.</li>
     * <li>c3 Golay(24,12), PN[48..71]. Speech bits 2-57/5 at channel bits 4-92/8. Parity at channel bits 100-140/8,
     *     5-45/8.</li>
     * <li>c4 Hamming(15,11), PN[72..86]. Speech bits 62-82/5, 86, 3-23/5 at channel bits 53-133/8. Parity at
     *     channel bits 141, 6-22/8.</li>
     * <li>c5 Hamming(15,11), PN[87..101]. Speech bits 28-78/5 at channel bits 30-110/8. Parity at channel bits
     *     118-142/8.</li>
     * <li>Unprotected speech bits 4-79/5, 83, 87 at channel bits 23-143/8, 7, 15.</li>
     * </ul>
     */
    AMBE_PLUS2_4400_2800(0x3B, 4400, 2800, 0x0458, 0x0986, 0x8020, 0x0000, 0x0000, 0x7390),

    //8000 bps total rate
    /**
     * AMBE vocoder, 8000 bps = 7750 bps speech + 250 bps FEC. 160 channel bits per 20 ms frame. Rate index 12
     * (0x0C).
     * <p>
     * FEC: Hamming(16,11), with 144 unprotected speech bits. 155 speech bits + 5 parity bits.
     * <ul>
     * <li>c0 Hamming(16,11), not scrambled. Speech bits 0-130/13 at channel bits 0-56/14, 69-134/13. Parity at
     *     channel bits 1-43/14, 147 (in channel order).</li>
     * <li>Unprotected speech bits 1-12, 14-25, 27-38, 40-51, 53-64, 66-77, 79-90, 92-103, 105-116, 118-129, 131-154
     *     at channel bits 70-81, 83-94, 96-107, 109-120, 122-133, 135-146, 148-159, 2-13, 16-27, 30-41, 44-55,
     *     57-68.</li>
     * </ul>
     */
    AMBE_7750_250(0x0C, 7750, 250, 0x009B, 0x0997, 0x0080, 0x0000, 0x0000, 0x49A0),
    /**
     * AMBE vocoder, 8000 bps = 4650 bps speech + 3350 bps FEC. 160 channel bits per 20 ms frame. Rate index 13
     * (0x0D).
     * <p>
     * FEC: Golay(24,12) + 5 x Golay(23,12) (PN), with 21 unprotected speech bits. 93 speech bits + 67 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0-72/8, 79, 86 at channel bits 0-56/14, 69-147/13. Parity at
     *     channel bits 1-57/14, 70-148/13.</li>
     * <li>c1 Golay(23,12), PN[0..22]. Speech bits 1-73/8, 80, 87 at channel bits 2-58/14, 71-149/13. Parity at
     *     channel bits 3-59/14, 72-137/13.</li>
     * <li>c2 Golay(23,12), PN[23..45]. Speech bits 2-74/8, 81, 88 at channel bits 150, 4-60/14, 73-138/13. Parity
     *     at channel bits 151, 5-61/14, 74-126/13.</li>
     * <li>c3 Golay(23,12), PN[46..68]. Speech bits 3-75/8, 82, 89 at channel bits 139, 152, 6-62/14, 75-127/13.
     *     Parity at channel bits 140, 153, 7-63/14, 76-115/13.</li>
     * <li>c4 Golay(23,12), PN[69..91]. Speech bits 4-76/8, 83, 90 at channel bits 128-154/13, 8-64/14, 77-116/13.
     *     Parity at channel bits 129-155/13, 9-65/14, 78-104/13.</li>
     * <li>c5 Golay(23,12), PN[92..114]. Speech bits 5-77/8, 84, 91 at channel bits 117-156/13, 10-66/14, 79-105/13.
     *     Parity at channel bits 118-157/13, 11-67/14, 80, 93.</li>
     * <li>Unprotected speech bits 6, 7, 14, 15, 22, 23, 30, 31, 38, 39, 46, 47, 54, 55, 62, 63, 70, 71-92/7 at
     *     channel bits 106, 107, 119, 120, 132, 133, 145, 146, 158, 159, 12, 13, 26, 27, 40, 41, 54, 55-94/13.</li>
     * </ul>
     */
    AMBE_4650_3350(0x0D, 4650, 3350, 0x005D, 0x0887, 0x3400, 0x0000, 0x0000, 0x31A0),
    /**
     * AMBE+ vocoder, 8000 bps = 8000 bps speech + 0 bps FEC. 160 channel bits per 20 ms frame. Rate index 20
     * (0x14).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 160 bits.
     */
    AMBE_PLUS_8000_0(0x14, 8000, 0, 0x02A0, 0x0997, 0x0000, 0x0000, 0x0000, 0x52A0),
    /**
     * AMBE+ vocoder, 8000 bps = 4000 bps speech + 4000 bps FEC. 160 channel bits per 20 ms frame. Rate index 28
     * (0x1C).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 10 -> 32 bits + (171, 133) rate 1/2, 46 -> 104 bits (PN), with
     * 24 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 10 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-56/7, 62. Coded bits in encoder order (step 0 first, generators in the order listed) at
     *     channel bits 0-56/14, 69-147/13, 1-57/14, 70-148/13, 2-58/14, 71-97/13. Sent outputs (steps 0-15): 171
     *     all steps; 133 all steps.</li>
     * <li>Block 2 (171, 133) rate 1/2, 46 info bits + 6 tail bits, coded bit n XOR PN[n], seeded with block 1's 10
     *     info bits in input order, first bit = MSB. Encoder input, first bit first: speech bits 68, 74, 1-57/7,
     *     63-75/6, 2-58/7, 64-76/6, 3-59/7, 65-77/6, 4-53/7. Coded bits in encoder order (step 0 first, generators
     *     in the order listed) at channel bits 110-149/13, 3-59/14, 72-150/13, 4-60/14, 73-151/13, 5-61/14,
     *     74-152/13, 6-62/14, 75-153/13, 7-63/14, 76-154/13, 8-64/14, 77-155/13, 9-65/14, 78-156/13, 10-66/14,
     *     79-157/13, 11-53/14. Sent outputs (steps 0-51): 171 all steps; 133 all steps.</li>
     * <li>Unprotected speech bits 5, 6, 12, 13, 19, 20, 26, 27, 33, 34, 40, 41, 47, 48, 54, 55, 60, 61, 66, 67, 72,
     *     73, 78, 79 at channel bits 119, 120, 132, 133, 145, 146, 158, 159, 12, 13, 26, 27, 40, 41, 54, 55, 67,
     *     68, 80, 81, 93, 94, 106, 107.</li>
     * </ul>
     */
    AMBE_PLUS_4000_4000(0x1C, 4000, 4000, 0x0250, 0x0765, 0x0005, 0x2010, 0x6834, 0x72A0),
    /**
     * AMBE+2 vocoder, 8000 bps = 8000 bps speech + 0 bps FEC. 160 channel bits per 20 ms frame. Rate index 45
     * (0x2D).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 160 bits.
     */
    AMBE_PLUS2_8000_0(0x2D, 8000, 0, 0x04A0, 0x0986, 0x0000, 0x0000, 0x0000, 0x31A0),
    /**
     * AMBE+2 vocoder, 8000 bps = 4000 bps speech + 4000 bps FEC. 160 channel bits per 20 ms frame. Rate index 60
     * (0x3C).
     * <p>
     * FEC: K=7 convolutional, (171, 133) rate 1/2, 12 -> 36 bits + (171, 133) rate 1/2, 44 -> 100 bits (PN), with
     * 24 unprotected speech bits.
     * <ul>
     * <li>Block 1 (171, 133) rate 1/2, 12 info bits + 6 tail bits, not scrambled. Encoder input, first bit first:
     *     speech bits 0-40/5, 44-52/4. Coded bits in encoder order (step 0 first, generators in the order listed)
     *     at channel bits 0-144/9, 152, 1-145/9, 153. Sent outputs (steps 0-17): 171 all steps; 133 all steps.</li>
     * <li>Block 2 (171, 133) rate 1/2, 44 info bits + 6 tail bits, coded bit n XOR PN[n], seeded with block 1's 12
     *     info bits in input order, first bit = MSB. Encoder input, first bit first: speech bits 56-76/4, 1-41/5,
     *     45-77/4, 2-42/5, 46-78/4, 3, 8. Coded bits in encoder order (step 0 first, generators in the order
     *     listed) at channel bits 2-146/9, 154, 3-147/9, 155, 4-148/9, 156, 5-149/9, 157, 6-150/9, 158, 7-88/9.
     *     Sent outputs (steps 0-49): 171 all steps; 133 all steps.</li>
     * <li>Unprotected speech bits 4, 9, 13, 14, 18, 19, 23, 24, 28, 29, 33, 34, 38, 39-79/4 at channel bits 80, 89,
     *     97, 98, 106, 107, 115, 116, 124, 125, 133, 134, 142, 143-159/8, 8-71/9.</li>
     * </ul>
     */
    AMBE_PLUS2_4000_4000(0x3C, 4000, 4000, 0x0450, 0x0986, 0x0005, 0x2412, 0x6432, 0x72A0),

    //9600 bps total rate
    /**
     * AMBE vocoder, 9600 bps = 9600 bps speech + 0 bps FEC. 192 channel bits per 20 ms frame. Rate index 4 (0x04).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 192 bits.
     */
    AMBE_9600_0(0x04, 9600, 0, 0x00C0, 0x0997, 0x0000, 0x0000, 0x0000, 0x72C0),
    /**
     * AMBE vocoder, 9600 bps = 4850 bps speech + 4750 bps FEC. 192 channel bits per 20 ms frame. Rate index 6
     * (0x06).
     * <p>
     * FEC: Golay(24,12) + 6 x Golay(24,12) (PN) + Golay(23,12) (PN), with 1 unprotected speech bit. 97 speech bits
     * + 95 parity bits.
     * <ul>
     * <li>c0 Golay(24,12), not scrambled. Speech bits 0, 9-89/8 at channel bits 0-176/16. Parity at channel bits
     *     1-177/16.</li>
     * <li>c1 Golay(24,12), PN[0..23]. Speech bits 1, 10-90/8 at channel bits 2-178/16. Parity at channel bits
     *     3-179/16.</li>
     * <li>c2 Golay(24,12), PN[24..47]. Speech bits 2, 11-91/8 at channel bits 4-180/16. Parity at channel bits
     *     5-181/16.</li>
     * <li>c3 Golay(24,12), PN[48..71]. Speech bits 3, 12-92/8 at channel bits 6-182/16. Parity at channel bits
     *     7-183/16.</li>
     * <li>c4 Golay(24,12), PN[72..95]. Speech bits 4, 13-93/8 at channel bits 8-184/16. Parity at channel bits
     *     9-185/16.</li>
     * <li>c5 Golay(24,12), PN[96..119]. Speech bits 5, 14-94/8 at channel bits 10-186/16. Parity at channel bits
     *     11-187/16.</li>
     * <li>c6 Golay(24,12), PN[120..143]. Speech bits 6, 15-95/8 at channel bits 12-188/16. Parity at channel bits
     *     13-189/16.</li>
     * <li>c7 Golay(23,12), PN[144..166]. Speech bits 7, 16-96/8 at channel bits 14-190/16. Parity at channel bits
     *     15-175/16.</li>
     * <li>Unprotected speech bit 8 at channel bit 191.</li>
     * </ul>
     */
    AMBE_4850_4750(0x06, 4850, 4750, 0x0061, 0x0887, 0xE400, 0x0000, 0x0000, 0x67C0),
    /**
     * AMBE+ vocoder, 9600 bps = 9600 bps speech + 0 bps FEC. 192 channel bits per 20 ms frame. Rate index 21
     * (0x15).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 192 bits.
     */
    AMBE_PLUS_9600_0(0x15, 9600, 0, 0x02C0, 0x0997, 0x0000, 0x0000, 0x0000, 0x69C0),
    /**
     * AMBE+ vocoder, 9600 bps = 3600 bps speech + 6000 bps FEC. 192 channel bits per 20 ms frame. Rate index 30
     * (0x1E).
     * <p>
     * FEC: K=7 convolutional, (163, 147, 135, 135) rate 1/4, 10 -> 64 bits + (173, 155, 113) rate 1/3, 40 -> 106
     * bits (PN), with 22 unprotected speech bits.
     * <ul>
     * <li>Block 1 (163, 147, 135, 135) rate 1/4, 10 info bits + 6 tail bits, not scrambled. Encoder input, first
     *     bit first: speech bits 0-54/6. Coded bits in encoder order (step 0 first, generators in the order listed)
     *     at channel bits 0-176/16, 1-177/16, 2-178/16, 3-179/16, 4-180/16, 5-53/16. Sent outputs (steps 0-15): 163
     *     all steps; 147 all steps; 135 (copy 1) all steps; 135 (copy 2) all steps.</li>
     * <li>Block 2 (173, 155, 113) rate 1/3, 40 info bits + 6 tail bits, coded bit n XOR PN[n], seeded with block
     *     1's 10 info bits in input order, first bit = MSB. Encoder input, first bit first: speech bits 60, 66,
     *     1-67/6, 2-68/6, 3-69/6, 4, 10. Coded bits in encoder order (step 0 first, generators in the order listed)
     *     at channel bits 69-181/16, 6-182/16, 7-183/16, 8-184/16, 9-185/16, 10-186/16, 11-187/16, 12-188/16,
     *     13-189/16, 14, 30. Sent outputs (steps 0-45): 173 all steps; 155 all steps; 113 steps 0-9/3, 13-19/3,
     *     23-32/3, 36-42/3.</li>
     * <li>Unprotected speech bits 5, 11, 16, 17, 22, 23, 28, 29, 34, 35, 40, 41, 46, 47, 52, 53, 58, 59, 64, 65,
     *     70, 71 at channel bits 15, 31, 46, 47, 62, 63, 78, 79, 94, 95, 110, 111, 126, 127, 142, 143, 158, 159,
     *     174, 175, 190, 191.</li>
     * </ul>
     */
    AMBE_PLUS_3600_6000(0x1E, 3600, 6000, 0x0248, 0x0763, 0x000E, 0x4010, 0x6A2E, 0x65C0),
    /**
     * AMBE+ vocoder, 9600 bps = 2400 bps speech + 7200 bps FEC. 192 channel bits per 20 ms frame. Rate index 29
     * (0x1D).
     * <p>
     * FEC: K=7 convolutional, (163, 147, 135, 135) rate 1/4, 20 -> 104 bits + (173, 155, 113) rate 1/3, 21 -> 81
     * bits (PN), with 7 unprotected speech bits.
     * <ul>
     * <li>Block 1 (163, 147, 135, 135) rate 1/4, 20 info bits + 6 tail bits, not scrambled. Encoder input, first
     *     bit first: speech bits 0-44/4, 1-29/4. Coded bits in encoder order (step 0 first, generators in the order
     *     listed) at channel bits 0-176/16, 1-177/16, 2-178/16, 3-179/16, 4-180/16, 5-181/16, 6-182/16, 7-183/16,
     *     8-120/16. Sent outputs (steps 0-25): 163 all steps; 147 all steps; 135 (copy 1) all steps; 135 (copy 2)
     *     all steps.</li>
     * <li>Block 2 (173, 155, 113) rate 1/3, 21 info bits + 6 tail bits, coded bit n XOR PN[n], seeded with block
     *     1's first 16 info bits in input order, first bit = MSB. Encoder input, first bit first: speech bits
     *     33-45/4, 2-46/4, 3-19/4. Coded bits in encoder order (step 0 first, generators in the order listed) at
     *     channel bits 136-184/16, 9-185/16, 10-186/16, 11-187/16, 12-188/16, 13-189/16, 14-190/16, 15-79/16. Sent
     *     outputs (steps 0-26): 173 all steps; 155 all steps; 113 all steps.</li>
     * <li>Unprotected speech bits 23-47/4 at channel bits 95-191/16.</li>
     * </ul>
     */
    AMBE_PLUS_2400_7200(0x1D, 2400, 7200, 0x0130, 0x0763, 0x000E, 0x681A, 0x511B, 0x76C0),
    /**
     * AMBE+2 vocoder, 9600 bps = 9600 bps speech + 0 bps FEC. 192 channel bits per 20 ms frame. Rate index 46
     * (0x2E).
     * <p>
     * No FEC. Channel bit n is speech bit n, for all 192 bits.
     */
    AMBE_PLUS2_9600_0(0x2E, 9600, 0, 0x04C0, 0x0986, 0x0000, 0x0000, 0x0000, 0x39C0),
    /**
     * AMBE+2 vocoder, 9600 bps = 3600 bps speech + 6000 bps FEC. 192 channel bits per 20 ms frame. Rate index 61
     * (0x3D).
     * <p>
     * FEC: K=7 convolutional, (173, 155, 113) rate 1/3, 12 -> 54 bits + (173, 155, 113) rate 1/3, 30 -> 108 bits
     * (PN), with 30 unprotected speech bits.
     * <ul>
     * <li>Block 1 (173, 155, 113) rate 1/3, 12 info bits + 6 tail bits, not scrambled. Encoder input, first bit
     *     first: speech bits 0-44/4. Coded bits in encoder order (step 0 first, generators in the order listed) at
     *     channel bits 0-132/11, 142-182/10, 1-133/11, 143-183/10, 2-134/11, 144-184/10. Sent outputs (steps 0-17):
     *     173 all steps; 155 all steps; 113 all steps.</li>
     * <li>Block 2 (173, 155, 113) rate 1/3, 30 info bits + 6 tail bits, coded bit n XOR PN[n], seeded with block
     *     1's 12 info bits in input order, first bit = MSB. Encoder input, first bit first: speech bits 48-68/4,
     *     1-69/4, 2-22/4. Coded bits in encoder order (step 0 first, generators in the order listed) at channel
     *     bits 3-135/11, 145-185/10, 4-136/11, 146-186/10, 5-137/11, 147-187/10, 6-138/11, 148-188/10, 7-139/11,
     *     149-189/10, 8-140/11, 150-190/10. Sent outputs (steps 0-35): 173 all steps; 155 all steps; 113 all
     *     steps.</li>
     * <li>Unprotected speech bits 3-23/4, 26, 27, 30, 31, 34, 35, 38, 39, 42, 43, 46, 47, 50, 51, 54, 55, 58, 59,
     *     62, 63, 66, 67, 70, 71 at channel bits 141-191/10, 9, 10, 20, 21, 31, 32, 42, 43, 53, 54, 64, 65, 75, 76,
     *     86, 87, 97, 98, 108, 109, 119, 120, 130, 131.</li>
     * </ul>
     */
    AMBE_PLUS2_3600_6000(0x3D, 3600, 6000, 0x0448, 0x0766, 0x000A, 0x3612, 0x6C24, 0x76C0),

    /**
     * Unknown or unrecognized rate index
     */
    UNKNOWN(0xFF, 0, 0, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000);

    private static final int RATE_CONTROL_WORD_COUNT = 6;
    private static final int NO_RATE_INDEX = 0xFF;

    private final int mValue;
    private final int mRawRate;
    private final int mFecRate;
    private final int[] mRateControlWords;

    /**
     * Constructs an instance
     *
     * @param value rate index (Table 114) used by the PKT_RATET packet field, or 0xFF when the rate has no index
     * @param rawRate speech rate in bits per second (bps), before forward error correction
     * @param fecRate forward error correction rate in bits per second (bps)
     * @param rateControlWords RCW0 through RCW5, each a 16-bit value
     */
    VocoderRate(int value, int rawRate, int fecRate, int... rateControlWords)
    {
        if(rateControlWords.length != RATE_CONTROL_WORD_COUNT)
        {
            throw new IllegalArgumentException("Expected " + RATE_CONTROL_WORD_COUNT + " rate control words");
        }

        mValue = value;
        mRawRate = rawRate;
        mFecRate = fecRate;
        mRateControlWords = rateControlWords;
    }

    /**
     * Lookup the rate from the specified rate index value
     *
     * @param value to lookup
     * @return matching entry or UNKNOWN
     */
    public static VocoderRate fromValue(int value)
    {
        if((value & 0xFF) != NO_RATE_INDEX)
        {
            for(VocoderRate rate : VocoderRate.values())
            {
                if(rate.mValue == (value & 0xFF))
                {
                    return rate;
                }
            }
        }

        return UNKNOWN;
    }

    /**
     * Byte code value for the rate index (Table 114).  Entries without a rate index, and UNKNOWN, return 0xFF.
     */
    public byte getValue()
    {
        return (byte)mValue;
    }

    /**
     * Indicates if this rate has a rate index that can be selected with the PKT_RATET packet field.  Rates without
     * an index can only be selected with their rate control words.
     */
    public boolean hasRateIndex()
    {
        return mValue != NO_RATE_INDEX;
    }

    /**
     * Rate control words RCW0 through RCW5, each a 16-bit value.
     *
     * @return a copy of the 6 element rate control word array
     */
    public int[] getRateControlWords()
    {
        return mRateControlWords.clone();
    }

    /**
     * Raw (speech) rate in bits per second (bps), before forward error correction
     */
    public int getRawRate()
    {
        return mRawRate;
    }

    /**
     * Forward Error Correction rate in bits per second (bps)
     */
    public int getFecRate()
    {
        return mFecRate;
    }

    /**
     * Total channel rate in bits per second (bps): raw rate plus FEC rate
     */
    public int getTotalRate()
    {
        return mRawRate + mFecRate;
    }
}
