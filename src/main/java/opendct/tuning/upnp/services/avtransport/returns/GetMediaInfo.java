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

package opendct.tuning.upnp.services.avtransport.returns;

import opendct.tuning.upnp.services.shared.ActionProcessing;
import org.fourthline.cling.model.action.ActionArgumentValue;

import java.util.Map;

public class GetMediaInfo {
    private final String nrTracks;
    private final String mediaDuration;
    private final String currentURI;
    private final String currentURIMetaData;
    private final String nextURI;
    private final String nextURIMetaData;
    private final String playMedium;
    private final String recordMedium;
    private final String writeStatus;

    public GetMediaInfo(String nrTracks, String mediaDuration, String currentURI, String currentURIMetaData, String nextURI, String nextURIMetaData, String playMedium, String recordMedium, String writeStatus) {
        this.nrTracks = nrTracks;
        this.mediaDuration = mediaDuration;
        this.currentURI = currentURI;
        this.currentURIMetaData = currentURIMetaData;
        this.nextURI = nextURI;
        this.nextURIMetaData = nextURIMetaData;
        this.playMedium = playMedium;
        this.recordMedium = recordMedium;
        this.writeStatus = writeStatus;
    }

    public GetMediaInfo(Map<String, ActionArgumentValue> runAction) {
        nrTracks = ActionProcessing.getActionVariableValue(runAction, "NrTracks");
        mediaDuration = ActionProcessing.getActionVariableValue(runAction, "MediaDuration");
        currentURI = ActionProcessing.getActionVariableValue(runAction, "CurrentURI");
        currentURIMetaData = ActionProcessing.getActionVariableValue(runAction, "CurrentURIMetaData");
        nextURI = ActionProcessing.getActionVariableValue(runAction, "NextURI");
        nextURIMetaData = ActionProcessing.getActionVariableValue(runAction, "NextURIMetaData");
        playMedium = ActionProcessing.getActionVariableValue(runAction, "PlayMedium");
        recordMedium = ActionProcessing.getActionVariableValue(runAction, "RecordMedium");
        writeStatus = ActionProcessing.getActionVariableValue(runAction, "WriteStatus");
    }

    public String getNrTracks() {
        return nrTracks;
    }

    public String getMediaDuration() {
        return mediaDuration;
    }

    public String getCurrentURI() {
        return currentURI;
    }

    public String getCurrentURIMetaData() {
        return currentURIMetaData;
    }

    public String getNextURI() {
        return nextURI;
    }

    public String getNextURIMetaData() {
        return nextURIMetaData;
    }

    public String getPlayMedium() {
        return playMedium;
    }

    public String getRecordMedium() {
        return recordMedium;
    }

    public String getWriteStatus() {
        return writeStatus;
    }
}
