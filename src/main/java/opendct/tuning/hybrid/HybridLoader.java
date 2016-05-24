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

import opendct.tuning.discovery.discoverers.HybridDiscoverer;

public class HybridLoader implements Runnable {
    String devices[];
    HybridDiscoverer discoverer;

    public HybridLoader(String devices[], HybridDiscoverer discoverer) {
        this.devices = devices;
        this.discoverer = discoverer;
    }

    @Override
    public void run() {
        for (String device : devices) {
            HybridDiscoveredDeviceParent loadParent = new HybridDiscoveredDeviceParent(device, device.hashCode());
            HybridDiscoveredDevice loadDevice = new HybridDiscoveredDevice(device, device.hashCode(), device.hashCode(), loadParent);

            // TODO: add device
            //discoverer.addCaptureDevice(loadDevice, loadParent);
        }
    }
}
