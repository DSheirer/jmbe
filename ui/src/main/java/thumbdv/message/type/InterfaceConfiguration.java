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

import java.util.EnumSet;

/**
 * Interface configurations enumeration.
 */
public enum InterfaceConfiguration
{
    CODEC_SPI_UART(0),
    CODEC_SPI_PPT(1),
    CODEC_SPI_MCBSP(2),
    CODEC_MCBSP_UART(3),
    CODEC_MCBSP_PPT(4),
    PACKET_UART(5),
    PACKET_PPT(6),
    PACKET_MCBSP(7),
    UNKNOWN(-1);

    private final int mValue;

    private static final EnumSet<InterfaceConfiguration> CODEC_MODES = EnumSet.range(CODEC_SPI_UART, CODEC_MCBSP_PPT);
    private static final EnumSet<InterfaceConfiguration> PACKET_MODES = EnumSet.range(PACKET_UART, PACKET_MCBSP);

    InterfaceConfiguration(int value)
    {
        mValue = value;
    }

    /**
     * Indicates if this is a PACKET mode.
     */
    public boolean isPacketMode()
    {
        return PACKET_MODES.contains(this);
    }

    /**
     * Indicates if this is a CODEC mode.
     */
    public boolean isCodecMode()
    {
        return CODEC_MODES.contains(this);
    }

    /**
     * Value of the bit registers.
     */
    public int getValue()
    {
        return mValue;
    }

    /**
     * Utility method to lookup a configuration from a value
     *
     * @param value to lookup
     * @return configuration or UNKNOWN if not found
     */
    public static InterfaceConfiguration fromValue(int value)
    {
        if(0 <= value && value <= 7)
        {
            return InterfaceConfiguration.values()[value];
        }

        return UNKNOWN;
    }
}
