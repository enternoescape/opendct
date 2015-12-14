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

package opendct.tuning.upnp.services.avtransport;

import opendct.tuning.upnp.services.avtransport.returns.GetMediaInfo;
import opendct.tuning.upnp.services.shared.ActionParameterPair;
import opendct.tuning.upnp.services.shared.ServiceActions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.meta.Service;

import java.util.Map;

public class AVTransportAction {
    private final Logger logger = LogManager.getLogger(AVTransportAction.class);
    public final ServiceActions SERVICE_ACTIONS;
    private final UpnpService upnpService;
    private final Service queryService;

    public AVTransportAction(UpnpService upnpService, Service queryService) {
        this.upnpService = upnpService;
        this.queryService = queryService;
        SERVICE_ACTIONS = new ServiceActions(upnpService, queryService);
    }

    // This is the second step when tuning a new channel.
    public boolean setPlay(String instanceID) {
        return setPlay(instanceID, "1");
    }

    // This is the second step when tuning a new channel.
    // The speed so far is always 1, but having the option to change it leaves things more
    // flexible. I know this seems backwards since we have not tuned into anything yet, but
    // this is the correct order.
    public boolean setPlay(String instanceID, String speed) {
        logger.entry();

        //Service: AVTransport
        //Action: Play
        //POST:
        // InstanceID:
        // Speed:
        //GET:
        // null

        Map<String, ActionArgumentValue> results;
        results = SERVICE_ACTIONS.runAction("Play",
                new ActionParameterPair[]{
                        new ActionParameterPair("InstanceID", instanceID),
                        new ActionParameterPair("Speed", speed)});

        if (results == null) {
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    // This is the first step after we have disconnected from the RTP stream when stopping streaming.
    public boolean setStop(String instanceID) {
        logger.entry();

        //Service: AVTransport
        //Actions: Stop
        //POST:
        // InstanceID:
        //GET:
        // null

        Map<String, ActionArgumentValue> results;
        results = SERVICE_ACTIONS.runAction("Stop",
                new ActionParameterPair[]{
                        new ActionParameterPair("InstanceID", instanceID)});

        if (results == null) {
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    // This is the first step after we have disconnected from the RTP stream when stopping streaming.
    public GetMediaInfo getGetMediaInfo(String instanceID) {
        logger.entry();

        //Service: AVTransport
        //Actions: GetMediaInfo
        //POST:
        // InstanceID:
        //GET:
        // InstanceID:
        // NrTracks:
        // MediaDuration:
        // CurrentURI:
        // CurrentURIMetaData:
        // NextURI:
        // NextURIMetaData:
        // PlayMedium:
        // RecordMedium:
        // WriteStatus:

        Map<String, ActionArgumentValue> results = SERVICE_ACTIONS.runAction("GetMediaInfo",
                new ActionParameterPair[]{
                        new ActionParameterPair("InstanceID", instanceID)});

        if (results == null) {
            return logger.exit(null);
        }

        return logger.exit(new GetMediaInfo(results));
    }
}
