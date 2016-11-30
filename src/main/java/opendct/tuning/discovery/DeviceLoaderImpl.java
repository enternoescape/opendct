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
import opendct.power.NetworkPowerEventManger;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;

public class DeviceLoaderImpl implements DeviceLoader {
    private static final Logger logger = LogManager.getLogger(DeviceLoaderImpl.class);

    // Once the web interface becomes the new way to load specific devices, this value will be
    // changed to discovery.devices.always_enable=false.
    private static boolean alwaysEnable = Config.getBoolean("discovery.devices.exp_always_enable", true);

    public static void disableAlwaysEnable() {
        if (!alwaysEnable || Config.getBoolean("discovery.devices.always_enable", false)) {
            return;
        }
        // Stop enabling devices by default.
        Config.setBoolean("discovery.devices.exp_always_enable", false);
        alwaysEnable = false;

        logger.info("Device availability has been manipulated via the JSON web interface. Saving" +
                " all currently loaded capture devices as enabled. All new capture devices will" +
                " be DISABLED by default.");
        List<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();
        int deviceIDs[] = new int[captureDevices.size()];
        for (int i = 0; i < deviceIDs.length; i++) {
            deviceIDs[i] = captureDevices.get(i).getEncoderUniqueHash();
        }
        DiscoveryManager.permitDevices(deviceIDs);

        // Clear out the legacy way to prevent capture devices from loading so that it doesn't
        // interfere if we try to load a previously disabled device. Also back up the old values so
        // we can restore them if the user changes their mind.
        Config.setString("sagetv.device.global.ignore_devices_csv_old", Config.getString("sagetv.device.global.ignore_devices_csv", ""));
        Config.setString("sagetv.device.global.ignore_devices_csv", "");
        Config.setString("sagetv.device.global.only_devices_csv_old", Config.getString("sagetv.device.global.only_devices_csv", ""));
        Config.setString("sagetv.device.global.only_devices_csv", "");
        logger.info("Cleared sagetv.device.global.ignore_devices_csv" +
                " and sagetv.device.global.only_devices_csv properties.");
    }

    public static void enableAlwaysEnable() {
        // Start enabling devices by default.
        Config.setBoolean("discovery.devices.exp_always_enable", true);
        alwaysEnable = true;

        // Restore the legacy configuration based on the current configuration.
        /*StringBuilder builder = new StringBuilder();
        List<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();
        for (CaptureDevice device : captureDevices) {
            builder.append(device.getEncoderName()).append(',');
        }
        if (builder.length() > 0) {
            builder.setLength(builder.length() - 1);
        }
        Config.setString("sagetv.device.global.only_devices_csv", builder.toString());*/

        // Restore legacy configuration.
        String ignore = Config.getString("sagetv.device.global.ignore_devices_csv_old", "");
        String only = Config.getString("sagetv.device.global.only_devices_csv_old", "");
        if (ignore.length() > 0) {
            Config.setString("sagetv.device.global.ignore_devices_csv", ignore);
        }
        if (only.length() > 0) {
            Config.setString("sagetv.device.global.only_devices_csv", only);
        }
    }

    @Override
    public synchronized void advertiseDevice(DiscoveredDevice details, DeviceDiscoverer discovery) {

        if (DiscoveryManager.isDevicePermitted(details.getId()) && !alwaysEnable) {
            logger.debug("The capture device '{}' is not permitted to loaded.", details.getName());
            return;
        } else {
            logger.debug("Advertising new capture device '{}'.", details.getName());
        }

        try {
            CaptureDevice captureDevice = discovery.loadCaptureDevice(details.getId());

            if (captureDevice == null) {
                logger.error("The capture device '{}' did not load.", details.getName());
                return;
            }

            SageTVManager.addCaptureDevice(captureDevice);

            DiscoveredDeviceParent parent = discovery.getDeviceParentDetails(details.getParentId());

            if (parent != null) {
                if (parent.isNetworkDevice()) {
                    if (parent.getRemoteAddress() == null) {
                        logger.warn("The capture device parent '{}' reports that it is a network" +
                                " device, but does not have a remote address.",
                                parent.getName());

                    } else {
                        if (!parent.getRemoteAddress().equals(InetAddress.getLoopbackAddress())) {
                            try {
                                NetworkPowerEventManger.POWER_EVENT_LISTENER
                                        .addDependentInterface(parent.getRemoteAddress());
                            }catch(IOException e){
                                logger.warn("Unable to register dependent interface '{}' => ",
                                        parent.getRemoteAddress(), e);
                            }
                        }
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

        } catch (Throwable e) {
            logger.error("Unexpected exception created by the capture device '{}', id {} => ",
                    details.getName(), details.getId(), e);

        }
    }

    @Override
    public boolean isWaitingForDevices() {
        return !SageTVManager.captureDevicesLoaded();
    }
}
