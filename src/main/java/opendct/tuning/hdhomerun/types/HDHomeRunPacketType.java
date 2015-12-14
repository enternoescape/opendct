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

public enum HDHomeRunPacketType {
    HDHOMERUN_TYPE_DISCOVER_REQ(0x0002),
    HDHOMERUN_TYPE_DISCOVER_RPY(0x0003),
    HDHOMERUN_TYPE_GETSET_REQ(0x0004),
    HDHOMERUN_TYPE_GETSET_RPY(0x0005),
    HDHOMERUN_TYPE_UPGRADE_REQ(0x0006),
    HDHOMERUN_TYPE_UPGRADE_RPY(0x0007);

    public final short MASK;

    HDHomeRunPacketType(int mask) {
        MASK = (short) mask;
    }
}
