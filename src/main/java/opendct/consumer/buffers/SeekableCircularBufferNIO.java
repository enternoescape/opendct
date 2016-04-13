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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class SeekableCircularBufferNIO {
    private final Logger logger = LogManager.getLogger(SeekableCircularBufferNIO.class);

    private int maxOverflowBytes;
    protected int capacity;
    protected volatile ByteBuffer buffer;
    protected volatile ByteBuffer readBuffer;
    protected volatile ByteBuffer writeBuffer;
    protected volatile byte[] writeInterBuffer = new byte[32768];
    protected volatile int writeIndex = 0;
    protected volatile int readIndex = 0;
    protected volatile int writePasses = 0;
    protected volatile int readPasses = 0;

    // These are only used to permanently expand the buffer while we are not allowed to wrap.
    private volatile int maxBufferSize;
    private volatile int resizeBufferIncrement;

    private LinkedBlockingDeque<byte[]> overflowQueue = new LinkedBlockingDeque<>();
    private AtomicInteger bytesOverflow = new AtomicInteger(0);
    private AtomicInteger bytesLost = new AtomicInteger(0);
    private boolean overflowToQueue = false;
    private boolean overflow = false;

    private volatile boolean noWrap = false;
    private volatile boolean closed = false;

    // These are in the order they should always be used if more than one needs to be used.
    private final Object readMonitor = new Object();
    private final Object writeLock = new Object();
    protected final Object readLock = new Object();
    protected final Object rwPassLock = new Object();

    /**
     * Create a new seekable circular buffer.
     *
     * @param bufferSize This is the static size of the buffer.
     */
    public SeekableCircularBufferNIO(int bufferSize) {
        buffer = ByteBuffer.allocateDirect(bufferSize);
        readBuffer = buffer.duplicate();
        writeBuffer = buffer.duplicate();
        capacity = buffer.capacity();
        maxBufferSize = bufferSize * 2;
        resizeBufferIncrement = bufferSize;
        maxOverflowBytes = bufferSize * 4;
    }

    /**
     * Create a new seekable circular buffer.
     *
     * @param buffer This is an already allocated buffer.
     */
    public SeekableCircularBufferNIO(ByteBuffer buffer) {
        this.buffer = buffer;
        readBuffer = buffer.duplicate();
        writeBuffer = buffer.duplicate();
        capacity = buffer.capacity();
        maxBufferSize = capacity * 2;
        resizeBufferIncrement = capacity;
        maxOverflowBytes = capacity * 4;
    }

    /**
     * Clears the all indexes and re-opens the buffer.
     * <p/>
     * This should be used to reset the buffer without re-initializing a new buffer.
     */
    public void clear() {
        //logger.entry();
        synchronized (writeLock) {
            synchronized (readLock) {
                if (capacity != resizeBufferIncrement) {
                    buffer = ByteBuffer.allocateDirect(resizeBufferIncrement);
                    readBuffer = buffer.duplicate();
                    writeBuffer = buffer.duplicate();
                    capacity = buffer.capacity();
                }

                writeIndex = 0;
                readIndex = 0;
                writePasses = 0;
                readPasses = 0;
                bytesOverflow.set(0);
                bytesLost.set(0);
                closed = false;
                noWrap = false;
            }
        }
        //logger.exit();
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setNoWrap(boolean noWrap) {
        this.noWrap = noWrap;
    }

    public boolean isNoWrap() {
        return noWrap;
    }

    public int getCurrentBufferSize() {
        return capacity;
    }

    public int getBufferMinSize() {
        return resizeBufferIncrement;
    }

    public int getBufferMaxSize() {
        return maxBufferSize;
    }

    public void waitForBytes() throws InterruptedException {
        synchronized (readMonitor) {
            while (readIndex == writeIndex && !closed) {
                readMonitor.wait(500);
            }

            readMonitor.notifyAll();
        }
    }

    public void writeBlocked(ByteBuffer bytes) throws ArrayIndexOutOfBoundsException, InterruptedException {
        int length = bytes.remaining();

        synchronized (readMonitor) {
            while (!closed && writeAvailable() - length <= 0) {
                readMonitor.wait(100);
            }
        }

        write(bytes);
    }

    public void writeBlocked(byte bytes[], int offset, int length) throws ArrayIndexOutOfBoundsException, InterruptedException {
        synchronized (readMonitor) {
            while (!closed && writeAvailable() - length <= 0) {
                readMonitor.wait(100);
            }
        }

        write(bytes, offset, length);
    }

    public void write(ByteBuffer bytes) throws ArrayIndexOutOfBoundsException {
        int length = bytes.remaining();

        // This technically shouldn't be happening.
        if (length == 0) {
            return;
        }

        // Once the buffer is closed, we turn off writing.
        if (closed) {
            return;
        }

        if (length > capacity) {
            throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot write more data than the buffer is able to allocate."));
        }

        synchronized (writeLock) {
            int writeAvailable = writeAvailable();

            if (writeAvailable - length <= 0) {
                if (noWrap && capacity < maxBufferSize) {
                    synchronized (readLock) {
                        ByteBuffer newBuffer = ByteBuffer.allocateDirect(capacity + resizeBufferIncrement);

                        logger.warn("The buffer is being expanded from {} bytes to {} bytes.", capacity, newBuffer.capacity());

                        buffer.limit(capacity).position(0);
                        newBuffer.put(buffer);
                        buffer = newBuffer;
                        readBuffer = buffer.duplicate();
                        writeBuffer = buffer.duplicate();
                        capacity = buffer.capacity();

                        logger.info("The buffer has been expanded.");
                    }

                    internalWrite(bytes);
                    return;
                }

                if (!overflowToQueue) {
                    logger.warn("The buffer has {} bytes left to be read, has only {} bytes left for writing and {} bytes cannot be added. Deferring bytes to queue buffer.", readAvailable(), writeAvailable, length);
                    overflowToQueue = true;
                }

                // Enable the queue to back up to 4 times the buffer size. On a system with 20
                // capture devices and a 7MB buffer, this potentially adds up to 560MB in RAM just
                // for the buffer if things get really backed up. The JVM should be able to handle
                // this kind of growth without crashing. Also this is not a typical situation.
                if (bytesOverflow.get() < maxOverflowBytes && overflowQueue.size() < Integer.MAX_VALUE) {
                    // Store overflowing bytes in double-ended queue.
                    byte[] queueBytes = new byte[length];
                    bytes.get(queueBytes, 0, length);
                    overflowQueue.addLast(queueBytes);

                    bytesOverflow.getAndAdd(length);
                } else if (!overflow) {
                    logger.warn("The buffer has {} bytes left to be read, has only {} bytes left for writing and {} bytes cannot be added. The queue buffer is full at {} bytes.", readAvailable(), writeAvailable, length, bytesOverflow.get());
                    overflow = true;
                    bytesLost.addAndGet(length);
                } else {
                    bytesLost.addAndGet(length);
                }

                synchronized (readMonitor) {
                    readMonitor.notifyAll();
                }

                return;
            } else if (overflowToQueue) {

                if (overflowQueue.size() < Integer.MAX_VALUE) {
                    // Store recently added data in double-ended queue.
                    byte[] queueBytes = new byte[length];
                    bytes.get(queueBytes, 0, length);
                    overflowQueue.addLast(queueBytes);
                    bytesOverflow.addAndGet(length);
                }

                processQueue();

                return;
            }

            internalWrite(bytes);
        }
    }

    /**
     * Writes data into the buffer.
     * <p/>
     * You cannot write more data into the buffer than the total size of the buffer.
     *
     * @param bytes The byte array containing data to be written into to the buffer.
     * @param offset The offset within the array to start copying data.
     * @param length The number of bytes to copy starting at the offset.
     * @throws ArrayIndexOutOfBoundsException If you try to write more data than the total length of
     *                                        the buffer.
     */
    public void write(byte bytes[], int offset, int length) throws ArrayIndexOutOfBoundsException {

        // This technically shouldn't be happening.
        if (length == 0) {
            return;
        }

        // Once the buffer is closed, we turn off writing.
        if (closed) {
            return;
        }

        if (length > capacity) {
            throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot write more data than the buffer is able to allocate."));
        }

        synchronized (writeLock) {
            int writeAvailable = writeAvailable();

            if (writeAvailable - length <= 0) {
                if (noWrap && capacity < maxBufferSize) {
                    synchronized (readLock) {
                        ByteBuffer newBuffer = ByteBuffer.allocateDirect(capacity + resizeBufferIncrement);

                        logger.warn("The buffer is being expanded from {} bytes to {} bytes.", capacity, newBuffer.capacity());

                        buffer.limit(capacity).position(0);
                        newBuffer.put(buffer);
                        buffer = newBuffer;
                        readBuffer = buffer.duplicate();
                        writeBuffer = buffer.duplicate();
                        capacity = buffer.capacity();

                        logger.info("The buffer has been expanded.");
                    }

                    internalWrite(bytes, offset, length);
                    return;
                }

                if (!overflowToQueue) {
                    logger.warn("The buffer has {} bytes left to be read, has only {} bytes left for writing and {} bytes cannot be added. Deferring bytes to queue buffer.", readAvailable(), writeAvailable, length);
                    overflowToQueue = true;
                }

                // Enable the queue to back up to 4 times the buffer size. On a system with 20
                // capture devices and a 7MB buffer, this potentially adds up to 560MB in RAM just
                // for the buffer if things get really backed up. The JVM should be able to handle
                // this kind of growth without crashing. Also this is not a typical situation.
                if (bytesOverflow.get() < maxOverflowBytes && overflowQueue.size() < Integer.MAX_VALUE) {
                    // Store overflowing bytes in double-ended queue.
                    byte[] queueBytes = new byte[length];
                    System.arraycopy(bytes, offset, queueBytes, 0, length);
                    overflowQueue.addLast(queueBytes);

                    bytesOverflow.getAndAdd(length);
                } else if (!overflow) {
                    logger.warn("The buffer has {} bytes left to be read, has only {} bytes left for writing and {} bytes cannot be added. The queue buffer is full at {} bytes.", readAvailable(), writeAvailable, length, bytesOverflow.get());
                    overflow = true;
                    bytesLost.addAndGet(length);
                } else {
                    bytesLost.addAndGet(length);
                }

                synchronized (readMonitor) {
                    readMonitor.notifyAll();
                }

                return;
            } else if (overflowToQueue) {

                if (overflowQueue.size() < Integer.MAX_VALUE) {
                    // Store recently added data in double-ended queue.
                    byte[] queueBytes = new byte[length];
                    System.arraycopy(bytes, offset, queueBytes, 0, length);
                    overflowQueue.addLast(queueBytes);
                    bytesOverflow.addAndGet(length);
                }

                processQueue();

                return;
            }

            internalWrite(bytes, offset, length);
        }
    }

    public boolean processQueue() {
        int recoveredBytes = 0;
        boolean returnValue = false;

        synchronized (writeLock) {
            while (true) {
                if (overflowQueue.size() == 0) {
                    if (recoveredBytes > 0) {
                        logger.info("Recovered {} bytes from the queue buffer.", recoveredBytes);
                        bytesOverflow.addAndGet(-recoveredBytes);
                        returnValue = true;
                    }

                    // Reset log warnings.
                    overflowToQueue = false;
                    overflow = false;
                    bytesOverflow.set(0);

                    if (bytesLost.get() > 0) {
                        logger.info("Lost {} bytes that could not be queued in the queue buffer.", bytesLost.get());
                        bytesLost.set(0);
                    }

                    break;
                }

                byte[] overflowBytes = overflowQueue.removeFirst();
                int writeAvailable = writeAvailable();

                if (overflowBytes.length > writeAvailable) {
                    // If the next array is larger than what will fit into the array, put it
                    // back in the front of the queue.
                    overflowQueue.addFirst(overflowBytes);
                    break;
                }

                internalWrite(overflowBytes, 0, overflowBytes.length);

                recoveredBytes += overflowBytes.length;
            }
        }

        return returnValue;
    }

    private void internalWrite(ByteBuffer bytes) {
        // This is always called within a write lock, there is no need to have any synchronization
        // within this method.

        int length = bytes.remaining();

        if (writeIndex + length > capacity) {
            int end = capacity - writeIndex;
            //logger.trace("bytes.length = {}, offset = {}, buffer.length = {}, writeIndex = {}, end = {}", bytes.length, offset, buffer.length, writeIndex, end);
            writeBuffer.limit(writeIndex + end).position(writeIndex);
            ByteBuffer slice = bytes.slice();
            slice.limit(end);
            writeBuffer.put(slice);
            bytes.position(bytes.position() + end);

            int writeRemaining = length - end;
            if (writeRemaining > 0) {
                //logger.trace("bytes.length = {}, end = {}, buffer.length = {}, writeRemaining = {}", bytes.length, end, buffer.length, writeRemaining);
                writeBuffer.limit(writeRemaining).position(0);
                writeBuffer.put(bytes);
            }

            synchronized (rwPassLock) {
                writeIndex = writeRemaining;
                writePasses += 1;
            }
        } else {
            writeBuffer.limit(writeIndex + length).position(writeIndex);
            writeBuffer.put(bytes);
            writeIndex += length;

        }

        synchronized (readMonitor) {
            readMonitor.notifyAll();
        }
    }

    private void internalWrite(byte bytes[], int offset, int length) {
        if (writeInterBuffer.length < length) {
            writeInterBuffer = new byte[length * 2];
        }

        if (writeIndex + length > capacity) {
            int end = capacity - writeIndex;
            //logger.trace("bytes.length = {}, offset = {}, buffer.length = {}, writeIndex = {}, end = {}", bytes.length, offset, buffer.length, writeIndex, end);
            writeBuffer.limit(writeIndex + end).position(writeIndex);
            writeBuffer.put(bytes, offset, end);

            int writeRemaining = length - end;
            if (writeRemaining > 0) {
                //logger.trace("bytes.length = {}, end = {}, buffer.length = {}, writeRemaining = {}", bytes.length, end, buffer.length, writeRemaining);
                writeBuffer.limit(writeRemaining).position(0);
                writeBuffer.put(bytes, offset + end, writeRemaining);
            }

            synchronized (rwPassLock) {
                writeIndex = writeRemaining;
                writePasses += 1;
            }
        } else {
            writeBuffer.limit(writeIndex + length).position(writeIndex);
            writeBuffer.put(bytes, offset, length);
            writeIndex += length;

        }

        synchronized (readMonitor) {
            readMonitor.notifyAll();
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
        //logger.entry(bytes.length, offset, length);

        // This technically shouldn't be happening.
        if (length == 0) {
            return 0;
        }

        if (length > capacity) {
            throw new IndexOutOfBoundsException("You cannot read more data than the buffer is able to allocate.");
        }

        int returnLength = 0;

        waitForBytes();

        synchronized (readLock) {
            returnLength = Math.min(length, readAvailable());

            /*if (logger.isTraceEnabled()) {
                long readAvailable = readAvailable();
                logger.trace("{} bytes are currently available with a length of {} bytes being requested.", readAvailable, length);
            }*/
            if (readIndex + returnLength > capacity) {
                int end = capacity - readIndex;
                //logger.trace("buffer.length = {}, readIndex = {}, bytes.length = {}, offset = {}, end = {}", buffer.length, readIndex, bytes.length, offset, end);
                readBuffer.limit(readIndex + end).position(readIndex);
                readBuffer.get(bytes, offset, end);

                int readRemaining = returnLength - end;

                if (readRemaining > 0) {
                    //logger.trace("buffer.length = {}, bytes.length = {}, offset = {}, end = {}, readRemaining = {}", buffer.length, bytes.length, offset, end, readRemaining);
                    readBuffer.limit(readRemaining).position(0);
                    readBuffer.get(bytes, offset + end, readRemaining);
                }

                synchronized (rwPassLock) {
                    readIndex = readRemaining;
                    readPasses += 1;
                }
            } else {
                readBuffer.limit(readIndex + returnLength).position(readIndex);
                readBuffer.get(bytes, offset, returnLength);
                readIndex += returnLength;
            }
        }

        /*if (logger.isTraceEnabled()) {
            long readAvailable = readAvailable();
            logger.trace("{} bytes remain available. Returning {} bytes.", readAvailable, returnLength);
        }*/
        //return logger.exit(returnLength);
        return returnLength;
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
        //logger.entry(outBuffer);

        // This technically shouldn't be happening.
        if (outBuffer.remaining() == 0) {
            return 0;
        }

        int length = Math.min(capacity, outBuffer.remaining());

        int returnLength = 0;

        waitForBytes();

        synchronized (readLock) {
            returnLength = Math.min(length, readAvailable());

            /*if (logger.isTraceEnabled()) {
                long readAvailable = readAvailable();
                logger.trace("{} bytes are currently available with a length of {} bytes being requested.", readAvailable, length);
            }*/

            if (readIndex + returnLength > capacity) {
                int end = capacity - readIndex;
                //logger.trace("buffer.length = {}, readIndex = {}, outBuffer.remaining = {}, end = {}", buffer.length, readIndex, outBuffer.remaining(), end);
                readBuffer.limit(readIndex + end).position(readIndex);
                outBuffer.put(readBuffer);

                int readRemaining = returnLength - end;

                if (readIndex > 0) {
                    //logger.trace("buffer.length = {}, outBuffer.remaining = {}, readRemaining = {}", buffer.length, outBuffer.remaining(), readRemaining);
                    readBuffer.limit(readRemaining).position(0);
                    outBuffer.put(readBuffer);
                }

                synchronized (rwPassLock) {
                    readIndex = readRemaining;
                    readPasses += 1;
                }
            } else {
                readBuffer.limit(readIndex + returnLength).position(readIndex);
                outBuffer.put(readBuffer);
                readIndex += returnLength;
            }
        }

        /*if (logger.isTraceEnabled()) {
            long readAvailable = readAvailable();
            logger.trace("{} bytes remain available. Returning {} bytes.", readAvailable, returnLength);
        }*/
        //return logger.exit(returnLength);
        return returnLength;
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
        //logger.entry();

        int returnValue;

        waitForBytes();

        synchronized (readLock) {

            returnValue = readBuffer.get(readIndex++) & 0xff;

            if (readIndex >= capacity) {
                synchronized (rwPassLock) {
                    readIndex = 0;
                    readPasses += 1;
                }
            }

        }

        //return logger.exit(returnValue);
        return returnValue;
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
        //logger.entry(index);

        if (index < 0) {
            return;
        }

        synchronized (readLock) {
            synchronized (rwPassLock) {
                int newIndex = Math.abs((int)(index % capacity));
                int newPasses = Math.abs((int)(index / capacity));

                if (newPasses > writePasses || newPasses == writePasses && newIndex > writeIndex || newPasses < writePasses && newIndex < writeIndex) {
                    throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot move the read index beyond the currently available data."));
                }

                readIndex = newIndex;
                readPasses = newPasses;
            }
        }

        //logger.debug("Relative index {} to bytes set read index to actual index {}, read passes {}.", index, readIndex, readPasses);
        logger.exit();
    }

    /**
     * Increments the current read index relative to the current read index and total bytes read
     * from the buffer.
     *
     * @param increment Total number of bytes to increment the read index.
     * @return The actual read index based on the total bytes read that was the result of the
     *         increment.
     */
    public long incrementReadIndexFromStart(long increment) throws IndexOutOfBoundsException {
        //logger.entry(increment);

        long returnValue = -1;

        synchronized (readLock) {
            synchronized (rwPassLock) {
                if (readIndex + increment > capacity) {
                    throw new IndexOutOfBoundsException("You cannot increment the read index to a value greater than the buffer size.");
                }

                long index = ((long)readPasses * (long)capacity) + (long)readIndex + increment;

                int newIndex = Math.abs((int)(index % capacity));
                int newPasses = Math.abs((int)(index / capacity));

                if (newPasses > writePasses || newPasses == writePasses && newIndex > writeIndex || newPasses < writePasses && newIndex < writeIndex) {
                    throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot move the read index beyond the currently available data."));
                }

                readIndex = newIndex;
                readPasses = newPasses;
            }

            returnValue = totalBytesReadIndex();
        }

        //logger.debug("Incremental index {} to bytes read index set read index to actual index {}, read passes {}.", increment, readIndex, readPasses);
        //return logger.exit(returnValue);
        return returnValue;
    }

    /**
     * Increments the current read index relative to the total bytes available to be read from the
     * buffer.
     *
     * @param increment Total number of bytes to increment the read index.
     * @return The actual read index based on the total bytes read that was the result of the
     *         increment.
     */
    public long incrementReadIndexFromEnd(long increment) throws IndexOutOfBoundsException {
        //logger.entry(increment);

        long returnValue = -1;

        synchronized (readLock) {
            synchronized (rwPassLock) {
                if (readIndex + increment > capacity) {
                    throw new IndexOutOfBoundsException("You cannot increment the read index to a value greater than the buffer size.");
                }

                long index = totalBytesAvailable() + increment;

                int newIndex = Math.abs((int)(index % capacity));
                int newPasses = Math.abs((int)(index / capacity));

                if (newPasses > writePasses || newPasses == writePasses && newIndex > writeIndex || newPasses < writePasses && newIndex < writeIndex) {
                    throw logger.throwing(new ArrayIndexOutOfBoundsException("You cannot move the read index beyond the currently available data."));
                }

                readIndex = newIndex;
                readPasses = newPasses;
            }

            returnValue = totalBytesReadIndex();
        }

        //logger.debug("Incremental index {} to bytes read index set read index to actual index {}, read passes {}.", increment, readIndex, readPasses);
        //return logger.exit(returnValue);
        return returnValue;
    }

    /**
     * Get how much free space there is available for writing. The writer always returns one byte
     * smaller than the total buffer size.
     *
     * @return The number of bytes available for writing.
     */
    public int writeAvailable() {
        //logger.entry();
        int available;
        int limitIndex;

        synchronized (rwPassLock) {
            limitIndex = readIndex;

            if (noWrap) {
                available = (capacity - 1) - writeIndex;
            } else if (limitIndex > writeIndex) {
                available = limitIndex - (writeIndex - 1);
            } else {
                available = (capacity - 1) - (writeIndex - limitIndex);
            }

            /*if (logger.isDebugEnabled() && available <= 0) {
                logger.debug("writeAvailable() = {}", available);
            }*/
        }

        //return logger.exit(available);
        return available;
    }

    /**
     * Get how many bytes are available to be read from the current index.
     *
     * @return The number of bytes available to be read.
     */
    public int readAvailable() {
        //logger.entry();
        int available;

        synchronized (rwPassLock) {
            if (readIndex <= writeIndex) {
                available = writeIndex - readIndex;
            } else {
                available = capacity - (readIndex - writeIndex);
            }
        }

        //return logger.exit(available);
        return available;
    }

    public long totalBytesReadIndex() {
        //logger.entry();
        long available;

        synchronized (rwPassLock) {
            available = totalBytesAvailable() - readAvailable();
        }

        //return logger.exit(available);
        return available;
    }

    public long totalBytesAvailable() {
        //logger.entry();
        int available;

        synchronized (rwPassLock) {
            if (readIndex <= writeIndex) {
                available = (writePasses * capacity) + writeIndex;
            } else {
                available = (writePasses * capacity) + (capacity - writeIndex);
            }
        }

        //return logger.exit(available);
        return available;
    }
}


