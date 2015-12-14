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

package opendct.tuning.hdhomerun.types;

public enum HDHomeRunPacketTag {
    HDHOMERUN_TAG_DEVICE_TYPE(0x01),
    HDHOMERUN_TAG_DEVICE_ID(0x02),
    HDHOMERUN_TAG_GETSET_NAME(0x03),
    HDHOMERUN_TAG_GETSET_VALUE(0x04),
    HDHOMERUN_TAG_GETSET_LOCKKEY(0x15),
    HDHOMERUN_TAG_ERROR_MESSAGE(0x05),
    HDHOMERUN_TAG_TUNER_COUNT(0x10),
    HDHOMERUN_TAG_DEVICE_AUTH_BIN(0x29),
    HDHOMERUN_TAG_BASE_URL(0x2A),
    HDHOMERUN_TAG_DEVICE_AUTH_STR(0x2B);

    public final byte MASK;

    HDHomeRunPacketTag(int mask) {
        MASK = (byte) mask;
    }
}
