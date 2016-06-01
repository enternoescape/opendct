/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.nanohttpd.pojo;

public class JsonChannelLineup {
    private String lineupName;
    private String source;
    private String friendlyName;
    private String address;
    private Long updateInterval = null;
    private Long nextUpdate = null;
    private Long offlineUpdateInterval = null;
    private Long nextOfflineUpdate = null;

    JsonChannel channels[];

    public String getLineupName() {
        return lineupName;
    }

    public void setLineupName(String lineupName) {
        this.lineupName = lineupName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Long getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(Long updateInterval) {
        this.updateInterval = updateInterval;
    }

    public Long getNextUpdate() {
        return nextUpdate;
    }

    public void setNextUpdate(Long nextUpdate) {
        this.nextUpdate = nextUpdate;
    }

    public Long getOfflineUpdateInterval() {
        return offlineUpdateInterval;
    }

    public void setOfflineUpdateInterval(Long offlineUpdateInterval) {
        this.offlineUpdateInterval = offlineUpdateInterval;
    }

    public Long getNextOfflineUpdate() {
        return nextOfflineUpdate;
    }

    public void setNextOfflineUpdate(long nextOfflineUpdate) {
        this.nextOfflineUpdate = nextOfflineUpdate;
    }
}
