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
import opendct.jetty.JettyManager;
import opendct.sagetv.SageTVManager;

import javax.ws.rs.core.Response;

public class JsonCaptureDeviceAdvancedDetails {
    private boolean isExternalLocked;
    private boolean isReady;
    private String broadcastStandard;
    private int signalStrength;
    private String copyProtection;

    /**
     * Sets the details of the object to reflect the current details of this capture device.
     *
     * @param captureDeviceName The name of the capture device to get details.
     * @return <i>true</i> if the capture device was found and everything is populated.
     * @throws JsonGetException Thrown if there was a problem populating details.
     */
    public Response setCaptureDeviceDetails(String captureDeviceName) {
        CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(captureDeviceName, false);

        if (captureDevice == null) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.NOT_FOUND,
                            "The capture device requested does not exist.",
                            captureDeviceName,
                            ""
                    )
            ).build();
        }

        try {
            isExternalLocked = captureDevice.isExternalLocked();
            isReady = captureDevice.isReady();
            broadcastStandard = captureDevice.getBroadcastStandard().toString();
            signalStrength = captureDevice.getSignalStrength();
            copyProtection = captureDevice.getCopyProtection().toString();
        } catch (Exception e) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.ERROR,
                            "An unexpected error happened while getting advanced details about this capture device.",
                            captureDeviceName,
                            e.toString()
                    )
            ).build();
        }

        return Response.status(JettyManager.OK).entity(this).build();
    }

    public boolean isExternalLocked() {
        return isExternalLocked;
    }

    public boolean isReady() {
        return isReady;
    }

    public String getBroadcastStandard() {
        return broadcastStandard;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public String getCopyProtection() {
        return copyProtection;
    }
}
