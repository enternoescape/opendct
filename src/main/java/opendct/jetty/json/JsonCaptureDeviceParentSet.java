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

package opendct.jetty.json;

import opendct.capture.CaptureDevice;
import opendct.config.Config;
import opendct.jetty.JettyManager;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVUnloadedDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.core.Response;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

public class JsonCaptureDeviceParentSet {
    private static final Logger logger = LogManager.getLogger(JsonCaptureDeviceParentSet.class);

    private String encoderParentName;
    private String localAddress;

    public JsonCaptureDeviceParentSet() {
        encoderParentName = null;
        localAddress = null;
    }

    public Response applyUpdates(String oldParentName) {
        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllCaptureDevicesForParent(oldParentName);

        if (captureDevices.size() == 0) {
            JsonError jsonError = new JsonError(
                    JettyManager.NOT_FOUND,
                    "No capture devices are loaded and belong to the requested parent.",
                    oldParentName,
                    ""
            );

            logger.error("{}", jsonError);

            return Response.status(JettyManager.NOT_FOUND).entity(jsonError).build();
        }

        if (encoderParentName != null && !captureDevices.get(0).getEncoderParentName().equals(encoderParentName)) {
            for (CaptureDevice captureDevice: captureDevices) {
                if (captureDevice.isLocked()) {
                    JsonError jsonError = new JsonError(
                            JettyManager.ERROR,
                            "Unable to change the parent name of this device" +
                                    " because it is currently in use.",
                            captureDevice.getEncoderName(),
                            ""
                    );

                    logger.error("{}", jsonError);

                    return Response.status(JettyManager.ERROR).entity(jsonError).build();
                }
            }

            // Changes the name so the next time these devices are loaded, they will use this new value.
            Config.setString("sagetv.device." + captureDevices.get(0).getEncoderParentUniqueHash() + ".device_name", encoderParentName);

            for (CaptureDevice captureDevice : captureDevices) {
                if (captureDevice.getEncoderParentName().equals(oldParentName)) {
                    try {
                        SageTVUnloadedDevice unloadedDevice = SageTVManager.removeCaptureDevice(captureDevice.getEncoderName());
                        SageTVManager.addCaptureDevice(unloadedDevice.ENCODER_NAME);
                    } catch (SocketException e) {
                        JsonError jsonError = new JsonError(
                                JettyManager.ERROR,
                                "An unexpected error happened while setting the parent name property for this capture device.",
                                oldParentName,
                                e.toString()
                        );

                        logger.error("{}", jsonError, e);

                        return Response.status(JettyManager.ERROR).entity(jsonError).build();
                    }
                }
            }
        }

        if (localAddress != null && !localAddress.equals("")) {
            for (CaptureDevice captureDevice : captureDevices) {
                try {
                    captureDevice.setLocalAddress(InetAddress.getByName(localAddress));
                } catch (Exception e) {
                    JsonError jsonError = new JsonError(
                            JettyManager.ERROR,
                            "An unexpected error happened while setting the local address property for this capture device.",
                            captureDevice.getEncoderName(),
                            e.toString()
                    );

                    logger.error("{}", jsonError, e);

                    return Response.status(JettyManager.ERROR).entity(jsonError).build();
                }
            }
        }

        try {
            Config.saveConfig();
        } catch (Exception e) {
            JsonError jsonError = new JsonError(
                    JettyManager.ERROR,
                    "An unexpected error happened while saving the new properties.",
                    oldParentName,
                    e.toString()
            );

            logger.error("{}", jsonError, e);

            return Response.status(JettyManager.ERROR).entity(jsonError).build();
        }

        return Response.status(JettyManager.OK).entity(oldParentName).build();
    }

}
