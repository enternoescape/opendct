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
import opendct.consumer.buffers.FFmpegCircularBuffer;
import opendct.consumer.upload.NIOSageTVUploadID;
import opendct.video.ffmpeg.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class FFmpegTransSageTVConsumerImpl implements SageTVConsumer {
    private final Logger logger = LogManager.getLogger(FFmpegTransSageTVConsumerImpl.class);

    private final boolean acceptsUploadID =
            Config.getBoolean("consumer.ffmpeg.upload_id_enabled", false);

    // We must have at a minimum a 5 MB buffer plus 1MB to catch up. This ensures that if
    // someone changes this setting to a lower value, it will be overridden.
    private final int circularBufferSize =
            (int) Math.max(
                    Config.getInteger("consumer.ffmpeg.circular_buffer_size", 7864320),
                    5617370 + 1123474
            );

    // This is the largest analyze duration allowed. 5,000,000 is the minimum allowed value.
    private final long maxAnalyzeDuration =
            Math.max(
                    Config.getInteger("consumer.ffmpeg.max_analyze_duration", 5000000),
                    5000000
            );

    // This value cannot go any lower than 65536. Lower values result in stream corruption when the
    // RTP packets are larger than the buffer size.
    private final int RW_BUFFER_SIZE =
            Math.max(Config.getInteger("consumer.ffmpeg.rw_buffer_size", 262144), 65536);

    // This is the smallest amount of data that will be transferred to the SageTV server.
    private final int minUploadIDTransfer =
            Math.max(
                    Config.getInteger("consumer.ffmpeg.min_upload_id_transfer_size", 20680),
                    RW_BUFFER_SIZE
            );

    // This is the smallest amount of data that will be flushed to the SageTV server.
    private final int minDirectFlush =
            Math.max(
                    Config.getInteger("consumer.ffmpeg.min_direct_flush_size", 1048576),
                    -1
            );

    private final int ffmpegThreadPriority =
            Math.max(
                    Math.min(
                            Config.getInteger("consumer.ffmpeg.thread_priority", Thread.MAX_PRIORITY - 2),
                            Thread.MAX_PRIORITY
                    ),
                    Thread.MIN_PRIORITY
            );

    private int uploadIDPort = Config.getInteger("consumer.ffmpeg.upload_id_port", 7818);
    private final long initBufferedData = 1048576;

    private String currentRecordingQuality;
    private boolean consumeToNull;

    // Atomic because long values take two clocks just to store in 32-bit. We could get incomplete
    // values otherwise. Don't ever forget to set this value and increment it correctly. This is
    // crucial to playback in SageTV.
    private AtomicLong bytesStreamed = new AtomicLong(0);
    private AtomicBoolean running = new AtomicBoolean(false);
    private boolean stalled = false;
    private boolean streaming = false;
    private String currentChannel = "";
    private String currentEncoderFilename = "";
    private FFmpegWriter currentWriter = null;
    private FFmpegWriter switchWriter = null;
    private int currentUploadID = 0;
    private long stvRecordBufferSize = 0;
    private final Object streamingMonitor = new Object();
    private int autoOffset = 0;
    private InetSocketAddress uploadSocketAddress = null;

    private String stateMessage;
    private FFmpegCircularBuffer circularBuffer;
    private FFmpegContext ctx;

    static {
        FFmpegUtil.initAll();
    }

    public FFmpegTransSageTVConsumerImpl() {
        ctx = new FFmpegContext(circularBufferSize, RW_BUFFER_SIZE, new FFmpegTranscoder());
    }

    @Override
    public void run() {
        if (running.getAndSet(true)) {
            logger.error("FFmpeg Transcoder consumer is already running.");
            return;
        }

        if (ctx == null) {
            logger.error("Context is null!");
            return;
        }

        logger.info("FFmpeg Transcoder consumer thread is now running.");

        stateMessage = "Opening file...";
        stalled = false;
        streaming = false;

        logger.debug("Thread priority is {}.", ffmpegThreadPriority);
        Thread.currentThread().setPriority(ffmpegThreadPriority);

        try {
            FFmpegProfile profile = FFmpegProfileManager.getEncoderProfile(currentRecordingQuality);
            ctx.setEncodeProfile(profile);

            if (!ctx.initTsStream(currentEncoderFilename)) {
                stateMessage = "Unable to detect any video.";
                stalled = true;
                return;
            }

            ctx.STREAM_PROCESSOR.initStreamOutput(ctx, currentEncoderFilename, currentWriter);

            ctx.STREAM_PROCESSOR.streamOutput();

        } catch (FFmpegException e) {
            logger.error("Unable to start streaming => ", e);
            stateMessage = e.getMessage();
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

            ctx.deallocAll();

            stateMessage = "Stopped.";
            running.set(false);
            logger.info("FFmpeg Transcoder consumer thread stopped.");
        }

    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        ctx.SEEK_BUFFER.write(bytes, offset, length);
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
        ctx.interrupt();
        ctx.SEEK_BUFFER.close();
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
        return bytesStreamed.get();
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
        stvRecordBufferSize = bufferSize;

        try {
            switchWriter = new FFmpegUploadIDWriter(uploadSocketAddress, filename, uploadId);

            ctx.STREAM_PROCESSOR.switchOutput(filename, switchWriter);

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
        stvRecordBufferSize = bufferSize;

        try {
            switchWriter = new FFmpegDirectWriter(filename);

            ctx.STREAM_PROCESSOR.switchOutput(filename, switchWriter);

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
        ctx.setProgram(program);
    }

    @Override
    public int getProgram() {
        return ctx.getProgram();
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
    public boolean isStalled() {
        return stalled;
    }

    @Override
    public String stateMessage() {
        return stateMessage;
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

    public class FFmpegUploadIDWriter implements FFmpegWriter {
        ByteBuffer streamBuffer = ByteBuffer.allocateDirect(minUploadIDTransfer * 2);
        NIOSageTVUploadID upload = new NIOSageTVUploadID();

        InetSocketAddress uploadSocket;
        String uploadFilename;
        int uploadID;

        long bytesFlushCounter;
        boolean firstWrite;

        public FFmpegUploadIDWriter (InetSocketAddress uploadSocket, String uploadFilename, int uploadID) throws IOException {
            upload.startUpload(uploadSocket, uploadFilename, uploadID);

            this.uploadSocket = uploadSocket;
            this.uploadFilename = uploadFilename;
            this.uploadID = uploadID;

            bytesFlushCounter = 0;
            firstWrite = true;
        }

        @Override
        public void closeFile() {
            try {
                upload.endUpload(true);
            } catch (IOException e) {
                logger.error("Unable to close the file '{}' upload ID {} => ",
                        uploadFilename, uploadID, e);
            }

            firstWrite = true;
        }

        @Override
        public synchronized int write(ByteBuffer data) throws IOException {
            if (firstWrite) {
                bytesStreamed.set(0);
                firstWrite = false;
            }

            int bufferLength = data.remaining();

            streamBuffer.put(data);

            if (streamBuffer.position() < minUploadIDTransfer) {
                return logger.exit(bufferLength);
            }

            streamBuffer.flip();

            int bytesToStream = streamBuffer.remaining();

            try {
                if (stvRecordBufferSize > 0) {
                    upload.uploadAutoBuffered(stvRecordBufferSize, streamBuffer);
                } else {
                    upload.uploadAutoIncrement(streamBuffer);
                }

                long currentBytes = bytesStreamed.addAndGet(bytesToStream);

                if (currentBytes > initBufferedData) {
                    synchronized (streamingMonitor) {
                        streamingMonitor.notifyAll();
                    }
                }
            } catch (IOException e) {
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

    public class FFmpegDirectWriter implements FFmpegWriter {
        long bytesFlushCounter;

        Future<Integer> future;
        BlockingQueue<ByteBuffer> buffers;
        CompletionHandler<Integer, Object> handler;
        AsynchronousFileChannel asyncFileChannel;
        String directFilename;
        File recordingFile;
        boolean firstWrite;

        public FFmpegDirectWriter(final String filename) throws IOException {
            future = null;
            buffers = new ArrayBlockingQueue<ByteBuffer>(3);

            buffers.add(ByteBuffer.allocateDirect(RW_BUFFER_SIZE * 2));
            buffers.add(ByteBuffer.allocateDirect(RW_BUFFER_SIZE * 2));
            buffers.add(ByteBuffer.allocateDirect(RW_BUFFER_SIZE * 2));

            asyncFileChannel = AsynchronousFileChannel.open(Paths.get(filename), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            directFilename = filename;
            recordingFile = new File(directFilename);

            bytesFlushCounter = 0;
            firstWrite = false;

            handler = new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {

                    bytesStreamed.addAndGet(result);

                    try {
                        buffers.put((ByteBuffer)attachment);
                    } catch (InterruptedException e) {
                        logger.debug("Interrupted while returning byte buffer to queue => ", e);
                    }
                }

                @Override
                public void failed(Throwable e, Object attachment) {
                    logger.error("File write failed => ", e);

                    try {
                        buffers.put((ByteBuffer)attachment);
                    } catch (InterruptedException e0) {
                        logger.debug("Interrupted while returning byte buffer to queue => ", e0);
                    }
                }
            };
        }

        @Override
        public synchronized int write(ByteBuffer data) throws IOException {
            if (firstWrite) {
                bytesStreamed.set(0);
                firstWrite = false;
            }

            while (future != null && future.isDone()) {
                try {
                    future.get();
                } catch (InterruptedException e) {

                } catch (ExecutionException e) {

                }

                if (future.isDone()) {
                    future = null;
                }
            }

            int bytesToStream = data.remaining();

            if (minDirectFlush != -1 && bytesFlushCounter >= minDirectFlush) {
                try {
                    asyncFileChannel.force(true);
                } catch (IOException e) {
                    logger.info("Unable to flush output => ", e);
                }
                bytesFlushCounter = 0;

                // According to many sources, if the file is deleted an IOException will
                // not be thrown. This handles the possible scenario. This also means
                // previously written data is now lost.

                if (!recordingFile.exists() || (bytesStreamed.get() > 0 && recordingFile.length() == 0)) {
                    try {
                        asyncFileChannel.close();
                    } catch (Exception e) {
                        logger.debug("Exception while closing missing file => ", e);
                    }

                    while (!ctx.isInterrupted()) {
                        try {
                            asyncFileChannel = AsynchronousFileChannel.open(Paths.get(directFilename), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                            bytesStreamed.set(0);
                            logger.warn("The file '{}' is missing and was re-created.");
                        } catch (Exception e) {
                            logger.error("The file '{}' is missing and cannot be re-created => ",
                                    directFilename, e);

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e1) {
                                logger.debug("Interrupted while trying to re-create recording file.");
                                break;
                            }
                            // Continue to re-try until interrupted.
                        }
                    }
                }
            }

            if (stvRecordBufferSize > 0 && stvRecordBufferSize < autoOffset + bytesToStream) {
                ByteBuffer slice = data.slice();
                slice.limit((int) (stvRecordBufferSize - autoOffset));

                try {
                    ByteBuffer writeBuffer = buffers.take();
                    writeBuffer.clear();
                    writeBuffer.put(slice);
                    writeBuffer.flip();
                    asyncFileChannel.write(writeBuffer, autoOffset, writeBuffer, handler);
                } catch (InterruptedException e) {
                    return -1;
                }

                data.position((int) (data.position() + (stvRecordBufferSize - autoOffset)));
                autoOffset = 0;
            }

            int savedSize = data.remaining();

            try {
                ByteBuffer writeBuffer = buffers.take();

                writeBuffer.clear();
                writeBuffer.put(data);
                writeBuffer.flip();

                asyncFileChannel.write(writeBuffer, autoOffset, writeBuffer, handler);
            } catch (InterruptedException e) {
                future = asyncFileChannel.write(data, autoOffset);
            }

            autoOffset += savedSize;

            bytesFlushCounter += savedSize;
            long currentBytes = bytesStreamed.addAndGet(savedSize);

            if (currentBytes > initBufferedData) {
                synchronized (streamingMonitor) {
                    streamingMonitor.notifyAll();
                }
            }

            return bytesToStream;
        }

        @Override
        public void closeFile() {
            try {
                // This closes the channel too.
                asyncFileChannel.close();
            } catch (IOException e) {
                logger.error("Unable to close the file '{}' => ",
                        directFilename, e);
            }
        }

        @Override
        public Logger getLogger() {
            return logger;
        }
    }

    public class FFmpegNullWriter implements FFmpegWriter {
        boolean firstWrite = false;

        @Override
        public int write(ByteBuffer data) throws IOException {
            if (firstWrite) {
                bytesStreamed.set(0);
                firstWrite = false;
            }

            bytesStreamed.addAndGet(data.remaining());

            return data.remaining();
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
