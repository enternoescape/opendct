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
import opendct.nanohttpd.pojo.JsonOption;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class NetworkPowerEventManger implements PowerEventListener, DeviceOptions {
    private static final Logger logger = LogManager.getLogger(NetworkPowerEventManger.class);
    public static NetworkPowerEventManger POWER_EVENT_LISTENER = new NetworkPowerEventManger();

    // Two minutes should be enough time to determine if we experiencing a problem. A value of 0
    // will disable the timeout.
    private int resumeNetworkTimeout = Math.max(
            0,
            Config.getInteger("pm.network.resume_timeout_ms", 240000)
    );

    private final static int startNetworkTimeout = Math.max(
            0,
            Config.getInteger("pm.network.start_retry", 120)
    );

    private static HashSet<String> currentInterfaceNames = new HashSet<String>();
    private HashSet<String> monitoredInterfaceNames = new HashSet<String>();

    static {
        // The network adapters are rarely available the instant this service starts up, and once
        // detection actually starts, it finishes every fast for most devices, so this delay does
        // not introduce any noticeable problems.
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {}

        boolean apipaPresent;
        int timeout = startNetworkTimeout;
        int cetonLimit = Math.min(timeout - 1, Config.getInteger("pm.network.infinitv_wait", 2));

        while (true) {
            try {
                Thread.sleep(1000);
                // This will print out the current interfaces when the program first starts.
                apipaPresent = discoverNetworkInterfaces(true, cetonLimit > 0, cetonLimit-- > 0);

                if (currentInterfaceNames.size() > 0) {
                    break;
                }

                if (!apipaPresent) {
                    logger.error("No network interfaces currently have an IP address. {} {}" +
                                    " remaining. Checking again in 1 second...",
                            timeout, timeout == 1 ? "attempt" : "attempts");
                }
            } catch (InterruptedException e) {
                timeout = 0;
            }

            if (timeout-- <= 0) {
                ExitCode.NO_NETWORK_INTERFACES.terminateJVM();
            }
        }
    }

    @Override
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
                                    " address for this program to work. The timeout between retry" +
                                    " attempts is 1-13 seconds depending on what interfaces are" +
                                    " discovered and their state when they are discovered."
                    )
            };
        } catch (DeviceOptionException e) {
            logger.error("{} => ", e.getMessage(), e);
        }

        // If this happens, it is a programmer mistake.
        return new DeviceOption[0];
    }

    @Override
    public void setOptions(JsonOption... deviceOptions) throws DeviceOptionException {
        for (JsonOption deviceOption : deviceOptions) {
            // Only apply properties we know about.
            if (deviceOption.getProperty().equals("pm.network.start_retry")) {
                // This covers all values that should not be changed instantly and makes the values persistent.
                Config.setJsonOption(deviceOption);
            }

            if (deviceOption.getProperty().equals("pm.network.resume_timeout_ms")) {
                Config.setJsonOption(deviceOption);
                try {
                    resumeNetworkTimeout = Math.max(0, Integer.parseInt(deviceOption.getValue()));
                } catch (NumberFormatException e) {
                    resumeNetworkTimeout = 240000;
                }
            }
        }
    }

    public static synchronized NetworkInterface[] getInterfaces() {
        discoverNetworkInterfaces(false, false, false);

        NetworkInterface returnValues[] = new NetworkInterface[currentInterfaceNames.size()];

        int i = 0;
        for (String networkInterface : currentInterfaceNames) {
            try {
                returnValues[i++] = NetworkInterface.getByName(networkInterface);
            } catch (SocketException e) {
                logger.error("Unable to find the interface '{}' => ", networkInterface, e);
            } catch (Throwable e) {
                logger.error("Unable to access the interface '{}'" +
                        " because of an unexpected exception => ", networkInterface, e);
            }
        }

        return returnValues;
    }

    public synchronized void addDependentInterface(String interfaceName) throws IOException {
        logger.entry(interfaceName);

        String interfaceLower = interfaceName.toLowerCase();

        if (!currentInterfaceNames.contains(interfaceLower)) {
            // Try updating the devices just in case we missed something when we first started the
            // program.
            discoverNetworkInterfaces(false, false, false);

            if (!currentInterfaceNames.contains(interfaceLower)) {
                throw new IOException("The interface '" + interfaceLower + "' does not exist on this computer.");
            }
        }

        monitoredInterfaceNames.add(interfaceLower);

        logger.exit();
    }

    public synchronized void addDependentInterface(InetAddress remoteAddress) throws IOException {
        logger.entry(remoteAddress);

        NetworkInterface networkInterface = null;
        try {
            networkInterface = Util.getNetworkInterfaceForRemoteIPAddress(remoteAddress);
        } catch (SocketException e) {
            throw new IOException(e);
        }

        if (networkInterface == null) {
            throw new IOException("An interface on the same subnet as '" +
                    remoteAddress.getHostAddress() + "' does not exist on this computer.");
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

    private static boolean discoverNetworkInterfaces(boolean printOutput, boolean failApipa, boolean waitCeton) {
        currentInterfaceNames.clear();
        StringBuilder msg =
                new StringBuilder("Network interfaces which are up and have an IP4 address are: ");

        Map<String, Boolean> infiniTVDevices = new HashMap<>();

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                String displayName = networkInterface.getDisplayName();
                if (displayName == null) {
                    continue;
                }

                // The InfiniTV 4 adapters can take a very long time to load, so when we see one of
                // those adapters, we will add to the waiting time in hopes that it will come online
                // in a reasonable amount of time. If there are multiple interfaces, they will have
                // #number appended (e.g. Ceton InfiniTV Network Device #2). There will be other
                // interfaces we are not interested in that might also start with Ceton InfiniTV
                // Network Device, so we need to limit the range that we are allowing this to match.
                if (Config.IS_WINDOWS) {
                    // If a bridge is in use, an IP address might not ever be assigned to the
                    // InfiniTV interfaces.
                    if (displayName.startsWith("Network Bridge")) {
                        infiniTVDevices.put("Network Bridge", true);
                    } else if (displayName.startsWith("Ceton InfiniTV Network Device")) {
                        int hashIndex = displayName.indexOf("#");
                        String name;
                        if (hashIndex != -1) {
                            int space = displayName.indexOf("-", hashIndex);
                            if (space == -1) {
                                space = displayName.indexOf(" ", hashIndex);
                            }
                            if (space == -1) {
                                space = displayName.length();
                            }
                            name = displayName.substring(0, space).trim();
                        } else {
                            name = "Ceton InfiniTV Network Device";
                        }
                        Boolean found = infiniTVDevices.get(name);
                        if (found == null || !found) {
                            infiniTVDevices.put(name, networkInterface.getInterfaceAddresses().size() > 0);
                        }
                    }
                }

                // Some adapters are never of interest, so we filter them out here.
                if (Config.IS_WINDOWS && "Npcap Loopback Adapter".equals(displayName)) {
                    continue;
                }

                if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                    List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();

                    for (InterfaceAddress interfaceAddr : addresses) {
                        InetAddress address = interfaceAddr.getAddress();

                        if (address instanceof Inet4Address) {
                            String hostAddress = address.getHostAddress();
                            if (failApipa && hostAddress.startsWith("169.254."))
                            {
                                logger.info("Extending wait for interface {}, APIPA address detected: {}",
                                        networkInterface, hostAddress);

                                // It will take longer than 250ms for DHCP to do it's thing and fix this problem.
                                Thread.sleep(2000);
                                currentInterfaceNames.clear();
                                return true;
                            }

                            currentInterfaceNames.add(networkInterface.getName().toLowerCase());
                            msg.append(Config.NEW_LINE)
                                    .append(networkInterface)
                                    .append(" ")
                                    .append(address.getHostAddress());
                            break;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            msg.append(e);
        }

        if (failApipa && waitCeton &&
                infiniTVDevices.size() > 0 &&
                !infiniTVDevices.containsKey("Network Bridge")) {
            for (Map.Entry<String, Boolean> entry : infiniTVDevices.entrySet()) {
                Boolean value = entry.getValue();
                if (value != null && !value) {
                    currentInterfaceNames.clear();
                    logger.info("InfiniTV device '{}' does not have an IP address. Extending wait...", entry.getKey());
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {}
                    return true;
                }
            }
        }

        if (printOutput && currentInterfaceNames.size() > 0) {
            logger.info(msg.toString());
        }

        return false;
    }

    private void waitForNetworkInterfaces() {
        // Wait a little before checking the interfaces.
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {}

        long lastCheck = System.currentTimeMillis();
        long timeout = resumeNetworkTimeout > 0 ?
                System.currentTimeMillis() + resumeNetworkTimeout : 0;
        List<String> remainingNames = new ArrayList<>(monitoredInterfaceNames.size());

        for (String monitoredInterfaceName : monitoredInterfaceNames) {
            remainingNames.add(monitoredInterfaceName);
        }

        logger.info("Waiting for network interfaces: {}", remainingNames);

        boolean keepPolling = true;

        while (keepPolling) {
            try {
                Enumeration<NetworkInterface> interfaceEnumeration = NetworkInterface.getNetworkInterfaces();

                while (interfaceEnumeration.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaceEnumeration.nextElement();

                    if (remainingNames.contains(networkInterface.getName().toLowerCase()) && !networkInterface.isLoopback() && networkInterface.isUp()) {
                        List<InterfaceAddress> iAddresses = networkInterface.getInterfaceAddresses();

                        for (InterfaceAddress iAddress : iAddresses) {
                            InetAddress address = iAddress.getAddress();

                            if (address instanceof Inet4Address) {
                                String address4 = address.getHostAddress();

                                if (address4.startsWith("169.254."))
                                {
                                    logger.info("Found network interface: {}. APIPA address detected: {}", networkInterface, address4);

                                    // It will take longer than 1000ms for DHCP to do it's thing and fix this problem.
                                    Thread.sleep(2000);
                                    continue;
                                }

                                remainingNames.remove(networkInterface.getName().toLowerCase());
                                logger.info("Found network interface: {}. Remaining network interfaces to find: {}.", networkInterface, remainingNames);
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                logger.warn("Error while waiting on network interfaces => ", e);
            }

            if (timeout != 0) {
                long thisCheck = System.currentTimeMillis();
                // If we have more than a 30 seconds difference between this check and the last
                // check, we are missing significant amounts of time. That most likely means we just
                // came out of standby.
                if (Math.abs(thisCheck - lastCheck) > 30000) {
                    logger.warn("This check was {} and the last check was {}. Restarting timer.", thisCheck, lastCheck);
                    timeout = thisCheck + resumeNetworkTimeout;
                    // Re-add interfaces so that they get checked a second time.
                    remainingNames.clear();
                    for (String monitoredInterfaceName : monitoredInterfaceNames) {
                        remainingNames.add(monitoredInterfaceName);
                    }
                }
                if (thisCheck > timeout) {
                    ExitCode.PM_NETWORK_RESUME.terminateJVM();
                    break;
                }
                lastCheck = thisCheck;
            }

            keepPolling = remainingNames.size() > 0;

            if (keepPolling) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.debug("waitForNetworkInterfaces was interrupted => {}", e);
                    ExitCode.PM_EXCEPTION.terminateJVM();
                }
            }
        }
    }
}
