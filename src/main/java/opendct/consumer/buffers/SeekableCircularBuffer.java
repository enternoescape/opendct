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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class SeekableCircularBuffer {
    private final Logger logger = LogManager.getLogger(SeekableCircularBuffer.class);

    private int maxOverflowBytes;
    protected volatile byte buffer[];
    protected volatile int writeIndex = 0;
    protected volatile int readIndex = 0;
    protected volatile int writePasses = 0;
    protected volatile int readPasses = 0;

    private AtomicInteger bytesOverflow = new AtomicInteger(0);
    private AtomicInteger bytesLost = new AtomicInteger(0);
    private boolean overflow = false;

    private volatile boolean closed = false;
    private final Object writeLock = new Object();
    private final Object readMonitor = new Object();
    protected final Object readLock = new Object();

    /**
     * Create a new seekable circular buffer.
     *
     * @param bufferSize This is the static size of the buffer.
     */
    public SeekableCircularBuffer(int bufferSize) {
        buffer = new byte[bufferSize];
        maxOverflowBytes = bufferSize;
    }

    /**
     * Clears the all indexes and re-opens the buffer.
     * <p/>
     * This should be used to reset the buffer without re-initializing a new buffer.
     */
    public void clear() {
        logger.entry();
        synchronized (writeLock) {
            synchronized (readLock) {
                writeIndex = 0;
                readIndex = 0;
                writePasses = 0;
                readPasses = 0;
                bytesOverflow.set(0);
                bytesLost.set(0);
                closed = false;
            }
        }
        logger.exit();
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    public void waitForBytes() throws InterruptedException {
        synchronized (readMonitor) {
            while (readIndex == writeIndex && !closed) {
                readMonitor.wait(500);
            }
        }
    }

    /**
     * Writes data into the buffer.
     * <p/>
     * You cannot write more data into the buffer than the total size of the buffer.
     *
     * @param bytes
     * @param offset
     * @param length
     * @throws ArrayIndexOutOfBoundsException If you try to write more data than the total length of
     *                                        the buffer.
     */
    public void write(byte bytes[], int offset, int length) throws ArrayIndexOutOfBoundsException {
        logger.entry(bytes.length, offset, length);

        // This technically shouldn't be happening.
        if (length == 0) {
            return;
        }

        if (length > buffer.length) {
            throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot write more data than the buffer is able to allocate."));
        }

        synchronized (writeLock) {
            int writeAvailable = writeAvailable();
            if (writeAvailable <= 0) {
                if (!overflow) {
                    logger.warn("The buffer contains {} bytes, has only {} bytes left for writing and {} bytes cannot be added.", readAvailable(), writeAvailable, length);
                    overflow = true;
                }
                bytesOverflow.getAndAdd(length);

                synchronized (readMonitor) {
                    readMonitor.notifyAll();
                }

                return;
            } else if (overflow && writeAvailable > 0) {
                logger.warn("The buffer has lost {} bytes.", bytesOverflow.get());

                /*if (bytesOverflow.get() > 0) {
                    logger.debug("Dumping the overflow bytes from the fifo buffer into the circular buffer.");

                }*/

                bytesOverflow.set(0);
                overflow = false;
            }

            if (writeIndex + length > buffer.length) {
                int end = buffer.length - writeIndex;
                logger.trace("bytes.length = {}, offset = {}, buffer.length = {}, writeIndex = {}, end = {}", bytes.length, offset, buffer.length, writeIndex, end);
                System.arraycopy(bytes, offset, buffer, writeIndex, end);

                writeIndex = length - end;
                if (writeIndex > 0) {
                    logger.trace("bytes.length = {}, end = {}, buffer.length = {}, writeIndex = {}", bytes.length, end, buffer.length, writeIndex);
                    System.arraycopy(bytes, offset + end, buffer, 0, writeIndex);
                }

                writePasses += 1;
            } else {
                System.arraycopy(bytes, offset, buffer, writeIndex, length);
                writeIndex += length;

            }

            synchronized (readMonitor) {
                readMonitor.notifyAll();
            }
        }

    }

    /**
     * Read data from the buffer into the provided byte array.
     * <p/>
     * This method will block until data is available. It will attempt to fill the buffer up to the
     * requested <b>length</b> with the available data, but if there is not enough data immediately
     * available, it will only return what is available and returns the number of bytes actually
     * returned.
     *
     * @param bytes  This is the provided byte array to receive the data.
     * @param offset This is the offset within <b>bytes[]</b> to begin copying data.
     * @param length This is the maximum amount of data to be copied into <b>bytes[]</b>.
     * @return The number of bytes actually read into the provided byte array.
     * @throws InterruptedException      This exception will happen if the thread calling this method is
     *                                   interrupted.
     * @throws IndexOutOfBoundsException <b>offset</b> + <b>length</b> cannot exceed
     *                                   <b>bytes.length</b> and <b>length</b> cannot exceed the
     *                                   total size of the ring buffer.
     */
    public int read(byte bytes[], int offset, int length) throws InterruptedException, IndexOutOfBoundsException {
        logger.entry(bytes, offset, length);

        // This technically shouldn't be happening.
        if (length == 0) {
            return 0;
        }

        if (length > buffer.length) {
            throw new IndexOutOfBoundsException("You cannot read more data than the buffer is able to allocate.");
        }

        int returnLength = 0;

        waitForBytes();

        synchronized (readLock) {
            returnLength = Math.min(length, readAvailable());

            if (logger.isTraceEnabled()) {
                long readAvailable = readAvailable();
                logger.trace("{} bytes are currently available with a length of {} bytes being requested.", readAvailable, length);
            }

            if (readIndex + returnLength > buffer.length) {
                int end = buffer.length - readIndex;
                logger.trace("buffer.length = {}, readIndex = {}, bytes.length = {}, offset = {}, end = {}", buffer.length, readIndex, bytes.length, offset, end);
                System.arraycopy(buffer, readIndex, bytes, offset, end);

                readIndex = returnLength - end;
                if (readIndex > 0) {
                    logger.trace("buffer.length = {}, bytes.length = {}, offset = {}, end = {}, readIndex = {}", buffer.length, bytes.length, offset, end, readIndex);
                    System.arraycopy(buffer, 0, bytes, offset + end, readIndex);
                }

                readPasses += 1;
            } else {
                System.arraycopy(buffer, readIndex, bytes, offset, returnLength);
                readIndex += returnLength;
            }
        }

        if (logger.isTraceEnabled()) {
            long readAvailable = readAvailable();
            logger.trace("{} bytes remain available. Returning {} bytes.", readAvailable, returnLength);
        }
        return logger.exit(returnLength);
    }

    /**
     * Read data from the buffer into the provided ByteBuffer.
     * <p/>
     * This method will block until data is available. It will attempt to fill the buffer up to its
     * limit with the available data, but if there is not enough data immediately available, it will
     * only return what is available and returns the number of bytes actually returned.
     *
     * @param outBuffer This is the provided byte array to receive the data.
     * @return The number of bytes actually read into the provided ByteBuffer.
     * @throws InterruptedException
     */
    public int read(ByteBuffer outBuffer) throws InterruptedException {
        logger.entry(outBuffer);

        // This technically shouldn't be happening.
        if (outBuffer.remaining() == 0) {
            return 0;
        }

        int length = Math.min(buffer.length, outBuffer.remaining());

        int returnLength = 0;

        waitForBytes();

        synchronized (readLock) {
            returnLength = Math.min(length, readAvailable());

            if (logger.isTraceEnabled()) {
                long readAvailable = readAvailable();
                logger.trace("{} bytes are currently available with a length of {} bytes being requested.", readAvailable, length);
            }

            if (readIndex + returnLength > buffer.length) {
                int end = buffer.length - readIndex;
                logger.trace("buffer.length = {}, readIndex = {}, outBuffer.remaining = {}, end = {}", buffer.length, readIndex, outBuffer.remaining(), end);
                outBuffer.put(buffer, readIndex, end);

                readIndex = returnLength - end;
                if (readIndex > 0) {
                    logger.trace("buffer.length = {}, outBuffer.remaining = {}, readIndex = {}", buffer.length, outBuffer.remaining(), readIndex);
                    outBuffer.put(buffer, 0, readIndex);
                }

                readPasses += 1;
            } else {
                outBuffer.put(buffer, readIndex, returnLength);
                readIndex += returnLength;
            }
        }

        if (logger.isTraceEnabled()) {
            long readAvailable = readAvailable();
            logger.trace("{} bytes remain available. Returning {} bytes.", readAvailable, returnLength);
        }
        return logger.exit(returnLength);
    }

    /**
     * Returns an unsigned byte as an integer.
     * <p/>
     * This method blocks until at least one byte is available to be read. It will return one
     * unsigned byte and increment the read index accordingly.
     *
     * @return Returns the value of the current read index.
     * @throws InterruptedException If an interrupt is encountered while waiting for the method to
     *                              return, this will be thrown. If you are not blocking, this will never happen.
     */
    public int read() throws InterruptedException {
        logger.entry();

        int returnValue;

        synchronized (readLock) {

            waitForBytes();

            returnValue = buffer[readIndex++] & 0xff;

            if (readIndex >= buffer.length) {
                readIndex = 0;
                readPasses += 1;
            }

        }

        return logger.exit(returnValue);
    }

    /**
     * This method does not block. The index is relative to the current bytes written. This will
     * return one unsigned byte at the requested index relative to the mark position and will not
     * increment the current read index.
     *
     * @param index Index relative to the total bytes written. This number cannot be negative.
     * @return Returns and unsigned byte as an integer or -1 if you tried to read an index that
     * cannot be read.
     */
    public int read(int index) {
        logger.entry(index);

        int returnValue = -1;

        synchronized (readLock) {
            int internalIndex = ((readPasses * buffer.length) + index) % buffer.length;

            if (index <= totalReadBytes()) {
                int relativeIndex = internalIndex + index;

                if (relativeIndex > buffer.length) {
                    relativeIndex -= buffer.length;
                }

                returnValue = buffer[relativeIndex] & 0xff;
            }
        }

        return logger.exit(returnValue);
    }

    /**
     * Changes the current read index relative to the total written bytes.
     * <p/>
     * This will not allow you to seek further back than the current write index and will not allow
     * you to seek further forward than the current write index. There is no guarantee that you have
     * not seeked into invalid data.
     *
     * @param index Relative index based on the total written bytes in the buffer.
     */
    public void setReadIndex(long index) throws ArrayIndexOutOfBoundsException {
        logger.entry(index);

        synchronized (readLock) {
            int newIndex = (int)(index % buffer.length);
            int newPasses = (int)(index / buffer.length);

            if (newPasses == writePasses && newIndex > writeIndex || newPasses < writePasses && newIndex < writeIndex) {
                throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot move the read index beyond the currently available data."));
            }

            readIndex = newIndex;
            readPasses = newPasses;
        }

        logger.debug("Relative index {} to bytes set read index to actual index {}, read passes {}.", index, readIndex, readPasses);
        logger.exit();
    }

    /**
     * Increments the current read index relative to it's current position without moving the mark
     * index.
     * <p/>
     * A mark must be set or nothing will be changed.
     *
     * @param increment Total number of indexes to increment the read index.
     * @return The actual relative read index that was the result of the increment.
     */
    public int incrementReadIndex(long increment) throws IndexOutOfBoundsException {
        logger.entry(increment);

        int returnValue = -1;

        synchronized (readLock) {
            if (readIndex + increment > buffer.length) {
                throw new IndexOutOfBoundsException("You cannot increment the read index to a value greater than the buffer size.");
            }

            if (readIndex + increment > readAvailable()) {
                throw new IndexOutOfBoundsException("You cannot increment the read index to a relative value greater than the bytes available to be read.");
            }

            long index = ((long)readPasses * (long)buffer.length) + (long)readIndex + increment;

            int newIndex = (int)(index % buffer.length);
            int newPasses = (int)(index / buffer.length);

            if (newPasses == writePasses && newIndex > writeIndex || newPasses < writePasses && newIndex < writeIndex) {
                throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot move the read index beyond the currently available data."));
            }

            readIndex = (int)(index % buffer.length);
            readPasses = (int)(index / buffer.length);

            returnValue = readAvailable();
        }

        logger.debug("Incremental index {} to bytes read index set read index to actual index {}, read passes {}.", increment, readIndex, readPasses);
        return logger.exit(returnValue);
    }

    /**
     * Get how much free space there is available for writing. The writer always returns one byte
     * smaller than the total buffer size.
     *
     * @return The number of bytes available for writing.
     */
    public int writeAvailable() {
        logger.entry();
        int available;
        int limitIndex;

        limitIndex = readIndex;

        if (limitIndex > writeIndex) {
            available = limitIndex - (writeIndex - 1);
        } else {
            available = (buffer.length - 1) - (writeIndex - limitIndex);
        }

        if (logger.isDebugEnabled() && available <= 0) {
            logger.debug("writeAvailable() = {}", available);
        }

        return logger.exit(available);
    }

    /**
     * Get how many bytes are available to be read from the current index.
     *
     * @return The number of bytes available to be read.
     */
    public int readAvailable() {
        logger.entry();
        int available;

        if (readIndex <= writeIndex) {
            available = writeIndex - readIndex;
        } else {
            available = buffer.length - (readIndex - writeIndex);
        }

        return logger.exit(available);
    }

    public long totalReadBytes() {
        return (readPasses * buffer.length) + readAvailable();
    }
}


