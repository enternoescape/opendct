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

package opendct.tuning.upnp;

import opendct.tuning.discovery.NetworkDiscoveredDeviceParent;
import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;

public class UpnpDiscoveredDeviceParent extends NetworkDiscoveredDeviceParent {
    private final static Logger logger = LogManager.getLogger(UpnpDiscoveredDeviceParent.class);

    InetAddress remoteAddress;

    public UpnpDiscoveredDeviceParent(String name, int parentId, InetAddress localAddress, InetAddress remoteAddress) {
        super(name, parentId, localAddress);
        this.remoteAddress = remoteAddress;
    }

    @Override
    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(InetAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    // The timeout only moves forward and only gets set if the destination is unreachable.
    private volatile long timeout = 0;

    /**
     * Returns if the host is currently reachable.
     * <p/>
     * If the host is unreachable, this method will not try again for up to 30 seconds.
     *
     * @return If the host is reachable.
     */
    public boolean isAvailable()
    {
        if (!UpnpDiscoverer.getDevicePingDetection())
            return true;

        // We are not protected from the remote address suddenly changing.
        InetAddress remoteAddress = this.remoteAddress;
        if (remoteAddress != null)
        {
            if (System.currentTimeMillis() > timeout) {
                try {
                    boolean returnValue = remoteAddress.isReachable(UpnpDiscoverer.getDevicePingTimeout());

                    if (returnValue) {
                        return true;
                    } else {
                        logger.warn("Unable to ping the remote address {}.", remoteAddress.toString());
                    }
                } catch (Exception e) {
                    logger.error("Unable to ping the remote address {} => ", remoteAddress.toString(), e);
                }

                // Set the timeout for 30 seconds from now. This way when multiple capture devices
                // on one host are unavailable we don't waste time finding out what we already know
                // for at least 30 more seconds which should be more than enough time to find a
                // better capture device or come back to this one and try to use it anyway.
                timeout = System.currentTimeMillis() + 30000;
            }
            return false;
        }

        return true;
    }
}
