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

import opendct.capture.services.HTTPCaptureDeviceServices;
import opendct.capture.services.RTPCaptureDeviceServices;
import opendct.channel.*;
import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.producer.HTTPProducer;
import opendct.producer.RTPProducer;
import opendct.producer.SageTVProducer;
import opendct.sagetv.SageTVDeviceCrossbar;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class HDHRNativeCaptureDevice extends BasicCaptureDevice {
    private final static Logger logger = LogManager.getLogger(HDHRNativeCaptureDevice.class);

    private final HDHomeRunDiscoveredDeviceParent discoveredDeviceParent;
    private final HDHomeRunDiscoveredDevice discoveredDevice;
    private final HDHomeRunDevice device;
    private final HDHomeRunTuner tuner;

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();
    //private Thread tuningThread;
    private final Frequency lookupMap[];
    private long lastTuneTime = 0;

    private RTPCaptureDeviceServices rtpServices;
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
        // Determine device tuning mode.
        // =========================================================================================
        encoderDeviceType = CaptureDeviceType.HDHOMERUN;
        boolean cableCardPresent = false;

        try {
            if (device.isCableCardTuner()) {
                cableCardPresent = device.getCardStatus().toLowerCase().contains("card=ready");

                if (cableCardPresent) {
                    encoderDeviceType = CaptureDeviceType.DCT_HDHOMERUN;
                    setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dct"));
                } else {
                    encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                    setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
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
                        setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "atsc_" + device.getDeviceIdHex().toLowerCase()));
                        break;

                    case US_CABLE:
                        encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                        setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
                        break;
                    case EU_BCAST:
                        encoderDeviceType = CaptureDeviceType.DVBT_HDHOMERUN;
                        setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dvb-t"));
                        break;
                    case EU_CABLE:
                        encoderDeviceType = CaptureDeviceType.DVBC_HDHOMERUN;
                        setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dvb-c"));
                        break;
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

        switch (encoderDeviceType) {
            case DVBC_HDHOMERUN:
                lookupMap = Frequencies.EU_CABLE;
                break;
            case DVBT_HDHOMERUN:
                lookupMap = Frequencies.EU_BCAST;
                break;
            case ATSC_HDHOMERUN:
                lookupMap = Frequencies.US_BCAST;
                break;
            default:
                lookupMap = Frequencies.US_CABLE;
        }

        rtpServices = new RTPCaptureDeviceServices(encoderName, propertiesDeviceParent);

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
                        device.getDeviceIdHex());
            } else {
                newChannelLineup = new ChannelLineup(
                        encoderLineup,
                        encoderParentName,
                        ChannelSourceType.HDHOMERUN,
                        device.getDeviceIdHex());
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
                discoveredDeviceParent.getLocalAddress().getHostAddress(),
                cableCardPresent,
                encoderLineup,
                offlineChannelScan,
                rtpServices.getRtpLocalPort());
    }

    @Override
    public boolean isInternalLocked() {
        return locked.get();
    }

    @Override
    public boolean setLocked(boolean locked) {
        // This means the lock was already set
        if (this.locked.getAndSet(locked) == locked) {
            logger.info("Capture device is was already {}.", (locked ? "locked" : "unlocked"));
            return false;
        }

        synchronized (exclusiveLock) {
            boolean messageLock = this.locked.getAndSet(locked);

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
                if (discoveredDevice.getForceExternalUnlock()) {
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
                if (discoveredDevice.getForceExternalUnlock()) {
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

        if (isInternalLocked() || isExternalLocked()) {
            return false;
        }

        synchronized (exclusiveLock) {
            // Return immediately if an exclusive lock was set between here and the first check if
            // there is an exclusive lock set.
            if (isInternalLocked()) {
                return logger.exit(false);
            }

            if (!startEncoding(tvChannel.getChannel(), null, "", 0, SageTVDeviceCrossbar.DIGITAL_TV_TUNER, 0, 0, null)) {
                return logger.exit(false);
            }

            int offlineDetectionMinBytes = HDHomeRunDiscoverer.getOfflineDetectionMinBytes();
            int timeout = HDHomeRunDiscoverer.getOfflineDetectionSeconds();

            if (!skipCCI) {
                CopyProtection copyProtection = getCopyProtection();
                while ((copyProtection == CopyProtection.NONE ||
                        copyProtection == CopyProtection.UNKNOWN) &&
                        timeout-- > 0) {

                    if (isInternalLocked()) {
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
                tvChannel.setProgram(tuner.getProgram());
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
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, SageTVDeviceCrossbar deviceType, int crossbarIndex, int uploadID, InetAddress remoteAddress) {
        logger.entry(channel, filename, encodingQuality, bufferSize, deviceType, crossbarIndex, uploadID, remoteAddress);

        //tuningThread = Thread.currentThread();

        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);
        String dotChannel = channel;

        int firstIndex = channel.indexOf("-");
        int secondIndex = channel.lastIndexOf("-");

        if (firstIndex != -1 && firstIndex != secondIndex) {

            // This string is used as a last resort when attempting to tune a channel.
            dotChannel = channel.substring(firstIndex + 1).replace("-", ".");

            // This will automatically create channels for channels that SageTV requests.
            if (encoderDeviceType != CaptureDeviceType.DCT_HDHOMERUN && tvChannel == null) {
                String vfChannel = channel.substring(0, firstIndex);
                String vChannel = channel.substring(firstIndex + 1);

                tvChannel = ChannelManager.getChannel(encoderLineup, vChannel);

                if (tvChannel == null) {
                    tvChannel = new TVChannelImpl(vChannel.replace("-", "."), vChannel);
                }

                try {
                    int fChannel = Integer.parseInt(vfChannel);

                    if (fChannel < 2 || fChannel > lookupMap.length ) {
                        logger.error("The channel number {} is not a valid {} channel number.", fChannel, lookupMap[5].STANDARD);
                    } else {
                        tvChannel.setFrequency(lookupMap[fChannel].FREQUENCY);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Unable to parse '{}' into int => ", vfChannel, e);
                }

                tvChannel.setChannelRemap(channel);
                ChannelManager.updateChannel(encoderLineup, tvChannel);
            }
        }

        synchronized (exclusiveLock) {
            return startEncodingSync(
                    channel,
                    filename,
                    encodingQuality,
                    bufferSize,
                    uploadID,
                    remoteAddress,
                    dotChannel,
                    tvChannel);
        }
    }

    private boolean startEncodingSync(String channel, String filename, String encodingQuality,
                                      long bufferSize, int uploadID, InetAddress remoteAddress,
                                      String dotChannel, TVChannel tvChannel) {

        boolean retune = false;
        boolean scanOnly = (filename == null);

        if (recordLastFilename != null && recordLastFilename.equals(filename)) {
            retune = true;
            logger.info("Re-tune: {}", getTunerStatusString());
        } else {
            recordLastFilename = filename;
        }

        long currentTime = System.currentTimeMillis();
        if (retune) {
            if (currentTime - lastTuneTime < 2000) {
                logger.info("Re-tune came back too fast. Skipping.");
                return true;
            }
        }

        // Only lock the HDHomeRun if this device is locked too. This will prevent any offline
        // activities from taking the tuner away from outside programs.
        if (isInternalLocked()) {
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

                /*if (tuningThread != Thread.currentThread()) {
                    return logger.exit(false);
                }*/
            }
        }

        if (!httpProducing) {
            // The producer and consumer methods are requested to not block. If they don't shut down in
            // time, it will be caught and handled later. This gives us a small gain in speed.
            rtpServices.stopProducing(false);
        } else {
            httpServices.stopProducing(false);
        }

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (!retune) {
            stopConsuming(false);
        }

        // Get a new producer and consumer.
        RTPProducer newRTPProducer = rtpServices.getNewRTPProducer(propertiesDeviceParent);
        SageTVConsumer newConsumer;

        // If we are trying to restart the stream, we don't need to get a new consumer.
        if (retune) {
            newConsumer = sageTVConsumerRunnable;
        } else if (scanOnly) {
            newConsumer = getNewChannelScanSageTVConsumer();
            newConsumer.consumeToNull(true);
        } else {
            newConsumer = getNewSageTVConsumer(channel);
        }

        if (!newConsumer.acceptsUploadID()) {
            remoteAddress = null;
        }

        if (remoteAddress != null) {
            logger.info("{} the encoding for the channel '{}' from the device '{}' to the file '{}' via the upload id '{}'...", retune ? "Retuning" : "Starting", channel, encoderName, filename, uploadID);
        } else if (filename != null) {
            logger.info("{} the encoding for the channel '{}' from the device '{}' to the file '{}'...", retune ? "Retuning" : "Starting", channel, encoderName, filename);
        } else {
            logger.info("Starting a channel scan for the channel '{}' from the device '{}'...", channel, encoderName);
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
            case DVBC_HDHOMERUN:
            case DVBT_HDHOMERUN:
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
                }
                break;
            case QAM_HDHOMERUN:
                if (tvChannel == null || HDHomeRunDiscoverer.getQamAlwaysRemapLookup()) {
                    TVChannel qamTvChannel = ChannelManager.autoDctToQamMap(
                            this,
                            encoderLineup,
                            new TVChannelImpl(channel, channel),
                            tvChannel == null);

                    if (qamTvChannel != null) {
                        tvChannel = qamTvChannel;
                    }

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
                    int frequency = tvChannel.getFrequency();
                    int program = tvChannel.getProgram();

                    if (!isTuneLegacy() &&
                            !HDHomeRunDiscoverer.getQamRemap()) {

                        if (HDHomeRunDiscoverer.getAllowHttpTuning()) {
                            httpProducing = tuneUrl(
                                    tvChannel,
                                    HDHomeRunDiscoverer.getTranscodeProfile(),
                                    newConsumer);
                        }

                        if (!httpProducing) {
                            tuner.setVirtualChannel(tvChannel.getChannel());
                        }

                        break;
                    }

                    if (modulation == null) {
                        logger.debug("The channel '{}' does not have a modulation" +
                                " on the lineup '{}'. Using auto.", channel, encoderLineup);
                        modulation = "auto";
                    }

                    if (frequency <= 0) {
                        logger.error("The channel '{}' does not have a frequency" +
                                " on the lineup '{}'.", channel, encoderLineup);
                        return logger.exit(false);
                    }

                    if (program == 0) {
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
                                    program != tProgram) {

                                tvChannel.setChannelRemap("");
                                ChannelManager.updateChannel(encoderLineup, tvChannel);

                                logger.info("The virtual channel {} no longer has the correct" +
                                        " program and/or frequency. Finding new virtual channel.",
                                        tvChannel.getChannel());

                                tvChannel = ChannelManager.getChannel(encoderLineup, channel);

                                if (tvChannel == null || HDHomeRunDiscoverer.getQamAlwaysRemapLookup()) {
                                    tvChannel = ChannelManager.autoDctToQamMap(
                                            this,
                                            encoderLineup,
                                            new TVChannelImpl(channel, channel),
                                            tvChannel == null);
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
                                if (program == 0) {
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

                                /*if (tuningThread != Thread.currentThread()) {
                                    return false;
                                }*/

                                if (Util.isNullOrEmpty(qamChannel.getUrl())) {
                                    continue;
                                }

                                try {
                                    tuner.setVirtualChannel(qamChannel.getChannel());

                                    String tFrequency = tuner.getChannel();
                                    int tProgram = tuner.getProgram();

                                    if (tFrequency.endsWith(String.valueOf(":" + frequency)) &&
                                            program == tProgram) {

                                        qamChannel.setChannelRemap(tvChannel.getChannel());
                                        qamChannel.setModulation(modulation);
                                        qamChannel.setFrequency(frequency);
                                        qamChannel.setProgram(program);

                                        ChannelManager.updateChannel(encoderLineup, qamChannel);

                                        anyUrl = qamChannel;

                                        // If we don't sleep here, the HDHomeRun sometimes gives a
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

                                                int optProgram = tuner.getProgram();

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

            if (!rtpServices.startProducing(newRTPProducer, newConsumer, discoveredDeviceParent.getRemoteAddress(), rtpServices.getRtpLocalPort(), encoderName)) {
                logger.error("The producer thread using the implementation '{}' failed to start.",
                        newRTPProducer.getClass().getSimpleName());

                return logger.exit(false);
            }

            try {
                tuner.setTarget("rtp://" + discoveredDeviceParent.getLocalAddress().getHostAddress() + ":" + rtpServices.getRtpLocalPort());
            } catch (IOException e) {
                logger.error("HDHomeRun is unable to start RTP because the device could not be reached => ", e);
                return logger.exit(false);
            } catch (GetSetException e) {
                logger.error("HDHomeRun is unable to start RTP because the command did not work => ", e);
                return logger.exit(false);
            }
        }

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (!retune) {
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

                    /*if (tuningThread != Thread.currentThread()) {
                        rtpServices.stopProducing(false);
                        logger.info("tuningThread != Thread.currentThread()");
                        return logger.exit(false);
                    }*/
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
                if (!newConsumer.consumeToUploadID(filename, uploadID, remoteAddress)) {
                    return logger.exit(false);
                }
            } else if (!scanOnly) {
                if (!newConsumer.consumeToFilename(filename)) {
                    return logger.exit(false);
                }
            }

            startConsuming(channel, newConsumer, encodingQuality, bufferSize);
        } else {
            logger.info("Consumer is already running; this is a re-tune and it does not need to restart.");
        }

        long streamingWaitTime = HDHomeRunDiscoverer.getStreamingWait();
        long streamingWaitInterval = streamingWaitTime / 10;
        CopyProtection copyProtection = getCopyProtection();

        if (streamingWaitInterval <= 100 || copyProtection == CopyProtection.COPY_FREELY) {
            sageTVConsumerRunnable.isStreaming(streamingWaitTime);
        } else {
            for (int i = 0; i < 10; i++) {
                if (sageTVConsumerRunnable.isStreaming(streamingWaitInterval)) {
                    break;
                }

                if (copyProtection != CopyProtection.COPY_FREELY) {
                    copyProtection = getCopyProtection();
                }

                if (copyProtection == CopyProtection.COPY_ONCE ||
                        copyProtection == CopyProtection.COPY_NEVER) {

                    break;
                }
            }
        }

        lastTuneTime = System.currentTimeMillis();
        return logger.exit(true);
    }

    private boolean legacyTuneChannel(String channel) {
        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);

        if (tvChannel != null && tvChannel.getFrequency() > 0 && tvChannel.getProgram() > 0) {
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
            case DVBC_HDHOMERUN:
            case DVBT_HDHOMERUN:
            case ATSC_HDHOMERUN:
                if (channelNumber <= 1 || channelNumber > lookupMap.length) {
                    logger.error("legacyTuneChannel: The channel number {} is not a valid ATSC channel.");
                    return false;
                }

                fChannel = lookupMap[channelNumber].FREQUENCY;
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
                    if (program.PROGRAM != 0 && program.CHANNEL != null && program.CHANNEL.equals(programName)) {

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

        String tunerUrl = channel.getUrl();

        //  Change the IP address to match the tuner we are actually trying to use.
        try {
            URL tempUrl = new URL(tunerUrl);
            tunerUrl = "http://" + device.getIpAddress().getHostAddress() + ":" + tempUrl.getPort() + tempUrl.getFile();
        } catch (MalformedURLException e) {
            logger.error("'{}' does not appear to be a valid URL => ", tunerUrl, e);
            return false;
        }

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
                httpServices.startProducing(encoderName, httpProducer, newConsumer, false, tuneUrl);

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
                    case DVBC_HDHOMERUN:
                    case DVBT_HDHOMERUN:
                    case DCT_HDHOMERUN:
                    case QAM_HDHOMERUN:
                        break;
                }
            }
        } catch (Exception e) {
            logger.error("Unable to get frequency from capture device => ", e);
        }

        try {
            tvChannel.setProgram(tuner.getProgram());
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
            case DVBC_HDHOMERUN:
            case DVBT_HDHOMERUN:
            case ATSC_HDHOMERUN:
                if (!isTuneLegacy() && ChannelManager.hasChannels(encoderLineup)) {
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

                if (scanChannelIndex++ > lookupMap.length) {
                    return "ERROR";
                }

                try {
                    tuner.setChannel(
                            "auto",
                            lookupMap[scanChannelIndex].FREQUENCY,
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
                HDHomeRunProgram programs[] = null;

                timeout = 12;
                while (timeout-- > 0 && !Thread.currentThread().isInterrupted()) {
                    try {
                        streamInfo = tuner.getStreamInfo();

                        if (streamInfo != null && streamInfo.getProgramsRaw().length > 0) {
                            programs = streamInfo.getProgramsParsed();
                            boolean incompleteInfo = false;

                            // Sometimes, it takes longer for the HDHomeRun to detect the info
                            // for the tuned channel (guide number and callsign), and sometimes,
                            // even when it has this info, it hasn't detected the datastreams yet.
                            // We will check for these conditions and get streaminfo again.
                            for (HDHomeRunProgram program : programs) {
                                if (program.NO_DATA || program.CHANNEL.equals("0")) {
                                	incompleteInfo = true;
                                    break;
                                }
                            }

                            if (!incompleteInfo) {
                                break;
                            }
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

                if (streamInfo != null && programs != null) {

                    StringBuilder stringBuilder = new StringBuilder();
                    StringBuilder tuneChannel = new StringBuilder();

                    for (HDHomeRunProgram program : programs) {
                        if (!program.isTunable()) {
                            continue;
                        }

                        tuneChannel.setLength(0);
                        tuneChannel.append(scanChannelIndex)
                                .append("-")
                                .append(program.CHANNEL.replace(".", "-"));
                        stringBuilder.append(tuneChannel)
                        		.append("(")
                        		.append(program.CALLSIGN)
                        		.append(")")
                        		.append("ATSC")
                        		.append(";");

                        TVChannel tvChannel = new TVChannelImpl(tuneChannel.toString(), program.CHANNEL, true, program.CALLSIGN, "", "auto", lookupMap[scanChannelIndex].FREQUENCY, program.PROGRAM, 100, CopyProtection.NONE, false);
                        ChannelManager.addChannel(encoderLineup, tvChannel);
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

    @Override
    public long getProducedPackets() {
        SageTVProducer producer;

        if (httpProducing) {
            if (httpServices != null) {
                producer = httpServices.getProducer();
            } else {
                producer = rtpServices.getProducer();
            }
        } else {
            producer = rtpServices.getProducer();
        }

        if (producer != null) {
            return producer.getPackets();
        }

        return 0;
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
            if (httpProducing) {
                httpServices.stopProducing(false);
                httpProducer = null;
            } else {
                rtpServices.stopProducing(false);
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
            if (isInternalLocked()) {
                setExternalLock(false);
            }
        }

        logger.exit();
    }

    @Override
    public void stopDevice() {
        logger.entry();

        if (httpProducing) {
            httpServices.stopProducing(false);
            httpProducer = null;
        } else {
            rtpServices.stopProducing(false);
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
}
