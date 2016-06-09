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
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.nanohttpd.serializer.ConsumerSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VersionJsonServlet {
    private static final Logger logger = LogManager.getLogger(VersionJsonServlet.class);
    // Increment this value whenever any breaking changes are made. Do not increment if a feature was added, but all previous functility is still working.
    public final static int JSON_VERSION = 1;

    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gsonBuilder.registerTypeAdapter(SageTVConsumer.class, new ConsumerSerializer());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public static class List extends RouterNanoHTTPD.DefaultHandler {
        @Override
        public String getText() {
            JsonObject newObject = new JsonObject();

            newObject.addProperty("versionProgram", Config.VERSION_PROGRAM);
            newObject.addProperty("versionMajor", Config.VERSION_MAJOR);
            newObject.addProperty("versionMinor", Config.VERSION_MINOR);
            newObject.addProperty("versionBuild", Config.VERSION_BUILD);
            newObject.addProperty("versionConfig", Config.VERSION_CONFIG);
            newObject.addProperty("versionOS", Config.OS_VERSION.toString());
            newObject.addProperty("is64Bit", Config.IS_64BIT);
            newObject.addProperty("configDir", Config.getConfigDir());
            newObject.addProperty("logDir", Config.getLogDir());
            newObject.addProperty("versionJson", JSON_VERSION);

            return gson.toJson(newObject);
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
