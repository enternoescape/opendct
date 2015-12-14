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

public class GetProtocolInfo {
    private final String source;
    private final String sink;

    public GetProtocolInfo(String source, String sink) {
        this.source = source;
        this.sink = sink;
    }

    public GetProtocolInfo(Map<String, ActionArgumentValue> runAction) {
        source = ActionProcessing.getReturnedActionValue(runAction, "Source", "rtsp-rtp-udp:*:dri-mp2t:*");
        sink = ActionProcessing.getActionVariableValue(runAction, "Sink");
    }

    public String getSource() {
        return source;
    }

    public String getSink() {
        return sink;
    }

}
