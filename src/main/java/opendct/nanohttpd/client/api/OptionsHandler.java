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
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.nanohttpd.pojo.PojoUtil;

public class OptionsHandler {
    private final String server;
    private final JsonOption cachedOptions[];
    private final String path;

    public OptionsHandler(String server, String path, JsonOption[] options) {
        this.server = server;
        this.cachedOptions = options;
        this.path = path;
    }

    private JsonOption getOption(String setting) {
        for (JsonOption option : cachedOptions) {
            if (option.getProperty().equals(setting)) {
                return option;
            }
        }
        return null;
    }

    public String[] getConfigSettings() {
        String returnValue[] = new String[cachedOptions.length];
        for (int i = 0; i < cachedOptions.length; i++) {
            returnValue[i] = cachedOptions[i].getProperty();
        }
        return returnValue;
    }

    public String getConfigValue(String setting) {
        JsonOption option = getOption(setting);
        if (option != null) {
            return option.getValue();
        }
        return null;
    }

    public String[] getConfigValues(String setting) {
        JsonOption option = getOption(setting);
        if (option != null) {
            return option.getValues();
        }
        return null;
    }

    public int getConfigType(String setting) {
        JsonOption option = getOption(setting);
        if (option == null) {
            return 0;
        }

        if (option.isArray()) {
            if (option.getValidValues().length == 0) {
                // This would be a multi-choice whereby the user is providing the values. I
                // don't think anything uses this possible option, but in case they do, we
                // will append some additional help text telling the user to enter the
                // configuration separated by commas.
                return Plugin.CONFIG_TEXT;
            } else {
                return Plugin.CONFIG_MULTICHOICE;
            }
        } else if (option.getValidValues().length > 0) {
            return Plugin.CONFIG_CHOICE;
        } else if (option.isReadonly()) {
            // Don't give the user the illusion that they can change the value.
            return Plugin.CONFIG_BUTTON;
        }

        switch (option.getType()) {
            case "STRING":
                return Plugin.CONFIG_TEXT;
            case "BOOLEAN":
                return Plugin.CONFIG_BOOL;
            case "INTEGER":
            case "LONG":
            case "FLOAT":
            case "DOUBLE":
                return Plugin.CONFIG_INTEGER;
        }
        return 0;
    }

    public void setConfigValue(String setting, String value) {
        JsonOption option = getOption(setting);
        if (option == null) {
            return;
        }

        JsonElement jsonSetting;
        if (option.isArray()) {
            if (option.getValidValues().length == 0) {
                // This would be a multi-choice whereby the user is providing the values. I
                // don't think anything uses this possible option, but in case they do, we
                // will append some additional help text telling the user to enter the
                // configuration separated by commas.

                // Multi-Choice (user-defined)
                jsonSetting = PojoUtil.setArrayOption(option, value.split(","));
            } else {
                // This should not actually be happening here.

                // Multi-Choice
                jsonSetting = PojoUtil.setArrayOption(option, value);
            }
        } else {
            // Choice and all other settings can be reduced to this.
            jsonSetting = PojoUtil.setOption(option, value);
        }

        if (jsonSetting != null) {
            JsonException exception = ServerManager.getInstance().postJson(server, JsonException.class, path, jsonSetting);
            if (exception.isException()) {
                System.out.println("Unable to set the property '" + exception.getObject() + "': " + exception.getMessage());
            }
        }
    }

    public void setConfigValues(String setting, String[] values) {
        JsonOption option = getOption(setting);
        if (option == null || !option.isArray()) {
            return;
        }

        JsonElement jsonSetting;
        if (option.getValidValues().length == 0) {
            // This would be a multi-choice whereby the user is providing the values. We will append
            // some additional help text telling the user to enter the configuration separated by
            // commas.

            // Multi-Choice (user-defined)
            jsonSetting = PojoUtil.setArrayOption(option, values);
        } else {
            // Multi-Choice
            jsonSetting = PojoUtil.setArrayOption(option, values);
        }

        if (jsonSetting != null) {
            JsonException exception = ServerManager.getInstance().postJson(server, JsonException.class, path, jsonSetting);
            if (exception.isException()) {
                System.out.println("OpenDCT - ERROR: Unable to set the property '" + exception.getObject() + "': " + exception.getMessage());
            }
        }
    }

    public String[] getConfigOptions(String setting) {
        JsonOption option = getOption(setting);
        if (option == null) {
            return new String[0];
        }
        return option.getValidValues();
    }

    public String getConfigHelpText(String setting) {
        JsonOption option = getOption(setting);
        if (option == null) {
            return null;
        }
        // Multi-Choice (user-defined)
        if (option.isArray() && option.getValidValues().length == 0) {
            return option.getDescription() + " Each value must be separated by a comma.";
        }

        return option.getDescription();
    }

    public String getConfigLabel(String setting) {
        JsonOption option = getOption(setting);
        if (option == null) {
            return null;
        }
        if (option.isReadonly()) {
            return option.getName() + " (Read Only)";
        }
        return option.getName();
    }
}
