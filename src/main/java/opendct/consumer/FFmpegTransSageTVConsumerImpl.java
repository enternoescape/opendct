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

package opendct.consumer;

import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.buffers.FFmpegCircularBufferNIO;
import opendct.consumer.upload.NIOSageTVMediaServer;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.util.ThreadPool;
import opendct.util.Util;
import opendct.video.ccextractor.CCExtractorSrtInstance;
import opendct.video.ffmpeg.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.BytePointer;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class FFmpegTransSageTVConsumerImpl implements SageTVConsumer {
    private final static Logger logger = LogManager.getLogger(FFmpegTransSageTVConsumerImpl.class);

    private final boolean acceptsUploadID = FFmpegConfig.getUploadIdEnabled();

    // This value cannot go any lower than 65536. Lower values result in stream corruption when the
    // RTP packets are larger than the buffer size.
    private final int RW_BUFFER_SIZE = FFmpegConfig.getRwBufferSize();

    // This is the smallest amount of data that will be transferred to the SageTV server.
    private final int minUploadIDTransfer =
            FFmpegConfig.getMinUploadIdTransferSize(RW_BUFFER_SIZE);

    private final int ffmpegThreadPriority = FFmpegConfig.getThreadPriority();

    private int uploadIDPort = FFmpegConfig.getUploadIdPort();

    private final boolean ccExtractorEnabled = FFmpegConfig.getCcExtractor();
    private final boolean ccExtractorAllStreams = FFmpegConfig.getCcExtractorAllStreams();
    private boolean ccExtractorAvailable = false;

    private long initBufferedData = 65536;

    private String currentRecordingQuality;
    private boolean consumeToNull;

    // volatile long is atomic as long as only one thread ever updates it.
    private volatile long bytesStreamed = 0;
    private AtomicBoolean running = new AtomicBoolean(false);
    private boolean streaming = false;
    private String currentChannel = "";
    private String currentEncoderFilename = "";
    private FFmpegWriter currentWriter = null;
    private FFmpegWriter switchWriter = null;
    private FFmpegWriter currentCcWriter = null;
    private FFmpegWriter switchCcWriter = null;
    private int currentUploadID = 0;
    private long stvRecordBufferSize = 0;
    private final Object streamingMonitor = new Object();
    private InetSocketAddress uploadSocketAddress = null;

    int desiredProgram = 0;
    private FFmpegCircularBufferNIO circularBuffer;
    private FFmpegContext ctx;

    public FFmpegTransSageTVConsumerImpl() {
        if (circularBuffer == null) {
            try
            {
                circularBuffer = new FFmpegCircularBufferNIO(FFmpegConfig.getCircularBufferSize());
            }
            catch (Throwable e)
            {
                // Try to free up memory for one more attempt.
                System.gc();
                logger.warn("There was a problem allocating a new buffer. Ran GC => ", e);
                circularBuffer = new FFmpegCircularBufferNIO(FFmpegConfig.getCircularBufferSize());
            }
        } else {
            if (circularBuffer.getBufferMinSize() != FFmpegConfig.getCircularBufferSize()) {
                circularBuffer = new FFmpegCircularBufferNIO(FFmpegConfig.getCircularBufferSize());
            } else {
                circularBuffer.clear();
            }
        }
    }

    @Override
    public void run() {
        if (running.getAndSet(true)) {
            logger.error("FFmpeg Transcoder consumer is already running.");
            return;
        }

        if (circularBuffer == null) {
            circularBuffer = new FFmpegCircularBufferNIO(FFmpegConfig.getCircularBufferSize());
        } else {
            circularBuffer.clear();
        }

        logger.info("FFmpeg Transcoder consumer thread is now running.");

        streaming = false;

        logger.debug("Thread priority is {}.", ffmpegThreadPriority);
        Thread.currentThread().setPriority(ffmpegThreadPriority);

        if (ccExtractorEnabled &&
                stvRecordBufferSize == 0 &&
                currentWriter instanceof FFmpegDirectWriter) {

            ccExtractorAvailable = true;

            try {
                currentCcWriter = new FFmpegCCExtractorWriter(currentEncoderFilename);
            } catch (IOException e) {
                logger.error("Unable to create CCExtractor writer => ", e);
            }
        }

        try {
            ctx = new FFmpegContext(circularBuffer, RW_BUFFER_SIZE, new FFmpegTranscoder());

            ctx.setProgram(desiredProgram);

            FFmpegProfile profile = FFmpegProfileManager.getEncoderProfile(currentRecordingQuality);
            ctx.setEncodeProfile(profile);

            if (!ctx.initTsStream(currentEncoderFilename)) {
                logger.info("Unable to detect any video.");
                return;
            }

            ctx.STREAM_PROCESSOR.initStreamOutput(ctx, currentEncoderFilename, currentWriter, currentCcWriter);

            initBufferedData = ctx.getDetectionBytes();

            ctx.STREAM_PROCESSOR.streamOutput();

        } catch (FFmpegException e) {
            logger.error("Unable to start streaming => ", e);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                logger.debug("FFmpeg Transcoder was interrupted.");
            } else {
                logger.error("FFmpeg Transcoder created an unexpected exception => ", e);
            }
        } finally {
            // Ensure that this always gets returned when it is no longer needed.
            FFmpegTranscoder.returnTranscodePermission(ctx.OPAQUE);

            if (currentWriter != null) {
                currentWriter.closeFile();
            }

            ctx.dispose();

            // This probably needs to be done differently if the class is to be reused.
            //buffers.offer(circularBuffer);
            //circularBuffer = null;

            running.set(false);
            logger.info("FFmpeg Transcoder consumer thread stopped.");
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        if (circularBuffer != null) {
            circularBuffer.write(bytes, offset, length);
        }
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {
        if (circularBuffer != null) {
            circularBuffer.write(buffer);
        }
    }

    @Override
    public void clearBuffer() {
        if (circularBuffer != null) {
            circularBuffer.close();
            circularBuffer.clear();
        }
    }

    @Override
    public void setRecordBufferSize(long bufferSize) {
        stvRecordBufferSize = bufferSize;
    }

    @Override
    public boolean canSwitch() {
        return true;
    }

    @Override
    public boolean getIsRunning() {
        return running.get();
    }

    @Override
    public void stopConsumer() {
        if (ctx != null) {
            ctx.interrupt();
        }
        circularBuffer.close();
    }

    @Override
    public void consumeToNull(boolean consumeToNull) {
        this.consumeToNull = consumeToNull;

        if (consumeToNull) {
            currentWriter = new FFmpegNullWriter();
        }
    }

    @Override
    public long getBytesStreamed() {
        return bytesStreamed;
    }

    @Override
    public boolean acceptsUploadID() {
        return acceptsUploadID;
    }

    @Override
    public boolean acceptsFilename() {
        return true;
    }

    @Override
    public void setEncodingQuality(String encodingQuality) {
        currentRecordingQuality = encodingQuality;
    }

    @Override
    public boolean consumeToUploadID(String filename, int uploadId, InetAddress socketAddress) {
        try {
            uploadSocketAddress = new InetSocketAddress(socketAddress, uploadIDPort);
            currentWriter = new FFmpegUploadIDWriter(uploadSocketAddress, filename, uploadId);
            currentEncoderFilename = filename;
        } catch (IOException e) {
            logger.error("Unable to open '{}' for writing via upload ID {} => ", filename, uploadId, e);
            return false;
        }

        return true;
    }

    @Override
    public boolean consumeToFilename(String filename) {
        try {
            currentWriter = new FFmpegDirectWriter(filename);
            currentEncoderFilename = filename;
        } catch (IOException e) {
            logger.error("Unable to open '{}' for writing => ", filename, e);
            return false;
        }

        return true;
    }

    @Override
    public boolean switchStreamToUploadID(String filename, long bufferSize, int uploadId) {
        if (ctx == null) {
            return false;
        }

        stvRecordBufferSize = bufferSize;

        try {
            switchWriter = new FFmpegUploadIDWriter(uploadSocketAddress, filename, uploadId);

            ctx.STREAM_PROCESSOR.switchOutput(filename, switchWriter, null);

            currentWriter.closeFile();
            currentUploadID = uploadId;
            currentEncoderFilename = filename;
            currentWriter = switchWriter;
        } catch (IOException e) {
            logger.error("Unable to open '{}' for writing via upload ID {} => ", filename, uploadId, e);
            return false;
        } catch (FFmpegException e) {
            logger.error("Unable to SWITCH output to '{}' via upload ID {} => ", filename, uploadId, e);
            return false;
        }

        return true;
    }

    @Override
    public boolean switchStreamToFilename(String filename, long bufferSize) {
        if (ctx == null) {
            return false;
        }

        stvRecordBufferSize = bufferSize;

        try {
            switchWriter = new FFmpegDirectWriter(filename);
            if (ccExtractorAvailable) {
                switchCcWriter = new FFmpegCCExtractorWriter(filename);
            }

            ctx.STREAM_PROCESSOR.switchOutput(filename, switchWriter, switchCcWriter);

            currentWriter.closeFile();
            currentEncoderFilename = filename;
            currentWriter = switchWriter;

        } catch (IOException e) {
            logger.error("Unable to open '{}' for writing => ", filename, e);
            return false;
        } catch (FFmpegException e) {
            logger.error("Unable to SWITCH output to '{}' => ", filename, e);
            return false;
        }

        return true;
    }

    @Override
    public String getEncoderQuality() {
        return currentRecordingQuality;
    }

    @Override
    public String getEncoderFilename() {
        return currentEncoderFilename;
    }

    @Override
    public int getEncoderUploadID() {
        return currentUploadID;
    }

    @Override
    public void setProgram(int program) {
        desiredProgram = program;
    }

    @Override
    public int getProgram() {
        return desiredProgram;
    }

    @Override
    public void setChannel(String channel) {
        currentChannel = channel;
    }

    @Override
    public String getChannel() {
        return currentChannel;
    }

    @Override
    public boolean isStreaming(long timeout) {
        synchronized (streamingMonitor) {
            try {
                streamingMonitor.wait(timeout);
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for consumer to start streaming.");
            }
        }

        return streaming;
    }

    @Override
    public DeviceOption[] getOptions() {
        return FFmpegConfig.getFFmpegTransOptions();
    }

    @Override
    public void setOptions(JsonOption... deviceOptions) throws DeviceOptionException {
        // Device options are re-loaded when the consumer is re-loaded. It would be a very bad idea
        // to change settings immediately while remuxing.
        FFmpegConfig.setOptions(deviceOptions);
    }

    public class FFmpegUploadIDWriter implements FFmpegWriter {
        private ByteBuffer streamBuffer = ByteBuffer.allocateDirect(minUploadIDTransfer * 2);
        protected NIOSageTVMediaServer mediaServer = new NIOSageTVMediaServer();

        private InetSocketAddress uploadSocket;
        private String uploadFilename;
        private int uploadID;

        private long bytesFlushCounter;
        private boolean firstWrite;
        private boolean isFailed;

        public FFmpegUploadIDWriter (InetSocketAddress uploadSocket, String uploadFilename, int uploadID) throws IOException {
            mediaServer.startUpload(uploadSocket, uploadFilename, uploadID);

            this.uploadSocket = uploadSocket;
            this.uploadFilename = uploadFilename;
            this.uploadID = uploadID;

            bytesFlushCounter = 0;
            firstWrite = true;
            isFailed = false;
        }

        @Override
        public void closeFile() {
            try {
                mediaServer.endUpload();
            } catch (IOException e) {
                logger.error("Unable to close the file '{}' upload ID {} => ",
                        uploadFilename, uploadID, e);
            }

            firstWrite = true;
            isFailed = false;
        }

        protected long lastWriteAddress = 0;
        protected int lastWriteCapacity = 0;
        protected long writeAddress = 0;
        protected ByteBuffer writeBuffer = null;

        @Override
        public synchronized int write(BytePointer data, int length) throws IOException {
            if (isFailed)
                return 0;

            if (firstWrite) {
                bytesStreamed = 0;
                firstWrite = false;
            }

            if (length == 0) {
                return length;
            }

            writeAddress = data.address();

            if (writeBuffer == null || writeAddress != lastWriteAddress || lastWriteCapacity < length) {
                writeBuffer = data.position(0).limit(length).asByteBuffer();;
                lastWriteAddress = writeAddress;
                lastWriteCapacity = length;
            } else {
                writeBuffer.limit(length).position(0);
            }

            streamBuffer.put(writeBuffer);

            if (streamBuffer.position() < minUploadIDTransfer) {
                return logger.exit(length);
            }

            streamBuffer.flip();

            int bytesToStream = streamBuffer.remaining();

            try {
                boolean retry = true;
                while (true) {
                    try {
                        if (stvRecordBufferSize > 0) {
                            mediaServer.uploadAutoBuffered(stvRecordBufferSize, streamBuffer);
                        } else {
                            mediaServer.uploadAutoIncrement(streamBuffer);
                        }
                    } catch (IOException e) {
                        if (retry && !isFailed) {
                            retry = false;
                            isFailed = true;
                            try {
                                mediaServer.endUpload();
                            } catch (Exception e1) {
                                logger.debug("Error cleaning up broken connection => {}" + e1.getMessage());
                            }
                            mediaServer.startUpload(uploadSocket, uploadFilename, uploadID, mediaServer.getAutoOffset());
                            isFailed = false;
                            continue;
                        } else {
                            throw e;
                        }
                    }
                    break;
                }

                long currentBytes = bytesStreamed += bytesToStream;

                if (currentBytes > initBufferedData) {
                    synchronized (streamingMonitor) {
                        streamingMonitor.notifyAll();
                    }
                }
            } catch (IOException e) {
                if (isFailed) {
                    logger.error("Unable to re-connect to server. Waiting for SageTV to restart recording...");
                }
                logger.error("Unable to stream '{}' via upload ID {} => ",
                        uploadFilename, uploadID, e);
                bytesToStream = 0;
            } finally {
                streamBuffer.clear();
            }

            return bytesToStream;
        }

        @Override
        public Logger getLogger() {
            return logger;
        }
    }

    public class FFmpegCCExtractorWriter implements FFmpegWriter {
        private CCExtractorSrtInstance ccInstance;
        protected DatagramChannel datagramChannel;
        protected SocketAddress targetAddress;
        protected int portNumber;

        public FFmpegCCExtractorWriter(String filename) throws IOException {
            String customOptions = FFmpegConfig.getCcExtractorCustomOptions();
            StringBuilder paramBuilder = new StringBuilder(1024);

            if (Config.IS_LINUX) {
                paramBuilder.append(" -nobi -stdin");
            } else {
                // UDP is a work around until -stdin option on Windows is fixed.

                portNumber = Config.getFreeRTSPPort(Thread.currentThread().getName());

                // Unfortunately -stdin is not functional, so this is the only option.
                paramBuilder.append(" -nobi -udp ")
                        .append(Inet4Address.getLoopbackAddress().getHostAddress())
                        .append(":")
                        .append(portNumber);

                targetAddress = new InetSocketAddress(Inet4Address.getLoopbackAddress(), portNumber);
                datagramChannel = DatagramChannel.open();
                datagramChannel.connect(targetAddress);
            }

            if (filename.endsWith(".mpg")) {
                paramBuilder.append(" -in=ps ");
            } else {
                paramBuilder.append(" -in=ts ");
            }

            if(ccExtractorAllStreams) {
                paramBuilder.append("-12 ");
            } else {
                paramBuilder.append("-1 ");
            }

            if (!Util.isNullOrEmpty(customOptions)) {
                paramBuilder.append(customOptions).append(" ");
            }

            int extIndex = filename.lastIndexOf(".");
            String baseFilename;

            if (extIndex > 0) {
                baseFilename = filename.substring(0, extIndex);
            } else {
                baseFilename = filename;
            }

            // Create the CCExtractor instance before the recording file so that the .srt files will
            // already exist providing the subtitle option during playback.
            ccInstance = new CCExtractorSrtInstance(paramBuilder.toString(), baseFilename);
        }

        protected long lastWriteAddress = 0;
        protected int lastWriteCapacity = 0;
        protected long writeAddress = 0;
        protected ByteBuffer writeBuffer = null;

        @Override
        public synchronized int write(BytePointer data, int length) throws IOException {

            if (length == 0) {
                return length;
            }

            writeAddress = data.address();

            if (writeBuffer == null || writeAddress != lastWriteAddress || lastWriteCapacity < length) {
                writeBuffer = data.position(0).limit(length).asByteBuffer();;
                lastWriteAddress = writeAddress;
                lastWriteCapacity = length;
            } else {
                writeBuffer.limit(length).position(0);
            }

            int bytesSent = length;

            if (ccInstance != null) {
                bytesSent = 0;

                if (datagramChannel != null) {
                    if (length > 31960) {
                        ByteBuffer slice;
                        int increment;
                        while (writeBuffer.hasRemaining()) {
                            increment = Math.min(31960, writeBuffer.remaining());
                            slice = writeBuffer.slice();
                            slice.limit(increment);
                            writeBuffer.position(writeBuffer.position() + increment);

                            while (slice.hasRemaining() && datagramChannel.isOpen()) {
                                bytesSent += datagramChannel.write(slice);
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    logger.debug("Interrupted while writing to CCExtractor => {}",
                                            e.toString());
                                }
                            }
                        }
                    } else {
                        while (writeBuffer.hasRemaining() && datagramChannel.isOpen()) {
                            bytesSent += datagramChannel.write(writeBuffer);
                        }
                    }
                } else {
                    ccInstance.streamIn(writeBuffer);
                }
            }

            return bytesSent;
        }

        @Override
        public synchronized void closeFile() {
            if (ccInstance != null) {
                ccInstance.setClosed();
            }

            ccInstance = null;

            if (datagramChannel != null) {
                try {
                    datagramChannel.close();
                } catch (IOException e) {
                    logger.debug("Error while closing datagram channel => ", e);
                }

                datagramChannel.socket().close();
                Config.returnFreeRTSPPort(portNumber);
            }
        }

        @Override
        public Logger getLogger() {
            return logger;
        }
    }

    public class FFmpegDirectWriter implements FFmpegWriter {
        private long autoOffset;
        private boolean firstWrite;
        private boolean closed;

        private final ByteBuffer asyncBuffer = ByteBuffer.allocateDirect(RW_BUFFER_SIZE * 2);
        private final int minWrite = RW_BUFFER_SIZE;
        private volatile boolean canWrite = false;

        private Future asyncWriter;
        private FileChannel fileChannel;
        private final String directFilename;
        private final File recordingFile;

        public FFmpegDirectWriter(final String filename) throws IOException {

            fileChannel = FileChannel.open(
                    Paths.get(filename),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE);

            directFilename = filename;
            recordingFile = new File(directFilename);

            autoOffset = 0;
            firstWrite = true;
            closed = false;

            asyncWriter = ThreadPool.submit(new AsyncWriter(), Thread.NORM_PRIORITY, "AsyncWriter", new File(directFilename).getName());
        }

        protected long lastWriteAddress = 0;
        protected int lastWriteCapacity = 0;
        protected long writeAddress = 0;
        protected ByteBuffer writeBuffer = null;

        private long lastFilelength;

        @Override
        public int write(BytePointer data, int length) throws IOException {
            if (closed) {
                return -1;
            }

            if (length == 0) {
                return length;
            }

            if (firstWrite) {
                bytesStreamed = 0;
                firstWrite = false;
                asyncBuffer.clear();
            }

            writeAddress = data.address();

            if (writeBuffer == null || writeAddress != lastWriteAddress || lastWriteCapacity < length) {
                writeBuffer = data.position(0).limit(length).asByteBuffer();;
                lastWriteAddress = writeAddress;
                lastWriteCapacity = length;
            } else {
                writeBuffer.limit(length).position(0);
            }

            if (stvRecordBufferSize > 0 && stvRecordBufferSize < autoOffset + length) {
                ByteBuffer slice = writeBuffer.slice();
                slice.limit((int) (stvRecordBufferSize - autoOffset));

                synchronized (asyncBuffer) {
                    while(canWrite) {
                        try {
                            asyncBuffer.wait(500);
                        } catch (InterruptedException e) {
                            logger.error("Interrupted while waiting for the buffer => ",
                                    directFilename, e);
                            break;
                        }
                    }

                    asyncBuffer.put(slice);
                    asyncBuffer.flip();
                    canWrite = true;
                    asyncBuffer.notifyAll();

                    while(canWrite) {
                        try {
                            asyncBuffer.wait(500);
                        } catch (InterruptedException e) {
                            logger.error("Interrupted while waiting for the buffer => ",
                                    directFilename, e);
                            break;
                        }
                    }

                    data.position((int) (data.position() + (stvRecordBufferSize - autoOffset)));
                    autoOffset = 0;
                }
            }

            synchronized (asyncBuffer) {
                while(canWrite) {
                    try {
                        asyncBuffer.wait(500);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted while waiting for the buffer => ",
                                directFilename, e);
                        break;
                    }
                }

                if (stvRecordBufferSize == 0) {
                    asyncBuffer.put(writeBuffer);

                    int remains = asyncBuffer.remaining();
                    if (remains > minWrite) {
                        return length;
                    }
                } else {
                    asyncBuffer.put(writeBuffer);
                }

                asyncBuffer.flip();
                canWrite = true;
                asyncBuffer.notifyAll();
            }

            return length;
        }

        @Override
        public void closeFile() {
            synchronized (asyncBuffer) {
                while(canWrite) {
                    try {
                        asyncBuffer.wait(500);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted while waiting for the buffer to return" +
                                " for filename '{}' => ", directFilename, e);
                        break;
                    }
                }

                closed = true;
                canWrite = true;
                asyncBuffer.notifyAll();

                while (fileChannel != null) {
                    try {
                        asyncBuffer.wait(500);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted while waiting for the buffer to return" +
                                " for filename '{}' => ", directFilename, e);
                        break;
                    }
                }
            }
        }

        @Override
        public Logger getLogger() {
            return logger;
        }

        public class AsyncWriter implements Runnable {
            private long bytesFlushCounter = 0;

            @Override
            public void run() {
                int bytesWritten;
                int writeBytes;
                long currentBytes;
                long currentBytesStreamed;

                while (true) {
                    synchronized (asyncBuffer) {
                        while (!canWrite) {
                            try {
                                asyncBuffer.wait(500);
                            } catch (InterruptedException e) {
                                logger.error("Interrupted while waiting for the buffer => ",
                                        directFilename, e);
                                break;
                            }
                        }

                        if (closed) {
                            if (fileChannel != null && fileChannel.isOpen()) {
                                logger.info("Closing the file '{}'", directFilename);

                                try {
                                    fileChannel.close();
                                    fileChannel = null;
                                } catch (IOException e) {
                                    logger.error("Unable to close the file '{}' => ",
                                            directFilename, e);
                                }

                                if (ccExtractorAvailable && currentCcWriter != null) {
                                    currentCcWriter.closeFile();
                                    currentCcWriter = switchCcWriter;
                                }
                            }

                            canWrite = false;
                            asyncBuffer.clear();
                            return;
                        }

                        writeBytes = 0;

                        try {
                            while (asyncBuffer.hasRemaining()) {
                                bytesWritten = fileChannel.write(asyncBuffer, autoOffset);
                                autoOffset += bytesWritten;
                                writeBytes += bytesWritten;
                            }
                        } catch (Exception e) {
                            logger.error("File '{}' write failed => ", directFilename, e);

                            if (fileChannel != null && fileChannel.isOpen()) {
                                try {
                                    fileChannel.close();
                                } catch (IOException e0) {
                                    logger.debug("Consumer created an exception while" +
                                            " closing the current file => {}", e0);
                                }
                            }

                            try {
                                fileChannel = FileChannel.open(
                                        Paths.get(directFilename),
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.CREATE);
                            } catch (IOException e0) {
                                logger.error("Unable to re-open file '{}' => ", directFilename, e0);
                            }

                            asyncBuffer.clear();
                            canWrite = false;
                            asyncBuffer.notifyAll();
                            continue;
                        }

                        asyncBuffer.clear();
                        canWrite = false;
                        asyncBuffer.notifyAll();
                    }

                    currentBytes = bytesStreamed += writeBytes;

                    bytesFlushCounter += writeBytes;

                    if (currentBytes > initBufferedData) {
                        synchronized (streamingMonitor) {
                            streamingMonitor.notifyAll();
                        }
                    }
                }
            }
        }
    }

    public class FFmpegNullWriter implements FFmpegWriter {
        boolean firstWrite = true;

        @Override
        public int write(BytePointer data, int length) throws IOException {
            if (firstWrite) {
                bytesStreamed = 0;
                firstWrite = false;
            }

            bytesStreamed += length;

            return length;
        }

        @Override
        public void closeFile() {

        }

        @Override
        public Logger getLogger() {
            return logger;
        }
    }
}
