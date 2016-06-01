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

package opendct;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import opendct.nanohttpd.pojo.JsonCaptureDevice;
import opendct.nanohttpd.pojo.JsonOption;
import org.testng.annotations.Test;

public class JsonTest {
    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static final Gson gson;

    static {
        gson = gsonBuilder.create();
    }

    @Test(groups = { "json", "parsePropertyArray" })
    public void jsonServerParsePropertyArray() {

        String serialized = "[\n" +
                "      {\n" +
                "        \"property\": \"sagetv.device.-1132986414.device_name\",\n" +
                "        \"value\": \"HDHomeRun HDHR4-2US Tuner FFFFFFFF-0\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"property\": \"sagetv.device.-1132986414.always_force_external_unlock\",\n" +
                "        \"value\": \"false\"\n" +
                "      }\n" +
                "    ]";


        JsonOption deviceOption[] = gson.fromJson(serialized, JsonOption[].class);

        assert (deviceOption[0].getProperty().equals("sagetv.device.-1132986414.device_name"));
        assert (!deviceOption[0].isArray());
        assert (deviceOption[0].getValue().equals("HDHomeRun HDHR4-2US Tuner FFFFFFFF-0"));
        assert (deviceOption[1].getProperty().equals("sagetv.device.-1132986414.always_force_external_unlock"));
        assert (!deviceOption[1].isArray());
        assert (deviceOption[1].getValue().equals("false"));
    }

    @Test(groups = { "json", "parsePropertySingle" })
    public void jsonServerParsePropertySingle() {

        String serialized = "{\n" +
                "        \"property\": \"sagetv.device.-1132986414.device_name\",\n" +
                "        \"value\": \"HDHomeRun HDHR4-2US Tuner FFFFFFFF-0\"\n" +
                "      }";

        JsonOption deviceOption = gson.fromJson(serialized, JsonOption.class);

        assert (deviceOption.getProperty().equals("sagetv.device.-1132986414.device_name"));
        assert (!deviceOption.isArray());
        assert (deviceOption.getValue().equals("HDHomeRun HDHR4-2US Tuner FFFFFFFF-0"));
    }

    @Test(groups = { "json", "parseClientPropertySingle" })
    public void jsonClientParsePropertySingle() {
        String serialized = "{\n" +
                "        \"property\": \"sagetv.device.-1132986414.device_name\",\n" +
                "        \"name\": \"Name\",\n" +
                "        \"description\": \"This is the name of the capture device that SageTV will use.\",\n" +
                "        \"readonly\": true,\n" +
                "        \"type\": \"STRING\",\n" +
                "        \"validValues\": [],\n" +
                "        \"value\": \"HDHomeRun HDHR4-2US Tuner FFFFFFFF-0\"\n" +
                "      }";

        JsonOption jsonOption = gson.fromJson(serialized, JsonOption.class);

        assert (jsonOption.getProperty().equals("sagetv.device.-1132986414.device_name"));
        assert (jsonOption.getName().equals("Name"));
        assert (jsonOption.getDescription().equals("This is the name of the capture device that SageTV will use."));
        assert (!jsonOption.isArray());
        assert (jsonOption.isReadonly());
        assert (jsonOption.getType().equals("STRING"));
        assert (jsonOption.getValidValues().length == 0);
        assert (jsonOption.getValue().equals("HDHomeRun HDHR4-2US Tuner FFFFFFFF-0"));
        assert (jsonOption.getValues() == null);
    }

    @Test(groups = { "json", "parseClientCaptureDeviceSingle" })
    public void jsonClientParseCaptureDeviceSingle() {
        String serialized = "{\n" +
                "    \"id\": -1132986414,\n" +
                "    \"enabled\": false,\n" +
                "    \"name\": \"HDHomeRun HDHR4-2US Tuner XXXXXXXX-0\",\n" +
                "    \"description\": \"HDHomeRun HDHR4-2US capture device.\",\n" +
                "    \"loaded\": true,\n" +
                "    \"poolName\": \"atsc_xxxxxxxx\",\n" +
                "    \"poolMerit\": 0,\n" +
                "    \"lineupName\": \"atsc_hdhomerun_xxxxxxxx\",\n" +
                "    \"lastChannel\": \"-1\",\n" +
                "    \"recordingStart\": 1464373680268,\n" +
                "    \"producedPackets\": 0,\n" +
                "    \"recordedBytes\": 0,\n" +
                "    \"recordingFilename\": \"\",\n" +
                "    \"recordingQuality\": \"\",\n" +
                "    \"internalLocked\": false,\n" +
                "    \"externalLocked\": false,\n" +
                "    \"offlineChannelScanEnabled\": false,\n" +
                "    \"uploadId\": 0,\n" +
                "    \"signalStrength\": 0,\n" +
                "    \"broadcastStandard\": \"ATSC\",\n" +
                "    \"copyProtection\": \"NONE\",\n" +
                "    \"consumer\": \"opendct.consumer.DynamicConsumerImpl\",\n" +
                "    \"transcodeProfile\": \"\",\n" +
                "    \"deviceType\": \"ATSC_HDHOMERUN\",\n" +
                "    \"sagetvCrossbars\": [\n" +
                "      {\n" +
                "        \"index\": 100,\n" +
                "        \"name\": \"Digital TV Tuner\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"options\": [\n" +
                "      {\n" +
                "        \"property\": \"sagetv.device.-1132986414.device_name\",\n" +
                "        \"name\": \"Name\",\n" +
                "        \"description\": \"This is the name of the capture device that SageTV will use. If this name is changed on a device already in use in SageTV, you will need to re-add the device.\",\n" +
                "        \"readonly\": true,\n" +
                "        \"type\": \"STRING\",\n" +
                "        \"validValues\": [],\n" +
                "        \"value\": \"HDHomeRun HDHR4-2US Tuner XXXXXXXX-0\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"property\": \"sagetv.device.-1132986414.always_force_external_unlock\",\n" +
                "        \"name\": \"Always Force Unlock\",\n" +
                "        \"description\": \"This will allow the program to always override the HDHomeRun lock when SageTV requests a channel to be tuned.\",\n" +
                "        \"readonly\": false,\n" +
                "        \"type\": \"BOOLEAN\",\n" +
                "        \"validValues\": [],\n" +
                "        \"value\": \"false\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }";

        JsonCaptureDevice jsonCaptureDevice = gson.fromJson(serialized, JsonCaptureDevice.class);

        assert (jsonCaptureDevice.isLoaded());
        assert (!jsonCaptureDevice.isEnabled());
        assert (jsonCaptureDevice.getOptions()[1].getProperty().equals("sagetv.device.-1132986414.always_force_external_unlock"));
        assert (jsonCaptureDevice.getSagetvCrossbars()[0].getIndex() == 100);
    }
}
