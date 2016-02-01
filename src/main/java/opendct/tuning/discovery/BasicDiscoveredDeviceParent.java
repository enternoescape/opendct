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

package opendct.tuning.discovery;

import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.StringDeviceOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BasicDiscoveredDeviceParent implements DiscoveredDeviceParent {
    private final Logger logger = LogManager.getLogger(BasicDiscoveredDeviceParent.class);

    private final String name;
    private final int parentId;
    private String friendlyName;

    protected final String propertiesDeviceParent;
    protected final String propertiesDeviceParentName;

    public BasicDiscoveredDeviceParent(String name, int parentId) {
        this.name = name;
        this.parentId = parentId;

        propertiesDeviceParent = "sagetv.device.parent." + parentId + ".";
        propertiesDeviceParentName = propertiesDeviceParent + "device_name";

        this.friendlyName = Config.getString(propertiesDeviceParentName, friendlyName);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFriendlyName() {
        return friendlyName;
    }

    protected void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public int getParentId() {
        return parentId;
    }

    public DeviceOption getParentNameOption() throws DeviceOptionException {
        return new StringDeviceOption(
                friendlyName,
                false,
                "Name",
                propertiesDeviceParentName,
                "This is the name of the parent capture device. The name of this device is for" +
                        " display purposes only and it not used in SageTV."
        );
    }

    @Override
    public DeviceOption[] getOptions() {
        try {
            return new DeviceOption[] {
                    getParentNameOption()
            };
        } catch (DeviceOptionException e) {
            logger.error("getOptions is unable to return options => ", e);
        }

        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            if (option.getProperty().equals(propertiesDeviceParentName)) {
                if (Config.setUniqueParentName(getParentId(), option.getValue())) {
                    setFriendlyName(option.getValue());
                } else {
                    throw new DeviceOptionException("The requested new parent device name" +
                            " conflicts with another parent device with the same name.", option);
                }
            }
        }
    }
}
