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

import opendct.power.NetworkPowerEventManger;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.hdhomerun.types.HDHomeRunPacketTag;
import opendct.tuning.hdhomerun.types.HDHomeRunPacketType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;

public class HDHomeRunDiscovery implements Runnable {
    private static final Logger logger = LogManager.getLogger(HDHomeRunDiscovery.class);

    public final InetAddress BROADCAST_ADDRESS[];
    public final int BROADCAST_PORT;
    public final InetSocketAddress BROADCAST_SOCKET[];

    private Thread sendThread;
    private Thread receiveThreads[];
    private DatagramChannel datagramChannels[];
    private HDHomeRunPacket txPacket;
    private HDHomeRunPacket rxPackets[];

    HDHomeRunDiscoverer discoverer;
    private final Object devicesLock = new Object();

    public HDHomeRunDiscovery(InetAddress... broadcastAddress) {
        BROADCAST_ADDRESS = broadcastAddress;
        BROADCAST_PORT = HDHomeRunPacket.HDHOMERUN_DISCOVER_UDP_PORT;
        BROADCAST_SOCKET = new InetSocketAddress[BROADCAST_ADDRESS.length];

        txPacket = new HDHomeRunPacket();
        rxPackets = new HDHomeRunPacket[BROADCAST_ADDRESS.length];

        for (int i = 0; i < BROADCAST_ADDRESS.length; i++ ) {
            BROADCAST_SOCKET[i] = new InetSocketAddress(BROADCAST_ADDRESS[i], BROADCAST_PORT);
            rxPackets[i] = new HDHomeRunPacket();
        }

        receiveThreads = new Thread[BROADCAST_ADDRESS.length];
        datagramChannels = new DatagramChannel[BROADCAST_ADDRESS.length];
    }

    public void start(HDHomeRunDiscoverer discoverer) throws IOException {
        if (sendThread != null && !sendThread.isAlive()) {
            logger.warn("Already listening for HDHomeRun devices on port {}", BROADCAST_PORT);
            return;
        }

        this.discoverer = discoverer;

        for (Thread receiveThread : receiveThreads) {
            if (receiveThread != null && receiveThread.isAlive()) {
                receiveThread.interrupt();
                try {
                    receiveThread.join();
                } catch (InterruptedException e) {
                    logger.debug("Interrupted while waiting for receive thread to stop => ", e);
                    return;
                }
            }
        }

        sendThread = new Thread(this);
        sendThread.setName("HDHomeRunDiscoverySend-" + sendThread.getId());

        for (int i = 0; i < datagramChannels.length; i++ ) {
            datagramChannels[i] = DatagramChannel.open();
            datagramChannels[i].socket().setBroadcast(true);
            datagramChannels[i].socket().setReceiveBufferSize(100000);

            ReceiveThread receiveThread = new ReceiveThread();
            receiveThread.listenIndex = i;
            receiveThreads[i] = new Thread(receiveThread);
            receiveThreads[i].setName("HDHomeRunDiscoveryReceive-" + sendThread.getId());
        }

        sendThread.start();

        for (Thread receiveThread : receiveThreads) {
            receiveThread.start();
        }
    }

    public void stop() {
        sendThread.interrupt();

        for (Thread receiveThread : receiveThreads) {
            receiveThread.interrupt();
        }
    }

    public boolean isRunning() {
        if (sendThread != null) {
            return sendThread.isAlive();
        }

        for (Thread receiveThread : receiveThreads) {
            if (receiveThread != null) {
                return receiveThread.isAlive();
            }
        }

        return false;
    }

    public void waitForStop() throws InterruptedException {
        if (receiveThreads != null) {
            for (Thread receiveThread : receiveThreads) {
                while (receiveThread.isAlive()) {
                    receiveThread.interrupt();
                    receiveThread.join(500);
                }
            }
        }

        if (sendThread != null) {
            while (sendThread.isAlive()) {
                sendThread.interrupt();
                sendThread.join(500);
            }
        }
    }

    public void run() {
        int retry = 3;

        logger.info("HDHomeRun discovery sender thread started.");

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

        txPacket.BUFFER.mark();

        while (!Thread.currentThread().isInterrupted()) {
            while (!Thread.currentThread().isInterrupted() && retry-- > 0) {

                boolean failed = false;

                for (int i = 0; i < datagramChannels.length; i++) {
                    while (txPacket.BUFFER.hasRemaining()) {
                        try {
                            if (discoverer.isWaitingForDevices()) {
                                logger.info("Broadcasting HDHomeRun discovery packet to {}...", BROADCAST_SOCKET[i]);
                            }

                            datagramChannels[i].send(txPacket.BUFFER, BROADCAST_SOCKET[i]);

                        } catch (IOException e) {
                            logger.error("Error while sending HDHomeRun discovery packets to {} => ", BROADCAST_SOCKET[i], e);
                            failed = true;
                            break;
                        }
                    }

                    txPacket.BUFFER.reset();
                }

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

            retry = 1;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int interval = HDHomeRunDiscoverer.getBroadcastInterval();
                    boolean devicesLoaded = discoverer.isWaitingForDevices();

                    if (interval == 0 && devicesLoaded) {
                        return;
                    } else if (interval == 0) {
                        Thread.sleep(4500);
                    } else {
                        Thread.sleep(HDHomeRunDiscoverer.getBroadcastInterval() * 1000);
                    }
                } catch (InterruptedException e) {
                    logger.debug("Interrupted while waiting for next broadcast to be sent => ", e);
                    return;
                }
            }
        }

        if (datagramChannels != null) {
            for (DatagramChannel datagramChannel : datagramChannels) {
                try {
                    datagramChannel.close();
                    datagramChannel.socket().close();
                } catch (IOException e) {
                    logger.debug("Created an IO exception while closing the datagram channel => ", e);
                }
            }
        }

        logger.info("HDHomeRun discovery sender thread stopped.");
    }

    private class ReceiveThread implements Runnable {
        protected int listenIndex = -1;

        public void run() {
            logger.info("HDHomeRun discovery receive thread for {} broadcast started.", BROADCAST_SOCKET[listenIndex]);

            final char recvBase64EncodeTable[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

            HDHomeRunPacket rxPacket = rxPackets[listenIndex];
            DatagramChannel datagramChannel = datagramChannels[listenIndex];

            while (sendThread != null && sendThread.isAlive()) {
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

                        try {
                            discoverer.addCaptureDevice(device);
                        } catch (Exception e) {
                            logger.error("Unable to add new HDHomeRun capture device => ", e);
                        }

                        if (discoverer.isWaitingForDevices()) {
                            logger.debug("Parsed discovery packet: {}", device);
                        }
                    }
                }
            }

            logger.info("HDHomeRun discovery receive thread for {} broadcast stopped.", BROADCAST_SOCKET[listenIndex]);
        }
    }

    public static InetAddress[] getBroadcast() {
        NetworkInterface[] networkInterfaces = NetworkPowerEventManger.getInterfaces();
        ArrayList<InetAddress> addresses = new ArrayList<>();

        for (NetworkInterface networkInterface : networkInterfaces) {
            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                if (interfaceAddress.getAddress() instanceof Inet4Address) {
                    addresses.add(interfaceAddress.getBroadcast());
                }
            }
        }

        return addresses.toArray(new InetAddress[addresses.size()]);
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
