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
import opendct.capture.CaptureDevice;
import opendct.channel.ChannelLineup;
import opendct.channel.ChannelManager;
import opendct.nanohttpd.HttpUtil;
import opendct.nanohttpd.pojo.JsonChannelLineup;
import opendct.nanohttpd.serializer.ChannelLineupSerializer;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class ChannelLinupsJsonServlet {
    private static final Logger logger = LogManager.getLogger(ChannelLinupsJsonServlet.class);

    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gsonBuilder.registerTypeAdapter(ChannelLineup.class, new ChannelLineupSerializer());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public static class List extends RouterNanoHTTPD.DefaultHandler {
        @Override
        public String getText() {
            java.util.List<ChannelLineup> lineups = ChannelManager.getChannelLineups();
            String returnValues[] = new String[lineups.size()];


            for (int i = 0; i < returnValues.length; i++) {
                returnValues[i] = lineups.get(i).LINEUP_NAME;
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

    public static class GetPostPutDelete extends RouterNanoHTTPD.DefaultHandler {
        @Override
        public String getText() {
            return "error";
        }

        @Override
        public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String channelLineup = urlParams.get("channel_lineup");

            String channelLineups[] = channelLineup.split("/");

            JsonArray jsonArray = new JsonArray();

            for (String channelLineupLookup : channelLineups) {
                ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineupLookup);

                if (lineup == null) {
                    return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' does not exist.");
                }

                if (session.getParms().size() == 0) {
                    jsonArray.add(gson.toJsonTree(lineup, ChannelLineup.class));
                } else {
                    JsonObject newObject = new JsonObject();

                    ChannelLineupSerializer.addProperty(newObject, ChannelLineupSerializer.LINEUP_NAME, lineup);

                    for (Map.Entry<String, String> kvp : session.getParms().entrySet()) {
                        ChannelLineupSerializer.addProperty(newObject, kvp.getKey(), lineup);
                    }

                    jsonArray.add(newObject);
                }
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson(jsonArray));
        }

        @Override
        public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String channelLineup = urlParams.get("channel_lineup");

            String channelLineups[] = channelLineup.split("/");

            String response = HttpUtil.getPostContent(session);

            JsonChannelLineup jsonChannelLineup = gson.fromJson(response, JsonChannelLineup.class);

            for (String channelLineupLookup : channelLineups) {
                ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineupLookup);

                if (lineup == null) {
                    return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' does not exist.");
                }

                ChannelLineupSerializer.setProperties(jsonChannelLineup, lineup);
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson("OK"));
        }

        @Override
        public NanoHTTPD.Response put(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String channelLineup = urlParams.get("channel_lineup");

            String channelLineups[] = channelLineup.split("/");

            String response = HttpUtil.getPostContent(session);

            JsonChannelLineup jsonChannelLineup = gson.fromJson(response, JsonChannelLineup.class);

            for (String channelLineupLookup : channelLineups) {
                ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineupLookup);

                if (lineup != null) {
                    return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' already exists.");
                }

                try {
                    lineup = ChannelLineupSerializer.createLineup(jsonChannelLineup);
                } catch (NullPointerException e) {
                    return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' could not be created because a required parameter was null.");
                } catch (IllegalArgumentException e) {
                    return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' could not be created because " + e.getMessage());
                }

                // If somehow more than more creation request make it in at the same time, the last
                // one will always win.
                ChannelManager.addChannelLineup(lineup, true);
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson("OK"));
        }

        @Override
        public NanoHTTPD.Response delete(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String channelLineup = urlParams.get("channel_lineup");

            String channelLineups[] = channelLineup.split("/");

            for (String channelLineupLookup : channelLineups) {
                ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineupLookup);

                if (lineup == null) {
                    return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' does not exist.");
                }

                java.util.List<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();

                StringBuilder responseBuilder = null;
                for (CaptureDevice captureDevice : captureDevices) {
                    if (captureDevice.getChannelLineup().equals(channelLineupLookup)) {
                        if (responseBuilder == null) {
                            responseBuilder = new StringBuilder(captureDevice.getEncoderName());
                        } else {
                            responseBuilder.append(", ").append(captureDevice.getEncoderName());
                        }
                    }
                }

                if (responseBuilder != null) {
                    return HttpUtil.returnException(channelLineupLookup, "The lineup '" +
                            channelLineupLookup + "' is in use by the capture devices '" +
                            responseBuilder.toString() + "' and cannot be deleted.");
                }

                ChannelManager.removeChannelLineup(channelLineupLookup, true);
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
