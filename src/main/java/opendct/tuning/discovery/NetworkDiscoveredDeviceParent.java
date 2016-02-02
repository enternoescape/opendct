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

package opendct.tuning.discovery;

import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.StringDeviceOption;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;

public abstract class NetworkDiscoveredDeviceParent extends BasicDiscoveredDeviceParent {
    private final Logger logger = LogManager.getLogger(NetworkDiscoveredDeviceParent.class);

    private InetAddress localAddress;
    private InetAddress remoteAddress;

    protected String propertiesLocalAddressOverride = propertiesDeviceParent + "local_ip_override";

    public NetworkDiscoveredDeviceParent(String name, int parentId, InetAddress localAddress, InetAddress remoteAddress) {
        super(name, parentId);

        if (!Util.isNullOrEmpty(Config.getString(propertiesLocalAddressOverride))) {
            localAddress = Config.getInetAddress(propertiesLocalAddressOverride, localAddress);
        }

        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public boolean isNetworkDevice() {
        return true;
    }

    @Override
    public InetAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    protected void setRemoteAddress(InetAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public DeviceOption getLocalAddressOverrideOption() throws DeviceOptionException {
        return new StringDeviceOption(
                localAddress.toString(),
                false,
                "Local IP Address Override",
                propertiesLocalAddressOverride,
                "This is used to override the local IP address of the network interface used when" +
                        " streaming to OpenDCT."
        );
    }

    @Override
    public DeviceOption[] getOptions() {
        try {
            return new DeviceOption[] {
                    getParentNameOption(),
                    getLocalAddressOverrideOption()
            };
        } catch (DeviceOptionException e) {
            logger.error("getOptions is unable to return options => ", e);
        }

        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            if (option.getProperty().equals(propertiesLocalAddressOverride)) {
                try {
                    localAddress = InetAddress.getByName(option.getValue());
                    Config.setDeviceOption(option);
                } catch (UnknownHostException e) {
                    throw new DeviceOptionException("The provided address is not a valid hostname" +
                            " or IP address.", option);
                }
            } else {
                super.setOptions(option);
            }
        }
    }
}