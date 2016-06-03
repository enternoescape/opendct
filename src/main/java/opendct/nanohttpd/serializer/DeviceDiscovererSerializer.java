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

package opendct.nanohttpd.serializer;

import com.google.gson.*;
import opendct.config.OSVersion;
import opendct.config.options.DeviceOption;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.tuning.discovery.DeviceDiscoverer;
import opendct.tuning.discovery.DiscoveryException;
import opendct.tuning.discovery.DiscoveryManager;

import java.lang.reflect.Type;

public class DeviceDiscovererSerializer implements JsonSerializer<DeviceDiscoverer> {
    public static final DeviceOptionSerializer deviceOptionSerializer = new DeviceOptionSerializer();

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String ENABLED = "enabled";
    public static final String RUNNING = "running";
    public static final String SUPPORTED_OS = "supportedOS";
    public static final String OPTIONS = "options";

    @Override
    public JsonElement serialize(DeviceDiscoverer src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty(NAME, src.getName());
        jsonObject.addProperty(DESCRIPTION, src.getDescription());
        jsonObject.addProperty(ERROR_MESSAGE, src.getErrorMessage());
        jsonObject.addProperty(ENABLED, src.isEnabled());
        jsonObject.addProperty(RUNNING, src.isRunning());

        JsonArray jsonArray = new JsonArray();
        for (OSVersion os : src.getSupportedOS()) {
            jsonArray.add(os.toString());
        }
        jsonObject.add(SUPPORTED_OS, jsonArray);

        jsonObject.add(OPTIONS, deviceOptionSerializer.serialize(src.getOptions(), DeviceOption.class, context));

        return jsonObject;
    }

    public static void addProperty(JsonObject object, String property, DeviceDiscoverer discoverer) {
        switch (property) {
            case NAME:
                object.addProperty(NAME, discoverer.getName());
                break;
            case DESCRIPTION:
                object.addProperty(DESCRIPTION, discoverer.getDescription());
                break;
            case ERROR_MESSAGE:
                object.addProperty(ERROR_MESSAGE, discoverer.getErrorMessage());
                break;
            case ENABLED:
                object.addProperty(ENABLED, discoverer.isEnabled());
                break;
            case RUNNING:
                object.addProperty(RUNNING, discoverer.isRunning());
                break;
            case SUPPORTED_OS:
                JsonArray jsonArray = new JsonArray();
                for (OSVersion os : discoverer.getSupportedOS()) {
                    jsonArray.add(os.toString());
                }
                object.add(SUPPORTED_OS, jsonArray);
                break;
            case OPTIONS:
                object.add(OPTIONS, deviceOptionSerializer.serialize(discoverer.getOptions(), DeviceOption.class, null));
                break;
        }
    }

    public static synchronized JsonException setProperties(JsonOption options, DeviceDiscoverer discoverer) {

        switch (options.getProperty()) {
            case ENABLED:
                boolean enabled = options.getValue().equals("true");

                if (discoverer.isEnabled() && !enabled) {
                    discoverer.setEnabled(false);
                    try {
                        discoverer.stopDetection();
                    } catch (DiscoveryException e) {
                        return new JsonException(discoverer.getName(), "Unable to stop discoverer: " + e.getMessage());
                    }
                } else if (!discoverer.isEnabled() && enabled) {
                    discoverer.setEnabled(true);
                    try {
                        discoverer.startDetection(DiscoveryManager.DEVICE_LOADER);
                    } catch (DiscoveryException e) {
                        return new JsonException(discoverer.getName(), "Unable to start discoverer: " + e.getMessage());
                    }
                }
        }

        return null;
    }
}
