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
 * UART Baud Rate enumeration
 */
public enum UartBaudRate
{
    RATE_28_800(0),
    RATE_57_600(1),
    RATE_115_200(2),
    RATE_230_400(3),
    RATE_460_800(4),
    UNKNOWN(-1);

    private int mValue;

    /**
     * Constructor
     *
     * @param value for the entry
     */
    UartBaudRate(int value)
    {
        mValue = value;
    }

    /**
     * Value of the entry
     *
     * @return value
     */
    public int getValue()
    {
        return mValue;
    }

    /**
     * Returns the UartBaudRate value corresponding to the given value.
     *
     * @param value from the ICD
     * @return matching entry or UNKNOWN
     */
    public static UartBaudRate fromValue(int value)
    {
        return switch(value)
        {
            case 0 -> RATE_28_800;
            case 1 -> RATE_57_600;
            case 2 -> RATE_115_200;
            case 3 -> RATE_230_400;
            case 4, 5, 6, 7 -> RATE_460_800;
            default -> UNKNOWN;
        };
    }
}
