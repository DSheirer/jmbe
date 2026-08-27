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
 * Compander enable and type selection.
 */
public enum Compander
{
    OFF(0),
    ON_U_LAW(2),
    ON_A_LAW(3),
    UNKNOWN(-1);

    private int mValue;

    /**
     * Constructor
     *
     * @param value for the entry
     */
    Compander(int value)
    {
        mValue = value;
    }

    /**
     * Value of the compander enumeration entry
     *
     * @return value
     */
    public int getValue()
    {
        return mValue;
    }

    /**
     * Utility method to get the compander enumeration entry corresponding to the given value.
     *
     * @param value to look up
     * @return matching entry or UNKNOWN
     */
    public static Compander fromValue(int value)
    {
        return switch(value)
        {
            case 0, 1 -> OFF;
            case 2 -> ON_U_LAW;
            case 3 -> ON_A_LAW;
            default -> UNKNOWN;
        };
    }
}
