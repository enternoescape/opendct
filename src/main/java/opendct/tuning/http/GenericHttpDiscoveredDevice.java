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

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
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
    private StringDeviceOption customChannels;
    private IntegerDeviceOption padChannel;

    GenericHttpDiscoveredDeviceParent parent;

    public GenericHttpDiscoveredDevice(String name, int id, int parentId, GenericHttpDiscoveredDeviceParent parent) {
        super(name, id, parentId, "Generic HTTP Capture Device");
        this.parent = parent;

        propertiesDeviceRoot = "sagetv.device." + id + ".";
        deviceOptions = new ConcurrentHashMap<>(5);

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
                    "This will optionally execute to change the channel being streamed. Insert %c%" +
                            " where the channel needs to be provided to the executable. If %c%" +
                            " isn't provided, but this property is populated, the channel number" +
                            " will be appended as a final parameter."
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

            padChannel = new IntegerDeviceOption(
                    Config.getInteger(propertiesDeviceRoot + "channel_padding", 0),
                    false,
                    "Channel Padding",
                    propertiesDeviceRoot + "channel_padding",
                    "This is the minimum length to be passed for the %c% variable. Values shorter" +
                            " than this length will have zeros (0) appended to the left of the" +
                            " channel to make up the difference. (Ex. 8 becomes 008 if this is" +
                            " set to 3.)"
            );

            Config.mapDeviceOptions(
                    deviceOptions,
                    streamingUrl,
                    customChannels,
                    pretuneExecutable,
                    tuningExecutable,
                    stoppingExecutable,
                    tuningDelay,
                    padChannel
            );

            // This information is used to ensure that the interface that will be used to
            // communicate with the device will be waited for when resuming from suspend.
            try {
                URL getRemoteIP = new URL(streamingUrl.getValue());
                parent.setRemoteAddress(InetAddress.getByName(getRemoteIP.getHost()));
            } catch (MalformedURLException e) {
                logger.debug("Unable to parse remote address => ", e);
            } catch (UnknownHostException e) {
                logger.debug("Unable to resolve remote address => ", e);
            }

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
                customChannels,
                pretuneExecutable,
                tuningExecutable,
                stoppingExecutable,
                tuningDelay,
                padChannel
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

    public String getCustomChannels() {
        return customChannels.getValue();
    }

    public int getPadChannel() {
        return padChannel.getInteger();
    }
}