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

package opendct.tuning.hdhomerun.returns;

public class HDHomeRunStatus {
    public final String CHANNEL;
    public final String LOCK_STR; //This is not the device lock.
    public final boolean SIGNAL_PRESENT;
    public final boolean LOCK_SUPPORTED;
    public final int SIGNAL_STRENGTH;
    public final int SIGNAL_TO_NOISE_QUALITY;
    public final int SYMBOL_ERROR_QUALITY;
    public final int RAW_BITS_PER_SECOND;
    public final int PACKETS_PER_SECOND;

    public HDHomeRunStatus(String channel, String lockStr, boolean signalPresent, boolean lockSupported, int signalStrength, int signalToNoiseQuality, int symbolErrorQuality, int rawBitsPerSecond, int packetsPerSecond) {
        CHANNEL = channel;
        LOCK_STR = lockStr;
        SIGNAL_PRESENT = signalPresent;
        LOCK_SUPPORTED = lockSupported;
        SIGNAL_STRENGTH = signalStrength;
        SIGNAL_TO_NOISE_QUALITY = signalToNoiseQuality;
        SYMBOL_ERROR_QUALITY = symbolErrorQuality;
        RAW_BITS_PER_SECOND = rawBitsPerSecond;
        PACKETS_PER_SECOND = packetsPerSecond;
    }

    public HDHomeRunStatus(String status) {
        String channel = "";
        String lockStr = ""; //This is not the device lock.
        boolean signalPresent = false;
        boolean lockSupported = false;
        int signalStrength = -1;
        int signalToNoiseQuality = -1;
        int symbolErrorQuality = -1;
        int rawBitsPerSecond = -1;
        int packetsPerSecond = -1;

        String split[] = status.split(" ");

        for (String section : split) {
            if (!section.contains("=")) {
                continue;
            }

            String keyValuePair[] = section.split("=");
            if (keyValuePair.length != 2) {
                continue;
            }

            String key = keyValuePair[0];
            String value = keyValuePair[1];

            try {
                if (key.equals("ch")) {
                    channel = value;
                } else if (key.equals("lock")) {
                    lockStr = value;
                } else if (key.equals("ss")) {
                    signalStrength = Integer.valueOf(value);
                } else if (key.equals("snq")) {
                    signalToNoiseQuality = Integer.valueOf(value);
                } else if (key.equals("seq")) {
                    symbolErrorQuality = Integer.valueOf(value);
                } else if (key.equals("bps")) {
                    rawBitsPerSecond = Integer.valueOf(value);
                } else if (key.equals("pps")) {
                    packetsPerSecond = Integer.valueOf(value);
                }
            } catch (NumberFormatException e) {

            }
        }

        signalPresent = signalStrength >= 45;
        if (lockStr.contains("none")) {
            if (lockStr.startsWith("(")) {
                lockSupported = false;
            } else {
                lockSupported = true;
            }
        }

        CHANNEL = channel;
        LOCK_STR = lockStr;
        SIGNAL_PRESENT = signalPresent;
        LOCK_SUPPORTED = lockSupported;
        SIGNAL_STRENGTH = signalStrength;
        SIGNAL_TO_NOISE_QUALITY = signalToNoiseQuality;
        SYMBOL_ERROR_QUALITY = symbolErrorQuality;
        RAW_BITS_PER_SECOND = rawBitsPerSecond;
        PACKETS_PER_SECOND = packetsPerSecond;
    }
}
