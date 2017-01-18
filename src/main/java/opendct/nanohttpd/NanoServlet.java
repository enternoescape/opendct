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

package opendct.nanohttpd;

import fi.iki.elonen.router.RouterNanoHTTPD;
import opendct.nanohttpd.servlets.*;

public class NanoServlet extends RouterNanoHTTPD {

    public NanoServlet(int port) {
        super(port);
        addMappings();
    }

    @Override
    public void addMappings() {
        super.addMappings();

        // GET: Landing page.
        addRoute("/", RootServlet.Get.class);
        addRoute("/index.html", RootServlet.Get.class);

        // GET: Get program version, host OS and path information.
        addRoute("/version", VersionJsonServlet.List.class);

        // GET: Get Capture Device ID's
        addRoute("/devices", CaptureDevicesJsonServlet.List.class);
        // GET: Get Capture Device Properties
        // POST: Set Capture Device Properties
        addRoute("/devices/:capture_device", CaptureDevicesJsonServlet.GetPost.class);

        // GET: Get Channel Lineup ID's
        addRoute("/lineups", ChannelLinupsJsonServlet.List.class);
        // GET: Get Channel Lineup Properties
        // POST: Set Channel Lineup Properties
        // PUT: Create a new Channel Lineup
        // DELETE: Remove an existing Channel Lineup
        addRoute("/lineups/:channel_lineup", ChannelLinupsJsonServlet.GetPostPutDelete.class);

        // GET: Get Channel ID's for a Channel Lineup ID
        addRoute("/channels/list/:channel_lineup", ChannelJsonServlet.List.class);
        // GET: Get Channel by ID for a Channel Lineup ID, all?all=true will list all channels in lineup.
        // POST: Update Channel by ID for a Channel Lineup ID
        // PUT: Create a new Channel by ID for a Channel Lineup ID
        // DELETE: Remove an existing Channel by ID for a Channel Lineup ID
        addRoute("/channels/:channel_lineup/:channel", ChannelJsonServlet.GetPostPutDelete.class);

        // GET: Get the names of all available consumers
        addRoute("/consumers", ConsumerJsonServlet.List.class);
        // GET: Get the properties for a consumer or multiple consumers
        // POST: Set the properties for a consumer or multiple consumers
        addRoute("/consumers/:consumer", ConsumerJsonServlet.GetPost.class);

        // GET: Get the properties for all general options
        // POST: Set the properties for general options
        addRoute("/general", GeneralJsonServlet.GetPost.class);

        // GET: Get the names of all available capture device discoverers
        addRoute("/discovery", DiscovererJsonServlet.List.class);
        // GET: Get the properties for a discoverer or multiple discoverers
        // POST: Set the properties for a discoverer or multiple discoverers
        addRoute("/discovery/:discoverer", DiscovererJsonServlet.GetPost.class);
    }
}
