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

package opendct.nanohttpd;

import opendct.config.Config;
import opendct.power.PowerEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NanoHTTPDManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(NanoHTTPDManager.class);
    public static final NanoHTTPDManager POWER_EVENT_LISTENTER = new NanoHTTPDManager();

    public static final boolean ENABLED = Config.getBoolean("web.enabled", true);
    private static int port = Config.getInteger("web.port", 9091);
    private static NanoServlet currentServer = null;

    /**
     * Start the webserver on the default port.
     *
     * @return <i>true</i> if successful.
     */
    public synchronized static boolean startWebServer() {
        return startWebServer(NanoHTTPDManager.port);
    }

    /**
     * Start the webserver on a specific port.
     * <p/>
     * If a webserver is already running, it will not be stopped until the new one successfully
     * starts.
     *
     * @param port The port number.
     * @return <i>true</i> if the server was able to start.
     */
    public synchronized static boolean startWebServer(int port) {
        if (!ENABLED) {
            return false;
        }

        if (currentServer != null && port == NanoHTTPDManager.port) {
            logger.warn("Webserver is already running on port {}...", port);
            return true;
        }

        logger.info("Starting webserver on port {}...", port);

        try {
            NanoServlet nanoServlet = new NanoServlet(port);
            nanoServlet.start();
            NanoHTTPDManager.port = port;

            // If a previous webserver was already running, this will now stop it before we swap
            // things out. This ensures we don't kill access to the webserver due to a port
            // configuration issue/error.
            stopWebServer();

            currentServer = nanoServlet;

            return true;
        } catch (Exception e) {
            logger.error("Unable to open webserver on port {} => ", port, e);
        }

        return false;
    }

    public synchronized static void stopWebServer() {
        if (currentServer != null) {
            logger.info("Stopping webserver on port {}...", port);

            currentServer.stop();
            currentServer = null;
        }
    }

    /**
     * Changes the saved port used by the webserver while the program is still running.
     * <p/>
     * The port saved doesn't get updated unless the new port was opened successfully.
     *
     * @param port The new webserver port.
     * @return <i>true</i> if the change was successful.
     */
    public synchronized static boolean changePort(int port) {
        stopWebServer();

        if (startWebServer(port)) {
            Config.setInteger("web.port", port);
            return true;
        }

        logger.warn("Unable to start webserver on port {}, reverted back to {}.",
                port, NanoHTTPDManager.port);

        return false;
    }

    public synchronized static int getPort() {
        return port;
    }

    @Override
    public void onSuspendEvent() {
        stopWebServer();
    }

    @Override
    public void onResumeSuspendEvent() {
        startWebServer();
    }

    @Override
    public void onResumeCriticalEvent() {
        startWebServer();
    }

    @Override
    public void onResumeAutomaticEvent() {
        startWebServer();
    }


    //TODO: [js] Create a simple web server for communicating with and troubleshooting the network encoder.

    /*
    Idea #1

    Overview layout.
    ================
     Network Encoders |       Status       |
    Network Encoder 0 | Streaming/Inactive |
    Network Encoder 1 | Streaming/Inactive |
    Network Encoder 2 | Streaming/Inactive |
    Network Encoder 3 | Streaming/Inactive |

    Network Encoders - These are links that will take you to the Detail layout.

    Status - Overall this will just be a very general state of the network encoder. Beyond the most obvious status, I
             think we could have things like Ready to indicate that the tuner is in a "hot" state whereby it just needs
             a channel and you will be streaming in less than a second. We can have Broken to indicate the tuner is in a
             very bad state that it doesn't know how to handle.

    Detail layout.
    ==============
    Network Encoders List | Status Tab | Properties Tab | Troubleshoot Tab
    ----------------------------------------------------------------------
    Network Encoder 0     |
    Network Encoder 1     |                      Body
    Network Encoder 2     |
    Network Encoder 3     |

    Network Encoders - List of capture devices by network encoder name available. Clicking on one changes the details
                       displayed in the Body to show the details of that tuner. For mobile devices, this might need to
                       be done a little differently. Maybe a button in the upper left that when tapped shows this list.

    Status - This is a tab. It displays all information obtainable about the current status of the selected network
             encoder. Depending on how much detail is available, we might need some tabs within the tab to keep it from
             looking ridiculous.

    Properties - This is a tab. Displays all properties pertaining to the selected network encoder. Will even allow
                 changes to be made at runtime if possible. Changes might need to be blocked when the network encoder is
                 actively streaming.

    Troubleshoot - This is a tab. Allows you to manipulate the selected network encoder impersonating a SageTV server.
                   Other helpful runtime manipulations related to debugging will be available here too.

     */
}
