
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

package opendct;

import opendct.consumer.buffers.FFmpegCircularBuffer;
import opendct.consumer.buffers.FFmpegCircularBufferNIO;
import opendct.consumer.buffers.SeekableCircularBuffer;
import opendct.consumer.buffers.SeekableCircularBufferNIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Random;

public final class CircularBufferNIOTest {
    private static final Logger logger = LogManager.getLogger(CircularBufferNIOTest.class);

    @DataProvider
    private static Object[][] getBufferPattern() {
        Object returnObject[][] = new Object[3][3];

        for (int i = 0; i < returnObject.length; i++) {
            returnObject[i][0] = (i + 5) * 1024 * 1024;
            returnObject[i][1] = (int)((int)returnObject[i][0] * 3.6 + i);
            returnObject[i][2] = (int)((int)returnObject[i][0] / 55.3 + i);
        }

        return returnObject;
    }


    @Test(groups = { "buffer", "byteArray" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testArrayBufferIntegrity(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBufferNIO seekableCircularBuffer = new SeekableCircularBufferNIO(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        byte readData[] = new byte[dataSize];

        int readPosition = 0;
        int dataWritten = 0;

        while(true) {
            if (dataWritten + addIncrement < dataSize) {
                seekableCircularBuffer.write(writeData, dataWritten, addIncrement);
                dataWritten += addIncrement;
            } else {
                // Don't worry about the remaining random data.
                break;
            }

            readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);
        }

        for (int i = 0; i < dataWritten; i++) {
            assert writeData[i] == readData[i] : "At index " + i + ": " + writeData[i] + " != " + readData[i];
        }
    }

    @Test(groups = { "buffer", "byteBuffer" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testByteBufferIntegrity(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBufferNIO seekableCircularBuffer = new SeekableCircularBufferNIO(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        ByteBuffer readData = ByteBuffer.allocateDirect(dataSize);

        int readPosition = 0;
        int dataWritten = 0;

        while(true) {
            if (dataWritten + addIncrement < dataSize) {
                seekableCircularBuffer.write(writeData, dataWritten, addIncrement);
                dataWritten += addIncrement;
            } else {
                // Don't worry about the remaining random data.
                break;
            }

            readPosition += seekableCircularBuffer.read(readData);
        }

        readData.flip();

        for (int i = 0; i < dataWritten; i++) {
            byte newByte = readData.get();
            assert writeData[i] == newByte : "At index " + i + ": " + writeData[i] + " != " + newByte;
        }
    }

    @Test(groups = { "buffer", "byteArray" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testArrayBufferFull(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBufferNIO seekableCircularBuffer = new SeekableCircularBufferNIO(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        byte readData[] = new byte[dataSize];
        int readPosition = 0;

        int dataWritten = 0;


        while(true) {
            if (dataWritten + addIncrement < dataSize) {
                seekableCircularBuffer.write(writeData, dataWritten, addIncrement);
                dataWritten += addIncrement;
            } else {
                // Don't worry about the remaining random data.
                break;
            }

            // Stop reading about half-way through the buffer.
            if (readPosition < (bufferSize / 2)) {
                readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);
            }
        }

        int filledReadPosition = readPosition;

        while (seekableCircularBuffer.readAvailable() > 0) {
            readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);

            // This will directly trigger queue cleanup. Currently the only other thing that will
            // trigger it is a write.
            seekableCircularBuffer.processQueue();
        }

        for (int i = 0; i < readPosition; i++) {
            assert writeData[i] == readData[i] : "At index " + i + ": " + writeData[i] + " != " + readData[i] + " buffer was filled at index " + filledReadPosition;
        }
    }

    @Test(groups = { "buffer", "byteBuffer" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testByteBufferFull(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBufferNIO seekableCircularBuffer = new SeekableCircularBufferNIO(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        ByteBuffer readData = ByteBuffer.allocateDirect(dataSize);
        int readPosition = 0;

        int dataWritten = 0;


        while(true) {
            if (dataWritten + addIncrement < dataSize) {
                seekableCircularBuffer.write(writeData, dataWritten, addIncrement);
                dataWritten += addIncrement;
            } else {
                // Don't worry about the remaining random data.
                break;
            }

            // Stop reading about half-way through the buffer.
            if (readPosition < (bufferSize / 2)) {
                readPosition += seekableCircularBuffer.read(readData);
            }
        }

        int filledReadPosition = readPosition;

        while (seekableCircularBuffer.readAvailable() > 0) {
            readPosition += seekableCircularBuffer.read(readData);

            // This will directly trigger queue cleanup. Currently the only other thing that will
            // trigger it is a write.
            seekableCircularBuffer.processQueue();
        }

        readData.flip();

        for (int i = 0; i < readPosition; i++) {
            byte newByte = readData.get();
            assert writeData[i] == newByte : "At index " + i + ": " + writeData[i] + " != " + newByte + " buffer was filled at index " + filledReadPosition;
        }
    }

    @Test(groups = { "buffer", "byteArray" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testArrayBufferGrow(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBufferNIO seekableCircularBuffer = new SeekableCircularBufferNIO(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        byte readData[] = new byte[dataSize];
        int readPosition = 0;

        int dataWritten = 0;

        seekableCircularBuffer.setNoWrap(true);

        while(true) {
            if (dataWritten + addIncrement < dataSize) {
                seekableCircularBuffer.write(writeData, dataWritten, addIncrement);
                dataWritten += addIncrement;
            } else {
                // Don't worry about the remaining random data.
                break;
            }

            // Stop reading about half-way through the buffer.
            if (readPosition < (bufferSize / 2)) {
                readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);
            }
        }

        seekableCircularBuffer.setNoWrap(false);

        int filledReadPosition = readPosition;

        while (seekableCircularBuffer.readAvailable() > 0) {
            readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);

            // This will directly trigger queue cleanup. Currently the only other thing that will
            // trigger it is a write.
            seekableCircularBuffer.processQueue();
        }

        for (int i = 0; i < readPosition; i++) {
            assert writeData[i] == readData[i] : "At index " + i + ": " + writeData[i] + " != " + readData[i] + " buffer was filled at index " + filledReadPosition;
        }
    }

    @Test(groups = { "buffer", "byteBuffer" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testByteBufferGrow(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBufferNIO seekableCircularBuffer = new SeekableCircularBufferNIO(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        ByteBuffer readData = ByteBuffer.allocateDirect(dataSize);
        int readPosition = 0;

        int dataWritten = 0;

        seekableCircularBuffer.setNoWrap(true);

        while(true) {
            if (dataWritten + addIncrement < dataSize) {
                seekableCircularBuffer.write(writeData, dataWritten, addIncrement);
                dataWritten += addIncrement;
            } else {
                // Don't worry about the remaining random data.
                break;
            }

            // Stop reading about half-way through the buffer.
            if (readPosition < (bufferSize / 2)) {
                readPosition += seekableCircularBuffer.read(readData);
            }
        }

        seekableCircularBuffer.setNoWrap(false);

        int filledReadPosition = readPosition;

        while (seekableCircularBuffer.readAvailable() > 0) {
            readPosition += seekableCircularBuffer.read(readData);

            // This will directly trigger queue cleanup. Currently the only other thing that will
            // trigger it is a write.
            seekableCircularBuffer.processQueue();
        }

        readData.flip();

        for (int i = 0; i < readPosition; i++) {
            byte newByte = readData.get();
            assert writeData[i] == newByte : "At index " + i + ": " + writeData[i] + " != " + newByte + " buffer was filled at index " + filledReadPosition;
        }
    }

    @Test(groups = { "buffer", "byteArray" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testArrayBufferFFmpegSeeking(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        FFmpegCircularBufferNIO seekableCircularBuffer = new FFmpegCircularBufferNIO(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        byte readData[] = new byte[dataSize];
        int readPosition = 0;

        int seekActions[] = new int[]{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        // We need to copy the complete buffer into the read buffer since we are going to be seeking
        // around and could miss a section. We are looking for the reads to not actually change this
        // copy to be considered a success.
        System.arraycopy(writeData, 0, readData, 0, writeData.length);

        int dataWritten = 0;
        int seekAction = 0;

        while(true) {
            if (dataWritten + addIncrement < dataSize) {
                seekableCircularBuffer.write(writeData, dataWritten, addIncrement);
                dataWritten += addIncrement;
            } else {
                // Don't worry about the remaining random data.
                break;
            }

            if (seekAction++ == 9) {
                seekAction = 0;
            }

            // Stop reading about half-way through the buffer.
            if (dataWritten < (addIncrement * 7)) {
                readPosition += seekableCircularBuffer.read(readData, readPosition, Math.min(readData.length - readPosition, addIncrement));
            } else if (seekAction == 0 && seekActions[seekAction] <= 3) {
                logger.trace("FFmpeg Seek 0: Set the read index to a specific index relative to the total number bytes ever placed in the buffer.");
                readPosition = (int)seekableCircularBuffer.seek(0, readPosition - (addIncrement * 3));
                logger.trace("readPosition = {}", readPosition);
                seekActions[seekAction]++;
            } else if (seekAction == 1 && seekActions[seekAction] <= 3) {
                logger.trace("FFmpeg Seek 1: Seek the read index relative to the current read index. Seek nowhere.");
                readPosition = (int)seekableCircularBuffer.seek(1, 0);
                logger.trace("readPosition = {}", readPosition);
                seekActions[seekAction]++;
            } else if (seekAction == 2 && seekActions[seekAction] <= 3) {
                logger.trace("FFmpeg Seek 1: Seek the read index relative to the current read index.");
                readPosition = (int)seekableCircularBuffer.seek(1, -1 * addIncrement);
                logger.trace("readPosition = {}", readPosition);
                seekActions[seekAction]++;
            } else if (seekAction == 3 && seekActions[seekAction] <= 3) {
                logger.trace("FFmpeg Seek 2: Seek the read index relative to the total available bytes. Seek to end.");
                readPosition = (int)seekableCircularBuffer.seek(2, 0);
                logger.trace("readPosition = {}", readPosition);
                seekActions[seekAction]++;
            } else if (seekAction == 4 && seekActions[seekAction] <= 3) {
                logger.trace("FFmpeg Seek 2: Seek the read index relative to the total available bytes.");
                readPosition = (int)seekableCircularBuffer.seek(2, -2 * addIncrement);
                logger.trace("readPosition = {}", readPosition);
                seekActions[seekAction]++;
            } else if (seekAction == 5 && seekActions[seekAction] <= 3) {
                logger.trace("FFmpeg Seek 65536: Get total available bytes since the start of the buffer.");
                // This is an index.
                long totalBytesAvailable = seekableCircularBuffer.seek(65536, 0) - 1;
                // This is a length.
                long totalBytesIndex = seekableCircularBuffer.totalBytesAvailable();

                assert totalBytesAvailable == totalBytesIndex : "totalBytesAvailable - 1: " + totalBytesAvailable + " != totalBytesIndex: " + totalBytesIndex + " at read index " + readPosition;
                seekActions[seekAction]++;
            } else {
                seekActions[6]++;
                readPosition += seekableCircularBuffer.read(readData, readPosition, Math.min(readData.length - readPosition, addIncrement));
            }

            // Occasionally we will trigger an invalid seek. This re-establishes the current read
            // position. If the invalid seek causes something else to fail, we will still fail the
            // test due to the exception.
            if (readPosition == -1) {
                readPosition = (int)seekableCircularBuffer.totalBytesReadIndex();
            }
        }

        while (seekableCircularBuffer.readAvailable() > 0) {
            readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);
        }

        for (int i = 0; i < 6; i++) {
            assert seekActions[i] == 4 : "Seek action for index " + i + " did not execute at least 4 times. The test needs to be fixed.";
        }

        for (int i = 0; i < dataWritten; i++) {
            assert writeData[i] == readData[i] : "At index " + i + ": " + writeData[i] + " != " + readData[i] + ".";
        }
    }

    public byte[] generateByteData(int length) {
        byte data[] = new byte[length];
        Random random = new Random(length);

        random.nextBytes(data);

        return data;
    }

    public ByteBuffer generateByteBufferData(int length) {
        ByteBuffer data = ByteBuffer.allocateDirect(length);
        byte putData[] = generateByteData(length);

        data.put(putData);
        data.flip();

        return data;
    }

}
