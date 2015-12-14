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

import opendct.tuning.hdhomerun.types.HDHomeRunPacketTag;
import opendct.tuning.hdhomerun.types.HDHomeRunPacketType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.HashSet;

public class HDHomeRunDiscovery implements Runnable {
    private static final Logger logger = LogManager.getLogger(HDHomeRunDiscovery.class);

    public final InetAddress BROADCAST_ADDRESS;
    public final int BROADCAST_PORT;
    public final InetSocketAddress BROADCAST_SOCKET;

    private Thread sendThread;
    private Thread receiveThread;
    private DatagramChannel datagramChannel;
    HDHomeRunPacket txPacket;
    HDHomeRunPacket rxPacket;

    private HashSet<HDHomeRunDevice> devices;
    private final Object devicesLock = new Object();

    public HDHomeRunDiscovery(InetAddress broadcastAddress) {
        this.BROADCAST_ADDRESS = broadcastAddress;
        BROADCAST_PORT = HDHomeRunPacket.HDHOMERUN_DISCOVER_UDP_PORT;
        BROADCAST_SOCKET = new InetSocketAddress(BROADCAST_ADDRESS, BROADCAST_PORT);
        txPacket = new HDHomeRunPacket();
        rxPacket = new HDHomeRunPacket();
        devices = new HashSet<HDHomeRunDevice>();
    }

    public HDHomeRunDiscovery(InetAddress broadcastAddress, int broadcastPort) {
        this.BROADCAST_ADDRESS = broadcastAddress;
        this.BROADCAST_PORT = broadcastPort;
        BROADCAST_SOCKET = new InetSocketAddress(BROADCAST_ADDRESS, BROADCAST_PORT);
        txPacket = new HDHomeRunPacket();
        rxPacket = new HDHomeRunPacket();
        devices = new HashSet<HDHomeRunDevice>();
    }

    private HashSet<HDHomeRunDevice> getAndClearDevices() {
        HashSet<HDHomeRunDevice> returnDevices = new HashSet<HDHomeRunDevice>();

        synchronized (devicesLock) {
            HashSet<HDHomeRunDevice> oldDevices = devices;
            devices = returnDevices;
            returnDevices = oldDevices;
        }

        return returnDevices;
    }

    private void addDevice(HDHomeRunDevice device) {
        synchronized (devicesLock) {
            devices.add(device);
        }
    }

    public void start() throws IOException {
        if (sendThread != null && !sendThread.isAlive()) {
            logger.warn("Already listening for HDHomeRun devices on port {}", BROADCAST_PORT);
            return;
        }

        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
            try {
                receiveThread.join();
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for receive thread to stop => ", e);
                return;
            }
        }

        sendThread = new Thread(this);
        sendThread.setName("HDHomeRunDiscoverySend-" + sendThread.getId());

        datagramChannel = DatagramChannel.open();
        datagramChannel.socket().bind(new InetSocketAddress(BROADCAST_PORT));
        datagramChannel.socket().setBroadcast(true);
        datagramChannel.socket().setReceiveBufferSize(10000);

        receiveThread = new Thread(new ReceiveThread());
        receiveThread.setName("HDHomeRunDiscoveryReceive-" + sendThread.getId());

        sendThread.start();
        receiveThread.start();
    }

    public void stop() throws InterruptedException {
        sendThread.interrupt();
        receiveThread.join();
    }

    public void waitForDiscovery() throws InterruptedException {
        sendThread.join();
    }

    public void run() {
        txPacket.startPacket(HDHomeRunPacketType.HDHOMERUN_TYPE_DISCOVER_REQ);

        txPacket.putTagLengthValue(
                HDHomeRunPacketTag.HDHOMERUN_TAG_DEVICE_TYPE,
                HDHomeRunPacket.HDHOMERUN_DEVICE_TYPE_WILDCARD
        );

        txPacket.putTagLengthValue(
                HDHomeRunPacketTag.HDHOMERUN_TAG_DEVICE_ID,
                HDHomeRunPacket.HDHOMERUN_DEVICE_ID_WILDCARD
        );

        txPacket.endPacket();

        int retry = 3;
        txPacket.BUFFER.mark();

        while (!Thread.currentThread().isInterrupted() && retry-- > 0) {

            boolean failed = false;
            while (txPacket.BUFFER.hasRemaining()) {
                try {
                    logger.info("Sending HDHomeRun discovery packet length {}...", txPacket.BUFFER.limit());
                    datagramChannel.send(txPacket.BUFFER, BROADCAST_SOCKET);
                } catch (IOException e) {
                    logger.error("Error while sending HDHomeRun discovery packets to {} => ", BROADCAST_SOCKET, e);
                    failed = true;
                    break;
                }
            }

            txPacket.BUFFER.reset();

            if (failed) {
                continue;
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for packets to be received => ", e);
                break;
            }

        }

        if (datagramChannel != null && datagramChannel.isConnected()) {
            try {
                datagramChannel.close();
                datagramChannel.socket().close();
            } catch (IOException e0) {
                logger.debug("Created an exception while closing the datagram channel => {}", e0);
            }
        }

        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
        }

        HDHomeRunManager.addDevices(getAndClearDevices());
    }

    private class ReceiveThread implements Runnable {

        public void run() {
            final char recvBase64EncodeTable[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

            while (sendThread.isAlive()) {
                rxPacket.BUFFER.clear();

                InetSocketAddress socketAddress;
                try {
                    socketAddress = (InetSocketAddress) datagramChannel.receive(rxPacket.BUFFER);
                } catch (ClosedChannelException e) {
                    logger.debug("Channel was closed while receiving HDHomeRun discovery packets from {} => ", BROADCAST_SOCKET, e);
                    return;
                } catch (IOException e) {
                    logger.error("Error while receiving HDHomeRun discovery packets from {} => ", BROADCAST_SOCKET, e);
                    return;
                }

                rxPacket.BUFFER.flip();
                if (rxPacket.BUFFER.limit() > 0) {
                    if (rxPacket.getPacketType() == HDHomeRunPacketType.HDHOMERUN_TYPE_DISCOVER_RPY) {
                        int packetLength = rxPacket.getPacketLength();
                        rxPacket.BUFFER.limit(packetLength + 4);

                        HDHomeRunDevice device = new HDHomeRunDevice(socketAddress.getAddress());

                        while (rxPacket.BUFFER.remaining() > 4) {
                            HDHomeRunPacketTag tag = rxPacket.getTag();
                            int length = rxPacket.getVariableLength();

                            if (tag == null) {
                                // Silicondust says to just ignore these.
                                logger.debug("HDHomerun device returned an unknown tag with the length {}", length);
                                rxPacket.BUFFER.position(rxPacket.BUFFER.position() + length);
                                continue;
                            }

                            switch (tag) {

                                case HDHOMERUN_TAG_DEVICE_TYPE:
                                    if (length != 4) {
                                        break;
                                    }
                                    device.setDeviceType(rxPacket.BUFFER.getInt());
                                    break;
                                case HDHOMERUN_TAG_DEVICE_ID:
                                    if (length != 4) {
                                        break;
                                    }

                                    int deviceId = rxPacket.BUFFER.getInt();

                                    if (validateDeviceId(deviceId)) {
                                        device.setDeviceId(deviceId);
                                        device.setLegacy(isLegacy(device.getDeviceId()));
                                    }
                                    break;
                                case HDHOMERUN_TAG_TUNER_COUNT:
                                    if (length != 1) {
                                        break;
                                    }

                                    device.setTunerCount(rxPacket.BUFFER.get());
                                    break;
                                case HDHOMERUN_TAG_DEVICE_AUTH_BIN:
                                    if (length != 18) {
                                        break;
                                    }

                                    char deviceAuth[] = new char[24];

                                    for (int i = 0; i < 24; i += 4) {
                                        int raw24;
                                        raw24 = (rxPacket.BUFFER.get() & 0xff) << 16;
                                        raw24 |= (rxPacket.BUFFER.get() & 0xff) << 8;
                                        raw24 |= (rxPacket.BUFFER.get() & 0xff);

                                        deviceAuth[i] = recvBase64EncodeTable[(raw24 >> 18) & 0x3F];
                                        deviceAuth[i + 1] = recvBase64EncodeTable[(raw24 >> 12) & 0x3F];
                                        deviceAuth[i + 2] = recvBase64EncodeTable[(raw24 >> 6) & 0x3F];
                                        deviceAuth[i + 3] = recvBase64EncodeTable[raw24 & 0x3F];
                                    }

                                    device.setDeviceAuth(new String(deviceAuth));

                                    break;
                                case HDHOMERUN_TAG_BASE_URL:
                                    String url = rxPacket.getTLVString(length);

                                    try {
                                        device.setBaseUrl(new URL(url));
                                    } catch (MalformedURLException e) {
                                        logger.error("HDHomeRun device returned a bad URL '{}' => ", url, e);
                                    }
                                    break;
                                case HDHOMERUN_TAG_DEVICE_AUTH_STR:
                                    device.setDeviceAuth(rxPacket.getTLVString(length));
                                    break;
                                default:
                                    // Silicondust says to just ignore these.
                                    logger.debug("HDHomerun device returned an unexpected tag {} with the length {}", tag, length);
                                    rxPacket.BUFFER.position(rxPacket.BUFFER.position() + length);
                                    break;
                            }
                        }

                        // Silicondust fixes for old firmware.
                        if (device.getTunerCount() == 0) {
                            switch (device.getDeviceId() >> 20) {
                                case 0x102:
                                    device.setTunerCount(1);
                                    break;

                                case 0x100:
                                case 0x101:
                                case 0x121:
                                    device.setTunerCount(2);
                                    break;

                                default:
                                    break;
                            }
                        }


                        addDevice(device);
                        logger.debug("Parsed discovery packet: {}", device);
                    }
                }
            }
        }
    }

    public static InetAddress getBroadcast() {
        try {
            return InetAddress.getByAddress(
                    new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        } catch (UnknownHostException e) {
            // This isn't going to happen.
            logger.error("Unable to create broadcast address.");
        }

        return null;
    }

    public static boolean isLegacy(int deviceId) {
        switch (deviceId >> 20) {
            case 0x100: /* TECH-US/TECH3-US */
                return (deviceId < 0x10040000);

            case 0x120: /* TECH3-EU */
                return (deviceId < 0x12030000);

            case 0x101: /* HDHR-US */
            case 0x102: /* HDHR-T1-US */
            case 0x103: /* HDHR3-US */
            case 0x111: /* HDHR3-DT */
            case 0x121: /* HDHR-EU */
            case 0x122: /* HDHR3-EU */
                return true;

            default:
                return false;
        }
    }

    public static boolean validateDeviceId(int deviceId) {
        byte lookupTable[] =
                {0xA, 0x5, 0xF, 0x6, 0x7, 0xC, 0x1, 0xB, 0x9, 0x2, 0x8, 0xD, 0x4, 0x3, 0xE, 0x0};

        byte checksum = 0;

        checksum ^= lookupTable[(deviceId >> 28) & 0x0F];
        checksum ^= (deviceId >> 24) & 0x0F;
        checksum ^= lookupTable[(deviceId >> 20) & 0x0F];
        checksum ^= (deviceId >> 16) & 0x0F;
        checksum ^= lookupTable[(deviceId >> 12) & 0x0F];
        checksum ^= (deviceId >> 8) & 0x0F;
        checksum ^= lookupTable[(deviceId >> 4) & 0x0F];
        checksum ^= deviceId & 0x0F;

        return (checksum == 0);
    }
}
