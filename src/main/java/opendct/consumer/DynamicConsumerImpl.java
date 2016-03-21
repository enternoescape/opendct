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
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicConsumerImpl implements SageTVConsumer {
    private static final Logger logger = LogManager.getLogger(DynamicConsumerImpl.class);

    private final static ConcurrentHashMap<String, DeviceOption> deviceOptions;

    private static final HashMap<String, String> dynamicMaps;
    private static StringDeviceOption defaultConsumer;
    private static ChannelRangesDeviceOption ffmpegConsumer;
    private static ChannelRangesDeviceOption ffmpegTransConsumer;
    private static ChannelRangesDeviceOption rawConsumer;

    private long bufferSize;
    private boolean consumeToNull;
    private String encodeQuality;
    private int desiredProgram;

    private String channel;
    private SageTVConsumer sageTVConsumer;

    static {
        dynamicMaps = new HashMap<>();
        deviceOptions = new ConcurrentHashMap<>();

        initDeviceOptions();
    }

    public DynamicConsumerImpl() throws IllegalAccessError {
        StackTraceElement elements[] = Thread.currentThread().getStackTrace();

        // Verify that we are not going in a circle by referencing this class.
        for (StackTraceElement element : elements) {
            if (element.getClassName().endsWith(DynamicConsumerImpl.class.getName())) {
                throw new IllegalAccessError("DynamicConsumerImpl cannot be used to initialize" +
                        " itself because it will create an endless loop.");
            }
        }
    }

    @Override
    public void run() {
        if (sageTVConsumer == null) {
            setConsumer();
        }

        updateConsumer();


    }

    private void setConsumer() {
        String consumerName;

        if (channel == null) {
            logger.warn("Unable to select a determine what consumer is requested because a " +
                    "channel was not provided. Using default '{}'", defaultConsumer);

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

        sageTVConsumer = Config.getSageTVConsumer(null, consumerName);
    }

    private void updateConsumer() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            consumer.setRecordBufferSize(bufferSize);
            consumer.consumeToNull(consumeToNull);
            consumer.setEncodingQuality(encodeQuality);
            consumer.setProgram(desiredProgram);
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        sageTVConsumer.write(bytes, offset, length);
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

        setConsumer();

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

        setConsumer();

        consumer = sageTVConsumer;
        return consumer != null && consumer.acceptsUploadID();
    }

    @Override
    public boolean acceptsFilename() {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.acceptsFilename();
        }

        setConsumer();

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

        setConsumer();
        updateConsumer();

        consumer = sageTVConsumer;
        return consumer != null && consumer.consumeToUploadID(filename, uploadId, socketAddress);
    }

    @Override
    public boolean consumeToFilename(String filename) {
        SageTVConsumer consumer = sageTVConsumer;

        if (consumer != null) {
            return consumer.consumeToFilename(filename);
        }

        setConsumer();
        updateConsumer();

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

        setConsumer();
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

        return consumer != null ? consumer.stateMessage() : "";
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
                        Config.getSageTVConsumers());

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
        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {

    }
}
