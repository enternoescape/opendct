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

package opendct.tuning.hdhomerun;

import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.hdhomerun.types.HDHomeRunPacketTag;
import opendct.tuning.hdhomerun.types.HDHomeRunPacketType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class HDHomeRunControl {
    private static final Logger logger = LogManager.getLogger(HDHomeRunControl.class);

    public final static int HDHOMERUN_CONTROL_CONNECT_TIMEOUT = 2500;
    public final static int HDHOMERUN_CONTROL_SEND_TIMEOUT = 2500;
    public final static int HDHOMERUN_CONTROL_RECV_TIMEOUT = 5000;
    public final static int HDHOMERUN_CONTROL_UPGRADE_TIMEOUT = 30000;

    private HDHomeRunPacket txPacket;
    private HDHomeRunPacket rxPacket;
    private SocketChannel socket;
    private volatile int totalBytes = 0;

    /**
     * Create a new HDHomeRun controller.
     * <p/>
     * Controllers are thread-safe and can be shared. It is recommended to only share between tuners
     * on the same device so we don't need to keep changing IP addresses.
     */
    public HDHomeRunControl() {
        txPacket = new HDHomeRunPacket();
        rxPacket = new HDHomeRunPacket();
    }

    /**
     * Get a variable from a device.
     *
     * @param address The address of the device to be controlled.
     * @param key     The key to set.
     * @return A value from the device. If the reply was invalid, this will be <i>null</i>.
     * @throws IOException     Thrown if communication with the device is not possible.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public synchronized String getVariable(InetAddress address, String key) throws GetSetException, IOException {
        return setVariable(address, key, null, 0);
    }


    /**
     * Set a variable on a device.
     *
     * @param address The address of the device to be controlled.
     * @param key     The key to set.
     * @param value   The value to set for the key.
     * @return A value from the device. If the reply was invalid, this will be <i>null</i>.
     * @throws IOException     Thrown if communication with the device is not possible.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public synchronized String setVariable(InetAddress address, String key, String value) throws GetSetException, IOException {
        return setVariable(address, key, value, 0);
    }

    /**
     * Set a variable on a device that has a lock key.
     *
     * @param address The address of the device to be controlled.
     * @param key     The key to set.
     * @param value   The value to set for the key.
     * @param lockkey The lockkey needed to set the key.
     * @return A value from the device. If the reply was invalid, this will be <i>null</i>.
     * @throws IOException     Thrown if communication with the device was incomplete or is not possible
     *                         at this time.
     * @throws GetSetException Thrown if the device returns an error instead of a value.
     */
    public synchronized String setVariable(InetAddress address, String key, String value, int lockkey) throws GetSetException, IOException {
        logger.entry(address, key, value, lockkey);

        txPacket.startPacket(HDHomeRunPacketType.HDHOMERUN_TYPE_GETSET_REQ);
        txPacket.putTagLengthValue(HDHomeRunPacketTag.HDHOMERUN_TAG_GETSET_NAME, key);

        if (value != null) {
            txPacket.putTagLengthValue(HDHomeRunPacketTag.HDHOMERUN_TAG_GETSET_VALUE, value);
        }

        if (lockkey != 0) {
            txPacket.putTagLengthValue(HDHomeRunPacketTag.HDHOMERUN_TAG_GETSET_LOCKKEY, lockkey);
        }

        txPacket.endPacket();

        connectSocket(new InetSocketAddress(address,
                HDHomeRunPacket.HDHOMERUN_CONTROL_TCP_PORT));

        if (logger.isDebugEnabled()) {
            logger.debug("key: '{}' value: '{}' lockKey: '{}' sendLength: {} address: {}",
                    key, value, lockkey, txPacket.BUFFER.remaining(),
                    address != null ? address.getHostAddress() : null);
        }

        IOException errorMessage = null;
        int retryCount = 0;
        int retryLimit = HDHomeRunDiscoverer.getControlRetryCount();
        boolean success = false;

        while (!success && retryCount++ <= retryLimit && !Thread.currentThread().isInterrupted()) {
            try {
                packetSendReceive(txPacket.BUFFER.slice(), HDHOMERUN_CONTROL_RECV_TIMEOUT);
                success = true;
            } catch (IOException e) {
                // Signal to the discoverer that we might need a new IP address.
                HDHomeRunDiscoverer.needBroadcast();

                errorMessage = e;
                closeSocket();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e0) {
                    // This is intentional. We should have the reason we are waiting returned as the
                    // exception since it is itself an exception.
                    throw e;
                }
                connectSocket(new InetSocketAddress(address,
                        HDHomeRunPacket.HDHOMERUN_CONTROL_TCP_PORT));
            }
        }

        if (!success) {
            // Signal to the discoverer that we might need a new IP address.
            HDHomeRunDiscoverer.needBroadcast();

            if (errorMessage != null) {
                logger.error("Unable to communicate with HDHomeRun after {} attempts.", retryLimit);
                throw errorMessage;
            } else {
                if (Thread.currentThread().isInterrupted()) {
                    logger.error("Data was not able to be sent because the thread has bee interrupted.");
                    throw new IOException("Data was not able to be sent because the thread has bee interrupted.");
                } else {
                    // We really should not be seeing this, but it's here just in case it happens.
                    logger.error("No error was reported, but data was not able to be sent.");
                    throw new IOException("No error was reported, but data was not able to be sent.");
                }
            }
        }

        int bufferLimit = rxPacket.BUFFER.limit();

        if (bufferLimit > 4) {
            if (rxPacket.getPacketType() == HDHomeRunPacketType.HDHOMERUN_TYPE_GETSET_RPY) {
                int packetLength = rxPacket.getPacketLength();
                logger.trace("bufferLimit: {}, packetLength: {}", bufferLimit, packetLength);
                rxPacket.BUFFER.limit(packetLength + 4);

                while (rxPacket.BUFFER.remaining() >= 2) {
                    HDHomeRunPacketTag tag = rxPacket.getTag();
                    int length = rxPacket.getVariableLength();

                    if (rxPacket.BUFFER.position() + length > rxPacket.BUFFER.limit()) {
                        if (logger.isDebugEnabled()) {
                            String returnValue = rxPacket.getTLVString(rxPacket.BUFFER.limit() - rxPacket.BUFFER.position());
                            logger.debug("HDHomeRun device returned a length ({}) larger than the data returned. UTF-8: '{}'", length, returnValue);
                        }

                        return logger.exit(null);
                    }

                    if (tag == null) {
                        // Silicondust says to just ignore these.
                        logger.debug("HDHomeRun device returned an unknown tag with the length {}", length);
                        rxPacket.BUFFER.position(rxPacket.BUFFER.position() + length);
                        continue;
                    }

                    switch (tag) {
                        case HDHOMERUN_TAG_GETSET_NAME:
                            // The device may return the key name.
                            rxPacket.BUFFER.position(rxPacket.BUFFER.position() + length);
                            logger.trace("HDHomeRun device returned the key name. Skipped {} bytes.", length);
                            break;

                        case HDHOMERUN_TAG_GETSET_VALUE:
                            return logger.exit(rxPacket.getTLVString(length));

                        case HDHOMERUN_TAG_ERROR_MESSAGE:
                            String returnError = rxPacket.getTLVString(length);
                            throw new GetSetException(returnError);

                        default:
                            // Silicondust says to just ignore these.
                            logger.debug("HDHomeRun device returned an unexpected tag {} with the length {}", tag, length);

                            rxPacket.BUFFER.position(rxPacket.BUFFER.position() + length);
                            break;
                    }
                }
            } else {
                logger.warn("Packet returned was not HDHOMERUN_TYPE_GETSET_RPY.");
            }
        } else {
            logger.warn("Message sent, HDHomeRun did not reply.");
            closeSocket();
        }

        if (logger.isDebugEnabled()) {
            if (rxPacket.BUFFER.remaining() > 0) {
                String returnValue = rxPacket.getTLVString(rxPacket.BUFFER.limit() - rxPacket.BUFFER.position());
                logger.debug("HDHomeRun device returned UTF-8: '{}'", returnValue);
            }
        }

        logger.error("HDHomeRun device did not reply with a valid message for key = '{}', value ='{}' and lockkey='{}'.", key, value, lockkey);
        return logger.exit(null);
    }

    private void connectSocket(SocketAddress address) throws IOException {
        logger.entry(address);

        try {
            if (socket != null && socket.isConnected() && socket.getRemoteAddress().equals(address)) {
                return;
            }

            closeSocket();
        } catch (Exception e) {
            logger.debug("connectSocket created an unexpected exception => ", e);
            closeSocket();
        }

        socket = SocketChannel.open(address);

        logger.exit();
    }

    public synchronized void closeSocket() {
        logger.entry();

        if (socket != null) {
            try {
                // Allow the socket to be re-opened immediately.
                socket.socket().setReuseAddress(true);
                socket.close();
                socket.socket().close();
                socket = null;
            } catch (Exception e) {
                logger.debug("closeSocket created an unexpected exception => ", e);
            }
        }

        logger.exit();
    }

    private void packetSendReceive(ByteBuffer packetSend, long timeout) throws IOException {
        logger.entry(packetSend, timeout);

        rxPacket.BUFFER.clear();
        totalBytes = 0;
        Thread receiveThread = new Thread(new ReceiveThread());
        receiveThread.setName("HDHomeRunControlReceive-" + receiveThread.getId());

        try {
            while (packetSend.hasRemaining()) {
                socket.write(packetSend);
            }
        } catch (IOException e) {
            // Signal to the discoverer that we might need a new IP address.
            HDHomeRunDiscoverer.needBroadcast();

            closeSocket();
            throw e;
        }

        receiveThread.start();

        try {
            receiveThread.join(timeout);

            //Stop receiving.
            receiveThread.interrupt();

            // Wait for the thread to actually stop.
            receiveThread.join();
        } catch (InterruptedException e) {
            receiveThread.interrupt();
            closeSocket();
            throw new IOException(e.getMessage());
        }

        rxPacket.BUFFER.flip();

        if (rxPacket.BUFFER.remaining() == 0) {
            // Signal to the discoverer that we might need a new IP address.
            HDHomeRunDiscoverer.needBroadcast();

            closeSocket();
            throw new IOException("HDHomeRun did not reply with a message. Closing socket...");
        }

        logger.exit();
    }

    private class ReceiveThread implements Runnable {

        public void run() {
            logger.entry();

            if (socket != null) {
                try {
                    boolean firstBytes = true;

                    int bytesNeeded = rxPacket.BUFFER.limit();
                    int readBytes = 0;

                    while (!Thread.currentThread().isInterrupted()) {
                        readBytes = socket.read(rxPacket.BUFFER);

                        if (readBytes == -1) {
                            logger.debug("socket returned -1");
                            break;
                        }

                        totalBytes += readBytes;

                        if (firstBytes && totalBytes > 4) {
                            firstBytes = false;
                            ByteBuffer slice = rxPacket.BUFFER.duplicate();
                            slice.flip();

                            // This makes sure we get everything in case the message gets broken up.
                            slice.getShort();
                            bytesNeeded = (slice.getShort() & 0xffff) + 8; // +8 to include header and CRC
                        }

                        if (bytesNeeded > totalBytes) {
                            logger.debug("bytesNeeded: {} > returnBytes: {}", bytesNeeded, totalBytes);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // Signal to the discoverer that we might need a new IP address.
                    HDHomeRunDiscoverer.needBroadcast();

                    logger.debug("ReceiveThread was unable to receive => ", e);
                } catch (Exception e) {
                    logger.warn("ReceiveThread experienced an unexpected exception => ", e);
                }
            }

            logger.exit();
        }
    }
}
