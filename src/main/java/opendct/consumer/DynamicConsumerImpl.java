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
import opendct.config.options.ChannelRangesDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.StringDeviceOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DynamicConsumerImpl implements SageTVConsumer {
    private static final Logger logger = LogManager.getLogger(DynamicConsumerImpl.class);

    private final static ConcurrentHashMap<String, DeviceOption> deviceOptions;

    private static final ReentrantReadWriteLock dynamicMapsLock;
    private static final HashMap<String, String> dynamicMaps;
    private static StringDeviceOption defaultConsumer;
    private static ChannelRangesDeviceOption ffmpegConsumer;
    private static ChannelRangesDeviceOption ffmpegTransConsumer;
    private static ChannelRangesDeviceOption rawConsumer;

    private long bufferSize;
    private boolean consumeToNull;
    private String encodeQuality;
    private int desiredProgram;

    private SageTVConsumer sageTVConsumer;
    private String channel;

    static {
        dynamicMapsLock = new ReentrantReadWriteLock();
        dynamicMaps = new HashMap<>();
        deviceOptions = new ConcurrentHashMap<>();

        initDeviceOptions();
        updateDynamicMap();
    }

    private static void updateDynamicMap() {
        String channels[];

        dynamicMapsLock.writeLock().lock();

        try {
            if (defaultConsumer.getValue().endsWith(DynamicConsumerImpl.class.getSimpleName())) {
                logger.error("Dynamic consumer cannot be the default consumer. Changing to '{}'",
                        FFmpegTransSageTVConsumerImpl.class.getCanonicalName());

                defaultConsumer.setValue(FFmpegTransSageTVConsumerImpl.class.getCanonicalName());
            }

            channels = ChannelRangesDeviceOption.parseRanges(ffmpegConsumer.getValue());
            for (String channel : channels) {
                dynamicMaps.put(channel, FFmpegSageTVConsumerImpl.class.getCanonicalName());
            }

            logger.info("Dynamic consumer set to use FFmpegSageTVConsumerImpl for {}", Arrays.toString(channels));

            channels = ChannelRangesDeviceOption.parseRanges(ffmpegTransConsumer.getValue());
            for (String channel : channels) {
                dynamicMaps.put(channel, FFmpegTransSageTVConsumerImpl.class.getCanonicalName());
            }

            logger.info("Dynamic consumer set to use FFmpegTransSageTVConsumerImpl for {}", Arrays.toString(channels));

            channels = ChannelRangesDeviceOption.parseRanges(rawConsumer.getValue());
            for (String channel : channels) {
                dynamicMaps.put(channel, RawSageTVConsumerImpl.class.getCanonicalName());
            }

            logger.info("Dynamic consumer set to use RawSageTVConsumerImpl for {}", Arrays.toString(channels));
        } catch (Exception e) {
            logger.warn("There was an unexpected exception while updating the dynamic consumer" +
                    " channel map => ", e);
        } finally {
            dynamicMapsLock.writeLock().unlock();
        }
    }

    @Override
    public void run() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer == null) {
            sageTVConsumer = getConsumer(channel);
            consumer = sageTVConsumer;
        }

        if (consumer == null) {
            logger.error("The consumer is null. Recording cannot start.");
            return;
        }

        updateConsumer(consumer);

        consumer.run();
    }

    public static SageTVConsumer getConsumer(String channel) {
        String consumerName;

        if (channel == null) {
            logger.warn("Unable to determine what consumer is needed because a " +
                    "channel was not provided. Using default '{}'", defaultConsumer.getValue());

            consumerName = defaultConsumer.getValue();
        } else {
            consumerName = dynamicMaps.get(channel);
        }

        if (consumerName == null) {
            logger.debug("Using default consumer '{}' for channel '{}'", defaultConsumer, channel);
            consumerName = defaultConsumer.getValue();
        } else {
            logger.debug("Using defined consumer '{}' for channel '{}'", defaultConsumer, channel);
        }

        return Config.getSageTVConsumer(null, consumerName, channel);
    }

    private void updateConsumer(SageTVConsumer consumer) {
        if (consumer != null) {
            consumer.setRecordBufferSize(bufferSize);
            consumer.consumeToNull(consumeToNull);
            consumer.setEncodingQuality(encodeQuality);
            consumer.setProgram(desiredProgram);
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer == null) {
            sageTVConsumer = getConsumer(channel);
            consumer = sageTVConsumer;
        }

        if (consumer != null) {
            consumer.write(bytes, offset, length);
        } else {
            logger.error("Unable to load a consumer for writing!");
        }
    }

    @Override
    public void write(ByteBuffer buffer) throws IOException {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer == null) {
            sageTVConsumer = getConsumer(channel);
            consumer = sageTVConsumer;
        }

        if (consumer != null) {
            consumer.write(buffer);
        } else {
            logger.error("Unable to load a consumer for writing!");
        }
    }

    @Override
    public void setRecordBufferSize(long bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Override
    public boolean canSwitch() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.canSwitch();
        }

        sageTVConsumer = getConsumer(channel);

        consumer = sageTVConsumer;
        return consumer != null && consumer.canSwitch();
    }

    @Override
    public boolean getIsRunning() {
        SageTVConsumer consumer = sageTVConsumer;

        return consumer != null && consumer.getIsRunning();
    }

    @Override
    public void stopConsumer() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            consumer.stopConsumer();
        }
    }

    @Override
    public void consumeToNull(boolean consumeToNull) {
        this.consumeToNull = consumeToNull;
    }

    @Override
    public long getBytesStreamed() {
        SageTVConsumer consumer = sageTVConsumer;

        return consumer != null ? consumer.getBytesStreamed() : 0;
    }

    @Override
    public boolean acceptsUploadID() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.acceptsUploadID();
        }

        sageTVConsumer = getConsumer(channel);

        consumer = sageTVConsumer;
        return consumer != null && consumer.acceptsUploadID();
    }

    @Override
    public boolean acceptsFilename() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.acceptsFilename();
        }

        sageTVConsumer = getConsumer(channel);

        consumer = sageTVConsumer;
        return consumer != null && consumer.acceptsFilename();
    }

    @Override
    public void setEncodingQuality(String encodingQuality) {
        this.encodeQuality = encodingQuality;
    }

    @Override
    public boolean consumeToUploadID(String filename, int uploadId, InetAddress socketAddress) {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.consumeToUploadID(filename, uploadId, socketAddress);
        }

        sageTVConsumer = getConsumer(channel);
        updateConsumer(consumer);

        consumer = sageTVConsumer;
        return consumer != null && consumer.consumeToUploadID(filename, uploadId, socketAddress);
    }

    @Override
    public boolean consumeToFilename(String filename) {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.consumeToFilename(filename);
        }

        sageTVConsumer = getConsumer(channel);
        updateConsumer(consumer);

        consumer = sageTVConsumer;
        return consumer != null && consumer.consumeToFilename(filename);
    }

    @Override
    public boolean switchStreamToUploadID(String filename, long bufferSize, int uploadId) {
        SageTVConsumer consumer = sageTVConsumer;

        return consumer != null && consumer.switchStreamToUploadID(filename, bufferSize, uploadId);
    }

    @Override
    public boolean switchStreamToFilename(String filename, long bufferSize) {
        SageTVConsumer consumer = sageTVConsumer;

        return consumer != null && consumer.switchStreamToFilename(filename, bufferSize);
    }

    @Override
    public String getEncoderQuality() {
        return encodeQuality;
    }

    @Override
    public String getEncoderFilename() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.getEncoderFilename();
        }

        return "";
    }

    @Override
    public int getEncoderUploadID() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.getEncoderUploadID();
        }

        return -1;
    }

    @Override
    public void setProgram(int program) {
        this.desiredProgram = program;
    }

    @Override
    public int getProgram() {
        return desiredProgram;
    }

    @Override
    public void setChannel(String channel) {
        this.channel = channel;

        sageTVConsumer = getConsumer(channel);
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public boolean isStalled() {
        SageTVConsumer consumer = sageTVConsumer;

        return consumer != null && consumer.isStalled();
    }

    @Override
    public String stateMessage() {
        SageTVConsumer consumer = sageTVConsumer;

        return consumer != null ? consumer.stateMessage() : "Dynamic consumer not selected.";
    }

    @Override
    public boolean isStreaming(long timeout) {
        SageTVConsumer consumer = sageTVConsumer;

        return consumer != null && consumer.isStreaming(timeout);
    }

    private static void initDeviceOptions() {
        while (true) {
            try {
                defaultConsumer = new StringDeviceOption(
                        Config.getString("consumer.dynamic.default",
                                FFmpegTransSageTVConsumerImpl.class.getCanonicalName()),
                        false,
                        "Default Consumer",
                        "consumer.dynamic.default",
                        "This if the default consumer to be used if the channel does not match" +
                                " any defined channels. The default consumer setting for upload" +
                                " if will also decide if that feature will be used.",
                        Config.getSageTVConsumersLessDynamic());

                ffmpegConsumer = new ChannelRangesDeviceOption(
                        Config.getString("consumer.dynamic.channels.ffmpeg", ""),
                        false,
                        "FFmpeg Consumer Channels",
                        "consumer.dynamic.channels.ffmpeg",
                        "These are the channel ranges that will always use" +
                                " FFmpegSageTVConsumerImpl."
                );

                ffmpegTransConsumer = new ChannelRangesDeviceOption(
                        Config.getString("consumer.dynamic.channels.ffmpeg_trans", ""),
                        false,
                        "FFmpeg Transcoder Consumer Channels",
                        "consumer.dynamic.channels.ffmpeg_trans",
                        "These are the channel ranges that will always use" +
                                " FFmpegTransSageTVConsumerImpl."
                );

                rawConsumer = new ChannelRangesDeviceOption(
                        Config.getString("consumer.dynamic.channels.raw", ""),
                        false,
                        "Raw Consumer Channels",
                        "consumer.dynamic.channels.raw",
                        "These are the channel ranges that will always use" +
                                " RawSageTVConsumerImpl."
                );


            } catch (DeviceOptionException e) {
                logger.warn("Invalid options. Reverting to defaults => ", e);

                Config.setString("consumer.dynamic.default",
                        FFmpegTransSageTVConsumerImpl.class.getCanonicalName());
                Config.setString("consumer.dynamic.channels.ffmpeg", "");
                Config.setString("consumer.dynamic.channels.ffmpeg_trans", "");
                Config.setString("consumer.dynamic.channels.raw", "");

                continue;
            }

            break;
        }

        Config.mapDeviceOptions(
                deviceOptions,
                defaultConsumer,
                ffmpegConsumer,
                ffmpegTransConsumer,
                rawConsumer
        );
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                defaultConsumer,
                ffmpegConsumer,
                ffmpegTransConsumer,
                rawConsumer
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference =
                    DynamicConsumerImpl.deviceOptions.get(option.getProperty());

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

        updateDynamicMap();

        Config.saveConfig();
    }
}
