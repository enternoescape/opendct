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
import opendct.producer.InputStreamProducer;
import opendct.producer.InputStreamProducerImpl;
import opendct.producer.SageTVProducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InputStreamCaptureDeviceServices {
    private final static Logger logger = LogManager.getLogger(InputStreamCaptureDeviceServices.class);

    private InputStreamProducer inputStreamProducerRunnable = null;
    private Thread inputStreamProducerThread = null;
    private final ReentrantReadWriteLock inputStreamProducerLock = new ReentrantReadWriteLock(true);

    /**
     * Start streaming data from the provided InputStream.
     *
     * @param encoderName This is the name of the network encoder calling this method. This is used
     *                    for naming the thread.
     * @param inputStreamProducer his is the producer to be used to receive the InputStream.
     * @param sageTVConsumer This is the consumer to be used to write the accumulated data from this
     *                       producer.
     * @param inputStream This is the InputStream that will provide all of the streaming data for
     *                    this producer.
     * @return <code>true</code> if the producer was able to start.
     */
    public boolean startProducing(String encoderName,
                                  InputStreamProducer inputStreamProducer,
                                  SageTVConsumer sageTVConsumer,
                                  InputStream inputStream) {

        logger.entry(encoderName, inputStreamProducer, sageTVConsumer, inputStream);

        boolean returnValue;

        //In case we left the last producer running.
        if (!stopProducing(true)) {
            logger.warn("Waiting for producer thread to exit was interrupted.");
            return logger.exit(false);
        }

        inputStreamProducerLock.writeLock().lock();

        try {
            inputStreamProducerRunnable = inputStreamProducer;
            inputStreamProducerRunnable.setConsumer(sageTVConsumer);
            inputStreamProducerRunnable.setInputStream(inputStream);

            inputStreamProducerThread = new Thread(inputStreamProducerRunnable);
            inputStreamProducerThread.setName(inputStreamProducerRunnable.getClass().getSimpleName() + "-" + inputStreamProducerThread.getId() + ":" + encoderName);
            inputStreamProducerThread.start();

            returnValue = true;
        } catch (Exception e) {
            logger.error("startProducing created an unexpected exception => ", e);
            returnValue = false;
        } finally {
            inputStreamProducerLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    /**
     * Get a new InputStream producer per the configuration.
     *
     * @return A new InputStream producer.
     */
    public InputStreamProducer getNewInputStreamProducer(String propertiesDeviceParent) {
        return Config.getInputStreamProducer(
                propertiesDeviceParent + "input_stream.producer",
                Config.getString("input_stream.new.default_producer",
                        InputStreamProducerImpl.class.getName()));
    }

    /**
     * Is the producer running?
     *
     * @return <i>true</i> if the producer is still running.
     */
    public boolean isProducing() {
        logger.entry();

        boolean returnValue = false;

        inputStreamProducerLock.readLock().lock();

        try {
            if (inputStreamProducerRunnable != null) {
                returnValue = inputStreamProducerRunnable.getIsRunning();
            }
        } finally {
            inputStreamProducerLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public SageTVProducer getProducer() {
        return inputStreamProducerRunnable;
    }

    public InputStreamProducer getInputStreamProducerRunnable() {
        return inputStreamProducerRunnable;
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

        inputStreamProducerLock.readLock().lock();

        try {
            if (inputStreamProducerRunnable != null && inputStreamProducerRunnable.getIsRunning()) {
                logger.debug("Stopping producer thread...");

                inputStreamProducerRunnable.stopProducing();
                inputStreamProducerThread.interrupt();

                int counter = 0;
                while (inputStreamProducerRunnable.getIsRunning()) {
                    if (counter++ < 5) {
                        logger.debug("Waiting for producer thread to stop...");
                    } else {
                        // It should never take 5 seconds for the producer to stop. This
                        // should make everyone aware that something abnormal is happening.
                        logger.warn("Waiting for producer thread to stop for over {} seconds...", counter);
                    }
                    inputStreamProducerThread.join(1000);
                }
            } else {
                logger.trace("Consumer was not running.");
            }
        } catch (InterruptedException e) {
            logger.trace("'{}' thread was interrupted => {}",
                    Thread.currentThread().getClass().toString(), e);
            returnValue = false;
        } finally {
            inputStreamProducerLock.readLock().unlock();
        }


        return logger.exit(returnValue);
    }
}
