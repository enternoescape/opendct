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

    // Extending this class will add a dependency for javax.enterprise.inject.Alternative
    // so this is the easier alternative.
    public static DefaultUpnpServiceConfiguration getDCTDefault() {
        final int listenPort = Config.getInteger("upnp.service.configuration.listen_port", 8501);

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

                new NetworkAddressFactoryImpl(0);

                return new NetworkAddressFactoryImpl(listenPort) {
                    @Override
                    public Iterator<NetworkInterface> getNetworkInterfaces() {
                        Iterator<NetworkInterface> interfaces = super.getNetworkInterfaces();

                        return interfaces;
                    }
                };

            }
        };
    }
}
