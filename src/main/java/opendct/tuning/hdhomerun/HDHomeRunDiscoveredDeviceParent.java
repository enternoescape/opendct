/*
 * Copyright 2016 The OpenDCT Authors. All Rights Reserved
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

package opendct.tuning.hdhomerun;

import opendct.tuning.discovery.NetworkDiscoveredDeviceParent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;

public class HDHomeRunDiscoveredDeviceParent extends NetworkDiscoveredDeviceParent {
    private final Logger logger = LogManager.getLogger(HDHomeRunDiscoveredDeviceParent.class);

    public HDHomeRunDiscoveredDeviceParent(String name, int parentId, InetAddress localAddress, InetAddress remoteAddress) {
        super(name, parentId, localAddress, remoteAddress);
    }
}
