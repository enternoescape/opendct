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

package opendct.nanohttpd.client;

import com.google.gson.JsonElement;
import opendct.nanohttpd.client.api.Devices;
import opendct.nanohttpd.client.api.Discovery;
import opendct.nanohttpd.client.api.General;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.PojoUtil;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This is a functional example of how a Standard SageTV plugin could be designed. This can be used
 * as the actual plugin by just extending this class, implementing the SageTVPlugin interface and
 * adding a constructor that accepts sage.SageTVPluginRegistry as a parameter.
 *
 * Ex. public class RealPlugin extends opendct.nanohttpd.client.Plugin implements sage.SageTVPlugin
 */
public class Plugin {
    public static final int DISCOVERY_PORT = 8271;

    private static final String SELECTED_SERVER_REDETECT = "Re-Detect Servers";
    private static final String SELECTED_SERVER_PROP = "opendct/selected_server";
    private static final String SELECTED_SERVER_HELP = "Change the server you are currently" +
            " configuring. If the server you are looking for is not present, try selecting '" +
            SELECTED_SERVER_REDETECT + "' to attempt to discover more OpenDCT servers.";
    private static final String SELECTED_SERVER_LABEL = "Selected Server";

    private static final String START_SERVICE_PROP = "opendct/start_service";
    private static final String START_SERVICE_HELP = "Start the OpenDCT service on this server." +
            " This will also enable the service if it is not already enabled to start on reboot." +
            " (This button is not available if the service is running on a remote server)";
    private static final String START_SERVICE_LABEL = "Start OpenDCT Service";
    private static final String START_SERVICE_VALUE = "Start";

    private static final String STOP_SERVICE_PROP = "opendct/stop_service";
    private static final String STOP_SERVICE_HELP = "Stop the OpenDCT service on this server." +
            " This will not disable the services from running again after a reboot." +
            " (This button is not available if the service is running on a remote server)";
    private static final String STOP_SERVICE_LABEL = "Stop OpenDCT Service";
    private static final String STOP_SERVICE_VALUE = "Stop";

    private static final String RESTART_SERVICE_PROP = "opendct/restart_service";
    private static final String RESTART_SERVICE_HELP = "Restart the OpenDCT service on the" +
            " selected server.";
    private static final String RESTART_SERVICE_LABEL = "Restart OpenDCT Service";
    private static final String RESTART_SERVICE_VALUE = "Restart";

    public static final String ENABLED_DEVICES_PROP = "opendct/enabled_devices";
    public static final String ENABLED_DEVICES_HELP = "Select what capture devices are enabled" +
            " for use in SageTV. Newly enabled capture devices may require SageTV to restart" +
            " before they will be available for use. This is currently a limitation in SageTV.";
    public static final String ENABLED_DEVICES_LABEL = "Enabled Capture Devices";

    private String selectedServer;
    private int discoveryPort;

    public Plugin() {
        this(null);
    }

    public Plugin(String selectedServer) {
        this(selectedServer, DISCOVERY_PORT);
    }

    public Plugin(String selectedServer, int discoveryPort) {
        this.selectedServer = selectedServer;
        this.discoveryPort = discoveryPort;
    }

    // This method is called when the plugin should startup
    public void start() {
        ServerManager.getInstance().discoverServers(discoveryPort);
        if (selectedServer == null || selectedServer.length() == 0) {
            autoSelectServer();
        }
    }

    // This method is called when the plugin should shutdown
    public void stop() {
        // This plugin uses entirely restful APIs, so there is nothing to stop.
    }

    // This method is called after plugin shutdown to free any resources used by the plugin
    public void destroy() {
        // This plugin uses entirely restful APIs, so there is nothing to stop.
    }

    // These methods are used to define any configuration settings for the plugin that should be
    // presented in the UI. If your plugin does not need configuration settings; you may simply return null or zero from these methods.

    // Returns the names of the settings for this plugin
    public String[] getConfigSettings() {
        List<String> settings = new ArrayList<>();
        settings.add(SELECTED_SERVER_PROP);
        ServerProperties server = ServerManager.getInstance().getServer(selectedServer);
        if (server != null) {
            if (server.isLocal()) {
                int serviceStatus = Util.opendctServiceStatus();
                if (serviceStatus == Util.SERVICE_RUNNING) {
                    settings.add(STOP_SERVICE_PROP);
                } else if (serviceStatus == Util.SERVICE_STOPPED) {
                    settings.add(START_SERVICE_PROP);
                }
            }
            settings.add(RESTART_SERVICE_PROP);
        } else {
            return new String[] { SELECTED_SERVER_PROP };
        }

        String serverOptions[] = settings.toArray(new String[settings.size()]);
        String generalOptions[] = General.getConfigSettings(selectedServer);
        String discoveryOptions[] = Discovery.getConfigSettings(selectedServer);
        String deviceOptions[] = Devices.getConfigSettings(selectedServer);

        for (String generalOption : generalOptions) {
            // Display a multi-choice option to select what capture devices are enabled if always
            // enable is disabled.
            if ("discovery.devices.exp_always_enable".equals(generalOption)) {
                if ("false".equalsIgnoreCase(General.getConfigValue(selectedServer, generalOption))) {
                    settings.add(ENABLED_DEVICES_PROP);
                }
                break;
            }
        }

        String returnValue[] = new String[serverOptions.length + generalOptions.length + discoveryOptions.length + deviceOptions.length];
        System.arraycopy(serverOptions, 0, returnValue, 0, serverOptions.length);
        System.arraycopy(generalOptions, 0, returnValue, serverOptions.length, generalOptions.length);
        System.arraycopy(discoveryOptions, 0, returnValue, serverOptions.length + generalOptions.length, discoveryOptions.length);
        System.arraycopy(deviceOptions, 0, returnValue, serverOptions.length + generalOptions.length + discoveryOptions.length, deviceOptions.length);
        return returnValue;
    }

    // Returns the current value of the specified setting for this plugin
    public String getConfigValue(String setting) {
        switch (setting) {
            case SELECTED_SERVER_PROP:
                return selectedServer;
            case START_SERVICE_PROP:
                return START_SERVICE_VALUE;
            case STOP_SERVICE_PROP:
                return STOP_SERVICE_VALUE;
            case RESTART_SERVICE_PROP:
                return RESTART_SERVICE_VALUE;
            default:
                String returnValue;
                if ((returnValue = General.getConfigValue(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Discovery.getConfigValue(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Devices.getConfigValue(selectedServer, setting)) != null) {
                    return returnValue;
                }
        }
        return null;
    }

    // Returns the current value of the specified multichoice setting for this plugin
    public String[] getConfigValues(String setting) {
        switch (setting) {
            case ENABLED_DEVICES_PROP:
                return Devices.getEnabledCaptureDeviceNames(selectedServer);
            default:
                String returnValue[];
                if ((returnValue = General.getConfigValues(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Discovery.getConfigValues(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Devices.getConfigValues(selectedServer, setting)) != null) {
                    return returnValue;
                }
        }
        return null;
    }

    // Constants for different types of configuration values
    public static final int CONFIG_BOOL = 1;
    public static final int CONFIG_INTEGER = 2;
    public static final int CONFIG_TEXT = 3;
    public static final int CONFIG_CHOICE = 4;
    public static final int CONFIG_MULTICHOICE = 5;
    public static final int CONFIG_FILE = 6;
    public static final int CONFIG_DIRECTORY = 7;
    public static final int CONFIG_BUTTON = 8;
    public static final int CONFIG_PASSWORD = 9;

    // Returns one of the constants above that indicates what type of value is used for a specific settings
    public int getConfigType(String setting) {
        switch (setting) {
            case SELECTED_SERVER_PROP:
                return CONFIG_CHOICE;
            case START_SERVICE_PROP:
                return CONFIG_BUTTON;
            case STOP_SERVICE_PROP:
                return CONFIG_BUTTON;
            case RESTART_SERVICE_PROP:
                return CONFIG_BUTTON;
            case ENABLED_DEVICES_PROP:
                return CONFIG_MULTICHOICE;
            default:
                int type;
                if ((type = General.getConfigType(selectedServer, setting)) != 0) {
                    return type;
                } else if ((type = Discovery.getConfigType(selectedServer, setting)) != 0) {
                    return type;
                } else if ((type = Devices.getConfigType(selectedServer, setting)) != 0) {
                    return type;
                }
                return 0;
        }
    }

    // Sets a configuration value for this plugin
    public void setConfigValue(String setting, String value) {
        switch (setting) {
            case SELECTED_SERVER_PROP:
                if (value.equalsIgnoreCase(SELECTED_SERVER_REDETECT)) {
                    ServerManager.getInstance().discoverServers(discoveryPort);
                    autoSelectServer();
                } else if (ServerManager.getInstance().getServer(value) != null) {
                    selectedServer = value;
                }
                break;
            case START_SERVICE_PROP:
                Util.startOpendctService();
                break;
            case STOP_SERVICE_PROP:
                Util.stopOpendctService();
                break;
            case RESTART_SERVICE_PROP:
                JsonElement element = PojoUtil.setOption("restart_service", "true");
                ServerManager.getInstance().postJson(selectedServer, JsonException.class, General.GENERAL, element);
                break;
            default:
                if (General.getConfigValue(selectedServer, setting) != null) {
                    General.setConfigValue(selectedServer, setting, value);
                } else if (Discovery.getConfigValue(selectedServer, setting) != null) {
                    Discovery.setConfigValue(selectedServer, setting, value);
                } else if (Devices.getConfigValue(selectedServer, setting) != null) {
                    Devices.setConfigValue(selectedServer, setting, value);
                }
        }
    }

    // Sets a configuration values for this plugin for a multiselect choice
    public void setConfigValues(String setting, String[] values) {
        switch (setting) {
            case ENABLED_DEVICES_PROP:
                // This will disable anything that is not on the values list.
                Devices.setEnableCaptureDeviceNames(selectedServer, values);
            default:
                if (General.getConfigValues(selectedServer, setting) != null) {
                    General.setConfigValues(selectedServer, setting, values);
                } else if (Discovery.getConfigValues(selectedServer, setting) != null) {
                    Discovery.setConfigValues(selectedServer, setting, values);
                } else if (Devices.getConfigValues(selectedServer, setting) != null) {
                    Devices.setConfigValues(selectedServer, setting, values);
                }
        }
    }

    // For CONFIG_CHOICE settings; this returns the list of choices
    public String[] getConfigOptions(String setting) {
        switch (setting) {
            case SELECTED_SERVER_PROP:
                String servers[] = ServerManager.getInstance().getServers();
                servers = Arrays.copyOf(servers, servers.length + 1);
                // At the very least a server hostname will not have a space in it, so this is
                // completely safe to provide in the list with the actual server options.
                servers[servers.length - 1] = SELECTED_SERVER_REDETECT;

                return ServerManager.getInstance().getServers();
            case ENABLED_DEVICES_PROP:
                return Devices.getAllCaptureDeviceNames(selectedServer);
            default:
                String returnValue[];
                if ((returnValue = General.getConfigOptions(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Discovery.getConfigOptions(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Devices.getConfigOptions(selectedServer, setting)) != null) {
                    return returnValue;
                }
                return null;
        }
    }

    // Returns the help text for a configuration setting
    public String getConfigHelpText(String setting) {
        switch (setting) {
            case SELECTED_SERVER_PROP:
                return SELECTED_SERVER_HELP;
            case START_SERVICE_PROP:
                return START_SERVICE_HELP;
            case STOP_SERVICE_PROP:
                return STOP_SERVICE_HELP;
            case RESTART_SERVICE_PROP:
                return RESTART_SERVICE_HELP;
            case ENABLED_DEVICES_PROP:
                return ENABLED_DEVICES_HELP;
            default:
                String returnValue;
                if ((returnValue = General.getConfigHelpText(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Discovery.getConfigHelpText(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Devices.getConfigHelpText(selectedServer, setting)) != null) {
                    return returnValue;
                }
                return null;
        }
    }

    // Returns the label used to present this setting to the user
    public String getConfigLabel(String setting) {
        switch (setting) {
            case SELECTED_SERVER_PROP:
                return SELECTED_SERVER_LABEL;
            case START_SERVICE_PROP:
                return START_SERVICE_LABEL;
            case STOP_SERVICE_PROP:
                return STOP_SERVICE_LABEL;
            case RESTART_SERVICE_PROP:
                return RESTART_SERVICE_LABEL;
            case ENABLED_DEVICES_PROP:
                return ENABLED_DEVICES_LABEL;
            default:
                String returnValue;
                if ((returnValue = General.getConfigLabel(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Discovery.getConfigLabel(selectedServer, setting)) != null) {
                    return returnValue;
                } else if ((returnValue = Devices.getConfigLabel(selectedServer, setting)) != null) {
                    return returnValue;
                }
                return null;
        }
    }

    // Resets the configuration of this plugin
    public void resetConfig() {
        autoSelectServer();
        General.newServer();
        Discovery.newServer();
        Devices.newServer();
    }

    private void autoSelectServer() {
        autoSelectServer(false);
    }

    private void autoSelectServer(boolean foundLocalServer) {
        String[] serverNames = ServerManager.getInstance().getServers();
        String lastServerName = null;

        for (String serverName : serverNames) {
            ServerProperties server = ServerManager.getInstance().getServer(serverName);
            if (server != null) {
                lastServerName = serverName;

                // Prefer the first IP address that is on the same machine. Otherwise we will select
                // whatever comes last on the list.
                if (server.isLocal()) {
                    foundLocalServer = true;
                    break;
                }
            }
        }

        // Add the local host address too since it wasn't detected possibly because the service is
        // not running.
        if (!foundLocalServer) {
            try {
                String returnValue = ServerManager.getInstance().addServer(InetAddress.getLocalHost().getHostAddress(), 9091);
                // If no servers were found, try to use the newly added local address.
                if (returnValue == null && lastServerName == null) {
                    // Indicate that the local server was found to prevent a potentially endless loop.
                    autoSelectServer(true);
                    return;
                } else if (returnValue != null) {
                    System.out.println("OpenDCT - ERROR: " + returnValue);
                }
            } catch (Exception e) {}
        }

        selectedServer = lastServerName;
    }

    /**
     * Returns the currently selected server or <code>null</code> if one does not exist.
     */
    public ServerProperties getSelectedServer() {
        return ServerManager.getInstance().getServer(selectedServer);
    }
}
