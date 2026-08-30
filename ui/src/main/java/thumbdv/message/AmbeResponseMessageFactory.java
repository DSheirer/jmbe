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

package thumbdv.message;

import thumbdv.message.response.AmbeResponse;
import thumbdv.message.response.DecodeSpeechResponse;
import thumbdv.message.response.EncodeSpeechResponse;
import thumbdv.message.response.GetOrReadConfigResponse;
import thumbdv.message.response.InitializeCodecResponse;
import thumbdv.message.response.ProductIdResponse;
import thumbdv.message.response.ReadConfigResponse;
import thumbdv.message.response.ReadyResponse;
import thumbdv.message.response.SetChannelFormatResponse;
import thumbdv.message.response.SetChannelResponse;
import thumbdv.message.response.SetPacketModeResponse;
import thumbdv.message.response.SetSpeechFormatResponse;
import thumbdv.message.response.SetVocoderParameterResponse;
import thumbdv.message.response.SetVocoderResponse;
import thumbdv.message.response.UnknownResponse;
import thumbdv.message.response.VersionResponse;

/**
 * Factory for identifying and creating AMBE response messages.
 */
public class AmbeResponseMessageFactory
{
    private static final byte CONTROL_PACKET = (byte) 0x00;
    private static final byte CHANNEL_PACKET = (byte) 0x01;
    private static final byte SPEECH_PACKET = (byte) 0x02;
    private static final int INDEX_PACKET_TYPE = 3;
    private static final int INDEX_CONTROL_PACKET_TYPE = 4;

    /**
     * Creates an AMBE response message from the specified byte array.
     *
     * @param data payload for a response message
     * @return response message
     */
    public static AmbeResponse getMessage(byte[] data)
    {
        if(data != null && data.length >= 5)
        {
            if(data[INDEX_PACKET_TYPE] == CONTROL_PACKET)
            {
                PacketField packetField = PacketField.fromValue(data[INDEX_CONTROL_PACKET_TYPE]);

                switch(packetField)
                {
                    case PKT_CHANNEL_0:
                        return new SetChannelResponse(data);
                    case PKT_CHANNEL_FORMAT:
                        return new SetChannelFormatResponse(data);
                    case PKT_CODEC_STOP:
                        return new SetPacketModeResponse(data);
                    case PKT_GET_CONFIG:
                    case PKT_READ_CONFIG:
                        //Deliberate fall through
                        return new GetOrReadConfigResponse(data);
                    case PKT_INIT:
                        return new InitializeCodecResponse(data);
                    case PKT_PRODUCT_ID:
                        return new ProductIdResponse(data);
                    case PKT_RATE_PARAMETER:
                        return new SetVocoderParameterResponse(data);
                    case PKT_RATE_TABLE:
                        return new SetVocoderResponse(data);
                    case PKT_READY:
                        return new ReadyResponse(data);
                    case PKT_SPEECH_FORMAT:
                        return new SetSpeechFormatResponse(data);
                    case PKT_VERSION_STRING:
                        return new VersionResponse(data);
                }
            }
            else if(data[INDEX_PACKET_TYPE] == CHANNEL_PACKET)
            {
                return new EncodeSpeechResponse(data);
            }
            else if(data[INDEX_PACKET_TYPE] == SPEECH_PACKET)
            {
                return new DecodeSpeechResponse(data);
            }
        }

        return new UnknownResponse(data);
    }
}
