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

package opendct.tuning.upnp.listener;

import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;

public class DiscoveryRegistryListener implements RegistryListener {
    private static final Logger logger = LogManager.getLogger(DiscoveryRegistryListener.class);

    private final UpnpDiscoverer deviceDiscoverer;

    public DiscoveryRegistryListener(UpnpDiscoverer deviceDiscoverer) {
        this.deviceDiscoverer = deviceDiscoverer;
    }

    @Override
    public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice remoteDevice) {
        logger.trace("UPnP remote device '{}' SSDP datagram received and parsed.",
                remoteDevice.getDisplayString());
    }

    @Override
    public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice remoteDevice, Exception e) {
        logger.trace("UPnP remote device '{}' discovery failed => {}",
                remoteDevice.getDisplayString(), e);
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice remoteDevice) {
        try {
            switch (getDeviceType(remoteDevice)) {
                case INFINITV:
                    RegisterInfiniTVDevice.addRemoteDevice(deviceDiscoverer, remoteDevice);
                    break;
                case UNKNOWN:
                    break;
            }
        } catch (Exception e) {
            logger.error("Unexpected exception created while adding UPnP device '{}' => ",
                    remoteDevice.getDisplayString() , e);
        }
    }

    @Override
    public void remoteDeviceUpdated(Registry registry, RemoteDevice remoteDevice) {
        try {
            switch (getDeviceType(remoteDevice)) {
                case INFINITV:
                    RegisterInfiniTVDevice.updateRemoteDevice(deviceDiscoverer, remoteDevice);
                    break;
                case UNKNOWN:
                    break;
            }
        } catch (Exception e) {
            logger.error("Unexpected exception created while updating UPnP device '{}' => ",
                    remoteDevice.getDisplayString() , e);
        }
    }

    @Override
    public void remoteDeviceRemoved(Registry registry, RemoteDevice remoteDevice) {
        logger.debug("UPnP remote device '{}' has been removed.", remoteDevice.getDisplayString());
    }

    @Override
    public void localDeviceAdded(Registry registry, LocalDevice localDevice) {
        logger.trace("UPnP local device '{}' is now available.", localDevice.getDisplayString());
    }

    @Override
    public void localDeviceRemoved(Registry registry, LocalDevice localDevice) {
        logger.trace("UPnP local device '{}' have been removed.", localDevice.getDisplayString());
    }

    @Override
    public void beforeShutdown(Registry registry) {
        if (registry.getDevices().size() > 0) {
            logger.debug("Before shutdown, the registry has {} device(s).", registry.getDevices().size());
        } else {
            logger.warn("Before UPnP shutdown, the registry did not contain any devices.", registry.getDevices().size());
        }
    }

    @Override
    public void afterShutdown() {
        logger.debug("Shutdown of UPnP registry complete.");
    }

    /**
     * Get the device type so an appropriate capture device can be made available.
     * <p/>
     * Try to make this fairly quick and no synchronized methods since it will happen every time a
     * device checks in.
     *
     * @param remoteDevice The remote device.
     * @return The type of device.
     */
    private UpnpDeviceType getDeviceType(RemoteDevice remoteDevice) {
        if (remoteDevice.getDisplayString().toUpperCase().contains("INFINITV")) {
            return UpnpDeviceType.INFINITV;
        }

        return UpnpDeviceType.UNKNOWN;
    }
}
