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

package opendct.jetty;

import opendct.capture.CaptureDevice;
import opendct.channel.ChannelLineup;
import opendct.channel.ChannelManager;
import opendct.config.Config;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVPoolManager;
import opendct.sagetv.SageTVUnloadedDevice;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;

public class WebDivBuilders {

    public static void printHeader(PrintWriter out) {
        out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">\n" +
                "<html>\n" +
                "<head>\n" +
                "   <script src=\"https://ajax.googleapis.com/ajax/libs/jquery/2.1.4/jquery.min.js\"></script>\n" +
                "   <link rel=\"stylesheet\" type=\"text/css\" href=\"css/opendct.css\"/>\n" +
                "   <title>OpenDCT Web Interface</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "<h1>OpenDCT Web Interface</h1>");
    }

    public static void printFooter(PrintWriter out) {
        out.println("<div class=\"footer\">\n" +
                "   <p>Page Generated " + (new Date()).toString() + "</p>\n" +
                "   <p>SageTV OpenDCT Version " + Config.VERSION + "</p>\n" +
                "</div>\n" +
                "</body>");
    }

    public static void printLoadedCaptureDeviceTable(PrintWriter out) {
        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllLoadedCaptureDevicesSorted();

        if (captureDevices.size() == 0) {
            out.println("<p/>");
            out.println("<div class=\"notification\">No capture devices currently available.</div>");
            out.println("<p/>");
            out.println("<hr/>");
            return;
        }

        out.println("<form><div class=\"loaded_devices_table\">");
        out.println("<select name=\"p\"");
        out.println("<option value=\"1\" selected>Option 1</option>");
        out.println("<option value=\"2\"         >Option 2</option>");
        out.println("</select>");
        out.println("<table class=\"loaded_devices\">");

        for (CaptureDevice captureDevice : captureDevices) {
            printCaptureDeviceCell(captureDevice.getEncoderName(), out);
        }

        out.println("</table></div></form>");
        out.println("<hr/>");
    }

    public static void printCaptureDeviceCell(String captureDeviceName, PrintWriter out) {
        CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(captureDeviceName, false);

        if (captureDevice == null || out == null) {
            return;
        }
        out.println("<table class=\"loaded_device\">");
        out.println("<tr>");

        try {
            out.println("<td class=\"checkbox\"><input type='checkbox' name=\"capdev[]\" value=\"" + URLEncoder.encode(captureDevice.getEncoderName(), Config.STD_BYTE) + "\"/></td>");
        } catch (UnsupportedEncodingException e) {

        }

        out.println("<td class=\"title_cell\">");
        out.println(captureDevice.getEncoderName());
        if (captureDevice.isLocked() && captureDevice.getRecordedBytes() > 0) {
            if (SageTVPoolManager.isUsePools()) {
                String virtualTuner = SageTVPoolManager.getPoolCaptureDeviceToVCaptureDevice(captureDeviceName);
                out.println("<br/>Mapped to SageTV as " + virtualTuner);
            } else {
                out.print("<br/>");
            }
            out.print("<br/>Recording from channel ");
            out.print(captureDevice.getLastChannel());
            out.print(" to ");
            out.println(captureDevice.getRecordFilename());
        } else {
            out.print("<br/><br/>Inactive");
        }
        out.println("</td>");

        if (SageTVPoolManager.isUsePools()) {
            out.println("<td class=\"pool_cell\">");

            String poolName = captureDevice.getEncoderPoolName();

            if (poolName != null && !poolName.equals("")) {
                out.print("Pool: ");
                out.print(captureDevice.getEncoderPoolName());
                out.println("<br/>");
                out.print("Merit: ");
                out.print(captureDevice.getMerit());
                out.println("<br/>");
                out.print("Locked: ");
                out.print(captureDevice.isExternalLocked());
                out.println("<br/>");
            }

            out.println("</td>");
        }


        ChannelLineup lineup = ChannelManager.getChannelLineup(captureDevice.getChannelLineup());
        String lineupName;
        if (lineup != null) {
            lineupName = lineup.getFriendlyName();
        } else {
            lineupName = captureDevice.getChannelLineup();
        }

        out.println("<td class=\"details_cell\">");

        out.print("Lineup: ");
        out.print(lineupName);
        out.println("<br/>");
        out.print("Offline Scan Enabled: ");
        out.print(captureDevice.isOfflineScanEnabled());
        out.println("<br/>");
        out.print("Switch Enabled: ");
        out.print(captureDevice.canSwitch());
        out.println("<br/>");

        out.println("</td>");
        out.println("</tr>");
        out.println("</table>");
    }

    public static void printUnloadedCaptureDeviceTable(PrintWriter out, String returnUrl) {

        ArrayList<SageTVUnloadedDevice> unloadedDevices = SageTVManager.getAllUnloadedDevicesSorted();

        if (unloadedDevices.size() == 0) {
            out.println("<p/>");
            out.println("<div class=\"notification\">No unloaded capture devices currently available.</div>");
            out.println("<p/>");
            out.println("<hr/>");
            return;
        }

        out.println("<div class=\"content\"><table class=\"unloaded_devices\">");

        for (SageTVUnloadedDevice unloadedDevice : SageTVManager.getAllUnloadedDevicesSorted()) {
            printUnloadedDeviceCell(unloadedDevice, out, returnUrl);
        }
        out.println("</table></div>");
        out.println("<hr/>");
    }

    public static void printUnloadedDeviceCell(SageTVUnloadedDevice unloadedDevice, PrintWriter out, String returnUrl) {
        if (unloadedDevice == null || out == null) {
            return;
        }

        out.println("<table class=\"loaded_device\">");
        out.println("<tr>");

        try {
            out.println("<td class=\"checkbox\"><input type='checkbox' name=\"unldev[]\" value=\"" + URLEncoder.encode(unloadedDevice.ENCODER_NAME, Config.STD_BYTE) + "\"/></td>");
        } catch (UnsupportedEncodingException e) {

        }

        out.println("<td class=\"title_cell\">");
        out.println(unloadedDevice.ENCODER_NAME + "<br/>");
        out.println(unloadedDevice.DESCRIPTION + "<br/>");
        try {
            out.print("<a href=\"opendct/sagetvmanager?unldev=" + URLEncoder.encode(unloadedDevice.ENCODER_NAME, Config.STD_BYTE) + "&p=load_device&v=true&return=" + URLEncoder.encode(returnUrl, Config.STD_BYTE) +"\">");
        } catch (UnsupportedEncodingException e) {

        }
        if (unloadedDevice.isPersistent()) {
            out.print("Create New Capture Device");
        } else {
            out.print("Enable Capture Device");
        }
        out.println("</a>");


        out.println("</td>");
        out.println("</tr>");
        out.println("</table>");
    }
}
