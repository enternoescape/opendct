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

import opendct.config.Config;
import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import opendct.tuning.upnp.InfiniTVDiscoveredDevice;
import opendct.tuning.upnp.InfiniTVDiscoveredDeviceParent;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteDevice;

import java.net.InetAddress;
import java.net.SocketException;

public class RegisterInfiniTVDevice {
    private static final Logger logger = LogManager.getLogger(RegisterInfiniTVDevice.class);

    private static final String dctSchemaFilters[] =
            Config.getStringArray("upnp.infinitv.device.schema_filter_strings_csv",
                    "schemas-cetoncorp-com");


    public static void addRemoteDevice(UpnpDiscoverer discoverer, RemoteDevice parentDevice) {

        if (parentDevice.getDetails() == null || parentDevice.getType() == null) {
            logger.error("Unable to get UPnP device details or type.");
            return;
        }

        String parentName = parentDevice.getDetails().getFriendlyName();
        String deviceSchema = parentDevice.getType().getNamespace();

        logger.debug("Checking if the schema '{}' can be used...", deviceSchema);

        if (parentName == null || deviceSchema == null) {
            logger.error("Unable to identify the UPnP device.");
            return;
        }

        int parentId = parentName.hashCode();
        InetAddress parentRemoteAddress = UpnpDiscoverer.getRemoteIpAddress(parentDevice);

        if (parentRemoteAddress == null) {
            logger.error("Unable to get the remote address for '{}'", parentName);
            return;
        }

        InetAddress parentLocalAddress;

        try {
            parentLocalAddress = Util.getLocalIPForRemoteIP(parentRemoteAddress);
        } catch (SocketException e) {
            logger.error("Unable to get the local address for '{}' => ", parentName, e);
            return;
        }
        InfiniTVDiscoveredDeviceParent newParent = new InfiniTVDiscoveredDeviceParent(
                parentName,
                parentId,
                parentLocalAddress,
                parentRemoteAddress);

        boolean correctDevice = false;
        for (String dctSchemaFilter : dctSchemaFilters) {
            if (deviceSchema.equals(dctSchemaFilter)) {
                correctDevice = true;
                break;
            }
        }

        if (!correctDevice) {
            return;
        }

        logger.debug("Creating network encoders from the embedded devices on '{}'" +
                " with the namespace '{}'.", parentName, deviceSchema);
        RemoteDevice[] embeddedDevices = parentDevice.getEmbeddedDevices();
        InfiniTVDiscoveredDevice newDevices[] = new InfiniTVDiscoveredDevice[embeddedDevices.length];
        int nextDevice = 0;

        for (Device embeddedDevice : embeddedDevices) {
            if (embeddedDevice.getDisplayString().contains("MOCUR-OCTA")) {
                logger.debug("Skipping embedded device '{}' because it is not a tuner.",
                        embeddedDevice.getDisplayString());

                continue;
            }

            String embeddedName = embeddedDevice.getDetails().getFriendlyName();

            if (embeddedName == null) {
                logger.debug("Skipping embedded device '{}' because it does not have a name.",
                        embeddedDevice.getDisplayString());

                continue;
            }

            String childName = "DCT-" + embeddedName;
            int childId = childName.hashCode();

            newDevices[nextDevice++] = new InfiniTVDiscoveredDevice(childName, childId, parentId,
                    "UPnP discovered InfiniTV capture device.", newParent, embeddedDevice);
        }

        discoverer.addCaptureDevice(newParent, newDevices);
    }

    public static void updateRemoteDevice(UpnpDiscoverer discoverer, RemoteDevice remoteDevice) {

        if (remoteDevice.getDetails() == null || remoteDevice.getType() == null) {
            logger.error("Unable to get UPnP device details or type.");
            return;
        }

        String parentName = remoteDevice.getDetails().getFriendlyName();
        String deviceSchema = remoteDevice.getType().getNamespace();

        logger.debug("Checking if the schema '{}' can be used...", deviceSchema);

        if (parentName == null || deviceSchema == null) {
            logger.error("Unable to identify the UPnP device.");
            return;
        }

        int parentId = parentName.hashCode();
        InetAddress parentRemoteAddress = UpnpDiscoverer.getRemoteIpAddress(remoteDevice);

        if (parentRemoteAddress == null) {
            logger.error("Unable to get the remote address for '{}'", parentName);
            return;
        }

        InetAddress parentLocalAddress;

        try {
            parentLocalAddress = Util.getLocalIPForRemoteIP(parentRemoteAddress);
        } catch (SocketException e) {
            logger.error("Unable to get the local address for '{}' => ", parentName, e);
            return;
        }

        InfiniTVDiscoveredDeviceParent newParent = new InfiniTVDiscoveredDeviceParent(
                parentName,
                parentId,
                parentLocalAddress,
                parentRemoteAddress);

        discoverer.addCaptureDevice(newParent);
    }
}
