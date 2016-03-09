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
import opendct.tuning.hdhomerun.*;
import opendct.tuning.upnp.UpnpDiscoveredDeviceParent;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class HDHomeRunDiscoverer implements DeviceDiscoverer {
    private static final Logger logger = LogManager.getLogger(HDHomeRunDiscoverer.class);

    // Static information about this discovery method.
    private static String name = "HDHomeRun";
    private static String description = "Discovers capture devices available via HDHomeRun native protocol.";
    private static OSVersion[] supportedOS = new OSVersion[] {
            OSVersion.WINDOWS,
            OSVersion.LINUX
    };

    // Global UPnP device settings.
    private final static ConcurrentHashMap<String, DeviceOption> deviceOptions;
    private static LongDeviceOption streamingWait;
    private static IntegerDeviceOption retunePolling;
    private static BooleanDeviceOption autoMapReference;
    private static BooleanDeviceOption autoMapTuning;
    private static BooleanDeviceOption hdhrLock;
    private static IntegerDeviceOption controlRetryCount;
    private static IntegerDeviceOption broadcastInterval;
    private static IntegerDeviceOption broadcastPort;
    private static StringDeviceOption ignoreModels;
    private static StringDeviceOption ignoreDeviceIds;
    private static BooleanDeviceOption alwaysTuneLegacy;
    private static BooleanDeviceOption allowHttpTuning;
    private static StringDeviceOption transcodeProfile;
    private static BooleanDeviceOption qamHttpTuningHack;
    private static IntegerDeviceOption offlineDetectionSeconds;
    private static IntegerDeviceOption offlineDetectionMinBytes;

    // Detection configuration and state
    private static boolean enabled;
    private static String errorMessage;
    private DeviceLoader deviceLoader;

    private final HDHomeRunDiscovery discovery = new HDHomeRunDiscovery(HDHomeRunDiscovery.getBroadcast());

    private final ReentrantReadWriteLock discoveredDevicesLock = new ReentrantReadWriteLock();
    private final HashMap<Integer, HDHomeRunDiscoveredDevice> discoveredDevices = new HashMap<>();
    private final HashMap<Integer, HDHomeRunDiscoveredDeviceParent> discoveredParents = new HashMap<>();
    private final HashMap<Integer, HDHomeRunDevice> hdHomeRunDevices = new HashMap<>();

    static {
        enabled = Config.getBoolean("hdhr.discoverer_enabled", true);

        // If the HDHomeRun Prime is allowed through the UPnP filter, we can't allow it here too.

        if (Config.getString("upnp.new.device.schema_filter_strings_csv", "schemas-cetoncorp-com")
                .contains("schemas-dkeystone-com")) {

            Config.setStringArray("hdhr.exp_ignore_models", "HDHR3-CC");
        }

        errorMessage = null;
        deviceOptions = new ConcurrentHashMap<>();

        while (true) {
            try {
                streamingWait = new LongDeviceOption(
                        Config.getInteger("hdhr.wait_for_streaming", 5000),
                        false,
                        "Return to SageTV",
                        "hdhr.wait_for_streaming",
                        "This is the maximum number of milliseconds to wait before returning to" +
                                " SageTV regardless of if the requested channel is actually streaming."
                );

                retunePolling = new IntegerDeviceOption(
                        Config.getInteger("hdhr.retune_poll_s", 1),
                        false,
                        "Re-tune Polling Seconds",
                        "hdhr.retune_poll_s",
                        "This is the frequency in seconds to poll the producer to check if it" +
                                " is stalled.",
                        0,
                        Integer.MAX_VALUE
                );

                autoMapReference = new BooleanDeviceOption(
                        Config.getBoolean("hdhr.qam.automap_reference_lookup", true),
                        false,
                        "ClearQAM Auto-Map by Reference",
                        "hdhr.qam.automap_reference_lookup",
                        "This enables ClearQAM devices to look up their channels based on the" +
                                " frequencies and programs available on a capture device with" +
                                " a CableCARD installed. This works well if you have an" +
                                " InfiniTV device with a CableCARD installed available."
                );

                autoMapTuning = new BooleanDeviceOption(
                        Config.getBoolean("hdhr.qam.automap_tuning_lookup", false),
                        false,
                        "ClearQAM Auto-Map by Tuning",
                        "hdhr.qam.automap_tuning_lookup",
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

                controlRetryCount = new IntegerDeviceOption(
                        Config.getInteger("hdhr.retry_count", 2),
                        false,
                        "Communication Retry Count",
                        "hdhr.retry_count",
                        "This is the number of times the program will attempt to communicate with the" +
                                " HDHomeRun device before returning a IO error.",
                        0,
                        Integer.MAX_VALUE
                );

                broadcastInterval = new IntegerDeviceOption(
                        Config.getInteger("hdhr.broadcast_s", 58),
                        false,
                        "Discovery Broadcast Interval",
                        "hdhr.broadcast_ms",
                        "This is the interval in seconds that the program will send out a" +
                                " broadcast to locate HDHomeRun devices on the network. A value of 0" +
                                " will turn off discovery after the first broadcast.",
                        0,
                        Integer.MAX_VALUE
                );

                broadcastPort = new IntegerDeviceOption(
                        Config.getInteger("hdhr.broadcast_port", 64998),
                        false,
                        "SageTV Upload ID Port",
                        "hdhr.broadcast_port",
                        "This is the port number used to send and receive the HDHomeRun discovery" +
                                " broadcast. If this value is less than 1024, the program will" +
                                " automatically select a port, the value cannot be greater than" +
                                " 65535.",
                        1023,
                        65535);

                // Ignore the HDHomeRun Prime for now.
                ignoreModels = new StringDeviceOption(
                        Config.getStringArray("hdhr.exp_ignore_models"),
                        true,
                        false,
                        "Ignore Models",
                        "hdhr.exp_ignore_models",
                        "Prevent specific HDHomeRun models from being detected and loaded."
                );

                ignoreDeviceIds = new StringDeviceOption(
                        Config.getStringArray("hdhr.ignore_device_ids"),
                        true,
                        false,
                        "Ignore Device IDs",
                        "hdhr.ignore_device_ids",
                        "Prevent specific HDHomeRun devices by ID from being detected and loaded."
                );

                alwaysTuneLegacy = new BooleanDeviceOption(
                        Config.getBoolean("hdhr.always_tune_legacy", false),
                        false,
                        "Always Tune in Legacy Mode",
                        "hdhr.always_tune_legacy",
                        "This tells the program to only tune HDHomeRun devices in legacy mode. The" +
                                " only devices that will always ignore this request are Digital Cable" +
                                " Tuners that have a CableCARD inserted since it would turn them into" +
                                " a ClearQAM device."
                );

                allowHttpTuning = new BooleanDeviceOption(
                        Config.getBoolean("hdhr.allow_http_tuning", true),
                        false,
                        "Allow HTTP Tuning",
                        "hdhr.allow_http_tuning",
                        "This will allow the HTTP URL to be used instead of RTP if a URL is available" +
                                " for the requested channel. This will allow for hardware transcoding" +
                                " on models that support it and higher reliability of the transport" +
                                " stream. Depending on how SageTV has channels mapped, sometimes a" +
                                " URL will not be located and RTP will be used instead."
                );

                transcodeProfile = new StringDeviceOption(
                        Config.getString("hdhr.extend_transcode_profile", ""),
                        false,
                        "Transcode Profile",
                        "hdhr.extend_transcode_profile",
                        "This is the profile to be used for all tuners that support hardware" +
                                " transcoding."
                );

                qamHttpTuningHack = new BooleanDeviceOption(
                        Config.getBoolean("hdhr.allow_qam_http_tuning", false),
                        false,
                        "Enable QAM HTTP Tuning",
                        "hdhr.allow_qam_http_tuning",
                        "This will allow the HTTP URL to be used instead of RTP if a URL is" +
                                " available for the requested channel. The reason this is its own" +
                                " option is because to get the correct channel it may need to" +
                                " scan the available virtual channels 5000+ on the capture" +
                                " device. It is recommended to have a seperate lineup per parent" +
                                " capture device when using this because the virtual channel" +
                                " mappings might be different between devices."
                );

                offlineDetectionSeconds = new IntegerDeviceOption(
                        Config.getInteger("hdhr.wait_for_offline_detection_s", 8),
                        false,
                        "Offline Channel Detection Seconds",
                        "hdhr.wait_for_offline_detection_s",
                        "This is the value in seconds to wait after tuning a channel before" +
                                " making a final determination on if it is tunable or not." +
                                " This applies only to offline scanning."
                );

                offlineDetectionMinBytes = new IntegerDeviceOption(
                        Config.getInteger("hdhr.offline_detection_min_bytes", 10528),
                        false,
                        "Offline Channel Detection Bytes",
                        "hdhr.offline_detection_min_bytes",
                        "This is the value in bytes that must be consumed before a channel is" +
                                " considered tunable."
                );

                Config.mapDeviceOptions(
                        deviceOptions,
                        streamingWait,
                        retunePolling,
                        autoMapReference,
                        autoMapTuning,
                        hdhrLock,
                        controlRetryCount,
                        broadcastInterval,
                        broadcastPort,
                        ignoreModels,
                        ignoreDeviceIds,
                        alwaysTuneLegacy,
                        allowHttpTuning,
                        transcodeProfile,
                        qamHttpTuningHack,
                        offlineDetectionSeconds,
                        offlineDetectionMinBytes
                );
            } catch (DeviceOptionException e) {
                logger.error("Unable to configure device options for HDHomeRunDiscoverer." +
                        " Reverting to defaults. => ", e);

                Config.setInteger("hdhr.wait_for_streaming", 5000);
                Config.setInteger("hdhr.retune_poll_s", 1);
                Config.setBoolean("hdhr.qam.automap_reference_lookup", true);
                Config.setBoolean("hdhr.qam.automap_tuning_lookup", false);
                Config.setBoolean("hdhr.locking", true);
                Config.setInteger("hdhr.retry_count", 2);
                Config.setInteger("hdhr.broadcast_s", 58);
                Config.setInteger("hdhr.broadcast_port", 64998);
                Config.setStringArray("hdhr.exp_ignore_models");
                Config.setStringArray("hdhr.ignore_device_ids");
                Config.setBoolean("hdhr.always_tune_legacy", false);
                Config.setBoolean("hdhr.allow_http_tuning", true);
                Config.setString("hdhr.extend_transcode_profile", "");
                Config.setBoolean("hdhr.allow_qam_http_tuning", false);

                continue;
            }

            break;
        }
    }

    @Override
    public String getName() {
        return HDHomeRunDiscoverer.name;
    }

    @Override
    public String getDescription() {
        return HDHomeRunDiscoverer.description;
    }

    @Override
    public boolean isEnabled() {
        return HDHomeRunDiscoverer.enabled;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        HDHomeRunDiscoverer.enabled = enabled;
        Config.setBoolean("hdhr.discoverer_enabled", enabled);
    }

    @Override
    public OSVersion[] getSupportedOS() {
        return HDHomeRunDiscoverer.supportedOS;
    }

    @Override
    public synchronized void startDetection(DeviceLoader deviceLoader) throws DiscoveryException {
        if (deviceLoader == null || !HDHomeRunDiscoverer.enabled || discovery.isRunning()) {
            return;
        }

        this.deviceLoader = deviceLoader;

        try {
            discovery.start(this);
        } catch (IOException e) {
            throw new DiscoveryException(e);
        }
    }

    @Override
    public synchronized void stopDetection() throws DiscoveryException {
        if (!discovery.isRunning()) {
            return;
        }

        discovery.stop();
    }

    @Override
    public synchronized void waitForStopDetection() throws InterruptedException {
        if (!discovery.isRunning()) {
            return;
        }

        discovery.waitForStop();

        discoveredDevicesLock.writeLock().lock();

        try {
            hdHomeRunDevices.clear();
            discoveredDevices.clear();
            discoveredParents.clear();
        } catch (Exception e) {
            logger.error("waitForStopDetection created an unexpected exception while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.writeLock().unlock();
        }
    }

    @Override
    public boolean isRunning() {
        return discovery.isRunning();
    }

    public boolean isWaitingForDevices() {
        return deviceLoader == null || deviceLoader.isWaitingForDevices();
    }

    public void addCaptureDevice(HDHomeRunDevice discoveredDevice) {

        discoveredDevicesLock.writeLock().lock();

        try {
            // This prevents accidentally re-adding a device after detection has stopped.
            if (!discovery.isRunning()) {
                return;
            }

            for (String ignoreModel : getIgnoreModels()) {
                if (discoveredDevice.getSysHwModel().toUpperCase().equals(ignoreModel.toUpperCase())) {
                    return;
                }
            }

            for (String ignoreDeviceId : getIgnoreDeviceIds()) {
                if (Integer.toHexString(discoveredDevice.getDeviceId()).toUpperCase().equals(ignoreDeviceId.toUpperCase())) {
                    return;
                }
            }

            HDHomeRunDevice updateDevice = hdHomeRunDevices.get(discoveredDevice.getDeviceId());

            if (updateDevice != null) {
                // This device has been detected before. We will only update the IP address.

                if (!(discoveredDevice.getIpAddress() == discoveredDevice.getIpAddress())) {
                    logger.info("HDHomeRun device '{}' changed its IP address from {} to {}.",
                            updateDevice.getUniqueDeviceName(),
                            updateDevice.getIpAddress().getHostAddress(),
                            discoveredDevice.getIpAddress().getHostAddress()
                    );

                    updateDevice.update(discoveredDevice);
                }

                return;
            }

            String uniqueParentName = discoveredDevice.getUniqueDeviceName();

            logger.info("Discovered a new HDHomeRun device '{}'.", uniqueParentName);

            HDHomeRunDiscoveredDeviceParent parentDevice = new HDHomeRunDiscoveredDeviceParent(
                    uniqueParentName,
                    uniqueParentName.hashCode(),
                    Util.getLocalIPForRemoteIP(discoveredDevice.getIpAddress()),
                    discoveredDevice
            );

            discoveredParents.put(parentDevice.getParentId(), parentDevice);

            for (int i = 0; i < discoveredDevice.getTunerCount(); i++) {

                String tunerName = discoveredDevice.getUniqueTunerName(i);

                HDHomeRunDiscoveredDevice newDevice = new HDHomeRunDiscoveredDevice(
                        tunerName,
                        tunerName.hashCode(),
                        parentDevice.getParentId(),
                        "HDHomeRun " + discoveredDevice.getSysHwModel() + " capture device.",
                        i,
                        parentDevice
                );

                this.discoveredDevices.put(newDevice.getId(), newDevice);

                parentDevice.addChild(newDevice.getId());

                deviceLoader.advertiseDevice(newDevice, this);
            }

            hdHomeRunDevices.put(discoveredDevice.getDeviceId(), discoveredDevice);

        } catch (IOException e) {
            logger.error("Unable to communicate with HDHomeRun device '{}' => ",
                    discoveredDevice, e);

        } catch (GetSetException e) {
            logger.error("HDHomeRun device '{}' returned an error instead of a value => ",
                    discoveredDevice, e);

        } catch (Exception e) {
            logger.error("addDevices created an unexpected exception => ", e);
        } finally {
            discoveredDevicesLock.writeLock().unlock();
        }
    }

    @Override
    public String getErrorMessage() {
        return HDHomeRunDiscoverer.errorMessage;
    }

    @Override
    public int discoveredDevices() {
        int returnValue = 0;

        discoveredDevicesLock.readLock().lock();

        try {
            returnValue = discoveredDevices.size();
        } catch (Exception e) {
            logger.error("discoveredDevices created an unexpected exception while using" +
                    " discoveredDevicesLock => ", e);
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
            for (Map.Entry<Integer, HDHomeRunDiscoveredDevice> discoveredDevice : discoveredDevices.entrySet()) {
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
            for (Map.Entry<Integer, HDHomeRunDiscoveredDeviceParent> discoveredParent : discoveredParents.entrySet()) {
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
        HDHomeRunDiscoveredDevice discoveredDevice;
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
                streamingWait,
                retunePolling,
                autoMapReference,
                autoMapTuning,
                hdhrLock,
                controlRetryCount,
                broadcastInterval,
                broadcastPort,
                ignoreModels,
                ignoreDeviceIds,
                alwaysTuneLegacy,
                allowHttpTuning,
                transcodeProfile,
                qamHttpTuningHack,
                offlineDetectionSeconds,
                offlineDetectionMinBytes
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference = HDHomeRunDiscoverer.deviceOptions.get(option.getProperty());

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

    public static int getRetunePolling() {
        return retunePolling.getInteger();
    }

    public static boolean getAutoMapReference() {
        return autoMapReference.getBoolean();
    }

    public static boolean getAutoMapTuning() {
        return autoMapTuning.getBoolean();
    }

    public static boolean getHdhrLock() {
        return hdhrLock.getBoolean();
    }

    public static int getControlRetryCount() {
        return controlRetryCount.getInteger();
    }

    public static int getBroadcastInterval() {
        return broadcastInterval.getInteger();
    }

    public static String[] getIgnoreModels() {
        return ignoreModels.getArrayValue();
    }

    public static String[] getIgnoreDeviceIds() {
        return ignoreDeviceIds.getArrayValue();
    }

    public static boolean getAlwaysTuneLegacy() {
        return alwaysTuneLegacy.getBoolean();
    }

    public static boolean getAllowHttpTuning() {
        return allowHttpTuning.getBoolean();
    }

    public static String getTranscodeProfile() {
        return transcodeProfile.getValue();
    }

    public static boolean getQamHttpTuningHack() {
        return qamHttpTuningHack.getBoolean();
    }

    public static int getOfflineDetectionSeconds() {
        return offlineDetectionSeconds.getInteger();
    }

    public static int getOfflineDetectionMinBytes() {
        return offlineDetectionMinBytes.getInteger();
    }

    public static int getBroadcastPort() {
        int returnValue = broadcastPort.getInteger();

        if (returnValue > 0 && returnValue < 1024) {
            try {
                broadcastPort.setValue(0);
            } catch (DeviceOptionException e) {

            }
            Config.setInteger("hdhr.broadcast_port", 0);

            returnValue = 0;
        }

        return returnValue;
    }
}
