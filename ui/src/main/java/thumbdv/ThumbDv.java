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

package thumbdv;

import com.fazecast.jSerialComm.SerialPort;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thumbdv.message.AmbeMessage;
import thumbdv.message.InitializeOption;
import thumbdv.message.request.AmbeRequest;
import thumbdv.message.request.DecodeSpeechRequest;
import thumbdv.message.request.InitializeCodecRequest;
import thumbdv.message.request.ProductIdRequest;
import thumbdv.message.request.SetVocoderRequest;
import thumbdv.message.request.VersionRequest;
import thumbdv.message.response.AmbeResponse;
import thumbdv.message.type.VocoderRate;

/**
 * Northwest Digital Radio (NWDR) ThumbDv dongle.
 *
 * Note: linux users must allow access to the serial port:
 *
 * sudo usermod -a -G uucp username
 * sudo usermod -a -G dialout username
 * sudo usermod -a -G lock username
 * sudo usermod -a -G tty username
 */
public class ThumbDv implements AutoCloseable
{
    private final static Logger LOG = LoggerFactory.getLogger(ThumbDv.class);
    private static final String PORT_DESCRIPTION = "USB-to-Serial Port (ftdi_sio)";
    private static final String PORT_DESCRIPTION_FRAGMENT = "ttyUSB0";
    private static final String PORT_DESCRIPTION_WINDOWS = "COM3";

    public enum AudioProtocol
    {
        DMR,
        DSTAR,
        NXDN,
        P25_PHASE2,
    }

    private AudioProtocol mAudioProtocol;
    private SerialCommunicationManager mCommunicationManager;

    public ThumbDv()
    {
    }

    /**
     * Opens and configures the ThumbDV to support encoding and decoding audio frames for the audio protocol.
     */
    public void open()
    {
        if(mCommunicationManager == null)
        {
            LOG.info("Starting");

            SerialPort[] ports = SerialPort.getCommPorts();
            LOG.info("Discovered [" + ports.length + "] serial ports");
            SerialPort thumbDVPort = null;

            for(SerialPort port : ports)
            {
                if(port.getDescriptivePortName().contentEquals(PORT_DESCRIPTION) ||
                        port.getSystemPortName().contains(PORT_DESCRIPTION_FRAGMENT) ||
                        port.getSystemPortName().contentEquals(PORT_DESCRIPTION_WINDOWS))
                {
                    thumbDVPort = port;
                    LOG.info("Selected Serial Port: " + port.getSystemPortName() + " - " + port.getDescriptivePortName());
                }
                else
                {
                    LOG.info("Available Serial Port: " + port.getSystemPortName() + " - " + port.getDescriptivePortName());
                }
            }

            if(thumbDVPort != null)
            {
                mCommunicationManager = new SerialCommunicationManager(thumbDVPort);

                try
                {
                    mCommunicationManager.open();
                    AmbeResponse response;
//                  response = send(new ResetRequest()).get(2, TimeUnit.SECONDS);
//                    LOG.info("Reset Response: " + response);
                    response = send(new ProductIdRequest()).get(2, TimeUnit.SECONDS);
                    LOG.info("Product ID: " + response);
                    response = send(new VersionRequest()).get(2, TimeUnit.SECONDS);
                    LOG.info("   Version: " + response);
                    LOG.info("ThumbDV Device ready.");
                }
                catch(Exception e)
                {
                    LOG.error("Error opening serial port and resetting device", e);
                }
            }
        }
    }

    public void close()
    {
        if(mCommunicationManager != null)
        {
            mCommunicationManager.close();
            mCommunicationManager = null;
        }
    }

    /**
     * Sends the AMBE request message
     *
     * @param request message
     */
    public Future<AmbeResponse> send(AmbeRequest request)
    {
        if(mCommunicationManager != null)
        {
            return mCommunicationManager.send(request);
        }

        return CompletableFuture.failedFuture(new IllegalStateException("ThumbDV not available"));
    }

    public static void main(String[] args)
    {
        LOG.info("Starting");

        ThumbDv thumbDv = new ThumbDv();

        try
        {
            thumbDv.open();
            AmbeRequest request;
            AmbeResponse response;

            request = new InitializeCodecRequest(InitializeOption.DECODER);
            response = thumbDv.send(request).get(2, TimeUnit.SECONDS);
            LOG.info("Initialize Codec Response: " + response);

            request = new SetVocoderRequest(VocoderRate.RATE_33);
            response = thumbDv.send(request).get(2, TimeUnit.SECONDS);
            LOG.info("Set Vocoder Response: " + response);

//            AmbeResponse response = thumbDv.send(new GetConfigRequest()).get(2, TimeUnit.SECONDS);
//            LOG.info("Startup Configuration Response: " + response);
//
//            ResetWithConfigRequest configRequest = new ResetWithConfigRequest(InterfaceConfiguration.PACKET_UART,
//                    VocoderRate.RATE_33); //DMR, NXDN and P25-2
//            configRequest.setUartBaudRate(UartBaudRate.RATE_460_800);
//            configRequest.setCompander(Compander.OFF);
//            configRequest.setDTXEnabled(false);
//            configRequest.setEchoCancelerEnabled(false);
//            configRequest.setEchoSuppressorEnabled(false);
//            configRequest.setNoiseSuppressorEnabled(true);
//            configRequest.setParityEnabled(true);
//
//            response = thumbDv.send(configRequest).get(2, TimeUnit.SECONDS);
//            LOG.info("Soft Reset Config Response: " + response);
//
//            //Confirm that the settings take effect ...
//            response = thumbDv.send(new GetConfigRequest()).get(2, TimeUnit.SECONDS);
//            LOG.info("Config Verification Response: " + response);

//            response = thumbDv.send(new SetPacketModeRequest()).get(2, TimeUnit.SECONDS);
//            LOG.info("Set Packet Mode: " + response);


            String[] frames = {"0E46122323067C60F8", "0E469433C1067CF1BC", "0E46122B23067C60F8", "0E67162BE08874E2B4",
                    "0E46163BE1067CF1BC", "0E46122B23067C60F8", "0A06163BE00A5C303E", "0E46122B23067C60F8", "0E46163BE1847CE1FC",
                    "0E46122B23067C60F8"};

            List<byte[]> frameData = new ArrayList<>();

            for(String frame : frames)
            {
                byte[] bytes = new byte[frame.length() / 2];
                for(int x = 0; x < frame.length(); x += 2)
                {
                    String hex = frame.substring(x, x + 2);
                    bytes[x / 2] = (byte) (0xFF & Integer.parseInt(hex, 16));
                }

                frameData.add(bytes);
            }

//            List<byte[]> ambeFrames = new ArrayList<>();
//
//            for(int x = 0; x < 5; x++)
//            {
//                EncodeSpeechRequest request = new EncodeSpeechRequest(new short[160]);
//                response = thumbDv.send(request).get(1, TimeUnit.SECONDS);
//
//                if(response instanceof EncodeSpeechResponse esr)
//                {
//                    ambeFrames.add(esr.getEncodedSpeech());
//                }
//
//                LOG.info("Encode Response: " + response);
//            }

            //Debug - resend the packet mode?
//            response = thumbDv.send(new SetPacketModeRequest()).get(2, TimeUnit.SECONDS);
//            LOG.info("Set Packet Mode before requesting decode: " + response);

            List<Future<AmbeResponse>> futures = new ArrayList<>();

            int counter = 0;

            for(byte[] ambeFrame: frameData)
            {
                counter++;
                request = new DecodeSpeechRequest(ambeFrame);
                LOG.info("Decode Request: " + AmbeMessage.toHex(request.getData()));
//                futures.add(thumbDv.send(new DecodeSpeechRequest(ambeFrame)));

                thumbDv.send(request);


//                response = thumbDv.send(request).get(2, TimeUnit.SECONDS);
//                LOG.info("Decode Response: " + response);
            }

//            for(Future<AmbeResponse> future : futures)
//            {
//                response = future.get(5, TimeUnit.SECONDS);
//                LOG.info("Decode Response: " + response);
//            }

            LOG.info("Sleeping ...");
            Thread.sleep(15000);
            LOG.info("End sleep.");

            thumbDv.close();

            //            for(byte[] frame : frameData)
//            {
//                thumbDv.send(new DecodeSpeechRequest(frame))
//            }

            //        try(ThumbDv thumbDv = new ThumbDv(AudioProtocol.NXDN, listener))
            //        {
            //            thumbDv.start();
            //
            //            Thread.sleep(6000);
            //
            //            for(int x = 0; x < 20; x++)
            //            {
            //                thumbDv.send(new EncodeSpeechRequest(new short[160]));
            //                Thread.sleep(20);
            //            }
            //            //            for(byte[] frame : frameData)
            //            //            {
            //            //                thumbDv.decode(frame);
            //            //            }
            //
            //            while(true);
            //        }
            //        catch(IOException ioe)
            //        {
            //            mLog.error("Error", ioe);
            //        }
            //        catch(InterruptedException e)
            //        {
            //            e.printStackTrace();
            //        }



        }
        catch(Exception e)
        {
            LOG.error("Error configuring audio protocol", e);
        }

        LOG.info("Finished!");
                //        String silence = "BEDDEA821EFD660C08";
//
    }
}