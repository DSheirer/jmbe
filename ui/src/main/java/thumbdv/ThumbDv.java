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
import jmbe.codec.ambe.AMBEFrame;
import jmbe.codec.ambe.AMBEModelParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thumbdv.message.AmbeMessage;
import thumbdv.message.VocoderRate;
import thumbdv.message.request.AmbeRequest;
import thumbdv.message.request.DecodeSpeechRequest;
import thumbdv.message.request.EncodeSpeechRequest;
import thumbdv.message.request.GetConfigRequest;
import thumbdv.message.request.ProductIdRequest;
import thumbdv.message.request.ResetRequest;
import thumbdv.message.request.SetVocoderParametersRequest;
import thumbdv.message.request.SetVocoderRequest;
import thumbdv.message.request.VersionRequest;
import thumbdv.message.response.AmbeResponse;
import thumbdv.message.response.EncodeSpeechResponse;
import thumbdv.message.response.SetVocoderParameterResponse;
import thumbdv.message.response.SetVocoderResponse;

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
                LOG.info("\tAvailable Serial Port: " + port.getSystemPortName() + " - " + port.getDescriptivePortName());

                if(port.getDescriptivePortName().contentEquals(PORT_DESCRIPTION) ||
                        port.getSystemPortName().contains(PORT_DESCRIPTION_FRAGMENT))
                {
                    thumbDVPort = port;
                }
            }

            if(thumbDVPort != null)
            {
                mCommunicationManager = new SerialCommunicationManager(thumbDVPort);

                try
                {
                    mCommunicationManager.open();
                    AmbeResponse response = send(new ResetRequest()).get(2, TimeUnit.SECONDS);
                    LOG.info("Reset Response: " + response);

                    response = send(new ProductIdRequest()).get(2, TimeUnit.SECONDS);
                    LOG.info("Product ID: " + response);

                    response = send(new VersionRequest()).get(2, TimeUnit.SECONDS);
                    LOG.info("Version: " + response);

                    LOG.info("ThumbDV Device ready.");
                }
                catch(Exception e)
                {
                    LOG.error("Error opening serial port and resetting device", e);
                }
            }
        }
    }

    /**
     * Configures the ThumbDv to support encoding and decoding audio frames for the audio protocol.
     *
     * @param audioProtocol to configure
     */
    public void configure(AudioProtocol audioProtocol) throws Exception
    {
        if(mCommunicationManager == null)
        {
            throw new IllegalStateException("ThumbDv not opened and available");
        }

        AmbeResponse response = send(new SetVocoderRequest(VocoderRate.RATE_33)).get(2, TimeUnit.SECONDS);

        if(response instanceof SetVocoderResponse svr && svr.isSuccessful())
        {
            LOG.info("Set vocoder complete for RATE_33");

            SetVocoderParametersRequest request = switch(audioProtocol)
            {
                case DMR, NXDN, P25_PHASE2 ->
                        new SetVocoderParametersRequest(0x0431, 0x0754, 0x2400, 0x0000, 0x0000, 0x6F48);
                case DSTAR -> new SetVocoderParametersRequest(0x0130, 0x0763, 0x4000, 0x0000, 0x0000, 0x0048);
            };

            response = send(request).get(2, TimeUnit.SECONDS);

            if(response instanceof SetVocoderParameterResponse svpr2 && svpr2.isSuccessful())
            {
                mAudioProtocol = audioProtocol;
                LOG.info("Audio Protocol configured: " + audioProtocol);
            }
            else
            {
                LOG.error("Error configuring vocoder parameters for audio protocol: " + audioProtocol + " " + response);
            }
        }
        else
        {
            LOG.error("Error configuring vocoder for audio protocol: " + audioProtocol + " " + response);
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
            thumbDv.configure(AudioProtocol.DMR);
            AmbeResponse response = thumbDv.send(new GetConfigRequest()).get(2, TimeUnit.SECONDS);
            LOG.info("Config Response: " + response);

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

            List<byte[]> ambeFrames = new ArrayList<>();

            for(int x = 0; x < 25; x++)
            {
                EncodeSpeechRequest request = new EncodeSpeechRequest(new short[160]);
//                LOG.info("\tRequest: " + AmbeMessage.toHex(request.getData()));
                response = thumbDv.send(request).get(1, TimeUnit.SECONDS);

                if(response instanceof EncodeSpeechResponse esr)
                {
                    ambeFrames.add(esr.getEncodedSpeech());
                }

                LOG.info("Encode Response: " + response);
            }

            AMBEModelParameters previous = new AMBEModelParameters();
            AMBEModelParameters current;

            for(byte[] ambeFrame: ambeFrames)
            {
//                AMBEFrame frame = new AMBEFrame(ambeFrame);
//                System.out.println(frame);
//                current = frame.getVoiceParameters(previous);
//                System.out.println(current);
//                previous = current;

                DecodeSpeechRequest request = new DecodeSpeechRequest(ambeFrame);
                LOG.info("Request: " + AmbeMessage.toHex(request.getData()));
                response = thumbDv.send(new DecodeSpeechRequest(ambeFrame)).get(1, TimeUnit.SECONDS);
                LOG.info("Decode Response: " + response);
            }


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