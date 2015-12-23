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

import opendct.capture.CaptureDeviceIgnoredException;
import opendct.capture.DCTCaptureDeviceImpl;
import opendct.config.Config;
import opendct.power.NetworkPowerEventManger;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVUnloadedDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.Registry;

public class RegisterDevice {
    private static final Logger logger = LogManager.getLogger(RegisterDevice.class);
    private static volatile int failedAdds = 0;
    private static volatile int successfulAdds = 0;
    private static volatile int ignoredAdds = 0;

    public static boolean addRemoteDevice(Registry registry, RemoteDevice remoteDevice) {
        logger.entry(registry, remoteDevice);

        // Since anything on the network that feels like responding will
        // come in, we need to filter out devices that don't meet our
        // actual request. This could also be used to send different
        // devices into different initialization paths.
        String dctSchemaFilters[] = Config.getStringArray("upnp.new.device.schema_filter_strings_csv", "schemas-cetoncorp-com", "schemas-dkeystone-com");
        String deviceName = "Unknown Device";
        String deviceSchema = "Unknown Schema";
        try {
            deviceName = remoteDevice.getDetails().getFriendlyName();
            deviceSchema = remoteDevice.getType().getNamespace();
            logger.debug("Checking if the schema '{}' can be used...", deviceSchema);

            UpnpService upnpService = registry.getUpnpService();

            for (String dctSchemaFilter : dctSchemaFilters) {
                if (deviceSchema.equals(dctSchemaFilter)) {
                    logger.debug("Creating network encoders from the embedded devices on '{}' with the namespace '{}'.", deviceName, deviceSchema);
                    RemoteDevice[] embeddedDevices = remoteDevice.getEmbeddedDevices();

                    for (Device embeddedDevice : embeddedDevices) {
                        try {
                            if (embeddedDevice.getDisplayString().contains("MOCUR-OCTA")) {
                                logger.debug("Skipping embedded device '{}' because it is not a tuner.", embeddedDevice.getDisplayString());
                                continue;
                            }

                            String embeddedName = embeddedDevice.getDetails().getFriendlyName();

                            logger.debug("Attempting to create capture device from embedded device '{}'...", embeddedName);

                            // This makes it a little easier to troubleshoot if the threads get frozen.
                            Thread.currentThread().setName("cling-" + Thread.currentThread().getId() + ":" + embeddedName);
                            DCTCaptureDeviceImpl captureDevice = new DCTCaptureDeviceImpl(embeddedDevice);

                            // This adds the capture device to the SageTV manager and starts the
                            // SageTV socket server for the port assigned to this capture device
                            // if it is not already running.
                            SageTVManager.addCaptureDevice(captureDevice);

                            try {
                                NetworkPowerEventManger.POWER_EVENT_LISTENER.addDependentInterface(captureDevice.getEncoderIpAddress());
                            } catch (Exception e) {
                                logger.error("Unable to register device with a valid network interface => ", e);
                            }

                            successfulAdds += 1;
                        } catch (NullPointerException e) {
                            logger.debug("There was a problem initializing the capture device => ", e);
                            failedAdds += 1;
                        } catch (CaptureDeviceIgnoredException e) {

                            // This provided SageTVManager with an object to initialize later if requested.
                            final SageTVUnloadedDevice sageTVUnloadedDevice = new SageTVUnloadedDevice(
                                    "DCT-" + embeddedDevice.getDetails().getFriendlyName(),
                                    DCTCaptureDeviceImpl.class,
                                    new Object[]{embeddedDevice},
                                    new Class[]{Device.class},
                                    false);

                            SageTVManager.addUnloadedDevice(sageTVUnloadedDevice);

                            logger.debug("The capture device was was not permitted to initialize => {}", e.toString());
                            ignoredAdds += 1;
                        } catch (Exception e) {
                            logger.error("There was a problem creating network encoders from the embedded devices on '{}' with the schema '{}' => ", deviceName, deviceSchema, e);
                            failedAdds += 1;
                        }
                    }

                    // Let's make sure we don't add the same tuner twice due to a repetitive configuration.
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("There was a problem creating network encoders from the embedded devices on '{}' with the schema '{}' => {}", deviceName, deviceSchema, e);
            failedAdds += 1;
        }

        return logger.exit(true);
    }

    public static boolean removeRemoteDevice(Registry registry, RemoteDevice remoteDevice) {
        //TODO: [js] Do we want to do anything about device removal?
        return true;
    }

    public static int getFailedAdds() {
        return failedAdds;
    }

    public static int getSuccessfulAdds() {
        return successfulAdds;
    }

    public static int getIgnoredAdds() {
        return ignoredAdds;
    }
}
