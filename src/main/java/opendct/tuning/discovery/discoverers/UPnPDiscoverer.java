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

package opendct.tuning.discovery.discoverers;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.config.Config;
import opendct.config.OSVersion;
import opendct.config.options.*;
import opendct.tuning.discovery.*;
import opendct.tuning.upnp.UpnpDiscoveredDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UpnpDiscoverer implements DeviceDiscoverer {
    private static final Logger logger = LogManager.getLogger(UpnpDiscoverer.class);

    // Static information about this discovery method.
    private static String name = "UPnP";
    private static String description = "Discovers capture devices available via UPnP.";
    private static OSVersion[] supportedOS = new OSVersion[] {
            OSVersion.WINDOWS,
            OSVersion.LINUX
    };

    // Global UPnP device settings.
    private static IntegerDeviceOption offlineDetectionSeconds;
    private static IntegerDeviceOption offlineDetectionMinBytes;
    private static IntegerDeviceOption retunePolling;
    private static LongDeviceOption streamingWait;
    private static BooleanDeviceOption httpTuning;
    private static BooleanDeviceOption hdhrTuning;
    private static BooleanDeviceOption autoMapReference;
    private static BooleanDeviceOption autoMapTuning;
    private static BooleanDeviceOption fastTune;
    private static BooleanDeviceOption hdhrLock;

    // Detection configuration and state
    private static boolean enabled;
    private boolean running;

    private final ReentrantReadWriteLock discoveredDevicesLock = new ReentrantReadWriteLock();
    private final HashMap<Integer, UpnpDiscoveredDevice> discoveredDevices = new HashMap<>();

    static {
        enabled = Config.getBoolean("upnp.discoverer_enabled", true);

        try {
            streamingWait = new LongDeviceOption(
                    Config.getInteger("upnp.dct.wait_for_streaming", 5000),
                    false,
                    "Return to SageTV",
                    "upnp.dct.wait_for_streaming",
                    "This is the maximum number of milliseconds to wait before returning to" +
                            " SageTV regardless of if the program is actually streaming."
            );

            offlineDetectionSeconds = new IntegerDeviceOption(
                    Config.getInteger("upnp.dct.wait_for_offline_detection_s", 8),
                    false,
                    "Offline Channel Detection Seconds",
                    "upnp.dct.wait_for_offline_detection_s",
                    "This is the value in seconds to wait after tuning a channel before" +
                            " making a final determination on if it is tunable or not." +
                            " This applies only to offline scanning."
            );

            offlineDetectionMinBytes = new IntegerDeviceOption(
                    Config.getInteger("upnp.dct.offline_detection_min_bytes", 18800),
                    false,
                    "Offline Channel Detection Bytes",
                    "upnp.dct.offline_detection_min_bytes",
                    "This is the value in bytes that must be consumed before a channel is" +
                            " considered tunable."
            );

            retunePolling = new IntegerDeviceOption(
                    Config.getInteger("upnp.retune_poll_s", 1),
                    false,
                    "Re-tune Polling Seconds",
                    "upnp.retune_poll_s",
                    "This is the frequency in seconds to poll the producer to check if it" +
                            " is stalled."
            );

            httpTuning = new BooleanDeviceOption(
                    Config.getBoolean("upnp.dct.http_tuning", true),
                    false,
                    "HTTP Tuning",
                    "upnp.dct.http_tuning",
                    "This enables HTTP tuning for InfiniTV devices. This is a tuning" +
                            " method that is faster than UPnP and is available on all" +
                            " InfiniTV devices except InfiniTV 4 devices with old" +
                            " firmware. This tuning mode is not optional and will" +
                            " automatically enable itself for ClearQAM tuning on InfiniTV" +
                            " devices. The affected capture devices need to be re-loaded for this" +
                            " setting to take effect."
            );

            hdhrTuning = new BooleanDeviceOption(
                    Config.getBoolean("upnp.dct.hdhr_tuning", true),
                    false,
                    "HDHomeRun Native Tuning",
                    "upnp.dct.http_tuning",
                    "This enables HDHomeRun native tuning for HDHomeRun Prime devices." +
                            " This is a tuning method that is faster than UPnP and is" +
                            " available on all HDHomeRun Prime devices. This tuning mode" +
                            " is not optional and will automatically enable itself for" +
                            " ClearQAM tuning on HDHomeRun Prime devices. The affected capture" +
                            " devices need to be re-loaded for this setting to take effect."
            );

            autoMapReference = new BooleanDeviceOption(
                    Config.getBoolean("upnp.qam.automap_reference_lookup", true),
                    false,
                    "ClearQAM Auto-Mapping by Reference",
                    "upnp.qam.automap_reference_lookup",
                    "This enables ClearQAM devices to look up their channels based on the" +
                            " frequencies and programs available on a capture device with" +
                            " a CableCARD installed. This works well if you have an" +
                            " InfiniTV device with a CableCARD installed available."
            );

            autoMapTuning = new BooleanDeviceOption(
                    Config.getBoolean("upnp.qam.automap_tuning_lookup", false),
                    false,
                    "ClearQAM Auto-Mapping by Tuning",
                    "upnp.qam.automap_reference_lookup",
                    "This enables ClearQAM devices to look up their channels by tuning" +
                            " into the channel on a capture device with a CableCARD" +
                            " installed and then getting the current frequency and" +
                            " program being used. This may be your only option if you are" +
                            " using only HDHomeRun Prime capture devices. The program" +
                            " always tries to get a channel by reference before resorting" +
                            " to this lookup method. It will also retain the results of" +
                            " the lookup so this doesn't need to happen the next time. If" +
                            " all tuners with a CableCARD installed are currently in use," +
                            " this method cannot be used and will fail to tune the channel."
            );

            fastTune = new BooleanDeviceOption(
                    Config.getBoolean("upnp.dct.fast_tuning", false),
                    false,
                    "UPnP Fast Tuning",
                    "upnp.dct.fast_tuning",
                    "This enables 'fast' tuning for devices being tuned using strictly" +
                            " UPnP. The result of enabling this is the device is left in" +
                            " an always ready state that dramatically reduces the number" +
                            " of steps involved to actually tune a channel. The state is" +
                            " checked for before tuning and if the capture device is not" +
                            " in the expected state, it will perform a normal tuning via" +
                            " UPnP. The affected capture devices need to be re-loaded for" +
                            " this setting to take effect."
            );

            hdhrLock = new BooleanDeviceOption(
                    Config.getBoolean("hdhr.locking", true),
                    false,
                    "HDHomeRun Locking",
                    "hdhr.locking",
                    "This enables when using HDHomeRun Native Tuning, the program to put" +
                            " the tuner in a locked state when it is in use. This should" +
                            " generally not be disabled. The affected capture devices need" +
                            " to be re-loaded for this setting to take effect."
            );
        } catch (DeviceOptionException e) {
            logger.error("Unable to configure device options for UpnpDiscoverer => ", e);
        }
    }

    public UpnpDiscoverer() {
        running = false;
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
    public synchronized boolean isEnabled() {
        return enabled;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        Config.setBoolean("upnp.discoverer_enabled", enabled);
    }

    @Override
    public OSVersion[] getSupportedOS() {
        return UpnpDiscoverer.supportedOS;
    }

    @Override
    public synchronized void startDetection(DeviceLoader deviceLoader) throws DiscoveryException {
        if (!enabled || running) {
            return;
        }


    }

    @Override
    public synchronized void stopDetection() throws DiscoveryException {
        if (!running) {
            return;
        }


    }

    @Override
    public synchronized void waitForStopDetection() throws InterruptedException {
        if (!running) {
            return;
        }


    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public String errorMessage() {
        return null;
    }

    @Override
    public int discoveredDevices() {
        int returnValue = 0;

        discoveredDevicesLock.readLock().lock();

        try {
            returnValue = discoveredDevices.size();
        } catch (Exception e) {
            logger.error("");
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
            for (Map.Entry<Integer, UpnpDiscoveredDevice> discoveredDevice : discoveredDevices.entrySet()) {
                returnValues[i++] = discoveredDevice.getValue();
            }

        } catch (Exception e) {
            logger.error("An unhandled exception happened in getAllDeviceDetails while using" +
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
            logger.error("An unhandled exception happened in getDeviceDetails while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.readLock().unlock();
        }

        return returnValue;
    }

    @Override
    public CaptureDevice loadCaptureDevice(int deviceId)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {

        CaptureDevice returnValue = null;
        UpnpDiscoveredDevice discoveredDevice = null;
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
                offlineDetectionSeconds,
                offlineDetectionMinBytes,
                retunePolling,
                httpTuning,
                hdhrTuning,
                autoMapReference,
                autoMapTuning,
                fastTune,
                hdhrLock
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {

    }

    public static int getOfflineDetectionSeconds() {
        return offlineDetectionSeconds.getInteger();
    }

    public static int getOfflineDetectionMinBytes() {
        return offlineDetectionMinBytes.getInteger();
    }

    public static int getRetunePolling() {
        return retunePolling.getInteger();
    }

    public static boolean getHttpTuning() {
        return httpTuning.getBoolean();
    }

    public static boolean getHdhrTuning() {
        return hdhrTuning.getBoolean();
    }

    public static boolean getAutoMapReference() {
        return autoMapReference.getBoolean();
    }

    public static boolean getAutoMapTuning() {
        return autoMapTuning.getBoolean();
    }

    public static boolean getFastTune() {
        return fastTune.getBoolean();
    }

    public static boolean getHdhrLock() {
        return hdhrLock.getBoolean();
    }

    public static long getStreamingWait() {
        return streamingWait.getLong();
    }
}