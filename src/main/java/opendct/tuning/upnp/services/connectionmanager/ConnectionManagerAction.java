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

import opendct.tuning.upnp.services.connectionmanager.returns.GetProtocolInfo;
import opendct.tuning.upnp.services.connectionmanager.returns.PrepareForConnection;
import opendct.tuning.upnp.services.shared.ActionParameterPair;
import opendct.tuning.upnp.services.shared.ServiceActions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.meta.Service;

import java.util.Map;

public class ConnectionManagerAction {
    private final Logger logger = LogManager.getLogger(ConnectionManagerAction.class);
    public ServiceActions serviceActions;
    private UpnpService upnpService;
    private Service queryService;

    public ConnectionManagerAction(UpnpService upnpService, Service queryService) {
        this.upnpService = upnpService;
        this.queryService = queryService;
        serviceActions = new ServiceActions(upnpService, queryService);
    }

    // This is the first step when tuning a new channel.
    public PrepareForConnection setPrepareForConnection(String remoteProtocolInfo) {
        return setPrepareForConnection(remoteProtocolInfo, "0", "Output");
    }

    // This is the first step when tuning a new channel.
    public PrepareForConnection setPrepareForConnection(String remoteProtocolInfo, String peerConnectionID, String direction) {
        logger.entry();

        //Service: ConnectionManager
        //Action: PrepareForConnection
        //POST:
        // RemoteProtocolInfo:
        // PeerConnectionManager: *leave null*
        // PeerConnectionID:
        // Direction:
        //GET:
        // ConnectionID:
        // AVTransportID:
        // RcsID:

        ActionParameterPair[] actionParameterPairs = new ActionParameterPair[]{
                new ActionParameterPair("RemoteProtocolInfo", remoteProtocolInfo),
                new ActionParameterPair("PeerConnectionID", peerConnectionID),
                new ActionParameterPair("Direction", direction)};

        Map<String, ActionArgumentValue> results = serviceActions.runAction("PrepareForConnection", actionParameterPairs);

        if (results == null) {
            return logger.exit(null);
        }

        PrepareForConnection prepareForConnection = new PrepareForConnection(results);

        return logger.exit(prepareForConnection);
    }

    // This is the second step after we have disconnected from the RTP stream when stopping streaming.
    public boolean setConnectionComplete(String connectionID) {
        logger.entry();

        // This just disconnects the RTP stream.
        //Service: ConnectionManager
        //Action: ConnectionComplete
        //POST:
        // ConnectionID:
        //GET:
        // null

        Map<String, ActionArgumentValue> results;
        results = serviceActions.runAction("ConnectionComplete",
                new ActionParameterPair[]{
                        new ActionParameterPair("ConnectionID", connectionID)});

        if (results == null) {
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    // This method returns the streaming protocols you can select from the service.
    // The InfiniTV DCT only offers one (rtsp-rtp-udp:*:dri-mp2t:*) so I am not sure
    // how multiple options are delimited. The return class might need to be updated
    // to accommodate multiple values.
    public GetProtocolInfo getGetProtocolInfo() {
        logger.entry();

        //Service: ConnectionManager
        //Action: GetProtocolInfo
        //POST:
        // null
        //GET:
        // Source:
        // Sink:

        Map<String, ActionArgumentValue> results = serviceActions.runAction("GetProtocolInfo", new ActionParameterPair[]{});

        if (results == null) {
            return logger.exit(null);
        }

        return logger.exit(new GetProtocolInfo(results));
    }
}
