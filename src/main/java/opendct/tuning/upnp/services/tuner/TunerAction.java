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

package opendct.tuning.upnp.services.tuner;

import opendct.tuning.upnp.services.shared.ActionParameterPair;
import opendct.tuning.upnp.services.shared.ServiceActions;
import opendct.tuning.upnp.services.tuner.returns.SetTunerParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.meta.Service;

import java.util.Map;

public class TunerAction {
    private final Logger logger = LogManager.getLogger(TunerAction.class);
    public final ServiceActions SERVICE_ACTIONS;
    private UpnpService upnpService;
    private Service queryService;

    public TunerAction(UpnpService upnpService, Service queryService) {
        this.upnpService = upnpService;
        this.queryService = queryService;
        SERVICE_ACTIONS = new ServiceActions(upnpService, queryService);
    }

    //This method tunes the specified frequency and modulation.
    //This is the first step when tuning without a channel number.
    public SetTunerParameters runSetTunerParameters(String newFrequency, String newModulationList) {
        logger.entry();

        //Service: Tuner
        //Action: SetTunerParameters
        //POST:
        // NewFrequency
        // NewModulationList
        //GET:
        // CurrentFrequency
        // CurrentModulation
        // PCRLockStatus

        Map<String, ActionArgumentValue> results = SERVICE_ACTIONS.runAction("SetTunerParameters",
                new ActionParameterPair[]{
                        new ActionParameterPair("NewFrequency", newFrequency),
                        new ActionParameterPair("NewModulationList", newModulationList)});
        if (results == null) {
            return logger.exit(null);
        }

        return logger.exit(new SetTunerParameters(results));
    }
}
