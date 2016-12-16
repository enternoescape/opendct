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
import opendct.config.Config;
import opendct.config.ExitCode;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.DeviceOptionType;
import opendct.nanohttpd.HttpUtil;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.nanohttpd.serializer.DeviceOptionSerializer;
import opendct.power.NetworkPowerEventManger;
import opendct.tuning.discovery.DeviceLoaderImpl;
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

    public static final String NAME = "name";
    public static final String OPTIONS = "options";
    public static final String NETWORKING = "Networking";

    public static final String POWER_MANAGEMENT = "Power Management";
    public static final String POWER_MANAGEMENT_ENABLED_PROP = "pm.enabled";
    public static final String POWER_MANAGEMENT_ENABLED_NAME = "Enabled";
    public static final String POWER_MANAGEMENT_ENABLED_DESC = "When this option is enabled," +
            " the program will be made aware of when the computer enters and exits standby." +
            " This option currently only applies to Windows. A program restart is required" +
            " for this setting to take effect.";

    public static final String CAPTURE_DEVICE_POOLING = "Capture Device Pooling";
    public static final String CAPTURE_DEVICE_POOLING_ENABLED_PROP = "pool.enabled";
    public static final String CAPTURE_DEVICE_POOLING_ENABLED_NAME = "Enabled";
    public static final String CAPTURE_DEVICE_POOLING_ENABLED_DESC = "When this option is" +
            " enabled, the program will allow capture devices to be selected based on merit" +
            " and availability instead of always using the capture device SageTV has" +
            " requested. A program restart is required for this setting to take effect.";


    public static final String SAGETV = "SageTV";
    public static final String SAGETV_EARLY_PORT_ASSIGNMENT_PROP = "sagetv.early_port_assignment";
    public static final String SAGETV_EARLY_PORT_ASSIGNMENT_NAME = "Early Port Assignment";
    public static final String SAGETV_EARLY_PORT_ASSIGNMENT_DESC = "When this option is" +
            " enabled, the program will open the defined port for every capture device that" +
            " might load so that SageTV can begin communication immediately. At startup, the" +
            " program will hold onto any requests for a capture device that is not currently" +
            " available until the requested capture device becomes available for up to 15" +
            " seconds before returning an error. The reason you might not want to enable this" +
            " is because it has the potential to open ports on the computer that might not be" +
            " used for anything if the corresponding capture device does not exist. A program" +
            " restart is required for this setting to take effect.";

    public static final String SAGETV_DISCOVERY_PORT_PROP = "sagetv.encoder_discovery_port";
    public static final String SAGETV_DISCOVERY_PORT_NAME = "Discovery Port";
    public static final String SAGETV_DISCOVERY_PORT_DESC = "This changes the port that the" +
            " program will listen on for the SageTV discovery broadcast. Do not change this" +
            " value unless you have changed the port on the SageTV side too. A program" +
            " restart is required for this setting to take effect.";

    public static final String SAGETV_AUTO_LOOPBACK_PROP = "sagetv.use_automatic_loopback";
    public static final String SAGETV_AUTO_LOOPBACK_NAME = "Automatic Loopback";
    public static final String SAGETV_AUTO_LOOPBACK_DESC = "When this is enabled, the program" +
            " will determine if it's running on the same computer as the SageTV server and" +
            " will automatically change to the loopback address for communications. A program" +
            " restart is required for this setting to take effect.";

    public static final String CAPTURE_DEVICE_LOADING = "Capture Device Loading";
    public static final String ALWAYS_ENABLE_PROP = "discovery.devices.exp_always_enable";
    public static final String ALWAYS_ENABLE_NAME = "Always Enable All Detected Capture Devices";
    public static final String ALWAYS_ENABLE_DESC = "Enable this option to always enable all" +
            " discovered capture devices. Disabling this option will display a new option that" +
            " allows you to select from the detected capture devices. Re-enabling this option" +
            " will not re-load any unloaded capture devices until OpenDCT is restarted.";


    public static final String RESTART_SERVICE_PROP = "restart_service";
    public static final String RESTART_PASSPHRASE = "true";

    public static class GetPost extends RouterNanoHTTPD.DefaultHandler {

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

            // Power Management Properties
            newObject = new JsonObject();
            newObject.addProperty(NAME, POWER_MANAGEMENT);
            JsonOption jsonOptions[] = new JsonOption[1];

            jsonOptions[0] = new JsonOption(
                    POWER_MANAGEMENT_ENABLED_PROP,
                    POWER_MANAGEMENT_ENABLED_NAME,
                    POWER_MANAGEMENT_ENABLED_DESC,
                    false,
                    DeviceOptionType.BOOLEAN,
                    new String[0],
                    Config.getString(POWER_MANAGEMENT_ENABLED_PROP));

            newObject.add(OPTIONS, gson.toJsonTree(jsonOptions));
            jsonArray.add(newObject);


            // Capture Device Pooling Properties
            newObject = new JsonObject();
            newObject.addProperty(NAME, CAPTURE_DEVICE_POOLING);
            jsonOptions = new JsonOption[1];

            jsonOptions[0] = new JsonOption(
                    CAPTURE_DEVICE_POOLING_ENABLED_PROP,
                    CAPTURE_DEVICE_POOLING_ENABLED_NAME,
                    CAPTURE_DEVICE_POOLING_ENABLED_DESC,
                    false,
                    DeviceOptionType.BOOLEAN,
                    new String[0],
                    Config.getString(CAPTURE_DEVICE_POOLING_ENABLED_PROP));

            newObject.add(OPTIONS, gson.toJsonTree(jsonOptions));
            jsonArray.add(newObject);

            // SageTV Server Related Properties
            newObject = new JsonObject();
            newObject.addProperty(NAME, SAGETV);
            jsonOptions = new JsonOption[3];

            jsonOptions[0] = new JsonOption(
                    SAGETV_EARLY_PORT_ASSIGNMENT_PROP,
                    SAGETV_EARLY_PORT_ASSIGNMENT_NAME,
                    SAGETV_EARLY_PORT_ASSIGNMENT_DESC,
                    false,
                    DeviceOptionType.BOOLEAN,
                    new String[0],
                    Config.getString(SAGETV_EARLY_PORT_ASSIGNMENT_PROP));

            jsonOptions[1] = new JsonOption(
                    SAGETV_DISCOVERY_PORT_PROP,
                    SAGETV_DISCOVERY_PORT_NAME,
                    SAGETV_DISCOVERY_PORT_DESC,
                    false,
                    DeviceOptionType.INTEGER,
                    new String[0],
                    Config.getString(SAGETV_EARLY_PORT_ASSIGNMENT_PROP));

            jsonOptions[2] = new JsonOption(
                    SAGETV_AUTO_LOOPBACK_PROP,
                    SAGETV_AUTO_LOOPBACK_NAME,
                    SAGETV_AUTO_LOOPBACK_DESC,
                    false,
                    DeviceOptionType.BOOLEAN,
                    new String[0],
                    Config.getString(SAGETV_AUTO_LOOPBACK_PROP));

            newObject.add(OPTIONS, gson.toJsonTree(jsonOptions));
            jsonArray.add(newObject);

            // Capture Device Loading Behavior
            newObject = new JsonObject();
            newObject.addProperty(NAME, CAPTURE_DEVICE_LOADING);
            jsonOptions = new JsonOption[1];

            jsonOptions[0] = new JsonOption(
                    ALWAYS_ENABLE_PROP,
                    ALWAYS_ENABLE_NAME,
                    ALWAYS_ENABLE_DESC,
                    false,
                    DeviceOptionType.BOOLEAN,
                    new String[0],
                    Config.getString(ALWAYS_ENABLE_PROP));

            newObject.add(OPTIONS, gson.toJsonTree(jsonOptions));
            jsonArray.add(newObject);

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

            // All of these options are not runtime configuration. If they are configured
            // incorrectly, they will be corrected on restart. This is also a filter to ensure you
            // can't change the properties for anything other than what you should be able to from
            // here.
            for (JsonOption jsonOption : jsonOptions) {
                switch (jsonOption.getProperty()) {
                    case POWER_MANAGEMENT_ENABLED_PROP:
                        Config.setString(POWER_MANAGEMENT_ENABLED_PROP, jsonOption.getValue());
                        break;
                    case CAPTURE_DEVICE_POOLING_ENABLED_PROP:
                        Config.setString(CAPTURE_DEVICE_POOLING_ENABLED_PROP, jsonOption.getValue());
                        break;
                    case SAGETV_EARLY_PORT_ASSIGNMENT_PROP:
                        Config.setString(SAGETV_EARLY_PORT_ASSIGNMENT_PROP, jsonOption.getValue());
                        break;
                    case SAGETV_DISCOVERY_PORT_PROP:
                        Config.setString(SAGETV_DISCOVERY_PORT_PROP, jsonOption.getValue());
                        break;
                    case SAGETV_AUTO_LOOPBACK_PROP:
                        Config.setString(SAGETV_AUTO_LOOPBACK_PROP, jsonOption.getValue());
                        break;
                    case RESTART_SERVICE_PROP:
                        // This is not intended to be secure so much as prevent accidents.
                        if (RESTART_PASSPHRASE.equals(jsonOption.getValue())) {
                            ExitCode.RESTART.terminateJVM();
                        } else {
                            logger.info("Restart passphrase is incorrect.");
                        }
                        break;
                    case ALWAYS_ENABLE_PROP:
                        if ("true".equalsIgnoreCase(jsonOption.getValue())) {
                            DeviceLoaderImpl.enableAlwaysEnable();
                        } else {
                            DeviceLoaderImpl.disableAlwaysEnable();
                        }
                }
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
