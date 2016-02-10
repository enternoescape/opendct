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

package opendct.power;

import opendct.config.Config;
import opendct.config.ExitCode;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.DeviceOptions;
import opendct.config.options.IntegerDeviceOption;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;
import java.util.*;

public class NetworkPowerEventManger implements PowerEventListener, DeviceOptions {
    private static final Logger logger = LogManager.getLogger(NetworkPowerEventManger.class);
    public static NetworkPowerEventManger POWER_EVENT_LISTENER = new NetworkPowerEventManger();

    // Two minutes should be enough time to determine if we experiencing a problem. A value of 0
    // will disable the timeout.
    private int resumeNetworkTimeout = Math.max(
            0,
            Config.getInteger("pm.network.resume_timeout_ms", 120000)
    );

    private final static int startNetworkTimeout = Math.max(
            0,
            Config.getInteger("pm.network.start_retry", 120)
    );

    private static HashSet<String> currentInterfaceNames = new HashSet<String>();
    private HashSet<String> monitoredInterfaceNames = new HashSet<String>();

    static {
        // This will print out the current interfaces when the program first starts.
        getNetworkInterfaces(true);

        int timeout = startNetworkTimeout;

        while (currentInterfaceNames.size() == 0) {
            try {
                logger.error("No network interfaces currently have an IP address. {} {}" +
                        " remaining. Checking again in 1 second...",
                        timeout, timeout == 1 ? "attempt" : "attempts");
                Thread.sleep(1000);
                getNetworkInterfaces(true);
            } catch (InterruptedException e) {
                logger.debug("");
            }

            if (timeout-- <= 0) {
                ExitCode.NO_NETWORK_INTERFACES.terminateJVM();
            }
        }
    }

    public DeviceOption[] getOptions() {
        try {
            return new DeviceOption[]{
                    new IntegerDeviceOption(
                            resumeNetworkTimeout,
                            false,
                            "Resume Timeout",
                            "pm.network.resume_timeout_ms",
                            "This indicates how long in milliseconds after a resume event to wait" +
                                    " for network devices to become available before terminating" +
                                    " the program. Setting this value to 0 disables the timeout."
                    ),
                    new IntegerDeviceOption(
                            startNetworkTimeout,
                            false,
                            "Startup Retry Count",
                            "pm.network.start_retry",
                            "This indicates how many times the program will attempt to find a" +
                                    " usable network interface at one second intervals. At least" +
                                    " one network interface must be available and assigned an IP" +
                                    " address for this program to work."
                    )
            };
        } catch (DeviceOptionException e) {
            logger.error("{} => ", e.getMessage(), e);
        }

        // If this happens, it is a programmer mistake.
        return new DeviceOption[0];
    }

    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption deviceOption : deviceOptions) {
            // This covers all values that should not be changed instantly and makes the values persistent.
            Config.setDeviceOption(deviceOption);

            if (deviceOption.getProperty().equals("pm.network.resume_timeout_ms")) {
                try {
                    resumeNetworkTimeout = Math.max(0, Integer.parseInt(deviceOption.getValue()));
                } catch (NumberFormatException e) {
                    resumeNetworkTimeout = 120000;
                }
            }
        }
    }

    public static synchronized NetworkInterface[] getInterfaces() {
        getNetworkInterfaces(false);

        NetworkInterface returnValues[] = new NetworkInterface[currentInterfaceNames.size()];

        int i = 0;
        for (String networkInterface : currentInterfaceNames) {
            try {
                returnValues[i++] = NetworkInterface.getByName(networkInterface);
            } catch (SocketException e) {
                logger.error("Unable to find the interface '{}' => ", networkInterface, e);
            }
        }

        return returnValues;
    }

    public synchronized void addDependentInterface(String interfaceName) throws IllegalArgumentException {
        logger.entry(interfaceName);

        String interfaceLower = interfaceName.toLowerCase();

        if (!currentInterfaceNames.contains(interfaceLower)) {
            // Try updating the devices just in case we missed something when we first started the
            // program.
            getNetworkInterfaces(false);

            if (!currentInterfaceNames.contains(interfaceLower)) {
                throw new IllegalArgumentException("The interface '" + interfaceLower + "' does not exist on this computer.");
            }
        }

        monitoredInterfaceNames.add(interfaceLower);

        logger.exit();
    }

    public synchronized void addDependentInterface(InetAddress remoteAddress) throws IllegalArgumentException {
        logger.entry(remoteAddress);

        NetworkInterface networkInterface = null;
        try {
            networkInterface = Util.getNetworkInterfaceForRemoteIPAddress(remoteAddress);
        } catch (SocketException e) {
            throw new IllegalArgumentException(e);
        }

        if (networkInterface == null) {
            throw new IllegalArgumentException("An interface on the same subnet as '" + remoteAddress.getHostAddress() + "' does not exist on this computer.");
        }

        addDependentInterface(networkInterface.getName());

        logger.exit();
    }

    public void onSuspendEvent() {
        // We don't need to do anything to prepare for standby.
    }

    public void onResumeSuspendEvent() {
        waitForNetworkInterfaces();
    }

    public void onResumeCriticalEvent() {
        waitForNetworkInterfaces();
    }

    public void onResumeAutomaticEvent() {
        waitForNetworkInterfaces();
    }

    private static void getNetworkInterfaces(boolean printOutput) {
        currentInterfaceNames.clear();

        StringBuilder msg = new StringBuilder("Network interfaces which are up and have an IP4 address are: ");

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                    List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();

                    for (Iterator<InterfaceAddress> iter = addresses.iterator(); iter.hasNext(); ) {
                        InterfaceAddress interfaceAddr = iter.next();
                        InetAddress addr = interfaceAddr.getAddress();

                        if (addr instanceof Inet4Address) {
                            currentInterfaceNames.add(networkInterface.getName().toLowerCase());
                            msg.append(Config.NEW_LINE + networkInterface + " " + addr.getHostAddress());
                            break;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            msg.append(e);
        }

        if (printOutput && currentInterfaceNames.size() > 0) {
            logger.info(msg.toString());
        }
    }

    private void waitForNetworkInterfaces() {
        long timeout = System.currentTimeMillis() + resumeNetworkTimeout;
        ArrayList<String> remainingNames = new ArrayList<String>();

        for (String monitoredInterfaceName : monitoredInterfaceNames) {
            remainingNames.add(monitoredInterfaceName);
        }

        logger.info("Waiting for network interfaces: {}", remainingNames);

        while (remainingNames.size() > 0) {
            try {
                Enumeration<NetworkInterface> interfaceEnumeration = NetworkInterface.getNetworkInterfaces();

                while (interfaceEnumeration.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaceEnumeration.nextElement();

                    if (remainingNames.contains(networkInterface.getName().toLowerCase()) && !networkInterface.isLoopback() && networkInterface.isUp()) {
                        List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();

                        for (Iterator<InterfaceAddress> iter = addresses.iterator(); iter.hasNext(); ) {
                            InterfaceAddress interfaceAddr = iter.next();
                            InetAddress addr = interfaceAddr.getAddress();

                            if (addr instanceof Inet4Address) {
                                remainingNames.remove(networkInterface.getName().toLowerCase());
                                logger.info("Found network interface: {}. Remaining network interfaces to find: {}.", networkInterface, remainingNames);
                                break;
                            }
                        }
                    }
                }
            } catch (SocketException e) {
            }

            if (System.currentTimeMillis() > timeout && timeout != 0) {
                ExitCode.PM_NETWORK_RESUME.terminateJVM();
                break;
            }

            if (remainingNames.size() > 0) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    logger.debug("waitForNetworkInterfaces was interrupted => {}", e);
                    ExitCode.PM_EXCEPTION.terminateJVM();
                }
            }
        }
    }
}
