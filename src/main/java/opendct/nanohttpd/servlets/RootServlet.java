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

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.router.RouterNanoHTTPD;
import opendct.capture.CaptureDevice;
import opendct.config.StaticConfig;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RootServlet {
    private static final Logger logger = LogManager.getLogger(RootServlet.class);
    private static final String BR = "<br/>";
    private static final String P = "<p/>";

    public static class Get extends RouterNanoHTTPD.DefaultHandler {

        @Override
        public String getText() {
            StringBuilder response = new StringBuilder(2048);
            response.append("OpenDCT ").append(StaticConfig.VERSION_PROGRAM).append(P).append(P);
            response.append("Loaded Capture Devices:").append(P);
            List<CaptureDevice> devices = SageTVManager.getAllSageTVCaptureDevices();
            Comparator<CaptureDevice> comparator = new Comparator<CaptureDevice>() {
                @Override
                public int compare(CaptureDevice o1, CaptureDevice o2) {
                    return o1.getEncoderName().compareTo(o2.getEncoderName());
                }
            };
            Collections.sort(devices, comparator);

            for (CaptureDevice device : devices) {
                response.append(device.getEncoderName()).append(": ");
                if (device.isInternalLocked()) {
                    response.append("Recording to ").append(device.getRecordFilename());
                } else {
                    response.append(device.isOfflineChannelScan() ? "Offline Scanning" : "Idle");
                }
                response.append(BR);
            }
            return response.toString();
        }

        @Override
        public String getMimeType() {
            return "text/html";
        }

        @Override
        public NanoHTTPD.Response.IStatus getStatus() {
            return NanoHTTPD.Response.Status.OK;
        }
    }
}
