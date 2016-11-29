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

import opendct.nanohttpd.client.ServerManager;
import opendct.nanohttpd.pojo.JsonGeneral;
import opendct.nanohttpd.pojo.JsonOption;

import java.util.ArrayList;
import java.util.List;

public class General {

    public static final String GENERAL = "/general";

    private static final Object serverOptions = new Object();
    private static String currentServer;
    private static OptionsHandler currentOptions;

    private static void refreshOptions() {
        synchronized (serverOptions) {
            currentOptions = null;
        }
    }

    private static OptionsHandler getOptions(String server) {
        OptionsHandler cachedOptions;
        synchronized (serverOptions) {
            cachedOptions = currentOptions;
            if (server.equalsIgnoreCase(General.currentServer) && cachedOptions != null) {
                return cachedOptions;
            }
        }

        JsonGeneral localGeneralOptions[] = ServerManager.getInstance().getJson(server, JsonGeneral[].class, GENERAL);
        if (localGeneralOptions == null) {
            return null;
        }

        List<JsonOption> localOptions = new ArrayList<>();
        for (JsonGeneral localOption : localGeneralOptions) {
            // The options actually come in organized sections, but the Standard plugin interface
            // only allows for a flat configuration without headers, so we are ignoring all of
            // the section headers.
            for (JsonOption option : localOption.getOptions()) {
                localOptions.add(option);
            }
        }
        JsonOption newOptions[] = localOptions.toArray(new JsonOption[localOptions.size()]);
        cachedOptions = new OptionsHandler(server, GENERAL, newOptions);
        synchronized (serverOptions) {
            currentServer = server;
            currentOptions = cachedOptions;
        }
        return cachedOptions;
    }

    public static String[] getConfigSettings(String server) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            return cachedOptions.getConfigSettings();
        }
        return new String[0];
    }

    public static String getConfigValue(String server, String setting) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            return cachedOptions.getConfigValue(setting);
        }
        return null;
    }

    public static String[] getConfigValues(String server, String setting) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            return cachedOptions.getConfigValues(setting);
        }
        return null;
    }

    public static int getConfigType(String server, String setting) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            return cachedOptions.getConfigType(setting);
        }
        return 0;
    }

    public static void setConfigValue(String server, String setting, String value) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            cachedOptions.setConfigValue(setting, value);
            refreshOptions();
        }
    }

    public static void setConfigValues(String server, String setting, String[] values) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            cachedOptions.setConfigValues(setting, values);
            refreshOptions();
        }
    }

    public static String[] getConfigOptions(String server, String setting) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            return cachedOptions.getConfigOptions(setting);
        }
        return null;
    }

    public static String getConfigHelpText(String server, String setting) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            return cachedOptions.getConfigHelpText(setting);
        }
        return null;
    }

    public static String getConfigLabel(String server, String setting) {
        OptionsHandler cachedOptions = getOptions(server);
        if (cachedOptions != null) {
            return cachedOptions.getConfigLabel(setting);
        }
        return null;
    }
}
