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
import opendct.jetty.json.*;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.ArrayList;

@Path("capturedevice")
public class RestCaptureDevice {
    private static final Logger logger = LogManager.getLogger(RestCaptureDevice.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCaptureDevices() {
        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();

        String captureDeviceNames[] = new String[captureDevices.size()];

        for (int i = 0; i < captureDeviceNames.length; i++) {
            captureDeviceNames[i] = captureDevices.get(i).getEncoderName();
        }

        return Response.status(200).entity(captureDeviceNames).build();
    }

    @GET
    @Path("{name}/details")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCaptureDeviceDetails(@PathParam("name") String deviceName) {
        JsonCaptureDeviceDetails deviceDetails = new JsonCaptureDeviceDetails();

        return deviceDetails.setCaptureDeviceDetails(deviceName);
    }

    @GET
    @Path("{name}/advdetails")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCaptureDeviceAdvDetails(@PathParam("name") String deviceName) {
        JsonCaptureDeviceAdvancedDetails deviceDetails = new JsonCaptureDeviceAdvancedDetails();

        return deviceDetails.setCaptureDeviceDetails(deviceName);
    }

    @GET
    @Path("{name}/detail/{method}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCaptureDeviceDetail(@PathParam("name") String deviceName, @PathParam("method") String methodName) {
        CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(deviceName, false);

        if (captureDevice == null) {
            JsonError jsonError = new JsonError(
                    JettyManager.NOT_FOUND,
                    "The capture device requested does not exist.",
                    deviceName,
                    ""
            );

            logger.error("{}", jsonError);
            return Response.status(JettyManager.NOT_FOUND).entity(jsonError).build();
        }

        try {
            Method method = captureDevice.getClass().getMethod(methodName);
            return Response.status(JettyManager.OK).entity(method.invoke(captureDevice)).build();
        } catch (Exception e) {
            JsonError jsonError = new JsonError(
                    JettyManager.ERROR,
                    "An unexpected error happened while getting a detail about this capture device.",
                    deviceName,
                    e.toString()
            );

            logger.error("{}", jsonError, e);

            return Response.status(JettyManager.NOT_FOUND).entity(jsonError).build();
        }


    }

    @POST
    @Path("{name}/details")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putCaptureDeviceDetails(@PathParam("name") String deviceName, JsonCaptureDeviceSet deviceSet) {
        CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(deviceName, false);

        if (captureDevice == null) {
            JsonError jsonError = new JsonError(
                    JettyManager.NOT_FOUND,
                    "The capture device requested does not exist.",
                    deviceName,
                    ""
            );

            logger.error("{}", jsonError);
            return Response.status(JettyManager.NOT_FOUND).entity(jsonError).build();
        }

        JsonCaptureDeviceSet setDeviceDetails = new JsonCaptureDeviceSet(captureDevice);

        return Response.status(JettyManager.OK).build();
    }
}
