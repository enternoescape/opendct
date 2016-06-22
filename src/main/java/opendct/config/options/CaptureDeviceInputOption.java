/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.config.options;

import opendct.capture.CaptureDevice;
import opendct.sagetv.SageTVDeviceCrossbar;
import opendct.sagetv.SageTVManager;

import java.util.ArrayList;

public class CaptureDeviceInputOption extends BaseDeviceOption {

    public CaptureDeviceInputOption(String value, String name, String property, String description) throws DeviceOptionException {
        super(DeviceOptionType.STRING, false, name, property, description);
        setValue(value);
    }

    // We don't actually validate on these, but this will ensure a current list is provided.
    @Override
    public String[] getValidValues() {
        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();
        ArrayList<String> returnValue = new ArrayList<>(captureDevices.size() * 2);


        for (CaptureDevice captureDevice : captureDevices) {
            for (SageTVDeviceCrossbar crossbar : captureDevice.getSageTVDeviceCrossbars()) {
                returnValue.add(captureDevice + " " + crossbar.NAME);
            }
        }

        return returnValue.toArray(new String[returnValue.size()]);
    }
}
