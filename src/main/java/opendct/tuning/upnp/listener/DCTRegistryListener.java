/*
 * Copyright 2015 The OpenDCT Authors. All Rights Reserved
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;

public class DCTRegistryListener implements RegistryListener {
    private static final Logger logger = LogManager.getLogger(DCTRegistryListener.class);

    public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
        logger.entry();
        logger.trace("UPnP remote device '{}' SSDP datagram received and parsed.", device.getDisplayString());
        logger.exit();
    }

    public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception e) {
        logger.entry();
        logger.trace("UPnP remote device '{}' discovery failed => {}", device.getDisplayString(), e);
        logger.exit();
    }

    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        logger.entry();

        // This will filter what we actually end up seeing in the logs.
        if (device.getDisplayString().toUpperCase().contains("PRIME") || device.getDisplayString().toUpperCase().contains("INFINITV")) {
            logger.info("UPnP remote device '{}' discovered.", device.getDisplayString());
        }

        // The event triggers a method that will attempt to add this device to the capture devices.
        RegisterDevice.addRemoteDevice(registry, device);

        logger.exit();
    }

    public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
        logger.entry();
        logger.trace("UPnP remote device '{}' has been updated.", device.getDisplayString());
        logger.exit();
    }

    public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
        logger.entry();
        logger.debug("UPnP remote device '{}' has been removed.", device.getDisplayString());

        // We could have the tuner stop working partly, but not interfere with anything
        // currently happening by making it aware that it can't make any requests to UPnP
        // resources at this time, then update the registry and device once the device
        // becomes available again. For example, if it's currently recording, don't stop
        // recording since this could be temporary and would likely only cause a problem
        // if we needed to tune into another channel or query for other things like CCI.

        RegisterDevice.removeRemoteDevice(registry, device);

        logger.exit();
    }

    public void localDeviceAdded(Registry registry, LocalDevice device) {
        logger.entry();
        logger.trace("UPnP local device '{}' is now available.", device.getDisplayString());
        logger.exit();
    }

    public void localDeviceRemoved(Registry registry, LocalDevice device) {
        logger.entry();
        logger.trace("UPnP local device '{}' have been removed.", device.getDisplayString());
        logger.exit();
    }

    public void beforeShutdown(Registry registry) {
        logger.entry();
        if (registry.getDevices().size() > 0) {
            logger.debug("Before shutdown, the registry has {} device(s).", registry.getDevices().size());
        } else {
            logger.warn("Before UPnP shutdown, the registry did not contain any devices.", registry.getDevices().size());
        }
        logger.exit();
    }

    public void afterShutdown() {
        logger.entry();
        logger.debug("Shutdown of registry complete.");
        logger.exit();
    }
}
