package opendct;

import opendct.consumer.buffers.FFmpegCircularBuffer;
import opendct.consumer.buffers.SeekableCircularBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.*;
import java.nio.ByteBuffer;
import java.util.Random;

public final class CircularBufferTest {
    private static final Logger logger = LogManager.getLogger(CircularBufferTest.class);

    @DataProvider
    private static Object[][] getBufferPattern() {
        Object returnObject[][] = new Object[3][3];

        for (int i = 0; i < returnObject.length; i++) {
            returnObject[i][0] = (i + 5) * 1024 * 1024;
            returnObject[i][1] = (int)((int)returnObject[i][0] * 2.2 + i);
            returnObject[i][2] = (int)((int)returnObject[i][0] / 5.3 + i);
        }

        return returnObject;
    }


    @Test(groups = { "buffer", "byteArray" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testArrayBufferIntegrity(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBuffer seekableCircularBuffer = new SeekableCircularBuffer(bufferSize);
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
        SeekableCircularBuffer seekableCircularBuffer = new SeekableCircularBuffer(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        ByteBuffer readData = ByteBuffer.allocate(dataSize);

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
        SeekableCircularBuffer seekableCircularBuffer = new SeekableCircularBuffer(bufferSize);
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
        }

        for (int i = 0; i < readPosition; i++) {
            assert writeData[i] == readData[i] : "At index " + i + ": " + writeData[i] + " != " + readData[i] + " buffer was filled at index " + filledReadPosition;
        }
    }

    @Test(groups = { "buffer", "byteBuffer" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testByteBufferFull(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        SeekableCircularBuffer seekableCircularBuffer = new SeekableCircularBuffer(bufferSize);
        byte writeData[] = generateByteData(dataSize);
        ByteBuffer readData = ByteBuffer.allocate(dataSize);
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
        }

        readData.flip();

        for (int i = 0; i < readPosition; i++) {
            byte newByte = readData.get();
            assert writeData[i] == newByte : "At index " + i + ": " + writeData[i] + " != " + newByte + " buffer was filled at index " + filledReadPosition;
        }
    }

    @Test(groups = { "buffer", "byteArray" }, dataProvider = "getBufferPattern", threadPoolSize = 3)
    public void testArrayBufferFFmpegSeekingWriteBarrierViolation(int bufferSize, int dataSize, int addIncrement) throws InterruptedException {
        FFmpegCircularBuffer seekableCircularBuffer = new FFmpegCircularBuffer(bufferSize);
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
            } else if (readPosition % 10 == 0) {
                System.out.print("FFmpeg Seek 0");
                seekableCircularBuffer.seek(0, 0);
            } else if (readPosition % 20 == 0) {
                System.out.print("FFmpeg Seek 1");
                seekableCircularBuffer.seek(1, 0);
            } else if (readPosition % 30 == 0) {
                System.out.print("FFmpeg Seek 2");
                seekableCircularBuffer.seek(2, 0);
            } else if (readPosition % 40 == 0) {
                System.out.print("FFmpeg Seek 65536");
                seekableCircularBuffer.seek(65536, 0);
            }
        }

        int filledReadPosition = readPosition;

        while (seekableCircularBuffer.readAvailable() > 0) {
            readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);
        }

        for (int i = 0; i < readPosition; i++) {
            assert writeData[i] == readData[i] : "At index " + i + ": " + writeData[i] + " != " + readData[i] + " buffer was filled at index " + filledReadPosition;
        }
    }

    public byte[] generateByteData(int length) {
        byte data[] = new byte[length];
        Random random = new Random(length);

        random.nextBytes(data);

        return data;
    }

    public ByteBuffer generateByteBufferData(int length) {
        ByteBuffer data = ByteBuffer.allocate(length);
        byte putData[] = generateByteData(length);

        data.put(putData);
        data.flip();

        return data;
    }

}
