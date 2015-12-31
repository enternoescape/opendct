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

package opendct.jetty.rest;

import opendct.channel.ChannelLineup;
import opendct.channel.ChannelManager;
import opendct.jetty.JettyManager;
import opendct.jetty.json.JsonChannelLineupDetails;
import opendct.jetty.json.JsonError;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVPoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;

@Path("lineup")
public class RestLineup {
    private static final Logger logger = LogManager.getLogger(RestLineup.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUnloadedDevices() {
        ArrayList<ChannelLineup> lineups = ChannelManager.getChannelLineups();
        ArrayList<String> channelLineupNames = new ArrayList<>();

        for (ChannelLineup lineup : lineups) {
            channelLineupNames.add(lineup.LINEUP_NAME);
        }

        Collections.sort(channelLineupNames);

        return Response.status(JettyManager.OK).entity(channelLineupNames).build();
    }

    @GET
    @Path("{name}/details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLineupDetails(@PathParam("name") String channelLineupName) {
        JsonChannelLineupDetails lineupDetails = new JsonChannelLineupDetails();

        return lineupDetails.setChannelLineupDetails(channelLineupName);
    }
}
