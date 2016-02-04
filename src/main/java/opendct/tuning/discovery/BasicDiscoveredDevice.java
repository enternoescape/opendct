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

public abstract class BasicDiscoveredDevice implements DiscoveredDevice {
    private final String name;
    private final int id;
    private final int parentId;
    private String friendlyName;
    private String description;

    protected final String propertiesDeviceRoot;
    protected final String propertiesDeviceParent;
    protected final String propertiesDeviceName;

    public BasicDiscoveredDevice(String name, int id, int parentId, String description) {
        this(name, name, id, parentId, description);
    }

    public BasicDiscoveredDevice(String name, String friendlyName, int id, int parentId, String description) {
        this.name = name;
        this.id = id;
        this.parentId = parentId;
        this.description = description;

        propertiesDeviceRoot = "sagetv.device." + id + ".";
        propertiesDeviceParent = "sagetv.device.parent." + parentId + ".";
        propertiesDeviceName = propertiesDeviceRoot + "device_name";

        this.friendlyName = Config.getString(propertiesDeviceName, friendlyName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BasicDiscoveredDevice that = (BasicDiscoveredDevice) o;

        if (id != that.id) return false;
        return parentId == that.parentId;

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + parentId;
        return result;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getFriendlyName() {
        return friendlyName;
    }

    protected void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int getParentId() {
        return parentId;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public DeviceOption getDeviceNameOption() throws DeviceOptionException {
        return new StringDeviceOption(
                friendlyName,
                false,
                "Name",
                propertiesDeviceName,
                "This is the name of the capture device that SageTV will use. If this" +
                        " name is changed on a device already in use in SageTV, you" +
                        " will need to re-add the device."
        );
    }
}
