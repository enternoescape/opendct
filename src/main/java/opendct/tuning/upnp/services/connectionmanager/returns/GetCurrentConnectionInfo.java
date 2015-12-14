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

package opendct.tuning.upnp.services.connectionmanager.returns;

import opendct.tuning.upnp.services.shared.ActionProcessing;
import org.fourthline.cling.model.action.ActionArgumentValue;

import java.util.Map;

public class GetCurrentConnectionInfo {
    private final String rcsID;
    private final String avTransportID;
    private final String protocolInfo;
    private final String peerConnectionManager;
    private final String peerConnectionID;
    private final String direction;
    private final String status;

    public GetCurrentConnectionInfo(String rcsID, String avTransportID, String protocolInfo, String peerConnectionManager, String peerConnectionID, String direction, String status) {
        this.rcsID = rcsID;
        this.avTransportID = avTransportID;
        this.protocolInfo = protocolInfo;
        this.peerConnectionManager = peerConnectionManager;
        this.peerConnectionID = peerConnectionID;
        this.direction = direction;
        this.status = status;
    }

    public GetCurrentConnectionInfo(Map<String, ActionArgumentValue> runAction) {
        rcsID = ActionProcessing.getActionVariableValue(runAction, "RcsID");
        avTransportID = ActionProcessing.getActionVariableValue(runAction, "AVTransportID");
        protocolInfo = ActionProcessing.getActionVariableValue(runAction, "ProtocolInfo");
        peerConnectionManager = ActionProcessing.getActionVariableValue(runAction, "PeerConnectionManager");
        peerConnectionID = ActionProcessing.getActionVariableValue(runAction, "PeerConnectionID");
        direction = ActionProcessing.getActionVariableValue(runAction, "Direction");
        status = ActionProcessing.getActionVariableValue(runAction, "Status");
    }

    public String getRcsID() {
        return rcsID;
    }

    public String getAVTransportID() {
        return avTransportID;
    }

    public String getProtocolInfo() {
        return protocolInfo;
    }

    public String getPeerConnectionManager() {
        return peerConnectionManager;
    }

    public String getPeerConnectionID() {
        return peerConnectionID;
    }

    public String getDirection() {
        return direction;
    }

    public String getStatus() {
        return status;
    }

}
