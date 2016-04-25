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

package opendct.tuning.http;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.capture.GenericHttpCaptureDevice;
import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.IntegerDeviceOption;
import opendct.config.options.StringDeviceOption;
import opendct.tuning.discovery.BasicDiscoveredDevice;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericHttpDiscoveredDevice extends BasicDiscoveredDevice {
    private final static Logger logger = LogManager.getLogger(GenericHttpDiscoveredDevice.class);

    // Pre-pend this value for saving and getting properties related to just this tuner.
    protected final String propertiesDeviceRoot;

    private final Map<String, DeviceOption> deviceOptions;
    private StringDeviceOption streamingUrl;
    private StringDeviceOption pretuneExecutable;
    private StringDeviceOption tuningExecutable;
    private StringDeviceOption stoppingExecutable;
    private IntegerDeviceOption tuningDelay;

    GenericHttpDiscoveredDeviceParent parent;

    public GenericHttpDiscoveredDevice(String name, int id, int parentId, GenericHttpDiscoveredDeviceParent parent) {
        super(name, id, parentId, "Generic HTTP Capture Device");
        this.parent = parent;

        propertiesDeviceRoot = "sagetv.device." + id + ".";
        deviceOptions = new HashMap<>(2);

        try {
            streamingUrl = new StringDeviceOption(
                    Config.getString(propertiesDeviceRoot + "streaming_url", ""),
                    false,
                    "Streaming URL",
                    propertiesDeviceRoot + "streaming_url",
                    "This is the entire URL to be read for streaming from this device."
            );

            pretuneExecutable = new StringDeviceOption(
                    Config.getString(propertiesDeviceRoot + "pretuning_executable", ""),
                    false,
                    "Pre-Tuning Executable",
                    propertiesDeviceRoot + "pretuning_executable",
                    "This will optionally execute every time before changing the channel. Insert" +
                            " %c% if the channel needs to be provided to the executable if %c%" +
                            " isn't provided, the channel number will not be provided."
            );

            tuningExecutable = new StringDeviceOption(
                    Config.getString(propertiesDeviceRoot + "tuning_executable", ""),
                    false,
                    "Tuning Executable",
                    propertiesDeviceRoot + "tuning_executable",
                    "This is the program that will execute to change the channel being streamed." +
                            " Insert %c% where the channel needs to be provided to the executable" +
                            " if %c% isn't provided, the channel number will be appended as a" +
                            " final parameter."
            );

            stoppingExecutable = new StringDeviceOption(
                    Config.getString(propertiesDeviceRoot + "stopping_executable", ""),
                    false,
                    "Stopping Executable",
                    propertiesDeviceRoot + "stopping_executable",
                    "This will optionally execute every time the capture device is stopped." +
                            " Insert %c% if the last channel needs to be provided to the" +
                            " executable if %c% isn't provided, the channel number will not be" +
                            " provided."
            );

            tuningDelay = new IntegerDeviceOption(
                    Config.getString(propertiesDeviceRoot + "tuning_delay_ms", ""),
                    false,
                    "Tuning Delay (ms)",
                    propertiesDeviceRoot + "tuning_delay_ms",
                    "This is the amount of time in milliseconds to wait after tuning a channel" +
                            " before starting to stream anything."
            );

            Config.mapDeviceOptions(
                    deviceOptions,
                    streamingUrl,
                    pretuneExecutable,
                    tuningExecutable,
                    stoppingExecutable,
                    tuningDelay
            );
        } catch (DeviceOptionException e) {
            logger.error("Unable to load the options for the generic capture device '{}'",
                    parent.getFriendlyName());
        }
    }

    @Override
    public CaptureDevice loadCaptureDevice() throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        return new GenericHttpCaptureDevice(parent, this);
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                streamingUrl,
                pretuneExecutable,
                tuningExecutable,
                stoppingExecutable,
                tuningDelay
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference = this.deviceOptions.get(option.getProperty());

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

    public String getTuningExecutable() {
        return tuningExecutable.getValue();
    }

    public String getStreamingUrl() {
        return streamingUrl.getValue();
    }

    public int getTuningDelay() {
        return tuningDelay.getInteger();
    }

    public String getPretuneExecutable() {
        return pretuneExecutable.getValue();
    }
}
