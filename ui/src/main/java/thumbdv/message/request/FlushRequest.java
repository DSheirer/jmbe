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

package thumbdv.message.request;

import thumbdv.message.PacketField;

/**
 * Sent as a final request to flush decode or encode requests and fully read the response stream.
 */
public class FlushRequest extends AmbeRequest
{
    @Override
    public PacketField getType()
    {
        return PacketField.UNKNOWN;
    }

    @Override
    public byte[] getData()
    {
        return new byte[0];
    }
}
