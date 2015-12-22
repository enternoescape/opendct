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

import opendct.power.PowerEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;

public class JettyManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(JettyManager.class);
    public static PowerEventListener POWER_EVENT_LISTENER = new JettyManager();

    //TODO: [js] Create a simple web server for communicating with and troubleshooting the network encoder.

    private static Server server = new Server();
    private static int jettyPort = 8090;
    private static int jettySecurePort = 8093;

    public synchronized static void startJetty(int port, int securePort) {
        logger.error("Starting Jetty server on ports {} and {}...", port, securePort);

        if (server.isStarting() || server.isStarted()) {
            return;
        }

        JettyManager.jettyPort = port;
        JettyManager.jettySecurePort = securePort;

        server = new Server(port);
        ServerConnector serverConnector = new ServerConnector(server);

        serverConnector.setPort(jettyPort);
        serverConnector.setIdleTimeout(30000);

        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        File file = new File("../../src/main/webapp");
        webapp.setWar(file.getAbsolutePath());

        server.setHandler(webapp);

        try {
            server.start();
        } catch (Exception e) {
            logger.error("There was a problem while attempting to start Jetty server => ", e);
        }
    }

    public synchronized static void stopJetty() throws InterruptedException {
        if (server.isStopping()) {
            server.join();
        } else if  (server.isStopped()) {
            return;
        }

        try {
            logger.info("Stopping Jetty server...");
            server.stop();
        } catch (Exception e) {
            logger.error("There was a problem while attempting to stop Jetty server => ", e);
        }
    }

    @Override
    public void onSuspendEvent() {

    }

    @Override
    public void onResumeSuspendEvent() {

    }

    @Override
    public void onResumeCriticalEvent() {

    }

    @Override
    public void onResumeAutomaticEvent() {

    }

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
