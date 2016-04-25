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

package opendct.tuning.discovery.discoverers;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.config.Config;
import opendct.config.OSVersion;
import opendct.config.options.*;
import opendct.tuning.discovery.*;
import opendct.tuning.http.GenericHttpDiscoveredDevice;
import opendct.tuning.http.GenericHttpDiscoveredDeviceParent;
import opendct.tuning.http.GenericHttpLoader;
import opendct.tuning.upnp.UpnpDiscoveredDeviceParent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GenericHttpDiscoverer implements DeviceDiscoverer {
    private static final Logger logger = LogManager.getLogger(HDHomeRunDiscoverer.class);

    // Static information about this discovery method.
    private static String name = "Generic HTTP";
    private static String description = "Discovers predefined capture devices available via the" +
            " combination of a static HTTP URL and tuning method.";

    private static OSVersion[] supportedOS = new OSVersion[] {
            OSVersion.WINDOWS,
            OSVersion.LINUX
    };

    // Global generic HTTP device settings.
    private final static Map<String, DeviceOption> deviceOptions;
    private static IntegerDeviceOption offlineDetectionSeconds;
    private static IntegerDeviceOption offlineDetectionMinBytes;
    private static StringDeviceOption deviceNames;
    private static LongDeviceOption streamingWait;

    // Detection configuration and state
    private static boolean enabled;
    private static boolean running;
    private static String errorMessage;
    DeviceLoader deviceLoader;

    private final ReentrantReadWriteLock discoveredDevicesLock = new ReentrantReadWriteLock();
    private final Map<Integer, GenericHttpDiscoveredDevice> discoveredDevices = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, GenericHttpDiscoveredDeviceParent> discoveredParents = new Int2ObjectOpenHashMap<>();

    static {
        enabled = Config.getBoolean("generic.http.discoverer_enabled", true);
        running = false;

        errorMessage = null;
        deviceOptions = new ConcurrentHashMap<>();

        while (true) {
            try {
                deviceNames = new StringDeviceOption(
                        Config.getStringArray("generic.http.device_names_csv"),
                        true,
                        false,
                        "Device Names",
                        "generic.http.device_names_csv",
                        "This is a comma separated list of the names to be used for each device." +
                                " Each name in this list will create all of the required" +
                                " properties to create a functional capture device."
                );

                streamingWait = new LongDeviceOption(
                        Config.getInteger("generic.http.wait_for_streaming", 15000),
                        false,
                        "Return to SageTV",
                        "generic.http.wait_for_streaming",
                        "This is the maximum number of milliseconds to wait before returning to" +
                                " SageTV regardless of if the requested channel is actually streaming."
                );

                offlineDetectionSeconds = new IntegerDeviceOption(
                        Config.getInteger("generic.http.wait_for_offline_detection_s", 8),
                        false,
                        "Offline Channel Detection Seconds",
                        "generic.http.wait_for_offline_detection_s",
                        "This is the value in seconds to wait after tuning a channel before" +
                                " making a final determination on if it is tunable or not." +
                                " This applies only to offline scanning."
                );

                offlineDetectionMinBytes = new IntegerDeviceOption(
                        Config.getInteger("generic.http.offline_detection_min_bytes", 10528),
                        false,
                        "Offline Channel Detection Bytes",
                        "generic.http.offline_detection_min_bytes",
                        "This is the value in bytes that must be consumed before a channel is" +
                                " considered tunable."
                );

                Config.mapDeviceOptions(
                        deviceOptions,
                        deviceNames,
                        streamingWait,
                        offlineDetectionSeconds,
                        offlineDetectionMinBytes
                );
            } catch (DeviceOptionException e) {
                logger.error("Unable to configure device options for GenericHttpDiscoverer." +
                        " Reverting to defaults. => ", e);

                Config.setInteger("generic.http.wait_for_offline_detection_s", 8);
                Config.setInteger("generic.http.offline_detection_min_bytes", 10528);

                continue;
            }

            break;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        GenericHttpDiscoverer.enabled = enabled;
        Config.setBoolean("generic.http.discoverer_enabled", enabled);
    }

    @Override
    public OSVersion[] getSupportedOS() {
        return supportedOS;
    }

    @Override
    public synchronized void startDetection(DeviceLoader deviceLoader) throws DiscoveryException {
        // Detection is instant and doesn't need to run on another thread.
        GenericHttpDiscoverer.running = true;

        this.deviceLoader = deviceLoader;

        Thread loadDevices = new Thread(new GenericHttpLoader(deviceNames.getArrayValue(), this));
        loadDevices.setName("GenericHttpLoader-" + loadDevices.getId());
        loadDevices.setDaemon(true);
        loadDevices.start();
    }

    @Override
    public synchronized void stopDetection() throws DiscoveryException {
        // There is nothing to stop.

        discoveredDevicesLock.writeLock().lock();

        try {
            discoveredDevices.clear();
            discoveredParents.clear();
        } finally {
          discoveredDevicesLock.writeLock().unlock();
        }

        GenericHttpDiscoverer.running = false;
    }

    @Override
    public void waitForStopDetection() throws InterruptedException {
        // There is nothing to stop.
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    public void addCaptureDevice(GenericHttpDiscoveredDevice device, GenericHttpDiscoveredDeviceParent parent) {

        discoveredDevicesLock.writeLock().lock();

        try {
            GenericHttpDiscoveredDeviceParent lastParentDevice =
                    discoveredParents.get(device.getParentId());

            if (lastParentDevice != null) {
                if (!lastParentDevice.getRemoteAddress().equals(parent.getRemoteAddress())) {

                }

                return;
            }

        } finally {
            discoveredDevicesLock.writeLock().unlock();
        }
    }

    @Override
    public int discoveredDevices() {
        int returnValue;

        discoveredDevicesLock.readLock().lock();

        try {
            returnValue = discoveredDevices.size();
        } finally {
            discoveredDevicesLock.readLock().unlock();
        }

        return returnValue;
    }

    @Override
    public DiscoveredDevice[] getAllDeviceDetails() {
        DiscoveredDevice[] returnValues = null;

        discoveredDevicesLock.readLock().lock();

        try {
            returnValues = new DiscoveredDevice[discoveredDevices.size()];

            int i = 0;
            for (Map.Entry<Integer, GenericHttpDiscoveredDevice> discoveredDevice : discoveredDevices.entrySet()) {
                returnValues[i++] = discoveredDevice.getValue();
            }

        } catch (Exception e) {
            logger.error("getAllDeviceDetails created an unexpected exception while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.readLock().unlock();
        }

        if (returnValues == null) {
            returnValues = new DiscoveredDevice[0];
        }

        return returnValues;
    }

    @Override
    public DiscoveredDevice getDeviceDetails(int deviceId) {
        DiscoveredDevice returnValue = null;

        discoveredDevicesLock.readLock().lock();

        try {
            returnValue = discoveredDevices.get(deviceId);
        } catch (Exception e) {
            logger.error("getDeviceDetails created an unexpected exception while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.readLock().unlock();
        }

        return returnValue;
    }

    @Override
    public DiscoveredDeviceParent[] getAllDeviceParentDetails() {
        DiscoveredDeviceParent[] returnValues = null;

        discoveredDevicesLock.readLock().lock();

        try {
            returnValues = new UpnpDiscoveredDeviceParent[discoveredParents.size()];

            int i = 0;
            for (Map.Entry<Integer, GenericHttpDiscoveredDeviceParent> discoveredParent : discoveredParents.entrySet()) {
                returnValues[i++] = discoveredParent.getValue();
            }

        } catch (Exception e) {
            logger.error("getAllDeviceParentDetails created an unexpected exception while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.readLock().unlock();
        }

        if (returnValues == null) {
            returnValues = new UpnpDiscoveredDeviceParent[0];
        }

        return returnValues;
    }

    @Override
    public DiscoveredDeviceParent getDeviceParentDetails(int parentId) {
        DiscoveredDeviceParent deviceParent = null;

        discoveredDevicesLock.readLock().lock();

        try {
            deviceParent = discoveredParents.get(parentId);
        } catch (Exception e) {
            logger.error("getDeviceParentDetails created an unexpected exception while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.readLock().unlock();
        }

        return deviceParent;
    }

    @Override
    public CaptureDevice loadCaptureDevice(int deviceId)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {

        CaptureDevice returnValue = null;
        GenericHttpDiscoveredDevice discoveredDevice;
        CaptureDeviceLoadException loadException = null;

        discoveredDevicesLock.readLock().lock();

        try {
            discoveredDevice = discoveredDevices.get(deviceId);

            if (discoveredDevice != null) {
                returnValue = discoveredDevice.loadCaptureDevice();
            } else {
                loadException = new CaptureDeviceLoadException("Unable to create capture device" +
                        " because it was never detected.");
            }

        } catch (CaptureDeviceLoadException e) {
            loadException = e;
        } catch (CaptureDeviceIgnoredException e) {
            logger.warn("Capture device will not be loaded => {}", e.getMessage());
        } catch (Exception e) {
            logger.error("An unhandled exception happened in loadCaptureDevice while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.readLock().unlock();
        }

        if (loadException != null) {
            throw loadException;
        }

        return returnValue;
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                deviceNames,
                streamingWait,
                offlineDetectionSeconds,
                offlineDetectionMinBytes
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference = GenericHttpDiscoverer.deviceOptions.get(option.getProperty());

            if (optionReference == null) {
                continue;
            }

            if (optionReference.isArray()) {
                optionReference.setValue(option.getArrayValue());
            } else {
                optionReference.setValue(option.getValue());
            }

            Config.setDeviceOption(optionReference);
        }

        Config.saveConfig();
    }

    public static long getStreamingWait() {
        return streamingWait.getLong();
    }

    public static int getOfflineDetectionSeconds() {
        return offlineDetectionSeconds.getInteger();
    }

    public static int getOfflineDetectionMinBytes() {
        return offlineDetectionMinBytes.getInteger();
    }

    public static String[] getDeviceNames() {
        return deviceNames.getArrayValue();
    }
}
