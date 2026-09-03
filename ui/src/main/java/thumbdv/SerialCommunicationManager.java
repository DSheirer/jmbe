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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thumbdv.message.AmbeMessage;
import thumbdv.message.AmbeResponseMessageFactory;
import thumbdv.message.request.AmbeRequest;
import thumbdv.message.request.FlushRequest;
import thumbdv.message.response.AmbeResponse;
import thumbdv.util.Utils;

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
    private static final int MAX_MESSAGE_LENGTH = 350;
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
    private final List<AsyncRequest> mPendingQueue = new ArrayList<>();
    private final SerialPort mSerialPort;
    private Processor mProcessor;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private int mPendingEncode = 0;
    private int mPendingDecode = 0;
    private long mLastSendTimestamp = 0;

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
            mInputStream = mSerialPort.getInputStreamWithSuppressedTimeoutExceptions();
            mOutputStream = mSerialPort.getOutputStream();
            mProcessor = new Processor();
            mProcessor.start();
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
        if(mProcessor != null)
        {
            mProcessor.end();
            mProcessor = null;
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
     * Reads a response from the serial port and completes the oldest async request in the pending queue.
     */
    private void processResponse() throws IOException
    {
        int read = 0, total = 0;

        while(read >= 0 && total < 4)
        {
            read = mInputStream.read(mReceiveBuffer, total, 4 - total);
            total += read;
        }

        LOG.info("...Processing response: " + AmbeMessage.toHex(Arrays.copyOf(mReceiveBuffer, total)));

        if(total == 4 && mReceiveBuffer[0] == PACKET_START)
        {
            int length = (0xFF & mReceiveBuffer[1]) << 8;
            length += (0xFF & mReceiveBuffer[2]);

            read = 0;
            total = 0;

            while(read >= 0 && total < length)
            {
                read = mInputStream.read(mReceiveBuffer, 4 + read, length - read);
                total += read;
            }

            if(total == length)
            {
                AmbeResponse response = AmbeResponseMessageFactory.getMessage(Arrays.copyOf(mReceiveBuffer, length + 4));

                if(!mPendingQueue.isEmpty())
                {
                    AsyncRequest pending = mPendingQueue.remove(0);

                    if(pending.getRequest().isAudioDecode())
                    {
                        mPendingDecode--;
                    }
                    else if(pending.getRequest().isAudioEncode())
                    {
                        mPendingEncode--;
                    }

                    pending.complete(response);
                }
                else
                {
                    LOG.info("Received response with no pending async requests: " + response);
                }
            }
            else
            {
                LOG.error("Unexpected packet read byte count: " + read + " - expected: " + length);
            }
        }
        else if(read > 0)
        {
            long skipped = mInputStream.skip(mInputStream.available());
            LOG.error("Unrecognized packet fragment [" + AmbeMessage.toHex(Arrays.copyOf(mReceiveBuffer, read)) + "] skipped [" + skipped + "] bytes remaining in the input stream to clear the buffer.");
        }
    }

    private void write(AmbeRequest request) throws IOException
    {
        mOutputStream.write(request.getData());
    }

    /**
     * Threaded processor for sending requests to the serial port and managing the total number of requests in play at
     * any given time..
     */
    public class Processor extends Thread
    {
        private boolean mRunning = true;

        /**
         * Constructs an instance.
         */
        public Processor()
        {
        }

        @Override
        public void run()
        {
            while(mRunning)
            {
                try
                {
                    //Blocking call waits until a request is available.
                    AsyncRequest request = mRequestQueue.take();

                    long elapsed = System.currentTimeMillis() - mLastSendTimestamp;

                    if(elapsed < 20)
                    {
                        try
                        {
                            LOG.info("Sleeping: " + (20 - elapsed));
                            Thread.sleep(20 - elapsed);
                        }
                        catch (InterruptedException e)
                        {
                            LOG.error("Send delay loop interrupted");
                        }
                    }

                    if(request.getRequest() instanceof FlushRequest)
                    {
                        shutdown();
                    }
                    else
                    {
                        try
                        {
                            mLastSendTimestamp = System.currentTimeMillis();
                            LOG.info("... Sending: " + AmbeMessage.toHex(request.getRequest().getData()) + " AS:" + request.getRequest());
                            write(request.getRequest());
                            mPendingQueue.add(request);

                            if(request.getRequest().isAudioEncode())
                            {
                                mPendingEncode++;

                                if(mPendingEncode >= 2)
                                {
                                    LOG.info("Pending Encode: " + mPendingEncode + " - requesting response");
                                    processResponse();
                                }
                            }
                            else if(request.getRequest().isAudioDecode())
                            {
                                mPendingDecode++;

                                if(mPendingDecode >= 2)
                                {
                                    LOG.info("Pending Decode: " + mPendingDecode + " - requesting response");
                                    processResponse();
                                }
                            }
                            else //Control requests
                            {
                                processResponse();
                            }
                        }
                        catch(IOException ioe)
                        {
                            LOG.error("Error while sending", ioe);
                            //If the request fails, remove it from the queue and signal the future with the exception.
                            mPendingQueue.remove(request);
                            request.getFuture().completeExceptionally(ioe);
                        }
                    }
                }
                catch(InterruptedException ie)
                {
                    LOG.info("Send processor interrupted");
                    //Ignore and allow the thread to die when end() is called.
                }
            }

            LOG.info("Send thread now stopped");
        }

        private void shutdown()
        {
            LOG.info("Shutting down ...");
            mRunning = false;

            int read = 1;

            while(read > 0)
            {
                try
                {
                    read = mInputStream.read(mReceiveBuffer);

                    if(read > 0)
                    {
                        LOG.info("Shutdown Flushing Read: " + AmbeRequest.toHex(Arrays.copyOf(mReceiveBuffer, read)));
                    }
                    else
                    {
                        LOG.info("Shutdown - no remaining bytes to read: " + read);
                    }
                }
                catch(IOException ioe)
                {
                    LOG.error("Error while sending", ioe);
                }
            }
        }

        /**
         * Stops the send processor.
         */
        public void end()
        {
            mRunning = false;

            Processor.this.interrupt();

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
}
