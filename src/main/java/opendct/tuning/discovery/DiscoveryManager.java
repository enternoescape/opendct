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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DiscoveryManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(DiscoveryManager.class);
    public static final PowerEventListener POWER_EVENT_LISTENER = new DiscoveryManager();
    private final AtomicBoolean suspend = new AtomicBoolean(false);

    // If using both locks, always use discoverLock first.
    private final static ReentrantReadWriteLock discoverLock = new ReentrantReadWriteLock();
    private final static ReentrantReadWriteLock permitLock = new ReentrantReadWriteLock();

    public static final DeviceLoader DEVICE_LOADER = new DeviceLoaderImpl();
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final ArrayList<DeviceDiscoverer> deviceDiscoveries = new ArrayList<>();
    private static final IntOpenHashSet permittedDevices = new IntOpenHashSet();

    static {
        // Add all saved permitted devices.
        int devices[] = Config.getIntegerArray("discovery.devices_permitted");

        for (int device : devices) {
            permittedDevices.add(device);
        }
    }

    /**
     * Check if a particular device is permitted to load.
     * <p/>
     * This is not an indicator of if the device is actually loaded.
     *
     * @param deviceId This is the device ID to check.
     * @return <i>true</i> if the device is permitted to load.
     */
    public static boolean isDevicePermitted(int deviceId) {
        boolean returnValue = false;

        permitLock.readLock().lock();

        try {
            returnValue = permittedDevices.contains(deviceId);
        } catch (Exception e) {
            logger.error("isDevicePermitted created an unexpected exception while using" +
                    " permitLock => ", e);
        } finally {
            permitLock.readLock().unlock();
        }

        return returnValue;
    }

    /**
     * Add a device to the permitted devices list.
     * <p/>
     * Note that this does not load the device if it is not already loaded. These changes are
     * immediately saved.
     *
     * @param deviceId This is the device ID to add.
     */
    public static void permitDevice(int deviceId) {

        permitLock.writeLock().lock();

        try {
            permittedDevices.add(deviceId);
            Integer newList[] = permittedDevices.toArray(new Integer[permittedDevices.size()]);
            Config.setIntegerArray("discovery.devices.permitted", newList);
            Config.saveConfig();
        } catch (Exception e) {
            logger.error("permitDevice created an unexpected exception while using" +
                    " permitLock => ", e);
        } finally {
            permitLock.writeLock().unlock();
        }
    }

    /**
     * Remove a device from the permitted devices list.
     * <p/>
     * Note that this does not unload the device if it has already been loaded. These changes are
     * immediately saved.
     *
     * @param deviceId This is the device ID to remove.
     */
    public static void revokeDevice(int deviceId) {

        permitLock.writeLock().lock();

        try {
            permittedDevices.remove(deviceId);
            Integer newList[] = permittedDevices.toArray(new Integer[permittedDevices.size()]);
            Config.setIntegerArray("discovery.devices.permitted", newList);
            Config.saveConfig();
        } catch (Exception e) {
            logger.error("revokeDevice created an unexpected exception while using" +
                    " permitLock => ", e);
        } finally {
            permitLock.writeLock().unlock();
        }
    }

    /**
     * Add a new discoverer to the discovery manager.
     * <p/>
     * The discoverer will be automatically started if discovery manager is already running.
     * Otherwise it will not be started until discovery manager is requested to start.
     *
     * @param newDiscoverer This is the discoverer to be added.
     * @throws DiscoveryException This will be thrown if a discovery method with the exact same name
     *                            is already registered.
     */
    public static void addDiscoverer(DeviceDiscoverer newDiscoverer)
            throws DiscoveryException {

        if (newDiscoverer == null) {
            return;
        }

        boolean supportedOS = false;
        for (OSVersion osVersion : newDiscoverer.getSupportedOS()) {
            if (osVersion == Config.getOsVersion()) {
                supportedOS = true;
            }
        }

        if (!supportedOS) {
            logger.info("The discovery method {} does not support the OS {}.",
                    newDiscoverer.getName(), Config.getOsVersion());
            return;
        }

        discoverLock.writeLock().lock();

        try {
            for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
                if (newDiscoverer.getName().equals(deviceDiscoverer.getName())) {
                    throw new DiscoveryException("This discovery method or another discovery" +
                            " method with the same name is a duplicate and will not be loaded.");
                }
            }

            // The discovery method is always added if it's new and supported by the current OS even
            // if it won't load.
            deviceDiscoveries.add(newDiscoverer);
        } catch (Exception e) {
            logger.error("addDiscoverer created an unexpected exception while using first" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.writeLock().unlock();
        }

        if (!newDiscoverer.isEnabled()) {
            return;
        }

        discoverLock.writeLock().lock();

        try {
            if (running.get()) {
                // This will throw an exception if there is a problem starting.
                newDiscoverer.startDetection(DEVICE_LOADER);
                logger.info("{} discovery started.", newDiscoverer.getName());
            }
        } catch (Exception e) {
            logger.error("addDiscoverer created an unexpected exception while using second" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.writeLock().unlock();
        }
    }

    /**
     * Returns a device discoverer by name.
     *
     * @param name Unique name of discover.
     * @return The requested discover if it is available. If not, <i>null</i> will be returned.
     */
    public static DeviceDiscoverer getDiscoverer(String name) {
        discoverLock.readLock().lock();

        try {
            for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
                if (deviceDiscoverer.getName().equals(name)) {
                    return deviceDiscoverer;
                }
            }
        } catch (Exception e) {
            logger.error("getDiscoverer created an unexpected exception while using" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.readLock().unlock();
        }

        return null;
    }

    /**
     * Start device discovery for all currently available discovery methods.
     */
    public static void startDeviceDiscovery() {
        if (running.getAndSet(true)) {
            logger.debug("Device discovery is already running.");
            return;
        }

        discoverLock.writeLock().lock();

        try {
            for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
                try {
                    deviceDiscoverer.startDetection(DEVICE_LOADER);
                } catch (DiscoveryException e) {
                    logger.warn("Unable to start capture device discovery for {} => {}",
                            deviceDiscoverer.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("startDeviceDiscovery created an unexpected exception while using" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.writeLock().unlock();
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
    public static void stopDeviceDiscovery() throws InterruptedException {
        if (!running.getAndSet(false)) {
            logger.debug("Device discovery is already stopped.");
            return;
        }

        InterruptedException interruptedException = null;

        discoverLock.writeLock().lock();

        try {
            for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
                if (deviceDiscoverer.isRunning()) {
                    try {
                        deviceDiscoverer.stopDetection();
                        logger.info("Stopping discovery for {}.", deviceDiscoverer.getName());
                    } catch (DiscoveryException e) {
                        logger.warn("Unable to stop capture device discovery for {} => {}",
                                deviceDiscoverer.getName(), e.getMessage());
                    } catch (Exception e) {
                        logger.error("Unexpected error while stopping discovery for {} => ",
                                deviceDiscoverer.getName(), e);
                    }
                }
            }

            for (DeviceDiscoverer deviceDiscoverer : deviceDiscoveries) {
                if (deviceDiscoverer.getErrorMessage() == null) {
                    try {
                        deviceDiscoverer.waitForStopDetection();
                        logger.info("{} discovery stopped.", deviceDiscoverer.getName());
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        logger.error("Unexpected error while waiting for discovery to stop for" +
                                " {} => ", deviceDiscoverer.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                interruptedException = (InterruptedException)e;
            } else {
                logger.error("stopDeviceDiscovery created an unexpected exception while using" +
                        " discoverLock => ", e);
            }
        } finally {
            discoverLock.writeLock().unlock();
        }

        if (interruptedException != null) {
            throw interruptedException;
        }
    }

    /**
     * This returns all discovered devices.
     * <p/>
     * This will include devices that are currently enabled and disabled for use by SageTV.
     *
     * @return An array of all discovered devices.
     */
    public static DiscoveredDevice[] getDiscoveredDevices() {
        ArrayList<DiscoveredDevice> returnValues = new ArrayList<>();

        discoverLock.readLock().lock();

        try {
            for (DeviceDiscoverer discovery : deviceDiscoveries) {
                Collections.addAll(returnValues, discovery.getAllDeviceDetails());
            }
        } catch (Exception e) {
            logger.error("getDiscoveredDevices created an unexpected exception while using" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.readLock().unlock();
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
    public static DiscoveredDevice[] getDisabledDiscoveredDevices() {
        ArrayList<DiscoveredDevice> returnValues = new ArrayList<>();

        discoverLock.readLock().lock();

        try {
            for (DeviceDiscoverer discovery : deviceDiscoveries) {
                for (DiscoveredDevice deviceDetail : discovery.getAllDeviceDetails()) {
                    if (!isDevicePermitted(deviceDetail.getId())) {
                        returnValues.add(deviceDetail);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("getDisabledDiscoveredDevices created an unexpected exception while" +
                    " using discoverLock => ", e);
        } finally {
            discoverLock.readLock().unlock();
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
    public static DiscoveredDevice[] getEnabledDiscoveredDevices() {
        ArrayList<DiscoveredDevice> returnValues = new ArrayList<>();

        discoverLock.readLock().lock();

        try {
            for (DeviceDiscoverer discovery : deviceDiscoveries) {
                for (DiscoveredDevice deviceDetail : discovery.getAllDeviceDetails()) {
                    if (isDevicePermitted(deviceDetail.getId())) {
                        returnValues.add(deviceDetail);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("getEnabledDiscoveredDevices created an unexpected exception while using" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.readLock().unlock();
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
    public static CaptureDevice enableCaptureDevice(int deviceId)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException, SocketException {

        DiscoveredDevice deviceDetails;
        CaptureDevice captureDevice = null;

        discoverLock.writeLock().lock();

        try {
            for (DeviceDiscoverer discovery : deviceDiscoveries) {
                deviceDetails = discovery.getDeviceDetails(deviceId);

                if (deviceDetails != null) {
                    captureDevice = discovery.loadCaptureDevice(deviceId);
                    SageTVManager.addCaptureDevice(captureDevice);
                    permitDevice(deviceId);
                    break;
                }
            }

        } catch (Exception e) {
            if (e instanceof CaptureDeviceIgnoredException) {
                throw (CaptureDeviceIgnoredException)e;
            } else if (e instanceof CaptureDeviceLoadException) {
                throw (CaptureDeviceLoadException)e;
            } else if (e instanceof SocketException) {
                throw (SocketException)e;
            } else {
                logger.error("enableCaptureDevice created an unexpected exception while using" +
                        " discoverLock => ", e);
            }
        } finally {
            discoverLock.writeLock().unlock();
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
    public static void disableCaptureDevice(int deviceId) {
        DiscoveredDevice deviceDetails;

        discoverLock.writeLock().lock();

        try {
            revokeDevice(deviceId);

            for (DeviceDiscoverer discovery : deviceDiscoveries) {
                deviceDetails = discovery.getDeviceDetails(deviceId);

                if (deviceDetails != null) {
                    SageTVManager.removeCaptureDevice(deviceId);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("disableCaptureDevice created an unexpected exception while using" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.writeLock().unlock();
        }
    }

    /**
     * Get details for all capture device parents.
     *
     * @return Details for all currently discovered capture device parents.
     */
    public static DiscoveredDeviceParent[] getAllDiscoveredParents() {
        ArrayList<DiscoveredDeviceParent> deviceParents = new ArrayList<>();

        discoverLock.readLock().lock();

        try {
            for (DeviceDiscoverer discovery : deviceDiscoveries) {
                DiscoveredDeviceParent parentDetails[] = discovery.getAllDeviceParentDetails();

                if (parentDetails != null && parentDetails.length > 0) {
                    Collections.addAll(deviceParents, parentDetails);
                }
            }
        } catch (Exception e) {
            logger.error("getAllDiscoveredParents created an unexpected exception while using" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.readLock().unlock();
        }

        return deviceParents.toArray(new DiscoveredDeviceParent[deviceParents.size()]);
    }

    /**
     * Get details for a specific capture device parent.
     *
     * @param parentId This is the unique ID associated with the capture device parent.
     * @return Details about capture device parent.
     */
    public static DiscoveredDeviceParent getDiscoveredDeviceParent(int parentId) {
        DiscoveredDeviceParent deviceDetails = null;

        discoverLock.readLock().lock();

        try {
            for (DeviceDiscoverer discovery : deviceDiscoveries) {
                deviceDetails = discovery.getDeviceParentDetails(parentId);

                if (deviceDetails != null) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("getDiscoveredDeviceParent created an unexpected exception while using" +
                    " discoverLock => ", e);
        } finally {
            discoverLock.readLock().unlock();
        }

        return  deviceDetails;
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
