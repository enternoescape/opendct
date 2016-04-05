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
import org.bytedeco.javacpp.BytePointer;

import java.nio.ByteBuffer;

public class FFmpegCircularBufferNIO extends SeekableCircularBufferNIO {
    private final Logger logger = LogManager.getLogger(FFmpegCircularBufferNIO.class);

    /**
     * Create a new seekable circular buffer.
     *
     * @param bufferSize This is the static size of the buffer.
     */
    public FFmpegCircularBufferNIO(int bufferSize) {
        super(bufferSize);
    }

    /**
     * Read data from the buffer into the provided JavaCPP BytePointer
     * <p/>
     * This method will block until data is available. It will attempt to fill the buffer up to the
     * requested <b>length</b> with the available data, but if there is not enough data immediately
     * available, it will only return what is available and returns the number of bytes actually
     * returned. It should always return with something unless there is an exception.
     *
     * @param bytePtr This is the provided native byte pointer from JavaCPP.
     * @param offset  This is the offset within <b>bytes[]</b> to begin copying data.
     * @param length  This is the maximum amount of data to be copied into <b>bytes[]</b>.
     * @return The number of bytes actually read into the provided byte array.
     * @throws InterruptedException      This exception will happen if the thread calling this method is
     *                                   interrupted.
     * @throws IndexOutOfBoundsException <b>offset</b> + <b>length</b> cannot exceed
     *                                   <b>bytes.length</b> and <b>length</b> cannot exceed the
     *                                   total size of the ring buffer.
     */
    public int read(BytePointer bytePtr, int offset, int length) throws InterruptedException, IndexOutOfBoundsException {
        //logger.entry(bytePtr, offset, length);

        // This technically shouldn't be happening.
        if (length == 0) {
            return 0;
        }

        if (length > buffer.capacity()) {
            throw new IndexOutOfBoundsException("You cannot read more data than the buffer is able to allocate.");
        }

        int returnLength = 0;

        waitForBytes();

        synchronized (readLock) {
            returnLength = Math.min(length, readAvailable());
            ByteBuffer returnBuffer = bytePtr.position(offset).limit(returnLength).asByteBuffer();

            /*if (logger.isTraceEnabled()) {
                logger.trace("{} bytes are currently available with a length of {} bytes being requested.", readAvailable(), length);
            }*/

            if (readIndex + returnLength > buffer.capacity()) {
                int end = buffer.capacity() - readIndex;
                //logger.debug("offset = {}, buffer.length = {}, readIndex = {}, , end = {}", offset, buffer.length, readIndex, end);

                readBuffer.position(readIndex).limit(readIndex + end);
                returnBuffer.put(returnBuffer);

                int readRemaining = returnLength - end;

                if (readIndex > 0) {
                    //logger.debug("offset = {}, buffer.length = {},  readRemaining = {}", offset, buffer.length, readRemaining);
                    readBuffer.position(0).limit(readRemaining);
                    returnBuffer.put(readBuffer);
                }

                synchronized (rwPassLock) {
                    readIndex = readRemaining;
                    readPasses += 1;
                }
            } else {
                readBuffer.position(readIndex).limit(readIndex + returnLength);
                returnBuffer.put(readBuffer);
                readIndex += returnLength;
            }
        }

        /*if (logger.isTraceEnabled()) {
            logger.trace("{} bytes remain available. Returning {} bytes.", readAvailable(), returnLength);
        }*/
        //return logger.exit(returnLength);
        return returnLength;
    }

    /**
     * A defined way of seeking through data used by the JavaCPP library in conjunction with FFmpeg.
     *
     * @param wence  This appears to be an enum returned from JavaCPP to seeking in specific ways.
     * @param offset This is the offset or an absolute position depending on the value of
     *               <b>wence</b>.
     * @return This returns either the current index or -1 if there was a problem.
     */
    public long seek(int wence, long offset) {
        //logger.entry(wence, offset);

        long returnValue = -1;

        logger.debug("Seek: wence = {}, offset = {}, readIndex = {}", wence, offset, readIndex);

        switch (wence) {
            case 0:
                // Set the read index to a specific index relative to the total number bytes ever
                // placed in the buffer.
                try {
                    if (offset >= 0) {
                        setReadIndex(offset);
                        returnValue = offset;
                    }
                } catch (IndexOutOfBoundsException e) {
                    logger.warn("Seek: Requested a read index that is not available => ", e);
                }
                break;
            case 1:
                // Seek the read index relative to the current read index.
                try {
                    returnValue = incrementReadIndexFromStart(offset);
                } catch (IndexOutOfBoundsException e) {
                    logger.warn("Seek: Requested a read index relative to current read index that is not available => ", e);
                }
                break;
            case 2:
                // Seek the read index relative to the total available bytes.
                try {
                    returnValue = incrementReadIndexFromEnd(offset);
                } catch (IndexOutOfBoundsException e) {
                    logger.warn("Seek: Requested a read index relative to the end of the stream that is not available => ", e);
                }
                break;
            case 65536:
                // Get total available bytes since the start of writing to the buffer.
                returnValue = totalBytesAvailable() + 1;
                break;
            default:
                logger.warn("Seek: The wence value {} is not being handled.", wence);
                break;
        }


        logger.debug("Seek: wence = {}, offset = {}, readIndex = {}, returnValue = {}", wence, offset, readIndex, returnValue);
        //return logger.exit(returnValue);
        return returnValue;
    }
}
