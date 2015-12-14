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

package opendct.consumer.buffers;

import opendct.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class CFQNIOBuffer {
    private final Logger logger = LogManager.getLogger(CFQNIOBuffer.class);

    private ByteBuffer storageBuffer = ByteBuffer.allocate(
            Config.getInteger("producer.cfq_nio.buffer_size", 1328000));

    // You can increase this to lower CPU consumption at the expense of live streaming performance.
    private int minimumTransferSize =
            Config.getInteger("producer.cfq_nio.min_transfer_size", 8192);

    private volatile boolean overflowing = false;
    private CountDownLatch storageBufferReady = new CountDownLatch(1);

    // This will make sure that reads happen in a fair order instead of one thread potentially
    // hogging synchronize for too long.
    private static final ReentrantLock storageBufferLock =
            new ReentrantLock(true);

    public boolean canNio() {
        return true;
    }

    public int getBytesRemaining() {
        return storageBuffer.position();
    }

    public void write(ByteBuffer putBuffer) {
        storageBufferLock.lock();
        try {
            if (storageBuffer.position() + putBuffer.remaining() > storageBuffer.capacity()) {
                storageBuffer.flip();
                storageBuffer.compact();
            }

            if (storageBuffer.position() + putBuffer.remaining() < storageBuffer.capacity()) {
                storageBuffer.put(putBuffer);
                overflowing = false;
            } else {
                if (!overflowing) {
                    logger.warn("CFQ NIO buffers has buffered {} bytes of data and cannot add the" +
                                    " requested {} bytes of data. It is dropping bytes.",
                            storageBuffer.position(), putBuffer.remaining());
                    overflowing = true;
                }
            }

            if (storageBuffer.position() > minimumTransferSize) {
                storageBufferReady.countDown();
            }
        } finally {
            storageBufferLock.unlock();
        }
    }

    public void write(byte putBuffer[], int offset, int length) {
        storageBufferLock.lock();
        try {
            if (storageBuffer.position() + length > storageBuffer.capacity()) {
                storageBuffer.flip();
                storageBuffer.compact();
            }

            if (storageBuffer.position() + length < storageBuffer.capacity()) {
                storageBuffer.put(putBuffer, offset, length);
                overflowing = false;
            } else {
                if (!overflowing) {
                    logger.warn("CFQ NIO buffers has buffered {} bytes of data and cannot add the" +
                                    "requested {} bytes of data. It is dropping bytes.",
                            storageBuffer.position(), length);
                    overflowing = true;
                }
            }

            if (storageBuffer.position() > minimumTransferSize) {
                storageBufferReady.countDown();
            }
        } finally {
            storageBufferLock.unlock();
        }
    }

    public void read(ByteBuffer returnBuffer) throws InterruptedException {
        logger.entry(returnBuffer);

        storageBufferReady.await();

        storageBufferLock.lock();

        try {
            storageBuffer.flip();

            if (storageBuffer.remaining() > returnBuffer.remaining()) {
                ByteBuffer slice = storageBuffer.slice();
                slice.limit(returnBuffer.remaining());
                while (slice.hasRemaining()) {
                    returnBuffer.put(slice);
                }

                // This keeps the receive buffers in sync.
                storageBuffer.position(storageBuffer.position() + slice.position());
            } else {

                while (storageBuffer.hasRemaining()) {
                    returnBuffer.put(storageBuffer);
                }

                storageBufferReady = new CountDownLatch(1);
            }

            // Moves all of the unread data still in the buffers to the beginning and sets the position to the number of
            // bytes not read. We can continue writing even if all of the available data was not consumed.
            storageBuffer.compact();
        } finally {
            storageBufferLock.unlock();
        }
    }

    public int read(byte[] returnBytes, int offset) throws InterruptedException {
        logger.entry();

        int maxLength;

        storageBufferReady.await();

        storageBufferLock.lock();

        try {
            storageBuffer.flip();

            maxLength = returnBytes.length - offset;

            if (maxLength > storageBuffer.remaining()) {
                maxLength = storageBuffer.remaining();
                storageBufferReady = new CountDownLatch(1);
            }

            storageBuffer.get(returnBytes, offset, maxLength);

            storageBuffer.compact();

            return logger.exit(maxLength);
        } finally {
            storageBufferLock.unlock();
        }
    }
}
