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

package opendct.tuning.hdhomerun;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.capture.HDHRNativeCaptureDevice;
import opendct.config.Config;
import opendct.config.options.BooleanDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.tuning.discovery.BasicDiscoveredDevice;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HDHomeRunDiscoveredDevice extends BasicDiscoveredDevice {
    private final static Logger logger = LogManager.getLogger(HDHomeRunDiscoveredDevice.class);

    // Pre-pend this value for saving and getting properties related to just this tuner.
    protected final String propertiesDeviceRoot;

    private final Map<String, DeviceOption> deviceOptions;
    private BooleanDeviceOption forceExternalUnlock;

    private int tunerNumber;
    private HDHomeRunDiscoveredDeviceParent deviceParent;

    public HDHomeRunDiscoveredDevice(String name, int id, int parentId, String description, int tunerNumber, HDHomeRunDiscoveredDeviceParent deviceParent) {
        super(name, id, parentId, description);

        this.tunerNumber = tunerNumber;
        this.deviceParent = deviceParent;

        propertiesDeviceRoot = "sagetv.device." + id + ".";
        deviceOptions = new ConcurrentHashMap<>(1);

        while(true) {
            try {
                forceExternalUnlock = new BooleanDeviceOption(
                        Config.getBoolean(propertiesDeviceRoot + "always_force_external_unlock", false),
                        false,
                        "Always Force Unlock",
                        propertiesDeviceRoot + "always_force_external_unlock",
                        "This will allow the program to always override the HDHomeRun lock when" +
                                " SageTV requests a channel to be tuned."
                );

                Config.mapDeviceOptions(
                        deviceOptions,
                        forceExternalUnlock
                );
            } catch (DeviceOptionException e) {
                logger.warn("Invalid options. Reverting to defaults => ", e);

                Config.setBoolean(propertiesDeviceRoot + "always_force_external_unlock", false);

                continue;
            }

            break;
        }
    }

    @Override
    public CaptureDevice loadCaptureDevice() throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {

        return new HDHRNativeCaptureDevice(deviceParent, this);
    }

    public int getTunerNumber() {
        return tunerNumber;
    }

    @Override
    public DeviceOption[] getOptions() {
        try {
            return new DeviceOption[] {
                    getDeviceNameOption(),
                    forceExternalUnlock
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

    public boolean getForceExternalUnlock() {
        return forceExternalUnlock.getBoolean();
    }
}
