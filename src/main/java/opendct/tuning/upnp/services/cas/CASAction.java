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

package opendct.tuning.upnp.services.cas;

import opendct.tuning.upnp.services.cas.returns.GetCardStatus;
import opendct.tuning.upnp.services.cas.returns.SetChannel;
import opendct.tuning.upnp.services.shared.ActionParameterPair;
import opendct.tuning.upnp.services.shared.ServiceActions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.meta.Service;

import java.util.Map;

public class CASAction {
    private final Logger logger = LogManager.getLogger(CASAction.class);
    public ServiceActions serviceActions;
    private UpnpService upnpService;
    private Service queryService;

    public CASAction(UpnpService upnpService, Service queryService) {
        this.upnpService = upnpService;
        this.queryService = queryService;
        serviceActions = new ServiceActions(upnpService, queryService);
    }

    // This method sets the channel number with the most likely defaults.
    public SetChannel setSetChannel(String newChannelNumber) {
        return setSetChannel(newChannelNumber, "0", "Live");
    }

    // This method sets the channel number.
    // The alternative is to tune the frequency, then program.
    public SetChannel setSetChannel(String newChannelNumber, String newSourceId, String newCaptureMode) {
        logger.entry();

        //Service: CAS
        //Action: SetChannel
        //POST:
        // NewChannelNumber
        // NewSourceId
        // NewCaptureMode
        //GET:
        // PCRLockStatus

        Map<String, ActionArgumentValue> results = serviceActions.runAction("SetChannel",
                new ActionParameterPair[]{
                        new ActionParameterPair("NewChannelNumber", newChannelNumber),
                        new ActionParameterPair("NewSourceId", newSourceId),
                        new ActionParameterPair("NewCaptureMode", newCaptureMode)});
        if (results == null) {
            return logger.exit(null);
        }

        return logger.exit(new SetChannel(results));
    }

    public GetCardStatus getGetCardStatus() {
        logger.entry();

        //Service: CAS
        //Action: GetCardStatus
        //POST:
        // null
        //GET:
        // CurrentCardStatus
        // CurrentCardManufacturer
        // CurrentCardVersion
        // CurrentDaylightSaving
        // CurrentEALocationCode
        // CurrentRatingRegion
        // CurrentTimeZone

        Map<String, ActionArgumentValue> results = serviceActions.runAction("GetCardStatus", new ActionParameterPair[0]);
        if (results == null) {
            return logger.exit(null);
        }

        return logger.exit(new GetCardStatus(results));
    }
}
