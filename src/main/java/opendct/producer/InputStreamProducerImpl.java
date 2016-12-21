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

package opendct.producer;

import opendct.consumer.SageTVConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

public class InputStreamProducerImpl implements InputStreamProducer {
    private final Logger logger = LogManager.getLogger(InputStreamProducerImpl.class);

    private volatile boolean stop = false;
    private volatile boolean running = false;
    private AtomicLong bytesStreamed = new AtomicLong(0);

    private SageTVConsumer consumer;
    private byte localBuffer[] = new byte[262144];
    private InputStream stream;

    @Override
    public void setInputStream(InputStream stream) {
        this.stream = stream;
    }

    @Override
    public boolean getIsRunning() {
        return running;
    }

    @Override
    public void setConsumer(SageTVConsumer sageTVConsumer) throws IOException {
        this.consumer = sageTVConsumer;
    }

    @Override
    public int getPacketsLost() {
        return 0;
    }

    @Override
    public long getPackets() {
        return bytesStreamed.get();
    }

    @Override
    public void stopProducing() {
        stop = true;
    }

    @Override
    public void run() {
        if (stream == null) {
            logger.error("Stream is null.");
            return;
        }

        int bytesRead = 0;
        boolean endOfStream = false;
        try {
            running = true;
            while (!stop) {
                try {
                    bytesRead = stream.read(localBuffer);

                    // EOF
                    if (bytesRead == -1) {
                        // Prevent the logging from creating lots of entries.
                        if (!endOfStream) {
                            logger.debug("Stream ended.");
                            endOfStream = true;
                        }
                        // If we are ahead of the stream, we should rest a little so hopefully we
                        // will not end up here very quickly.
                        Thread.sleep(1000);
                        // Sometimes end of stream is returned just because nothing else is in the
                        // pipe and not because the stream has truly ended. When the consumer should
                        // actually stop, it will be told to directly.
                        continue;
                    } else if (endOfStream) {
                        logger.debug("Stream resumed.");
                        endOfStream = false;
                    }

                    if (bytesRead > 0) {
                        consumer.write(localBuffer, 0, bytesRead);
                    }
                } catch (Exception e) {

                }
            }
        } finally {
            running = false;
        }
    }
}
