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

package opendct.capture;

import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.producer.HTTPProducer;
import opendct.producer.HTTPProducerImpl;
import opendct.producer.SageTVProducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
This incorporates a lot of very basic functionality that might be
otherwise duplicated between RTP capture devices.
 */
public abstract class HTTPCaptureDevice extends BasicCaptureDevice implements CaptureDevice {
    private final Logger logger = LogManager.getLogger(HTTPCaptureDevice.class);

    protected URL httpURL;
    protected HTTPProducer httpProducerRunnable = null;
    protected Thread httpProducerThread = null;
    protected final ReentrantReadWriteLock httpProducerLock = new ReentrantReadWriteLock(true);

    /**
     * Create a new RTP capture device.
     *
     * @param deviceParentName This is the name of the device containing this capture device. This
     *                         is used for identifying groupings of devices.
     * @param deviceName       This name is used to uniquely identify this capture device. The encoder
     *                         version is defaulted to 3.0.
     * @throws CaptureDeviceIgnoredException If the configuration indicates that this device should
     *                                       not be loaded, this exception will be thrown.
     */
    public HTTPCaptureDevice(String deviceParentName, String deviceName) throws CaptureDeviceIgnoredException {
        super(deviceParentName, deviceName);
    }

    /**
     * Start receiving RTP content on a specific port.
     *
     * @param httpProducer   This is the producer to be used to receive the HTTP stream.
     * @param sageTVConsumer This is the consumer to be used to write the accumulated data from this
     *                       producer.
     * @param httpURL        This is the URL source of the stream.
     * @return <i>true</i> if the producer was able to start.
     */
    protected Boolean startProducing(HTTPProducer httpProducer,
                                     SageTVConsumer sageTVConsumer,
                                     URL httpURL) {

        logger.entry(httpProducer, sageTVConsumer, httpURL);

        boolean returnValue = false;
        int retryCount = 0;

        //In case we left the last producer running.
        if (!stopProducing(true)) {
            logger.warn("Waiting for producer thread to exit was interrupted.");
            return logger.exit(false);
        }

        httpProducerLock.writeLock().lock();

        try {
            while (!returnValue) {
                try {
                    this.httpURL = httpURL;

                    sageTVProducerRunnable = httpProducer;
                    httpProducerRunnable = httpProducer;
                    httpProducerRunnable.setConsumer(sageTVConsumer);
                    httpProducerRunnable.setSourceUrls(httpURL);

                    httpProducerThread = new Thread(httpProducerRunnable);
                    httpProducerThread.setName(httpProducerRunnable.getClass().getSimpleName() + "-" + sageTVConsumerThread.getId() + ":" + encoderName);
                    httpProducerThread.start();

                    returnValue = true;
                } catch (IOException e) {
                    logger.error("Unable to open the URL '{}'. Attempt number {}. => ", httpURL, retryCount, e);
                } catch (Exception e) {
                    logger.error("Unable to start producing RTP from the URL {} => ", httpURL, e);
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
            httpProducerLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    // Use factory methods like this one to get interfaces so it can be customized via the
    // properties file.
    protected HTTPProducer getNewHTTPProducer() {
        return Config.getHTTProducer(
                propertiesDeviceRoot + "http.producer",
                Config.getString("http.new.default_producer_impl",
                        HTTPProducerImpl.class.getName()));
    }

    // It is entirely possible for the object to become null from the start thread.
    public Boolean isProducing() {
        logger.entry();

        boolean returnValue = false;

        httpProducerLock.readLock().lock();

        try {
            if (httpProducerRunnable != null) {
                returnValue = httpProducerRunnable.getIsRunning();
            }
        } finally {
            httpProducerLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public SageTVProducer getProducer() {
        return httpProducerRunnable;
    }

    /**
     * Stops the producer if it is running.
     *
     * @param wait Set this <i>true</i> if you want to wait for the consumer to completely stop.
     * @return <i>false</i> if blocking the consumer was interrupted.
     */
    protected boolean stopProducing(boolean wait) {
        logger.entry();

        boolean returnValue = true;

        httpProducerLock.readLock().lock();

        try {
            if (httpProducerRunnable != null && httpProducerRunnable.getIsRunning()) {
                logger.debug("Stopping producer thread...");

                httpProducerRunnable.stopProducing();
                httpProducerThread.interrupt();

                int counter = 0;
                while (httpProducerRunnable.getIsRunning()) {
                    if (counter++ < 5) {
                        logger.debug("Waiting for producer thread to stop...");
                    } else {
                        // It should never take 5 seconds for the producer to stop. This
                        // should make everyone aware that something abnormal is happening.
                        logger.warn("Waiting for producer thread to stop for over {} seconds...", counter);
                    }
                    httpProducerThread.join(1000);
                }
            } else {
                logger.trace("Consumer was not running.");
            }
        } catch (InterruptedException e) {
            logger.trace("'{}' thread was interrupted => {}",
                    Thread.currentThread().getClass().toString(), e);
            returnValue = false;
        } finally {
            httpProducerLock.readLock().unlock();
        }


        return logger.exit(returnValue);
    }

    @Override
    public void stopEncoding() {
        logger.entry();

        stopProducing(false);
        super.stopEncoding();

        logger.exit();
    }

    // Stop the capture device from streaming and stop the encoder threads.
    public void stopDevice() {
        stopEncoding();
    }
}
