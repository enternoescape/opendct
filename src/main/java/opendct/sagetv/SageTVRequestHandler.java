/*
 * Copyright 2015 The OpenDCT Authors.
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
 
 /*
  * Portions of this file are sourced from the SageTV Open Source project.
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * https://github.com/google/sagetv/blob/master/java/sage/EncodingServer.java
 */
 

package opendct.sagetv;

import opendct.capture.CaptureDevice;
import opendct.capture.DCTCaptureDeviceImpl;
import opendct.config.Config;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class SageTVRequestHandler implements Runnable {
    private final Logger logger = LogManager.getLogger(SageTVRequestHandler.class);

    private final boolean LOG_TRACE = Config.getBoolean("sagetv.log_noop_and_size", false);

    private Socket socket;
    private CaptureDevice captureDevice = null;
    private String currentRecordFile = null;
    private BufferedReader in = null;
    private OutputStreamWriter out = null;
    private String lastRequest = null;

    public SageTVRequestHandler(Socket socket) {
        this.socket = socket;
    }

    public SageTVRequestHandler(Socket socket, CaptureDevice captureDevice) {
        this.socket = socket;
        this.captureDevice = captureDevice;
    }

    public int getRemotePort() {
        if (socket != null) {
            return socket.getPort();
        }
        return -1;
    }

    public int getLocalPort() {
        if (socket != null) {
            return socket.getLocalPort();
        }
        return -1;
    }

    public String getRemoteAddress() {
        if (socket != null) {
            return socket.getInetAddress().getHostAddress();
        }
        return "";
    }

    public String getLocalAddress() {
        if (socket != null) {
            return socket.getLocalAddress().getHostAddress();
        }
        return "";
    }

    public void run() {
        logger.entry();

        if (captureDevice != null) {
            setThreadName("Unknown", captureDevice.getEncoderName());
        }

        try {
            if (logger.isTraceEnabled()) {
                logger.trace("Starting connection to remote socket {}:{}", getRemoteAddress(), getRemotePort());
            }

            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                logger.error("Unable to start receiving data => {}", e);
                logger.exit();
                return;
            }

            try {
                out = new OutputStreamWriter(socket.getOutputStream());
            } catch (IOException e) {
                logger.error("Unable to start sending data => {}", e);
                logger.exit();
                return;
            }

            // Continue to read the commands returned by SageTV until the connection closes or we
            // get a blank line. Much of this is transcribed from the implementation in
            // EncodingServer.java
            lastRequest = null;

            while (!Thread.currentThread().isInterrupted()) {

                lastRequest = in.readLine();

                if (lastRequest == null || lastRequest.equals("")) {
                    break;
                }

                if (!lastRequest.equals("NOOP") && !lastRequest.startsWith("GET_FILE_SIZE ")) {
                    logger.debug("SageTV sent: '{}'", lastRequest);
                } else if (LOG_TRACE) {
                    logger.trace("SageTV sent: '{}'", lastRequest);
                }

                try {
                    //=============================================================================================
                    // VERSION
                    //=============================================================================================
                    if (lastRequest.equals("VERSION")) {
                        // We are all version 3.0 capture devices. There doesn't appear to be any
                        // value in distinguishing.
                        sendResponse("3.0");
                    } else if (lastRequest.startsWith("STOP")) {
                        if (lastRequest.contains(" ")) {
                            //It appears we can have more than one tuner on the same port.
                            String deviceName = lastRequest.substring(lastRequest.indexOf(' ') + 1);

                            //This is not a mistake.
                            CaptureDevice captureDevice = getVCaptureDeviceToPoolCaptureDevice(deviceName, true);

                            if (captureDevice != null) {
                                setThreadName(deviceName, captureDevice.getEncoderName());

                                if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                    SageTVTuningMonitor.stopMonitorRecording(captureDevice);
                                }

                                captureDevice.stopEncoding();
                                unlockEncoder(captureDevice);
                            } else {
                                logger.error("SageTV requested the tuner '{}' and it does not exist at this time.", deviceName);
                            }

                            removeVCaptureDeviceToPoolCaptureDevice(deviceName);

                            setThreadName(deviceName, deviceName);

                            sendResponse("OK");
                        } else {
                            if (captureDevice != null) {
                                captureDevice.stopEncoding();
                                unlockEncoder(captureDevice);
                            }
                        }
                        //=============================================================================================
                        // START
                        //                                Device Name                                UploadID  Chn 2*Sage.time()                        Filename                            Quality
                        // V3: START DCT-Ceton InfiniTV PCIe (xx-xx-xx-xx) Tuner 4 Digital TV Tuner|1295665805|502|2890245964968|R:\Recordings\WheelofFortune-AmericasGameWeek-1968967-0.ts|Great
                        // V3: START DCT-Ceton InfiniTV PCIe (xx-xx-xx-xx) Tuner 2 Digital TV Tuner|1496210288|502|2890247883508|R:\Recordings\WheelofFortune-AmericasGameWeek-1968967-0.ts|Great
                        // V3: START DCT-Ceton InfiniTV PCIe (xx-xx-xx-xx) Tuner 4 Digital TV Tuner|1723577771|502|2890248518360|R:\Recordings\WheelofFortune-AmericasGameWeek-1968967-0.ts|Great
                        //=============================================================================================
                    } else if (lastRequest.startsWith("START ")) {
                        currentRecordFile = null;
                        // Same for V3/V2 encoders because the input name is specified
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring(6), "|");
                        int uploadID = 0;

                        String vCaptureDevice = null;
                        if (tokens.countTokens() == 6) {
                            // V3 has upload file ID
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                            uploadID = Integer.parseInt(tokens.nextToken());
                        } else {
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                        }

                        String channel = tokens.nextToken();
                        // I guess this is to synchronize time with the server.
                        String stvTimeSync = tokens.nextToken();
                        String filename = tokens.nextToken();
                        String encoding = tokens.nextToken();

                        if (captureDevice != null) {
                            boolean success;

                            try {
                                setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                                lockEncoder(captureDevice);

                                if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                    // This is done to prevent a potential race condition if a
                                    // re-tune happens at the same time we are trying to tune into a
                                    // new channel.
                                    SageTVTuningMonitor.pauseMonitorRecording(captureDevice);
                                }

                                if (captureDevice.isReady()) {
                                    if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                        logger.debug("Starting network encoder via upload ID '{}' to file name '{}'.", uploadID, filename);
                                        success = captureDevice.startEncoding(channel, filename, encoding, 0, uploadID, socket.getInetAddress());
                                    } else {
                                        logger.debug("Starting network encoder to file name '{}'.", filename);
                                        success = captureDevice.startEncoding(channel, filename, encoding, 0);
                                    }

                                    if (success) {
                                        currentRecordFile = filename;
                                        SageTVManager.setFilesByCaptureDevice(captureDevice, currentRecordFile);

                                        if (uploadID != 0) {
                                            SageTVManager.setUploadIDByFilename(currentRecordFile, uploadID);
                                        }

                                        sendResponse("OK");

                                        if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                            if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                                SageTVTuningMonitor.monitorRecording(captureDevice, channel, encoding, 0, uploadID, socket.getInetAddress());
                                            } else {
                                                SageTVTuningMonitor.monitorRecording(captureDevice, channel, encoding, 0);
                                            }
                                        }
                                    } else {
                                        sendResponse("ERROR Device Start Failed");
                                        logger.error("Encoder device is unable to start.");
                                    }
                                } else {
                                    sendResponse("ERROR Device Not Ready");
                                    logger.error("Encoder device is not ready.");
                                }
                            } catch (Exception e) {
                                sendResponse("ERROR Device Start Failed");
                                logger.error("Unexpected exception while starting network encoder to filename '{}' => ", filename, e);
                            }
                        } else {
                            sendResponse("ERROR Invalid Input");
                            logger.error("Encoder device does not exist.");
                        }
                        //=============================================================================================
                        // BUFFER
                        //=============================================================================================
                    } else if (lastRequest.startsWith("BUFFER ")) {
                        currentRecordFile = null;
                        // Same for V3/V2 encoders because the input name is specified
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring(6), "|");
                        Integer uploadID = 0;

                        String vCaptureDevice = null;
                        if (tokens.countTokens() == 6) {
                            // V3 has upload file ID
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                            uploadID = Integer.parseInt(tokens.nextToken());
                        } else {
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                        }

                        String channel = tokens.nextToken();
                        long bufferSize = Long.parseLong(tokens.nextToken());
                        String filename = tokens.nextToken();
                        String encoding = tokens.nextToken();

                        if (captureDevice != null) {
                            boolean success;

                            try {
                                setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                                lockEncoder(captureDevice);

                                if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                    // This is done to prevent a potential race condition if a
                                    // re-tune happens at the same time we are trying to tune into a
                                    // new channel.
                                    SageTVTuningMonitor.pauseMonitorRecording(captureDevice);
                                }

                                if (captureDevice.isReady()) {
                                    if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                        logger.debug("Starting buffered network encoder via upload ID '{}' to file name '{}'.", uploadID, filename);
                                        success = captureDevice.startEncoding(channel, filename, encoding, bufferSize, uploadID, socket.getInetAddress());
                                    } else {
                                        logger.debug("Starting buffered network encoder to file name '{}'.", filename);
                                        success = captureDevice.startEncoding(channel, filename, encoding, bufferSize);
                                    }

                                    if (success) {
                                        currentRecordFile = filename;
                                        SageTVManager.setFilesByCaptureDevice(captureDevice, currentRecordFile);

                                        if (uploadID != 0) {
                                            SageTVManager.setUploadIDByFilename(currentRecordFile, uploadID);
                                        }

                                        sendResponse("OK");

                                        if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                            if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                                SageTVTuningMonitor.monitorRecording(captureDevice, channel, encoding, bufferSize, uploadID, socket.getInetAddress());
                                            } else {
                                                SageTVTuningMonitor.monitorRecording(captureDevice, channel, encoding, bufferSize);
                                            }
                                        }
                                    } else {
                                        sendResponse("ERROR Device Start Failed");
                                        logger.error("Encoder device is unable to start.");
                                    }
                                } else {
                                    sendResponse("ERROR Device Not Ready");
                                    logger.error("Encoder device is not ready.");
                                }
                            } catch (Exception e) {
                                sendResponse("ERROR Device Start Failed");
                                logger.error("Unexpected exception while starting buffered network encoder to filename '{}' => ", filename, e);
                            }
                        } else {
                            sendResponse("ERROR Invalid Input");
                            logger.error("Encoder device does not exist.");
                        }
                        //=============================================================================================
                        // BUFFER_SWITCH
                        //=============================================================================================
                    } else if (lastRequest.startsWith("BUFFER_SWITCH ")) {
                        currentRecordFile = null;
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring(7), "|");
                        Integer uploadID = 0;

                        String vCaptureDevice = null;
                        if (tokens.countTokens() == 4) {
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, true);
                            uploadID = Integer.parseInt(tokens.nextToken());
                        } else if (tokens.countTokens() == 3) {
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, true);
                        }

                        String channel = tokens.nextToken();
                        long bufferSize = Long.parseLong(tokens.nextToken());
                        String filename = tokens.nextToken();

                        if (captureDevice != null) {
                            boolean success;

                            try {
                                setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                                lockEncoder(captureDevice);

                                if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                    // This is done to prevent a potential race condition if a
                                    // re-tune happens at the same time we are trying to change the
                                    // file.
                                    SageTVTuningMonitor.pauseMonitorRecording(captureDevice);
                                }

                                if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                    logger.debug("Switching network encoder via upload ID '{}' to file name '{}'.", uploadID, filename);
                                    success = captureDevice.switchEncoding(channel, filename, bufferSize, uploadID, socket.getInetAddress());
                                } else {
                                    logger.debug("Switching network encoder to filename '{}'.", filename);
                                    success = captureDevice.switchEncoding(channel, filename, bufferSize);
                                }
                            } catch (Exception e) {
                                success = false;
                                logger.error("Unexpected exception while switching network encoder to filename '{}' => ", filename, e);
                            }

                            if (success) {
                                currentRecordFile = filename;
                                SageTVManager.setFilesByCaptureDevice(captureDevice, currentRecordFile);

                                if (uploadID != 0) {
                                    SageTVManager.setUploadIDByFilename(currentRecordFile, uploadID);
                                }

                                sendResponse("OK");

                                if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                    if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                        SageTVTuningMonitor.resumeMonitorRecording(captureDevice, uploadID, socket.getInetAddress());
                                    } else {
                                        SageTVTuningMonitor.resumeMonitorRecording(captureDevice);
                                    }
                                }
                            } else {
                                sendResponse("ERROR Device Switch Failed");
                                logger.error("Encoder device is unable to switch.");
                            }
                        } else {
                            sendResponse("ERROR Invalid Input");
                            logger.error("Encoder device does not exist.");
                        }
                        //=============================================================================================
                        // SWITCH
                        //=============================================================================================
                    } else if (lastRequest.startsWith("SWITCH ")) {
                        currentRecordFile = null;
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring(7), "|");
                        Integer uploadID = 0;

                        String vCaptureDevice = null;
                        if (tokens.countTokens() == 4) {
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, true);
                            uploadID = Integer.parseInt(tokens.nextToken());
                        } else if (tokens.countTokens() == 3) {
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, true);
                        }

                        String channel = tokens.nextToken();
                        String filename = tokens.nextToken();

                        if (captureDevice != null) {
                            boolean success;

                            try {
                                setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                                lockEncoder(captureDevice);

                                if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                    // This is done to prevent a potential race condition if a
                                    // re-tune happens at the same time we are trying to change the
                                    // file.
                                    SageTVTuningMonitor.pauseMonitorRecording(captureDevice);
                                }

                                if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                    logger.debug("Switching network encoder via upload ID '{}' to file name '{}'.", uploadID, filename);
                                    success = captureDevice.switchEncoding(channel, filename, 0, uploadID, socket.getInetAddress());
                                } else {
                                    logger.debug("Switching network encoder to filename '{}'.", filename);
                                    success = captureDevice.switchEncoding(channel, filename, 0);
                                }
                            } catch (Exception e) {
                                success = false;
                                logger.error("Unexpected exception while switching network encoder to filename '{}' => ", filename, e);
                            }

                            if (success) {
                                currentRecordFile = filename;
                                SageTVManager.setFilesByCaptureDevice(captureDevice, currentRecordFile);

                                if (uploadID != 0) {
                                    SageTVManager.setUploadIDByFilename(currentRecordFile, uploadID);
                                }

                                sendResponse("OK");

                                if (!(captureDevice instanceof DCTCaptureDeviceImpl)) {
                                    if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                        SageTVTuningMonitor.resumeMonitorRecording(captureDevice, uploadID, socket.getInetAddress());
                                    } else {
                                        SageTVTuningMonitor.resumeMonitorRecording(captureDevice);
                                    }
                                }
                            } else {
                                sendResponse("ERROR Device Switch Failed");
                                logger.error("Encoder device is unable to switch.");
                            }
                        } else {
                            sendResponse("ERROR Invalid Input");
                            logger.error("Encoder device does not exist.");
                        }
                        //=============================================================================================
                        // GET_START (return time in milliseconds since start of recording)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("GET_START")) {

                        String vCaptureDevice = null;
                        if (lastRequest.indexOf(' ') != -1) {
                            // V3 encoder
                            vCaptureDevice = lastRequest.substring(lastRequest.indexOf(' ') + 1);
                            captureDevice = getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, true);
                        }

                        if (captureDevice != null) {
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            captureDevice.getRecordStart();
                        }

                        //=============================================================================================
                        // GET_SIZE (return the size of a recording as the encoder sees it)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("GET_SIZE")) {

                        String vCaptureDevice = null;
                        if (lastRequest.indexOf(' ') != -1) {
                            // V3 encoder
                            vCaptureDevice = lastRequest.substring(lastRequest.indexOf(' ') + 1);
                            captureDevice = getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, true);
                        }

                        if (captureDevice != null) {
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            sendResponse(String.valueOf(captureDevice.getRecordedBytes()));
                        }

                        //=============================================================================================
                        // GET_FILE_SIZE (return the size of a file that might not currently be recording)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("GET_FILE_SIZE ")) {
                        String getFilename = lastRequest.substring("GET_FILE_SIZE ".length());

                        // Find the device capturing this file.
                        captureDevice = SageTVManager.getCaptureDeviceByFilename(getFilename);

                        if (captureDevice != null) {
                            setThreadName(null, captureDevice.getEncoderName());

                            sendTraceResponse(String.valueOf(captureDevice.getRecordedBytes()));
                        } else {
                            try {
                                sendTraceResponse(String.valueOf(new java.io.File(getFilename).length()));
                            } catch (Exception e) {
                                logger.error("Unable to get the file size of '{}'.", getFilename);
                                sendTraceResponse("0");
                            }
                        }

                        //=============================================================================================
                        // NOOP
                        //=============================================================================================
                    } else if (lastRequest.equals("NOOP")) {
                        sendTraceResponse("OK");

                        //=============================================================================================
                        // TUNE (tunes a channel)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("TUNE ")) {
                        /*StringTokenizer tokens = new StringTokenizer(lastRequest.substring(5), "|");

                        String vCaptureDevice = null;
                        if (tokens.countTokens() == 2) {
                            // V3 encoder
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                        }

                        String chanString = tokens.nextToken();

                        if (captureDevice != null) {
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            captureDevice.tuneToChannel(chanString);
                        }*/

                        logger.warn("SageTV requested '{}'.", lastRequest);
                        sendResponse("OK");


                        //=============================================================================================
                        // AUTOTUNE (checks if channel is tunable or not)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("AUTOTUNE ")) {
                        /*StringTokenizer tokens = new StringTokenizer(lastRequest.substring(9), "|");

                        String vCaptureDevice = null;
                        if (tokens.countTokens() == 2) {
                            // V3 encoder
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                        }

                        String chanString = tokens.nextToken();
                        Boolean returnValue = false;

                        if (captureDevice != null) {
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            returnValue = captureDevice.autoTuneChannel(chanString);
                        }

                        sendResponse((returnValue ? "OK" : "NO_SIGNAL"));*/

                        logger.warn("SageTV requested '{}'.", lastRequest);
                        sendResponse("OK");

                        //=============================================================================================
                        // AUTOSCAN (checks if channel is tunable or not)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("AUTOSCAN ")) {
                        /*StringTokenizer tokens = new StringTokenizer(lastRequest.substring(9), "|");

                        String vCaptureDevice = null;
                        if (tokens.countTokens() == 2) {
                            // V3 encoder
                            vCaptureDevice = tokens.nextToken();
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                        }

                        String chanString = tokens.nextToken();
                        boolean returnValue = false;

                        if (captureDevice != null) {
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            returnValue = captureDevice.autoScanChannel(chanString);
                        }

                        sendResponse((returnValue ? "OK" : "NO_SIGNAL"));*/

                        logger.warn("SageTV requested '{}'.", lastRequest);
                        sendResponse("OK");

                        //=============================================================================================
                        // AUTOINFOSCAN
                        //=============================================================================================
                    } else if (lastRequest.startsWith("AUTOINFOSCAN ")) {
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring("AUTOINFOSCAN ".length()), "|");

                        String vCaptureDevice = tokens.nextToken();
                        if (tokens.countTokens() == 2) {
                            // V3 encoder
                            captureDevice = getAndLockCaptureDevice(vCaptureDevice, true);
                        }

                        String chanString = tokens.nextToken();
                        String returnValue = "ERROR";

                        if (captureDevice != null) {
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            returnValue = captureDevice.scanChannelInfo(chanString);
                        }

                        sendResponse(returnValue);

                        //=============================================================================================
                        // PROPERTIES
                        //=============================================================================================
                    } else if (lastRequest.equals("PROPERTIES")) {

                        ArrayList<String> properties = SageTVManager.getAllTunerProperties(this);

                        StringBuilder stringBuilder = new StringBuilder();

                        for (String property : properties) {
                            stringBuilder.append(property).append("\r\n");
                        }

                        out.write(String.valueOf(properties.size()) + "\r\n");
                        out.write(stringBuilder.toString());
                        out.flush();

                        logger.info("Sent PROPERTIES.");
                    } else if (lastRequest.equals("QUIT")) {
                        break;
                    } else {
                        logger.error("Unknown command: {}", lastRequest);
                    }

                } catch (IOException e) {
                    logger.error("The SageTV server has disconnected ungracefully => ", e);
                    break;
                }

                //out.flush();
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Closing connection to {} on port {}", socket.getInetAddress().getHostAddress(), socket.getPort());
            }
        } catch (Exception e) {
            // This kind of exception appears to mostly happen when stopping the SageTV server.
            logger.debug("An unhandled exception was created => ", e);
        } catch (Throwable e) {
            logger.error("An unhandled throwable was created => ", e);
        } finally {

            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                logger.trace("Failed to close BufferedReader => ", e);
            }

            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
                logger.trace("Failed to close OutputStreamWriter => ", e);
            }

            try {
                if (socket != null && socket.isConnected()) {
                    socket.close();
                }
            } catch (Exception e) {
                logger.trace("Failed to close socket => ", e);
            }
        }

        logger.exit();
    }

    private void setThreadName(String virtualDevice, String poolDevice) {

        if (virtualDevice == null && poolDevice == null) {
            return;
        }

        if (SageTVPoolManager.isUsePools()) {
            if (Util.isNullOrEmpty(virtualDevice)) {
                virtualDevice = SageTVPoolManager.getPoolCaptureDeviceToVCaptureDevice(poolDevice);
            }

            if (Util.isNullOrEmpty(poolDevice)) {
                poolDevice = SageTVPoolManager.getVCaptureDeviceToPoolCaptureDevice(virtualDevice);
            }
        }

        if (virtualDevice != null && virtualDevice.endsWith(" Digital TV Tuner")) {
            virtualDevice = virtualDevice.substring(0, virtualDevice.length() - " Digital TV Tuner".length());
        }

        if (virtualDevice == null) {
            virtualDevice = "Unknown";
        }

        if (poolDevice != null && poolDevice.endsWith(" Digital TV Tuner")) {
            poolDevice = poolDevice.substring(0, poolDevice.length() - " Digital TV Tuner".length());
        }

        String newThreadName;

        if (!SageTVPoolManager.isUsePools() || virtualDevice.equals(poolDevice) || poolDevice == null) {
            newThreadName = "SageTVRequestHandler-" + Thread.currentThread().getId() + ":" + virtualDevice;
        } else if (virtualDevice.equals("Unknown")) {
            newThreadName = "SageTVRequestHandler-" + Thread.currentThread().getId() + ": NoVirtualDevice > " + poolDevice;
        } else {
            newThreadName = "SageTVRequestHandler-" + Thread.currentThread().getId() + ":" + virtualDevice + " > " + poolDevice;
        }

        Thread.currentThread().setName(newThreadName);
    }

    /**
     * Sets the exclusive lock on the capture device.
     * <p/>
     * This will quickly stop any offline activities being performed by the capture device and
     * prevent any further activities until the exclusive lock is removed.
     *
     * @param captureDevice The capture device to lock.
     */
    private void lockEncoder(CaptureDevice captureDevice) {
        captureDevice.setLocked(true);
    }

    /**
     * Clears the exclusive lock on the capture device.
     * <p/>
     * This will free the capture device up for offline activities such as channel scanning.
     *
     * @param captureDevice The capture device to unlock.
     */
    private void unlockEncoder(CaptureDevice captureDevice) {
        captureDevice.setLocked(false);
    }

    /**
     * Prints the response out to the log and then sends the response to SageTV.
     * <p/>
     * This will only log if we are logging trace specifically for this class.
     *
     * @param response This is the string to respond to SageTV.
     * @throws IOException Thrown if there is an I/O error.
     */
    private void sendTraceResponse(String response) throws IOException {
        out.write(response + "\r\n");
        out.flush();

        if (LOG_TRACE) {
            logger.trace("Replied: '{}'", response);
        }
    }

    /**
     * Prints the response out to the log and then sends the response to SageTV.
     *
     * @param response This is the string to respond to SageTV.
     * @throws IOException Thrown if there is an I/O error.
     */
    private void sendResponse(String response) throws IOException {
        out.write(response + "\r\n");
        out.flush();

        if (response.startsWith("ERROR ")) {
            logger.error("SageTV sent: '{}', Replied: '{}'", lastRequest, response);
        } else {
            logger.debug("Replied: '{}'", response);
        }
    }

    private CaptureDevice getAndLockCaptureDevice(String vCaptureDevice, boolean wait) {

        if (!SageTVPoolManager.isUsePools()) {
            return SageTVManager.getSageTVCaptureDevice(vCaptureDevice, wait);
        }

        String pCaptureDevice = SageTVPoolManager.getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice);

        if (pCaptureDevice == null) {
            pCaptureDevice = SageTVPoolManager.getAndLockBestCaptureDevice(vCaptureDevice);
        }

        while (pCaptureDevice == null) {
            boolean retry = false;

            try {
                retry = SageTVManager.blockUntilNextCaptureDeviceLoaded();
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for the next device to be detected => ", e);
            }

            pCaptureDevice = SageTVPoolManager.getAndLockBestCaptureDevice(vCaptureDevice);

            if (!retry) {
                break;
            }
        }

        if (pCaptureDevice != null) {
            return SageTVManager.getSageTVCaptureDevice(pCaptureDevice, wait);
        }

        return null;
    }

    private CaptureDevice getVCaptureDeviceToPoolCaptureDevice(String vCaptureDevice, boolean wait) {

        if (!SageTVPoolManager.isUsePools()) {
            return SageTVManager.getSageTVCaptureDevice(vCaptureDevice, wait);
        }

        String pCaptureDevice = SageTVPoolManager.getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice);

        if (pCaptureDevice != null) {
            return SageTVManager.getSageTVCaptureDevice(pCaptureDevice, wait);
        }

        return null;
    }

    private void removeVCaptureDeviceToPoolCaptureDevice(String deviceName) {
        if (SageTVPoolManager.isUsePools()) {
            SageTVPoolManager.removeCaptureDeviceMapping(deviceName);
        }
    }
}
