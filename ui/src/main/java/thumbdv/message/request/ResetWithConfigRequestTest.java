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

package thumbdv.message.request;

import jmbe.binary.BinaryFrame;
import thumbdv.message.PacketField;
import thumbdv.message.type.Compander;
import thumbdv.message.type.InterfaceConfiguration;
import thumbdv.message.type.UartBaudRate;
import thumbdv.message.type.VocoderRate;

/**
 * Reset request packet with configuration values
 */
public class ResetWithConfigRequestTest extends AmbeRequest
{
    /**
     * Constructs an instance
     */
    public ResetWithConfigRequestTest()
    {
    }

    @Override
    public PacketField getType()
    {
        return PacketField.PKT_RESET_SOFT_CONFIG;
    }

    @Override
    public byte[] getData()
    {
        byte[] data = createMessage(7, getType());
        data[5] = (byte)0x05; //Interface
        data[6] = (byte)0x21; //Vocoder rate 33 (0x21)
        data[7] = (byte)0x00;
        data[8] = (byte)0x0F;
        data[9] = (byte)0x3F;
        data[10] = (byte)0x00;
        return data;
    }

    @Override
    public String toString()
    {
        return "RESET WITH CONFIG INTERFACE - CUSTOM: " + toHex(getData());
    }
}
