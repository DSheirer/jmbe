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

package thumbdv.message.response;

import thumbdv.message.AmbeMessage;
import thumbdv.message.PacketField;
import thumbdv.message.type.Compander;
import thumbdv.message.type.InterfaceConfiguration;
import thumbdv.message.type.UartBaudRate;
import thumbdv.message.type.VocoderRate;

/**
 * Get configuration response
 */
public class GetOrReadConfigResponse extends AmbeResponse
{
    //Bit indices are in order MSB to LSB.  The ICD table shows reverse, LSB to MSB order.
    private static final int[] COMPANDER = new int[] { 0, 1 };
    private static final int NOISE_SUPPRESSOR_ENABLE = 2;
    //Bit 3 must always be zero.
    private static final int DTX_ENABLE = 4;
    private static final int[] INTERFACE_SELECTION = new int[] { 5, 6, 7 };

    private static final int ECHO_SUPPRESSOR_ENABLE = 8;
    private static final int ECHO_CANCELLER_ENABLE = 9;
    private static final int[] VOCODER_RATE = new int[] { 10, 11, 12, 13, 14, 15 };

    //Bit 16-18 Reserved
    private static final int PARITY_ENABLE = 19;
    //Bit 20 Reserved
    private static final int[] UART_BAUD_RATE = new int[] { 21, 22, 23 };

    public GetOrReadConfigResponse(byte[] message)
    {
        super(message);
    }

    /**
     * Physical interface selection
     */
    public InterfaceConfiguration getInterface()
    {
        return InterfaceConfiguration.fromValue(getFrame().getInt(INTERFACE_SELECTION));
    }

    /**
     * Discontinuous transmission.  Indicates if the Voice Activity Detection (VAD) and Comfort Noise Insertion (CNI)
     * are enabled.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isDTX()
    {
        return getFrame().get(DTX_ENABLE);
    }

    public boolean isNoiseSuppressor()
    {
        return getFrame().get(NOISE_SUPPRESSOR_ENABLE);
    }

    public boolean isEchoCanceller()
    {
        return getFrame().get(ECHO_CANCELLER_ENABLE);
    }

    public boolean isEchoSuppressor()
    {
        return getFrame().get(ECHO_SUPPRESSOR_ENABLE);
    }

    /**
     * Compander enable and compander type status.
     * @return compander entry.
     */
    public Compander getCompander()
    {
        return Compander.fromValue(getFrame().getInt(COMPANDER));
    }

    /**
     * Vocoder rate setting.
     * @return vocoder rate.
     */
    public VocoderRate getVocoderRate()
    {
        return VocoderRate.fromValue(getFrame().getInt(VOCODER_RATE));
    }

    /**
     * UART comm port rate setting.
     * @return baud rate.
     */
    public UartBaudRate getUartBaudRate()
    {
        return UartBaudRate.fromValue(getFrame().getInt(UART_BAUD_RATE));
    }

    /**
     * Indicates if parity is enabled.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isParityEnabled()
    {
        return getFrame().get(PARITY_ENABLE);
    }

    @Override
    public PacketField getType()
    {
        return PacketField.PKT_GET_CONFIG;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("CONFIGURATION -");
        sb.append(" VOCODER:").append(getVocoderRate());
        sb.append(" INTERFACE:").append(getInterface());
        sb.append(" UART BAUD RATE:").append(getUartBaudRate());
        sb.append(" COMPANDER:").append(getCompander());
        sb.append(" DTX (VAD/CNI):").append(isDTX());
        sb.append(" PARITY ENABLE:").append(isParityEnabled());
        sb.append(" ECHO CANCELLER:").append(isEchoCanceller());
        sb.append(" ECHO SUPPRESSOR:").append(isEchoSuppressor());
        sb.append(" NOISE SUPPRESSOR:").append(isNoiseSuppressor());
        sb.append(" MSG [").append(AmbeMessage.toHex(getPayload()).trim()).append("]");

        return sb.toString();
    }
}
