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

import thumbdv.message.PacketField;

/**
 * Set speech format request packet
 */
public class SetSpeechFormatRequest extends AmbeRequest
{
    private static final int FORMAT_INDEX_HIGH = 5;
    private static final int FORMAT_INDEX_LOW = 6;

    @Override
    public PacketField getType()
    {
        return PacketField.PKT_SPEECH_FORMAT;
    }

    @Override
    public byte[] getData()
    {
        byte[] data = createMessage(3, getType());
        data[FORMAT_INDEX_HIGH] = (byte)0x00;
        data[FORMAT_INDEX_LOW] = (byte)0x05; //Always include sample count; always include DCMODE flags
        return data;
    }
}
