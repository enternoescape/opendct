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

package opendct.producer;

import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.video.rtsp.rtcp.RTCPClient;
import opendct.video.rtsp.rtp.RTPPacketProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class NIORTPProducerImpl implements RTPProducer {
    private final Logger logger = LogManager.getLogger(NIORTPProducerImpl.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private RTPPacketProcessor packetProcessor = new RTPPacketProcessor();
    private RTCPClient rtcpClient = new RTCPClient();

    private int packetsBadReceived = 0;
    private long packetsReceived = 0;
    private long packetsLastReceived = 0;
    private boolean stalled = false;
    private int localPort = 0;
    private final int stalledTimeout =
            Config.getInteger("producer.rtp.nio.stalled_timeout_s", 6);
    private final int udpNativeReceiveBufferSize =
            Config.getInteger("producer.rtp.nio.native_udp_receive_buffer", 1328000);
    private int udpInternalReceiveBufferSize =
            Config.getInteger("producer.rtp.nio.internal_udp_receive_buffer", 1500);
    private final int udpInternalReceiveBufferLimit = 5242880;
    private InetAddress remoteIPAddress = null;
    private DatagramChannel datagramChannel = null;
    private Thread timeoutThread = null;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final Object receiveMonitor = new Object();

    private SageTVConsumer sageTVConsumer = null;

    public synchronized void setStreamingSocket(InetAddress streamRemoteIP, int streamLocalPort) throws IOException {
        logger.entry(streamRemoteIP, streamLocalPort);
        if (running.get()) {
            throw new IOException("The IP address and port for RTP producer cannot be changed while the thread is running.");
        }

        this.localPort = streamLocalPort;
        this.remoteIPAddress = streamRemoteIP;

        try {
            datagramChannel = DatagramChannel.open();
            datagramChannel.socket().bind(new InetSocketAddress(this.localPort));
            datagramChannel.socket().setBroadcast(false);
            datagramChannel.socket().setReceiveBufferSize(udpNativeReceiveBufferSize);

            // In case 0 was used and a port was automatically chosen.
            this.localPort = datagramChannel.socket().getLocalPort();

            rtcpClient.startReceiving(streamRemoteIP, this.localPort + 1);
        } catch (IOException e) {
            if (datagramChannel != null) {
                try {
                    datagramChannel.close();
                    datagramChannel.socket().close();
                } catch (IOException e0) {
                    logger.debug("Producer created an exception while closing the datagram channel => ", e0);
                }
            }

            rtcpClient.stopReceiving();

            throw e;
        }

        logger.exit();
    }

    public boolean getIsRunning() {
        return running.get();
    }

    public synchronized void setConsumer(SageTVConsumer sageTVConsumer) throws IOException {
        if (running.get()) {
            throw new IOException("The consumer cannot be changed while the thread is running.");
        }

        this.sageTVConsumer = sageTVConsumer;
    }

    public int getPacketsLost() {
        return packetProcessor.getMissedRTPPackets() + packetsBadReceived;
    }

    public void stopProducing() {
        if (stop.getAndSet(true)) {
            return;
        }

        datagramChannel.socket().close();
    }

    public boolean isStalled() {
        synchronized (receiveMonitor) {
            return stalled;
        }
    }

    public int getLocalPort() {
        return localPort;
    }

    public InetAddress getRemoteIPAddress() {
        return remoteIPAddress;
    }

    public void run() throws IllegalThreadStateException {
        if (running.getAndSet(true)) {
            logger.warn("The producer is already running.");
            throw new IllegalThreadStateException("The producer is already running.");
        }

        if (stop.getAndSet(false)) {
            logger.warn("Producer was requesting to stop before it started.");
            throw new IllegalThreadStateException("The producer is still stopping.");
        }

        logger.info("Producer thread is running.");

        timeoutThread = new Thread(new Runnable() {
            @Override
            public void run() {

                if (stalledTimeout == 0) {
                    logger.debug("Stall timeout detection is disabled.");
                    return;
                }

                logger.info("Producer packet monitoring thread is running.");
                boolean firstPacketsReceived = true;

                while(!stop.get() && !Thread.currentThread().isInterrupted()) {
                    synchronized (receiveMonitor) {
                        packetsLastReceived = packetsReceived;
                    }

                    long recentPackets;

                    try {
                        int timeout = stalledTimeout;

                        while (!Thread.currentThread().isInterrupted() && timeout-- > 0) {
                            Thread.sleep(1000);

                            synchronized (receiveMonitor) {
                                recentPackets = packetsReceived;

                                if (!(recentPackets == packetsLastReceived)) {
                                    stalled = false;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        logger.debug("The packet monitoring thread has been interrupted.");
                        break;
                    }

                    synchronized (receiveMonitor) {
                        recentPackets = packetsReceived;
                    }

                    if (recentPackets == packetsLastReceived) {
                        logger.info("No packets received in over {} seconds.", stalledTimeout);

                        synchronized (receiveMonitor) {
                            stalled = true;
                        }
                    } else {
                        synchronized (receiveMonitor) {
                            stalled = false;
                        }
                    }

                    if (recentPackets > 0) {
                        if (firstPacketsReceived) {
                            firstPacketsReceived = false;
                            logger.info("Received first {} datagram packets. Missed RTP packets {}. Bad RTP packets {}.", recentPackets, packetProcessor.getMissedRTPPackets(), packetsBadReceived);
                        }
                    }
                }

                logger.info("Producer packet monitoring thread has stopped.");
            }
        });

        timeoutThread.setName("PacketsMonitor-" + timeoutThread.getId() + ":" + Thread.currentThread().getName());
        timeoutThread.start();

        // We could be doing channel scanning that doesn't need this kind of prioritization.
        if (Thread.currentThread().getPriority() != Thread.MIN_PRIORITY) {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        }

        while (!stop.get() && !Thread.currentThread().isInterrupted()) {
            if (stop.get()) {
                break;
            }

            try {
                int datagramSize = -1;

                // A standard RTP transmitted datagram payload should not be larger than 1328 bytes,
                // but the largest possible UDP packet size is 65535, so we'll double that to 131016.
                ByteBuffer datagramBuffer = ByteBuffer.allocate(udpInternalReceiveBufferSize);

                while (!Thread.currentThread().isInterrupted()) {
                    datagramBuffer.clear();

                    logger.trace("Waiting for datagram...");
                    datagramChannel.receive(datagramBuffer);
                    datagramSize = datagramBuffer.position();
                    datagramBuffer.flip();

                    //Copying and queuing bad packets wastes resources.
                    if (datagramSize > 12) {
                        // Keeps a counter updated with how many RTP packets we probably
                        // lost and in the case of a byte buffers, it moves the index
                        // position to 12.
                        packetProcessor.findMissingRTPPackets(datagramBuffer);

                        sageTVConsumer.write(datagramBuffer.array(), datagramBuffer.position(), datagramBuffer.remaining());

                        if (datagramSize >= udpInternalReceiveBufferSize) {
                            if (udpInternalReceiveBufferSize < udpInternalReceiveBufferLimit) {
                                if (udpInternalReceiveBufferSize < 32767) {
                                    // This will cover 99% of the required adjustments.
                                    udpInternalReceiveBufferSize = 32767;
                                    datagramBuffer = ByteBuffer.allocate(udpInternalReceiveBufferSize);
                                } else {
                                    if (udpInternalReceiveBufferSize * 2 >= udpInternalReceiveBufferLimit) {
                                        udpInternalReceiveBufferSize = udpInternalReceiveBufferLimit;
                                        datagramBuffer = ByteBuffer.allocate(udpInternalReceiveBufferSize);
                                    } else {
                                        udpInternalReceiveBufferSize = udpInternalReceiveBufferLimit * 2;
                                        datagramBuffer = ByteBuffer.allocate(udpInternalReceiveBufferSize);
                                    }
                                }

                                Config.setInteger("producer.rtp.nio.internal_udp_receive_buffer", udpInternalReceiveBufferSize);
                                logger.warn("The datagram buffer is at its limit. Data may have been lost. Increased buffer capacity to {} bytes.", datagramBuffer.limit());
                            } else {
                                if (!(udpInternalReceiveBufferSize == udpInternalReceiveBufferLimit)) {
                                    datagramBuffer = ByteBuffer.allocate(udpInternalReceiveBufferLimit);
                                    Config.setInteger("producer.rtp.nio.internal_udp_receive_buffer", udpInternalReceiveBufferSize);
                                }
                                logger.warn("The datagram buffer is at its limit. Data may have been lost. Buffer increase capacity limit reached at {} bytes.", datagramBuffer.limit());
                            }
                        }
                    } else {
                        synchronized (receiveMonitor) {
                            packetsBadReceived += 1;
                        }
                    }

                    synchronized (receiveMonitor) {
                        packetsReceived += 1;
                    }
                }
            } catch (ClosedByInterruptException e) {
                logger.debug("Producer was closed by an interrupt exception => ", e);
            } catch (AsynchronousCloseException e) {
                logger.debug("Producer was closed by an asynchronous close exception => ", e);
            } catch (ClosedChannelException e) {
                logger.debug("Producer was closed by a close channel exception => ", e);
            } catch (Exception e) {
                logger.error("Producer created an unexpected exception => ", e);
            } finally {
                logger.info("Producer thread has disconnected.");
            }
        }

        if (datagramChannel != null) {
            try {
                rtcpClient.stopReceiving();
            } catch (Exception e) {
                logger.debug("Producer created an exception while closing the RTCP channel => ", e);
            }

            try {
                datagramChannel.close();
                // The datagram channel doesn't seem to close the socket every time.
                datagramChannel.socket().close();
            } catch (IOException e) {
                logger.debug("Producer created an exception while closing the datagram channel => ", e);
            }
        }

        logger.info("Producer thread has stopped.");

        if (timeoutThread != null) {
            try {
                int timeout = 0;

                while (timeoutThread != null && timeoutThread.isAlive()) {
                    timeoutThread.interrupt();
                    timeoutThread.join(1000);

                    if (timeout++ > 5) {
                        logger.warn("Producer has been waiting for packet monitoring thread to stop for over {} seconds.", timeout);
                    }
                }
            } catch (InterruptedException e) {
                logger.debug("Producer was interrupted while waiting for packet monitoring thread to stop => ", e);
            }
        }

        if (rtcpClient != null) {
            try {
                rtcpClient.waitForStop();
            } catch (InterruptedException e) {
                logger.debug("Producer was interrupted while waiting for RTCP client thread to stop => ", e);
            }
        }

        running.set(false);
        stop.set(false);
    }

    public long getPackets() {
        synchronized (receiveMonitor) {
            return packetsReceived;
        }
    }
}