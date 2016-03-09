/*
 * Copyright 2016 The OpenDCT Authors. All Rights Reserved
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

package opendct.capture;

import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.producer.NIORTPProducerImpl;
import opendct.producer.RTPProducer;
import opendct.producer.SageTVProducer;
import opendct.video.rtsp.DCTRTSPClientImpl;
import opendct.video.rtsp.RTSPClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
This incorporates a lot of very basic functionality that might be
otherwise duplicated between RTP capture devices.
 */
public abstract class RTPCaptureDeviceServices {
    private final Logger logger = LogManager.getLogger(RTPCaptureDeviceServices.class);

    protected URI rtpStreamRemoteURI;
    protected InetAddress rtpStreamRemoteIP;
    protected int rtpLocalPort = -1;
    protected RTPProducer rtpProducerRunnable = null;
    protected Thread rtpProducerThread = null;
    protected final ReentrantReadWriteLock rtpProducerLock = new ReentrantReadWriteLock(true);
    protected RTSPClient rtspClient = null;

    /**
     * Create a new RTP helper services.
     *
     * @param encoderName This name is used to uniquely identify this capture device. The encoder
     *                   version is defaulted to 3.0.
     * @throws CaptureDeviceIgnoredException If the configuration indicates that this device should
     *                                       not be loaded, this exception will be thrown.
     */
    public RTPCaptureDeviceServices(String encoderName, String propertiesDeviceParent) {
        logger.debug("Initializing RTSP client...");
        rtspClient = getNewRTSPClient(propertiesDeviceParent);

        logger.debug("Getting a port for incoming RTP data...");
        rtpLocalPort = Config.getFreeRTSPPort(encoderName);
    }


    /**
     * Start receiving RTP content on a specific port.
     *
     * @param rtpProducer    This is the producer to be used to receive RTP packets.
     * @param sageTVConsumer This is the consumer to be used to write the accumulated data from this
     *                       producer.
     * @param remoteIP       This is the IP address of the RTP server.
     * @param localPort      This is the suggested port to listen for RTP packets. If this port is
     *                       already in use, this method will use
     * @return <i>true</i> if the producer was able to start.
     */
    public boolean startProducing(RTPProducer rtpProducer, SageTVConsumer sageTVConsumer,
                                     InetAddress remoteIP, Integer localPort, String encoderName) {
        logger.entry(rtpProducer, sageTVConsumer, remoteIP, localPort);

        boolean returnValue = false;
        int retryCount = 0;

        //In case we left the last producer running.
        if (!stopProducing(true)) {
            logger.warn("Waiting for producer thread to exit was interrupted.");
            return logger.exit(false);
        }

        rtpProducerLock.writeLock().lock();

        try {
            while (!returnValue) {
                try {
                    this.rtpLocalPort = localPort;
                    this.rtpStreamRemoteIP = remoteIP;

                    rtpProducerRunnable = rtpProducer;
                    rtpProducerRunnable.setConsumer(sageTVConsumer);
                    rtpProducerRunnable.setStreamingSocket(remoteIP, this.rtpLocalPort);

                    // In case the port was dynamically assigned.
                    this.rtpLocalPort = rtpProducerRunnable.getLocalPort();
                    rtpProducerThread = new Thread(rtpProducerRunnable);
                    rtpProducerThread.setName(rtpProducerRunnable.getClass().getSimpleName() + "-" + rtpProducerThread.getId() + ":" + encoderName);
                    rtpProducerThread.start();

                    returnValue = true;
                } catch (IOException e) {
                    try {
                        Config.returnFreeRTSPPort(this.rtpLocalPort);
                        this.rtpLocalPort = Config.getFreeRTSPPort(encoderName);
                        logger.error("Unable to open port {}. Changed port to {}. Attempt number {}. => ", localPort, this.rtpLocalPort, retryCount, e);
                        localPort = this.rtpLocalPort;
                    } catch (Exception e0) {
                        logger.error("Unable to change port => ", e0);
                        returnValue = false;
                        break;
                    }
                } catch (Exception e) {
                    logger.error("Unable to start producing RTP from port {} => ", localPort, e);
                    returnValue = false;
                    break;
                }

                if (retryCount++ > 5) {
                    returnValue = false;
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("startProducing created an unexpected exception => ", e);
        } finally {
            rtpProducerLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    // Use factory methods like this one to get interfaces so it can be customized via the
    // properties file.
    public RTSPClient getNewRTSPClient(String propertiesDeviceParent) {
        return Config.getRTSPClient(
                propertiesDeviceParent + "rtsp",
                Config.getString("rtsp.new.default_impl",
                        DCTRTSPClientImpl.class.getName()));
    }

    public RTPProducer getNewRTPProducer(String propertiesDeviceParent) {
        return Config.getRTProducer(
                propertiesDeviceParent + "rtp.producer",
                Config.getString("rtp.new.default_producer_impl",
                        NIORTPProducerImpl.class.getName()));
    }

    // It is entirely possible for the object to become null from the start thread.
    public Boolean isProducing() {
        logger.entry();

        boolean returnValue = false;

        rtpProducerLock.readLock().lock();

        try {
            if (rtpProducerRunnable != null) {
                returnValue = rtpProducerRunnable.getIsRunning();
            }
        } finally {
            rtpProducerLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public SageTVProducer getProducer() {
        return rtpProducerRunnable;
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

        rtpProducerLock.readLock().lock();

        try {
            if (rtpProducerRunnable != null && rtpProducerRunnable.getIsRunning()) {
                logger.debug("Stopping producer thread...");

                rtpProducerRunnable.stopProducing();
                rtpProducerThread.interrupt();

                int counter = 0;
                while (rtpProducerThread.isAlive()) {
                    if (counter++ < 5) {
                        logger.debug("Waiting for producer thread to stop...");
                    } else {
                        // It should never take 5 seconds for the producer to stop. This
                        // should make everyone aware that something abnormal is happening.
                        logger.warn("Waiting for producer thread to stop for over {} seconds...", counter);
                    }
                    rtpProducerThread.join(1000);
                }
            } else {
                logger.debug("Producer was not running.");
            }
        } catch (InterruptedException e) {
            logger.debug("'{}' thread was interrupted => {}",
                    Thread.currentThread().getClass().toString(), e);
            returnValue = false;
        } finally {
            rtpProducerLock.readLock().unlock();
        }


        return logger.exit(returnValue);
    }

    // Stop the capture device from streaming and stop the encoder threads.
    public void returnRTPPort() {
        Config.returnFreeRTSPPort(rtpLocalPort);

        if (rtspClient != null && rtpStreamRemoteURI != null) {
            try {
                rtspClient.stopRTPStream(rtpStreamRemoteURI);
            } catch (Exception e) {
                logger.error("An unexpected exception was created while stopping the RTP stream => ", e);
            }
        }
    }

}
