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

package opendct.jetty.json;

import opendct.channel.ChannelLineup;
import opendct.channel.ChannelManager;
import opendct.channel.ChannelSourceType;
import opendct.jetty.JettyManager;

import javax.ws.rs.core.Response;

public class JsonChannelLineupDetails {
    String lineupName;
    String friendlyName;
    ChannelSourceType source;
    String address;
    long updateInterval;
    long offlineUpdateInterval;
    long nextUpdate;
    long nextOfflineUpdate;
    boolean hasChannels;

    public Response setChannelLineupDetails(String channelLineupName) {
        ChannelLineup channelLineup = ChannelManager.getChannelLineup(channelLineupName);

        if (channelLineup == null) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.NOT_FOUND,
                            "The channel lineup requested does not exist.",
                            channelLineupName,
                            ""
                    )
            ).build();
        }

        try {
            lineupName = channelLineup.LINEUP_NAME;
            friendlyName = channelLineup.getFriendlyName();
            source = channelLineup.SOURCE;
            address = channelLineup.getAddress();
            updateInterval = channelLineup.getUpdateInterval();
            offlineUpdateInterval = channelLineup.getOfflineUpdateInterval();
            nextUpdate = channelLineup.getNextUpdate();
            nextOfflineUpdate = channelLineup.getNextOfflineUpdate();
            hasChannels = channelLineup.hasChannels();
        } catch (Exception e) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.ERROR,
                            "An unexpected error happened while getting details about this lineup.",
                            channelLineupName,
                            e.toString()
                    )
            ).build();
        }

        return Response.status(JettyManager.OK).entity(this).build();
    }

    public String getLineupName() {
        return lineupName;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public ChannelSourceType getSource() {
        return source;
    }

    public String getAddress() {
        return address;
    }

    public long getUpdateInterval() {
        return updateInterval;
    }

    public long getOfflineUpdateInterval() {
        return offlineUpdateInterval;
    }

    public long getNextUpdate() {
        return nextUpdate;
    }

    public long getNextOfflineUpdate() {
        return nextOfflineUpdate;
    }

    public boolean isHasChannels() {
        return hasChannels;
    }
}
