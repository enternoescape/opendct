/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import opendct.config.options.IntegerDeviceOption;
import opendct.consumer.buffers.SeekableCircularBufferNIO;
import opendct.consumer.upload.NIOSageTVUploadID;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.video.java.VideoUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MediaServerConsumerImpl implements SageTVConsumer {
    private static final Logger logger = LogManager.getLogger(MediaServerConsumerImpl.class);

    private final int initDataSize = initDataSizeOpt.getInteger();
    private final int minTransferSize = minTransferSizeOpt.getInteger();
    private final int maxTransferSize = maxTransferSizeOpt.getInteger();
    private final int bufferSize = bufferSizeOpt.getInteger();
    private final int rawThreadPriority = threadPriorityOpt.getInteger();

    // Atomic because long values take two clocks to process in 32-bit. We could get incomplete
    // values otherwise. Don't ever forget to set this value and increment it correctly. This is
    // crucial to playback actually starting in SageTV.
    private AtomicLong bytesStreamed = new AtomicLong(0);

    private AtomicBoolean running = new AtomicBoolean(false);
    private long stvRecordBufferSize = 0;

    private boolean consumeToNull = false;
    private String currentRecordingFilename = null;
    private String switchRecordingFilename = null;
    private int currentUploadID = -1;
    private int switchUploadID = -1;
    private boolean currentInit = false;
    private final Object streamingMonitor = new Object();

    private String currentRecordingQuality = null;
    private int desiredProgram = -1;
    private String tunedChannel = "";

    private int switchAttempts = 50;
    private volatile boolean switchFile = false;
    private final Object switchMonitor = new Object();

    private NIOSageTVUploadID mediaServer = new NIOSageTVUploadID();
    private ByteBuffer streamBuffer = ByteBuffer.allocateDirect(maxTransferSize);
    private SeekableCircularBufferNIO seekableBuffer = new SeekableCircularBufferNIO(bufferSize);

    private final int uploadIDPort = uploadIdPortOpt.getInteger();
    private SocketAddress uploadIDSocket = null;

    private volatile boolean stalled = false;
    private String stateMessage = "Waiting for first bytes...";

    static {
        deviceOptions = new ConcurrentHashMap<>();

        initDeviceOptions();
    }

    @Override
    public void run() {
        if (running.getAndSet(true)) {
            throw new IllegalThreadStateException("MediaServer consumer is already running.");
        }

        logger.info("MediaServer thread started.");

        stalled = false;
        stateMessage = "Waiting for first bytes...";

        logger.debug("Thread priority is {}.", rawThreadPriority);
        Thread.currentThread().setPriority(rawThreadPriority);

        try {
            if (consumeToNull) {
                while (!seekableBuffer.isClosed()) {
                    streamBuffer.clear();
                    int bytesRead = seekableBuffer.read(streamBuffer);
                    bytesStreamed.addAndGet(bytesRead);
                }

                return;
            }

            boolean connected = false;
            boolean remuxStarted = false;

            // Connect and start remuxing if not buffering.
            while (!seekableBuffer.isClosed()) {
                stateMessage = "Opening file via MediaServer...";
                logger.info(stateMessage);
                try {
                    connected = mediaServer.startUpload(
                            uploadIDSocket, currentRecordingFilename, currentUploadID);
                } catch (IOException e) {
                    logger.error("Unable to connect to socket {} => ", uploadIDSocket, e);
                }

                if (!connected) {
                    logger.error("SageTV refused the creation of the file '{}'. Trying again...",
                            currentRecordingFilename);
                    Thread.sleep(1000);

                    continue;
                }

                stateMessage = "Setting up remuxing on MediaServer...";
                logger.info(stateMessage);
                try {
                    remuxStarted = mediaServer.setupRemux(
                            currentRecordingFilename.endsWith(".mpg") ? "PS" : "TS",
                            0, initDataSize);

                } catch (IOException e) {
                    logger.error("Unable to communicate on socket {} => ", uploadIDSocket, e);
                }

                if (!remuxStarted) {
                    logger.error("SageTV refused to start remuxing the file '{}'. Trying again...",
                            currentRecordingFilename);
                    Thread.sleep(1000);

                    try {
                        mediaServer.endUpload(true);
                    } catch (IOException e) {
                        logger.debug("There was an exception while closing the previous connection => ", e);
                    }

                    mediaServer.reset();
                    connected = false;
                    continue;
                }

                if (stvRecordBufferSize > 0) {
                    stateMessage = "Setting BUFFER on MediaServer...";
                    logger.info(stateMessage);
                    try {
                        mediaServer.setRemuxBuffer(stvRecordBufferSize);
                    } catch (IOException e) {
                        logger.error("Unable to communicate on socket {} => ", uploadIDSocket, e);
                    }
                }

                break;
            }

            boolean start = true;
            stateMessage = "Streaming...";
            //ByteBuffer tempBuffer = null;
            // Start actual streaming.
            streamBuffer.clear();
            while (!seekableBuffer.isClosed()) {
                /*if (tempBuffer != null) {
                    seekableBuffer.read(tempBuffer);
                    tempBuffer = null;
                }*/

                seekableBuffer.read(streamBuffer);

                if (streamBuffer.position() < minTransferSize && !seekableBuffer.isClosed()) {
                    continue;
                }

                /*int overBytes = streamBuffer.position() % 188;
                if (overBytes > 0) {
                    logger.info("overBytes = {}", overBytes);
                    tempBuffer = streamBuffer.duplicate();
                    tempBuffer.position(tempBuffer.position() - overBytes);
                    tempBuffer.flip();
                    streamBuffer.limit(tempBuffer.position() - 1);
                }*/

                streamBuffer.flip();

                if (start) {
                    int startIndex = VideoUtil.getTsVideoPatStartByte(
                            streamBuffer,
                            false
                    );

                    if (startIndex > 0) {
                        streamBuffer.position(startIndex);
                        start = false;
                        stateMessage = "Streaming...";
                        logger.info("MediaServer consumer is now streaming...");
                    } else {
                        continue;
                    }
                }

                if (switchFile) {
                    synchronized (switchMonitor) {
                        int switchIndex;

                        if (switchAttempts-- > 0) {
                             switchIndex = VideoUtil.getTsVideoRandomAccessIndicator(
                                    streamBuffer,
                                    false
                            );

                            if (switchIndex > -1) {
                                switchIndex += 188;
                            }
                        } else {
                            if (switchAttempts == -1) {
                                logger.warn("Stream does not appear to contain any random access" +
                                        " indicators. Using the nearest PES packet.");
                            }

                            switchIndex = VideoUtil.getTsVideoPesStartByte(
                                    streamBuffer,
                                    false
                            );

                            if (switchIndex > -1) {
                                switchIndex += 188;
                            }
                        }

                        if (switchIndex > -1) {
                            switchAttempts = 50;

                            if (switchIndex > streamBuffer.position()) {
                                ByteBuffer lastWriteBuffer = streamBuffer.duplicate();
                                lastWriteBuffer.limit(switchIndex - 1);
                                streamBuffer.position(switchIndex);

                                mediaServer.uploadAutoIncrement(lastWriteBuffer);
                            }

                            while (!seekableBuffer.isClosed()) {
                                stateMessage = "Opening file via MediaServer for SWITCH...";
                                logger.info(stateMessage);

                                try {
                                    connected = mediaServer.switchRemux(
                                            switchRecordingFilename, switchUploadID);
                                } catch (IOException e) {
                                    logger.error("Unable to connect to socket {} => ", uploadIDSocket, e);
                                }

                                if (!connected) {
                                    logger.error("SageTV refused the creation of the file '{}'. Trying again...",
                                            currentRecordingFilename);
                                    Thread.sleep(1000);

                                    continue;
                                }

                                break;
                            }

                            int initBytes = 0;

                            currentRecordingFilename = switchRecordingFilename;
                            currentUploadID = switchUploadID;

                            bytesStreamed.set(mediaServer.getSize());
                            switchFile = false;

                            stateMessage = "Streaming...";
                            logger.info("SWITCH successful.");
                            switchMonitor.notifyAll();
                        }
                    }
                }

                if (!currentInit) {
                    currentInit = mediaServer.isRemuxInitialized();

                    if (currentInit) {
                        synchronized (streamingMonitor) {
                            streamingMonitor.notifyAll();
                        }
                    }
                }

                int bytesToStream = streamBuffer.remaining();

                mediaServer.uploadAutoIncrement(streamBuffer);

                if (consumeToNull) {
                    bytesStreamed.addAndGet(bytesToStream);
                }

                streamBuffer.clear();
            }

        } catch (InterruptedException e) {
            logger.debug("MediaServer consumer was interrupted.");
        } catch (SocketException e) {
            logger.debug("MediaServer consumer has disconnected => ", e);
        } catch (Exception e) {
            logger.warn("MediaServer consumer created an unexpected exception => ", e);
        } finally {
            try {
                mediaServer.endUpload(true);
            } catch (IOException e) {
                logger.error("There was a problem while disconnecting from MediaServer.");
            }

            logger.info("MediaServer thread stopped.");
            running.getAndSet(false);
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        seekableBuffer.write(bytes, offset, length);
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {
        seekableBuffer.write(buffer);
    }

    @Override
    public void clearBuffer() {
        seekableBuffer.close();
        seekableBuffer.clear();
    }

    @Override
    public void setRecordBufferSize(long bufferSize) {
        stvRecordBufferSize = bufferSize;
    }

    @Override
    public boolean canSwitch() {
        return false;
    }

    @Override
    public boolean getIsRunning() {
        return running.get();
    }

    @Override
    public void stopConsumer() {
        seekableBuffer.close();
    }

    @Override
    public void consumeToNull(boolean consumeToNull) {
        this.consumeToNull = consumeToNull;
    }

    @Override
    public long getBytesStreamed() {
        if (consumeToNull) {
            return bytesStreamed.get();
        } else if (currentInit) {
            synchronized (switchMonitor) {
                try {
                    long returnValue = mediaServer.getSize();
                    bytesStreamed.set(returnValue);
                    return returnValue;
                } catch (IOException e) {
                    logger.error("Unable to get bytes from MediaServer. Replying with estimate.");
                    return bytesStreamed.get();
                }
            }
        }

        return 0;
    }

    @Override
    public boolean acceptsUploadID() {
        return true;
    }

    @Override
    public boolean acceptsFilename() {
        return false;
    }

    @Override
    public void setEncodingQuality(String encodingQuality) {
        this.currentRecordingQuality = encodingQuality;
    }

    @Override
    public boolean consumeToUploadID(String filename, int uploadId, InetAddress socketAddress) {
        logger.entry(filename, uploadId, socketAddress);

        this.currentRecordingFilename = filename;
        this.currentUploadID = uploadId;

        uploadIDSocket = new InetSocketAddress(socketAddress, uploadIDPort);

        return logger.exit(true);
    }

    @Override
    public boolean consumeToFilename(String filename) {
        // Writing to a file directly is unsupported.
        return false;
    }

    @Override
    public boolean switchStreamToUploadID(String filename, long bufferSize, int uploadId) {
        logger.entry(filename, bufferSize, uploadId);

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

    @Override
    public boolean switchStreamToFilename(String filename, long bufferSize) {
        // Writing to a file directly is unsupported.
        return false;
    }

    @Override
    public String getEncoderQuality() {
        return currentRecordingQuality;
    }

    @Override
    public String getEncoderFilename() {
        return currentRecordingFilename;
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
        tunedChannel = channel;
    }

    @Override
    public String getChannel() {
        return tunedChannel;
    }

    @Override
    public boolean isStalled() {
        return false;
    }

    @Override
    public String stateMessage() {
        return stateMessage;
    }

    @Override
    public boolean isStreaming(long timeout) {
        if (currentInit) {
            return true;
        }

        synchronized (streamingMonitor) {
            try {
                streamingMonitor.wait(timeout);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for consumer to start streaming.");
            }
        }

        return currentInit;
    }

    private final static Map<String, DeviceOption> deviceOptions;

    private static IntegerDeviceOption initDataSizeOpt;
    private static IntegerDeviceOption minTransferSizeOpt;
    private static IntegerDeviceOption maxTransferSizeOpt;
    private static IntegerDeviceOption bufferSizeOpt;
    private static IntegerDeviceOption threadPriorityOpt;
    private static IntegerDeviceOption uploadIdPortOpt;

    private static void initDeviceOptions() {
        while (true) {
            try {
                initDataSizeOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.media_server.init_data_size", 1048576),
                        false,
                        "Min Initialization Data Size",
                        "consumer.media_server.init_data_size",
                        "This is the minimum number of bytes that SageTV will use while detecting" +
                                " what streams are present to remux. This value cannot be less" +
                                " than 8084 and cannot be greater than 5242756. This value will" +
                                " auto-align to the nearest multiple of 188.",
                        8084,
                        52427560);

                minTransferSizeOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.media_server.min_transfer_size", 64860),
                        false,
                        "Min Transfer Rate",
                        "consumer.media_server.min_transfer_size",
                        "This is the minimum number of bytes to write at one time. This value" +
                                " cannot be less than 16356 bytes and cannot be greater than" +
                                " 262072 bytes. This value will auto-align to the nearest" +
                                " multiple of 188.",
                        16356,
                        262072);

                maxTransferSizeOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.media_server.max_transfer_size", 1048476),
                        false,
                        "Max Transfer Rate",
                        "consumer.media_server.max_transfer_size",
                        "This is the maximum number of bytes to write at one time. This value" +
                                " cannot be less than 786404 bytes and cannot be greater than" +
                                " 1048476 bytes. This value will auto-align to the nearest" +
                                " multiple of 188.",
                        786404,
                        1048476);

                bufferSizeOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.media_server.stream_buffer_size", 2097152),
                        false,
                        "Stream Buffer Size",
                        "consumer.media_server.stream_buffer_size",
                        "This is the size of the streaming buffer. If this is not greater than 2" +
                                " * Max Transfer Size, it will be adjusted. This value cannot be" +
                                " less than 2097152 bytes and cannot be greater than 33554432" +
                                " bytes.",
                        2097152,
                        33554432);


                threadPriorityOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.media_server.thread_priority", Thread.MAX_PRIORITY - 2),
                        false,
                        "Raw Thread Priority",
                        "consumer.media_server.thread_priority",
                        "This is the priority given to the raw processing thread. A higher" +
                                " number means higher priority. Only change this value for" +
                                " troubleshooting. This value cannot be less than 1 and cannot be" +
                                " greater than 10.",
                        Thread.MIN_PRIORITY,
                        Thread.MAX_PRIORITY);

                uploadIdPortOpt = new IntegerDeviceOption(
                        Config.getInteger("consumer.media_server.upload_id_port", 7818),
                        false,
                        "SageTV Upload ID Port",
                        "consumer.media_server.upload_id_port",
                        "This is the port number used to communicate with SageTV when using" +
                                " upload ID for recording. You only need to change this value if" +
                                " you have changed it in SageTV. This value cannot be less than" +
                                " 1024 and cannot be greater than 65535.",
                        1024,
                        65535);

                // Enforce 188 alignment.
                initDataSizeOpt.setValue((initDataSizeOpt.getInteger() / 188) * 188);
                minTransferSizeOpt.setValue((minTransferSizeOpt.getInteger() / 188) * 188);
                maxTransferSizeOpt.setValue((maxTransferSizeOpt.getInteger() / 188) * 188);
            } catch (DeviceOptionException e) {
                logger.warn("Invalid options. Reverting to defaults => ", e);

                Config.setInteger("consumer.media_server.init_data_size", 1048576);
                Config.setInteger("consumer.media_server.min_transfer_size", 65536);
                Config.setInteger("consumer.media_server.max_transfer_size", 1048476);
                Config.setInteger("consumer.media_server.stream_buffer_size", 2097152);
                Config.setInteger("consumer.media_server.thread_priority", Thread.MAX_PRIORITY - 2);
                Config.setInteger("consumer.media_server.upload_id_port", 7818);
                continue;
            }

            break;
        }

        Config.mapDeviceOptions(
                deviceOptions,
                initDataSizeOpt,
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
                initDataSizeOpt,
                minTransferSizeOpt,
                maxTransferSizeOpt,
                bufferSizeOpt,
                threadPriorityOpt,
                uploadIdPortOpt
        };
    }

    @Override
    public void setOptions(JsonOption... deviceOptions) throws DeviceOptionException {
        for (JsonOption option : deviceOptions) {
            DeviceOption optionReference =
                    MediaServerConsumerImpl.deviceOptions.get(option.getProperty());

            if (optionReference == null) {
                continue;
            }

            if (optionReference.isArray()) {
                optionReference.setValue(option.getValues());
            } else {
                optionReference.setValue(option.getValue());
            }

            Config.setDeviceOption(optionReference);
        }

        // Enforce 188 alignment.
        initDataSizeOpt.setValue((initDataSizeOpt.getInteger() / 188) * 188);
        minTransferSizeOpt.setValue((minTransferSizeOpt.getInteger() / 188) * 188);
        maxTransferSizeOpt.setValue((maxTransferSizeOpt.getInteger() / 188) * 188);

        Config.saveConfig();
    }
}
