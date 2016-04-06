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

import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.StringDeviceOption;
import opendct.tuning.discovery.NetworkDiscoveredDeviceParent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HDHomeRunDiscoveredDeviceParent extends NetworkDiscoveredDeviceParent {
    private final static Logger logger = LogManager.getLogger(HDHomeRunDiscoveredDeviceParent.class);

    HDHomeRunDevice device;

    Map<String, DeviceOption> deviceOptions;
    StringDeviceOption channelMap;

    public HDHomeRunDiscoveredDeviceParent(String name, int parentId, InetAddress localAddress, HDHomeRunDevice hdHomeRunDevice) {
        super(name, parentId, localAddress);

        device = hdHomeRunDevice;

        deviceOptions = new ConcurrentHashMap<>();

        while(true) {
            try {
                channelMap = new StringDeviceOption(
                        "",
                        false,
                        "Channel Map",
                        propertiesDeviceParent + "channel_map",
                        "Change the channel map used for tuning on this HDHomeRun device. Valid" +
                                " values are 'us-cable' and 'us-bcast'. This option has no effect" +
                                " on CableCARD devices.",
                        "",
                        "us-cable",
                        "us-bcast"
                );

                Config.mapDeviceOptions(
                        deviceOptions,
                        channelMap
                );
            } catch (DeviceOptionException e) {
                logger.warn("Invalid options. Reverting to defaults => ", e);

                Config.setString(propertiesDeviceParent + "channel_map", "");

                continue;
            }

            break;
        }

    }

    @Override
    public InetAddress getRemoteAddress() {
        return device.getIpAddress();
    }

    public HDHomeRunDevice getDevice() {
        return device;
    }

    @Override
    public DeviceOption[] getOptions() {
        DeviceOption tempArray[] = super.getOptions();
        DeviceOption returnArray[] = new DeviceOption[tempArray.length + 1];

        if (tempArray.length > 0) {
            System.arraycopy(tempArray, 0, returnArray, 0, tempArray.length);
        }

        returnArray[returnArray.length - 1] = channelMap;

        return returnArray;
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        super.setOptions(deviceOptions);

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

    public String getChannelMap() {
        return channelMap.getValue();
    }
}
