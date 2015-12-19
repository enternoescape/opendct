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

package opendct.tuning.upnp.config;

import opendct.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.transport.impl.CDATAGENAEventProcessorImpl;
import org.fourthline.cling.transport.impl.NetworkAddressFactoryImpl;
import org.fourthline.cling.transport.impl.SOAPActionProcessorImpl;
import org.fourthline.cling.transport.spi.GENAEventProcessor;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.fourthline.cling.transport.spi.SOAPActionProcessor;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Iterator;

public class DCTDefaultUpnpServiceConfiguration {
    private static final Logger logger = LogManager.getLogger(DCTDefaultUpnpServiceConfiguration.class);

    // Extending this class will add a dependency for javax.enterprise.inject.Alternative
    // so this is the easier alternative.
    public static DefaultUpnpServiceConfiguration getDCTDefault() {
        final int listenPort = Config.getInteger("upnp.service.configuration.http_listen_port", 8501);
        final String excludeInterfaces[] = Config.getStringArray("upnp.service.configuration.ignore_interfaces_csv");
        final InetAddress excludeLocalAddresses[] = Config.getInetAddressArray("upnp.service.configuration.ignore_local_ip_csv");

        return new DefaultUpnpServiceConfiguration() {

            @Override
            public SOAPActionProcessor getSoapActionProcessor() {
                // Default.
                return new SOAPActionProcessorImpl();
            }

            @Override
            public GENAEventProcessor getGenaEventProcessor() {
                // Modified to return the CDATA returned in the XML of DCT subscription events.
                return new CDATAGENAEventProcessorImpl();
            }

            @Override
            public NetworkAddressFactory createNetworkAddressFactory() {
                return new NetworkAddressFactoryImpl(listenPort) {
                    @Override
                    protected boolean isUsableAddress(NetworkInterface networkInterface, InetAddress address) {
                        if (!super.isUsableAddress(networkInterface, address)) {
                            return false;
                        }

                        for (String excludeInterface : excludeInterfaces) {
                            if (networkInterface.getName().toLowerCase().equals(excludeInterface.toLowerCase())) {
                                logger.info("Excluding the interface '{}' with IP address {} from UPnP discovery because it matched {}.", networkInterface.getName(), address.getHostAddress(), excludeInterface);
                                return false;
                            }
                        }

                        for (InetAddress excludeLocalAddress : excludeLocalAddresses) {
                            if (address.equals(excludeLocalAddress)) {
                                logger.info("Excluding interface '{}' with IP address {} from UPnP discovery because it matched {}.", networkInterface.getName(), address.getHostAddress(), excludeLocalAddress);
                                return false;
                            }
                        }

                        logger.info("Using the interface '{}' with IP address {} for UPnP discovery.", networkInterface.getName(), address.getHostAddress());

                        return true;
                    }
                };

            }
        };
    }
}
