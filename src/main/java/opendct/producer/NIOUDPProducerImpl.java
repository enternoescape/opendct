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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class NIOUDPProducerImpl implements UDPProducer {
    private final Logger logger = LogManager.getLogger(NIOUDPProducerImpl.class);

    private static final int RECEIVE_BUFFER_LIMIT = 5242880;

    private AtomicBoolean running = new AtomicBoolean(false);

    private AtomicInteger packetsLost = new AtomicInteger(0);
    private AtomicLong packetsReceived = new AtomicLong(0);
    private int localPort = 0;

    private final int nioUdpThreadPriority =
            Math.max(
                    Math.min(
                            Config.getInteger("producer.udp.nio.thread_priority", Thread.MAX_PRIORITY - 1),
                            Thread.MAX_PRIORITY
                    ),
                    Thread.MIN_PRIORITY
            );

    private final int nativeReceiveBufferSize =
            Config.getInteger("producer.udp.nio.native_udp_receive_buffer", 5312000);
    // A standard RTP transmitted datagram payload should not be larger than 1328 bytes,
    // but the largest possible UDP packet size is 65535, so this value will be adjusted
    // automatically if this value is found to be too small.
    private int receiveBufferSize =
            Config.getInteger("producer.udp.nio.internal_udp_receive_buffer", 1500);
    private boolean allocateDirect =
            Config.getBoolean("producer.udp.nio.allocate_direct", true);
    private boolean logTiming =
            Config.getBoolean("producer.udp.nio.log_timing_exp", true);
    private InetAddress remoteIPAddress = null;
    private DatagramChannel datagramChannel = null;
    private AtomicBoolean stop = new AtomicBoolean(false);

    private SageTVConsumer sageTVConsumer = null;

    public synchronized void setStreamingSocket(InetAddress streamRemoteIP, int streamLocalPort) throws IOException {
        logger.entry(streamRemoteIP, streamLocalPort);
        if (running.getAndSet(true)) {
            throw new IOException("The IP address and port for RTP producer cannot be changed while the thread is running.");
        }

        this.localPort = streamLocalPort;
        this.remoteIPAddress = streamRemoteIP;

        openStreamingSocket();

        logger.exit();
    }

    public synchronized void openStreamingSocket() throws IOException {
        try {
            datagramChannel = DatagramChannel.open();
            datagramChannel.socket().bind(new InetSocketAddress(this.localPort));
            datagramChannel.socket().setBroadcast(false);
            datagramChannel.socket().setReceiveBufferSize(nativeReceiveBufferSize);

            // In case 0 was used and a port was automatically chosen.
            this.localPort = datagramChannel.socket().getLocalPort();

            //rtcpClient.startReceiving(streamRemoteIP, this.localPort + 1);
        } catch (IOException e) {
            if (datagramChannel != null) {
                try {
                    datagramChannel.close();
                    datagramChannel.socket().close();
                } catch (IOException e0) {
                    logger.debug("Producer created an exception while closing the datagram channel => ", e0);
                }
            }

            throw e;
        }
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

    @Override
    public int getPacketsLost() {
        return packetsLost.intValue();
    }

    public void stopProducing() {
        if (stop.getAndSet(true)) {
            return;
        }

        datagramChannel.socket().close();
    }

    public int getLocalPort() {
        return localPort;
    }

    public InetAddress getRemoteIPAddress() {
        return remoteIPAddress;
    }

    public void run() {
        logger.info("Producer thread is running.");

        // We could be doing channel scanning that doesn't need this kind of prioritization.
        if (Thread.currentThread().getPriority() != Thread.MIN_PRIORITY) {
            Thread.currentThread().setPriority(nioUdpThreadPriority);
        }

        logger.debug("Thread priority is {}.", Thread.currentThread().getPriority());

        // Buffer transfers should always be less than a millisecond.
        int transferTolerance = 1;
        long timer = 0;
        int datagramSize;

        ByteBuffer datagramBuffer = allocateDirect ? ByteBuffer.allocateDirect(receiveBufferSize) : ByteBuffer.allocate(receiveBufferSize);
        ByteBuffer doubleBuffer = null;

        while (!stop.get()) {
            while (datagramChannel == null && !stop.get()) {
                try {
                    openStreamingSocket();
                } catch (IOException e) {
                    logger.error("Error opening socket => ", e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        logger.debug("Socket re-open sleep interrupted.");
                    }
                }
            }

            try {
                while (!stop.get()) {
                    datagramBuffer.clear();

                    //logger.trace("Waiting for datagram...");
                    datagramChannel.receive(datagramBuffer);
                    datagramSize = datagramBuffer.position();
                    datagramBuffer.flip();

                    //Copying and queuing bad packets wastes resources.
                    if (datagramSize >= 188) {
                        if (logTiming) timer = System.currentTimeMillis();

                        if (doubleBuffer != null) {
                            if (doubleBuffer.remaining() < datagramSize) {
                                doubleBuffer.flip();
                                sageTVConsumer.write(doubleBuffer);
                                doubleBuffer.clear();
                            }
                            doubleBuffer.put(datagramBuffer);
                        } else {
                            sageTVConsumer.write(datagramBuffer);
                        }

                        if (logTiming) {
                            timer = System.currentTimeMillis() - timer;
                            if (timer > 5 && doubleBuffer == null) {
                                logger.info("High transfer latency detected. Double buffer enabled. {}ms",  timer);
                                doubleBuffer = ByteBuffer.allocateDirect(Math.max((receiveBufferSize * 2) + 1, 32768));
                                doubleBuffer.clear();
                                logTiming = false;
                            }
                        }

                        if (datagramSize >= receiveBufferSize) {
                            if (receiveBufferSize < RECEIVE_BUFFER_LIMIT) {
                                if (receiveBufferSize < 32768) {
                                    // This will cover 99% of the required adjustments.
                                    receiveBufferSize = 32768;
                                } else {
                                    receiveBufferSize = Math.min(receiveBufferSize * 2, RECEIVE_BUFFER_LIMIT);
                                }

                                if (doubleBuffer != null) {
                                    doubleBuffer.flip();
                                    sageTVConsumer.write(doubleBuffer);
                                    doubleBuffer = ByteBuffer.allocateDirect(doubleBuffer.capacity() * 2);
                                    doubleBuffer.clear();
                                }

                                datagramBuffer = allocateDirect ? ByteBuffer.allocateDirect(receiveBufferSize) : ByteBuffer.allocate(receiveBufferSize);
                                Config.setInteger("producer.udp.nio.internal_udp_receive_buffer", receiveBufferSize);
                                logger.warn("The datagram buffer is at its limit. Data may have been lost. Increased buffer capacity to {} bytes.", datagramBuffer.limit());
                            } else {
                                if (!(receiveBufferSize == RECEIVE_BUFFER_LIMIT)) {
                                    datagramBuffer = ByteBuffer.allocateDirect(RECEIVE_BUFFER_LIMIT);
                                    Config.setInteger("producer.udp.nio.internal_udp_receive_buffer", receiveBufferSize);
                                }
                                logger.warn("The datagram buffer is at its limit. Data may have been lost. Buffer increase capacity limit reached at {} bytes.", datagramBuffer.limit());
                            }
                        }
                    } else {
                        packetsLost.addAndGet(1);
                        logger.warn("Bad UDP packet size: {}", datagramSize);
                    }

                    packetsReceived.addAndGet(1);
                }
            } catch (ClosedByInterruptException e) {
                logger.debug("Producer was closed by an interrupt exception => {}", e.getMessage());
            } catch (AsynchronousCloseException e) {
                logger.debug("Producer was closed by an asynchronous close exception => {}", e.getMessage());
            } catch (ClosedChannelException e) {
                logger.debug("Producer was closed by a close channel exception => {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Producer created an unexpected exception => ", e);
            } finally {
                logger.info("Producer thread has disconnected.");

                if (datagramChannel != null) {
                    try {
                        datagramChannel.close();
                        // The datagram channel doesn't seem to close the socket every time.
                        datagramChannel.socket().close();
                    } catch (Exception e) {
                        logger.debug("Producer created an exception while closing the datagram channel => {}", e.getMessage());
                    }
                }
            }
        }

        logger.info("Producer thread has stopped.");

        running.set(false);
        stop.set(false);
    }

    public long getPackets() {
        return packetsReceived.get();
    }
}