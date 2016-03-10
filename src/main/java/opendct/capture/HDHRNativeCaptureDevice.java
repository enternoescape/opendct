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
import opendct.config.options.StringDeviceOption;
import opendct.consumer.SageTVConsumer;
import opendct.producer.HTTPProducer;
import opendct.producer.RTPProducer;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import opendct.tuning.hdhomerun.*;
import opendct.tuning.hdhomerun.returns.*;
import opendct.tuning.hdhomerun.types.HDHomeRunChannelMap;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
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
    private StringDeviceOption channelMap;

    private HTTPCaptureDeviceServices httpServices;
    private HTTPProducer httpProducer;
    private boolean httpProducing;

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

        deviceOptions = new ConcurrentHashMap<>();

        while(true) {
            try {
                forceExternalUnlock = new BooleanDeviceOption(
                        Config.getBoolean(propertiesDeviceRoot + "always_force_external_unlock", false),
                        false,
                        "Always Force Unlock",
                        propertiesDeviceRoot + "always_force_external_unlock",
                        "This will allow the program to always override the HDHomeRun lock when" +
                                " SageTV requests a channel to be tuned."
                );

                Config.mapDeviceOptions(
                        deviceOptions,
                        forceExternalUnlock
                );
            } catch (DeviceOptionException e) {
                logger.warn("Invalid options. Reverting to defaults => ", e);

                Config.setBoolean(propertiesDeviceRoot + "always_force_external_unlock", false);

                continue;
            }

            break;
        }

        // =========================================================================================
        // Determine device tuning mode.
        // =========================================================================================
        encoderDeviceType = CaptureDeviceType.HDHOMERUN;

        try {
            if (device.isCableCardTuner()) {
                if (device.getCardStatus().toLowerCase().contains("card=ready")) {
                    encoderDeviceType = CaptureDeviceType.DCT_HDHOMERUN;
                    setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dct"));
                } else {
                    encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                    setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
                }
            } else {
                if (!discoveredDeviceParent.getChannelMap().equals("")) {
                    tuner.setChannelmap(discoveredDeviceParent.getChannelMap());
                }

                String channelMapName = tuner.getChannelmap();
                HDHomeRunChannelMap channelMap = HDHomeRunFeatures.getEnumForChannelmap(channelMapName);

                switch (channelMap) {
                    case US_BCAST:
                        encoderDeviceType = CaptureDeviceType.ATSC_HDHOMERUN;
                        setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "atsc_" + device.getDeviceIdHex().toLowerCase()));
                        break;

                    case US_CABLE:
                        encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                        setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
                        break;

                    case US_IRC:
                    case US_HRC:
                    case UNKNOWN:
                        throw new CaptureDeviceLoadException("The program currently does not" +
                                " know how to use the channel map '" + channelMapName + "'.");
                }
            }
        } catch (IOException e) {
            logger.error("Unable to check HDHomeRun configuration because it cannot be reached => ",
                    e);
        } catch (GetSetException e) {
            logger.error("Unable to get channel map from HDHomeRun because the command did not" +
                    " work => ", e);
        }

        // =========================================================================================
        // Configure channel lineup
        // =========================================================================================

        String newLineupName;

        if (isTuneLegacy()) {
            // This way we don't end up with a device that doesn't have a lineup.xml file becoming
            // the primary source.
            if (encoderDeviceType == CaptureDeviceType.ATSC_HDHOMERUN) {
               newLineupName = Config.getString(
                        propertiesDeviceParent + "lineup",
                        String.valueOf(encoderDeviceType).toLowerCase() + "_legacy_" +
                                device.getDeviceIdHex().toLowerCase());
            } else {
                newLineupName = Config.getString(
                        propertiesDeviceParent + "lineup",
                        String.valueOf(encoderDeviceType).toLowerCase() + "_legacy");
            }
        } else {
            if (encoderDeviceType == CaptureDeviceType.ATSC_HDHOMERUN ||
                    encoderDeviceType == CaptureDeviceType.QAM_HDHOMERUN) {

                newLineupName = Config.getString(
                        propertiesDeviceParent + "lineup",
                        String.valueOf(encoderDeviceType).toLowerCase() + "_" +
                                device.getDeviceIdHex().toLowerCase());
            } else {
                newLineupName = Config.getString(
                        propertiesDeviceParent + "lineup",
                        String.valueOf(encoderDeviceType).toLowerCase());
            }

            httpServices = new HTTPCaptureDeviceServices();
        }

        setChannelLineup(newLineupName);

        if (!ChannelManager.hasChannels(encoderLineup) &&
                encoderLineup.equals(newLineupName)) {

            ChannelLineup newChannelLineup;

            if (isTuneLegacy()) {
                // There are no sources to available on legacy devices.
                newChannelLineup = new ChannelLineup(
                        encoderLineup,
                        encoderParentName,
                        ChannelSourceType.STATIC,
                        device.getIpAddress().getHostAddress());
            } else {
                newChannelLineup = new ChannelLineup(
                        encoderLineup,
                        encoderParentName,
                        ChannelSourceType.HDHOMERUN,
                        device.getIpAddress().getHostAddress());
            }

            ChannelManager.updateChannelLineup(newChannelLineup);
            ChannelManager.addChannelLineup(newChannelLineup, true);
            ChannelManager.saveChannelLineup(encoderLineup);

        }

        httpProducing = false;

        // =========================================================================================
        // Print out diagnostic information for troubleshooting.
        // =========================================================================================
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
                offlineChannelScan,
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
    public boolean getChannelInfoOffline(TVChannel tvChannel, boolean skipCCI) {
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

            int offlineDetectionMinBytes = HDHomeRunDiscoverer.getOfflineDetectionMinBytes();
            int timeout = HDHomeRunDiscoverer.getOfflineDetectionSeconds();

            if (!skipCCI) {
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
                if (copyProtection == CopyProtection.COPY_FREELY || copyProtection == CopyProtection.NONE) {
                    tvChannel.setTunable(getRecordedBytes() > offlineDetectionMinBytes);
                } else {
                    tvChannel.setTunable(false);
                }
            }

            tvChannel.setSignalStrength(getSignalStrength());

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
                } catch (IOException e) {
                    logger.error("Unable to get virtual channel status from HDHomeRun because it cannot be reached => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to get virtual channel status from HDHomeRun because the command did not work => ", e);

                    // Try one more time to see if anything actually recorded.
                    tvChannel.setTunable(getRecordedBytes() > offlineDetectionMinBytes);
                }
            } else {
                try {
                    HDHomeRunStatus status = tuner.getStatus();
                    tvChannel.setTunable(status.SIGNAL_PRESENT);
                } catch (IOException e) {
                    logger.error("Unable to get signal presence from HDHomeRun because it cannot be reached => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to get signal presence from HDHomeRun because the command did not work => ", e);

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

        tuningThread = Thread.currentThread();

        if (monitorThread != null && monitorThread != Thread.currentThread()) {
            monitorThread.interrupt();
        }

        long startTime = System.currentTimeMillis();
        boolean scanOnly = false;

        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);
        String dotChannel = channel;

        int firstIndex = channel.indexOf("-");
        int secondIndex = channel.lastIndexOf("-");

        if (firstIndex != -1 && firstIndex != secondIndex) {

            // This string is used as a last resort when attempting to tune a channel.
            dotChannel = channel.substring(firstIndex + 1).replace("-", ".");

            // This will automatically create channels for channels that SageTV requests.
            if (encoderDeviceType == CaptureDeviceType.ATSC_HDHOMERUN && tvChannel == null) {
                String vfChannel = channel.substring(0, firstIndex - 1);
                String vChannel = channel.substring(firstIndex + 1);

                tvChannel = ChannelManager.getChannel(encoderLineup, vChannel);

                if (tvChannel == null) {
                    tvChannel = new TVChannelImpl(vChannel.replace("-", "."), vChannel);
                }

                try {
                    int fChannel = Integer.parseInt(vfChannel);

                    if (fChannel < 2 || fChannel > Frequencies.US_BCAST.length ) {
                        logger.error("The channel number {} is not a valid ATSC channel number.", fChannel);
                    } else {
                        tvChannel.setFrequency(Frequencies.US_BCAST[fChannel].FREQUENCY);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Unable to parse '{}' into int => ", vfChannel, e);
                }

                tvChannel.setChannelRemap(channel);
                ChannelManager.updateChannel(encoderLineup, tvChannel);
            }
        }

        if (remoteAddress != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}' via the upload id '{}'...", channel, encoderName, filename, uploadID);
        } else if (filename != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}'...", channel, encoderName, filename);
        } else {
            logger.info("Starting a channel scan for the channel '{}' from the device '{}'...", channel, encoderName);
            scanOnly = true;
        }

        if (!httpProducing) {
            // Only lock the HDHomeRun if this device is locked too. This will prevent any offline
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
         } else {
            httpServices.stopProducing(true);
        }

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
                        httpProducing = tuneUrl(tvChannel, HDHomeRunDiscoverer.getTranscodeProfile(), newConsumer);

                        if (!httpProducing) {
                            tuner.setVirtualChannel(tvChannel.getChannel());
                        }
                    } else {
                        // This is in case the lineup doesn't have the channel requested.
                        tuner.setVirtualChannel(dotChannel);
                    }
                } catch (IOException e) {
                    logger.error("HDHomeRun is unable to tune into channel" +
                            " because it cannot be reached '{}' => ", channel, e);
                    return logger.exit(false);
                } catch (GetSetException e) {
                    logger.error("HDHomeRun is unable to tune into channel" +
                            " because the command did not work '{}' => ", channel, e);
                    return logger.exit(false);
                }
                break;
            case ATSC_HDHOMERUN:
                try {
                    if (tvChannel != null) {
                        if (isTuneLegacy()) {
                            if (!legacyTuneChannel(channel)) {
                                return logger.exit(false);
                            }
                        } else {
                            httpProducing = tuneUrl(tvChannel, HDHomeRunDiscoverer.getTranscodeProfile(), newConsumer);

                            if (!httpProducing) {
                                try {
                                    tuner.setVirtualChannel(dotChannel);
                                } catch (GetSetException e) {
                                    logger.debug("Unable to set virtual channel." +
                                            " Trying legacy tuning. => ", e);
                                    if (!legacyTuneChannel(channel)) {
                                        return logger.exit(false);
                                    }
                                }
                            }
                        }
                    } else {
                        // This is in case the lineup doesn't have the channel requested.
                        try {
                            tuner.setVirtualChannel(dotChannel);
                        } catch (GetSetException e) {
                            logger.debug("Unable to set virtual channel." +
                                    " Trying legacy tuning. => ", e);
                            if (!legacyTuneChannel(channel)) {
                                return logger.exit(false);
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("HDHomeRun is unable to tune into channel" +
                            " because it cannot be reached '{}' => ", channel, e);
                    return logger.exit(false);
                /*} catch (GetSetException e) {
                    logger.error("HDHomeRun is unable to tune into channel" +
                            " because the command did not work '{}' => ", channel, e);
                    return logger.exit(false);*/
                }
                break;
            case QAM_HDHOMERUN:
                if (tvChannel == null) {
                    tvChannel = ChannelManager.autoDctToQamMap(
                            this, encoderLineup, new TVChannelImpl(channel, channel));

                    if (tvChannel == null) {
                        logger.error("Unable to tune channel because no references" +
                                " were found for this channel number.");
                        return logger.exit(false);
                    }

                    logger.info("Added the channel '{}' to the lineup '{}'.",
                            channel, encoderLineup);
                }

                try {
                    String modulation = tvChannel.getModulation();
                    if (modulation == null) {
                        logger.debug("The channel '{}' does not have a modulation" +
                                " on the lineup '{}'. Using auto.", channel, encoderLineup);
                        modulation = "auto";
                    }

                    int frequency = tvChannel.getFrequency();
                    if (frequency <= 0) {
                        logger.error("The channel '{}' does not have a frequency" +
                                " on the lineup '{}'.", channel, encoderLineup);
                        return logger.exit(false);
                    }

                    String program = tvChannel.getProgram();
                    if (program == null) {
                        logger.error("The channel '{}' does not have a program" +
                                " on the lineup '{}'.", channel, encoderLineup);
                        return logger.exit(false);
                    }

                    if (!isTuneLegacy() &&
                            HDHomeRunDiscoverer.getAllowHttpTuning() &&
                            HDHomeRunDiscoverer.getQamHttpTuningHack()) {

                        long qamStartTime = System.currentTimeMillis();
                        TVChannel anyUrl = null;

                        // If this channel has a URL, verify the channel will tune into the correct
                        // frequency and program.
                        if (!Util.isNullOrEmpty(tvChannel.getUrl())) {
                            tuner.setVirtualChannel(tvChannel.getChannel());

                            String tFrequency = tuner.getChannel();
                            int tProgram = tuner.getProgram();

                            if (!tFrequency.endsWith(String.valueOf(":" + frequency)) ||
                                    !program.equals(String.valueOf(tProgram))) {

                                tvChannel.setChannelRemap("");
                                ChannelManager.updateChannel(encoderLineup, tvChannel);

                                logger.info("The virtual channel {} no longer has the correct" +
                                        " program and/or frequency. Finding new virtual channel.",
                                        tvChannel.getChannel());

                                tvChannel = ChannelManager.getChannel(encoderLineup, channel);

                                if (tvChannel == null) {
                                    tvChannel = ChannelManager.autoDctToQamMap(
                                            this,
                                            encoderLineup,
                                            new TVChannelImpl(channel, channel));
                                }

                                if (tvChannel == null) {
                                    logger.error("Unable to tune channel because no references" +
                                            " were found for this channel number.");
                                    return logger.exit(false);
                                }

                                modulation = tvChannel.getModulation();
                                if (modulation == null) {
                                    logger.debug("The channel '{}' does not have a modulation" +
                                            " on the lineup '{}'. Using 'auto'.",
                                            channel,
                                            encoderLineup);
                                    modulation = "auto";
                                }

                                frequency = tvChannel.getFrequency();
                                if (frequency <= 0) {
                                    logger.error("The channel '{}' does not have a frequency" +
                                            " on the lineup '{}'.",
                                            channel,
                                            encoderLineup);
                                    return logger.exit(false);
                                }

                                program = tvChannel.getProgram();
                                if (program == null) {
                                    logger.error("The channel '{}' does not have a program" +
                                            " on the lineup '{}'.",
                                            channel,
                                            encoderLineup);
                                    return logger.exit(false);
                                }
                            } else {
                                anyUrl = tvChannel;
                            }
                        }

                        if (anyUrl == null) {

                            TVChannel qamChannels[] =
                                    ChannelManager.getChannelList(encoderLineup, true, true);

                            for (TVChannel qamChannel : qamChannels) {

                                if (tuningThread != Thread.currentThread()) {
                                    return false;
                                }

                                if (Util.isNullOrEmpty(qamChannel.getUrl())) {
                                    continue;
                                }

                                try {
                                    tuner.setVirtualChannel(qamChannel.getChannel());

                                    String tFrequency = tuner.getChannel();
                                    int tProgram = tuner.getProgram();

                                    if (tFrequency.endsWith(String.valueOf(":" + frequency)) &&
                                            program.equals(String.valueOf(tProgram))) {

                                        qamChannel.setChannelRemap(tvChannel.getChannel());
                                        qamChannel.setModulation(modulation);
                                        qamChannel.setFrequency(frequency);
                                        qamChannel.setProgram(program);

                                        ChannelManager.updateChannel(encoderLineup, qamChannel);

                                        anyUrl = qamChannel;

                                        // If we don't rest here, the HDHomeRun sometimes gives a
                                        // 503 error when the URL is accessed less than 1ms later.
                                        Thread.sleep(25);
                                        break;
                                    } else {
                                        // While we have this one tuned in, let's try to match it up
                                        // so maybe we don't need to do this often.

                                        try {
                                            String split[] = tuner.getChannel().split(":");

                                            if (split.length > 1 &&
                                                    split[split.length - 1].length() > 3) {

                                                String optModulation = split[0].toUpperCase();
                                                int optFrequency =
                                                        Integer.parseInt(split[split.length - 1]);

                                                String optProgram =
                                                        String.valueOf(tuner.getProgram());

                                                String optChannel =
                                                        ChannelManager.
                                                                autoFrequencyProgramToCableChannel(
                                                                        this,
                                                                        optFrequency,
                                                                        optProgram);

                                                if (optChannel == null) {
                                                    continue;
                                                }

                                                qamChannel.setChannelRemap(optChannel);
                                                qamChannel.setModulation(optModulation);
                                                qamChannel.setFrequency(optFrequency);
                                                qamChannel.setProgram(optProgram);

                                                ChannelManager.updateChannel(
                                                        encoderLineup, qamChannel);
                                            }
                                        } catch (NumberFormatException e) {
                                            logger.warn("Unable to parse frequency from tuning" +
                                                    " the channel {}.", qamChannel.getChannel());
                                        }
                                    }
                                } catch (GetSetException e) {
                                    logger.error("Unable to tune the channel {}." +
                                            " Removing channel from lineup.",
                                            qamChannel.getChannel());

                                    ChannelLineup removeLineup =
                                            ChannelManager.getChannelLineup(encoderLineup);

                                    if (removeLineup != null) {
                                        removeLineup.removeChannel(qamChannel.getChannel());
                                    }
                                }
                            }
                        }

                        long qamEndTime = System.currentTimeMillis();

                        if (anyUrl != null) {
                            logger.info("Found QAM virtual channel {} in {}ms.",
                                    anyUrl.getChannel(),  qamEndTime - qamStartTime);

                            httpProducing = tuneUrl(
                                    anyUrl,
                                    HDHomeRunDiscoverer.getTranscodeProfile(),
                                    newConsumer);

                        } else {
                            logger.warn("QAM HTTP tuning was enabled," +
                                    " but the lineup does not appear to contain any QAM URLs." +
                                    " Reverting to legacy tuning. Wasted {}ms.",
                                    qamEndTime - qamStartTime);
                        }

                    }

                    if (!httpProducing) {
                        tuner.setChannel(modulation, frequency, false);

                        tuner.setProgram(program);
                    }

                } catch (IOException e) {
                    logger.error("HDHomeRun is unable to tune into channel" +
                            " because it cannot be reached => ", e);
                    return logger.exit(false);
                } catch (GetSetException e) {
                    logger.error("HDHomeRun is unable to tune into channel" +
                            " because the command did not work => ", e);
                    return logger.exit(false);
                } catch (Exception e) {
                    logger.error("Unable to tune into channel because of an unexpected error => ",
                            e);
                    return logger.exit(false);
                }

                break;
            default:
                logger.error("This device has been assigned an " +
                        "unsupported capture device type: {}", encoderDeviceType);
                return logger.exit(false);
        }

        if (!httpProducing) {
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
        }

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {

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
                        logger.info("tuningThread != Thread.currentThread()");
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

        sageTVConsumerRunnable.isStreaming(HDHomeRunDiscoverer.getStreamingWait());

        return logger.exit(true);
    }

    private boolean isProducerStalled() {
        if (httpProducing && httpProducer != null) {
            return httpProducer.isStalled();
        } else if (rtpProducerRunnable != null) {
            return rtpProducerRunnable.isStalled();
        }

        // This should not be happening.
        return false;
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

                tvChannel = updateChannelMapping(tvChannel);

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

                    if (isProducerStalled() && !Thread.currentThread().isInterrupted()) {
                        String filename = originalFilename;
                        String encodingQuality = originalEncodingQuality;
                        int uploadID = originalUploadID;

                        // Since it's possible that a SWITCH may have happened since we last started
                        // the recording, this keeps everything consistent.
                        SageTVConsumer consumer = sageTVConsumerRunnable;
                        if (consumer != null) {
                            filename = consumer.getEncoderFilename();
                            encodingQuality = consumer.getEncoderQuality();
                            uploadID = consumer.getEncoderUploadID();
                        }


                        logger.error("No data was streamed. Copy protection status is '{}' and signal strength is {}. Re-tuning channel...", getCopyProtection(), getSignalStrength());
                        if (logger.isInfoEnabled()) {
                            logger.info(getTunerStatusString());
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

                        if (logger.isInfoEnabled()) {
                            logger.info(getTunerStatusString());
                        }
                    }
                }

                logger.info("Tuning monitoring thread stopped.");
            }
        });

        monitorThread.setName("TuningMonitor-" + monitorThread.getId() + ":" + encoderName);
        monitorThread.start();
    }

    private boolean legacyTuneChannel(String channel) {
        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);

        if (tvChannel != null && tvChannel.getFrequency() > 0 && !Util.isNullOrEmpty(tvChannel.getProgram())) {
            try {
                tuner.setChannel("auto", tvChannel.getFrequency(), false);
                tuner.setProgram(tvChannel.getProgram());

                return true;
            } catch (IOException e) {
                logger.error("Unable to set channel and program on HDHomeRun capture device because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to set channel and program on HDHomeRun capture device because the command did not work => ", e);
            }
        }

        int firstDash = channel.indexOf("-");

        if (firstDash < 1) {
            logger.error("legacyTuneChannel: Channel has an unexpected format: '{}'", channel);
            return false;
        }

        String channelString = channel.substring(0, firstDash);
        String programName = channel.substring(firstDash + 1).replace("-", ".");

        int channelNumber;

        try {
            channelNumber = Integer.parseInt(channelString);
        } catch (NumberFormatException e) {
            logger.error("legacyTuneChannel: Unable to parse the value '{}' into an int => ", channelString, e);
            return false;
        }

        int fChannel;

        switch (encoderDeviceType) {
            case ATSC_HDHOMERUN:
                if (channelNumber <= 1 || channelNumber > Frequencies.US_BCAST.length) {
                    logger.error("legacyTuneChannel: The channel number {} is not a valid ATSC channel.");
                    return false;
                }

                fChannel = Frequencies.US_BCAST[channelNumber].FREQUENCY;
                break;

            default:
                logger.error("legacyTuneChannel: The device type {} is not supported for legacy tuning.", encoderDeviceType);
                return false;
        }

        logger.info("legacyTuneChannel: Using the frequency {}.", fChannel);

        try {
            tuner.setChannel("auto", fChannel, false);
        } catch (IOException e) {
            logger.error("Unable to set channel on HDHomeRun capture device because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to set channel on HDHomeRun capture device because the command did not work => ", e);
        }

        int attempt = 0;
        int attemptLimit = 30;
        boolean programSelected = false;

        while(!programSelected && attempt++ < attemptLimit) {

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                logger.info("Interrupted while waiting for programs to become available => ", e);
                return false;
            }

            if (attempt > 25) {
                logger.info("Program search attempt {} of {}.", attempt, attemptLimit);
            }

            try {
                HDHomeRunStreamInfo streamInfo = tuner.getStreamInfo();

                if (streamInfo == null || streamInfo.getProgramsRaw().length == 0) {
                    continue;
                }

                if (attempt > 35) {
                    logger.info("Searching for channel '{}' program in '{}'...", programName, streamInfo.getProgramsRaw());
                } else {
                    logger.debug("Searching for channel '{}' program in '{}'...", programName, streamInfo.getProgramsRaw());
                }

                for (HDHomeRunProgram program : streamInfo.getProgramsParsed()) {
                    if (program.PROGRAM != null && program.CHANNEL != null && program.CHANNEL.equals(programName)) {

                        logger.info("Found '{}' in '{}' out of '{}'.", programName, program, streamInfo.getProgramsRaw());
                        tuner.setProgram(program.PROGRAM);
                        programSelected = true;
                        break;
                    }
                }
            } catch (IOException e) {
                logger.error("Unable to get available programs on HDHomeRun capture device because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to get available programs on HDHomeRun capture device because the command did not work => ", e);
            }

            if (Thread.currentThread().isInterrupted()) {
                logger.info("Interrupted while waiting for programs to become available.");
                return false;
            }
        }

        return programSelected;
    }

    public boolean tuneUrl(TVChannel channel, String transcodeProfile, SageTVConsumer newConsumer) {
        if (isTuneLegacy() ||
                Util.isNullOrEmpty(channel.getUrl()) ||
                !HDHomeRunDiscoverer.getAllowHttpTuning()) {

            return false;
        }

        /*
        Reference: http://www.silicondust.com/hdhomerun/hdhomerun_http_development.pdf

        Transcode Profiles:
            • heavy: transcode to AVC with the same resolution, frame-rate, and interlacing as the
            original stream. For example 1080i60 AVC 1080i60, 720p60 AVC 720p60.
            • mobile: trancode to AVC progressive not exceeding 1280x720 30fps.
            • internet720: transcode to low bitrate AVC progressive not exceeding 1280x720 30fps.
            • internet480: transcode to low bitrate AVC progressive not exceeding 848x480 30fps for
            16:9 content, not exceeding 640x480 30fps for 4:3 content.
            • internet360: transcode to low bitrate AVC progressive not exceeding 640x360 30fps for
            16:9 content, not exceeding 480x360 30fps for 4:3 content.
            • internet240: transcode to low bitrate AVC progressive not exceeding 432x240 30fps for
            16:9 content, not exceeding 320x240 30fps for 4:3 content.
         */

        ChannelLineup lineup = ChannelManager.getChannelLineup(encoderLineup);

        String tunerUrl;

        //  Change the IP address to match the tuner we are actually trying to use.
        tunerUrl = channel.getUrl().replace(
                "http://" + lineup.getAddress(),
                "http://" + device.getIpAddress().getHostAddress());

        // Change the tuner to a specific tuner instead of just picking whatever is available.
        tunerUrl = tunerUrl.replace("/auto/", "/tuner" + tuner.TUNER_NUMBER + "/");

        // Support for transcode.
        if (!Util.isNullOrEmpty(transcodeProfile)) {
            tunerUrl += "?transcode=" + transcodeProfile;
        }

        URL tuneUrl;

        try {
            tuneUrl = new URL(tunerUrl);
        } catch (MalformedURLException e) {
            logger.error("'{}' does not appear to be a valid URL => ", tunerUrl, e);
            return false;
        }

        httpProducer = httpServices.getNewHTTPProducer(propertiesDeviceParent);

        try {
            tuner.clearLockkey();
        } catch (IOException e) {
            logger.error("Unable to clear lock on HDHomeRun capture device" +
                    " because it cannot be reached => ", e);

            return false;
        } catch (GetSetException e) {
            logger.error("Unable to clear lock on HDHomeRun capture device" +
                    " because the command did not work => ", e);

            return false;
        }

        boolean returnValue =
                httpServices.startProducing(encoderName, httpProducer, newConsumer, tuneUrl);

        if (!returnValue) {
            try {
                tuner.setLockkey(device.getIpAddress());
            } catch (IOException e) {
                logger.error("Unable to set lock on HDHomeRun capture device" +
                        " because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to set lock HDHomeRun capture device" +
                        " because the command did not work => ", e);
            }
        }

        return returnValue;
    }

    /**
     * Updates the modulation, frequency and program stored on the channel lineup.
     *
     * @param tvChannel The channel to update.
     * @return The channel updated.
     */
    private TVChannel updateChannelMapping(TVChannel tvChannel) {

        if (tvChannel == null) {
            return null;
        }

        try {
            String frequency = tuner.getChannel();

            if (frequency != null) {
                String split[] = frequency.split(":");
                if (split.length > 1 && split[split.length - 1].length() > 3) {
                    tvChannel.setModulation(split[0].toUpperCase());

                    tvChannel.setFrequency(Integer.valueOf(split[split.length - 1]));
                }

                switch (encoderDeviceType) {
                    case ATSC_HDHOMERUN:
                        int fChannel = Frequencies.getChannelForFrequency(
                                FrequencyType.US_BCAST,
                                tvChannel.getFrequency());

                        String remap = tvChannel.getChannelRemap();
                        int firstDash = remap.indexOf("-");
                        int secondDash = remap.lastIndexOf("-");

                        // Verify that we haven't already done this and that the expected format is
                        // already in place.
                        if (firstDash > -1 && firstDash == secondDash) {
                            remap = String.valueOf(fChannel) + "-" + remap;
                            tvChannel.setChannelRemap(remap);
                        }

                        break;
                    case DCT_HDHOMERUN:
                    case QAM_HDHOMERUN:
                        break;
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

        return tvChannel;
    }

    private int scanChannelIndex = 2;
    private boolean channelScanFirstZero = true;

    @Override
    public String scanChannelInfo(String channel) {
        String returnValue = "ERROR";

        switch (encoderDeviceType) {
            case ATSC_HDHOMERUN:
                if (!isTuneLegacy()) {
                    String nextChannel = super.scanChannelInfo(channel, false);

                    int firstDash = nextChannel.indexOf("-");
                    int secondDash = nextChannel.lastIndexOf("-");

                    if (firstDash > -1 && firstDash == secondDash) {
                        try {
                            tuner.setVirtualChannel(nextChannel.replace("-", "."));
                        } catch (IOException e) {
                            logger.error("Unable to set virtual channel on HDHomeRun capture" +
                                    " device because it cannot be reached => ", e);
                        } catch (GetSetException e) {
                            logger.error("Unable to set virtual channel on HDHomeRun capture" +
                                    " device because the command did not work => ", e);
                        }

                        TVChannel tvChannel = updateChannelMapping(
                                ChannelManager.getChannel(encoderLineup, nextChannel));

                        if (tvChannel != null) {
                            nextChannel = tvChannel.getChannelRemap();
                        }

                        try {
                            tuner.clearVirtualChannel();
                        } catch (IOException e) {
                            logger.error("Unable to clear virtual channel on HDHomeRun capture" +
                                    " device because it cannot be reached => ", e);
                        } catch (GetSetException e) {
                            logger.error("Unable to clear virtual channel on HDHomeRun capture" +
                                    " device because the command did not work => ", e);
                        }
                    }

                    return nextChannel;
                }

                if (channel.equals("-1")) {
                    scanChannelIndex = 2;
                    channelScanFirstZero = true;
                    return "OK";
                }

                if (channelScanFirstZero) {
                    channelScanFirstZero = false;
                    return "OK";
                }

                int timeout = 12;

                if (scanChannelIndex++ > Frequencies.US_BCAST.length) {
                    return "ERROR";
                }

                try {
                    tuner.setChannel(
                            "auto",
                            Frequencies.US_BCAST[scanChannelIndex].FREQUENCY,
                            false);

                } catch (IOException e) {
                    logger.error("Unable to set virtual channel on HDHomeRun capture device" +
                            " because it cannot be reached => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to set virtual channel on HDHomeRun capture device" +
                            " because the command did not work => ", e);
                }

                while (timeout-- > 0 && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        return "ERROR";
                    }

                    try {
                        if (tuner.getStatus().SIGNAL_PRESENT) {
                            break;
                        }
                    } catch (IOException e) {
                        logger.error("Unable to get signal present on HDHomeRun capture device" +
                                " because it cannot be reached => ", e);
                    } catch (GetSetException e) {
                        logger.error("Unable to get signal present on HDHomeRun capture device" +
                                " because the command did not work => ", e);
                    }
                }


                HDHomeRunStreamInfo streamInfo = null;

                timeout = 12;
                while (timeout-- > 0 && !Thread.currentThread().isInterrupted()) {
                    try {
                        streamInfo = tuner.getStreamInfo();

                        if (streamInfo != null && streamInfo.getProgramsRaw().length > 0) {
                            break;
                        }
                    } catch (IOException e) {
                        logger.error("Unable to get programs on HDHomeRun capture device" +
                                " because it cannot be reached => ", e);
                    } catch (GetSetException e) {
                        logger.error("Unable to get programs on HDHomeRun capture device" +
                                " because the command did not work => ", e);
                    }

                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        return "ERROR";
                    }
                }

                if (streamInfo != null) {
                    HDHomeRunProgram programs[] = streamInfo.getProgramsParsed();

                    StringBuilder stringBuilder = new StringBuilder();

                    for (HDHomeRunProgram program : programs) {
                        stringBuilder.append(scanChannelIndex)
                                .append("-")
                                .append(program.CHANNEL.replace(".", "-"))
                                .append(";");
                    }

                    // Remove the extra ;.
                    if (stringBuilder.length() > 0) {
                        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                    }

                    return stringBuilder.toString();
                }

                return "";
            case QAM_HDHOMERUN:
                return super.scanChannelInfo(channel, true);
            case DCT_HDHOMERUN:
                return super.scanChannelInfo(channel, true);
        }

        return returnValue;
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
                stringBuilder.append(", HDHomeRunVStatus: ")
                        .append(tuner.getVirtualChannelStatus().toString());
            } catch (Exception e) {
                logger.debug("Unable to get HDHomeRunVStatus status from HDHomeRun.");
            }
        }

        try {
            stringBuilder.append(", HDHomeRunStreamInfo: ")
                    .append(tuner.getStreamInfo().toString());
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

            if (httpProducing) {
                httpServices.stopProducing(false);
                httpProducer = null;
            }

            super.stopEncoding();

            try {
                if (httpProducing) {
                    tuner.forceClearLockkey();
                    httpProducing = false;
                }

                tuner.clearChannel();
                tuner.clearTarget();
            } catch (IOException e) {
                logger.error("Unable to stop HDHomeRun capture device" +
                        " because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to stop HDHomeRun capture device" +
                        " because the command did not work => ", e);
            }

            // Only remove the external lock if we are not performing an offline activity. This also
            // prevents the device from possibly being forced to be unlocked.
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

        if (httpProducing) {
            httpServices.stopProducing(false);
            httpProducer = null;
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
            logger.error("Unable to clear the HDHomeRun target" +
                    " because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to clear the HDHomeRun target" +
                    " because the command did not work => ", e);
        }
    }

    @Override
    public BroadcastStandard getBroadcastStandard() {
        //TODO: Get the actual broadcast standard in use.

        try {
            String lockStr = tuner.getStatus().LOCK_STR;
            logger.debug("getBroadcastStandard: {}", lockStr);
        } catch (IOException e) {
            logger.error("Unable to get broadcast standard from HDHomeRun" +
                    " because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get broadcast standard from HDHomeRun" +
                    " because the command did not work => ", e);
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
            logger.error("Unable to get signal strength from HDHomeRun" +
                    " because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get signal strength from HDHomeRun" +
                    " because the command did not work => ", e);
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
                logger.error("Unable to get CCI from HDHomeRun" +
                        " because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to get CCI from HDHomeRun" +
                        " because the command did not work => ", e);
            }
        } else {
            returnValue = CopyProtection.NONE;
        }

        return returnValue;
    }

    private boolean isTuneLegacy() {
        return device.isLegacy() ||
                (encoderDeviceType != CaptureDeviceType.DCT_HDHOMERUN &&
                        HDHomeRunDiscoverer.getAlwaysTuneLegacy());
    }

    private static final HashSet<String> updateLineups = new HashSet<>();

    private static void updateAllChannelMappings(final HDHRNativeCaptureDevice captureDevice, final Logger logger, boolean async) {

        synchronized (updateLineups) {
            int currentLock = 0;
            try {
                currentLock = captureDevice.tuner.isLockedByThisComputer();
            } catch (IOException e) {
                logger.error("Unable to get if this computer has a lock on HDHomeRun capture" +
                        " device because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to get if this computer has a lock on HDHomeRun capture" +
                        " device because the command did not work => ", e);
            }

            if (currentLock == -1 || currentLock == 1) {
                if (updateLineups.contains(captureDevice.encoderLineup)) {
                    return;
                }
            } else {
                return;
            }

            updateLineups.add(captureDevice.encoderLineup);
        }

        Runnable updateChannels = new Runnable() {
            @Override
            public void run() {
                TVChannel tvChannels[] = ChannelManager.getChannelList(captureDevice.encoderLineup, true, true);

                long startTime = System.currentTimeMillis();

                for (TVChannel tvChannel : tvChannels) {
                    try {
                        captureDevice.tuner.setVirtualChannel(tvChannel.getChannel());
                    } catch (IOException e) {
                        logger.error("Unable to set virtual channel on HDHomeRun capture device" +
                                " because it cannot be reached => ", e);
                    } catch (GetSetException e) {
                        logger.error("Unable to set virtual channel on HDHomeRun capture device" +
                                " because the command did not work => ", e);
                    }

                    captureDevice.updateChannelMapping(tvChannel);
                }

                try {
                    captureDevice.tuner.clearVirtualChannel();
                } catch (IOException e) {
                    logger.error("Unable to clear virtual channel on HDHomeRun capture device" +
                            " because it cannot be reached => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to clear virtual channel on HDHomeRun capture device" +
                            " because the command did not work => ", e);
                }

                long endTime = System.currentTimeMillis();
                logger.info("Channel map update completed in {}ms.", endTime - startTime);

                synchronized (updateLineups) {
                    updateLineups.remove(captureDevice.encoderLineup);
                }
            }
        };


        if (async) {
            Thread updateChannelsThread = new Thread(updateChannels);
            updateChannelsThread.setName(
                    "HDHomeRunUpdateChannels-" +
                            updateChannelsThread.getId() +
                            ":" +
                            captureDevice.encoderName);

            updateChannelsThread.start();
        } else {
            updateChannels.run();
        }
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
