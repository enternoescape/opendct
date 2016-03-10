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

package opendct.tuning.http;

import opendct.channel.BroadcastStandard;
import opendct.channel.CopyProtection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class InfiniTVStatus {
    private static final Logger logger = LogManager.getLogger(InfiniTVStatus.class);
    private static final String DATA_START = "<body class=\"get\">";
    private static final String DATA_END = "</body></html>";

    /**
     * Get a parsed variable from an InfiniTV capture device with retry attempts.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @param service The service name to query.
     * @param value The value to query from the service.
     * @param retry Number of times to retry the query.
     * @return A string representation of the current value returned.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static String getVar(String deviceAddress, int tunerNumber, String service, String value, int retry) throws IOException, InterruptedException {
        IOException e0 = new IOException();

        retry = Math.abs(retry) + 1;

        for (int i = 0; i < retry; i++) {
            try {
                return getVar(deviceAddress, tunerNumber, service, value);
            } catch (IOException e) {
                e0 = e;
                logger.error("Unable to access device '{}', attempt number {}", deviceAddress, i);
                Thread.sleep(200);
            }
        }

        throw e0;
    }

    /**
     * Get a parsed variable from an InfiniTV capture device.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @param service The service name to query.
     * @param value The value to query from the service.
     * @return A string representation of the current value returned.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static String getVar(String deviceAddress, int tunerNumber, String service, String value) throws IOException {
        logger.entry(deviceAddress, tunerNumber, service, value);

        int tunerIndex = tunerNumber - 1;

        URL url = new URL("http://" + deviceAddress + "/get_var?i=" + tunerIndex + "&s=" + service + "&v=" + value);
        logger.debug("Connecting to InfiniTV tuner using the URL '{}'", url);

        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        InputStreamReader inputStreamReader = new InputStreamReader(httpURLConnection.getInputStream());
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        String line = bufferedReader.readLine();
        logger.debug("InfiniTV DCT returned the value '{}'", line);

        int start = line.indexOf(DATA_START);
        int end = line.indexOf(DATA_END);

        if (start > 0 && end > start) {
            line = line.substring(start + DATA_START.length(), end);
        }
        logger.debug("The returned value was trimmed to '{}'", line);

        return logger.exit(line);
    }

    /**
     * Get the currently selected program.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The currently selected program. The program will be -1 or 0 if no program is
     *         selected.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static int getProgram(String deviceAddress, int tunerNumber, int retry) throws IOException, InterruptedException {
        logger.entry(deviceAddress, tunerNumber);

        String value = getVar(deviceAddress, tunerNumber, "mux", "ProgramNumber", retry);

        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            logger.error("Unable to convert program string '{}' to number => ", value, e);
        }

        return -1;
    }

    /**
     * Get the currently selected program.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The currently selected program. The program will be -1 or 0 if no program is
     *         selected.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static int getProgram(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String value = getVar(deviceAddress, tunerNumber, "mux", "ProgramNumber");

        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            logger.error("Unable to convert program string '{}' to number => ", value, e);
        }

        return -1;
    }

    /**
     * Get the currently streaming PIDs.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return An array of all of the PIDs currently allowed to pass through the filter. The array
     *         may contain one element with the value 0 (PMT) even if no program is currently
     *         selected.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static int[] getPids(String deviceAddress, int tunerNumber, int retry) throws IOException, InterruptedException {
        logger.entry(deviceAddress, tunerNumber);

        String value = getVar(deviceAddress, tunerNumber, "mux", "PIDList", retry);

        String split[] = value.split(",");
        int pids[] = new int[split.length];

        try {
            for (int i = 0; i < pids.length; i++) {
                pids[i] = Integer.parseInt(split[i].trim(), 16);
            }
        } catch (NumberFormatException e) {
            logger.error("Unable to parse PID's '{}' => ", value, e);
            return new int[0];
        }

        return pids;
    }

    /**
     * Get the current copy protection status.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The current copy protection status in a CopyProtection enum.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static CopyProtection getCCIStatus(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String value = getVar(deviceAddress, tunerNumber, "diag", "CopyProtectionStatus");

        if (value.contains("None")) {
            return logger.exit(CopyProtection.NONE);
        } else if (value.contains("(00)") || value.contains("(0x00)")) {
            return logger.exit(CopyProtection.COPY_FREELY);
        } else if (value.contains("(0x02)")) {
            return logger.exit(CopyProtection.COPY_ONCE);
        } else if (value.contains("(0x03)")) {
            return logger.exit(CopyProtection.COPY_NEVER);
        }

        return logger.exit(CopyProtection.UNKNOWN);
    }

    @Deprecated
    public static int getSignalStrength(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String value = getVar(deviceAddress, tunerNumber, "diag", "Signal_Level");

        if (value.contains(" dBmV")) {
            String parseValue = value.substring(0, value.indexOf(" dBmV"));
            float signalStrength = -1;
            try {
                signalStrength = Float.parseFloat(parseValue);
                signalStrength = signalStrength * -10;
            } catch (NumberFormatException e) {
                logger.error("Unable to parse the value '{}' into a float.", parseValue);
            }
            return logger.exit((int) signalStrength);
        }

        return logger.exit(-1);
    }

    /**
     * Get the current signal level.
     * <p/>
     * The old method adjusts the value which doesn't do anything helpful.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The current signal level as a float in dBmV.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static float getSignalLevel(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String value = getVar(deviceAddress, tunerNumber, "diag", "Signal_Level");

        if (value.contains(" dBmV")) {
            String parseValue = value.substring(0, value.indexOf(" dBmV"));
            float signalStrength = -1;

            try {
                signalStrength = Float.parseFloat(parseValue);
            } catch (NumberFormatException e) {
                logger.error("Unable to parse the value '{}' into a float.", parseValue);
            }
            return logger.exit((int) signalStrength);
        }

        return logger.exit(-1);
    }

    /**
     * Get the current signal to noise ratio.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The current signal to noise ratio as a float in dB.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static float getSignalNoiseRatio(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String value = getVar(deviceAddress, tunerNumber, "diag", "Signal_SNR");

        if (value.contains(" dB")) {
            String parseValue = value.substring(0, value.indexOf(" dB"));
            float signalStrength = -1;

            try {
                signalStrength = Float.parseFloat(parseValue);
            } catch (NumberFormatException e) {
                logger.error("Unable to parse the value '{}' into a float.", parseValue);
            }
            return logger.exit((int) signalStrength);
        }

        return logger.exit(-1);
    }

    /**
     * Get the current RTP streaming address.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return A string representation of the IP address RTP is currently set to stream.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static String getStreamingIP(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String currentIP = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "diag", "Streaming_IP");

        return logger.exit(currentIP);
    }

    /**
     * Get the current RTP streaming port.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The port number currently set for RTP streaming.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static int getStreamingPort(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String currentPort = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "diag", "Streaming_Port");
        int returnValue = -1;

        try {
            returnValue = Integer.valueOf(currentPort.trim());
        } catch (NumberFormatException e) {
            logger.error("Unable to parse the value '{}' into an integer.", currentPort);
        }

        return logger.exit(returnValue);
    }

    /**
     * Get the device RTP transport state.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return A string representation of the current transport state. Known values are STOPPED and
     *         PLAYING
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static String getTransportState(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String playback = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "av", "TransportState");

        return logger.exit(playback);
    }

    /**
     * Get the device temperature.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The current temperature as a floating point.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static float getTemperature(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String value = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "diag", "Temperature");

        if (value.contains(" C")) {
            String parseValue = value.substring(0, value.indexOf(" C"));
            float temperature = -1;
            try {
                temperature = Float.parseFloat(parseValue);
            } catch (NumberFormatException e) {
                logger.error("Unable to parse the value '{}' into a float.", parseValue);
            }
            return logger.exit(temperature);
        }

        return logger.exit(-1);
    }

    /**
     * Get the carrier lock.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return A string representation of the response. In this case, it should be a 0 or 1.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static String getCarrierLock(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String carrierLock = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "tuner", "CarrierLock");

        return logger.exit(carrierLock);
    }

    /**
     * Get the current PCR Lock.
     * <p/>
     * This is also known as Digital Lock.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return A string representation of the response. In this case, it should be a 0 or 1.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static String getPCRLock(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String carrierLock = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "tuner", "PCRLock");

        return logger.exit(carrierLock);
    }

    /**
     * Get the current modulation in use.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return A BroadcastStandard enum with the current modulation in use.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static BroadcastStandard getModulation(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String modulation = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "tuner", "Modulation").toUpperCase();
        BroadcastStandard returnValue = BroadcastStandard.QAM256;

        if (modulation.contains("QAM256")) {
            returnValue = BroadcastStandard.QAM256;
        } else if (modulation.contains("QAM64")) {
            returnValue = BroadcastStandard.QAM64;
        }

        return logger.exit(returnValue);
    }

    /**
     * Get the current modulation in use as a string.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return A string representation of the response.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static String getModulationString(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String modulation = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "tuner", "Modulation").toUpperCase();

        return logger.exit(modulation);
    }

    /**
     * Get the device temperature.
     *
     * @param deviceAddress The IP/hostname of the capture device.
     * @param tunerNumber The tuner number to query.
     * @return The current frequency. -1 will be returned if the frequency was not a number. 0 will
     *         be returned if no frequency is currently tuned.
     * @throws IOException Thrown if the capture device was unreachable.
     */
    public static int getFrequency(String deviceAddress, int tunerNumber) throws IOException {
        logger.entry(deviceAddress, tunerNumber);

        String parseValue = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "tuner", "Frequency").trim();

        int returnValue = -1;

        try {
            returnValue = Integer.parseInt(parseValue);

            // The frequency returned is always in kHz and it needs to be Hz;
            if (returnValue > 0) {
                returnValue *= 1000;
            }
        } catch (NumberFormatException e) {
            logger.error("Unable to parse the value '{}' into an int.", parseValue);
        }

        return logger.exit(returnValue);
    }
}
