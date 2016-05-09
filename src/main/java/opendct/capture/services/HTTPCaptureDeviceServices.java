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
import opendct.producer.HTTPProducer;
import opendct.producer.NIOHTTPProducerImpl;
import opendct.producer.SageTVProducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class HTTPCaptureDeviceServices {
    private final Logger logger = LogManager.getLogger(HTTPCaptureDeviceServices.class);

    private URL httpURL[];
    private HTTPProducer httpProducerRunnable = null;
    private Thread httpProducerThread = null;
    private final ReentrantReadWriteLock httpProducerLock = new ReentrantReadWriteLock(true);

    /**
     * Start receiving HTTP content from a single or list of URL's.
     * <p/>
     * All provided URL's are assumed to be the same content. They should be provided in order of
     * preference.
     *
     * @param encoderName  This is the name of the network encoder calling this method. This is used
     *                     for naming the thread.
     * @param httpProducer   This is the producer to be used to receive the HTTP stream.
     * @param sageTVConsumer This is the consumer to be used to write the accumulated data from this
     *                       producer.
     * @param httpURL        This is the URL sources of the stream. This must be a minimum of one
     *                       URL. Additional URL's can be provided for failover.
     * @return <i>true</i> if the producer was able to start.
     */
    public boolean startProducing(String encoderName,
                                     HTTPProducer httpProducer,
                                     SageTVConsumer sageTVConsumer,
                                     URL... httpURL) {

        logger.entry(httpProducer, sageTVConsumer, httpURL);

        boolean returnValue = false;
        
        //In case we left the last producer running.
        if (!stopProducing(true)) {
            logger.warn("Waiting for producer thread to exit was interrupted.");
            return logger.exit(false);
        }

        httpProducerLock.writeLock().lock();

        try {
            this.httpURL = httpURL;

            httpProducerRunnable = httpProducer;
            httpProducerRunnable.setConsumer(sageTVConsumer);
            httpProducerRunnable.setSourceUrls(httpURL);

            httpProducerThread = new Thread(httpProducerRunnable);
            httpProducerThread.setName(httpProducerRunnable.getClass().getSimpleName() + "-" + httpProducerThread.getId() + ":" + encoderName);
            httpProducerThread.start();

            returnValue = true;
        } catch (IOException e) {
            logger.error("Unable to open the URL '{}'. => ", httpURL, e);
            returnValue = false;
        } catch (Exception e) {
            logger.error("startProducing created an unexpected exception => ", e);
                returnValue = false;
        } finally {
            httpProducerLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    /**
     * Get a new HTTP producer per the configuration.
     *
     * @return A new HTTP producer.
     */
    public HTTPProducer getNewHTTPProducer(String propertiesDeviceParent) {
        return Config.getHTTProducer(
                propertiesDeviceParent + "http.producer",
                Config.getString("http.new.default_producer",
                        NIOHTTPProducerImpl.class.getName()));
    }

    /**
     * Is the producer running?
     *
     * @return <i>true</i> if the producer is still running.
     */
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
    public boolean stopProducing(boolean wait) {
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

    public URL[] getHttpURL() {
        return httpURL;
    }

    public HTTPProducer getHttpProducerRunnable() {
        return httpProducerRunnable;
    }

    public Thread getHttpProducerThread() {
        return httpProducerThread;
    }
}
