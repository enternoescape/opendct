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

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;

public class JsonCaptureDeviceParentDetails {
    private ArrayList<String> encoderNames;
    private String encoderParentName;
    private boolean networkDevice;
    private String remoteAddress;
    private String localAddress;
    private boolean cableCardPresent;

    public Response setCaptureDeviceParentDetails(String captureDeviceParentName) {
        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllCaptureDevicesForParent(captureDeviceParentName);

        if (captureDevices.size() == 0) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.NOT_FOUND,
                            "The capture device parent requested does not exist or has no capture devices loaded.",
                            captureDeviceParentName,
                            ""
                    )
            ).build();
        }

        CaptureDevice captureDevice = captureDevices.get(0);

        try {
            encoderParentName = captureDevice.getEncoderParentName();
            networkDevice = captureDevice.isNetworkDevice();
            remoteAddress = captureDevice.getRemoteAddress().getHostAddress();
            localAddress = captureDevice.getLocalAddress().getHostAddress();
            cableCardPresent = Boolean.valueOf(Config.getString("sagetv.device.parent." + captureDevice.getEncoderParentUniqueHash() + ".cable_card_inserted"));

            encoderNames = new ArrayList<>();
            for (CaptureDevice device : captureDevices) {
                encoderNames.add(device.getEncoderName());
            }

            Collections.sort(encoderNames);
        } catch (Exception e) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.ERROR,
                            "An unexpected error happened while getting details about this capture device parent.",
                            captureDeviceParentName,
                            e.toString()
                    )
            ).build();
        }

        return Response.status(JettyManager.OK).entity(this).build();
    }

    public String getEncoderParentName() {
        return encoderParentName;
    }

    public boolean isNetworkDevice() {
        return networkDevice;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public ArrayList<String> getEncoderNames() {
        return encoderNames;
    }

    public boolean isCableCardPresent() {
        return cableCardPresent;
    }
}
