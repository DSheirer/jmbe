/*
 * *****************************************************************************
 * Copyright (C) 2014-2022 Dennis Sheirer
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
 * ****************************************************************************
 */

package thumbdv.message.response;

import java.nio.ByteOrder;
import java.util.Arrays;
import jmbe.binary.BinaryFrame;
import thumbdv.message.AmbeMessage;
import thumbdv.message.PacketField;

/**
 * AMBE-3000R Response Packet
 */
public abstract class AmbeResponse extends AmbeMessage
{
    protected static final int PAYLOAD_START_INDEX = 5;
    private final byte[] mMessage;
    private final BinaryFrame mFrame;

    protected AmbeResponse(byte[] message)
    {
        mMessage = message;
        mFrame = BinaryFrame.fromBytes(getPayload(), ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Binary frame wrapper for the message.
     */
    protected BinaryFrame getFrame()
    {
        return mFrame;
    }

    /**
     * Control packet type
     */
    public abstract PacketField getType();

    /**
     * Received message bytes
     */
    protected byte[] getMessage()
    {
        return mMessage;
    }

    /**
     * Payload of the packet (does not include the packet header)
     */
    protected byte[] getPayload()
    {
        return Arrays.copyOfRange(getMessage(), PAYLOAD_START_INDEX, getMessage().length);
    }
}
