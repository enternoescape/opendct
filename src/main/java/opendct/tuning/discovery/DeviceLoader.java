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

public interface DeviceLoader {

    /**
     * Advertises a new capture device.
     * <p/>
     * This is used to let the device loader implementation know that the device discoverer
     * implementation has discovered a new capture device.
     *
     * @param details The details of the capture device that has been discovered.
     * @param discovery The discovery implementation that called this method.
     */
    public void advertiseDevice(DiscoveredDevice details, DeviceDiscoverer discovery);

    /**
     * Returns if the detection method should not stop discovery even if it is configured to do so.
     *
     * @return <i>true</i> if the device loader is still waiting on capture devices.
     */
    public boolean isWaitingForDevices();
}
