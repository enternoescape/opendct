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

import opendct.consumer.SageTVConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class HTTPProducerImpl implements HTTPProducer {
    private final Logger logger = LogManager.getLogger(HTTPProducerImpl.class);

    private AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean interrupted = false;

    private HttpURLConnection httpURLConnection = null;
    private URL currentURL = null;
    private URL availableURL[] = new URL[0];
    private int selectedURL = 0;

    private final Object receivedLock = new Object();
    private volatile long bytesReceived = 0;

    private SageTVConsumer sageTVConsumer = null;
    private byte localBuffer[] = new byte[32768];

    public synchronized void setSourceUrls(URL... urls) throws IOException {
        if (urls.length == 0) {
            throw new IOException("The connection for HTTP producer cannot process and empty array.");
        }

        selectURL(urls, false);
    }

    private void selectURL(URL urls[], boolean isThread) throws IOException {
        logger.entry(urls, isThread);

        if (!isThread) {
            if (running.get()) {
                throw new IOException("The connection for HTTP producer cannot be changed while the thread is running.");
            }
        }

        int lastURL = selectedURL;
        if (lastURL >= urls.length) {
            lastURL = urls.length - 1;
            selectedURL = 0;
        } else {
            selectedURL += 1;
        }

        while(true) {
            try {
                setSourceUrl(urls[selectedURL], isThread);
                break;
            } catch (IOException e) {
                logger.error("Unable to connect to the URL '{}' => ", urls[selectedURL], e);
            }

            if (selectedURL++ == lastURL) {
                throw new IOException("Unable to connect to any of the provided addresses.");
            }

            if (selectedURL == urls.length) {
                selectedURL = 0;
            }
        }

        availableURL = urls;
        currentURL = availableURL[selectedURL];
    }

    private void setSourceUrl(URL url, boolean isThread) throws IOException {
        logger.entry(url, isThread);

        if (!isThread) {
            if (running.get()) {
                throw new IOException("The connection for HTTP producer cannot be changed while the thread is running.");
            }
        }

        logger.debug("Connecting to source using the URL '{}'", url);

        httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setRequestMethod("GET");
        httpURLConnection.connect();

        currentURL = url;

        logger.exit();
    }

    public boolean getIsRunning() {
        return false;
    }

    public synchronized void setConsumer(SageTVConsumer sageTVConsumer) throws IOException {
        if (running.get()) {
            throw new IOException("The consumer cannot be changed while the thread is running.");
        }

        this.sageTVConsumer = sageTVConsumer;
    }

    public int getPacketsLost() {
        return 0;
    }

    public long getPackets() {
        synchronized (receivedLock) {
            return bytesReceived;
        }
    }

    public void stopProducing() {
        try {
            httpURLConnection.disconnect();
        } catch (Exception e) {

        }
    }

    public boolean isStalled() {
        synchronized (receivedLock) {
            return false;
        }
    }

    private boolean isInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            interrupted = true;
        }

        return interrupted;
    }

    public void run() {
        if (running.getAndSet(true)) {
            logger.warn("The producer is already running.");
            throw new IllegalThreadStateException("The HTTP producer is already running.");
        }

        logger.info("Producer thread is running.");

        interrupted = false;

        // We could be doing channel scanning that doesn't need this kind of prioritization.
        if (Thread.currentThread().getPriority() != Thread.MIN_PRIORITY) {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        }

        BufferedInputStream inputStream = null;
        int readBytes = 0;

        // Keep re-connecting if the connection is interrupted until the producer is told to stop.
        while (!isInterrupted()) {
            while (!isInterrupted() && inputStream == null) {
                try {
                    inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
                } catch (IOException e) {
                    logger.error("There was a problem getting a stream => ", e);
                    inputStream = null;
                    try {
                        selectURL(availableURL, true);
                    } catch (IOException e0) {
                        logger.warn("Unable to re-connect to any of the available addresses. Waiting 250ms before the next attempt.");
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException e1) {
                            logger.debug("Producer was interrupted waiting to retry HTTP connection => ", e);
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            while (!isInterrupted()) {
                try {
                    if (inputStream != null) {
                        readBytes = inputStream.read(localBuffer, 0, localBuffer.length);

                        synchronized (receivedLock) {
                            bytesReceived += readBytes;
                        }

                        if (readBytes > 0) {
                            sageTVConsumer.write(localBuffer, 0, readBytes);
                        } else {
                            logger.info("We have reached the end of the stream. Stopping thread.");
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (IOException e) {
                    inputStream = null;
                }
            }
        }

        logger.info("Producer thread has stopped.");
        running.set(false);
    }

    public URL getSource() {
        return null;
    }

    public URL[] getSources() {
        return availableURL;
    }
}
