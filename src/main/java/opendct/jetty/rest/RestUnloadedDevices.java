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

import opendct.capture.CaptureDevice;
import opendct.jetty.JettyManager;
import opendct.jetty.json.JsonError;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVUnloadedDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("unloadeddevices")
public class RestUnloadedDevices {
    private static final Logger logger = LogManager.getLogger(RestUnloadedDevices.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUnloadedDevices() {
        // Unloaded devices are 100% static content and have only 3 properties to return per device,
        // so instead of providing a list and then querying each one, we just send everything
        // available.
        return Response.status(JettyManager.OK).entity(SageTVManager.getAllUnloadedDevicesSorted()).build();
    }

    @GET
    @Path("{name}/load")
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadCaptureDevice(@PathParam("name") String unloadedDeviceName) {
        try {
            CaptureDevice captureDevice = SageTVManager.addCaptureDevice(unloadedDeviceName);

            if (captureDevice == null) {
                return Response.status(JettyManager.NOT_FOUND).build();
            }

            return Response.status(JettyManager.OK).entity(captureDevice.getEncoderName()).build();
        } catch (Exception e) {
            logger.error("Loading the unloaded capture device '{}' created an unexpected exception => ", unloadedDeviceName, e);
            return Response.status(JettyManager.ERROR).build();
        }
    }

    @GET
    @Path("{name}/unload")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unloadCaptureDevice(@PathParam("name") String loadedDeviceName) {
        try {
            SageTVUnloadedDevice unloadedDevice = SageTVManager.removeCaptureDevice(loadedDeviceName);

            if (unloadedDevice == null) {
                JsonError jsonError = new JsonError(
                        JettyManager.NOT_FOUND,
                        "The loaded capture device requested to be unloaded does not exist.",
                        loadedDeviceName,
                        ""
                );

                logger.error("{}", jsonError);
                return Response.status(JettyManager.NOT_FOUND).entity(jsonError).build();
            }

            return Response.status(JettyManager.OK).entity(unloadedDevice).build();
        } catch (Exception e) {
            JsonError jsonError = new JsonError(
                    JettyManager.ERROR,
                    "An unexpected error happened while unloading this capture device.",
                    loadedDeviceName,
                    e.toString()
            );

            logger.error("{}", jsonError, e);

            return Response.status(JettyManager.ERROR).entity(jsonError).build();
        }
    }
}
