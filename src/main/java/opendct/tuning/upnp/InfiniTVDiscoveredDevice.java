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

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.capture.InfiniTVCaptureDevice;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.model.meta.Device;

public class InfiniTVDiscoveredDevice extends UpnpDiscoveredDevice {
    private final static Logger logger = LogManager.getLogger(InfiniTVDiscoveredDevice.class);

    private final Device device;
    private final InfiniTVDiscoveredDeviceParent parent;

    public InfiniTVDiscoveredDevice(String name, int id, int parentId, String description,
                                    InfiniTVDiscoveredDeviceParent parent, Device device) {

        super(name, id, parentId, description);
        this.device = device;
        this.parent = parent;
    }

    @Override
    public CaptureDevice loadCaptureDevice()
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {

        return new InfiniTVCaptureDevice(this, parent);
    }
}
