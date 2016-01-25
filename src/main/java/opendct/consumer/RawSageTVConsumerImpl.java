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

package opendct.consumer;

import opendct.config.Config;
import opendct.consumer.buffers.SeekableCircularBuffer;
import opendct.consumer.upload.NIOSageTVUploadID;
import opendct.video.java.VideoUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RawSageTVConsumerImpl implements SageTVConsumer {
    private final Logger logger = LogManager.getLogger(RawSageTVConsumerImpl.class);

    private final boolean acceptsUploadID =
            Config.getBoolean("consumer.raw.upload_id_enabled", false);

    private final int minTransferSize =
            Math.max(
                    Config.getInteger("consumer.raw.min_transfer_size", 16384),
                    1316
            );

    private final int maxTransferSize =
            Math.max(
                    Config.getInteger("consumer.raw.max_transfer_size", 131072),
                    minTransferSize * 4
            );

    private final int bufferSize =
            Math.max(
                    Config.getInteger("consumer.raw.stream_buffer_size", 1048476),
                    maxTransferSize * 2
            );

    private final int rawThreadPriority =
            Math.max(
                    Math.min(
                            Config.getInteger("consumer.ffmpeg.thread_priority", Thread.MAX_PRIORITY - 2),
                            Thread.MAX_PRIORITY
                    ),
                    Thread.MIN_PRIORITY
            );

    private final int standoff = Config.getInteger("consumer.raw.standoff", 8192);

    // Atomic because long values take two clocks to process in 32-bit. We could get incomplete
    // values otherwise. Don't ever forget to set this value and increment it correctly. This is
    // crucial to playback actually starting in SageTV.
    private AtomicLong bytesStreamed = new AtomicLong(0);

    private boolean consumeToNull = false;
    private FileOutputStream currentFileOutputStream = null;
    private FileOutputStream switchFileOutputStream = null;
    private String currentRecordingFilename = null;
    private String switchRecordingFilename = null;
    private int currentUploadID = -1;
    private int switchUploadID = -1;
    private String currentRecordingQuality = null;
    private int desiredProgram = -1;
    private String tunedChannel = "";

    private AtomicBoolean running = new AtomicBoolean(false);
    private long stvRecordBufferSize = 0;
    private AtomicLong stvRecordBufferPos = new AtomicLong(0);

    private volatile boolean switchFile = false;
    private final Object switchMonitor = new Object();

    private ByteBuffer streamBuffer = ByteBuffer.allocate(maxTransferSize);
    private SeekableCircularBuffer seekableBuffer = new SeekableCircularBuffer(bufferSize);

    private NIOSageTVUploadID nioSageTVUploadID = null;

    private final int uploadIDPort = Config.getInteger("consumer.raw.upload_id_port", 7818);
    private SocketAddress uploadIDSocket = null;

    public void run() {
        logger.entry();
        if (running.getAndSet(true)) {
            throw new IllegalThreadStateException("Raw consumer is already running.");
        }

        logger.debug("Thread priority is {}.", rawThreadPriority);
        Thread.currentThread().setPriority(rawThreadPriority);

        int bytesReceivedCount = 0;
        int bytesReceivedBuffer = 0;

        boolean uploadEnabled = false;
        int bytesToStream = 0;
        FileChannel currentFile = null;
        switchFile = false;
        seekableBuffer.clear();

        try {
            logger.info("Raw consumer thread is now running.");

            if (currentUploadID > 0) {
                if (nioSageTVUploadID == null) {
                    nioSageTVUploadID = new NIOSageTVUploadID();
                } else {
                    nioSageTVUploadID.reset();
                }

                boolean uploadIDConfigured = false;

                try {
                    uploadIDConfigured = nioSageTVUploadID.startUpload(
                            uploadIDSocket, currentRecordingFilename, currentUploadID);
                } catch (IOException e) {
                    logger.error("Unable to connect to SageTV server to start transfer via uploadID.");
                }

                if (!uploadIDConfigured) {

                    logger.error("Raw consumer did not receive OK from SageTV server to start" +
                                    " uploading to the file '{}' via the upload id '{}'" +
                                    " using the socket '{}'.",
                            currentRecordingFilename, currentUploadID, uploadIDSocket);

                    if (currentRecordingFilename != null) {
                        logger.info("Attempting to write the file directly.");
                        try {
                            this.currentFileOutputStream = new FileOutputStream(currentRecordingFilename);
                            currentFile = currentFileOutputStream.getChannel();
                        } catch (FileNotFoundException e) {
                            logger.error("Unable to create the recording file '{}'.", currentRecordingFilename);
                            currentRecordingFilename = null;
                        }
                    }
                } else {
                    uploadEnabled = true;
                }
            } else if (currentRecordingFilename != null) {
                currentFile = currentFileOutputStream.getChannel();
            } else if (consumeToNull) {
                logger.debug("Consuming to a null output.");
            } else {
                logger.error("Raw consumer does not have a file or UploadID to use.");
                throw new IllegalThreadStateException(
                        "Raw consumer does not have a file or UploadID to use.");
            }

            boolean start = true;
            int standoffCountdown = standoff;

            while (!Thread.currentThread().isInterrupted()) {
                streamBuffer.clear();

                while (streamBuffer.position() < minTransferSize && !Thread.currentThread().isInterrupted()) {

                    seekableBuffer.read(streamBuffer);

                    if (switchFile) {
                        break;
                    }
                }

                // Switch the buffers to reading mode.
                streamBuffer.flip();

                if (start) {
                    int startIndex = VideoUtil.getTsVideoPesStartByte(
                            streamBuffer,
                            false
                    );

                    if (startIndex > 0) {
                        streamBuffer.position(startIndex);
                        start = false;
                        logger.info("Raw consumer is now streaming...");
                    } else {
                        continue;
                    }
                }

                try {
                    if (uploadEnabled) {
                        if (switchFile) {
                            int switchIndex = VideoUtil.getTsVideoPatStartByte(
                                    streamBuffer,
                                    false
                            );

                            if (switchIndex > -1) {
                                synchronized (switchMonitor) {
                                    int lastBytesToStream = 0;
                                    if (switchIndex > streamBuffer.position()) {
                                        ByteBuffer lastWriteBuffer = streamBuffer.duplicate();
                                        lastWriteBuffer.limit(switchIndex - 1);
                                        streamBuffer.position(switchIndex);

                                        lastBytesToStream = lastWriteBuffer.remaining();


                                        if (stvRecordBufferSize > 0) {
                                            nioSageTVUploadID.uploadAutoBuffered(stvRecordBufferSize, lastWriteBuffer);
                                        } else {
                                            nioSageTVUploadID.uploadAutoIncrement(lastWriteBuffer);
                                        }
                                    }

                                    bytesStreamed.addAndGet(lastBytesToStream + standoff);
                                    standoffCountdown = standoff;

                                    if (!nioSageTVUploadID.switchUpload(
                                            switchRecordingFilename, switchUploadID)) {

                                        logger.error("Raw consumer did not receive OK from SageTV" +
                                                        " server to switch to the file '{}' via the" +
                                                        " upload id '{}'.",
                                                switchRecordingFilename, switchUploadID);

                                    } else {
                                        currentRecordingFilename = switchRecordingFilename;
                                        currentUploadID = switchUploadID;
                                        bytesStreamed.set(0);
                                        switchFile = false;

                                        switchMonitor.notifyAll();
                                        logger.info("SWITCH was successful.");
                                    }

                                }
                            }
                        }

                        bytesToStream = streamBuffer.remaining();
                        if (stvRecordBufferSize > 0) {
                            nioSageTVUploadID.uploadAutoBuffered(stvRecordBufferSize, streamBuffer);
                        } else {
                            nioSageTVUploadID.uploadAutoIncrement(streamBuffer);
                        }

                        if(standoffCountdown < 0) {
                            bytesStreamed.addAndGet(bytesToStream);
                        } else {
                            standoffCountdown -= bytesToStream;
                        }
                    } else if (!consumeToNull) {
                        if (switchFile) {
                            int switchIndex = VideoUtil.getTsVideoPatStartByte(
                                    streamBuffer,
                                    false
                            );

                            if (switchIndex > -1) {
                                synchronized (switchMonitor) {
                                    int lastBytesToStream = 0;
                                    if (switchIndex > streamBuffer.position()) {
                                        ByteBuffer lastWriteBuffer = streamBuffer.duplicate();
                                        lastWriteBuffer.limit(switchIndex - 1);
                                        streamBuffer.position(switchIndex);

                                        lastBytesToStream = lastWriteBuffer.remaining();

                                        while (lastWriteBuffer.hasRemaining()) {
                                            int savedSize = currentFile.write(lastWriteBuffer);
                                            bytesStreamed.addAndGet(savedSize);

                                            if (stvRecordBufferSize > 0 && stvRecordBufferPos.get() >
                                                    stvRecordBufferSize) {

                                                currentFile.position(0);
                                            }
                                            stvRecordBufferPos.set(currentFile.position());
                                        }
                                    }

                                    bytesStreamed.addAndGet(lastBytesToStream + standoff);
                                    standoffCountdown = lastBytesToStream;

                                    if (switchFileOutputStream != null) {
                                        if (currentFile != null && currentFile.isOpen()) {
                                            try {
                                                currentFile.close();
                                            } catch (IOException e) {
                                                logger.error("Raw consumer created an exception" +
                                                        " while closing the current file => {}", e);
                                            } finally {
                                                currentFile = null;
                                            }
                                        }
                                        currentFile = switchFileOutputStream.getChannel();
                                        currentFileOutputStream = switchFileOutputStream;
                                        currentRecordingFilename = switchRecordingFilename;
                                        switchFileOutputStream = null;
                                        bytesStreamed.set(0);
                                    }
                                    switchFile = false;

                                    switchMonitor.notifyAll();
                                    logger.info("SWITCH was successful.");
                                }
                            }
                        }

                        while (streamBuffer.hasRemaining()) {
                            int savedSize = currentFile.write(streamBuffer);

                            if(standoffCountdown < 0) {
                                bytesStreamed.addAndGet(savedSize);
                            } else {
                                standoffCountdown -= savedSize;
                            }

                            if (stvRecordBufferSize > 0 && stvRecordBufferPos.get() >
                                    stvRecordBufferSize) {

                                currentFile.position(0);
                            }
                            stvRecordBufferPos.set(currentFile.position());
                        }
                    } else {
                        // Write to null.
                        bytesStreamed.addAndGet(streamBuffer.limit());
                    }
                } catch (IOException e) {
                    logger.error("Raw consumer created an unexpected IO exception => {}", e);
                }
            }
        } catch (InterruptedException e) {
            logger.debug("Raw consumer was closed by an interrupt exception => ", e);
        } catch (Exception e) {
            logger.error("Raw consumer created an unexpected exception => {}", e);
        } finally {
            logger.info("Raw consumer thread is now stopping.");

            bytesStreamed.set(0);

            seekableBuffer.clear();

            currentRecordingFilename = null;
            if (currentFile != null && currentFile.isOpen()) {
                try {
                    currentFile.close();
                } catch (IOException e) {
                    logger.debug("Raw consumer created an exception while closing the current file => {}", e);
                } finally {
                    currentFile = null;
                }
            }

            if (nioSageTVUploadID != null) {
                try {
                    nioSageTVUploadID.endUpload(true);
                } catch (IOException e) {
                    logger.debug("Raw consumer created an exception while ending the current upload id session => ", e);
                } finally {
                    nioSageTVUploadID = null;
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Bytes available to be read = {}", seekableBuffer.readAvailable());
                logger.debug("Space available for writing in bytes = {}", seekableBuffer.writeAvailable());
            }

            logger.info("Raw consumer thread has stopped.");
            running.set(false);
        }
    }

    public void write(byte[] bytes, int offset, int length) throws IOException {
        seekableBuffer.write(bytes, offset, length);
    }

    public void setRecordBufferSize(long bufferSize) {
        this.stvRecordBufferSize = bufferSize;
    }

    public void setRecordBufferPosition(long bufferPosition) {
        stvRecordBufferPos.set(bufferPosition);
    }

    public long getRecordBufferPosition() {
        return stvRecordBufferPos.get();
    }

    public boolean canBuffer() {
        return true;
    }

    public boolean canSwitch() {
        return true;
    }

    public boolean getIsRunning() {
        return running.get();
    }

    public void stopConsumer() {
        seekableBuffer.close();
    }

    public long getBytesStreamed() {
        return bytesStreamed.get();
    }

    public boolean acceptsUploadID() {
        return acceptsUploadID;
    }

    public boolean acceptsFilename() {
        return true;
    }

    public void setEncodingQuality(String encodingQuality) {
        currentRecordingQuality = encodingQuality;
    }

    public boolean consumeToUploadID(String filename, int uploadId, InetAddress inetAddress) {
        logger.entry(filename, uploadId, inetAddress);

        this.currentRecordingFilename = filename;
        this.currentUploadID = uploadId;

        uploadIDSocket = new InetSocketAddress(inetAddress, uploadIDPort);

        return logger.exit(true);
    }


    public boolean consumeToFilename(String filename) {
        logger.entry(filename);

        try {
            this.currentFileOutputStream = new FileOutputStream(filename);
            this.currentRecordingFilename = filename;
        } catch (FileNotFoundException e) {
            logger.error("Unable to create the recording file '{}'.", filename);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    public synchronized boolean switchStreamToUploadID(String filename, long bufferSize, int uploadId) {
        logger.entry(filename, uploadId);

        logger.info("SWITCH to '{}' via uploadID '{}' was requested.", filename, uploadId);

        synchronized (switchMonitor) {
            this.switchUploadID = uploadId;
            this.switchRecordingFilename = filename;
            this.switchFile = true;

            while (switchFile && this.getIsRunning()) {
                try {
                    switchMonitor.wait(500);
                } catch (Exception e) {
                    break;
                }
            }
        }

        return logger.exit(false);
    }

    public synchronized boolean switchStreamToFilename(String filename, long bufferSize) {
        logger.entry(filename);

        logger.info("SWITCH to '{}' was requested.", filename);

        try {
            synchronized (switchMonitor) {
                this.switchFileOutputStream = new FileOutputStream(filename);
                this.switchRecordingFilename = filename;
                this.switchFile = true;

                while (switchFile && this.getIsRunning()) {
                    try {
                        switchMonitor.wait(500);
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Unable to create the recording file '{}'.", filename);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    public String getEncoderQuality() {
        return currentRecordingQuality;
    }

    public String getEncoderFilename() {
        return currentRecordingFilename;
    }

    public int getEncoderUploadID() {
        return currentUploadID;
    }

    public void setProgram(int program) {
        desiredProgram = program;
    }

    public int getProgram() {
        return desiredProgram;
    }

    public void consumeToNull(boolean consumeToNull) {
        this.consumeToNull = consumeToNull;
    }

    public String getChannel() {
        return tunedChannel;
    }

    public void setChannel(String tunedChannel) {
        this.tunedChannel = tunedChannel;
    }
}

