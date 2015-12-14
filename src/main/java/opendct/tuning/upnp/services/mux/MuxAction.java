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

package opendct.tuning.upnp.services.mux;

import opendct.tuning.upnp.services.mux.returns.SetProgram;
import opendct.tuning.upnp.services.shared.ActionParameterPair;
import opendct.tuning.upnp.services.shared.ServiceActions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.meta.Service;

import java.util.Map;

public class MuxAction {
    private final Logger logger = LogManager.getLogger(MuxAction.class);
    public final ServiceActions SERVICE_ACTIONS;
    private UpnpService upnpService;
    private Service queryService;

    public MuxAction(UpnpService upnpService, Service queryService) {
        this.upnpService = upnpService;
        this.queryService = queryService;
        SERVICE_ACTIONS = new ServiceActions(upnpService, queryService);
    }

    // This method returns the currently selected program number.
    // Returns null if there is a problem.
    private String getCurrentProgramNumber() {
        return SERVICE_ACTIONS.queryActionVariable("ProgramNumber");
    }

    // This method returns the currently selected PIDs.
    // Returns null if there is a problem.
    private String getCurrentPIDList() {
        return SERVICE_ACTIONS.queryActionVariable("PIDList");
    }

    // This method selects a program from the currently tuned frequency.
    // This is the second step when tuning without a channel number.
    public SetProgram setSetProgram(String newProgram) {
        logger.entry();

        //Service: Mux
        //Action: SetProgram
        //POST:
        // NewProgram
        //GET:
        // null

        Map<String, ActionArgumentValue> results;
        results = SERVICE_ACTIONS.runAction("SetProgram",
                new ActionParameterPair[]{
                        new ActionParameterPair("NewProgram", newProgram)});
        if (results == null) {
            return logger.exit(null);
        }

        return logger.exit(new SetProgram(results));
    }

    // This method selects the pids to be streamed from the tuner.
    public void setAddPid(String addPid) {
        logger.entry();

        //Service: Mux
        //Action: SetProgram
        //POST:
        // NewProgram
        //GET:
        // null

        Map<String, ActionArgumentValue> results;
        results = SERVICE_ACTIONS.runAction("AddPid",
                new ActionParameterPair[]{
                        new ActionParameterPair("AddPidList", addPid)});

        logger.exit();
        return;
    }
}
