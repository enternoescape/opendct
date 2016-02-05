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

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.power.NetworkPowerEventManger;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketException;

public class DeviceLoaderImpl implements DeviceLoader {
    private final Logger logger = LogManager.getLogger(DeviceLoaderImpl.class);

    @Override
    public synchronized void advertiseDevice(DiscoveredDevice details, DeviceDiscoverer discovery) {
        if (DiscoveryManager.isDevicePermitted(details.getId())) {
            return;
        }

        try {
            CaptureDevice captureDevice = discovery.loadCaptureDevice(details.getId());
            SageTVManager.addCaptureDevice(captureDevice);

            DiscoveredDeviceParent parent = discovery.getDeviceParentDetails(details.getParentId());

            if (parent != null) {
                if (parent.isNetworkDevice()) {
                    if (parent.getRemoteAddress() == null) {
                        logger.warn("The capture device parent '{}' reports that it is a network" +
                                " device, but does not have a remote address.",
                                parent.getName());

                    } else {
                        NetworkPowerEventManger.POWER_EVENT_LISTENER.addDependentInterface(parent.getRemoteAddress());
                    }
                }
            } else {
                logger.warn("The capture device '{}' does not have a parent.",
                        details.getName());

            }
        } catch (CaptureDeviceIgnoredException e) {
            logger.error("Not permitted to load the capture device '{}', id {} => ",
                    details.getName(), details.getId(), e);

        } catch (CaptureDeviceLoadException e) {
            logger.error("Unable to load the capture device '{}', id {} => ",
                    details.getName(), details.getId(), e);

        } catch (SocketException e) {
            logger.error("Unable to open a socket for the capture device '{}', id {} => ",
                    details.getName(), details.getId(), e);

        } catch (Exception e) {
            logger.error("Unexpected exception created by the capture device '{}', id {} => ",
                    details.getName(), details.getId(), e);

        }
    }

    @Override
    public boolean isWaitingForDevices() {
        return !SageTVManager.captureDevicesLoaded();
    }
}
