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
import opendct.config.Config;
import opendct.config.OSVersion;
import opendct.power.PowerEventListener;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscoveryManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(DiscoveryManager.class);
    public static final PowerEventListener POWER_EVENT_LISTENER = new DiscoveryManager();
    private final AtomicBoolean suspend = new AtomicBoolean(false);

    public static final DeviceLoader DEVICE_LOADER = new DeviceLoaderImpl();
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final ArrayList<DeviceDiscoverer> deviceDiscoveries = new ArrayList<>();
    private static final HashSet<Integer> permittedDevices = new HashSet<>();

    static {
        int devices[] = Config.getIntegerArray("discovery.devices_permitted");

        for (int device : devices) {
            permittedDevices.add(device);
        }
    }

    public static synchronized boolean isDevicePermitted(int deviceId) {
        return permittedDevices.contains(deviceId);
    }

    public static synchronized void permitDevice(int deviceId) {
        permittedDevices.add(deviceId);
        Integer newList[] = permittedDevices.toArray(new Integer[permittedDevices.size()]);
        Config.setIntegerArray("discovery.devices.permitted", newList);
        Config.saveConfig();
    }

    public static synchronized void revokeDevice(int deviceId) {
        permittedDevices.remove(deviceId);
        Integer newList[] = permittedDevices.toArray(new Integer[permittedDevices.size()]);
        Config.setIntegerArray("discovery.devices.permitted", newList);
        Config.saveConfig();
    }

    public static synchronized void addDiscovery(DeviceDiscoverer newDiscovery)
            throws DiscoveryException {

        if (newDiscovery == null) {
            return;
        }

        boolean supportedOS = false;
        for (OSVersion osVersion : newDiscovery.getSupportedOS()) {
            if (osVersion == Config.getOsVersion()) {
                supportedOS = true;
            }
        }

        if (!supportedOS) {
            logger.info("The discovery method {} does not support the OS {}.", newDiscovery.name(), Config.getOsVersion());
            return;
        }

        for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
            if (newDiscovery.name().equals(deviceDiscoverer.name())) {
                throw new DiscoveryException("This discovery method or another discovery method" +
                        " with the same name is a duplicate and will not be loaded.");
            }
        }

        // The discovery method is always added if it's new even if it won't load.
        deviceDiscoveries.add(newDiscovery);

        if (!newDiscovery.isEnabled()) {
            return;
        }

        if (running.get()) {
            // This will throw an exception if there is a problem starting.
            newDiscovery.startDetection(DEVICE_LOADER);
            logger.info("{} discovery started.", newDiscovery.name());
        }
    }

    public static synchronized void startDeviceDiscovery() {
        if (running.getAndSet(true)) {
            logger.debug("Device discovery is already running.");
            return;
        }

        for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
            try {
                deviceDiscoverer.startDetection(DEVICE_LOADER);
            } catch (DiscoveryException e) {
                logger.warn("Unable to start capture device discovery for {} => {}", deviceDiscoverer.name(), e.getMessage());
            }
        }
    }

    /**
     * Stop all currently running device discovery methods.
     * <p/>
     * This will also wait for them to report that they are no longer running before returning.
     *
     * @throws InterruptedException Thrown if this thread is interrupted while waiting for device
     *                              discovery methods to stop running.
     */
    public static synchronized void stopDeviceDiscovery() throws InterruptedException {
        if (!running.getAndSet(false)) {
            logger.debug("Device discovery is already stopped.");
            return;
        }

        for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
            if (deviceDiscoverer.isRunning()) {
                try {
                    deviceDiscoverer.stopDetection();
                    logger.info("Stopping discovery for {}.", deviceDiscoverer.name());
                } catch (DiscoveryException e) {
                    logger.warn("Unable to stop capture device discovery for {} => {}", deviceDiscoverer.name(), e.getMessage());
                } catch (Exception e) {
                    logger.error("Unexpected error while stopping discovery for {} => ", deviceDiscoverer.name(), e);
                }
            }
        }

        for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
            if (deviceDiscoverer.errorMessage() == null) {
                try {
                    deviceDiscoverer.waitForStopDetection();
                    logger.info("{} discovery stopped.", deviceDiscoverer.name());
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    logger.error("Unexpected error while waiting for discovery to stop for {} => ", deviceDiscoverer.name(), e);
                }
            }
        }
    }

    /**
     * This returns all discovered devices.
     * <p/>
     * This will include devices that are currently enabled and disabled for use by SageTV.
     *
     * @return An array of all discovered devices.
     */
    public static synchronized DiscoveredDevice[] getDiscoveredDevices() {
        ArrayList<DiscoveredDevice> returnValues = new ArrayList<>();

        for (DeviceDiscoverer discovery : deviceDiscoveries) {
            returnValues.addAll(Arrays.asList(discovery.getAllDeviceDetails()));
        }

        return returnValues.toArray(new DiscoveredDevice[returnValues.size()]);
    }

    /**
     * This returns all discovered devices that are not permitted to load.
     * <p/>
     * This will exclude devices that can/will be loaded for use by SageTV.
     *
     * @return An array of all discovered devices that are not permitted to load.
     */
    public static synchronized DiscoveredDevice[] getDisabledDiscoveredDevices() {
        ArrayList<DiscoveredDevice> returnValues = new ArrayList<>();

        for (DeviceDiscoverer discovery : deviceDiscoveries) {
            for (DiscoveredDevice deviceDetail : discovery.getAllDeviceDetails()) {
                if (!isDevicePermitted(deviceDetail.getId())) {
                    returnValues.add(deviceDetail);
                }
            }
        }

        return returnValues.toArray(new DiscoveredDevice[returnValues.size()]);
    }

    /**
     * This returns all discovered devices that are permitted to load.
     * <p/>
     * This will include all devices that are enabled to be loaded for use by SageTV. This list does
     * not guarantee that they are actually loaded. That information is held my SageTVManager.
     *
     * @return An array of all discovered devices that are permitted to load.
     */
    public static synchronized DiscoveredDevice[] getEnabledDiscoveredDevices() {
        ArrayList<DiscoveredDevice> returnValues = new ArrayList<>();

        for (DeviceDiscoverer discovery : deviceDiscoveries) {
            for (DiscoveredDevice deviceDetail : discovery.getAllDeviceDetails()) {
                if (isDevicePermitted(deviceDetail.getId())) {
                    returnValues.add(deviceDetail);
                }
            }
        }

        return returnValues.toArray(new DiscoveredDevice[returnValues.size()]);
    }

    /**
     * Enable a discovered capture device.
     * <p/>
     * This will create the new capture capture device, then make it available to SageTV, then
     * enable the device to be loaded automatically on restart.
     *
     * @param deviceId The capture device id.
     * @return The new capture device object.
     * @throws CaptureDeviceIgnoredException This is a deprecated exception that should not be
     *                                       happening.
     * @throws CaptureDeviceLoadException Thrown if there is a problem loading the device.
     * @throws SocketException Thrown if a socket can't be opened to listen to SageTV.
     */
    public static synchronized CaptureDevice enableCaptureDevice(int deviceId)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException, SocketException {

        DiscoveredDevice deviceDetails;
        CaptureDevice captureDevice = null;

        for (DeviceDiscoverer discovery : deviceDiscoveries) {
            deviceDetails = discovery.getDeviceDetails(deviceId);

            if (deviceDetails != null) {
                captureDevice = discovery.loadCaptureDevice(deviceId);
                SageTVManager.addCaptureDevice(captureDevice);
                permitDevice(deviceId);
                break;
            }
        }

        return  captureDevice;
    }

    /**
     * Disables a capture device.
     * <p/>
     * This will revoke the capture device, then unload it and make it unavailable to SageTV.
     *
     * @param deviceId This is the unique ID associated with this device.
     */
    public static synchronized void disableCaptureDevice(int deviceId) {
        revokeDevice(deviceId);

        DiscoveredDevice deviceDetails;

        for (DeviceDiscoverer discovery : deviceDiscoveries) {
            deviceDetails = discovery.getDeviceDetails(deviceId);

            if (deviceDetails != null) {
                SageTVManager.removeCaptureDevice(deviceId);
                revokeDevice(deviceId);
                break;
            }
        }
    }

    /**
     * Implements a callback for Suspend Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onSuspendEvent() {
        if (suspend.getAndSet(true)) {
            logger.error("onSuspendEvent: The computer is going into suspend mode and DiscoveryManager has possibly not recovered from the last suspend event.");
        } else {
            logger.debug("onSuspendEvent: Stopping services due to a suspend event.");

            try {
                stopDeviceDiscovery();
            } catch (InterruptedException e) {
                logger.warn("onSuspendEvent: Interrupted while waiting for DiscoveryManager to stop => ", e);
            }
        }
    }

    /**
     * Implements a callback for Resume Suspend Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onResumeSuspendEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeSuspendEvent: The computer returned from suspend mode and DiscoveryManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeSuspendEvent: Starting services due to a resume event.");

            startDeviceDiscovery();
        }
    }

    /**
     * Implements a callback for Resume Critical Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onResumeCriticalEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeCriticalEvent: The computer returned from suspend mode and DiscoveryManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeCriticalEvent: Starting services due to a resume event.");

            startDeviceDiscovery();
        }
    }

    /**
     * Implements a callback for Resume Automatic Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onResumeAutomaticEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeAutomaticEvent: The computer returned from suspend mode and DiscoveryManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeAutomaticEvent: Starting services due to a resume event.");

            startDeviceDiscovery();
        }
    }
}
