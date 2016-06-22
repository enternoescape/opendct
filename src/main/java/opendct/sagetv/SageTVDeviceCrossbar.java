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

public enum SageTVDeviceCrossbar {
    UNKNOWN(0, "Unknown"),
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
    YPBPR_SPDIF(90, "Component+SPDIF"),
    FM_RADIO(99, "FM Radio"),
    DIGITAL_TV_TUNER(100, "Digital TV Tuner");

    public final int INDEX;
    public final String NAME;

    SageTVDeviceCrossbar(int code, String name) {
        INDEX = code;
        NAME = name;
    }

    /**
     * Convert a device name to a crossbar and index (if there are more than one of the same
     * crossbar.)
     *
     * @param deviceName The device name as provided to this network encoder by SageTV.
     * @param index An array with at least one element. The index will be returned via this array.
     * @return The device crossbar name.
     */
    public static SageTVDeviceCrossbar getTypeForName(String deviceName, int index[]) {
        int multi = deviceName.lastIndexOf("_");

        if (multi > 0) {
            if (index.length > 0) {
                try {
                    index[0] = Integer.parseInt(deviceName.substring(multi + 1)) - 1;
                } catch (NumberFormatException e) {
                    index[0] = 0;
                }
            }

            deviceName = deviceName.substring(0, multi);
        }

        if (deviceName.endsWith(DIGITAL_TV_TUNER.NAME)) {
            return DIGITAL_TV_TUNER;
        }

        for (SageTVDeviceCrossbar type : SageTVDeviceCrossbar.values()) {
            if (deviceName.endsWith(type.NAME)) {
                return type;
            }
        }

        // If for any reason this can't be determined, assume it must be a digital tuner.
        return DIGITAL_TV_TUNER;
    }

    /**
     * Remove the crossbar information from device name.
     *
     * @param deviceName The raw device name.
     * @param type The crossbar type to be removed.
     * @param index The index of the crossbar type to be removed.
     * @return The filtered device name.
     */
    public static String trimToName(String deviceName, SageTVDeviceCrossbar type, int index) {
        if (index > 0) {
            String indexedName = type.NAME + "_" + (index + 1);

            if (deviceName.endsWith(indexedName)) {
                return deviceName.substring(0, deviceName.length() - indexedName.length()).trim();
            }
        } else {
            if (deviceName.endsWith(type.NAME)) {
                return deviceName.substring(0, deviceName.length() - type.NAME.length()).trim();
            }
        }

        return deviceName;
    }
}
