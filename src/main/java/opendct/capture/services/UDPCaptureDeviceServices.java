/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.capture.services;

import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.producer.NIOUDPProducerImpl;
import opendct.producer.SageTVProducer;
import opendct.producer.UDPProducer;
import opendct.util.ThreadPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
This incorporates a lot of very basic functionality that might be
otherwise duplicated between RTP capture devices.
 */
public class UDPCaptureDeviceServices {
    private final static Logger logger = LogManager.getLogger(UDPCaptureDeviceServices.class);

    private int udpLocalPort = -1;
    private InetAddress udpRemoteAddress = null;
    private UDPProducer udpProducerRunnable = null;
    private Future udpProducerFuture = null;
    private final ReentrantReadWriteLock udpProducerLock = new ReentrantReadWriteLock(true);

    /**
     * Create a new RTP helper services.
     *
     * @param encoderName This name is used to uniquely identify this capture device when asking for
     *                    a port number.
     * @param propertiesDeviceParent  This is the root for parent properties for this device.
     * @param port Specific port to be used for incoming UDP data.
     */
    public UDPCaptureDeviceServices(String encoderName, String propertiesDeviceParent, int port, InetAddress remoteAddress) {
        logger.debug("Forcing use of port {} for incoming UDP data...", port);
        udpLocalPort = port;
        udpRemoteAddress = remoteAddress;
    }


    /**
     * Start receiving UDP content on a specific port.
     *
     * @param encoderName    Used to get a new port if the currently selected port is already in
     *                       use.
     * @param udpProducer    This is the producer to be used to receive UDP packets.
     * @param sageTVConsumer This is the consumer to be used to write the accumulated data from this
     *                       producer.
     * @param uri            The URI consisting of the remote server hostname/IP address and local
     *                       port the UDP data will be streamed into.
     * @return <i>true</i> if the producer was able to start.
     */
    public boolean startProducing(String encoderName, UDPProducer udpProducer, SageTVConsumer sageTVConsumer, URI uri) {
        logger.entry(encoderName, udpProducer, sageTVConsumer, uri);

        boolean returnValue = false;
        int retryCount = 0;

        InetAddress newUdpRemoteAddress = udpRemoteAddress;
        int newUdpLocalPort = udpLocalPort;

        String testHost = uri.getHost();
        try {
            newUdpRemoteAddress = InetAddress.getByName(testHost);
        } catch (UnknownHostException e) {
            logger.error("'{}' will not resolve to an IP address, using last known IP address '{}'.", testHost, udpRemoteAddress);
        }

        int testPort = uri.getPort();
        if (testPort > 0) {
            newUdpLocalPort = testPort;
        } else {
            logger.error("'{}' is not a valid port, using last known port '{}'.", testPort, udpLocalPort);
        }

        //In case we left the last producer running.
        if (!stopProducing(true)) {
            logger.warn("Waiting for producer thread to exit was interrupted.");
            return logger.exit(false);
        }

        udpProducerLock.writeLock().lock();

        try {
            udpRemoteAddress = newUdpRemoteAddress;
            udpLocalPort = newUdpLocalPort;

            try {
                udpProducerRunnable = udpProducer;
                udpProducerRunnable.setConsumer(sageTVConsumer);
                udpProducerRunnable.setStreamingSocket(udpRemoteAddress, udpLocalPort);

                // In case the port was dynamically assigned.
                this.udpLocalPort = udpProducerRunnable.getLocalPort();
                udpProducerFuture = ThreadPool.submit(udpProducerRunnable, Thread.NORM_PRIORITY,
                        udpProducerRunnable.getClass().getSimpleName(), encoderName);

                returnValue = true;
            } catch (IOException e) {
                logger.error("Unable to open port {} => ", udpLocalPort, e);
                returnValue = false;
            } catch (Exception e) {
                logger.error("Unable to start producing UDP from port {} => ", udpLocalPort, e);
                returnValue = false;
            }
        } catch (Exception e) {
            logger.error("startProducing created an unexpected exception => ", e);
        } finally {
            udpProducerLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public UDPProducer getNewUDPProducer(String propertiesDeviceParent) {
        return Config.getUDProducer(
                propertiesDeviceParent + "udp.producer",
                Config.getString("udp.new.default_producer_impl",
                        NIOUDPProducerImpl.class.getName()));
    }

    // It is entirely possible for the object to become null from the start thread.
    public boolean isProducing() {
        logger.entry();

        boolean returnValue = false;

        udpProducerLock.readLock().lock();

        try {
            if (udpProducerRunnable != null) {
                returnValue = udpProducerRunnable.getIsRunning();
            }
        } finally {
            udpProducerLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public SageTVProducer getProducer() {
        return udpProducerRunnable;
    }

    public UDPProducer getUdpProducerRunnable() {
        return udpProducerRunnable;
    }

    /**
     * Stops the producer if it is running.
     *
     * @param wait Set this <i>true</i> if you want to wait for the consumer to completely stop.
     * @return <i>false</i> if blocking the consumer was interrupted.
     */
    public boolean stopProducing(boolean wait) {
        logger.entry();

        boolean returnValue = true;

        udpProducerLock.readLock().lock();

        try {
            if (udpProducerRunnable != null && udpProducerRunnable.getIsRunning()) {
                logger.debug("Stopping producer thread...");

                udpProducerRunnable.stopProducing();
                udpProducerFuture.cancel(true);

                int counter = 0;
                while (true) {
                    try {
                        udpProducerFuture.get(1000, TimeUnit.MILLISECONDS);
                        break;
                    } catch (TimeoutException e) {
                        if (counter++ < 5) {
                            logger.debug("Waiting for producer thread to stop...");
                        } else {
                            // It should never take 5 seconds for the producer to stop. This
                            // should make everyone aware that something abnormal is happening.
                            logger.warn("Waiting for producer thread to stop for over {} seconds...", counter);
                        }
                    } catch (CancellationException e) {
                        break;
                    }
                }
            } else {
                logger.debug("Producer was not running.");
            }
        } catch (InterruptedException e) {
            logger.debug("'{}' thread was interrupted => {}",
                    Thread.currentThread().getClass().toString(), e);
            returnValue = false;
        } catch (ExecutionException e) {
            logger.debug("Thread was terminated badly => ", e);
            returnValue = false;
        } finally {
            udpProducerLock.readLock().unlock();
        }


        return logger.exit(returnValue);
    }

    public int getUdpLocalPort() {
        return udpLocalPort;
    }
}
