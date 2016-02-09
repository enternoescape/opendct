/*
 * Copyright 2016 The OpenDCT Authors. All Rights Reserved
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

package opendct.capture;

import opendct.channel.*;
import opendct.config.Config;
import opendct.config.options.BooleanDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.SageTVConsumer;
import opendct.producer.RTPProducer;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import opendct.tuning.hdhomerun.*;
import opendct.tuning.hdhomerun.returns.HDHomeRunFeatures;
import opendct.tuning.hdhomerun.returns.HDHomeRunStatus;
import opendct.tuning.hdhomerun.returns.HDHomeRunVStatus;
import opendct.tuning.hdhomerun.types.HDHomeRunChannelMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class HDHRNativeCaptureDevice extends RTPCaptureDevice {
    private final Logger logger = LogManager.getLogger(HDHRNativeCaptureDevice.class);

    private final HDHomeRunDiscoveredDeviceParent discoveredDeviceParent;
    private final HDHomeRunDiscoveredDevice discoveredDevice;
    private final HDHomeRunDevice device;
    private final HDHomeRunTuner tuner;

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();
    private Thread monitorThread;
    private Thread tuningThread;

    private final ConcurrentHashMap<String, DeviceOption> deviceOptions;
    private BooleanDeviceOption forceExternalUnlock;
    private boolean offlineScan;

    /**
     * Create a new HDHomeRun capture device.
     *
     * @param discoveredDeviceParent This is the name of the device containing this capture device. This
     *                         is used for identifying groupings of devices.
     * @param discoveredDevice This name is used to uniquely identify this capture device. The encoder
     *               version is defaulted to 3.0.
     * @throws CaptureDeviceIgnoredException If the configuration indicates that this device should
     *                                       not be loaded, this exception will be thrown.
     */
    public HDHRNativeCaptureDevice(HDHomeRunDiscoveredDeviceParent discoveredDeviceParent, HDHomeRunDiscoveredDevice discoveredDevice) throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        super(discoveredDeviceParent.getFriendlyName(), discoveredDevice.getFriendlyName(), discoveredDeviceParent.getParentId(), discoveredDevice.getId());

        this.discoveredDeviceParent = discoveredDeviceParent;
        this.discoveredDevice = discoveredDevice;
        device = discoveredDeviceParent.getDevice();
        tuner = device.getTuner(discoveredDevice.getTunerNumber());

        // =========================================================================================
        // Print out diagnostic information for troubleshooting.
        // =========================================================================================
        if (logger.isDebugEnabled()) {
            try {
                logger.debug("HDHomeRun details: {}, {}, {}, {}", device.getSysHwModel(), device.getSysModel(), device.getSysVersion(), device.getSysFeatures());
                logger.debug("HDHomeRun help: {}", Arrays.toString(device.getHelp()));
            } catch (IOException e) {
                logger.error("Unable to get help from HDHomeRun because the device cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to get help from the HDHomeRun because the command did not work => ", e);
            }
        }

        // =========================================================================================
        // Initialize and configure device options.
        // =========================================================================================
        offlineScan = Config.getBoolean(propertiesDeviceParent + "offline_scan", false);

        deviceOptions = new ConcurrentHashMap<>();

        try {
            forceExternalUnlock = new BooleanDeviceOption(
                    Config.getBoolean(propertiesDeviceRoot + "always_force_external_unlock", false),
                    false,
                    "Always Force Unlock",
                    propertiesDeviceRoot + "always_force_external_unlock",
                    "This will allow the program to always override the HDHomeRun lock when" +
                            " SageTV requests a channel to be tuned."
            );

            /*offlineScan = new BooleanDeviceOption(
                    Config.getBoolean(propertiesDeviceParent + "offline_scan", false),
                    false,
                    "Offline Scanning",
                    propertiesDeviceRoot + "offline_scan",
                    "Enable this tuner to be included in offline activities that require use of" +
                            " the tuner when SageTV is not using it. These activities mostly" +
                            " involve channel scanning and will yield to SageTV the instant a" +
                            " tuning request is received."
            );*/

            Config.mapDeviceOptions(
                    deviceOptions,
                    forceExternalUnlock
            );
        } catch (DeviceOptionException e) {
            throw new CaptureDeviceLoadException(e);
        }

        // =========================================================================================
        // Determine device tuning mode.
        // =========================================================================================
        encoderDeviceType = CaptureDeviceType.HDHOMERUN;

        try {
            if (device.isCableCardTuner()) {
                if (device.getCardStatus().toLowerCase().equals("inserted")) {
                    encoderDeviceType = CaptureDeviceType.DCT_HDHOMERUN;
                    setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dct"));
                } else {
                    encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                    setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
                }
            } else {
                String channelMapName = tuner.getChannelmap();
                HDHomeRunChannelMap channelMap = HDHomeRunFeatures.getEnumForChannelmap(channelMapName);

                switch (channelMap) {
                    case US_BCAST:
                        encoderDeviceType = CaptureDeviceType.ATSC_HDHOMERUN;
                        setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "atsc"));
                        break;

                    case US_CABLE:
                        encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                        setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
                        break;

                    case US_IRC:
                    case US_HRC:
                    case UNKNOWN:
                        throw new CaptureDeviceLoadException("The program currently does not know how to use the channel map '" + channelMapName + "'.");
                }
            }
        } catch (IOException e) {
            logger.error("Unable to check HDHomeRun configuration because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get channel map from HDHomeRun because the command did not work => ", e);
        }

        if (device.isLegacy()) {
            // This way we don't end up with a device that doesn't have a lineup.xml file becoming the primary source.
            setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", String.valueOf(encoderDeviceType).toLowerCase() + "_legacy"));
        } else {
            setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", String.valueOf(encoderDeviceType).toLowerCase()));
        }

        if (!ChannelManager.hasChannels(encoderLineup) && encoderLineup.equals(String.valueOf(encoderDeviceType).toLowerCase())) {
            ChannelLineup newChannelLineup;

            if (device.isLegacy()) {
                // There are no sources to available on legacy devices.
                newChannelLineup = new ChannelLineup(encoderLineup, encoderParentName, ChannelSourceType.STATIC, device.getIpAddress().getHostAddress());
            } else {
                newChannelLineup = new ChannelLineup(encoderLineup, encoderParentName, ChannelSourceType.HDHOMERUN, device.getIpAddress().getHostAddress());
            }

            ChannelManager.updateChannelLineup(newChannelLineup);
            ChannelManager.addChannelLineup(newChannelLineup, true);
            ChannelManager.saveChannelLineup(encoderLineup);

        }

        if (offlineScan) {
            ChannelManager.addDeviceToOfflineScan(encoderLineup, encoderName);
        }

        logger.debug("Initializing RTSP client...");
        rtspClient = getNewRTSPClient();

        logger.debug("Getting a port for incoming RTP data...");
        rtpLocalPort = Config.getFreeRTSPPort(encoderName);

        logger.info("Encoder Manufacturer: '{}'," +
                " Number: {}," +
                " Remote IP: '{}'," +
                " Local IP: '{}'," +
                " CableCARD: {}," +
                " Lineup: '{}'," +
                " Offline Scan Enabled: {}," +
                " RTP Port: {}",
                "Silicondust",
                tuner.TUNER_NUMBER,
                device.getIpAddress(),
                discoveredDeviceParent.getLocalAddress(),
                (encoderDeviceType == CaptureDeviceType.DCT_HDHOMERUN),
                encoderLineup,
                offlineScan,
                rtpLocalPort);

    }

    @Override
    public boolean isLocked() {
        return locked.get();
    }

    @Override
    public boolean setLocked(boolean locked) {
        boolean messageLock = this.locked.get();

        // This means the lock was already set
        if (this.locked.getAndSet(locked) == locked) {
            logger.info("Capture device is was already {}.", (locked ? "locked" : "unlocked"));
            return false;
        }

        synchronized (exclusiveLock) {
            this.locked.set(locked);

            if (messageLock != locked) {
                logger.info("Capture device is now {}.", (locked ? "locked" : "unlocked"));
            } else {
                logger.debug("Capture device is now re-{}.", (locked ? "locked" : "unlocked"));
            }
        }

        return true;
    }

    @Override
    public boolean isExternalLocked() {
        try {
            boolean returnValue = tuner.isLocked();

            logger.info("HDHomeRun is currently {}.", (returnValue ? "locked" : "unlocked"));

            return returnValue;
        } catch (IOException e) {
            logger.error("Unable to get the locked status of HDHomeRun because it cannot be reached => ", e);

            // If we can't reach it, it's as good as locked.
            return true;
        } catch (GetSetException e) {
            logger.error("Unable to get the locked status of HDHomeRun because the command did not work => ", e);

            // The device must not support locking.
            return false;
        }
    }

    @Override
    public boolean setExternalLock(boolean locked) {
        if (HDHomeRunDiscoverer.getHdhrLock() && locked) {
            try {
                if (forceExternalUnlock.getBoolean()) {
                    tuner.forceClearLockkey();
                }

                tuner.setLockkey(discoveredDeviceParent.getLocalAddress());
                logger.info("HDHomeRun is now locked.");

                return true;
            } catch (IOException e) {
                logger.error("Unable to lock HDHomeRun because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to lock HDHomeRun because the command did not work => ", e);
            }
        } else if (HDHomeRunDiscoverer.getHdhrLock()) {
            try {
                if (forceExternalUnlock.getBoolean()) {
                    tuner.forceClearLockkey();
                } else {
                    tuner.clearLockkey();
                }

                logger.info("HDHomeRun is now unlocked.");

                return true;
            } catch (IOException e) {
                logger.error("Unable to unlock HDHomeRun because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to unlock HDHomeRun because the command did not work => ", e);
            }
        }

        return false;
    }

    @Override
    public boolean getChannelInfoOffline(TVChannel tvChannel) {
        logger.entry(tvChannel);

        if (isLocked() || isExternalLocked()) {
            return false;
        }

        synchronized (exclusiveLock) {
            // Return immediately if an exclusive lock was set between here and the first check if
            // there is an exclusive lock set.
            if (isLocked()) {
                return logger.exit(false);
            }

            if (!startEncoding(tvChannel.getChannel(), null, "", 0)) {
                return logger.exit(false);
            }

            //TODO: This needs to be improved before the web interface is completed.
            int offlineDetectionMinBytes = 10528; //HDHomeRunDiscoverer.getOfflineDetectionMinBytes();
            int timeout = 8; //HDHomeRunDiscoverer.getOfflineDetectionSeconds();

            CopyProtection copyProtection = getCopyProtection();
            while ((copyProtection == CopyProtection.NONE ||
                    copyProtection == CopyProtection.UNKNOWN) &&
                    timeout-- > 0) {

                if (isLocked()) {
                    stopEncoding();
                    return logger.exit(false);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return logger.exit(false);
                }

                copyProtection = getCopyProtection();
            }

            tvChannel.setCci(copyProtection);
            tvChannel.setSignalStrength(getSignalStrength());
            if (copyProtection == CopyProtection.COPY_FREELY || copyProtection == CopyProtection.NONE) {
                tvChannel.setTunable(getRecordedBytes() > offlineDetectionMinBytes);
            } else {
                tvChannel.setTunable(false);
            }

            try {
                String frequency = tuner.getChannel();

                if (frequency != null) {
                    String split[] = frequency.split(":");
                    if (split.length > 1 && split[split.length - 1].length() > 3) {
                        tvChannel.setModulation(split[0].toUpperCase());

                        tvChannel.setFrequency(Integer.valueOf(split[split.length - 1]));
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to get frequency from capture device => ", e);
            }

            try {
                tvChannel.setProgram(String.valueOf(tuner.getProgram()));
            } catch (Exception e) {
                logger.error("Unable to get program from HDHomeRun => ", e);
            }

            if (encoderDeviceType == CaptureDeviceType.DCT_HDHOMERUN) {
                try {
                    HDHomeRunVStatus status = tuner.getVirtualChannelStatus();
                    tvChannel.setTunable(!status.NOT_AVAILABLE && !status.COPY_PROTECTED && !status.NOT_SUBSCRIBED);
                } catch (Exception e) {
                    logger.error("Unable to get status from HDHomeRun => ", e);

                    // Try one more time to see if anything actually recorded.
                    tvChannel.setTunable(getRecordedBytes() > offlineDetectionMinBytes);
                }
            }
        }

        return logger.exit(true);
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize) {
        return startEncoding(channel, filename, encodingQuality, bufferSize, -1, null);
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        logger.entry(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
        long startTime = System.currentTimeMillis();
        boolean scanOnly = false;

        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);

        if (encoderDeviceType == CaptureDeviceType.ATSC_HDHOMERUN && tvChannel == null) {
            int firstIndex = channel.indexOf("-");
            int secondIndex = channel.lastIndexOf("-");

            if (firstIndex != -1 && firstIndex != secondIndex) {
                channel = channel.substring(firstIndex + 1);
            }

            tvChannel = ChannelManager.getChannel(encoderLineup, channel);
        }

        if (remoteAddress != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}' via the upload id '{}'...", channel, encoderName, filename, uploadID);
        } else if (filename != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}'...", channel, encoderName, filename);
        } else {
            logger.info("Starting a channel scan for the channel '{}' from the device '{}'...", channel, encoderName);
            scanOnly = true;
        }

        // Only lock the HDHomeRun is this device is locked too. This will prevent any offline
        // activities from taking the tuner away from outside programs.
        if (isLocked()) {
            int timeout = 5;
            while (!setExternalLock(true) && !Thread.currentThread().isInterrupted()) {
                if (timeout-- < 0) {
                    logger.error("Locking HDHomeRun device failed after 5 attempts.");
                    return logger.exit(false);
                }

                logger.warn("Unable to lock HDHomeRun device.");

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return logger.exit(false);
                }
            }
        }

        // The producer and consumer methods are requested to not block. If they don't shut down in
        // time, it will be caught and handled later. This gives us a small gain in speed.
        stopProducing(false);

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
            stopConsuming(false);
        }

        // Get a new producer and consumer.
        RTPProducer newRTPProducer = getNewRTPProducer();
        SageTVConsumer newConsumer;

        // If we are trying to restart the stream, we don't need to get a new consumer.
        if (monitorThread != null && monitorThread == Thread.currentThread()) {
            newConsumer = sageTVConsumerRunnable;
        } else if (scanOnly) {
            newConsumer = getNewChannelScanSageTVConsumer();
            newConsumer.consumeToNull(true);
        } else {
            newConsumer = getNewSageTVConsumer();
        }

        switch (encoderDeviceType) {
            case DCT_HDHOMERUN:
                try {
                    if (tvChannel != null) {
                        tuner.setVirtualChannel(tvChannel.getChannel());
                    } else {
                        // This is in case the lineup doesn't have the channel requested.
                        tuner.setVirtualChannel(channel);
                    }
                } catch (IOException e) {
                    logger.error("HDHomeRun is unable to tune into channel because it cannot be reached '{}' => ", channel, e);
                    return logger.exit(false);
                } catch (GetSetException e) {
                    logger.error("HDHomeRun is unable to tune into channel because the command did not work '{}' => ", channel, e);
                    return logger.exit(false);
                }
                break;
            case ATSC_HDHOMERUN:
                try {
                    if (tvChannel != null) {
                        logger.info("Tuning '{}'.", tvChannel.getChannel());
                        tuner.setVirtualChannel(tvChannel.getChannel());
                    } else {
                        // This is in case the lineup doesn't have the channel requested.
                        tuner.setVirtualChannel(channel);
                    }
                } catch (IOException e) {
                    logger.error("HDHomeRun is unable to tune into channel because it cannot be reached '{}' => ", channel, e);
                    return logger.exit(false);
                } catch (GetSetException e) {
                    logger.error("HDHomeRun is unable to tune into channel because the command did not work '{}' => ", channel, e);
                    return logger.exit(false);
                }
                break;
            case QAM_HDHOMERUN:
                if (tvChannel == null) {
                    logger.error("The channel '{}' does not exist on the lineup '{}'.", channel, encoderLineup);
                    return logger.exit(false);
                }

                try {
                    String modulation = tvChannel.getModulation();
                    if (modulation == null) {
                        logger.warn("The channel '{}' does not have a modulation on the lineup '{}'. Using QAM256.", channel, encoderLineup);
                        modulation = "auto";
                    }

                    int frequency = tvChannel.getFrequency();
                    if (frequency <= 0) {
                        logger.error("The channel '{}' does not have a frequency on the lineup '{}'.", channel, encoderLineup);
                        return logger.exit(false);
                    }

                    String program = tvChannel.getProgram();
                    if (program == null) {
                        logger.error("The channel '{}' does not have a program on the lineup '{}'.", channel, encoderLineup);
                        return logger.exit(false);
                    }

                    tuner.setChannel(modulation, frequency, false);

                    boolean foundProgram = false;

                    for (int i = 0; i < 20; i++) {
                        try {
                            tuner.setProgram(program);
                            foundProgram = true;
                        } catch (GetSetException e) {
                            logger.debug("HDHomeRun device returned an error => ", e);
                        }

                        if (foundProgram) {
                            break;
                        }

                        Thread.sleep(50);
                    }

                    if (!foundProgram) {
                        logger.error("The frequency '{}' does not have the program on the lineup '{}'.", frequency, encoderLineup);
                        return logger.exit(false);
                    }
                } catch (IOException e) {
                    logger.error("HDHomeRun is unable to tune into channel because it cannot be reached => ", e);
                    return logger.exit(false);
                } catch (GetSetException e) {
                    logger.error("HDHomeRun is unable to tune into channel because the command did not work => ", e);
                    return logger.exit(false);
                } catch (InterruptedException e) {
                    logger.debug("Interrupted while trying to tune into channel => ", e);
                    return logger.exit(false);
                }

                break;
            default:
                logger.error("This device has been assigned an " +
                        "unsupported capture device type: {}", encoderDeviceType);
                return logger.exit(false);
        }

        logger.info("Configuring and starting the new RTP producer...");

        if (!startProducing(newRTPProducer, newConsumer, rtpStreamRemoteIP, rtpLocalPort)) {
            logger.error("The producer thread using the implementation '{}' failed to start.",
                    newRTPProducer.getClass().getSimpleName());

            return logger.exit(false);
        }

        rtpLocalPort = newRTPProducer.getLocalPort();

        try {
            tuner.setTarget("rtp://" + discoveredDeviceParent.getLocalAddress().getHostAddress() + ":" + rtpLocalPort);
        } catch (IOException e) {
            logger.error("HDHomeRun is unable to start RTP because the device could not be reached => ", e);
            return logger.exit(false);
        } catch (GetSetException e) {
            logger.error("HDHomeRun is unable to start RTP because the command did not work => ", e);
            return logger.exit(false);
        }

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
            // If we are buffering this can create too much backlog and overruns the file based buffer.
            //if (bufferSize == 0) {
            try {
                newConsumer.setProgram(tuner.getProgram());

                int timeout = 20;

                while (newConsumer.getProgram() <= 0) {
                    Thread.sleep(100);
                    newConsumer.setProgram(tuner.getProgram());

                    if (timeout-- < 0) {
                        logger.error("Unable to get program after 2 seconds.");
                        newConsumer.setProgram(-1);
                        break;
                    }

                    if (tuningThread != Thread.currentThread()) {
                        stopProducing(false);
                        return logger.exit(false);
                    }
                }
            } catch (IOException e) {
                logger.error("HDHomeRun is unable to get program because the device cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("HDHomRun is unable to get program because the command did not work => ", e);
            } catch (InterruptedException e) {
                logger.debug("HDHomeRun is unable to get program because the thread was interrupted => ", e);
                return logger.exit(false);
            }
            //}

            logger.info("Configuring and starting the new SageTV consumer...");

            if (uploadID > 0 && remoteAddress != null) {
                newConsumer.consumeToUploadID(filename, uploadID, remoteAddress);
            } else if (!scanOnly) {
                newConsumer.consumeToFilename(filename);
            }

            startConsuming(channel, newConsumer, encodingQuality, bufferSize);
        } else {
            logger.info("Consumer is already running; this is a re-tune and it does not need to restart.");
        }

        if (logger.isDebugEnabled()) {
            long endTime = System.currentTimeMillis();
            logger.debug("Total tuning time: {}ms", endTime - startTime);
        }

        // If we are trying to restart the stream, we only need one monitoring thread.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
            if (!scanOnly) {
                monitorTuning(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
            }
        }

        sageTVConsumerRunnable.isStreaming(UpnpDiscoverer.getStreamingWait());

        return logger.exit(true);
    }

    private void monitorTuning(final String channel, final String originalFilename, final String originalEncodingQuality, final long bufferSize, final int originalUploadID, final InetAddress remoteAddress) {
        if (monitorThread != null && monitorThread != Thread.currentThread()) {
            monitorThread.interrupt();
        }

        monitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                logger.info("Tuning monitoring thread started.");

                TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);

                updateChannelMapping(channel);

                int timeout = 0;
                boolean firstPass = true;

                if (tvChannel != null && tvChannel.getName().startsWith("MC")) {
                    // Music Choice channels take forever to start and with a 4 second timeout,
                    // they might never start.
                    timeout = UpnpDiscoverer.getRetunePolling() * 4000;
                } else {
                    timeout = UpnpDiscoverer.getRetunePolling() * 1000;
                }

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException e) {
                        return;
                    }

                    if (rtpProducerRunnable.isStalled() && !Thread.currentThread().isInterrupted()) {
                        String filename = originalFilename;
                        String encodingQuality = originalEncodingQuality;
                        int uploadID = originalUploadID;

                        // Since it's possible that a SWITCH may have happened since we last started
                        // the recording, this keeps everything consistent.
                        if (sageTVConsumerRunnable != null) {
                            filename = sageTVConsumerRunnable.getEncoderFilename();
                            encodingQuality = sageTVConsumerRunnable.getEncoderQuality();
                            uploadID = sageTVConsumerRunnable.getEncoderUploadID();
                        }


                        logger.error("No data was streamed. Copy protection status is '{}' and signal strength is {}. Re-tuning channel...", getCopyProtection(), getSignalStrength());
                        if (logger.isDebugEnabled()) {
                            logger.debug(getTunerStatusString());
                        }

                        boolean tuned = false;

                        while (!tuned && !Thread.currentThread().isInterrupted()) {
                            stopDevice();
                            tuned = startEncoding(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }

                        logger.info("Stream halt caused re-tune. Copy protection status is '{}' and signal strength is {}.", getCopyProtection(), getSignalStrength());
                    }

                    if (getRecordedBytes() != 0 && firstPass) {
                        firstPass = false;
                        logger.info("Streamed first {} bytes.", getRecordedBytes());
                    }
                }

                logger.info("Tuning monitoring thread stopped.");
            }
        });

        monitorThread.setName("TuningMonitor-" + monitorThread.getId() + ":" + encoderName);
        monitorThread.start();
    }

    public void updateChannelMapping(String channel) {

        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);

        if (tvChannel == null) {
            return;
        }

        try {
            String frequency = tuner.getChannel();

            if (frequency != null) {
                String split[] = frequency.split(":");
                if (split.length > 1 && split[split.length - 1].length() > 3) {
                    tvChannel.setModulation(split[0].toUpperCase());

                    tvChannel.setFrequency(Integer.valueOf(split[split.length - 1]));
                }

                if (encoderDeviceType == CaptureDeviceType.ATSC_HDHOMERUN) {
                    int fChannel = Frequencies.getChannelForFrequency(FrequencyType._8VSB, tvChannel.getFrequency());

                    //tvChannel.setChannelRemap();
                }
            }
        } catch (Exception e) {
            logger.error("Unable to get frequency from capture device => ", e);
        }

        try {
            tvChannel.setProgram(String.valueOf(tuner.getProgram()));
        } catch (Exception e) {
            logger.error("Unable to get program from HDHomeRun => ", e);
        }

        ChannelManager.updateChannel(encoderLineup, tvChannel);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    public String getTunerStatusString() {
        logger.entry();

        StringBuilder stringBuilder = new StringBuilder();

        try {
            stringBuilder.append("Target: ").append(tuner.getTarget());
        } catch (Exception e) {
            logger.debug("Unable to get Target status from HDHomeRun.");
        }

        try {
            stringBuilder.append(", Lockkey: ").append(tuner.getLockkey());
        } catch (Exception e) {
            logger.debug("Unable to get Lockkey status from HDHomeRun.");
        }

        if (encoderDeviceType == CaptureDeviceType.DCT_HDHOMERUN) {
            try {
                stringBuilder.append(", HDHomeRunVStatus: ").append(tuner.getVirtualChannelStatus().toString());
            } catch (Exception e) {
                logger.debug("Unable to get HDHomeRunVStatus status from HDHomeRun.");
            }
        }

        try {
            stringBuilder.append(", HDHomeRunStreamInfo: ").append(tuner.getStreamInfo().toString());
        } catch (Exception e) {
            logger.debug("Unable to get HDHomeRunStreamInfo status from HDHomeRun.");
        }

        try {
            stringBuilder.append(", HDHomeRunStatus: ").append(tuner.getStatus().toString());
        } catch (Exception e) {
            logger.debug("Unable to get HDHomeRunStatus status from HDHomeRun.");
        }

        return logger.exit(stringBuilder.toString());
    }

    @Override
    public void stopEncoding() {
        logger.entry();

        logger.debug("Stopping encoding...");

        synchronized (exclusiveLock) {
            if (monitorThread != null && monitorThread != Thread.currentThread()) {
                monitorThread.interrupt();
            }

            super.stopEncoding();

            try {
                if (encoderDeviceType == CaptureDeviceType.DCT_HDHOMERUN) {
                    tuner.clearVirtualChannel();
                    tuner.clearTarget();
                } else if (encoderDeviceType == CaptureDeviceType.QAM_HDHOMERUN) {
                    tuner.clearChannel();
                    tuner.clearTarget();
                }
            } catch (IOException e) {
                logger.error("Unable to stop HDHomeRun Prime capture device because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to stop HDHomeRun Prime capture device because the command did not work => ", e);
            }

            // Only remove the external lock if we are not performing an offline activity.
            if (isLocked()) {
                setExternalLock(false);
            }
        }

        logger.exit();
    }

    @Override
    public void stopDevice() {
        logger.entry();

        if (monitorThread != null && monitorThread != Thread.currentThread()) {
            monitorThread.interrupt();
        }

        try {
            tuner.clearLockkey();
        } catch (IOException e) {
            logger.error("Unable to unlock HDHomeRun because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to unlock HDHomeRun because the command did not work => ", e);
        }

        try {
            tuner.clearTarget();
        } catch (IOException e) {
            logger.error("Unable to clear the HDHomeRun target because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to clear the HDHomeRun target because the command did not work => ", e);
        }
    }

    @Override
    public BroadcastStandard getBroadcastStandard() {
        //TODO: Get the actual broadcast standard in use.

        try {
            String lockStr = tuner.getStatus().LOCK_STR;
            logger.debug("getBroadcastStandard: {}", lockStr);
        } catch (IOException e) {
            logger.error("Unable to get broadcast standard from HDHomeRun because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get broadcast standard from HDHomeRun because the command did not work => ", e);
        }

        switch (encoderDeviceType) {
            case DCT_HDHOMERUN:
            case QAM_HDHOMERUN:
                return BroadcastStandard.QAM256;
            case ATSC_HDHOMERUN:
                return BroadcastStandard.ATSC;
        }

        return BroadcastStandard.UNKNOWN;
    }

    @Override
    public int getSignalStrength() {
        int signal = 0;

        try {
            HDHomeRunStatus status = tuner.getStatus();
            signal = status.SIGNAL_STRENGTH;
        } catch (IOException e) {
            logger.error("Unable to get signal strength from HDHomeRun because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get signal strength from HDHomeRun because the command did not work => ", e);
        }

        return signal;
    }

    @Override
    public CopyProtection getCopyProtection() {
        CopyProtection returnValue = CopyProtection.UNKNOWN;

        if (encoderDeviceType == CaptureDeviceType.DCT_HDHOMERUN) {
            try {
                HDHomeRunVStatus vstatus = tuner.getVirtualChannelStatus();
                returnValue = vstatus.COPY_PROTECTION;
            } catch (IOException e) {
                logger.error("Unable to get CCI from HDHomeRun because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to get CCI from HDHomeRun because the command did not work => ", e);
            }
        } else {
            returnValue = CopyProtection.NONE;
        }

        return returnValue;
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                forceExternalUnlock
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference = this.deviceOptions.get(option.getProperty());

            if (optionReference == null) {
                continue;
            }

            if (optionReference.isArray()) {
                optionReference.setValue(option.getArrayValue());
            } else {
                optionReference.setValue(option.getValue());
            }

            Config.setDeviceOption(optionReference);
        }

        Config.saveConfig();
    }
}
