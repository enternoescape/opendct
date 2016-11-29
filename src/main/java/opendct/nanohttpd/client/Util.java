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

package opendct.nanohttpd.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class Util {
    public static final boolean IS_WINDOWS;
    public static final boolean IS_LINUX;

    static {
        String returnValue = getOsVersion();
        IS_WINDOWS = returnValue.equals("WINDOWS");
        IS_LINUX = returnValue.equals("LINUX");
    }

    private static String getOsVersion() {
        String os = System.getProperty("os.name");
        if (os.toLowerCase().contains("windows")) {
            return "WINDOWS";
        } else if (os.toLowerCase().contains("linux")) {
            return "LINUX";
        } else if (os.toLowerCase().contains("mac")) {
            return "MAC";
        }
        System.out.println("OpenDCT - WARN: Unable to determine OS from " + os);
        return "UNKNOWN";
    }

    public static List<InetAddress> getBroadcastAddresses() {
        return getBroadcastAddresses(true);
    }

    private static List<InetAddress> getBroadcastAddresses(boolean retry)
    {
        List<InetAddress> returnValue = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                    List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();

                    for (InterfaceAddress interfaceAddr : addresses) {
                        InetAddress address = interfaceAddr.getAddress();

                        if (address instanceof Inet4Address) {
                            returnValue.add(interfaceAddr.getBroadcast());
                            break;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            // NetworkInterface.getNetworkInterfaces() is known to on very rare occasion throw an
            // exception when returning the interfaces. If this happens, we will try one more time
            // because giving up.
            System.out.println("OpenDCT - ERROR: Unable to enumerate broadcast addresses => " + e.getMessage());
            e.printStackTrace(System.out);
            if (retry) {
                return getBroadcastAddresses(false);
            }
        }

        // Last effort to get at least one broadcast address. This should be a rare situation unless
        // we're on an IPv6 only network in which case we would need to completely re-write all of
        // this. This is not ideal because Java will select an interface of its choosing to
        // broadcast from and it might not be the most desirable one.
        if (returnValue.size() == 0) {
            try {
                returnValue.add(InetAddress.getByName("255.255.255.255"));
            } catch (UnknownHostException e1) {}
        }
        return returnValue;
    }

    private static String[] getCommandValue(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            ReadOutput stdOut = new ReadOutput(process.getInputStream(), "stdout");
            ReadOutput errOut = new ReadOutput(process.getErrorStream(), "errout");
            Thread stdOutThread = new Thread(stdOut);
            Thread errOutThread = new Thread(errOut);
            stdOutThread.start();
            errOutThread.start();
            process.waitFor();
            stdOut.setClosed();
            errOut.setClosed();
            return stdOut.getLines();
        } catch (Exception e) {
            System.out.println("OpenDCT - ERROR: Exception executing " + command + " => ");
            e.printStackTrace(System.out);
            return new String[0];
        }
    }

    public static final int SERVICE_NOT_FOUND = -1;
    public static final int SERVICE_DISABLED = 0;
    public static final int SERVICE_ENABLED = 1;
    public static final int SERVICE_RUNNING = 2;
    public static final int SERVICE_STOPPED = 3;

    private static int serviceRunning(String serviceName) {
        return serviceRunning(serviceName, serviceName);
    }

    private static int serviceRunning(String windows, String linux) {
        String values[];
        if (IS_WINDOWS) {
            values = getCommandValue("sc query \"" + windows + "\"");
/* Process running example:
SERVICE_NAME: opendct
        TYPE               : 10  WIN32_OWN_PROCESS
        STATE              : 2  START_PENDING
                                (NOT_STOPPABLE, NOT_PAUSABLE, IGNORES_SHUTDOWN)
        WIN32_EXIT_CODE    : 0  (0x0)
        SERVICE_EXIT_CODE  : 0  (0x0)
        CHECKPOINT         : 0x11
        WAIT_HINT          : 0x3e8

SERVICE_NAME: opendct
        TYPE               : 10  WIN32_OWN_PROCESS
        STATE              : 4  RUNNING
                                (STOPPABLE, NOT_PAUSABLE, ACCEPTS_SHUTDOWN)
        WIN32_EXIT_CODE    : 0  (0x0)
        SERVICE_EXIT_CODE  : 0  (0x0)
        CHECKPOINT         : 0x0
        WAIT_HINT          : 0x0
*/
/* 	Proccess stopped example:
SERVICE_NAME: opendct
        TYPE               : 10  WIN32_OWN_PROCESS
        STATE              : 3  STOP_PENDING
                                (STOPPABLE, NOT_PAUSABLE, ACCEPTS_SHUTDOWN)
        WIN32_EXIT_CODE    : 0  (0x0)
        SERVICE_EXIT_CODE  : 0  (0x0)
        CHECKPOINT         : 0x27
        WAIT_HINT          : 0x3e8

SERVICE_NAME: opendct
        TYPE               : 10  WIN32_OWN_PROCESS
        STATE              : 1  STOPPED
        WIN32_EXIT_CODE    : 0  (0x0)
        SERVICE_EXIT_CODE  : 0  (0x0)
        CHECKPOINT         : 0x0
        WAIT_HINT          : 0x0
*/
            for (String value : values) {
                if (value.contains("STATE")) {
                    if (value.contains("START_PENDING") || value.contains("RUNNING")) {
                        return SERVICE_RUNNING;
                    } else if (value.contains("STOPPED") || value.contains("STOP_PENDING")) {
                        return SERVICE_STOPPED;
                    }
                }
            }
        } else if (IS_LINUX) {
            values = getCommandValue("service " + linux + " status");
            for (String value : values) {
                if (value.contains("Active:")) {
                    if (value.contains(" active (running)")) {
                        return SERVICE_RUNNING;
                    } else if (value.contains(" inactive (dead)")) {
                        return SERVICE_STOPPED;
                    }
                }
            }
            values = getCommandValue("systemctl status " + linux);
/* Process running example:
  opendct.service - OpenDCT Digital Cable Tuner for SageTV
   Loaded: loaded (/usr/lib/systemd/system/opendct.service; enabled; vendor preset: disabled)
   Active: active (running) since Fri 2016-11-18 22:14:07 EST; 4 days ago
     Docs: https://github.com/enternoescape/opendct
  Process: 18167 ExecStop=/opt/opendct/service stop (code=exited, status=0/SUCCESS)
  Process: 18355 ExecStart=/opt/opendct/service start (code=exited, status=0/SUCCESS)
 Main PID: 18405 (wrapper)
*/
/* Process stopped example:
  opendct.service - OpenDCT Digital Cable Tuner for SageTV
   Loaded: loaded (/usr/lib/systemd/system/opendct.service; enabled; vendor preset: disabled)
   Active: inactive (dead) since Wed 2016-11-23 17:33:41 EST; 7s ago
     Docs: https://github.com/enternoescape/opendct
  Process: 4840 ExecStop=/opt/opendct/service stop (code=exited, status=0/SUCCESS)
  Process: 18355 ExecStart=/opt/opendct/service start (code=exited, status=0/SUCCESS)
 Main PID: 18405 (code=exited, status=0/SUCCESS)
*/
            for (String value : values) {
                if (value.contains("Active:")) {
                    if (value.contains("active")) {
                        return SERVICE_RUNNING;
                    } else if (value.contains("inactive")) {
                        return SERVICE_STOPPED;
                    }
                }
            }
        }
        return SERVICE_NOT_FOUND;
    }

    /**
     * Get the OpenDCT service status.
     * <p/>
     * {@link #SERVICE_NOT_FOUND} if the service does not exist on this computer.
     * {@link #SERVICE_RUNNING} if the service is currently running/starting.
     * {@link #SERVICE_STOPPED} if the service is currently stopped/stopping.
     *
     * @return The OpenDCT service status.
     */
    public static int opendctServiceStatus() {
        return serviceRunning("opendct");
    }

    public static void stopOpendctService() {
        if (IS_WINDOWS) {
            getCommandValue("sc stop opendct");
        } else if (IS_LINUX) {
            getCommandValue("service opendct stop");
            getCommandValue("systemctl stop opendct");
        }
    }

    public static void startOpendctService() {
        if (IS_WINDOWS) {
            getCommandValue("sc start opendct");
        } else if (IS_LINUX) {
            getCommandValue("service opendct start");
            getCommandValue("systemctl start opendct");
        }
    }

    private static class ReadOutput implements Runnable {
        private List<String> lines = new ArrayList<>();
        private boolean closed = false;
        private final InputStreamReader reader;
        private final String streamType;

        public ReadOutput(InputStream reader, String streamType) {
            this.reader = new InputStreamReader(reader);
            this.streamType = streamType;
        }

        public String[] getLines() {
            return lines.toArray(new String[lines.size()]);
        }

        public void setClosed() {
            closed = true;

            try {
                reader.close();
            } catch (IOException e) {}
        }

        @Override
        public void run() {
            char buffer[] = new char[2048];
            StringBuilder builder = new StringBuilder(2048);
            int readLen;

            while (!closed) {
                try {
                    readLen = reader.read(buffer, 0, buffer.length);
                } catch (IOException e) {
                    System.out.println("OpenDCT - ERROR: Exception reading " + streamType + " stream => ");
                    e.printStackTrace(System.out);
                    break;
                }

                if (readLen == -1) {
                    break;
                } else if (readLen == 0) {
                    continue;
                }

                for (int i = 0; i < readLen; i++) {
                    if (buffer[i] == '\n' || buffer[i] == '\r') {
                        if (builder.length() == 0) {
                            continue;
                        }

                        lines.add(builder.toString());
                        builder.setLength(0);
                        continue;
                    }

                    builder.append(buffer[i]);
                }
            }
        }
    }
}
