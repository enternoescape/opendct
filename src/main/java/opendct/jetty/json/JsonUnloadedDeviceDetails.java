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

import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVUnloadedDevice;

public class JsonUnloadedDeviceDetails {
    private String encoderName;
    private String description;
    private boolean persistent;

    /**
     * Sets the details of the object to reflect the current details of this capture device.
     *
     * @param unloadDeviceName The name of the capture device to get details.
     * @return <i>true</i> if the capture device was found and everything is populated.
     * @throws JsonGetException Thrown if there was a problem populating details.
     */
    public boolean setUnloadedDeviceDetails(String unloadDeviceName) throws JsonGetException {
        SageTVUnloadedDevice unloadedDevice = SageTVManager.getUnloadedDevice(unloadDeviceName);

        if (unloadedDevice == null) {
            return false;
        }

        try {
            encoderName = unloadedDevice.ENCODER_NAME;
            description = unloadedDevice.DESCRIPTION;
            persistent = unloadedDevice.isPersistent();
        } catch (Exception e) {
            throw new JsonGetException(e);
        }

        return true;
    }

    public String getEncoderName() {
        return encoderName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPersistent() {
        return persistent;
    }
}
