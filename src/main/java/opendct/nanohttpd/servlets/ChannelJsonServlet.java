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
import opendct.channel.TVChannel;
import opendct.channel.TVChannelImpl;
import opendct.nanohttpd.HttpUtil;
import opendct.nanohttpd.pojo.JsonChannel;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.serializer.ChannelLineupSerializer;
import opendct.nanohttpd.serializer.ChannelSerializer;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Map;

public class ChannelJsonServlet {
    private static final Logger logger = LogManager.getLogger(ChannelJsonServlet.class);

    private static final List list = new List();
    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gsonBuilder.registerTypeAdapter(TVChannelImpl.class, new ChannelSerializer());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public static class List extends RouterNanoHTTPD.DefaultHandler {
        @Override
        public String getText() {
            return "error";
        }

        @Override
        public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String channelLineup = urlParams.get("channel_lineup");

            ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineup);

            if (lineup == null) {
                return HttpUtil.returnException(channelLineup, "The lineup '" + channelLineup + "' does not exist.");
            }

            ArrayList<TVChannel> channels = lineup.getAllChannels(true, true);
            String returnValues[] = new String[channels.size()];

            for (int i = 0; i < returnValues.length; i++) {
                returnValues[i] = channels.get(i).getChannel();
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson(returnValues));
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
            String channelLineupLookup = urlParams.get("channel_lineup");
            String channels = urlParams.get("channel");

            ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineupLookup);

            if (lineup == null) {
                return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' does not exist.");
            }

            String channelLookups[];
            if (session.getParms().get("all") == null) {
                channelLookups = channels.split("/");
            } else {
                ArrayList<TVChannel> allChannels = lineup.getAllChannels(true, true);
                channelLookups = new String[allChannels.size()];

                for (int i = 0; i < channelLookups.length; i++) {
                    channelLookups[i] = allChannels.get(i).getChannel();
                }

                session.getParms().remove("all");
            }

            JsonArray jsonArray = new JsonArray();

            for (String channelLookup : channelLookups) {

                TVChannel tvChannel = lineup.getOriginalChannel(channelLookup);

                if (tvChannel == null) {
                    return HttpUtil.returnException(channelLookup, "The channel '" + channelLookup + "' does not exist.");
                }

                if (session.getParms().size() == 0) {
                    jsonArray.add(gson.toJsonTree(tvChannel, TVChannelImpl.class));
                } else {
                    JsonObject newObject = new JsonObject();

                    ChannelLineupSerializer.addProperty(newObject, ChannelSerializer.CHANNEL, lineup);

                    for (Map.Entry<String, String> kvp : session.getParms().entrySet()) {
                        ChannelSerializer.addProperty(newObject, kvp.getKey(), tvChannel);
                    }

                    jsonArray.add(newObject);
                }
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson(jsonArray));
        }

        @Override
        public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String channelLineupLookup = urlParams.get("channel_lineup");
            String channels = urlParams.get("channel");

            ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineupLookup);

            if (lineup == null) {
                return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' does not exist.");
            }

            String channelLookups[] = channels.split("/");

            for (String channelLookup : channelLookups) {

                TVChannel tvChannel = lineup.getOriginalChannel(channelLookup);

                if (tvChannel == null) {
                    return HttpUtil.returnException(channelLookup, "The channel '" + channelLookup + "' does not exist.");
                }

                JsonChannel jsonChannel = gson.fromJson(HttpUtil.getPostContent(session), JsonChannel.class);

                JsonException jsonException = ChannelSerializer.setProperty(jsonChannel, lineup);

                if (jsonException !=  null) {
                    return HttpUtil.returnException(jsonException);
                }
            }

            return NanoHTTPD.newFixedLengthResponse(gson.toJson("OK"));
        }

        @Override
        public NanoHTTPD.Response put(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            String channelLineupLookup = urlParams.get("channel_lineup");
            String channels = urlParams.get("channel");

            ChannelLineup lineup = ChannelManager.getChannelLineup(channelLineupLookup);

            if (lineup == null) {
                return HttpUtil.returnException(channelLineupLookup, "The lineup '" + channelLineupLookup + "' does not exist.");
            }

            String channelLookups[] = channels.split("/");

            for (String channelLookup : channelLookups) {

                TVChannel tvChannel = lineup.getOriginalChannel(channelLookup);

                if (tvChannel != null) {
                    return HttpUtil.returnException(channelLookup, "The channel '" + channelLookup + "' already exists.");
                }

                JsonChannel jsonChannel = gson.fromJson(HttpUtil.getPostContent(session), JsonChannel.class);

                JsonException jsonException = ChannelSerializer.createChannel(jsonChannel, lineup);

                if (jsonException !=  null) {
                    return HttpUtil.returnException(jsonException);
                }
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

                ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();

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
