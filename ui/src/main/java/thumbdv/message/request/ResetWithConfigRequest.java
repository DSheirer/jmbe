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
import thumbdv.message.type.Compander;
import thumbdv.message.type.InterfaceConfiguration;
import thumbdv.message.PacketField;
import thumbdv.message.type.UartBaudRate;
import thumbdv.message.type.VocoderRate;

import java.nio.ByteOrder;

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
    private boolean mDTXEnable = false;
    private boolean mNoiseSuppressor = true;
    private Compander mCompander = Compander.OFF;
    private VocoderRate mVocoderRate = VocoderRate.RATE_0;
    private boolean mEchoCanceller = false;
    private boolean mEchoSuppressor = false;
    private UartBaudRate mUartBaudRate = UartBaudRate.RATE_460_800;
    private boolean mParity = true;
    private static final byte[] MASK = new byte[]{(byte)0xF7, (byte)0xFF, (byte)0xE4};

    public ResetWithConfigRequest(InterfaceConfiguration interfaceConfiguration)
    {
        mInterfaceConfiguration = interfaceConfiguration;
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
        frame.set(DTX_ENABLE, mDTXEnable);
        frame.set(NOISE_SUPPRESSOR_ENABLE, mNoiseSuppressor);
        frame.setInt(COMPANDER, mCompander.getValue());
        frame.setInt(VOCODER_RATE, mVocoderRate.getValue());
        frame.set(ECHO_CANCELLER_ENABLE, mEchoCanceller);
        frame.set(ECHO_SUPPRESSOR_ENABLE, mEchoSuppressor);
        frame.setInt(UART_BAUD_RATE, mUartBaudRate.getValue());
        frame.set(PARITY_ENABLE, mParity);

        byte[] data = createMessage(6, getType());
        byte[] payload = frame.toByteArray();
        System.arraycopy(payload, 0, data, 4, payload.length);
        System.arraycopy(MASK, 0, data, 7, MASK.length);

        return data;
    }

    /**
     * Enable Voice Activity Detection (VAD) and Comfort Noise Insertion (CNI) to insert white noise during periods of
     * no voice activity.
     *
     * @param enable true to enable, false to disable.  Disabled by default.
     */
    public void setDTXEnable(boolean enable)
    {
        mDTXEnable = enable;
    }
}
