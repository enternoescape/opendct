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
import opendct.jetty.json.JsonCaptureDeviceParentDetails;
import opendct.jetty.json.JsonCaptureDeviceParentSet;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("capturedeviceparent")
public class RestCaptureDeviceParent {
    private static final Logger logger = LogManager.getLogger(RestCaptureDeviceParent.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDeviceParents() {
        return Response.status(JettyManager.OK).entity(SageTVManager.getAllCaptureDeviceParentsSorted()).build();
    }

    @GET
    @Path("{name}/details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCaptureDeviceDetails(@PathParam("name") String deviceParentName) {
        JsonCaptureDeviceParentDetails deviceDetails = new JsonCaptureDeviceParentDetails();

        return deviceDetails.setCaptureDeviceParentDetails(deviceParentName);
    }

    @POST
    @Path("{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putCaptureDeviceDetails(@PathParam("name") String deviceParentName, JsonCaptureDeviceParentSet parentSet) {
        parentSet.applyUpdates(deviceParentName);

        return Response.status(JettyManager.OK).build();
    }
}
