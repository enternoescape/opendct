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
import opendct.config.Config;
import opendct.config.options.DeviceOptionException;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVUnloadedDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class SageTVManagerServlet extends HttpServlet {
    private static final Logger logger = LogManager.getLogger(SageTVManagerServlet.class);

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);

        // Get the capture devices.
        String captureDeviceNames[] = request.getParameterValues("capdev");
        if (captureDeviceNames == null) {
            captureDeviceNames = new String[0];
        }

        // Get the capture device parents.
        String captureDeviceParentNames[] = request.getParameterValues("cappar");
        if (captureDeviceParentNames == null) {
            captureDeviceParentNames = new String[0];
        }

        String unloadedDeviceNames[] = request.getParameterValues("unldev");
        if (unloadedDeviceNames == null) {
            unloadedDeviceNames = new String[0];
        }

        if (captureDeviceNames.length > 0 && captureDeviceParentNames.length > 0) {
            throw new ServletException("You cannot get or update the properties for more than one device grouping at a time.");
        } else if (captureDeviceNames.length > 0 && unloadedDeviceNames.length > 0) {
            throw new ServletException("You cannot get or update the properties for more than one device grouping at a time.");
        } else if (captureDeviceParentNames.length > 0 && unloadedDeviceNames.length > 0) {
            throw new ServletException("You cannot get or update the properties for more than one device grouping at a time.");
        }

        // Get the property. The current value is always returned even if we are setting the value.
        String properties[] = request.getParameterValues("p");
        if (properties == null) {
            properties = new String[0];
        }

        if (properties.length == 0) {
            response.sendError(404, "There are no properties to query.");
            return;
        }

        // Get the value to set. If there are no values to set, the request becomes strictly a get.
        String values[] = request.getParameterValues("v");
        if (values == null) {
            values = new String[0];
        }

        boolean setValue = !(values.length == 0);

        if (setValue && !(values.length == properties.length)) {
            response.sendError(404, "There needs to be a value for every property.");
            return;
        }

        for (int i = 0; i < properties.length; i++) {
            String property = properties[i];
            String value;
            if (setValue) {
                value = values[i];
            } else {
                value = null;
            }

            if (captureDeviceNames.length > 0) {
                try {
                    if (setValue) {
                        setToCaptureDevices(captureDeviceNames, property, value);
                    }

                    final String[] fromCaptureDevices = getFromCaptureDevices(captureDeviceNames, property);

                    for (String line: fromCaptureDevices) {
                        response.getWriter().println(line);
                    }
                } catch (DeviceOptionException e) {
                    response.sendError(404, e.toString());
                }
            } else if (captureDeviceParentNames.length > 0) {
                try {
                    if (setValue) {
                        setToCaptureDeviceParents(captureDeviceParentNames, property, value);
                    }


                } catch (DeviceOptionException e) {
                    response.sendError(404, e.toString());
                }
            } else if (unloadedDeviceNames.length > 0) {
                try {
                    if (setValue) {
                        setToUnloadedDevices(unloadedDeviceNames, property, value);
                    }


                } catch (DeviceOptionException e) {
                    response.sendError(404, e.toString());
                }
            } else {
                // When no device groups are specified it's treated as a general request.

            }
        }

        String returnUrl = request.getParameter("return");
        if (returnUrl != null) {
            response.sendRedirect(returnUrl);
        }
    }

    /**
     * Gets a property or result of a method by property name.
     *
     * @param captureDevices The capture devices to query.
     * @param property The property to return from each capture device.
     * @return An array of values.
     * @throws DeviceOptionException
     */
    public static String[] getFromCaptureDevices(String captureDevices[], String property) throws DeviceOptionException {
        logger.entry(captureDevices, property);
        String returnValues[] = new String[captureDevices.length];


        for (int i = 0; i < returnValues.length; i++) {
            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(captureDevices[i], false);

            if (captureDevice == null) {
                continue;
            }

            switch (property) {
                case "device_name":
                    returnValues[i] = captureDevice.getEncoderName();

                    break;
                case "always_force_external_unlock":
                    returnValues[i] = Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".always_force_external_unlock");

                    break;
                case "encoder_listen_port":
                    returnValues[i] = Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".encoder_listen_port");

                    break;
                case "encoder_merit":
                    returnValues[i] = String.valueOf(captureDevice.getMerit());

                    break;
                case "encoder_pool":
                    returnValues[i] = captureDevice.getEncoderPoolName();

                    break;
                case "fast_network_encoder_switch":
                    returnValues[i] = String.valueOf(captureDevice.canSwitch());

                    break;
                case "lineup":
                    returnValues[i] = captureDevice.getChannelLineup();

                    break;
                case "offline_scan":
                    returnValues[i] = String.valueOf(captureDevice.isOfflineScanEnabled());

                    break;
                case "external_locked":
                    returnValues[i] = String.valueOf(captureDevice.isExternalLocked());

                    break;
                case "internal_locked":
                    returnValues[i] = String.valueOf(captureDevice.isLocked());

                    break;
                case "last_channel":
                    returnValues[i] = captureDevice.getLastChannel();

                    break;
                case "record_filename":
                    returnValues[i] = captureDevice.getRecordFilename();

                    break;
                case "record_quality":
                    returnValues[i] = captureDevice.getRecordQuality();

                    break;
                case "broadcast_standard":
                    returnValues[i] = captureDevice.getBroadcastStandard().toString();

                    break;
                case "encode_filename":
                    returnValues[i] = String.valueOf(captureDevice.canEncodeFilename());

                    break;
                case "encode_uploadid":
                    returnValues[i] = String.valueOf(captureDevice.canEncodeUploadID());

                    break;
                case "copy_protection":
                    returnValues[i] = captureDevice.getCopyProtection().toString();

                    break;
                case "encoder_type":
                    returnValues[i] = captureDevice.getEncoderDeviceType().toString();

                    break;
                case "local_ip":
                    returnValues[i] = captureDevice.getLocalAddress().getHostAddress();

                    break;
                case "remote_ip":
                    returnValues[i] = captureDevice.getRemoteAddress().getHostAddress();

                    break;
                case "record_bytes":
                    returnValues[i] = String.valueOf(captureDevice.getRecordedBytes());

                    break;
                case "record_start":
                    returnValues[i] = String.valueOf(captureDevice.getRecordStart());

                    break;
                case "record_uploadid":
                    returnValues[i] = String.valueOf(captureDevice.getRecordUploadID());

                    break;
                case "signal_strength":
                    returnValues[i] = String.valueOf(captureDevice.getSignalStrength());

                    break;
                case "network_device":
                    returnValues[i] = String.valueOf(captureDevice.isNetworkDevice());

                    break;
                default:
                    throw new DeviceOptionException("The property '" + property + "' is not a valid get.", null);
            }
        }

        return logger.exit(returnValues);
    }

    /**
     * Applies a property to all capture devices.
     * <p/>
     * Note that some of these modifications will set the restart flag.
     *
     * @param captureDevices An array of the names of all capture devices to be updated.
     * @param property       The property name to update.
     * @param value          The value to set the property.
     */
    public static void setToCaptureDevices(String captureDevices[], String property, String value) throws DeviceOptionException {
        logger.entry(captureDevices, property, value);

        for (String captureDeviceName : captureDevices) {
            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(captureDeviceName, false);

            if (captureDevice == null) {
                continue;
            }

            // We check each value individually to try to control what can be changed and also
            // because many of them need to be handled in very different ways.
            switch (property) {
                case "device_name":
                    if (captureDevices.length > 1) {
                        throw new DeviceOptionException("Not allowed to rename multiple capture devices to the same name.", null);
                    }
                    if (property.contains(",")) {
                        throw new DeviceOptionException("Device names cannot contain commas.", null);
                    }

                    Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + "." + property, value);

                    //TODO: [js] Rename devices without needing to restart for changes to take effect.
                    Config.setRestartPending();

                    break;
                case "always_force_external_unlock":
                    if (value.equals("true") || value.equals("false")) {
                        captureDevice.setAlwaysForceExternalUnlock(value.equals("true"));
                    } else {
                        throw new DeviceOptionException("This is a boolean value that only accepts 'true' or 'false'.", null);
                    }

                    break;
                case "encoder_listen_port":
                    // Since we don't have any restrictions on what port has access to what
                    // collection of capture devices, all we actually need to do is open a new port.
                    int port = 0;
                    try {
                        port = Integer.valueOf(value);
                    } catch (NumberFormatException e) {
                        throw new DeviceOptionException(e, null);
                    }

                    // The old port will be closed the next time the program is restarted if no
                    // other devices are using it.
                    SageTVManager.addAndStartSocketServers(new int[]{port});
                    Config.setInteger("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".encoder_listen_port", port);

                    break;
                case "encoder_merit":
                    int merit = 0;
                    try {
                        merit = Integer.valueOf(value);
                    } catch (NumberFormatException e) {
                        throw new DeviceOptionException(e, null);
                    }

                    // This is thread-safe and will automatically re-sort the list of capture
                    // devices in the same pool by merit.
                    captureDevice.setMerit(merit);

                    break;
                case "encoder_pool":
                    // This is thread-safe and will automatically change the pool of the capture
                    // device if it is different. If the value is null or an empty string, it will
                    // remove the capture device from its current pool.
                    captureDevice.setEncoderPoolName(value);

                    break;
                case "fast_network_encoder_switch":
                    // This is only referenced when telling SageTV about the device capabilities.
                    // This does not change how the consumer behaves; this tells SageTV to not use
                    // the SWITCH command on this network encoder when set to false.
                    if (value.equals("true") || value.equals("false")) {
                        Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".fast_network_encoder_switch", value);
                    } else {
                        throw new DeviceOptionException("This is a boolean value that only accepts 'true' or 'false'.", null);
                    }

                    break;
                case "lineup":
                    // This will change the channel lineup and update the offline scan lineup
                    // accordingly.
                    captureDevice.setChannelLineup(value);

                    break;
                case "offline_scan":
                    if (value.equals("true") || value.equals("false")) {
                        captureDevice.setOfflineScan(value.equals("true"));
                    } else {
                        throw new DeviceOptionException("This is a boolean value that only accepts 'true' or 'false'.", null);
                    }
                    break;
                default:
                    throw new DeviceOptionException("The property '" + property + "' cannot be set.", null);
            }
        }

        logger.info("Set the property '{}' to the value '{}' for {}.{}", property, value, captureDevices, Config.isRestartPending() ? " Restart is pending." : "");

        logger.exit();
    }

    public static void setToCaptureDeviceParents(String captureDeviceParents[], String property, String value) throws DeviceOptionException {
        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();

        for (String captureDeviceParentName : captureDeviceParents) {
            for (CaptureDevice captureDevice : captureDevices) {
                if (!captureDevice.getEncoderParentName().equals(captureDeviceParentName)) {
                    continue;
                }

                // We check each value individually to try to control what can be changed and also
                // because many of them need to be handled in very different ways.
                switch (property) {
                    case "device_name":
                        if (captureDeviceParents.length > 1) {
                            throw new DeviceOptionException("Not allowed to rename multiple capture device parents to the same name.", null);
                        }
                        if (property.contains(",")) {
                            throw new DeviceOptionException("Device parent names cannot contain commas.", null);
                        }

                        Config.setString("sagetv.device.parent." + captureDevice.getEncoderUniqueHash() + "." + property, value);

                        //TODO: [js] Rename devices without needing to restart for changes to take effect.
                        Config.setRestartPending();

                        break;
                    case "local_ip_override":
                        // This will change the local IP address used the next time the capture
                        // device starts an encoding.
                        try {
                            captureDevice.setLocalAddress(InetAddress.getByName(value));
                        } catch (UnknownHostException e) {
                            throw new DeviceOptionException(e, null);
                        }
                    default:
                        throw new DeviceOptionException("The property '" + property + "' cannot be set.", null);
                }
            }
        }
    }

    public static void setToUnloadedDevices(String unloadedDeviceNames[], String property, String value) throws SocketException, DeviceOptionException {
        for (String unloadedDeviceName : unloadedDeviceNames) {
            SageTVUnloadedDevice unloadedDevice = SageTVManager.getUnloadedDevice(unloadedDeviceName);

            if (unloadedDevice == null) {
                continue;
            }

            switch (property) {
                case "load_device":
                    SageTVManager.addCaptureDevice(unloadedDeviceName);

                    break;
                default:
                    throw new DeviceOptionException("The property '" + property + "' cannot be set.", null);
            }

        }
    }
}
