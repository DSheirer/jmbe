/*******************************************************************************
 * sdr-trunk
 * Copyright (C) 2014-2019 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by  the Free Software Foundation, either version 3 of the License, or  (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,  but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License  along with this program.
 * If not, see <http://www.gnu.org/licenses/>
 *
 ******************************************************************************/

package thumbdv.message.response;

import ch.qos.logback.core.util.StringUtil;
import java.util.Arrays;
import thumbdv.message.AmbeMessage;
import thumbdv.message.PacketField;
import thumbdv.util.Utils;

/**
 * Decode speech response
 */
public class DecodeSpeechResponse extends AmbeResponse
{
    private static final int OFFSET_DCMODE = 326;
    private static final int OFFSET_DCMODE_HIGH = 327;
    private static final int OFFSET_DCMODE_LOW = 328;
    private static final byte DCMODE_FLAG = (byte)0x02;
    private static final byte DATA_INVALID = (byte)0x20; //0x0020
    private static final byte TONE_FRAME = (byte)0x80; //0x8002
    private static final byte VOICE_ACTIVE = (byte)0x02; //0x0002
    private static final byte EMPTY = (byte)0x00;

    public DecodeSpeechResponse(byte[] message)
    {
        super(message);
    }

    @Override
    public PacketField getType()
    {
        return PacketField.PACKET_TYPE_SPEECH;
    }


    /**
     * Payload of the packet (does not include the packet header) containing just audio samples
     */
    public byte[] getAudioPayload()
    {
        return Arrays.copyOfRange(getMessage(), PAYLOAD_START_INDEX + 1, PAYLOAD_START_INDEX + 321);
    }

    /** The raw 16-bit DCMODE value, or -1 when the response has no DCMODE field. */
    public int getDCMODEValue()
    {
        return hasDCMODEField() ? ((getMessage()[OFFSET_DCMODE_HIGH] & 0xFF) << 8) | (getMessage()[OFFSET_DCMODE_LOW] & 0xFF)
            : -1;
    }

    public boolean hasDCMODEField()
    {
        return getMessage().length >= 329 && getMessage()[OFFSET_DCMODE] == DCMODE_FLAG;
    }

    /**
     * DCMODE flags from Table 15.
     */
    public String getDCMODE()
    {
        if(hasDCMODEField())
        {
            if(getMessage()[OFFSET_DCMODE_HIGH] == TONE_FRAME)
            {
                return "TONE FRAME";
            }
            else if(getMessage()[OFFSET_DCMODE_LOW] == DATA_INVALID)
            {
                return "INVALID DATA - FRAME REPEAT";
            }
            else if(getMessage()[OFFSET_DCMODE_LOW] == VOICE_ACTIVE)
            {
                return "VOICE ACTIVE";
            }
            else if(getMessage()[OFFSET_DCMODE_LOW] == EMPTY)
            {
                return "COMFORT NOISE INSERTED";
            }
            else
            {
                return "UNRECOGNIZED DCMODE VALUES [" + AmbeMessage.toHex(Arrays.copyOfRange(getMessage(), OFFSET_DCMODE_LOW,
                        OFFSET_DCMODE_HIGH + 1)) + "]";
            }
        }

        return "";
    }

    public float[] getSamples()
    {
        byte[] payload = getAudioPayload();
        float[] samples = new float[payload.length / 2];

        for(int i = 0; i < payload.length; i += 2)
        {
            short s = (short)(((payload[i] & 0xFF) << 8) | (payload[i + 1] & 0xFF));
            samples[i / 2] = (float)s / Short.MAX_VALUE;
        }

        return samples;
    }

    @Override
    public String toString()
    {
        if(getDCMODE().contentEquals("TONE FRAME"))
        {
            StringBuilder sb = new StringBuilder();
            sb.append("DECODED SPEECH TONE FRAME ").append(AmbeMessage.toHex(getMessage()));
            // The audio samples only: getPayload() also holds the sample count byte and the trailing DCMODE field,
            // which ran this loop past the 160 samples (IndexOutOfBounds on every tone frame response)
            byte[] payload = getAudioPayload();
            float[] samples = getSamples();
            for(int x = 0; x + 1 < payload.length; x += 2)
            {
                sb.append("\n\t").append(x).append(": ");
                sb.append(String.format("%02X", payload[x]));
                sb.append(String.format("%02X", payload[x + 1])).append(" ");
                short s = (short)(((payload[x] & 0xFF) << 8) | (payload[x + 1] & 0xFF));
                sb.append(s).append(String.format("%6d", s));
                sb.append(" ").append((float)s / Short.MAX_VALUE);
                sb.append(" SAMPLE:").append(samples[x / 2]);
            }

            return sb.toString();

        }
        else
        {
            return "DECODED SPEECH " + getDCMODE() + " " + AmbeMessage.toHex(getMessage());
        }
    }
}
