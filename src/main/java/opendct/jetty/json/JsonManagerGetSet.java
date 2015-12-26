/*
 * Copyright 2015 The OpenDCT Authors. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.jetty.json;

import opendct.capture.CaptureDevice;
import opendct.config.Config;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVUnloadedDevice;

import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;

public class JsonManagerGetSet {
    private String managerName;
    private String command;
    private String parameters[];
    private String result;

    public JsonManagerGetSet() {
        managerName = "";
        command = "";
        parameters = new String[0];
    }

    public String getManagerName() {
        return managerName;
    }

    public void setManagerName(String managerName) {
        this.managerName = managerName;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String[] getParameters() {
        return parameters;
    }

    public void setParameters(String[] parameters) {
        this.parameters = parameters;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void doGetSet() {
        switch (managerName.toLowerCase()) {
            case "config":
                result = config(command, parameters);

                break;
            case "sagetvmanager":
                result = sageTVManager(command, parameters);

                break;
        }
    }

    public static String config(String command, String parameters[]) {
        switch(command.toLowerCase()) {
            case "get":
                if (parameters.length == 1) {
                    return Config.getString(parameters[0]);
                } else if (parameters.length == 2) {
                    // Get property with a default value.
                    return Config.getString(parameters[0], parameters[1]);
                }
                break;
            case "set":
                if (parameters.length == 2) {
                    Config.setString(parameters[0], parameters[1]);
                    return "ConfigSet";
                }
                break;
        }
        return "Error";
    }

    public static String sageTVManager(String command, String parameters[]) {
        String returnValue = "Error";

        switch (command.toLowerCase()) {
            case "add_capture_device":
                returnValue = "";

                for (String parameter : parameters) {
                    try {
                        CaptureDevice captureDevice = SageTVManager.addCaptureDevice(parameter);

                        if (captureDevice == null) {
                            returnValue += "Failure,";
                        } else {
                            returnValue += captureDevice.getEncoderName() + ",";
                        }
                    } catch (SocketException e) {
                        returnValue += "Failure,";
                    }
                }

                if (returnValue.length() > 0) {
                    returnValue = returnValue.substring(0, returnValue.length() - 1);
                }

                break;
            case "remove_capture_device":
                returnValue = "";

                for (String parameter : parameters) {
                    SageTVUnloadedDevice unloadedDevice = SageTVManager.removeCaptureDevice(parameter);

                    if (unloadedDevice == null) {
                        returnValue += "Failure,";
                    } else {
                        returnValue += unloadedDevice.ENCODER_NAME + ",";
                    }
                }

                if (returnValue.length() > 0) {
                    returnValue = returnValue.substring(0, returnValue.length() - 1);
                }
        }

        return returnValue;
    }
}
