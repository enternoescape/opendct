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
import opendct.capture.CaptureDeviceInitException;
import opendct.capture.CaptureDeviceType;
import opendct.channel.ChannelManager;
import opendct.config.Config;
import opendct.config.ExitCode;
import opendct.power.PowerEventListener;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SageTVManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(SageTVManager.class);
    public static final PowerEventListener POWER_EVENT_LISTENER = new SageTVManager();
    private AtomicBoolean suspend = new AtomicBoolean(false);

    private static AtomicBoolean broadcasting = new AtomicBoolean(false);
    private static SageTVDevicesLoaded devicesWaitingThread = null;

    public static final int MPEG_PURE_CAPTURE_MASK = 0x2000;
    public static final int MPEG_LIVE_PREVIEW_MASK = 0x1000;

    private static final ReentrantReadWriteLock portToSocketServerLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock captureDeviceNameToCaptureDeviceLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock captureDeviceToFilesLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock fileToUploadIDLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock fileToSocketServerLock = new ReentrantReadWriteLock();

    private static final HashMap<Integer, SageTVSocketServer> portToSocketServer = new HashMap<Integer, SageTVSocketServer>();
    private static final HashMap<String, CaptureDevice> captureDeviceNameToCaptureDevice = new HashMap<String, CaptureDevice>();
    private static final HashMap<Integer, CaptureDevice> captureDeviceIdToCaptureDevice = new HashMap<Integer, CaptureDevice>();
    private static final HashMap<CaptureDevice, String> captureDeviceToFiles = new HashMap<CaptureDevice, String>();
    private static final HashMap<String, Integer> fileToUploadID = new HashMap<String, Integer>();
    private static final HashMap<String, SageTVSocketServer> fileToSocketServer = new HashMap<String, SageTVSocketServer>();

    public static boolean isBroadcasting() {
        return broadcasting.get();
    }

    /**
     * Add a capture device and start listening to the SageTV server.
     * <p/>
     * Before calling this method, you should ensure that you are not adding a duplicate device. If
     * you do add a duplicate device, the JVM will terminate with the assumption there has been an
     * error and the capture devices may be in an indeterminable state.
     *
     * @param captureDevice This is the already configured and ready for use capture device.
     * @throws SocketException If the SocketServer was unable to be open the requested port. If the
     *                         tuner is sharing a port with another tuner that has already started,
     *                         you should never see this error.
     */
    public static void addCaptureDevice(CaptureDevice captureDevice) throws SocketException {
        logger.entry(captureDevice);

        // Returns either the saved port for this capture device if it is already in properties or
        // an unused/shared port. This static method is thread-safe and designed to guarantee that
        // two socket servers will not unintentionally use the same port.
        int newPort = Config.getSocketServerPort(
                captureDevice.getEncoderUniqueHash());

        if (newPort == 0) {
            throw new SocketException("There are no available ports within the provided range. The tuner cannot start.");
        }

        boolean failure = false;
        SageTVSocketServer socketServer = null;
        boolean portInUse = false;

        portToSocketServerLock.writeLock().lock();
        captureDeviceNameToCaptureDeviceLock.writeLock().lock();

        try {
            if (captureDeviceNameToCaptureDevice.get(captureDevice.getEncoderName()) != null) {
                logger.error("A capture device with the name '{}' already exists.",
                        captureDevice.getEncoderName());

                throw new CaptureDeviceInitException("Duplicate capture device.");
            }

            //Check to see if a socket server is already running with this port.
            socketServer = portToSocketServer.get(newPort);
            portInUse = !(socketServer == null);

            if (!portInUse) {
                socketServer = new SageTVSocketServer(newPort, captureDevice);
            }

            portToSocketServer.put(newPort, socketServer);
            captureDeviceNameToCaptureDevice.put(captureDevice.getEncoderName(), captureDevice);
            captureDeviceIdToCaptureDevice.put(captureDevice.getEncoderUniqueHash(), captureDevice);

            if (!Util.isNullOrEmpty(captureDevice.getEncoderPoolName()) &&
                    SageTVPoolManager.isUsePools()) {

                SageTVPoolManager.addPoolCaptureDevice(
                        captureDevice.getEncoderPoolName(),
                        captureDevice.getEncoderName());
            }

            logger.info("The capture device '{}' is ready.", captureDevice.getEncoderName());

            // Count down the devices loaded timeout thread.
            devicesWaitingThread.deviceAdded();

            if (Config.isConfigOnly()) {
                buildTunerProperty(
                        captureDevice.getEncoderName(),
                        captureDevice.getEncoderUniqueHash(),
                        "",
                        0,
                        captureDevice.canSwitch()
                );

            } else if (!SageTVDiscovery.isRunning()) {
                SageTVDiscovery.startDiscoveryBroadcast(newPort);
            }
        } catch (CaptureDeviceInitException e) {
            logger.debug("Unable to load capture device => ", e);
            failure = true;
        } catch (Exception e) {
            failure = true;
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            portToSocketServerLock.writeLock().unlock();
            captureDeviceNameToCaptureDeviceLock.writeLock().unlock();
        }

        if (failure) {
            // We can't kill the JVM within a lock because the lock will not be released.
            ExitCode.SAGETV_DUPLICATE.terminateJVM();
        } else if (!portInUse) {
            if (!Config.isConfigOnly()) {
                // This can kill the JVM, so we start listening outside of the lock.
                socketServer.startListening();
            }
        }

        if (!Util.isNullOrEmpty(captureDevice.getEncoderPoolName()) &&
                SageTVPoolManager.isUsePools()) {

            SageTVPoolManager.resortMerits(captureDevice.getEncoderPoolName());
        }

        if (captureDevice.isOfflineChannelScan()) {

            ChannelManager.addDeviceToOfflineScan(
                    captureDevice.getChannelLineup(),
                    captureDevice.getEncoderName());
        }

        logger.exit();
    }

    public static void removeCaptureDevice(int captureDeviceId) {
        logger.entry(captureDeviceId);

        CaptureDevice captureDevice = null;

        captureDeviceNameToCaptureDeviceLock.writeLock().lock();
        captureDeviceToFilesLock.writeLock().lock();

        try {
            captureDevice = captureDeviceIdToCaptureDevice.get(captureDeviceId);

            if (captureDevice == null) {
                logger.exit();
                return;
            }

            try {
                logger.info("The capture device '{}' is being unloaded.", captureDevice.getEncoderName());
                // This should cease all offline activities.
                captureDevice.setLocked(true);
                captureDevice.stopDevice();
            } catch (Exception e) {
                logger.error("The capture device '{}' did not stop gracefully.",
                        captureDevice.getEncoderName());
            }

            captureDeviceIdToCaptureDevice.remove(captureDeviceId);
            captureDeviceNameToCaptureDevice.remove(captureDevice.getEncoderName());
            captureDeviceToFiles.remove(captureDevice);

        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceNameToCaptureDeviceLock.writeLock().unlock();
            captureDeviceToFilesLock.writeLock().lock();
        }

        if (captureDevice != null) {
            ChannelManager.removeDeviceFromOfflineScan(
                    captureDevice.getChannelLineup(),
                    captureDevice.getEncoderName());

            SageTVPoolManager.removePoolCaptureDevice(
                    captureDevice.getEncoderName());
        }

        logger.exit();
    }

    /**
     * This tells all of the SageTV socket server threads to close the listening port and stop
     * running.
     * <p/>
     * This will stop all communication with SageTV. It will leave the capture devices unchanged.
     * That means if it was streaming, it will continue to stream until told to do otherwise. It
     * also does not clear the socket servers. You can use <b>resumeAllSocketServers()</b> to
     * re-start the socket servers.
     */
    public static void stopAllSocketServers() {
        SageTVTuningMonitor.stopMonitor();

        ArrayList<SageTVSocketServer> stvSocketServers = getAllSageTVSocketServers();

        for (SageTVSocketServer stvSocketServer : stvSocketServers) {
            try {
                // This is blocking.
                stvSocketServer.stopListening();
            } catch (Exception e) {
                logger.error("An unexpected exception occurred while stopping a SageTV Socket Server => ", e);
            }
        }
    }

    /**
     * This tells all of the SageTV socket server threads start back up.
     * <p/>
     * This will resume all communication with SageTV. Normally this would be called after a suspend
     * event and should not be used unless <b>stopAllSocketServers()</b> was called earlier.
     */
    public static void resumeAllSocketServers() {
        ArrayList<SageTVSocketServer> stvSocketServers = getAllSageTVSocketServers();

        for (SageTVSocketServer stvSocketServer : stvSocketServers) {
            try {
                // This is non-blocking.
                stvSocketServer.startListening();
            } catch (Exception e) {
                logger.error("An unexpected exception occurred while starting a SageTV Socket Server => ", e);

            }
        }
    }

    /**
     * Add and open sockets to listen on for requests.
     * <p/>
     * This method will not attempt to open an sockets that are already assigned.
     *
     * @param ports A list of sockets to be added.
     */
    public static void addAndStartSocketServers(int ports[]) {
        logger.entry(Arrays.toString(ports));

        for (int port : ports) {
            portToSocketServerLock.writeLock().lock();

            SageTVSocketServer stvSocketServer = null;

            try {
                ArrayList<SageTVSocketServer> sageTVSocketServers = new ArrayList<>();

                if (portToSocketServer.get(port) == null) {
                    stvSocketServer = new SageTVSocketServer(port, null);
                    portToSocketServer.put(port, stvSocketServer);
                }
            } catch (Exception e) {
                logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
            } finally {
                portToSocketServerLock.writeLock().unlock();
            }

            // If we do this within the lock and it crashes the JVM, everything will lock up because
            // portToSocketServerLock doesn't get unlocked.
            if (stvSocketServer != null) {
                stvSocketServer.startListening();
            }
        }

        logger.exit();
    }

    /**
     * This tells all of the capture devices to clean up and stop/disconnect from their associated
     * devices.
     * <p/>
     * Before calling this method, all of the socket server threads should be stopping. This can run
     * even while they spin down since any request made will likely not be able to successfully find
     * a capture device once all of the requested locks are in place and the HashMaps are cleared.
     * Also be sure that anything that adds capture devices is not running since that might start
     * populating this list and end up firing up a new SageTV Socket Server right after this method
     * has completed.
     */
    public static void stopAndClearAllCaptureDevices() {
        if (devicesWaitingThread.isAlive()) {
            devicesWaitingThread.interrupt();
        }

        // This only needs a read lock. Since it uses captureDeviceToSocketServerLock and we can't
        // upgrade to a write lock, that create a problem.
        ArrayList<CaptureDevice> captureDevices = getAllSageTVCaptureDevices();

        // This will prevent the SageTV Socket Server from looking up capture devices until we are
        // done. That basically means the SageTV Socket Server will not be able to locate any
        // capture devices once this method completes which is what we want.
        captureDeviceNameToCaptureDeviceLock.writeLock().lock();

        try {
            for (CaptureDevice captureDevice : captureDevices) {
                if (captureDevice != null) {
                    try {
                        logger.info("The capture device '{}' is being unloaded.", captureDevice.getEncoderName());
                        // This should cease all offline activities.
                        captureDevice.setLocked(true);
                        captureDevice.stopDevice();
                    } catch (Exception e) {
                        logger.error("The capture device '{}' did not stop gracefully.",
                                captureDevice.getEncoderName());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("An unexpected error occurred while stopping and clearing all of the capture devices => ", e);
        } finally {

            // We need all of these exclusive locks or there could be trouble. If only there was a
            // way to say this lock is more important than all of the other ones since this is
            // likely to be called when entering standby.
            captureDeviceToFilesLock.writeLock().lock();
            fileToUploadIDLock.writeLock().lock();
            fileToSocketServerLock.writeLock().lock();

            try {
                captureDeviceNameToCaptureDevice.clear();
                captureDeviceIdToCaptureDevice.clear();
                captureDeviceToFiles.clear();
                fileToUploadID.clear();
                fileToSocketServer.clear();
            } finally {
                captureDeviceToFilesLock.writeLock().unlock();
                fileToUploadIDLock.writeLock().unlock();
                fileToSocketServerLock.writeLock().unlock();
            }

            captureDeviceNameToCaptureDeviceLock.writeLock().unlock();
        }
    }

    /**
     * Returns an array containing all currently available SageTV socket servers.
     * <p/>
     * There are no guarantees that the returned socket servers are running or not. The only
     * guarantee is that there are no duplicate ports. It is not recommended to modify the socket
     * servers returned from the method unless you know what the implications your actions could
     * have on the stability of the socket server.
     *
     * @return All currently available SageTV socket servers.
     */
    public static ArrayList<SageTVSocketServer> getAllSageTVSocketServers() {
        logger.entry();

        ArrayList<SageTVSocketServer> sageTVSocketServers = new ArrayList<SageTVSocketServer>();

        portToSocketServerLock.readLock().lock();

        try {
            for(Map.Entry<Integer, SageTVSocketServer> keyValuePair : portToSocketServer.entrySet()) {
                if (keyValuePair.getValue() != null) {
                    sageTVSocketServers.add(keyValuePair.getValue());
                }
            }
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            portToSocketServerLock.readLock().unlock();
        }

        return logger.exit(sageTVSocketServers);
    }

    public static CaptureDevice getSageTVCaptureDevice(int deviceId) {
        logger.entry(deviceId);

        CaptureDevice captureDevice = null;

        captureDeviceNameToCaptureDeviceLock.readLock().lock();

        try {
            captureDevice = captureDeviceIdToCaptureDevice.get(deviceId);
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceNameToCaptureDeviceLock.readLock().unlock();
        }

        return logger.exit(captureDevice);
    }

    /**
     * Get a capture device by name.
     * <p/>
     * This will return a capture device and offers optional blocking in the event that we know the
     * device will be there, but it's just not there right this second. The blocking is based on if
     * all required devices are accounted for or not.
     *
     * @param deviceName This is the name of the capture device.
     * @param wait When <i>true</i> this will wait for all of the required capture devices to be
     *             loaded if it is not found on the first attempt.
     * @return The capture device requested or <i>null</i> if it doesn't exist.
     */
    public static CaptureDevice getSageTVCaptureDevice(String deviceName, boolean wait) {
        logger.entry(deviceName);

        CaptureDevice captureDevice = null;

        captureDeviceNameToCaptureDeviceLock.readLock().lock();

        try {
            if (deviceName.endsWith(" Digital TV Tuner")) {
                deviceName = deviceName.substring(0, deviceName.length() - " Digital TV Tuner".length());
            }
            captureDevice = captureDeviceNameToCaptureDevice.get(deviceName.trim());
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceNameToCaptureDeviceLock.readLock().unlock();
        }

        if (wait) {
            // In case the capture device was not loaded yet, we can wait for an further expected
            // devices here before trying one more time.
            if (captureDevice == null) {
                try {
                    blockUntilCaptureDevicesLoaded();
                } catch (InterruptedException e) {
                    logger.debug("getSageTVCaptureDevice was interrupted while waiting for all capture devices to be loaded.");
                }

                captureDeviceNameToCaptureDeviceLock.readLock().lock();

                try {
                    if (deviceName.endsWith(" Digital TV Tuner")) {
                        deviceName = deviceName.substring(0, deviceName.length() - " Digital TV Tuner".length());
                    }
                    captureDevice = captureDeviceNameToCaptureDevice.get(deviceName.trim());
                } catch (Exception e) {
                    logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
                } finally {
                    captureDeviceNameToCaptureDeviceLock.readLock().unlock();
                }
            }
        }

        return logger.exit(captureDevice);
    }

    /**
     * Set a capture device to map to a filename.
     *
     * @param captureDevice This is the capture device object to associate the filename.
     * @param filename      This is the complete filename (including path).
     */
    public static void setFilesByCaptureDevice(CaptureDevice captureDevice, String filename) {
        logger.entry(captureDevice, filename);

        captureDeviceToFilesLock.writeLock().lock();

        try {
            captureDeviceToFiles.put(captureDevice, filename);
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceToFilesLock.writeLock().unlock();
        }

        logger.exit();
    }

    /**
     * Set a filename to map to an uploadID.
     *
     * @param filename This is the complete filename (including path).
     * @param uploadID This is the uploadID assigned to the filename.
     */
    public static void setUploadIDByFilename(String filename, Integer uploadID) {
        logger.entry(filename, uploadID);

        fileToUploadIDLock.writeLock().lock();

        try {
            fileToUploadID.put(filename, uploadID);
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            fileToUploadIDLock.writeLock().unlock();
        }

        logger.exit();
    }

    /**
     * Get a capture device based on the complete filename (including path) it is currently
     * recording.
     *
     * @param filename This is the filename to lookup.
     * @return Returns a capture device or null if one could not be found.
     */
    public static CaptureDevice getCaptureDeviceByFilename(String filename) {
        logger.entry(filename);

        captureDeviceToFilesLock.readLock().lock();
        CaptureDevice captureDevice = null;

        try {
            for (Map.Entry<CaptureDevice, String> keyValuePair : captureDeviceToFiles.entrySet()) {
                if (keyValuePair.getValue() != null && keyValuePair.getValue().equals(filename)) {
                    if (keyValuePair.getValue().equals(filename)) {
                        captureDevice = keyValuePair.getKey();
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceToFilesLock.readLock().unlock();
        }

        return logger.exit(captureDevice);
    }

    /**
     * Returns an array containing all currently available capture devices.
     * <p/>
     * It is not recommended to modify the capture devices returned from the method unless you know
     * what the implications your actions could have on the stability of the capture device.
     *
     * @return All currently available capture devices.
     */
    public static ArrayList<CaptureDevice> getAllSageTVCaptureDevices() {
        return getAllSageTVCaptureDevices(new CaptureDeviceType[0]);
    }

    /**
     * Returns an array containing all currently available capture devices of a specific type.
     * <p/>
     * It is not recommended to modify the capture devices returned from the method unless you know
     * what the implications your actions could have on the stability of the capture device.
     *
     * @param captureDeviceTypes The device type or types to filter by.
     * @return A filtered array of all currently available capture devices.
     */
    public static ArrayList<CaptureDevice> getAllSageTVCaptureDevices(CaptureDeviceType... captureDeviceTypes) {
        logger.entry();

        captureDeviceNameToCaptureDeviceLock.readLock().lock();
        ArrayList<CaptureDevice> captureDevices = new ArrayList<CaptureDevice>();

        try {
            for (Map.Entry<String, CaptureDevice> captureDeviceMap : captureDeviceNameToCaptureDevice.entrySet()) {
                CaptureDevice captureDevice = captureDeviceMap.getValue();

                if (captureDevice != null) {
                    if (captureDeviceTypes.length == 0) {
                        captureDevices.add(captureDevice);
                    } else {
                        for (CaptureDeviceType captureDeviceType : captureDeviceTypes) {
                            if (captureDevice.getEncoderDeviceType() == captureDeviceType) {
                                captureDevices.add(captureDevice);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceNameToCaptureDeviceLock.readLock().unlock();
        }

        return logger.exit(captureDevices);
    }

    protected static ArrayList<String> getAllTunerProperties(SageTVRequestHandler requestHandler) {
        logger.entry();

        captureDeviceNameToCaptureDeviceLock.readLock().lock();
        ArrayList<String> tunerPropertiesList = new ArrayList<String>();

        try {
            for (Map.Entry<String, CaptureDevice> captureDeviceMap : captureDeviceNameToCaptureDevice.entrySet()) {
                CaptureDevice captureDevice = captureDeviceMap.getValue();

                // This allows us to make a capture device only appear in detection for a server on
                // a specific IP address.
                String dedicatedServerAddress = Config.getString("sagetv.device." +
                        captureDevice.getEncoderUniqueHash() + ".exclusive_server_address", "");
                if (dedicatedServerAddress.length() > 0 && !dedicatedServerAddress.equals(requestHandler.getRemoteAddress())) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Skipping the capture device '{}'" +
                                        " for discovery because it is exclusive to '{}'" +
                                        " and the remote server IP address is '{}'",
                                captureDevice.getEncoderName(),
                                dedicatedServerAddress,
                                requestHandler.getRemoteAddress()
                        );
                    }
                    continue;
                }

                String localAddress = requestHandler.getLocalAddress();
                if (localAddress.equals(requestHandler.getRemoteAddress())) {
                    // The addresses match, let's use the loopback address.
                    localAddress = "127.0.0.1";
                }

                int listenPort = Config.getInteger(
                        "sagetv.device." + captureDevice.getEncoderUniqueHash() +
                                ".encoder_listen_port",
                        Config.getSocketServerPort(
                                captureDevice.getEncoderUniqueHash()
                        )
                );

                String tunerProperties[] = buildTunerProperty(
                        captureDevice.getEncoderName(),
                        captureDevice.getEncoderUniqueHash(),
                        localAddress,
                        listenPort,
                        captureDevice.canSwitch()
                );
                tunerPropertiesList.addAll(Arrays.asList(tunerProperties));
            }
        } catch (Exception e) {
            logger.debug("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceNameToCaptureDeviceLock.readLock().unlock();
        }

        return logger.exit(tunerPropertiesList);
    }

    private static String[] buildTunerProperty(String encoderName, Integer encoderUniqueHash, String socketServerAddress, int socketServerPort, boolean canSwitch) {

        String propertiesRoot = "sagetv.device." + encoderUniqueHash + ".";

        String prefix = "mmc/encoders/" + encoderUniqueHash;

        return new String[]{
                prefix + "/100/0/brightness=-1",
                prefix + "/100/0/contrast=-1",
                prefix + "/100/0/encode_digital_tv_as_program_stream=false",
                prefix + "/100/0/hue=-1",
                prefix + "/100/0/last_channel=",
                prefix + "/100/0/saturation=-1",
                prefix + "/100/0/sharpness=-1",
                prefix + "/100/0/tuning_mode=Cable",
                prefix + "/100/0/tuning_plugin=",
                prefix + "/100/0/tuning_plugin_port=0",
                prefix + "/100/0/video_crossbar_index=0",
                prefix + "/100/0/video_crossbar_type=100",
                prefix + "/audio_capture_device_name=",
                prefix + "/broadcast_standard=",
                prefix + "/capture_config=" + (MPEG_PURE_CAPTURE_MASK | MPEG_LIVE_PREVIEW_MASK),
                prefix + "/default_device_quality=",
                prefix + "/delay_to_wait_after_tuning=" +
                        Config.getInteger(propertiesRoot + "delay_to_wait_after_tuning", 0),
                prefix + "/device_class=NetworkEncoder",
                prefix + "/encoder_host=" + socketServerAddress + ":" + socketServerPort,
                prefix + "/encoder_merit=" +
                        Config.getInteger(propertiesRoot + "encoder_merit", 0),
                prefix + "/encoding_host=" + socketServerAddress + ":" + socketServerPort,
                prefix + "/fast_network_encoder_switch=" +
                        Config.getBoolean(propertiesRoot + "fast_network_encoder_switch",
                                canSwitch),
                prefix + "/forced_video_storage_path_prefix=",
                prefix + "/last_cross_index=0",
                prefix + "/last_cross_type=100",
                prefix + "/live_audio_input=",
                prefix + "/multicast_host=",
                prefix + "/never_stop_encoding=false",
                prefix + "/video_capture_device_name=" + encoderName,
                prefix + "/video_capture_device_num=0",
                prefix + "/video_encoding_params=Great"
        };
    }

    /**
     * Start the timeout for capture devices to be loaded.
     * <p/>
     * Always start this thread before it is possible for any capture devices to be loaded so
     * everything will start as quickly as possible. Otherwise it may take up to the timeout for
     * SageTVRequestHandler to start accepting connections from SageTV.
     */
    public static void startWaitingForCaptureDevices() {
        logger.entry();
        if (devicesWaitingThread != null && devicesWaitingThread.isAlive()) {
            devicesWaitingThread.stopNoError();
        }

        devicesWaitingThread = new SageTVDevicesLoaded();
        devicesWaitingThread.setName("SageTVDevicesLoaded-" + devicesWaitingThread.getId());
        devicesWaitingThread.start();

        logger.exit();
    }

    /**
     * Call this method to block until all required devices are loaded.
     * <p/>
     * Be careful where you call this and test to ensure you are not calling this method in a place
     * that will potentially cause a deadlock. Do not use this as any part of tuner initialization.
     *
     * @throws InterruptedException Thrown if the thread is interrupted. In this case it will likely
     *                              be due to suspend or the program closing.
     */
    public static void blockUntilCaptureDevicesLoaded() throws InterruptedException {
        devicesWaitingThread.blockUntilLoaded();
    }

    /**
     * Is OpenDCT still waiting for all capture devices to be loaded?
     *
     * @return <i>true</i> if all expected capture devices have not been loaded yet.
     */
    public static boolean captureDevicesLoaded() {
        return !devicesWaitingThread.isAlive();
    }

    /**
     * Implements a callback for Suspend Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onSuspendEvent() {
        if (suspend.getAndSet(true)) {
            logger.error("onSuspendEvent: The computer is going into suspend mode and SageTVManager has possibly not recovered from the last suspend event.");
        } else {
            logger.debug("onSuspendEvent: Stopping services due to a suspend event.");

            if (devicesWaitingThread != null) {
                // In case we go into Standby immediately after start up, this will keep the program from
                // terminating when you could not have any expectation that it has detected all expected
                // devices.
                devicesWaitingThread.stopNoError();
            }

            // This only stops all of the socket servers. It does not remove them.
            stopAllSocketServers();
            stopAndClearAllCaptureDevices();
        }
    }

    /**
     * Implements a callback for Resume Suspend Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onResumeSuspendEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeSuspendEvent: The computer returned from suspend mode and SageTVManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeSuspendEvent: Starting services due to a resume event.");

            // This resumes all of the socket servers that were stopped prior to suspend.
            resumeAllSocketServers();
            startWaitingForCaptureDevices();
        }
    }

    /**
     * Implements a callback for Resume Critical Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onResumeCriticalEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeCriticalEvent: The computer returned from suspend mode and SageTVManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeCriticalEvent: Starting services due to a resume event.");

            // This resumes all of the socket servers that were stopped prior to suspend.
            resumeAllSocketServers();
            startWaitingForCaptureDevices();
        }
    }

    /**
     * Implements a callback for Resume Automatic Event.
     * <p/>
     * This is a callback for the PowerMessagePump. This method should not be getting called by
     * anything else with the exception of testing.
     */
    public void onResumeAutomaticEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeAutomaticEvent: The computer returned from suspend mode and SageTVManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeAutomaticEvent: Starting services due to a resume event.");

            // This resumes all of the socket servers that were stopped prior to suspend.
            resumeAllSocketServers();
            startWaitingForCaptureDevices();
        }
    }
}
