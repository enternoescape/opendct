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

package opendct.tuning.upnp.services.connectionmanager;

import opendct.tuning.upnp.services.shared.ServiceSubscription;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Service;

public class ConnectionManagerSubscription extends ServiceSubscription {
    private final Logger logger = LogManager.getLogger(ConnectionManagerSubscription.class);

    // ConnectionManager subscription returned variables.
    private String connectionManagerSinkProtocolInfo = "rtsp-rtp-udp:*:dri-mp2t:*";
    private String connectionManagerCurrentConnectionIDs = "0";
    private String connectionManagerSourceProtocolInfo = "QAM256";

    public ConnectionManagerSubscription(UpnpService upnpService, Service service) {
        super(upnpService, service);
    }

    public String getConnectionManagerSinkProtocolInfo() {
        connectionManagerSinkProtocolInfo = getReturnedEventValue("SinkProtocolInfo", connectionManagerSinkProtocolInfo);
        return connectionManagerSinkProtocolInfo;
    }

    public String getConnectionManagerCurrentConnectionIDs() {
        connectionManagerCurrentConnectionIDs = getReturnedEventValue("CurrentConnectionIDs", connectionManagerCurrentConnectionIDs);
        return connectionManagerCurrentConnectionIDs;
    }

    public String getConnectionManagerSourceProtocolInfo() {
        connectionManagerSourceProtocolInfo = getReturnedEventValue("SourceProtocolInfo", connectionManagerSourceProtocolInfo);
        return connectionManagerSourceProtocolInfo;
    }
}
