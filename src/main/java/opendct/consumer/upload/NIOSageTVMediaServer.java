/*
 * Copyright 2015 The OpenDCT Authors. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.consumer.upload;

import opendct.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class NIOSageTVMediaServer {
    private final Logger logger = LogManager.getLogger(NIOSageTVMediaServer.class);

    private final Object uploadLock = new Object();
    private SocketChannel socketChannel = null;
    private String uploadFilename = null;
    private int uploadID = -1;
    private SocketAddress currentServerSocket = null;
    private AtomicBoolean uploadInProgress = new AtomicBoolean(false);
    private long autoOffset = 0;

    private boolean remuxEnabled = false;

    private ByteBuffer messageInBuffer = ByteBuffer.allocateDirect(4096);
    private StringBuilder messageInBuilder = new StringBuilder();
    private int messageInBytes = 0;
    private long messageInTimeout = 0;

    private ByteBuffer messageOutBuffer = ByteBuffer.allocateDirect(4096);

    /**
     * Performs all of the steps needed to start uploading to the SageTV server.
     *
     * @param uploadID        This is the ID that SageTV has already provided you.
     * @param newServerSocket This is the socket to be used for communication with the SageTV server.
     * @return Returns <i>true</i> if the SageTV server accepted the connection.
     * @throws IOException If there was a problem establishing a connection to the SageTV server.
     */
    public boolean startUpload(
            SocketAddress newServerSocket, String uploadFilename, int uploadID) throws IOException {
        return startUpload(newServerSocket, uploadFilename, uploadID, 0);
    }

    /**
     * Performs all of the steps needed to start uploading to the SageTV server.
     *
     * @param uploadID        This is the ID that SageTV has already provided you.
     * @param newServerSocket This is the socket to be used for communication with the SageTV server.
     * @param offset          This is the offset to start on. This is usually 0 unless a stream is
     *                        resuming from a disconnection.
     * @return Returns <i>true</i> if the SageTV server accepted the connection.
     * @throws IOException If there was a problem establishing a connection to the SageTV server.
     */
    public boolean startUpload(
            SocketAddress newServerSocket, String uploadFilename, int uploadID, long offset) throws IOException {

        logger.entry(newServerSocket, uploadFilename, uploadID, offset);
        String response;

        synchronized (uploadLock) {
            this.uploadFilename = null;

            logger.info("Connecting to SageTV server on socket {}...",
                    newServerSocket.toString());

            socketChannel = SocketChannel.open(newServerSocket);
            currentServerSocket = newServerSocket;

            this.uploadFilename = uploadFilename;
            this.uploadID = uploadID;
            this.autoOffset = offset;

            sendMessage("WRITEOPEN " + uploadFilename + " " + uploadID);

            // The expected responses are OK or NON_MEDIA.
            try {
                response = waitForMessage(true);

                if (response.equals("NON_MEDIA")) {
                    logger.error("SageTV replied NON_MEDIA!");
                }
            } catch (Exception e) {
                response = e.getMessage();
            }
        }

        return logger.exit(response != null && response.equals("OK"));
    }

    public boolean setupRemux(String containerFormat, boolean isTV) throws IOException {
        boolean returnValue;

        synchronized (uploadLock) {
            sendMessage("REMUX_SETUP AUTO " + containerFormat + " " + (isTV ? "TRUE" : "FALSE"));

            String response;

            try {
                response = waitForMessage(true);
            } catch (Exception e) {
                response = e.getMessage();
            }

            returnValue = remuxEnabled = response != null && response.equals("OK");
        }

        return logger.exit(returnValue);
    }

    public boolean isRemuxInitialized() throws IOException {
        boolean returnValue;

        synchronized (uploadLock) {
            sendMessage("REMUX_CONFIG INIT");

            String response;

            try {
                response = waitForMessage(true);
            } catch (Exception e) {
                response = e.getMessage();
            }

            returnValue = response != null && response.equals("TRUE");
        }

        return logger.exit(returnValue);
    }

    public boolean setRemuxBuffer(long bufferSize) throws IOException {
        boolean returnValue;

        synchronized (uploadLock) {
            sendMessage("REMUX_CONFIG BUFFER " + bufferSize);

            String response;

            try {
                response = waitForMessage(true);
            } catch (Exception e) {
                response = e.getMessage();
            }

            returnValue = response != null && response.equals("TRUE");
        }

        return logger.exit(returnValue);
    }

    public long getSize() throws IOException {
        long returnValue = 0;

        synchronized (uploadLock) {
            sendMessage("SIZE");

            String response = null;

            try {
                response = waitForMessage(false);
                response = response.substring(0, response.lastIndexOf(" "));
                returnValue = Long.parseLong(response);
            } catch (Exception e) {
                logger.error("Unable to get/parse size '{}' => ", response, e);
            }
        }

        return logger.exit(returnValue);
    }

    public String getFormatString() throws IOException {
        String returnValue;

        synchronized (uploadLock) {
            sendMessage("REMUX_CONFIG FORMAT");

            try {
                returnValue = waitForMessage(true);
            } catch (Exception e) {
                returnValue = e.getMessage();
            }
        }

        return logger.exit(returnValue);
    }

    /**
     * Reset the MediaServer communications to a known state and clear all relevant variables.
     * <p/>
     * This should be called if you are starting a new thread, but not creating a new object. Call
     * this method before <b>startUpload</b> if you cannot be certain of state of the socket
     * connection. It will attempt to keep the connection open and just close the open file. If it
     * is unable to keep the connection open, it will make sure the connection will be
     * re-established when you call <b>startUpload</b>.
     */
    public void reset() {
        if (currentServerSocket != null) {
            try {
                sendMessage("CLOSE");
            } catch (IOException e) {
                logger.debug("Unable to gracefully close connection => ", e);
            } finally {
                currentServerSocket = null;
            }
        }

        remuxEnabled = false;
        socketChannel = null;
        uploadFilename = null;
        uploadID = -1;
    }

    public boolean isConnected() {
        logger.entry();

        if (socketChannel != null) {
            return logger.exit(socketChannel.isConnected());
        }

        return logger.exit(false);
    }

    public boolean switchRemux(String uploadFilename, int uploadID) throws IOException {
        logger.entry(uploadFilename, uploadID);
        boolean returnValue = false;

        synchronized (uploadLock) {
            autoOffset = 0;
            sendMessage("REMUX_SWITCH " + uploadFilename + " " + uploadID);

            String response;

            try {
                response = waitForMessage(true);
            } catch (Exception e) {
                response = e.getMessage();
            }

            returnValue = response != null && response.equals("OK");
        }

        return logger.exit(returnValue);
    }

    public boolean isSwitched() throws IOException {
        boolean returnValue;

        synchronized (uploadLock) {
            sendMessage("REMUX_CONFIG SWITCHED");

            String response;

            try {
                response = waitForMessage(true);
            } catch (Exception e) {
                response = e.getMessage();
            }

            returnValue = response != null && response.equals("TRUE");
        }

        return logger.exit(returnValue);
    }

    /**
     * Uploads all of the contents of the provided buffers to an automatically incrementing offset.
     *
     * @param byteBuffer This is the data that will be written in it's entirety.
     * @throws IOException If there was a problem writing the bytes to the to the SageTV server socket.
     */
    public void uploadAutoIncrement(ByteBuffer byteBuffer) throws IOException {
        upload(autoOffset, byteBuffer);
    }

    /**
     * Uploads all of the contents of the provided buffers to an automatic wrap around limit.
     *
     * @param limit      This is the number of bytes at which the index is returned to zero.
     * @param byteBuffer This is the data that will be written in it's entirety.
     * @throws IOException If there was a problem writing the bytes to the to the SageTV server socket.
     */
    public void uploadAutoBuffered(long limit, ByteBuffer byteBuffer) throws IOException {
        if (byteBuffer.remaining() > limit - autoOffset) {
            ByteBuffer slice = byteBuffer.slice();
            slice.limit((int) (limit - autoOffset));
            upload(autoOffset, slice);

            byteBuffer.position((int) (byteBuffer.position() + (limit - autoOffset)));
            autoOffset = 0;
            upload(autoOffset, byteBuffer);
        } else {
            upload(autoOffset, byteBuffer);
        }
    }

    /**
     * Uploads all of the contents of the provided buffers to the specified offset.
     *
     * @param offset     Specify the offset to upload the data to the remote file.
     * @param byteBuffer This is the data that will be written in it's entirety.
     * @return Returns <i>true</i> if SageTV accepted the transfer.
     * @throws IOException If there was a problem writing the bytes to the to the SageTV server
     *                     socket.
     */
    public boolean upload(long offset, ByteBuffer byteBuffer) throws IOException {
        synchronized (uploadLock) {

            // This way you can alternate between overloads if somehow that's useful.
            autoOffset = offset + byteBuffer.remaining();

            boolean returnValue = false;

            try {
                sendMessage("WRITE " + offset + " " + byteBuffer.remaining());
                while (byteBuffer.hasRemaining() && !Thread.currentThread().isInterrupted()) {
                    int sentBytes = socketChannel.write(byteBuffer);
                    logger.trace("Transferred {} stream bytes to SageTV server. {} bytes remaining.", sentBytes, byteBuffer.remaining());
                }

                returnValue = true;
            } catch (IOException e) {
                logger.warn("The SageTV server communication has stopped => ", e);
                returnValue = false;
            }

            return logger.exit(returnValue);
        }
    }

    /**
     * Ends an uploading session with SageTV.
     * <p/>
     * SageTV will close the previous upload session when you start a new upload, but for proper usage this should be
     * used when you are done uploading.
     *
     * @throws IOException If there was a problem writing the bytes to the to the SageTV server socket.
     */
    public void endUpload() throws IOException {
        if (socketChannel != null) {
            String response = null;

            Thread timeout = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e) {
                        return;
                    }

                    try {
                        socketChannel.close();
                    } catch (Exception e) {
                        logger.debug("Exception while closing socket channel => ", e);
                    }
                }
            });

            timeout.start();
            synchronized (uploadLock) {
                sendMessage("CLOSE");

                // The expected responses are OK or NON_MEDIA.
                response = waitForMessage(true);
                timeout.interrupt();
            }

            if (socketChannel.isOpen()) {
                sendMessage("QUIT");
            }

            if (socketChannel.isOpen()) {
                socketChannel.close();
            }
        }

        socketChannel = null;
        uploadFilename = null;
        uploadID = -1;
        currentServerSocket = null;
    }

    private void sendMessage(String message) throws IOException {
        logger.entry(message);

        if (socketChannel != null && socketChannel.isConnected()) {
            if (message.startsWith("WRITE ") || message.equals("SIZE")) {
                logger.trace("Sending '{}' to SageTV server...", message);
            } else {
                logger.info("Sending '{}' to SageTV server...", message);
            }

            messageOutBuffer.clear();
            messageOutBuffer.put((message + "\r\n").getBytes(Config.STD_BYTE));
            messageOutBuffer.flip();

            while (messageOutBuffer.hasRemaining()) {
                int sendBytes = socketChannel.write(messageOutBuffer);
                logger.trace("Sent {} bytes to the SageTV server.", sendBytes);
            }
        } else {
            logger.warn("Unable to send '{}' because the socket has not been initialized.", message);
            throw new IOException("The socket is not available.");
        }

        logger.exit();
    }

    private String waitForMessage(boolean stopLogging) throws IOException {
        logger.entry();

        // Only one response should ever come when listening.
        messageInBuilder.setLength(0);
        messageInBytes = 0;

        if (socketChannel != null && socketChannel.isConnected()) {
            while (!Thread.currentThread().isInterrupted()) {
                if (!stopLogging) {
                    logger.debug("messageInBytes = {}", messageInBytes);
                }
                if (messageInBytes > 0) {
                    while (messageInBuffer.hasRemaining()) {
                        byte readChar = messageInBuffer.get();

                        if (readChar == '\r') {
                            if (messageInBuffer.hasRemaining()) {
                                readChar = messageInBuffer.get();
                                if (readChar != '\n') {
                                    logger.debug("Expected LF, but found '{}'. Returning message anyway.", readChar);

                                    messageInBuffer.position(messageInBuffer.position() - 1);
                                    messageInBytes -= (messageInBuilder.length() + 1);
                                } else {
                                    messageInBytes -= (messageInBuilder.length() + 2);
                                }
                            } else {
                                messageInBytes -= (messageInBuilder.length() + 1);
                            }

                            if (messageInBytes > 0) {
                                messageInBuilder.setLength(0);
                                continue;
                            }

                            String returnString = messageInBuilder.toString();
                            if (!stopLogging) {
                                logger.info("Received message from SageTV server '{}'", returnString);
                            }

                            return logger.exit(returnString);
                        } else if (readChar != '\n'){
                            messageInBuilder.append((char)readChar);
                        }
                    }
                } else {
                    messageInBuffer.clear();
                    messageInBytes = socketChannel.read(messageInBuffer);
                    messageInBuffer.flip();
                    if (!stopLogging) {
                        logger.debug("Received {} bytes from SageTV server.", messageInBytes);
                    }
                }
            }
        }

        logger.warn("Unable to receive because the socket has not been initialized.");
        throw new IOException("The socket is not available.");
    }
}
