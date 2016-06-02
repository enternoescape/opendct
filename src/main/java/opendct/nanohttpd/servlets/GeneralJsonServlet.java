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
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.nanohttpd.HttpUtil;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.nanohttpd.serializer.DeviceOptionSerializer;
import opendct.power.NetworkPowerEventManger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class GeneralJsonServlet {
    private static final Logger logger = LogManager.getLogger(GeneralJsonServlet.class);

    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gsonBuilder.registerTypeAdapter(DeviceOption.class, new DeviceOptionSerializer());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public static class GetPost extends RouterNanoHTTPD.DefaultHandler {
        public static final String NAME = "name";
        public static final String OPTIONS = "options";
        public static final String NETWORKING = "Networking";

        @Override
        public String getText() {
            return "error";
        }

        @Override
        public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            JsonArray jsonArray = new JsonArray();

            JsonObject newObject = new JsonObject();

            newObject.addProperty(NAME, NETWORKING);
            newObject.add(OPTIONS,
                    gson.toJsonTree(
                            NetworkPowerEventManger.POWER_EVENT_LISTENER.getOptions(),
                            DeviceOption.class));

            jsonArray.add(newObject);
            /*newObject = new JsonObject();

            newObject.addProperty(NAME, "Networking");
            newObject.add(OPTIONS,
                    gson.toJsonTree(
                            NetworkPowerEventManger.POWER_EVENT_LISTENER.getOptions(),
                            DeviceOption.class));*/


            return NanoHTTPD.newFixedLengthResponse(gson.toJson(jsonArray));
        }

        @Override
        public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {

            String response = HttpUtil.getPostContent(session);

            JsonOption jsonOptions[];
            if (response.startsWith("[")) {
                jsonOptions = gson.fromJson(response, JsonOption[].class);
            } else {
                jsonOptions = new JsonOption[] { gson.fromJson(response, JsonOption.class) };
            }

            // Each option has a unique property, so we can apply them to every possible manager and
            // it will only be applied if it's relevant.
            try {
                NetworkPowerEventManger.POWER_EVENT_LISTENER.setOptions(jsonOptions);
            } catch (DeviceOptionException e) {
                return HttpUtil.returnException(e);
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson("OK"));
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
