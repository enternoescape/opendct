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

import opendct.channel.ChannelManager;
import opendct.channel.TVChannel;
import opendct.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

public class InfiniTVTuning {
    private static final Logger logger = LogManager.getLogger(InfiniTVTuning.class);

    public static boolean tuneChannel(String lineupName, String channel, String deviceAddress, int tunerNumber, boolean useVChannel, int retry) throws InterruptedException {
        logger.entry(channel, deviceAddress, tunerNumber);

        boolean returnValue = false;

        if (useVChannel) {
            // There is no need to look up the channel when a CableCARD is present.
            returnValue = tuneVChannel(channel, deviceAddress, tunerNumber, retry);
        } else {
            // This will only hang up temporarily when the channel map is still loading or being
            // refreshed.
            TVChannel tvChannel = ChannelManager.getChannel(lineupName, channel);

            if (tvChannel == null) {
                logger.error("The requested channel does not exist in the channel map.");
                return logger.exit(false);
            }

            try {
                // Check if the frequency is already correct.
                String currentFrequency = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "tuner", "Frequency") + "000";

                boolean frequencyTuned = currentFrequency.equals(tvChannel.getFrequency());
                int attempts = 20;

                while (!frequencyTuned) {
                    tuneFrequency(tvChannel, deviceAddress, tunerNumber, retry);

                    currentFrequency = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "tuner", "Frequency") + "000";
                    frequencyTuned = currentFrequency.equals(tvChannel.getFrequency());

                    if (attempts-- == 0 && !frequencyTuned) {
                        logger.error("The requested frequency cannot be tuned.");
                        return logger.exit(false);
                    } else if (!frequencyTuned) {
                        try {
                            // Sleep if the first request fails so we don't overwhelm the device
                            // with requests. Remember up to 6 of these kinds of request could
                            // happen at the exact same time.
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            logger.error("tuneChannel was interrupted while tuning to a frequency.");
                            return logger.exit(false);
                        }
                    }
                }

                attempts = 20;
                boolean programSelected = false;

                Thread.sleep(250);

                while (!programSelected) {
                    // If we are not already on the correct frequency, it takes the tuner a moment
                    // to detect the available programs. If you try to set a program before the list
                    // is available, it will fail. Normally this happens so fast, a sleep method
                    // isn't appropriate. We have a while loop to retry a few times if it fails.

                    tuneProgram(tvChannel, deviceAddress, tunerNumber, retry);

                    programSelected = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "mux", "ProgramNumber").equals(tvChannel.getProgram());
                    if (attempts-- == 0 && !programSelected) {
                        logger.error("The requested program cannot be selected.");
                        return logger.exit(false);
                    } else if (!programSelected) {
                        try {
                            // Sleep if the first request fails so we don't overwhelm the device
                            // with requests. Remember up to 6 of these kinds of request could
                            // happen at the exact same time.
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            logger.error("tuneChannel was interrupted while selecting a program.");
                            return logger.exit(false);
                        }
                    }
                }
                returnValue = true;
            /*} catch (InterruptedException e) {
                logger.debug("tuneChannel was interrupted while waiting setting the program.");*/
            } catch (IOException e) {
                logger.debug("tuneChannel was unable to get the current program value.");
            }
        }

        return logger.exit(returnValue);
    }

    public static boolean tuneVChannel(String vchannel, String deviceAddress, int tunerNumber, int retry) throws InterruptedException {
        logger.entry(vchannel, deviceAddress, tunerNumber);

        if (tunerNumber - 1 < 0) {
            logger.error("The tuner number cannot be less than 1.");
            return logger.exit(false);
        }
        String instanceId = "instance_id=" + String.valueOf(tunerNumber - 1);

        String channel = "channel=" + vchannel;

        boolean returnValue = postContent(deviceAddress, "/channel_request.cgi", retry, instanceId,
                channel);

        return logger.exit(returnValue);
    }

    public static boolean tuneVChannel(TVChannel tvChannel, String deviceAddress, int tunerNumber, int retry) throws InterruptedException {
        logger.entry(tvChannel, deviceAddress, tunerNumber);

        if (tunerNumber - 1 < 0) {
            logger.error("The tuner number cannot be less than 1.");
            return logger.exit(false);
        }
        String instanceId = "instance_id=" + String.valueOf(tunerNumber - 1);

        String channel = "channel=" + tvChannel.getChannel();

        boolean returnValue = postContent(deviceAddress, "/channel_request.cgi", retry, instanceId,
                channel);

        return logger.exit(returnValue);
    }

    public static boolean tuneFrequency(TVChannel tvChannel, String deviceAddress, int tunerNumber, int retry) throws InterruptedException {
        logger.entry(tvChannel, deviceAddress, tunerNumber);

        logger.info("Tuning frequency '{}'.", tvChannel.getFrequency());

        if (tunerNumber - 1 < 0) {
            logger.error("The tuner number cannot be less than 1.");
            return logger.exit(false);
        }
        String instanceId = "instance_id=" + String.valueOf(tunerNumber - 1);

        String frequency;

        if (tvChannel.getFrequency().length() < 3) {
            frequency = "frequency=" + tvChannel.getFrequency();
        } else {
            // Tuning on the InfiniTV always excludes the last 3 zeros in the frequency.
            frequency = "frequency=" + tvChannel.getFrequency().substring(0, tvChannel.getFrequency().length() - 3);
        }

        String modulation = null;
        if (tvChannel.getModulation().equals("QAM256")) {
            modulation = "modulation=2";
        } else if (tvChannel.getModulation().equals("QAM64")) {
            modulation = "modulation=0";
        } else if (tvChannel.getModulation().equals("NTSC-M")) {
            modulation = "modulation=4";
        } else if (tvChannel.getModulation().equals("8VSB")) {
            modulation = "modulation=6";
        } else {
            logger.error("Cannot get the modulation index value for POST.");
            return logger.exit(false);
        }

        String tuner = "tuner=1";
        String demod = "demod=1";
        String rstChnl = "rst_chnl=0";
        String forceTune = "force_tune=0";

        boolean returnValue = postContent(deviceAddress, "/tune_request.cgi", retry, instanceId,
                frequency, modulation, tuner, demod, rstChnl, forceTune);

        return logger.exit(returnValue);
    }

    public static boolean tuneProgram(TVChannel tvChannel, String deviceAddress, int tunerNumber, int retry) throws InterruptedException {
        logger.entry(deviceAddress, deviceAddress, tunerNumber);

        logger.info("Selecting program '{}'.", tvChannel.getProgram());

        if (tunerNumber - 1 < 0) {
            logger.error("The tuner number cannot be less than 1.");
            return logger.exit(false);
        }
        String instanceId = "instance_id=" + String.valueOf(tunerNumber - 1);

        String program = "program=" + tvChannel.getProgram();

        boolean returnValue = postContent(deviceAddress, "/program_request.cgi", retry, instanceId,
                program);

        return logger.exit(returnValue);
    }

    public static boolean startRTSP(String localIPAddress, int rtpStreamLocalPort, String deviceAddress, int tunerNumber) {
        logger.entry(localIPAddress, rtpStreamLocalPort, deviceAddress, tunerNumber);

        logger.info("Starting streaming from tuner number {} to local port '{}'.", tunerNumber, rtpStreamLocalPort);

        if (tunerNumber - 1 < 0) {
            logger.error("The tuner number cannot be less than 1.");
            return logger.exit(false);
        }

        try {
            String currentIP = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "diag", "Streaming_IP");
            String currentPort = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "diag", "Streaming_Port");
            String playback = InfiniTVStatus.getVar(deviceAddress, tunerNumber, "av", "TransportState");

            if (currentIP.equals(localIPAddress) &&
                    currentPort.equals(String.valueOf(rtpStreamLocalPort)) &&
                    playback.equals("PLAYING")) {

                logger.info("The IP address and port for RTP are already set.");
                return logger.exit(true);
            }
        } catch (IOException e) {
            logger.error("Unable to get the current ip address, transport state and streaming port => {}", e);
        }

        String instanceId = "instance_id=" + String.valueOf(tunerNumber - 1);

        String destIp = "dest_ip=" + localIPAddress;
        String destPort = "dest_port=" + rtpStreamLocalPort;
        String protocol = "protocol=0"; //RTP
        String start = "start=1"; // 1 = Started (0 = Stopped)

        boolean returnValue = postContent(deviceAddress, "/stream_request.cgi", instanceId,
                destIp, destPort, protocol, start);

        return logger.exit(returnValue);
    }

    public static boolean stopRTSP(String deviceAddress, int tunerNumber) {
        logger.entry(deviceAddress, tunerNumber);

        logger.info("Stopping streaming from tuner number {} at '{}'.", tunerNumber, deviceAddress);

        if (tunerNumber - 1 < 0) {
            logger.error("The tuner number cannot be less than 1.");
            return logger.exit(false);
        }

        String instanceId = "instance_id=" + String.valueOf(tunerNumber - 1);

        String destIp = "dest_ip=192.168.200.2";
        String destPort = "dest_port=8000";
        String protocol = "protocol=0"; //RTP
        String start = "start=0"; // 0 = Stopped (1 = Started)

        boolean returnValue = postContent(deviceAddress, "/stream_request.cgi", instanceId,
                destIp, destPort, protocol, start);

        return logger.exit(returnValue);
    }

    public static boolean postContent(String deviceAddress, String postPath, int retry, String... parameters) throws InterruptedException {
        retry = Math.abs(retry) + 1;

        for (int i = 0; i < retry; i++) {
            if (postContent(deviceAddress, postPath, parameters)) {
                return logger.exit(true);
            }

            logger.error("Unable to access device '{}', attempt number {}", deviceAddress, i);
            Thread.sleep(200);
        }

        return logger.exit(false);
    }

    public static boolean postContent(String deviceAddress, String postPath, String... parameters) {
        logger.entry(deviceAddress, postPath, parameters);

        StringBuilder postParameters = new StringBuilder();
        for (String parameter : parameters) {
            postParameters.append(parameter);
            postParameters.append("&");
        }

        if (postParameters.length() > 0) {
            postParameters.deleteCharAt(postParameters.length() - 1);
        }

        byte postBytes[];
        try {
            postBytes = postParameters.toString().getBytes(Config.STD_BYTE);
        } catch (UnsupportedEncodingException e) {
            logger.error("Unable to convert '{}' into bytes.", postParameters.toString());
            return logger.exit(false);
        }

        URL url = null;
        try {
            url = new URL("http://" + deviceAddress + postPath);
        } catch (MalformedURLException e) {
            logger.error("Unable to create a valid URL using 'http://{}{}'", deviceAddress, postPath);
            return logger.exit(false);
        }
        logger.info("Connecting to InfiniTV tuner using the URL '{}'", url);

        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            logger.error("Unable to open an HTTP connection => {}", e);
            return logger.exit(false);
        }

        httpURLConnection.setDoOutput(true);

        try {
            httpURLConnection.setRequestMethod("POST");
        } catch (ProtocolException e) {
            logger.error("Unable to change request method to POST => {}", e);
            return logger.exit(false);
        }

        httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        httpURLConnection.setRequestProperty("charset", Config.STD_BYTE.toLowerCase());
        httpURLConnection.setRequestProperty("Content-Length", String.valueOf(postBytes.length));

        DataOutputStream dataOutputStream = null;
        try {
            httpURLConnection.connect();
            dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());
            dataOutputStream.write(postBytes);
            dataOutputStream.flush();
        } catch (IOException e) {
            logger.error("Unable to write POST bytes => {}", e);
            return logger.exit(false);
        }

        String line = null;
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
            /*while ((line = bufferedReader.readLine()) != null) {
                //logger.debug("POST returned: {}", line);
            }*/

            dataOutputStream.close();
            bufferedReader.close();
        } catch (IOException e) {
            logger.error("Unable to read reply => {}", e);
            return logger.exit(false);
        }
        return logger.exit(true);
    }
}
