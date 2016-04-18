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

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceIgnoredException;
import opendct.config.Config;
import opendct.config.OSVersion;
import opendct.config.options.*;
import opendct.tuning.discovery.*;
import opendct.tuning.upnp.UpnpDiscoveredDevice;
import opendct.tuning.upnp.UpnpDiscoveredDeviceParent;
import opendct.tuning.upnp.UpnpManager;
import opendct.tuning.upnp.config.DCTDefaultUpnpServiceConfiguration;
import opendct.tuning.upnp.listener.DiscoveryRegistryListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.model.meta.RemoteDevice;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final static Map<String, DeviceOption> deviceOptions;
    private static IntegerDeviceOption offlineDetectionSeconds;
    private static IntegerDeviceOption offlineDetectionMinBytes;
    private static LongDeviceOption streamingWait;
    private static BooleanDeviceOption smartBroadcast;

    // Detection configuration and state
    private static boolean enabled;
    private static boolean requestBroadcast;
    private static String errorMessage;
    private DeviceLoader deviceLoader;

    private final ReentrantReadWriteLock discoveredDevicesLock = new ReentrantReadWriteLock();
    private final Map<Integer, UpnpDiscoveredDevice> discoveredDevices = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, UpnpDiscoveredDeviceParent> discoveredParents = new Int2ObjectOpenHashMap<>();

    static {
        enabled = Config.getBoolean("upnp.discoverer_enabled", true);

        requestBroadcast = false;

        errorMessage = null;
        deviceOptions = new ConcurrentHashMap<>();

        while (true) {
            try {
                streamingWait = new LongDeviceOption(
                        Config.getInteger("upnp.device.wait_for_streaming", 15000),
                        false,
                        "Return to SageTV",
                        "upnp.device.wait_for_streaming",
                        "This is the maximum number of milliseconds to wait before returning to" +
                                " SageTV regardless of if the requested channel is actually streaming."
                );

                offlineDetectionSeconds = new IntegerDeviceOption(
                        Config.getInteger("upnp.device.wait_for_offline_detection_s", 18),
                        false,
                        "Offline Channel Detection Seconds",
                        "upnp.dct.wait_for_offline_detection_s",
                        "This is the value in seconds to wait after tuning a channel before" +
                                " making a final determination on if it is tunable or not." +
                                " This applies only to offline scanning."
                );

                offlineDetectionMinBytes = new IntegerDeviceOption(
                        Config.getInteger("upnp.device.offline_detection_min_bytes", 18800),
                        false,
                        "Offline Channel Detection Bytes",
                        "upnp.dct.offline_detection_min_bytes",
                        "This is the value in bytes that must be consumed before a channel is" +
                                " considered tunable."
                );

                smartBroadcast = new BooleanDeviceOption(
                        Config.getBoolean("upnp.smart_broadcast", true),
                        false,
                        "Smart Broadcast Enabled",
                        "upnp.smart_broadcast",
                        "This tells the program to only broadcast for new UPnP devices if" +
                                " one is inaccessible possibly due to an IP address change or if" +
                                " an expected device has not yet loaded. When this is enabled," +
                                " Discovery Broadcast Interval is ignored since this makes the" +
                                " broadcast run on demand."
                );

                Config.mapDeviceOptions(
                        deviceOptions,
                        offlineDetectionSeconds,
                        offlineDetectionMinBytes,
                        streamingWait,
                        smartBroadcast
                );

            } catch (DeviceOptionException e) {
                logger.error("Unable to configure device options for HDHomeRunDiscoverer." +
                        " Reverting to defaults. => ", e);

                Config.setInteger("upnp.device.wait_for_streaming", 15000);
                Config.setInteger("upnp.device.wait_for_offline_detection_s", 18);
                Config.setInteger("upnp.device.offline_detection_min_bytes", 18800);
                Config.setBoolean("upnp.smart_broadcast", true);

                continue;
            }

            break;
        }
    }

    @Override
    public String getName() {
        return UpnpDiscoverer.name;
    }

    @Override
    public String getDescription() {
        return UpnpDiscoverer.description;
    }

    @Override
    public synchronized boolean isEnabled() {
        return UpnpDiscoverer.enabled;
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        UpnpDiscoverer.enabled = enabled;
        Config.setBoolean("upnp.discoverer_enabled", enabled);
    }

    @Override
    public OSVersion[] getSupportedOS() {
        return UpnpDiscoverer.supportedOS;
    }

    @Override
    public synchronized void startDetection(DeviceLoader deviceLoader) throws DiscoveryException {

        if (deviceLoader == null || !UpnpDiscoverer.enabled || UpnpManager.isRunning()) {
            return;
        }

        this.deviceLoader = deviceLoader;

        UpnpManager.startUpnpServices(
                DCTDefaultUpnpServiceConfiguration.getDCTDefault(),
                new DiscoveryRegistryListener(this));

    }

    @Override
    public synchronized void stopDetection() throws DiscoveryException {

        if (!UpnpManager.isRunning()) {
            return;
        }

        UpnpManager.stopUpnpServices();

        discoveredDevicesLock.writeLock().lock();

        try {
            discoveredDevices.clear();
            discoveredParents.clear();
        } catch (Exception e) {
            logger.error("stopDetection created an unexpected exception while using" +
                    " discoveredDevicesLock => ", e);
        } finally {
            discoveredDevicesLock.writeLock().unlock();
        }
    }

    @Override
    public synchronized void waitForStopDetection() throws InterruptedException {
        long timeout = System.currentTimeMillis() + 5000;

        try {
            while (timeout > System.currentTimeMillis()) {
                UpnpManager.stopUpnpServices();

                if (!UpnpManager.isRunning()) {
                    return;
                }

                Thread.sleep(100);
            }
        } catch (Exception e) {
            logger.error("UPnP shutdown created an exception => ", e);
        }

        discoveredDevicesLock.writeLock().lock();

        try {
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
        return UpnpManager.isRunning();
    }

    @Override
    public String getErrorMessage() {
        return UpnpDiscoverer.errorMessage;
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
            for (Map.Entry<Integer, UpnpDiscoveredDevice> discoveredDevice : discoveredDevices.entrySet()) {
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
            for (Map.Entry<Integer, UpnpDiscoveredDeviceParent> discoveredParent : discoveredParents.entrySet()) {
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

    public void addCaptureDevice(UpnpDiscoveredDeviceParent parent, UpnpDiscoveredDevice... devices) {
        discoveredDevicesLock.writeLock().lock();

        try {
            // This prevents accidentally re-adding a device after detection has stopped.
            if (!UpnpManager.isRunning()) {
                return;
            }

            // Any universal filtering can go here.

            UpnpDiscoveredDeviceParent updateDeviceParent =
                    discoveredParents.get(parent.getParentId());

            if (updateDeviceParent != null) {
                // This device has been detected before. We will only update the IP address.

                if (!(updateDeviceParent.getRemoteAddress().equals(parent.getRemoteAddress()))) {
                    logger.info("HDHomeRun device '{}' changed its IP address from {} to {}.",
                            updateDeviceParent.getFriendlyName(),
                            updateDeviceParent.getRemoteAddress().getHostAddress(),
                            parent.getRemoteAddress().getHostAddress()
                    );

                    updateDeviceParent.setRemoteAddress(parent.getRemoteAddress());
                }

                return;
            }

            discoveredParents.put(parent.getParentId(), parent);

            boolean addedDevices = false;

            for (UpnpDiscoveredDevice newDevice : devices) {

                // The array size will usually be 1-2 devices too big.
                if (newDevice == null) {
                    continue;
                }

                addedDevices = true;

                this.discoveredDevices.put(newDevice.getId(), newDevice);

                parent.addChild(newDevice.getId());

                deviceLoader.advertiseDevice(newDevice, this);
            }

            if (!addedDevices) {
                // Remove parents without any children.
                discoveredParents.remove(parent.getParentId());
            }

        } catch (Exception e) {
            logger.error("addDevices created an unexpected exception => ", e);
        } finally {
            discoveredDevicesLock.writeLock().unlock();
        }
    }

    @Override
    public CaptureDevice loadCaptureDevice(int deviceId)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {

        CaptureDevice returnValue = null;
        UpnpDiscoveredDevice discoveredDevice;
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
            logger.warn("Capture device will not be loaded => {}", e.toString());
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

    public static InetAddress getRemoteIpAddress(RemoteDevice remoteDevice) {
        InetAddress returnAddress = null;

        if (remoteDevice.getDetails() != null &&
                remoteDevice.getDetails().getBaseURL() != null &&
                remoteDevice.getDetails().getBaseURL().getHost() != null) {

            try {
                returnAddress = InetAddress.getByName(remoteDevice.getDetails().getBaseURL().getHost());
            } catch (UnknownHostException e) {
                // Sometimes a URL doesn't show up in the base URL field soon enough.

                //(RemoteDeviceIdentity) UDN: uuid:24FB1FB3-38BF-39B9-AAC0-F1A177B8E8D5, Descriptor: http://x.x.x.x:80/dri/device.xml
                String encoderIPAddress = remoteDevice.getParentDevice().getIdentity().toString();

                try {
                    encoderIPAddress = encoderIPAddress.substring(encoderIPAddress.lastIndexOf("http://"));
                    returnAddress = InetAddress.getByName(new URI(encoderIPAddress).getHost());
                } catch (Exception e1) {
                    logger.error("Unable to parse '{}' into encoder IP address => ", encoderIPAddress, e);
                }
            }
        }


        return returnAddress;
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                offlineDetectionSeconds,
                offlineDetectionMinBytes,
                smartBroadcast
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference = UpnpDiscoverer.deviceOptions.get(option.getProperty());

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

    public static int getOfflineDetectionSeconds() {
        return offlineDetectionSeconds.getInteger();
    }

    public static int getOfflineDetectionMinBytes() {
        return offlineDetectionMinBytes.getInteger();
    }

    public static int getRetunePolling() {
        return Config.getInteger("upnp.retune_poll_s", 1);
    }

    public static boolean getHttpTuning() {
        return Config.getBoolean("upnp.dct.http_tuning", true);
    }

    public static boolean getHdhrTuning() {
        return Config.getBoolean("upnp.dct.http_tuning", true);
    }

    public static boolean getAutoMapReference() {
        return Config.getBoolean("upnp.qam.automap_reference_lookup", true);
    }

    public static boolean getAutoMapTuning() {
        return Config.getBoolean("upnp.qam.automap_tuning_lookup", false);
    }

    public static boolean getFastTune() {
        return Config.getBoolean("upnp.dct.fast_tuning", false);
    }

    public static boolean getHdhrLock() {
        return Config.getBoolean("hdhr.locking", true);
    }

    public static long getStreamingWait() {
        return streamingWait.getLong();
    }

    public static boolean getSmartBroadcast() {
        return smartBroadcast.getBoolean();
    }

    public synchronized static void requestBroadcast() {
        requestBroadcast = true;
    }

    public synchronized static boolean needBroadcast() {
        boolean returnValue = requestBroadcast;
        requestBroadcast = false;
        return returnValue;
    }
}
