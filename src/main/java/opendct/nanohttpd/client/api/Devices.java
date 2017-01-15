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
import opendct.nanohttpd.pojo.JsonCaptureDevice;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.PojoUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Devices {
    public static final String DEVICES = "/devices";

    public static final String DEVICE_SELECT_NONE = "None";
    public static final String DEVICE_SELECT_PROP = "opendct/selected_device";
    public static final String DEVICE_SELECT_HELP = "Select a capture device to view/modify its" +
            " properties below. Select '" + DEVICE_SELECT_NONE + "' to stop displaying properties" +
            " for any capture device.";
    public static final String DEVICE_SELECT_LABEL = "Show/Edit Capture Device Properties";

    public static final String POOL_NAME_PROP = "opendct/pool_name";
    public static final String POOL_NAME_HELP = "Change the name of the pool that this capture" +
            " device belongs to. This does not change or rename the pool for any other capture" +
            " devices. To remove this capture device from pooling, set this value to nothing.";
    public static final String POOL_NAME_LABEL = "Change Capture Device Pool Name";

    public static final String POOL_MERIT_PROP = "opendct/pool_merit";
    public static final String POOL_MERIT_HELP = "Change the merit of a capture device within a" +
            " its pool. Higher numbers mean higher priority.";
    public static final String POOL_MERIT_LABEL = "Change Capture Device Pool Merit";

    public static final String CONSUMER_PROP = "opendct/device_consumer";
    public static final String CONSUMER_HELP = "Select the stream consumer for this capture" +
            " device. 'FFmpeg' uses the FFmpeg libraries to remux the existing video and audio or" +
            " transcode to new a new video codec. 'Media Server' uses the SageTV media server to" +
            " remux the existing video and audio. 'Raw' copies the stream without any changes or" +
            " filtering. 'Dynamic' selects the desired consumer based on the currently requested" +
            " channel.";
    public static final String CONSUMER_LABEL = "Select Capture Device Stream Consumer";

    // Since we can't correctly map the device ID to the name because we can only provide the device
    // name as it will be displayed, this map allows us to change the selected option back to the
    // ID we actually use to manipulate the capture device. It is important that the JSON server
    // uses IDs instead of the actual device names because capture devices can be renamed and we
    // need to be able to ensure the intended capture device can be identified regardless of its
    // current name.
    private static final Map<String, String> deviceMap = new ConcurrentHashMap<>();
    private static JsonCaptureDevice captureDevice;
    private static OptionsHandler deviceOptions;
    private static String selectedDevice;

    public static void newServer() {
        deviceMap.clear();
        selectedDevice = null;
        refreshOptions();
    }

    private static void refreshOptions() {
        deviceOptions = null;
        captureDevice = null;
    }

    private static JsonCaptureDevice[] getAllDevices(String server) {
        String devices[] = ServerManager.getInstance().getJson(server, String[].class, DEVICES);
        if (devices != null && devices.length > 0) {
            StringBuilder builder = new StringBuilder();
            builder.append(DEVICES);
            for (String device : devices) {
                builder.append('/').append(ServerManager.encodeFile(device));
            }
            // We only need the IDs, names and if the device is enabled.
            builder.append("?name=true&enabled=true");
            return ServerManager.getInstance().getJson(server, JsonCaptureDevice[].class, builder.toString());
        }
        return new JsonCaptureDevice[0];
    }

    public static String[] getAllCaptureDeviceNames(String server) {
        JsonCaptureDevice captureDevices[] = getAllDevices(server);
        String devices[] = new String[captureDevices.length];
        for (int i = 0; i < captureDevices.length; i++) {
            devices[i] = captureDevices[i].getName();
            deviceMap.put(devices[i], String.valueOf(captureDevices[i].getId()));
        }

        devices = Arrays.copyOf(devices, devices.length + 1);
        devices[devices.length - 1] = DEVICE_SELECT_NONE;
        return devices;
    }

    public static String[] getEnabledCaptureDeviceNames(String server) {
        JsonCaptureDevice captureDevices[] = getAllDevices(server);
        List<String> returnValue = new ArrayList<>();
        for (JsonCaptureDevice captureDevice : captureDevices) {
            if (captureDevice.isEnabled()) {
                returnValue.add(captureDevice.getName());
                deviceMap.put(captureDevice.getName(), String.valueOf(captureDevice.getId()));
            }
        }
        return returnValue.toArray(new String[returnValue.size()]);
    }

    public static void setEnableCaptureDeviceNames(String server, String deviceNames[]) {
        JsonCaptureDevice captureDevices[] = getAllDevices(server);

        if (deviceNames != null && deviceNames.length > 0) {
            StringBuilder enable = new StringBuilder(DEVICES);
            StringBuilder disable = new StringBuilder(DEVICES);
            for (String deviceName : deviceNames) {
                String deviceString = deviceMap.get(deviceName);
                if (deviceString == null) {
                    continue;
                }
                int deviceId;
                try {
                    deviceId = Integer.parseInt(deviceString);
                } catch (NumberFormatException e) {
                    System.out.println("OpenDCT - ERROR: Unable to parse " + deviceString + " into an integer.");
                    continue;
                }

                for (int i = 0; i < captureDevices.length; i++) {
                    if (captureDevices[i] == null) {
                        continue;
                    }
                    if (captureDevices[i].getId() == deviceId) {
                        // Only enable capture devices that actually show they are not currently
                        // enabled.
                        if (!captureDevices[i].isEnabled()) {
                            enable.append('/').append(deviceString);
                        }
                        // When we find a capture device is on the enabled list, make it null so
                        // that we can make sure everything that is not intended to be enabled gets
                        // disabled.
                        captureDevices[i] = null;
                        break;
                    }
                }
            }
            for (JsonCaptureDevice device : captureDevices) {
                if (device != null && device.isEnabled()) {
                    disable.append('/').append(device.getId());
                }
            }
            // Disable first in the rare occurrence that loading these new capture devices creates a
            // big spike in memory usage that could cause problems.
            if (disable.length() > DEVICES.length()) {
                JsonElement element = PojoUtil.setOption("disableDevice", "true");
                ServerManager.getInstance().postJson(server, JsonException.class, enable.toString(), element);
            }
            if (enable.length() > DEVICES.length()) {
                JsonElement element = PojoUtil.setOption("enableDevice", "true");
                ServerManager.getInstance().postJson(server, JsonException.class, enable.toString(), element);
            }
        }
        refreshOptions();
    }

    private static JsonCaptureDevice getCaptureDevice(String server) {
        String cachedSelectedDevice = selectedDevice;
        if (cachedSelectedDevice == null) {
            return null;
        }
        JsonCaptureDevice cachedDevice = captureDevice;
        if (cachedDevice == null) {
            JsonCaptureDevice devices[] = ServerManager.getInstance().getJson(server, JsonCaptureDevice[].class, DEVICES + "/" + ServerManager.encodeFile(cachedSelectedDevice));
            if (devices != null && devices.length > 0) {
                cachedDevice = ServerManager.getInstance().getJson(server, JsonCaptureDevice[].class, DEVICES + "/" + ServerManager.encodeFile(cachedSelectedDevice))[0];
                captureDevice = cachedDevice;
            } else {
                return null;
            }
        }
        return cachedDevice;
    }

    private static OptionsHandler getOptions(String server, JsonCaptureDevice cachedDevice) {
        OptionsHandler cachedOptions = deviceOptions;
        if (cachedOptions == null) {
            if (cachedDevice == null) {
                return null;
            }
            cachedOptions = new OptionsHandler(server, DEVICES + "/" + String.valueOf(cachedDevice.getId()), cachedDevice.getOptions());
            deviceOptions = cachedOptions;
        }
        return cachedOptions;
    }

    public static String[] getConfigSettings(String server) {
        String options[];
        List<String> optionsList = new ArrayList<>();
        JsonCaptureDevice cachedDevice = getCaptureDevice(server);
        OptionsHandler cachedOptions = getOptions(server, cachedDevice);
        if (cachedDevice!= null && cachedOptions != null) {
            optionsList.add(DEVICE_SELECT_PROP);
            optionsList.add(CONSUMER_PROP);

            // This variable will be null if pooling is not enabled. If this feature is not enabled
            // for this specific device, it will be an empty string, but never null.
            if (cachedDevice.getPoolName() != null) {
                optionsList.add(POOL_NAME_PROP);
                optionsList.add(POOL_MERIT_PROP);
            }
            options = cachedOptions.getConfigSettings();
        } else {
            return new String[] { DEVICE_SELECT_PROP };
        }
        String primaryOptions[] = optionsList.toArray(new String[optionsList.size()]);
        String returnValue[] = new String[options.length + primaryOptions.length];
        System.arraycopy(options, 0, returnValue, primaryOptions.length, options.length);
        System.arraycopy(primaryOptions, 0, returnValue, 0, primaryOptions.length);
        return returnValue;
    }

    public static String getConfigValue(String server, String setting) {
        JsonCaptureDevice cachedDevice = getCaptureDevice(server);
        switch (setting) {
            case DEVICE_SELECT_PROP:
                if (selectedDevice == null) {
                    return DEVICE_SELECT_NONE;
                } else {
                    String returnValue = deviceMap.get(selectedDevice);
                    if (returnValue == null) {
                        return DEVICE_SELECT_NONE;
                    } else {
                        return selectedDevice;
                    }
                }
            case CONSUMER_PROP:
                if (cachedDevice != null) {
                    return cachedDevice.getConsumer();
                }
                break;
            case POOL_NAME_PROP:
                if (cachedDevice != null) {
                    return cachedDevice.getPoolName();
                }
                break;
            case POOL_MERIT_PROP:
                if (cachedDevice != null) {
                    return String.valueOf(cachedDevice.getPoolMerit());
                }
                break;
            default:
                OptionsHandler cachedOptions = getOptions(server, cachedDevice);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigValue(setting);
                }
        }
        return null;
    }

    public static String[] getConfigValues(String server, String setting) {
        switch (setting) {
            default:
                JsonCaptureDevice cachedDevice = getCaptureDevice(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDevice);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigValues(setting);
                }
                return null;
        }
    }

    public static int getConfigType(String server, String setting) {
        switch (setting) {
            case DEVICE_SELECT_PROP:
                return Plugin.CONFIG_CHOICE;
            case CONSUMER_PROP:
                return Plugin.CONFIG_CHOICE;
            case POOL_NAME_PROP:
                return Plugin.CONFIG_TEXT;
            case POOL_MERIT_PROP:
                return Plugin.CONFIG_INTEGER;
            default:
                JsonCaptureDevice cachedDevice = getCaptureDevice(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDevice);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigType(setting);
                }
                return 0;
        }
    }

    public static void setConfigValue(String server, String setting, String value) {
        JsonElement element;
        String property;
        switch (setting) {
            case DEVICE_SELECT_PROP:
                String newSelectedDevice = deviceMap.get(value);
                if (newSelectedDevice != null) {
                    selectedDevice = newSelectedDevice;
                    refreshOptions();
                }
                return;
            case CONSUMER_PROP:
                property = "consumer";
                break;
            case POOL_NAME_PROP:
                property = "poolName";
                break;
            case POOL_MERIT_PROP:
                property = "poolMerit";
                break;
            default:
                OptionsHandler cachedOptions = getOptions(server, getCaptureDevice(server));
                if (cachedOptions != null) {
                    cachedOptions.setConfigValue(setting, value);
                    refreshOptions();
                }
                return;
        }
        JsonCaptureDevice cachedDevice = getCaptureDevice(server);
        if (cachedDevice != null) {
            element = PojoUtil.setOption(property, value);
            ServerManager.getInstance().postJson(server, JsonException.class, DEVICES + "/" + String.valueOf(cachedDevice.getId()), element);
        }
    }

    public static void setConfigValues(String server, String setting, String[] values) {
        switch (setting) {
            default:
                JsonCaptureDevice cachedDevice = getCaptureDevice(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDevice);
                if (cachedOptions != null) {
                    cachedOptions.setConfigValues(setting, values);
                    refreshOptions();
                }
        }
    }

    public static String[] getConfigOptions(String server, String setting) {
        switch (setting) {
            case DEVICE_SELECT_PROP:
                return getAllCaptureDeviceNames(server);
            case CONSUMER_PROP:
                return ServerManager.getInstance().getJson(server, String[].class, Consumers.CONSUMERS);
            default:
                JsonCaptureDevice cachedDevice = getCaptureDevice(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDevice);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigOptions(setting);
                }
                return null;
        }
    }

    public static String getConfigHelpText(String server, String setting) {
        switch (setting) {
            case DEVICE_SELECT_PROP:
                return DEVICE_SELECT_HELP;
            case CONSUMER_PROP:
                return CONSUMER_HELP;
            case POOL_NAME_PROP:
                return POOL_NAME_HELP;
            case POOL_MERIT_PROP:
                return POOL_MERIT_HELP;
            default:
                JsonCaptureDevice cachedDevice = getCaptureDevice(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDevice);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigHelpText(setting);
                }
                return null;
        }
    }

    public static String getConfigLabel(String server, String setting) {
        switch (setting) {
            case DEVICE_SELECT_PROP:
                return DEVICE_SELECT_LABEL;
            case CONSUMER_PROP:
                return CONSUMER_LABEL;
            case POOL_NAME_PROP:
                return POOL_NAME_LABEL;
            case POOL_MERIT_PROP:
                return POOL_MERIT_LABEL;
            default:
                JsonCaptureDevice cachedDevice = getCaptureDevice(server);
                OptionsHandler cachedOptions = getOptions(server, cachedDevice);
                if (cachedOptions != null) {
                    return cachedOptions.getConfigLabel(setting);
                }
                return null;
        }

    }
}
