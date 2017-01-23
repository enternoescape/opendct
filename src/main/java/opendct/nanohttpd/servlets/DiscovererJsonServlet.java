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
import fi.iki.elonen.router.RouterNanoHTTPD;
import opendct.config.options.DeviceOptionException;
import opendct.nanohttpd.HttpUtil;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.nanohttpd.serializer.DeviceDiscovererSerializer;
import opendct.tuning.discovery.DeviceDiscoverer;
import opendct.tuning.discovery.DiscoveryManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import static opendct.nanohttpd.HttpUtil.JSON_OK;

public class DiscovererJsonServlet {
    private static final Logger logger = LogManager.getLogger(DiscovererJsonServlet.class);

    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gsonBuilder.registerTypeAdapter(DeviceDiscoverer.class, new DeviceDiscovererSerializer());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public static class List extends RouterNanoHTTPD.DefaultHandler {
        @Override
        public String getText() {
            DeviceDiscoverer discoverers[] = DiscoveryManager.getDiscoverers();
            String returnValues[] = new String[discoverers.length];

            for (int i = 0; i < discoverers.length; i++) {
                returnValues[i] = discoverers[i].getName();
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

    public static class GetPost extends RouterNanoHTTPD.DefaultHandler {

        @Override
        public String getText() {
            return "error";
        }

        @Override
        public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String discovererLookup = urlParams.get("discoverer");

            if (discovererLookup == null) {
                return HttpUtil.returnException("", "No discoverer was requested.");
            }

            String discovererLookups[] = discovererLookup.split("/");
            JsonArray jsonArray = new JsonArray();

            for (String discoveredLookup : discovererLookups) {
                DeviceDiscoverer discoverer = DiscoveryManager.getDiscoverer(discoveredLookup);

                if (discoverer == null) {
                    return HttpUtil.returnException(discoveredLookup, "The discoverer '" + discoveredLookup + "' does not exist.");
                }

                if (session.getParms().size() > 0) {
                    JsonObject newObject = new JsonObject();
                    newObject.addProperty(DeviceDiscovererSerializer.NAME, discoverer.getName());

                    for (Map.Entry<String, String> kvp : session.getParms().entrySet()) {
                        DeviceDiscovererSerializer.addProperty(newObject, kvp.getKey(), discoverer);
                    }

                    jsonArray.add(newObject);
                } else {
                    jsonArray.add(gson.toJsonTree(discoverer, DeviceDiscoverer.class));
                }
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson(jsonArray));
        }

        @Override
        public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String discovererLookup = urlParams.get("discoverer");

            if (discovererLookup == null) {
                return HttpUtil.returnException("", "No discoverer was requested.");
            }

            String discovererLookups[] = discovererLookup.split("/");

            String response = HttpUtil.getPostContent(session);

            JsonOption jsonOptions[];
            if (response.startsWith("[")) {
                jsonOptions = gson.fromJson(response, JsonOption[].class);
            } else {
                jsonOptions = new JsonOption[] { gson.fromJson(response, JsonOption.class) };
            }

            for (String discoveredLookup : discovererLookups) {
                DeviceDiscoverer discoverer = DiscoveryManager.getDiscoverer(discoveredLookup);

                if (discoverer == null) {
                    return HttpUtil.returnException(discoveredLookup, "The discoverer '" + discoveredLookup + "' does not exist.");
                }

                try {
                    discoverer.setOptions(jsonOptions);
                } catch (DeviceOptionException e) {
                    return HttpUtil.returnException(e);
                }

                for (JsonOption jsonOption : jsonOptions) {
                    JsonException jsonException = DeviceDiscovererSerializer.setProperties(jsonOption, discoverer);

                    if (jsonException != null) {
                        return HttpUtil.returnException(jsonException);
                    }
                }
            }

            return NanoHTTPD.newFixedLengthResponse(JSON_OK);
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
}
