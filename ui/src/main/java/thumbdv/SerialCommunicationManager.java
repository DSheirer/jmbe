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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thumbdv.message.AmbeMessage;
import thumbdv.message.AmbeResponseMessageFactory;
import thumbdv.message.request.AmbeRequest;
import thumbdv.message.response.AmbeResponse;

/**
 * Manages communication with the AMBE-3000R USB device and provides asynchronous request/response handling.
 */
public class SerialCommunicationManager
{
    private final static Logger LOG = LoggerFactory.getLogger(SerialCommunicationManager.class);

    private static final int BAUD_RATE = 460800;
    private static final byte PACKET_START = (byte) 0x61;

    /**
     * Serial port send and receive buffers sized to hold 2x max-length request or response messages. The largest
     * messages are the encode/decode speech messages that are 327 (160 samples x 2 bytes + 7 overhead) bytes each.
     */
    private static final int MAX_MESSAGE_LENGTH = 327;
    private static final int SERIAL_PORT_BUFFER_SIZE = MAX_MESSAGE_LENGTH * 2;
    private final byte[] mReceiveBuffer = new byte[MAX_MESSAGE_LENGTH];

    /**
     * Asynchronous request queue is sized to hold 20 requests at a time.
     */
    private final BlockingQueue<AsyncRequest> mRequestQueue = new ArrayBlockingQueue<>(20);

    /**
     * Submit queue is fixed to 2x requests in play at a time.  The ICD indicates that the AMBE-3000 has input buffer
     * capacity to hold 2x requests.
     */
    private final BlockingQueue<AsyncRequest> mSubmitQueue = new ArrayBlockingQueue<>(2);
    private final SerialPort mSerialPort;
    private SendProcessor mSendProcessor;
    private ReceiveProcessor mReceiveProcessor;

    /**
     * Constructs an instance of SerialCommunicationManager using the specified serial port.
     *
     * @param serialPort to use
     */
    public SerialCommunicationManager(SerialPort serialPort)
    {
        mSerialPort = serialPort;
    }

    /**
     * Opens the serial port and resets the AMBE-3000R device to prepare for communication.
     *
     * @throws IOException if there is an error opening the serial port.
     */
    public void open() throws IOException
    {
        LOG.info("Using baud rate [" + BAUD_RATE + "] to open serial port [" + mSerialPort.getSystemPortName() + "/" + mSerialPort.getDescriptivePortName() + "]");
        mSerialPort.setBaudRate(BAUD_RATE);
        LOG.info("Opening serial port ... ");

        mSerialPort.openPort(0, SERIAL_PORT_BUFFER_SIZE, SERIAL_PORT_BUFFER_SIZE);

        if(mSerialPort.isOpen())
        {
            LOG.info("Serial port opened. Starting send and receive threads.");
            mReceiveProcessor = new ReceiveProcessor(mSerialPort.getInputStreamWithSuppressedTimeoutExceptions());
            mReceiveProcessor.start();

            mSendProcessor = new SendProcessor(mSerialPort.getOutputStream());
            mSendProcessor.start();
        }
        else
        {
            LOG.error("Serial port could not be opened");
        }

        LOG.info("Serial port open and AMBE-3000R device is ready for initial reset.");
    }

    /**
     * Indicates if the serial port is open.
     *
     * @return open state of the serial port.
     */
    public boolean isOpen()
    {
        return mSerialPort != null && mSerialPort.isOpen();
    }

    /**
     * Closes the serial port and stops the send/receive processor threads.
     */
    public void close()
    {
        if(mSendProcessor != null)
        {
            mSendProcessor.end();
            mSendProcessor = null;
        }

        if(mReceiveProcessor != null)
        {
            mReceiveProcessor.end();
            mReceiveProcessor = null;
        }

        if(mSerialPort.isOpen())
        {
            mSerialPort.closePort();
        }
    }

    /**
     * Submits the request to the queue and returns a future that will be completed with the response.
     *
     * @param request to send
     * @return asynchronous future that will be completed with the response
     */
    public Future<AmbeResponse> send(AmbeRequest request)
    {
        if(isOpen())
        {
            AsyncRequest asyncRequest = new AsyncRequest(request);
            mRequestQueue.add(asyncRequest);
            return asyncRequest.getFuture();
        }

        CompletableFuture<AmbeResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new IOException("Serial port is not open"));
        return future;
    }

    /**
     * Wrapper for a response and an asynchronous response.
     */
    public static class AsyncRequest
    {
        private final CompletableFuture<AmbeResponse> mFuture = new CompletableFuture<>();
        private final AmbeRequest mRequest;

        /**
         * Constructs an instance
         *
         * @param request to be dispatched
         */
        public AsyncRequest(AmbeRequest request)
        {
            mRequest = request;
        }

        /**
         * Completable future that will be completed with the response, canceled, or signal an error condition.
         */
        public CompletableFuture<AmbeResponse> getFuture()
        {
            return mFuture;
        }

        /**
         * The request
         *
         * @return request
         */
        public AmbeRequest getRequest()
        {
            return mRequest;
        }

        /**
         * Completes the request with the response.
         *
         * @param response that was received for the request.
         */
        public void complete(AmbeResponse response)
        {
            mFuture.complete(response);
        }

        /**
         * Signals that the request is canceled and no response will be received.
         */
        public void cancel()
        {
            mFuture.cancel(true);
        }
    }

    /**
     * Threaded processor for sending requests to the serial port and managing the total number of requests in play at
     * any given time..
     */
    public class SendProcessor extends Thread
    {
        private final OutputStream mOutputStream;
        private boolean mRunning = true;

        /**
         * Constructs an instance.
         *
         * @param outputStream from the serial port.
         */
        public SendProcessor(OutputStream outputStream)
        {
            mOutputStream = outputStream;
        }

        @Override
        public void run()
        {
            while(mRunning)
            {
                try
                {
                    //Blocking call will wait until a request is available.
                    AsyncRequest request = mRequestQueue.take();

                    //Blocking call will wait until the submit queue has space.  This places a limit of 2x requests active
                    // at a time.  The ICD indicates that the AMBE-3000 has input buffer capacity for two requests.
                    mSubmitQueue.put(request);

                    try
                    {
                        mOutputStream.write(request.getRequest().getData());
                    }
                    catch(IOException ioe)
                    {
                        //If the request fails, remove it from the queue and signal the future with the exception.
                        mSubmitQueue.remove(request);
                        request.getFuture().completeExceptionally(ioe);
                    }
                }
                catch(InterruptedException ie)
                {
                    //Ignore and allow the thread to die when end() is called.
                }
            }
        }

        /**
         * Stops the send processor.
         */
        public void end()
        {
            mRunning = false;

            SendProcessor.this.interrupt();

            if(mOutputStream != null)
            {
                try
                {
                    mOutputStream.close();
                }
                catch(IOException ioe)
                {
                    LOG.error("Error closing serial port output stream", ioe);
                }
            }
        }
    }

    /**
     * Threaded processor for processing received data from the serial port.
     */
    public class ReceiveProcessor extends Thread
    {
        private final InputStream mInputStream;
        private boolean mRunning = true;

        /**
         * Constructs an instance.
         *
         * @param inputStream to read from the serial port.
         */
        public ReceiveProcessor(InputStream inputStream)
        {
            mInputStream = inputStream;
        }

        @Override
        public void run()
        {
            while(mRunning)
            {
                try
                {
                    //Blocking call to read the 4-byte message header
                    int read = mInputStream.read(mReceiveBuffer, 0, 4);

                    if(read == 4 && mReceiveBuffer[0] == PACKET_START)
                    {
                        int length = (0xFF & mReceiveBuffer[1]) << 8;
                        length += (0xFF & mReceiveBuffer[2]);
                        read = mInputStream.read(mReceiveBuffer, 4, length);

                        if(read == length)
                        {
                            AmbeResponse response = AmbeResponseMessageFactory
                                    .getMessage(Arrays.copyOf(mReceiveBuffer, length + 4));

                            AsyncRequest request = mSubmitQueue.poll();

                            if(request != null)
                            {
                                request.complete(response);
                            }
                            else
                            {
                                LOG.error("Received response for unknown request: " + response);
                            }
                        }
                    }
                    else if(read > 0)
                    {
                        long skipped = mInputStream.skip(mInputStream.available());
                        LOG.error("Unrecognized packet fragment [" + AmbeMessage.toHex(Arrays.copyOf(mReceiveBuffer, read)) +
                                "] skipped [" + skipped + "] bytes remaining in the input stream to clear the buffer.");
                    }
                }
                catch(IOException ioe)
                {
                    LOG.error("Error reading from serial port", ioe);
                }
            }
        }

        /**
         * Stops the send processor.
         */
        public void end()
        {
            mRunning = false;
            ReceiveProcessor.this.interrupt();

            try
            {
                mInputStream.close();
            }
            catch(IOException ioe)
            {
                LOG.error("Error closing serial port input stream", ioe);
            }
        }
    }
}
