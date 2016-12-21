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

package opendct.tuning.pipe;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.capture.GenericPipeCaptureDevice;
import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.IntegerDeviceOption;
import opendct.config.options.StringDeviceOption;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.tuning.discovery.BasicDiscoveredDevice;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericPipeDiscoveredDevice extends BasicDiscoveredDevice {
    private final static Logger logger = LogManager.getLogger(GenericPipeDiscoveredDevice.class);

    // Pre-pend this value for saving and getting properties related to just this tuner.
    protected final String propertiesDeviceRoot;

    private final Map<String, DeviceOption> deviceOptions;
    private StringDeviceOption streamingExecutable;
    private StringDeviceOption stoppingExecutable;
    private IntegerDeviceOption tuningDelay;
    private StringDeviceOption customChannels;

    private GenericPipeDiscoveredDeviceParent parent;

    public GenericPipeDiscoveredDevice(String name, int id, int parentId, GenericPipeDiscoveredDeviceParent parent) {
        super(name, id, parentId, "Generic Pipe Capture Device");

        this.parent = parent;

        propertiesDeviceRoot = "sagetv.device." + id + ".";
        deviceOptions = new ConcurrentHashMap<>(10);

        try {
            streamingExecutable = new StringDeviceOption(
                    Config.getString(propertiesDeviceRoot + "streaming_executable", ""),
                    false,
                    "Streaming Executable",
                    propertiesDeviceRoot + "streaming_executable",
                    "This is the entire path to an executable or script that will tune in the" +
                            " desired channel if needed and start streaming it to standard" +
                            " output. All data directed to error output will be displayed in the" +
                            " logs and could be used for troubleshooting. The desired channel" +
                            " will be appended as a parameter for the script. If you need to use" +
                            " a batch file or PowerShell, you will need to prepend the 'cmd /c '" +
                            " or 'PowerShell -File ' respectively for execution to work."
            );

            stoppingExecutable = new StringDeviceOption(
                    Config.getString(propertiesDeviceRoot + "stopping_executable", ""),
                    false,
                    "Stopping Executable",
                    propertiesDeviceRoot + "stopping_executable",
                    "This is an optional path to an executable or script that will tell the" +
                            " streaming script to stop streaming gracefully. If this script takes" +
                            " over 15 seconds to execute, it and the streaming executable will be" +
                            " forcefully terminated. If this script is not provided, the" +
                            " streaming executable will be forcefully terminated."
            );

            tuningDelay = new IntegerDeviceOption(
                    Config.getInteger(propertiesDeviceRoot + "tuning_delay_ms", 0),
                    false,
                    "Tuning Delay",
                    propertiesDeviceRoot + "tuning_delay_ms",
                    "This is the amount of time in milliseconds to wait after tuning a channel" +
                            " before starting to stream anything."
            );

            customChannels = new StringDeviceOption(
                    Config.getString(propertiesDeviceRoot + "custom_channels", ""),
                    false,
                    "Custom Channels",
                    propertiesDeviceRoot + "custom_channels",
                    "This is an optional semicolon delimited list of" +
                            " channels you want to appear in SageTV for this device. This is a" +
                            " shortcut around creating an actual OpenDCT lineup. If there are any" +
                            " values in the field, they will override the lineup assigned to this" +
                            " capture device on channel scan. This provides an easy way to add" +
                            " channels if you are not actually going to use guide data."
            );

            Config.mapDeviceOptions(
                    deviceOptions,
                    streamingExecutable,
                    stoppingExecutable,
                    tuningDelay,
                    customChannels
            );

        } catch (DeviceOptionException e) {
            logger.error("Unable to load the options for the generic pipe capture device '{}'",
                    parent.getFriendlyName());
        }
    }

    @Override
    public CaptureDevice loadCaptureDevice() throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        return new GenericPipeCaptureDevice(parent, this);
    }

    @Override
    public DeviceOption[] getOptions() {
        try {
            return new DeviceOption[] {
                    getDeviceNameOption(),
                    streamingExecutable,
                    stoppingExecutable,
                    tuningDelay,
                    customChannels
            };
        } catch (DeviceOptionException e) {
            logger.error("Unable to build options for device => ", e);
        }

        return new DeviceOption[0];
    }

    @Override
    public void setOptions(JsonOption... deviceOptions) throws DeviceOptionException {
        for (JsonOption option : deviceOptions) {

            if (option.getProperty().equals(propertiesDeviceName)) {
                setFriendlyName(option.getValue());
                Config.setJsonOption(option);
                continue;
            }

            DeviceOption optionReference = this.deviceOptions.get(option.getProperty());

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

        Config.saveConfig();
    }

    public String getStreamingExecutable() {
        return streamingExecutable.getValue();
    }

    public String getStoppingExecutable() {
        return stoppingExecutable.getValue();
    }

    public int getTuningDelay() {
        return tuningDelay.getInteger();
    }

    public String getCustomChannels() {
        return customChannels.getValue();
    }
}
