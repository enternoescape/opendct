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

package opendct.nanohttpd.servlets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD.DefaultHandler;
import fi.iki.elonen.router.RouterNanoHTTPD.UriResource;
import opendct.capture.CaptureDevice;
import opendct.config.options.DeviceOptionException;
import opendct.nanohttpd.HttpUtil;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.nanohttpd.serializer.CaptureDevicesSerializer;
import opendct.nanohttpd.serializer.DeviceOptionSerializer;
import opendct.sagetv.SageTVManager;
import opendct.tuning.discovery.DiscoveredDevice;
import opendct.tuning.discovery.DiscoveryManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class CaptureDevicesJsonServlet {
    private static final Logger logger = LogManager.getLogger(CaptureDevicesJsonServlet.class);

    private static final DeviceOptionSerializer deviceOptionSerializer = new DeviceOptionSerializer();
    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gsonBuilder.registerTypeAdapter(DiscoveredDevice.class, new CaptureDevicesSerializer());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public static class List extends DefaultHandler {
        @Override
        public String getText() {
            DiscoveredDevice devices[] = DiscoveryManager.getDiscoveredDevices();
            int returnValues[] = new int[devices.length];


            for (int i = 0; i < devices.length; i++) {
                returnValues[i] = devices[i].getId();
            }

            return gson.toJson(returnValues);
        }

        @Override
        public String getMimeType() {
            return "application/json";
        }

        @Override
        public NanoHTTPD.Response.IStatus getStatus() {
            return NanoHTTPD.Response.Status.OK;
        }
    }

    public static class GetPost extends DefaultHandler {

        @Override
        public String getText() {
            return "error";
        }

        @Override
        public NanoHTTPD.Response get(UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String captureDevice = urlParams.get("capture_device");

            if (captureDevice == null) {
                return HttpUtil.returnException("", "No capture device was requested.");
            }

            String captureDeviceLookups[] = captureDevice.split("/");

            if (session.getParms().size() > 0) {
                JsonArray devices = new JsonArray();

                for (String captureDeviceLookup : captureDeviceLookups) {
                    DiscoveredDevice device;

                    try {
                        device = DiscoveryManager.getDiscoveredDevice(Integer.parseInt(captureDeviceLookup));
                    } catch (NumberFormatException e) {
                        return HttpUtil.returnException("","Capture device id '" + captureDeviceLookup + "' is not a valid id.");
                    }

                    if (device == null) {
                        return HttpUtil.returnException("","Capture device id '" + captureDeviceLookup + "' does not exist.");
                    }

                    CaptureDevice loadedCaptureDevice = SageTVManager.getSageTVCaptureDevice(device.getId());

                    JsonObject newEntry = new JsonObject();

                    newEntry.addProperty(CaptureDevicesSerializer.ID, device.getId());

                    for (Map.Entry<String, String> kvp : session.getParms().entrySet()) {
                        String getProperty = kvp.getKey();

                        CaptureDevicesSerializer.addProperty(newEntry, getProperty, device, loadedCaptureDevice);
                    }

                    devices.add(newEntry);
                }

                return NanoHTTPD.newFixedLengthResponse(gson.toJson(devices));
            }

            DiscoveredDevice devices[] = new DiscoveredDevice[captureDeviceLookups.length];

            for (int i = 0; i < devices.length; i++) {
                devices[i] = DiscoveryManager.getDiscoveredDevice(Integer.parseInt(captureDeviceLookups[i]));
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson(devices, DiscoveredDevice.class));
        }

        @Override
        public NanoHTTPD.Response post(UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String captureDevice = urlParams.get("capture_device");

            if (captureDevice == null) {
                return HttpUtil.returnException("", "No capture device was requested.");
            }

            String captureDeviceLookups[] = captureDevice.split("/");

            String response = HttpUtil.getPostContent(session);

            JsonOption jsonOptions[];
            if (response.startsWith("[")) {
                jsonOptions = gson.fromJson(response, JsonOption[].class);
            } else {
                jsonOptions = new JsonOption[] { gson.fromJson(response, JsonOption.class) };
            }

            for (String captureDeviceLookup : captureDeviceLookups) {
                DiscoveredDevice device;

                try {
                    device = DiscoveryManager.getDiscoveredDevice(Integer.parseInt(captureDeviceLookup));
                } catch (NumberFormatException e) {
                    return HttpUtil.returnException("","Capture device id '" + captureDeviceLookup + "' is not a valid id.");
                }

                if (device == null) {
                    return HttpUtil.returnException("","Capture device id '" + captureDeviceLookup + "' does not exist.");
                }

                CaptureDevice loadedCaptureDevice = SageTVManager.getSageTVCaptureDevice(device.getId());

                try {
                    device.setOptions(jsonOptions);
                } catch (DeviceOptionException e) {
                    return HttpUtil.returnException(e);
                }

                if (loadedCaptureDevice != null) {
                    for (JsonOption jsonOption : jsonOptions) {
                        JsonException jsonException =
                                CaptureDevicesSerializer.setProperty(jsonOption, loadedCaptureDevice);

                        if (jsonException != null) {
                            return HttpUtil.returnException(jsonException);
                        }
                    }
                }
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson("OK"));
        }

        @Override
        public NanoHTTPD.Response.IStatus getStatus() {
            return NanoHTTPD.Response.Status.OK;
        }

        @Override
        public String getMimeType() {
            return "application/json";
        }
    }
}
