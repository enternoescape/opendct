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

package opendct.tuning.upnp;

import opendct.config.Config;
import opendct.config.options.BooleanDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.IntegerDeviceOption;
import opendct.tuning.discovery.BasicDiscoveredDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UpnpDiscoveredDevice extends BasicDiscoveredDevice {
    private final Logger logger = LogManager.getLogger(UpnpDiscoveredDevice.class);

    public UpnpDiscoveredDevice(String name, int id, int parentId, String description) {
        super(name, id, parentId, description);
    }

    @Override
    public DeviceOption[] getOptions() {
        try {
            return new DeviceOption[] {
                    getDeviceNameOption(),
                    new IntegerDeviceOption(
                            Config.getInteger("upnp.dct.wait_for_offline_detection_s", 8),
                            false,
                            "Offline Channel Detection Seconds",
                            "upnp.dct.wait_for_offline_detection_s",
                            "This is the value in seconds to wait after tuning a channel before" +
                                    " making a final determination on if it is tunable or not." +
                                    " This applies only to offline scanning."
                    ),
                    new IntegerDeviceOption(
                            Config.getInteger("upnp.dct.offline_detection_min_bytes", 18800),
                            false,
                            "Offline Channel Detection Bytes",
                            "upnp.dct.offline_detection_min_bytes",
                            "This is the value in bytes that must be consumed before a channel is" +
                                    " considered tunable.."
                    ),
                    new IntegerDeviceOption(
                            Config.getInteger("upnp.retune_poll_s", 1),
                            false,
                            "Re-tune Polling Seconds",
                            "upnp.retune_poll_s",
                            "This is the frequency in seconds to poll the producer to check if it" +
                                    " is stalled."
                    ),
                    new BooleanDeviceOption(
                            Config.getBoolean("upnp.dct.http_tuning", true),
                            false,
                            "HTTP Tuning",
                            "upnp.dct.http_tuning",
                            "This enables HTTP tuning for InfiniTV devices. This is a tuning" +
                                    " method that is faster than UPnP and is available on all" +
                                    " InfiniTV devices except InfiniTV 4 devices with old firmware."
                    ),
                    new BooleanDeviceOption(
                            Config.getBoolean("upnp.dct.hdhr_tuning", true),
                            false,
                            "HTTP Tuning",
                            "upnp.dct.http_tuning",
                            "This enables HDHomeRun native tuning for HDHomeRun Prime devices." +
                                    " This is a tuning method that is faster than UPnP and is" +
                                    " available on all HDHomeRun Prime devices."
                    )
            };
        } catch (DeviceOptionException e) {
            logger.error("Unable to build options for device => ", e);
        }

        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption deviceOption : deviceOptions) {
            if (deviceOption.getProperty().equals(propertiesDeviceName)) {
                setFriendlyName(deviceOption.getValue());
            }

            Config.setDeviceOption(deviceOption);
        }
    }

}
