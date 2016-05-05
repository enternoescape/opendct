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
import opendct.config.options.BooleanDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.IntegerDeviceOption;
import opendct.consumer.buffers.SeekableCircularBufferNIO;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RawSageTVConsumerImpl implements SageTVConsumer {
    private static final Logger logger = LogManager.getLogger(RawSageTVConsumerImpl.class);

    private final boolean acceptsUploadID = uploadIdEnabledOpt.getBoolean();

    private final int minTransferSize = minTransferSizeOpt.getInteger();

    private final int maxTransferSize = maxTransferSizeOpt.getInteger();

    private final int bufferSize = bufferSizeOpt.getInteger();

    private final int rawThreadPriority = threadPriorityOpt.getInteger();

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
    private SeekableCircularBufferNIO seekableBuffer = new SeekableCircularBufferNIO(bufferSize);

    private NIOSageTVUploadID nioSageTVUploadID = null;

    private final int uploadIDPort = uploadIdPortOpt.getInteger();
    private SocketAddress uploadIDSocket = null;

    private volatile boolean stalled = false;
    private String stateMessage = "Waiting for first bytes...";

    static {
        deviceOptions = new ConcurrentHashMap<>();

        initDeviceOptions();
    }

    public void run() {
        logger.entry();
        if (running.getAndSet(true)) {
            throw new IllegalThreadStateException("Raw consumer is already running.");
        }

        stalled = false;
        stateMessage = "Waiting for first bytes...";

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

                    stalled = true;
                    stateMessage = "ERROR: Unable to connect to SageTV server to start transfer via uploadID.";
                }

                if (!uploadIDConfigured) {

                    logger.error("Raw consumer did not receive OK from SageTV server to start" +
                                    " uploading to the file '{}' via the upload id '{}'" +
                                    " using the socket '{}'.",
                            currentRecordingFilename, currentUploadID, uploadIDSocket);

                    if (currentRecordingFilename != null) {
                        logger.info("Attempting to write the file directly...");
                        try {
                            this.currentFileOutputStream = new FileOutputStream(currentRecordingFilename);
                            currentFile = currentFileOutputStream.getChannel();
                        } catch (FileNotFoundException e) {
                            logger.error("Unable to create the recording file '{}'.", currentRecordingFilename);
                            currentRecordingFilename = null;

                            stalled = true;
                            stateMessage = "ERROR: Unable to create the recording file '" + currentRecordingFilename + "'.";
                        }
                    }
                } else {
                    uploadEnabled = true;
                }
            } else if (currentRecordingFilename != null) {
                currentFile = currentFileOutputStream.getChannel();
            } else if (consumeToNull) {
                logger.debug("Consuming to a null output.");
                stateMessage = "Consuming to a null output...";
            } else {
                logger.error("Raw consumer does not have a file or UploadID to use.");

                stalled = true;
                stateMessage = "ERROR: Raw consumer does not have a file or UploadID to use.";
                throw new IllegalThreadStateException(
                        "Raw consumer does not have a file or UploadID to use.");
            }

            boolean start = true;
            stateMessage = "Waiting for PES start byte...";
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
                        stateMessage = "Streaming...";
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

                                    bytesStreamed.addAndGet(lastBytesToStream);

                                    if (!nioSageTVUploadID.switchUpload(
                                            switchRecordingFilename, switchUploadID)) {

                                        logger.error("Raw consumer did not receive OK from SageTV" +
                                                        " server to switch to the file '{}' via the" +
                                                        " upload id '{}'.",
                                                switchRecordingFilename, switchUploadID);

                                        stalled = true;
                                        stateMessage = "ERROR: Failed to SWITCH...";
                                    } else {
                                        currentRecordingFilename = switchRecordingFilename;
                                        currentUploadID = switchUploadID;
                                        bytesStreamed.set(0);
                                        switchFile = false;

                                        stalled = false;
                                        stateMessage = "Streaming...";

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

                        bytesStreamed.addAndGet(bytesToStream);
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

                                    bytesStreamed.addAndGet(lastBytesToStream);

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

                                    stalled = false;
                                    stateMessage = "Streaming...";

                                    switchMonitor.notifyAll();
                                    logger.info("SWITCH was successful.");
                                }
                            }
                        }

                        while (currentFile != null && streamBuffer.hasRemaining()) {
                            int savedSize = currentFile.write(streamBuffer);

                            bytesStreamed.addAndGet(savedSize);

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

            stateMessage = "Stopped.";
            stalled = true;

            logger.info("Raw consumer thread has stopped.");
            running.set(false);
        }
    }

    public void write(byte[] bytes, int offset, int length) throws IOException {
        seekableBuffer.write(bytes, offset, length);
    }

    public void write(ByteBuffer buffer) throws IOException {
        seekableBuffer.write(buffer);
    }

    @Override
    public void clearBuffer() {
        seekableBuffer.close();
        seekableBuffer.clear();
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

    public boolean isStalled() {
        return stalled;
    }

    public String stateMessage() {
        return stateMessage;
    }

    /**
     * This method always returns immediately for the raw consumer because it just streams.
     *
     * @param timeout The amount of time in milliseconds to block until returning even if the stream
     *                has not started.
     * @return <i>true</i> if the consumer is currently streaming.
     */
    public boolean isStreaming(long timeout) {
        try {
            int segments = 5;
            long increment = timeout / segments;

            if (increment < 1000) {
                Thread.sleep(timeout);
            } else {
                while (stalled && segments-- > 0 && getBytesStreamed() > 1048576) {
                    Thread.sleep(increment);
                }
            }
        } catch (InterruptedException e) {
            logger.debug("Interrupted while waiting for streaming.");
        }

        return !stalled;
    }

    private final static Map<String, DeviceOption> deviceOptions;

    private static BooleanDeviceOption uploadIdEnabledOpt;
    private static IntegerDeviceOption minTransferSizeOpt;
    private static IntegerDeviceOption maxTransferSizeOpt;
    private static IntegerDeviceOption bufferSizeOpt;
    private static IntegerDeviceOption threadPriorityOpt;
    private static IntegerDeviceOption uploadIdPortOpt;

    private static void initDeviceOptions() {
        while (true) {
            try {
                uploadIdEnabledOpt = new BooleanDeviceOption(
                        Config.getBoolean("consumer.raw.upload_id_enabled", false),
                        false,
                        "Enable Upload ID",
                        "consumer.raw.upload_id_enabled",
                        "This enables the use of upload ID with SageTV for writing out recordings.");

                minTransferSizeOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.raw.min_transfer_size", 65536),
                        false,
                        "Min Transfer Rate",
                        "consumer.raw.min_transfer_size",
                        "This is the minimum number of bytes to write at one time. This value" +
                                " cannot be less than 16384 bytes and cannot be greater than" +
                                " 262144 bytes.",
                        16384,
                        262144);

                maxTransferSizeOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.raw.max_transfer_size", 1048476),
                        false,
                        "Max Transfer Rate",
                        "consumer.raw.max_transfer_size",
                        "This is the maximum number of bytes to write at one time. This value" +
                                " cannot be less than 786432 bytes and cannot be greater than" +
                                " 1048576 bytes.",
                        786432,
                        1048576);

                bufferSizeOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.raw.stream_buffer_size", 2097152),
                        false,
                        "Stream Buffer Size",
                        "consumer.raw.stream_buffer_size",
                        "This is the size of the streaming buffer. If this is not greater than 2" +
                                " * Max Transfer Size, it will be adjusted. This value cannot be" +
                                " less than 2097152 bytes and cannot be greater than 33554432" +
                                " bytes.",
                        2097152,
                        33554432);


                threadPriorityOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.raw.thread_priority", Thread.MAX_PRIORITY - 2),
                        false,
                        "Raw Thread Priority",
                        "consumer.raw.thread_priority",
                        "This is the priority given to the raw processing thread. A higher" +
                                " number means higher priority. Only change this value for" +
                                " troubleshooting. This value cannot be less than 1 and cannot be" +
                                " greater than 10.",
                        Thread.MIN_PRIORITY,
                        Thread.MAX_PRIORITY);

                uploadIdPortOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.raw.upload_id_port", 7818),
                        false,
                        "SageTV Upload ID Port",
                        "consumer.raw.upload_id_port",
                        "This is the port number used to communicate with SageTV when using" +
                                " upload ID for recording. You only need to change this value if" +
                                " you have changed it in SageTV. This value cannot be less than" +
                                " 1024 and cannot be greater than 65535.",
                        1024,
                        65535);

            } catch (DeviceOptionException e) {
                logger.warn("Invalid options. Reverting to defaults => ", e);

                Config.setBoolean("consumer.raw.upload_id_enabled", false);
                Config.setInteger("consumer.raw.min_transfer_size", 65536);
                Config.setInteger("consumer.raw.max_transfer_size", 1048476);
                Config.setInteger("consumer.raw.stream_buffer_size", 2097152);
                Config.setInteger("consumer.ffmpeg.thread_priority", Thread.MAX_PRIORITY - 2);
                Config.setInteger("consumer.raw.upload_id_port", 7818);
                continue;
            }

            break;
        }

        Config.mapDeviceOptions(
                deviceOptions,
                uploadIdEnabledOpt,
                minTransferSizeOpt,
                maxTransferSizeOpt,
                bufferSizeOpt,
                threadPriorityOpt,
                uploadIdPortOpt
        );
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                uploadIdEnabledOpt,
                minTransferSizeOpt,
                maxTransferSizeOpt,
                bufferSizeOpt,
                threadPriorityOpt,
                uploadIdPortOpt
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference =
                    RawSageTVConsumerImpl.deviceOptions.get(option.getProperty());

            if (optionReference == null) {
                continue;
            }

            if (optionReference.isArray()) {
                optionReference.setValue(option.getArrayValue());
            } else {
                optionReference.setValue(option.getValue());
            }

            Config.setDeviceOption(optionReference);
        }

        Config.saveConfig();
    }
}

