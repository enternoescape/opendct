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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SageTVSocketServer implements Runnable {
    private final Logger logger = LogManager.getLogger(SageTVSocketServer.class);

    private volatile boolean listening = false;
    private volatile boolean allowConcurrentConnections = false;
    private Thread socketServerThread;
    private final Object listeningLock = new Object();

    private ServerSocket serverSocket = null;

    // This is to support V1.0 capture devices. This will not always be the actual capture device
    // SageTV will request on this port.
    private CaptureDevice captureDevice = null;

    private volatile int listenPort = 0;

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
                ExitCode.SAGETV_SOCKET.terminateJVM();
                return logger.exit(false);
            }

            try {
                socketServerThread.setName("SageTVSocketServer-" + socketServerThread.getId() + ":" + listenPort);
                socketServerThread.start();
            } catch (Exception e) {
                logger.error("There was a problem starting the listening thread for port {} => {}", listenPort, e);
            }
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

    /**
     * Enable multiple connections to be allowed on this socket server.
     * <p/>
     * Once enabled, this cannot be disabled since it would put us in a bad place organizationally.
     * For example, what server would we exclusively choose. This is enabled by SageTVManager when
     * more than one capture device wants to use the same port.
     */
    public void enableConcurrentConnections() {
        logger.entry();

        allowConcurrentConnections = true;

        logger.debug("Concurrent connections have been enabled.");

        logger.exit();
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

                // Stop the previous thread if it is still running...
                if (!allowConcurrentConnections) {
                    oldRequestThread = requestThread;
                }

                // ...after we start the new thread.
                if (allowConcurrentConnections) {
                    requestThread = new Thread(new SageTVRequestHandler(socket, null));
                } else {
                    requestThread = new Thread(new SageTVRequestHandler(socket, captureDevice));
                }
                requestThread.setName("SageTVRequestHandler-" + requestThread.getId() + ":Unknown-" + listenPort);
                requestThread.start();

                // We only want one line of communication to SageTV per port at
                // any time. This of course will have unexpected results if
                // more than one server tries to use this port.
                if (oldRequestThread != null) {
                    try {
                        logger.debug("A new connection has been established with a SageTV server. Closing old connection...");
                        oldRequestThread.interrupt();
                    } catch (Exception e) {
                        logger.error("There was an error while attempting to stop the thread => {}", e);
                    }
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
