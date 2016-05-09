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

package opendct.sagetv;

public enum SageTVDeviceType {
    TV_TUNER(1, "TV Tuner"),
    COMPOSITE(2, "Composite"),
    S_VIDEO(3, "S Video"),
    RGB(4, "RGB"),
    COMPONENT(5, "Component"),
    HDMI(6, "HDMI"),
    PARALLEL_DIGITAL(7, "ParallelDigital"),
    SCSI(8, "SCSI"),
    AUX(9, "AUX"),
    _1394(10, "1394"),
    USB(11, "USB"),
    VIDEO_DECODER(12, "VideoDecoder"),
    VIDEO_ENCODER(13, "VideoEncoder"),
    SCART(14, "SCART"),
    BLACK(15, "Black"),
    FM_RADIO(99, "FM Radio"),
    DIGITAL_TV_TUNER(100, "Digital TV Tuner"),
    YPBPR_SPDIF(90, "Component+SPDIF");

    public final int INDEX;
    public final String NAME;

    SageTVDeviceType(int code, String name) {
        INDEX = code;
        NAME = name;
    }

    public static SageTVDeviceType getTypeForName(String deviceName) {
        if (deviceName.endsWith(DIGITAL_TV_TUNER.NAME)) {
            return DIGITAL_TV_TUNER;
        }

        for (SageTVDeviceType type : SageTVDeviceType.values()) {
            if (deviceName.endsWith(type.NAME)) {
                return type;
            }
        }

        // If for any reason this can't be determined, assume it must be a digital tuner.
        return DIGITAL_TV_TUNER;
    }

    public static String trimToName(String deviceName, SageTVDeviceType type) {
        if (deviceName.endsWith(type.NAME)) {
            return deviceName.substring(0, deviceName.length() - type.NAME.length()).trim();
        }

        return deviceName;
    }
}
