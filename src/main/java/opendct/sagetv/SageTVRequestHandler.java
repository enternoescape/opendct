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

    private long minTuningTime = Config.getLong("sagetv.min_tuning_time_ms", 500);
    private long lastTuningStartTime = 0;

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
            setThreadName(null, captureDevice.getEncoderName());
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
                        sendResponse("3.0\r\n");
                    } else if (lastRequest.startsWith("STOP")) {
                        if (lastRequest.contains(" ")) {
                            //It appears we can have more than one tuner on the same port.
                            String deviceName = lastRequest.substring(lastRequest.indexOf(' ') + 1);

                            //This is not a mistake.
                            CaptureDevice captureDevice = getVCaptureDeviceToPoolCaptureDevice(deviceName, true);

                            if (captureDevice != null) {
                                setThreadName(deviceName, captureDevice.getEncoderName());
                                captureDevice.stopEncoding();
                                unlockEncoder(captureDevice);
                            } else {
                                logger.error("SageTV requested the tuner '{}' and it does not exist at this time.", deviceName);
                            }

                            removeVCaptureDeviceToPoolCaptureDevice(deviceName);

                            setThreadName(deviceName, deviceName);

                            sendResponse("OK\r\n");
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
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            preRecording();

                            if (captureDevice.isReady()) {
                                boolean success = true;
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

                                    postRecording();
                                    sendResponse("OK\r\n");
                                } else {
                                    sendResponse("ERROR Device Start Failed\r\n");
                                    logger.error("Encoder device is unable to start.");
                                }
                            } else {
                                sendResponse("ERROR Device Not Ready\r\n");
                                logger.error("Encoder device is not ready.");
                            }
                        } else {
                            sendResponse("ERROR Invalid Input\r\n");
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
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            preRecording();

                            if (captureDevice.isReady()) {
                                boolean success;

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

                                    postRecording();
                                    sendResponse("OK\r\n");
                                } else {
                                    sendResponse("ERROR Device Start Failed\r\n");
                                    logger.error("Encoder device is unable to start.");
                                }
                            } else {
                                sendResponse("ERROR Device Not Ready\r\n");
                                logger.error("Encoder device is not ready.");
                            }
                        } else {
                            sendResponse("ERROR Invalid Input\r\n");
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
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            preRecording();

                            boolean success;

                            if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                logger.debug("Switching network encoder via upload ID '{}' to file name '{}'.", uploadID, filename);
                                success = captureDevice.switchEncoding(channel, filename, bufferSize, uploadID, socket.getInetAddress());
                            } else {
                                logger.debug("Switching network encoder to filename '{}'.", filename);
                                success = captureDevice.switchEncoding(channel, filename, bufferSize);
                            }

                            if (success) {
                                currentRecordFile = filename;
                                SageTVManager.setFilesByCaptureDevice(captureDevice, currentRecordFile);

                                if (uploadID != 0) {
                                    SageTVManager.setUploadIDByFilename(currentRecordFile, uploadID);
                                }

                                sendResponse("OK\r\n");
                            } else {
                                sendResponse("ERROR Device Switch Failed\r\n");
                                logger.error("Encoder device is unable to switch.");
                            }
                        } else {
                            sendResponse("ERROR Invalid Input\r\n");
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
                            setThreadName(vCaptureDevice, captureDevice.getEncoderName());
                            lockEncoder(captureDevice);
                            preRecording();

                            boolean success;
                            if (captureDevice.canEncodeUploadID() && uploadID != 0) {
                                logger.debug("Switching network encoder via upload ID '{}' to file name '{}'.", uploadID, filename);
                                success = captureDevice.switchEncoding(channel, filename, 0, uploadID, socket.getInetAddress());
                            } else {
                                logger.debug("Switching network encoder to filename '{}'.", filename);
                                success = captureDevice.switchEncoding(channel, filename, 0);
                            }

                            if (success) {
                                currentRecordFile = filename;
                                SageTVManager.setFilesByCaptureDevice(captureDevice, currentRecordFile);

                                if (uploadID != 0) {
                                    SageTVManager.setUploadIDByFilename(currentRecordFile, uploadID);
                                }

                                sendResponse("OK\r\n");
                            } else {
                                sendResponse("ERROR Device Switch Failed\r\n");
                                logger.error("Encoder device is unable to switch.");
                            }
                        } else {
                            sendResponse("ERROR Invalid Input\r\n");
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
                            sendResponse(captureDevice.getRecordedBytes() + "\r\n");
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

                            sendTraceResponse(captureDevice.getRecordedBytes() + "\r\n");
                        } else {
                            try {
                                sendTraceResponse(new java.io.File(getFilename).length() + "\r\n");
                            } catch (Exception e) {
                                logger.error("Unable to get the file size of '{}'.", getFilename);
                                sendTraceResponse("0\r\n");
                            }
                        }

                        //=============================================================================================
                        // NOOP
                        //=============================================================================================
                    } else if (lastRequest.equals("NOOP")) {
                        sendTraceResponse("OK\r\n");

                        //=============================================================================================
                        // TUNE (tunes a channel)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("TUNE ")) {
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring(5), "|");

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
                        }

                        sendResponse("OK\r\n");

                        //=============================================================================================
                        // AUTOTUNE (checks if channel is tunable or not)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("AUTOTUNE ")) {
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring(9), "|");

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

                        sendResponse((returnValue ? "OK\r\n" : "NO_SIGNAL\r\n"));

                        //=============================================================================================
                        // AUTOSCAN (checks if channel is tunable or not)
                        //=============================================================================================
                    } else if (lastRequest.startsWith("AUTOSCAN ")) {
                        StringTokenizer tokens = new StringTokenizer(lastRequest.substring(9), "|");

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

                        sendResponse((returnValue ? "OK\r\n" : "NO_SIGNAL\r\n"));

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

                        sendResponse((returnValue + "\r\n"));

                        //=============================================================================================
                        // PROPERTIES
                        //=============================================================================================
                    } else if (lastRequest.equals("PROPERTIES")) {

                        ArrayList<String> properties = SageTVManager.getAllTunerProperties(this);

                        out.write(properties.size() + "\r\n");
                        for (String property : properties) {
                            out.write(property + "\r\n");
                        }

                        out.flush();

                        logger.info("Sent PROPERTIES.");
                    } else if (lastRequest.equals("QUIT")) {
                        break;
                    } else {
                        logger.error("Unknown command: {}", lastRequest);
                    }

                } catch (IOException e) {
                    logger.error("The SageTV server has disconnected ungracefully => {}", e);
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

            // Setting the interrupt or the JVM might crash when using native code.
            Thread.currentThread().interrupt();
        } finally {

            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                logger.trace("Failed to close BufferedReader => {}", e);
            }

            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
                logger.trace("Failed to close OutputStreamWriter => {}", e);
            }

            try {
                if (socket != null && socket.isConnected()) {
                    socket.close();
                }
            } catch (Exception e) {
                logger.trace("Failed to close socket => {}", e);
            }
        }

        logger.exit();
    }

    private void setThreadName(String virtualDevice, String poolDevice) {

        if (virtualDevice == null && poolDevice == null) {
            return;
        } else if (Util.isNullOrEmpty(virtualDevice)) {
            virtualDevice = SageTVPoolManager.getPoolCaptureDeviceToVCaptureDevice(poolDevice);
        } else if (Util.isNullOrEmpty(poolDevice)) {
            poolDevice = SageTVPoolManager.getVCaptureDeviceToPoolCaptureDevice(virtualDevice);
        }

        String newThreadName;

        if ((virtualDevice != null && virtualDevice.equals(poolDevice)) || poolDevice == null) {
            newThreadName = "SageTVRequestHandler-" + Thread.currentThread().getId() + ":" + virtualDevice;
        } else if (virtualDevice == null) {
            newThreadName = "SageTVRequestHandler-" + Thread.currentThread().getId() + ":" + poolDevice + " > NoVirtualDevice";
        } else {
            newThreadName = "SageTVRequestHandler-" + Thread.currentThread().getId() + ":" + poolDevice + " > " + virtualDevice;
        }

        if (logger.isDebugEnabled()) {
            // This probably takes more time than it saves. This will only be checked in debug mode so
            // we don't keep seeing that the thread name changed when it didn't actually change.
            if (newThreadName.equals(Thread.currentThread().getName())) {
                return;
            }

            if (!lastRequest.startsWith("GET_FILE_SIZE ") || LOG_TRACE) {
                logger.debug("Renaming the thread '{}' to '{}'...", Thread.currentThread().getName(), newThreadName);
            }
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
     * Anything you want to happen before START or BUFFER goes here.
     * <p/>
     * This will only execute after the capture device has been determined to exist.
     */
    private void preRecording() {
        lastTuningStartTime = System.currentTimeMillis();
    }

    /**
     * Anything you want to happen before START or BUFFER returns OK goes here.
     * <p/>
     * This will only execute if the capture device successful started the recording.
     */
    private void postRecording() {
        long tuningSleep = minTuningTime - (System.currentTimeMillis() - lastTuningStartTime);
        if (tuningSleep > 0) {
            try {
                logger.debug("Recording returned sooner than {}ms. Sleeping {}ms...", minTuningTime, tuningSleep);
                Thread.sleep(tuningSleep);
            } catch (InterruptedException e) {
                logger.debug("postRecording was interrupted => ", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Prints the response out to the log and then sends the response to SageTV.\
     * <p/>
     * This will only log if we are logging trace specifically for this class.
     *
     * @param response This is the string to respond to SageTV.
     * @throws IOException Thrown if there is an I/O error.
     */
    private void sendTraceResponse(String response) throws IOException {
        out.write(response);
        out.flush();

        if (LOG_TRACE) {
            logger.trace("Replied: '{}'", response);
        }
    }

    /**
     * Prints the response out to the log and then sends the response to SageTV.\
     *
     * @param response This is the string to respond to SageTV.
     * @throws IOException Thrown if there is an I/O error.
     */
    private void sendResponse(String response) throws IOException {
        out.write(response);
        out.flush();

        if (response.startsWith("ERROR ")) {
            logger.error("SageTV sent: '{}', Replied: '{}'", lastRequest, response);
        } else {
            logger.debug("Replied: '{}'", response);
        }
    }

    private CaptureDevice getAndLockCaptureDevice(String deviceName, boolean wait) {
        String pCaptureDevice = SageTVPoolManager.getAndLockBestCaptureDevice(deviceName);

        if (pCaptureDevice != null) {
            return SageTVManager.getSageTVCaptureDevice(pCaptureDevice, wait);
        }

        return null;
    }

    private CaptureDevice getVCaptureDeviceToPoolCaptureDevice(String deviceName, boolean wait) {
        String pCaptureDevice = SageTVPoolManager.getVCaptureDeviceToPoolCaptureDevice(deviceName);

        if (pCaptureDevice != null) {
            return SageTVManager.getSageTVCaptureDevice(pCaptureDevice, wait);
        }

        return null;
    }

    private void removeVCaptureDeviceToPoolCaptureDevice(String deviceName) {
        SageTVPoolManager.removeCaptureDeviceMapping(deviceName);
    }
}
