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
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import opendct.config.Config;
import opendct.consumer.DynamicConsumerImpl;
import opendct.consumer.SageTVConsumer;
import opendct.nanohttpd.serializer.DeviceOptionSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class ConsumerJsonServlet {
    private static final Logger logger = LogManager.getLogger(ConsumerJsonServlet.class);

    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gsonBuilder.registerTypeAdapter(SageTVConsumer.class, new DeviceOptionSerializer());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public static class List extends RouterNanoHTTPD.DefaultHandler {
        @Override
        public String getText() {
            return gson.toJson(Config.getSageTVConsumers());
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
            String consumers[] = urlParams.get("consumer").split("/");

            JsonArray jsonArray = new JsonArray();

            for (String consumer : consumers) {
                SageTVConsumer sageTVConsumer;
                if (consumer.endsWith(DynamicConsumerImpl.class.getSimpleName())) {
                    sageTVConsumer = new DynamicConsumerImpl();
                } else {
                    sageTVConsumer = Config.getSageTVConsumer(null, consumer, null);
                }

                jsonArray.add(gson.toJsonTree(sageTVConsumer, SageTVConsumer.class));
            }

            return super.get(uriResource, urlParams, session);
        }

        @Override
        public NanoHTTPD.Response post(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
            return super.post(uriResource, urlParams, session);
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
