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

import opendct.jetty.JettyManager;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVPoolManager;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("unloadeddevices")
public class RestUnloadedDevices {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUnloadedDevices() {
        // Unloaded devices are 100% static content and have only 3 properties to return per device,
        // so instead of providing a list and then querying each one, we just send everything
        // available.
        return Response.status(JettyManager.OK).entity(SageTVManager.getAllUnloadedDevicesSorted()).build();
    }
}
