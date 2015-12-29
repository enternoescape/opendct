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
import opendct.jetty.json.JsonError;
import opendct.sagetv.SageTVPoolManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("pool")
public class RestPool {
    private static final Logger logger = LogManager.getLogger(RestPool.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPools() {
        // Just returns if we are using pools or not. Pools are created automatically when a capture
        // device declares that it is using a pool of a specific name and they are not actually
        // maintained like normal objects since they only exist by name.
        return Response.status(JettyManager.OK).entity(SageTVPoolManager.isUsePools()).build();
    }

    @GET
    @Path("{name}/tovirtualdevice")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getPoolDeviceToVirtualDevice(@PathParam("name") String deviceName) {
        String virtualDeviceName = SageTVPoolManager.getPoolCaptureDeviceToVCaptureDevice(deviceName);

        if (virtualDeviceName == null) {
            JsonError jsonError = new JsonError(
                    JettyManager.NOT_FOUND,
                    "The virtual capture device mapping requested does not exist.",
                    deviceName,
                    ""
            );

            logger.error("{}", jsonError);
            return Response.status(JettyManager.NOT_FOUND).entity(jsonError).build();
        }

        return Response.status(JettyManager.OK).entity(virtualDeviceName).build();
    }

    @GET
    @Path("{name}/topooldevice")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getVirtualDeviceToPoolDevice(@PathParam("name") String deviceName) {
        String poolDeviceName = SageTVPoolManager.getVCaptureDeviceToPoolCaptureDevice(deviceName);

        if (poolDeviceName == null) {
            JsonError jsonError = new JsonError(
                    JettyManager.NOT_FOUND,
                    "The pool capture device mapping requested does not exist.",
                    deviceName,
                    ""
            );

            logger.error("{}", jsonError);
            return Response.status(JettyManager.NOT_FOUND).entity(jsonError).build();
        }

        return Response.status(JettyManager.OK).entity(poolDeviceName).build();
    }
}
