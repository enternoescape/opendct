import opendct.consumer.buffers.SeekableCircularBuffer;
import org.junit.Test;

import javax.swing.plaf.synth.SynthTextAreaUI;
import java.io.Console;
import java.nio.ByteBuffer;
import java.util.Random;

public class CircularBufferTest {
    int bufferSize = 1048576;
    // So we don't feed the buffer something that fits in a perfect way.
    int dataSize = (int)(bufferSize * 2.2);
    // This will create a little over 5 passes of data that will always end up crossing the end of
    // the buffer in an uneven way most of the time.
    int addIncrement = (int)(bufferSize / 5.3);


    @Test
    public void testBufferIntegrity() throws InterruptedException {
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

    @Test
    public void testBufferFull() throws InterruptedException {
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

        int stoppedReadPosition = readPosition;

        while (seekableCircularBuffer.readAvailable() > 0) {
            readPosition += seekableCircularBuffer.read(readData, readPosition, bufferSize);
        }

        for (int i = 0; i < readPosition; i++) {
            assert writeData[i] == readData[i] : "At index " + i + ": " + writeData[i] + " != " + readData[i] + " buffer was filled at index " + stoppedReadPosition;
        }
    }

    public byte[] generateByteData(int length) {
        byte data[] = new byte[length];
        Random random = new Random(bufferSize);

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
