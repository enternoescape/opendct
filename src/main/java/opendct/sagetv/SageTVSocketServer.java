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

package opendct.sagetv;

import opendct.capture.CaptureDevice;
import opendct.config.ExitCode;
import opendct.power.NetworkPowerEventManger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;

public class SageTVSocketServer implements Runnable {
    private final Logger logger = LogManager.getLogger(SageTVSocketServer.class);

    private volatile boolean listening = false;
    private Thread socketServerThread;
    private final Object listeningLock = new Object();

    // Every unique IP address that connects to this program is placed in this list for one time
    // operations.
    private static HashSet<InetAddress> registeredRemoteIps = new HashSet<>();
    private ServerSocket serverSocket = null;

    // This is to support V1.0 capture devices. This will not always be the actual capture device
    // SageTV will request on this port.
    private CaptureDevice captureDevice;

    private volatile int listenPort;

    public SageTVSocketServer(Integer listenPort, CaptureDevice captureDevice) {
        this.listenPort = listenPort;
        this.captureDevice = captureDevice;
    }

    /**
     * Starts the listening server.
     * <p/>
     * Be careful when calling this method without a lock since this method will close the JVM if it
     * fails to open the port because the port is in use by another process since this is a critical
     * function of the program.
     *
     * @return <i>true</i> if the port was opened or <i>false</i> if the port is already open.
     */
    public boolean startListening() {
        logger.entry();

        SageTVTuningMonitor.startMonitor();

        boolean error = false;

        synchronized (listeningLock) {
            logger.debug("Setting listening flag...");

            // All of the methods used to start listening are private, so this will prevent
            // more than one request at a time to run the entire tuning process.
            if (listening) {
                logger.warn("Listening is already in progress.");
                return logger.exit(false);
            }

            socketServerThread = new Thread(this);

            logger.info("Opening ServerSocket on port {}...", listenPort);
            try {
                serverSocket = new ServerSocket(listenPort);
            } catch (IOException e) {
                logger.error("Unable to open SocketServer on port {} => {}", listenPort, e);
                error = true;
            }

            if (!error) {
                socketServerThread.setName("SageTVSocketServer-" + socketServerThread.getId() + ":" + listenPort);
                socketServerThread.start();
            }
        }

        if (error) {
            // Doing this within a lock will cause the lock to be released.
            ExitCode.SAGETV_SOCKET.terminateJVM("Make sure you are not running the service and" +
                    " the console at the same time.");
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    /**
     * This will tell the SageTV Socket Server thread to terminate and clean up.
     */
    public void stopListening() {
        logger.entry();

        synchronized (listeningLock) {
            logger.debug("Stopping listening thread...");

            if (!listening) {
                logger.debug("Listening is not in progress.");
                logger.exit();
                return;
            }
            listening = false;

            try {
                serverSocket.close();
            } catch (IOException e) {

            }

            try {
                socketServerThread.interrupt();
                socketServerThread.join();
            } catch (Exception e) {
                logger.trace("There was a problem stopping the thread => {}", e);
            }
        }

        logger.exit();
        return;
    }

    public boolean changeListenPort(int newListenPort) {
        logger.entry();

        Boolean returnValue = false;

        if (newListenPort != this.listenPort) {
            synchronized (listeningLock) {
                stopListening();
                this.listenPort = newListenPort;
                returnValue = startListening();
            }
        }

        return logger.exit(returnValue);
    }

    /**
     * Get the current listening port.
     *
     * @return Returns the currently used listening port.
     */
    public int getListenPort() {
        return listenPort;
    }

    public void run() {
        logger.entry();
        logger.info("Started listening on port {}...", listenPort);

        Thread requestThread = null;
        Thread oldRequestThread = null;
        listening = true;

        while (listening) {
            try {
                if (logger.isTraceEnabled()) {
                    logger.trace("Accepting connection on port {}...", listenPort);
                }

                Socket socket = serverSocket.accept();

                requestThread = new Thread(new SageTVRequestHandler(socket, captureDevice));
                requestThread.setName("SageTVRequestHandler-" + requestThread.getId() + ":Unknown-" + listenPort);
                requestThread.start();


                InetAddress remoteAddress = socket.getInetAddress();

                // This will keep this task from being performed constantly on connection. It only
                // needs to be done once.
                if (!registeredRemoteIps.contains(remoteAddress)) {
                    if (remoteAddress instanceof Inet4Address) {
                        try {
                            NetworkPowerEventManger.POWER_EVENT_LISTENER.addDependentInterface(
                                    remoteAddress);

                        } catch (Exception e) {
                            logger.debug("Unable to register a local interface for the" +
                                            " external IP address {}. Will not try again => ",
                                    socket.getInetAddress(), e);
                        }
                    } else {
                        logger.warn("IPv6 connection detected. This is an untested configuration.");
                    }

                    registeredRemoteIps.add(socket.getInetAddress());
                }

            } catch (IOException e) {
                if (listening) {
                    logger.error("Unable to accept connections on port {} => {}", listenPort, e);

                    logger.info("Re-opening ServerSocket on port {}...", listenPort);

                    if (serverSocket != null && !serverSocket.isClosed()) {
                        try {
                            serverSocket.close();
                        } catch (Exception e0) {
                            logger.debug("An unexpected exception occurred while closing the socket => {}", e0);
                        }
                    }

                    try {
                        serverSocket = new ServerSocket(listenPort);
                    } catch (IOException e0) {
                        logger.error("Unable to open SocketServer on port {} => {}", listenPort, e0);
                        ExitCode.SAGETV_SOCKET.terminateJVM();
                    }
                }
            }
        }

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                logger.debug("An unexpected exception occurred while closing the socket => {}", e);
            }
        }

        logger.info("Stopped listening on port {}...", listenPort);
        logger.exit();
    }
}
