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
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.tuning.discovery.BasicDiscoveredDevice;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HDHomeRunDiscoveredDevice extends BasicDiscoveredDevice {
    private final Logger logger = LogManager.getLogger(HDHomeRunDiscoveredDevice.class);

    public HDHomeRunDiscoveredDevice(String name, int id, int parentId, String description) {
        super(name, id, parentId, description);
    }

    @Override
    public CaptureDevice loadCaptureDevice() throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        return null;
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {

    }
}
