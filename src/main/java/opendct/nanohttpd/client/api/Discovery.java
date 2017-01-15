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

package opendct.nanohttpd.client.api;

import com.google.gson.JsonElement;
import opendct.nanohttpd.client.Plugin;
import opendct.nanohttpd.client.ServerManager;
import opendct.nanohttpd.pojo.JsonDiscoverer;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.PojoUtil;

import java.util.ArrayList;
import java.util.List;

public class Discovery {
    public static final String DISCOVERY = "/discovery";

    public static final String DISCOVERY_ENABLED_PROP = "opendct/enabled_discovery";
    public static final String DISCOVERY_ENABLED_HELP = "Select the capture device discovery" +
            " methods to be enabled.";
    public static final String DISCOVERY_ENABLED_LABEL = "Select Enabled Discovery Methods";

    public static final String DISCOVERY_SELECT_NONE = "None";
    public static final String DISCOVERY_SELECT_PROP = "opendct/selected_discovery";
    public static final String DISCOVERY_SELECT_HELP = "Select a discovery method to view/modify" +
            " its properties below. Select '" + DISCOVERY_SELECT_NONE + "' to stop displaying" +
            " properties for any capture device.";
    public static final String DISCOVERY_SELECT_LABEL = "Show/Edit Capture Device Properties";

    private static String selectedDiscovery = null;
    private static JsonDiscoverer currentDiscoverer;
    private static OptionsHandler currentOptions;

    public static void newServer() {
        selectedDiscovery = null;
        refreshOptions();
    }

    private static void refreshOptions() {
        currentOptions = null;
        currentDiscoverer = null;
    }

    private static String[] getAllDiscovererNames(String server) {
        String returnValue[] = ServerManager.getInstance().getJson(server, String[].class, DISCOVERY);
        return returnValue != null ? returnValue : new String[0];
    }

    private static JsonDiscoverer[] getAllDiscoverers(String server) {
        String names[] = getAllDiscovererNames(server);
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            builder.append('/').append(ServerManager.encodeFile(name));
        }
        JsonDiscoverer discoverers[] = ServerManager.getInstance().getJson(server, JsonDiscoverer[].class, DISCOVERY + builder.toString());
        return discoverers != null ? discoverers : new JsonDiscoverer[0];
    }

    private static String[] getEnabledDiscoverers(String server) {
        String names[] = getAllDiscovererNames(server);
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            builder.append('/').append(ServerManager.encodeFile(name));
        }
        builder.append("?name=true&enabled=true");
        JsonDiscoverer discoverers[] = ServerManager.getInstance().getJson(server, JsonDiscoverer[].class, DISCOVERY + builder.toString());
        if (discoverers == null) {
            return new String[0];
        }
        List<String> returnValues = new ArrayList<>(discoverers.length);
        for (JsonDiscoverer discoverer : discoverers) {
            if (discoverer.isEnabled() == null || discoverer.isEnabled()) {
                returnValues.add(discoverer.getName());
            }
        }
        return returnValues.toArray(new String[returnValues.size()]);
    }

    private static void setEnabledDiscovererNames(String server, String discovererNames[]) {
        JsonDiscoverer discoverers[] = getAllDiscoverers(server);
        StringBuilder enable = new StringBuilder(DISCOVERY);
        StringBuilder disable = new StringBuilder(DISCOVERY);

        for (String discovererString : discovererNames) {
            if (discovererString == null) {
                continue;
            }

            for (int i = 0; i < discoverers.length; i++) {
                if (discovererNames[i] == null) {
                    continue;
                }
                if (discoverers[i].getName().equals(discovererString)) {
                    // Only enable discovery methods that actually show they are not currently
                    // enabled.
                    if (!discoverers[i].isEnabled()) {
                        enable.append('/').append(ServerManager.encodeFile(discovererString));
                    }
                    // When we find a discovery method is on the enabled list, make it null so
                    // that we can make sure everything that is not intended to be enabled gets
                    // disabled later.
                    discoverers[i] = null;
                    break;
                }
            }
        }
        for (JsonDiscoverer discoverer : discoverers) {
            if (discoverer != null && discoverer.isEnabled()) {
                disable.append('/').append(ServerManager.encodeFile(discoverer.getName()));
            }
        }
        // Disable first in the rare occurrence that loading these new discovery method creates a
        // big spike in memory usage that could cause problems.
        if (disable.length() > DISCOVERY.length()) {
            JsonElement element = PojoUtil.setOption("disableDevice", "true");
            ServerManager.getInstance().postJson(server, JsonException.class, enable.toString(), element);
        }
        if (enable.length() > DISCOVERY.length()) {
            JsonElement element = PojoUtil.setOption("enableDevice", "true");
            ServerManager.getInstance().postJson(server, JsonException.class, enable.toString(), element);
        }
    }

    private static JsonDiscoverer getDiscoverer(String server) {
        JsonDiscoverer cachedDiscoverer = currentDiscoverer;
        if (cachedDiscoverer == null && selectedDiscovery != null) {
            JsonDiscoverer discoverers[] = ServerManager.getInstance().getJson(server, JsonDiscoverer[].class, DISCOVERY + "/" + ServerManager.encodeFile(selectedDiscovery));
            if (discoverers != null && discoverers.length != 0) {
                cachedDiscoverer = discoverers[0];
                currentDiscoverer = cachedDiscoverer;
            }
        }
        return cachedDiscoverer;
    }

    private static OptionsHandler getOptions(String server, JsonDiscoverer discoverer) {
        OptionsHandler cachedOptions = currentOptions;
        if (cachedOptions == null && discoverer != null) {
            cachedOptions = new OptionsHandler(server, DISCOVERY, discoverer.getOptions());
            currentOptions = cachedOptions;
        }
        return cachedOptions;
    }

    public static String[] getConfigSettings(String server) {
        JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
        OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
        String options[];
        if (cachedOptions != null) {
            options = cachedOptions.getConfigSettings();
        } else  {
            return new String[] { DISCOVERY_ENABLED_PROP, DISCOVERY_SELECT_PROP };
        }
        String returnValue[] = new String[options.length + 2];
        returnValue[0] = DISCOVERY_ENABLED_PROP;
        returnValue[1] = DISCOVERY_SELECT_PROP;
        System.arraycopy(options, 0, returnValue, 2, options.length);
        return returnValue;
    }

    public static String getConfigValue(String server, String setting) {
        switch (setting) {
            case DISCOVERY_SELECT_PROP:
                String returnValue = selectedDiscovery;
                if (returnValue == null) {
                    return DISCOVERY_SELECT_NONE;
                } else {
                    return returnValue;
                }
            default:
                JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigValue(setting);
                }
        }
        return null;
    }

    public static String[] getConfigValues(String server, String setting) {
        switch (setting) {
            case DISCOVERY_ENABLED_PROP:
                return getEnabledDiscoverers(server);
            default:
                JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigValues(setting);
                }
        }
        return null;
    }

    public static int getConfigType(String server, String setting) {
        switch (setting) {
            case DISCOVERY_ENABLED_PROP:
                return Plugin.CONFIG_MULTICHOICE;
            case DISCOVERY_SELECT_PROP:
                return Plugin.CONFIG_CHOICE;
            default:
                JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigType(setting);
                }
        }
        return 0;
    }

    public static void setConfigValue(String server, String setting, String value) {
        switch (setting) {
            case DISCOVERY_SELECT_PROP:
                if (DISCOVERY_SELECT_NONE.equals(value)) {
                    selectedDiscovery = null;
                } else {
                    selectedDiscovery = value;
                }
                refreshOptions();
                break;
            default:
                JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
                if (cachedOptions != null) {
                    cachedOptions.setConfigValue(setting, value);
                    refreshOptions();
                }
        }
    }

    public static void setConfigValues(String server, String setting, String[] values) {
        switch (setting) {
            case DISCOVERY_ENABLED_PROP:
                Discovery.setEnabledDiscovererNames(server, values);
                break;
            default:
                JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
                if (cachedOptions != null) {
                    cachedOptions.setConfigValues(setting, values);
                    refreshOptions();
                }
        }
    }

    public static String[] getConfigOptions(String server, String setting) {
        switch (setting) {
            case DISCOVERY_ENABLED_PROP:
            case DISCOVERY_SELECT_PROP:
                return getAllDiscovererNames(server);
            default:
                JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigOptions(setting);
                }
        }
        return null;
    }

    public static String getConfigHelpText(String server, String setting) {
        switch (setting) {
            case DISCOVERY_ENABLED_PROP:
                return DISCOVERY_ENABLED_HELP;
            case DISCOVERY_SELECT_PROP:
                return DISCOVERY_SELECT_HELP;
            default:
                JsonDiscoverer cachedDiscoverer = getDiscoverer(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDiscoverer);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigHelpText(setting);
                }
        }
        return null;
    }

    public static String getConfigLabel(String server, String setting) {
        switch (setting) {
            case DISCOVERY_ENABLED_PROP:
                return DISCOVERY_ENABLED_LABEL;
            case DISCOVERY_SELECT_PROP:
                return DISCOVERY_SELECT_LABEL;
            default:
                OptionsHandler cachedOptions = getOptions(server, getDiscoverer(server));
                if (cachedOptions != null) {
                    return cachedOptions.getConfigLabel(setting);
                }
        }
        return null;
    }
}
