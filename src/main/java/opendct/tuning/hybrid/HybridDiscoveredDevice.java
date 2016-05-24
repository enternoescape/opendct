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

package opendct.tuning.hybrid;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.config.Config;
import opendct.config.options.*;
import opendct.tuning.discovery.BasicDiscoveredDevice;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HybridDiscoveredDevice extends BasicDiscoveredDevice {
    private final static Logger logger = LogManager.getLogger(BasicDiscoveredDevice.class);

    // Pre-pend this value for saving and getting properties related to just this tuner.
    protected final String propertiesDeviceRoot;

    private final Map<String, DeviceOption> deviceOptions;
    private CaptureDeviceOption primaryDevice;
    private CaptureDeviceOption secondaryDevice;
    private BooleanDeviceOption secondaryOnDrm;
    private ChannelRangesDeviceOption secondaryChannelRanges;
    private BooleanDeviceOption secondaryPooling;

    private HybridDiscoveredDeviceParent parent;

    public HybridDiscoveredDevice(String name, int id, int parentId, HybridDiscoveredDeviceParent parent) {
        super(name, id, parentId, "Hybrid Capture Device");
        this.parent = parent;

        propertiesDeviceRoot = "sagetv.device." + id + ".";
        deviceOptions = new ConcurrentHashMap<>();

        try {
            primaryDevice = new CaptureDeviceOption(
                    Config.getString(propertiesDeviceRoot + "primary_device", ""),
                    "Primary Device",
                    propertiesDeviceRoot + "primary_device",
                    "This is the name of the capture device to be used by default."
            );

            secondaryDevice = new CaptureDeviceOption(
                    Config.getString(propertiesDeviceRoot + "secondary_device", ""),
                    "Secondary Device",
                    propertiesDeviceRoot + "secondary_device",
                    "This is the name of the capture device to be used conditionally."
            );

            secondaryPooling = new BooleanDeviceOption(
                    Config.getBoolean(propertiesDeviceRoot + "secondary_pooling", false),
                    false,
                    "Enable Secondary Pooling",
                    propertiesDeviceRoot + "secondary_pooling",
                    "This will enable the selection of any available device in the same pool as" +
                            " the secondary device."
            );

            secondaryOnDrm = new BooleanDeviceOption(
                    Config.getBoolean(propertiesDeviceRoot + "secondary_on_drm", false),
                    false,
                    "Secondary on DRM",
                    propertiesDeviceRoot + "secondary_on_drm",
                    "Switch to the secondary device when DRM is detected."
            );

            secondaryChannelRanges = new ChannelRangesDeviceOption(
                    Config.getString(propertiesDeviceRoot + "secondary_channels_csv", ""),
                    false,
                    "Secondary on Channels",
                    propertiesDeviceRoot + "secondary_channels_csv",
                    "Switch to the secondary device when specific channels are tuned in. This can" +
                            " be combined with DRM detection and will generally be faster than" +
                            " always checking for DRM before switching."
            );

            Config.mapDeviceOptions(
                    deviceOptions,
                    primaryDevice,
                    secondaryDevice,
                    secondaryPooling,
                    secondaryOnDrm,
                    secondaryChannelRanges
            );
        } catch (DeviceOptionException e) {
            logger.error("Unable to load the options for the hybrid capture device '{}'",
                    parent.getFriendlyName());
        }
    }

    @Override
    public CaptureDevice loadCaptureDevice() throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        return null;
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                primaryDevice,
                secondaryDevice,
                secondaryPooling,
                secondaryOnDrm,
                secondaryChannelRanges
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

    public String getPrimaryDevice() {
        return primaryDevice.getValue();
    }

    public String getSecondaryDevice() {
        return secondaryDevice.getValue();
    }

    public boolean getSecondaryOnDrm() {
        return secondaryOnDrm.getBoolean();
    }

    public String[] getSecondaryChannelRanges() {
        return ChannelRangesDeviceOption.parseRanges(secondaryChannelRanges.getValue());
    }

    public boolean getSecondaryPooling() {
        return secondaryPooling.getBoolean();
    }
}
