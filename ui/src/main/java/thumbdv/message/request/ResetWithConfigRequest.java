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

import java.util.logging.Logger;
import jmbe.binary.BinaryFrame;
import thumbdv.message.AmbeMessage;
import thumbdv.message.type.Compander;
import thumbdv.message.type.InterfaceConfiguration;
import thumbdv.message.PacketField;
import thumbdv.message.type.UartBaudRate;
import thumbdv.message.type.VocoderRate;

/**
 * Reset request packet with configuration values
 */
public class ResetWithConfigRequest extends AmbeRequest
{
    private static final int[] INTERFACE_SELECTION = new int[] { 0, 1, 2 };
    private static final int DTX_ENABLE = 3;
    private static final int NOISE_SUPPRESSOR_ENABLE = 5;
    private static final int[] COMPANDER = new int[] { 6, 7 };
    private static final int[] VOCODER_RATE = new int[] { 8, 9, 10, 11, 12, 13 };
    private static final int ECHO_CANCELLER_ENABLE = 14;
    private static final int ECHO_SUPPRESSOR_ENABLE = 15;
    private static final int[] UART_BAUD_RATE = new int[] { 16, 17, 18 };
    private static final int PARITY_ENABLE = 20;

    private final InterfaceConfiguration mInterfaceConfiguration;
    private final VocoderRate mVocoderRate;
    private UartBaudRate mUartBaudRate = UartBaudRate.RATE_460_800;
    private Compander mCompander = Compander.OFF;
    private boolean mDTXEnabled = false;
    private boolean mEchoCancelerEnabled = false;
    private boolean mEchoSuppressorEnabled = false;
    private boolean mNoiseSuppressorEnabled = true;
    private boolean mParityEnabled = true;

    /**
     * MASK bits.  Although the ICD shows CFG0 bit 4 must be set to zero, the device allows that bit register to be
     * masked as writable.  Also, the default read config state of CFG2 is 0xEC even though bit 5 is reserved. The mask
     * uses a value of E8 to align with the ICD reserved bits and attempting to set it any other way results in an
     * error state.
     **/
    private static final byte[] MASK = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xE8};

    /**
     * Constructs an instance
     * @param interfaceConfiguration to use
     */
    public ResetWithConfigRequest(InterfaceConfiguration interfaceConfiguration, VocoderRate vocoderRate)
    {
        mInterfaceConfiguration = interfaceConfiguration;
        mVocoderRate = vocoderRate;
    }

    @Override
    public PacketField getType()
    {
        return PacketField.PKT_RESET_SOFT_CONFIG;
    }

    @Override
    public byte[] getData()
    {
        BinaryFrame frame = new BinaryFrame(24);
        frame.setInt(INTERFACE_SELECTION, mInterfaceConfiguration.getValue());
        frame.set(DTX_ENABLE, mDTXEnabled);
        frame.set(NOISE_SUPPRESSOR_ENABLE, mNoiseSuppressorEnabled);
        frame.setInt(COMPANDER, mCompander.getValue());
        frame.setInt(VOCODER_RATE, mVocoderRate.getValue());
        frame.set(ECHO_CANCELLER_ENABLE, mEchoCancelerEnabled);
        frame.set(ECHO_SUPPRESSOR_ENABLE, mEchoSuppressorEnabled);
        frame.setInt(UART_BAUD_RATE, mUartBaudRate.getValue());
        frame.set(PARITY_ENABLE, mParityEnabled);
        byte[] data = createMessage(7, getType());
        byte[] payload = frame.getBytes(3);
        System.arraycopy(payload, 0, data, 5, payload.length);
        System.arraycopy(MASK, 0, data, 8, MASK.length);
        return data;
    }

    /**
     * Enable Voice Activity Detection (VAD) and Comfort Noise Insertion (CNI) to insert white noise during periods of
     * no voice activity.
     *
     * @param enable true to enable, false to disable.  Disabled by default.
     */
    public void setDTXEnabled(boolean enable)
    {
        mDTXEnabled = enable;
    }

    /**
     * Enables (default) or disables the noise suppressor feature.
     */
    public void setNoiseSuppressorEnabled(boolean enable)
    {
        mNoiseSuppressorEnabled = enable;
    }

    /**
     * Sets the enabled or disabled (default) state of the compander and the compander type.
     * @param compander enabled and type
     */
    public void setCompander(Compander compander)
    {
        mCompander = compander;
    }

    /**
     * Sets the enabled or disabled (default) state of the echo canceler feature.
\     */
    public void setEchoCancelerEnabled(boolean enable)
    {
        mEchoCancelerEnabled = enable;
    }

    /**
     * Sets the enabled or disabled (default)  state of the echo suppressor feature.
     */
    public void setEchoSuppressorEnabled(boolean enable)
    {
        mEchoSuppressorEnabled = enable;
    }

    /**
     * Sets the baud rate (460k default) for the UART serial port.
     * @param baudRate for the port.
     */
    public void setUartBaudRate(UartBaudRate baudRate)
    {
        mUartBaudRate = baudRate;
    }

    /**
     * Sets the enabled (default) or disabled state of parity feature.
     */
    public void setParityEnabled(boolean enable)
    {
        mParityEnabled = enable;
    }
}
