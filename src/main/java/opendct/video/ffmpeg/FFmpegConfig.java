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

package opendct.video.ffmpeg;

import opendct.config.Config;
import opendct.config.options.BooleanDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.IntegerDeviceOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class FFmpegConfig {
    private static final Logger logger = LogManager.getLogger(FFmpegConfig.class);

    private static final ConcurrentHashMap<String, DeviceOption> deviceOptions;

    private static BooleanDeviceOption uploadIdEnabled;
    private static IntegerDeviceOption circularBufferSize;
    private static IntegerDeviceOption minProbeSize;
    private static IntegerDeviceOption minAnalyseDuration;
    private static IntegerDeviceOption maxAnalyseDuration;
    private static IntegerDeviceOption rwBufferSize;
    private static IntegerDeviceOption minUploadIdTransferSize;
    private static IntegerDeviceOption minDirectFlush;
    private static IntegerDeviceOption threadPriority;
    private static IntegerDeviceOption uploadIdPort;
    private static BooleanDeviceOption h264PtsHack;

    static {
        deviceOptions = new ConcurrentHashMap<>();

        initDeviceOptions();

        Config.mapDeviceOptions(
                deviceOptions,
                uploadIdEnabled,
                circularBufferSize,
                minProbeSize,
                minAnalyseDuration,
                maxAnalyseDuration,
                rwBufferSize,
                minUploadIdTransferSize,
                minDirectFlush,
                threadPriority,
                uploadIdPort,
                h264PtsHack
        );
    }

    private static void initDeviceOptions() {

        while (true) {
            try {
                uploadIdEnabled = new BooleanDeviceOption(
                        Config.getBoolean("consumer.ffmpeg.upload_id_enabled", false),
                        false,
                        "Enable Upload ID",
                        "consumer.ffmpeg.upload_id_enabled",
                        "This enables the use of upload ID with SageTV for writing out recordings.");

                circularBufferSize = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.circular_buffer_size", 7864320),
                        false,
                        "Circular Buffer Size",
                        "consumer.ffmpeg.circular_buffer_size",
                        "This is the starting size of the circular buffer. The buffer can grow up" +
                                " to 3 times its initial size during stream detection. Once" +
                                " stream detection is done, the buffer will not get any larger." +
                                " This value cannot be less than 6740844 bytes and cannot be" +
                                " greater than 33554432 bytes.",
                        6740844,
                        33554432);

                minProbeSize = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.min_probe_size", 165440),
                        false,
                        "Minimum Probe Size",
                        "consumer.ffmpeg.min_probe_size",
                        "This is the smallest amount of data in bytes to be probed. Increase this" +
                                " size if you are noticing very bad CPU spikes when starting" +
                                " recordings. This value cannot be less than 82720 and cannot" +
                                " exceed 6740844.",
                        82720,
                        6740844);

                minAnalyseDuration = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.min_analyze_duration", 165440),
                        false,
                        "Minimum Analyze Duration",
                        "consumer.ffmpeg.min_analyze_duration",
                        "This is the shortest amount of time in microseconds that FFmpeg will" +
                                " probe the stream. This value cannot be less than 82720 and" +
                                " cannot be greater than 5000000.",
                        82720,
                        5000000);

                maxAnalyseDuration = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.max_analyze_duration", 5000000),
                        false,
                        "Maximum Analyze Duration",
                        "consumer.ffmpeg.max_analyze_duration",
                        "This is the longest amount of time in microseconds that FFmpeg will" +
                                " probe the stream. This value cannot be less than 5000000 and" +
                                " cannot be greater than 10000000.",
                        5000000,
                        10000000);

                rwBufferSize = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.rw_buffer_size", 262144),
                        false,
                        "Read/Write Transfer Buffer Size",
                        "consumer.ffmpeg.rw_buffer_size",
                        "This is the size of the buffers in bytes to be created for reading data" +
                                " into and writing data out of FFmpeg to disk. The consumer will" +
                                " use 2-3 times this value in memory. This value cannot be less" +
                                " than 65536 and cannot be greater than 1048576.",
                        65536,
                        1048576);

                minUploadIdTransferSize = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.min_upload_id_transfer_size", 262144),
                        false,
                        "Minimum Upload ID Transfer Size",
                        "consumer.ffmpeg.min_upload_id_transfer_size",
                        "This is the minimum amount of data that must be collected from FFmpeg" +
                                " before it will be sent over to SageTV. If this value is less" +
                                " than the Read/Write Transfer Buffer Size, this value will be" +
                                " increased to match it. This value cannot be less than 65536 and" +
                                " cannot be greater than 1048576.",
                        65536,
                        1048576);

                minDirectFlush = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.min_direct_flush_size", 1048576),
                        false,
                        "Minimum Direct Flush Size",
                        "consumer.ffmpeg.min_direct_flush_size",
                        "This is the minimum amount of data that must be written to disk before" +
                                " it is verified that the data has been written to disk when" +
                                " directly recording to disk. If the file size is 0 bytes, a" +
                                " flush is forced and the file size is checked again. If the file" +
                                " size doesn't change, the file it re-created. Set this value to" +
                                " -1 to disable flushing. This value cannot be greater than" +
                                " 2147483647.",
                        -1,
                        Integer.MAX_VALUE);

                threadPriority = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.thread_priority", Thread.MAX_PRIORITY - 2),
                        false,
                        "FFmpeg Thread Priority",
                        "consumer.ffmpeg.thread_priority",
                        "This is the priority given to the FFmpeg processing thread. A higher" +
                                " number means higher priority. Only change this value for" +
                                " troubleshooting. This value cannot be less than 1 and cannot be" +
                                " greater than 10.",
                        Thread.MIN_PRIORITY,
                        Thread.MAX_PRIORITY);

                uploadIdPort = new IntegerDeviceOption(
                        Config.getInteger("consumer.ffmpeg.upload_id_port", 7818),
                        false,
                        "SageTV Upload ID Port",
                        "consumer.ffmpeg.upload_id_port",
                        "This is the port number used to communicate with SageTV when using" +
                                " upload ID for recording. You only need to change this value if" +
                                " you have changed it in SageTV. This value cannot be less than" +
                                " 1024 and cannot be greater than 65535.",
                        1024,
                        65535);

                h264PtsHack = new BooleanDeviceOption(
                        Config.getBoolean("consumer.ffmpeg.h264_pts_hack", false),
                        false,
                        "H.264 PTS Hack",
                        "consumer.ffmpeg.h264_pts_hack",
                        "This enables the removal of out of order PTS frames on H.264 720p" +
                                " content so that it will playback correctly on the Fire TV and" +
                                " Nexus. When enabled, this will only apply to 1280x720 H.264" +
                                " non-interlaced content. Disable this if you notice interlaced" +
                                " video not playing back smoothly."
                        );

            } catch (DeviceOptionException e) {
                logger.warn("Invalid options. Reverting to defaults => ", e);

                Config.setBoolean("consumer.ffmpeg.upload_id_enabled", false);
                Config.setInteger("consumer.ffmpeg.circular_buffer_size", 7864320);
                Config.setInteger("consumer.ffmpeg.min_probe_size", 165440);
                Config.setInteger("consumer.ffmpeg.min_analyze_duration", 165440);
                Config.setInteger("consumer.ffmpeg.max_analyze_duration", 5000000);
                Config.setInteger("consumer.ffmpeg.rw_buffer_size", 262144);
                Config.setInteger("consumer.ffmpeg.min_upload_id_transfer_size", 262144);
                Config.setInteger("consumer.ffmpeg.min_direct_flush_size", 1048576);
                Config.setInteger("consumer.ffmpeg.thread_priority", Thread.MAX_PRIORITY - 2);
                Config.setInteger("consumer.ffmpeg.upload_id_port", 7818);
                Config.setBoolean("consumer.ffmpeg.h264_pts_hack", false);
                continue;
            }

            break;
        }
    }

    public static DeviceOption[] getFFmpegOptions() {
        return new DeviceOption[] {
                uploadIdEnabled,
                circularBufferSize,
                minProbeSize,
                minAnalyseDuration,
                maxAnalyseDuration,
                rwBufferSize,
                minUploadIdTransferSize,
                minDirectFlush,
                threadPriority,
                uploadIdPort
        };
    }

    public static DeviceOption[] getFFmpegTransOptions() {
        return new DeviceOption[] {
                uploadIdEnabled,
                circularBufferSize,
                minProbeSize,
                minAnalyseDuration,
                maxAnalyseDuration,
                rwBufferSize,
                minUploadIdTransferSize,
                minDirectFlush,
                threadPriority,
                uploadIdPort,
                h264PtsHack
        };
    }

    public static void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference = FFmpegConfig.deviceOptions.get(option.getProperty());

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

    public static boolean getUploadIdEnabled() {
        return uploadIdEnabled.getBoolean();
    }

    public static int getCircularBufferSize() {
        return circularBufferSize.getInteger();
    }

    public static int getMinProbeSize() {
        return minProbeSize.getInteger();
    }

    public static int getMinAnalyseDuration() {
        return minAnalyseDuration.getInteger();
    }

    public static int getMaxAnalyseDuration() {
        return maxAnalyseDuration.getInteger();
    }

    public static int getRwBufferSize() {
        return rwBufferSize.getInteger();
    }

    public static int getMinUploadIdTransferSize() {
        return minUploadIdTransferSize.getInteger();
    }

    public static int getMinUploadIdTransferSize(int rwBufferSize) {
        if (rwBufferSize > minUploadIdTransferSize.getInteger()) {
            try {
                minUploadIdTransferSize.setValue(rwBufferSize);
            } catch (DeviceOptionException e) {
                logger.warn("Unable to update minUploadIdTransferSize => ", e);
            }

            return rwBufferSize;
        }

        return minUploadIdTransferSize.getInteger();
    }

    public static int getMinDirectFlush() {
        return minDirectFlush.getInteger();
    }

    public static int getThreadPriority() {
        return threadPriority.getInteger();
    }

    public static int getUploadIdPort() {
        return uploadIdPort.getInteger();
    }

    public static boolean getH264PtsHack() {
        return h264PtsHack.getBoolean();
    }
}
