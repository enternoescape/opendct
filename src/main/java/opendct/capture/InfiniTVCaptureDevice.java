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

import opendct.capture.services.RTPCaptureDeviceServices;
import opendct.channel.*;
import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.SageTVConsumer;
import opendct.producer.RTPProducer;
import opendct.producer.SageTVProducer;
import opendct.sagetv.SageTVDeviceType;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import opendct.tuning.http.InfiniTVStatus;
import opendct.tuning.http.InfiniTVTuning;
import opendct.tuning.upnp.InfiniTVDiscoveredDevice;
import opendct.tuning.upnp.InfiniTVDiscoveredDeviceParent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class InfiniTVCaptureDevice extends BasicCaptureDevice {
    private final static Logger logger = LogManager.getLogger(InfiniTVCaptureDevice.class);

    private final InfiniTVDiscoveredDeviceParent parent;
    private final InfiniTVDiscoveredDevice device;

    private final RTPCaptureDeviceServices rtpServices;
    private final int encoderNumber;

    private final AtomicBoolean locked;
    private final Object exclusiveLock;

    private Thread tuningThread;

    public InfiniTVCaptureDevice(InfiniTVDiscoveredDevice device,
                                 InfiniTVDiscoveredDeviceParent parent)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {

        super(parent.getFriendlyName(), device.getFriendlyName(), parent.getParentId(), device.getId());

        this.device = device;
        this.parent = parent;

        locked = new AtomicBoolean(false);
        exclusiveLock = new Object();

        // This should never be turned into a global variable because it can change.
        String encoderAddress = parent.getRemoteAddress().getHostAddress();

        try {
            logger.debug("Determining the encoder number...");
            encoderNumber =
                    Integer.parseInt(device.getName().substring(device.getName().length() - 1));

        } catch (NumberFormatException e) {
            logger.error("Unable to parse the encoder number from '{}'", device.getName());
            throw new CaptureDeviceLoadException("Unable to parse the encoder number.");
        }

        boolean cableCardPresent = false;

        try {
            String cardStatus = InfiniTVStatus.getVar(
                    encoderAddress, encoderNumber, "cas", "CardStatus");

            if (cardStatus == null) {
                // We only reference the properties if we can't get the status the best way.
                cableCardPresent =
                        Config.getBoolean(propertiesDeviceParent + "cable_card_inserted", false);

                logger.warn("Unable to read the CableCARD status. Using the value '{}' from" +
                        " properties.", cableCardPresent);
            } else {
                cableCardPresent = cardStatus.toLowerCase().contains("inserted");

                // Update properties with the currently known cable card status.
                Config.setBoolean(propertiesDeviceParent + "cable_card_inserted", cableCardPresent);
            }
        } catch (IOException e) {
            // We only reference the properties if we can't get the status the best way.
            cableCardPresent =
                    Config.getBoolean(propertiesDeviceParent + "cable_card_inserted", false);

            logger.warn("Unable to read the CableCARD status. Using the value '{}' from" +
                    " properties => ", cableCardPresent, e);
        }

        if (cableCardPresent) {
            encoderDeviceType = CaptureDeviceType.DCT_INFINITV;
            setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dct"));
        } else {
            encoderDeviceType = CaptureDeviceType.QAM_INFINITV;
            setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
        }

        try {
            InfiniTVStatus.getVar(encoderAddress, encoderNumber, "diag", "Streaming_IP");
        } catch (IOException e) {
            logger.warn("HTTP tuning has been requested on this capture device, but it can't" +
                    " support it.");
            if (encoderDeviceType == CaptureDeviceType.QAM_INFINITV) {
                logger.error("This device is configured for QAM and HTTP tuning is not available," +
                        " you may not be able to use it.");
            }
            throw new CaptureDeviceLoadException("This capture device does not support tuning via" +
                    " the web interface. Upgrade your firmware.");
        }

        rtpServices = new RTPCaptureDeviceServices(encoderName, propertiesDeviceParent);

        setChannelLineup(
                Config.getString(propertiesDeviceParent + "lineup",
                String.valueOf(encoderDeviceType).toLowerCase()));

        if (!ChannelManager.hasChannels(encoderLineup) &&
                encoderLineup.equals(String.valueOf(encoderDeviceType).toLowerCase())) {

            ChannelLineup newChannelLineup;

            if (encoderDeviceType == CaptureDeviceType.DCT_INFINITV) {
                newChannelLineup = new ChannelLineup(
                        encoderLineup,
                        encoderParentName,
                        ChannelSourceType.INFINITV,
                        encoderAddress);
            } else {
                // The lineup on the InfiniTV QAM devices is always stale. This will make a copy
                // from an InfiniTV DCT if one exists.
                newChannelLineup = new ChannelLineup(
                        encoderLineup,
                        encoderParentName,
                        ChannelSourceType.COPY,
                        String.valueOf(CaptureDeviceType.DCT_INFINITV).toLowerCase());
            }

            ChannelManager.updateChannelLineup(newChannelLineup);
            ChannelManager.addChannelLineup(newChannelLineup, true);
            ChannelManager.saveChannelLineup(encoderLineup);
        }

        logger.info("Encoder Manufacturer: 'Ceton'," +
                        " Number: {}," +
                        " Remote IP: '{}'," +
                        " Local IP: '{}'," +
                        " CableCARD: {}," +
                        " Lineup: '{}'," +
                        " Offline Scan Enabled: {}," +
                        " RTP Port: {}",
                encoderNumber,
                encoderAddress,
                parent.getLocalAddress().getHostAddress(),
                cableCardPresent,
                encoderLineup,
                offlineChannelScan,
                rtpServices.getRtpLocalPort());
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
            logger.info("Capture device was already {}.", (locked ? "locked" : "unlocked"));
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
        // This device doesn't have any locking mechanism, so it always says the lock is not set.
        return false;
    }

    @Override
    public boolean setExternalLock(boolean locked) {
        // This device doesn't have any locking mechanism, so it always says the lock was set
        // successfully.
        return true;
    }

    @Override
    /**
     * Tunes a channel outside of any requests from the SageTV server and updates information about
     * the channel.
     *
     * @param tvChannel A TVChannel object with at the very least a defined channel or frequency and
     *                  program. Otherwise there is nothing to tune.
     * @param skipCCI If <i>true</i>, the method will not wait to ensure the CCI is correct. The
     *                reason to skip this is because it takes much longer for unencrypted channels.
     * @return <i>true</i> if the test was complete and successful. <i>false</i> if we should try
     *         again on a different capture device since this one is currently locked.
     */
    public boolean getChannelInfoOffline(TVChannel tvChannel, boolean skipCCI) {
        logger.entry(tvChannel);

        if (isLocked() || isExternalLocked()) {
            return logger.exit(false);
        }

        synchronized (exclusiveLock) {
            // Return immediately if an exclusive lock was set between here and the first check if
            // there is an exclusive lock set.
            if (isLocked()) {
                return logger.exit(false);
            }

            if (!startEncoding(tvChannel.getChannel(), null, "", 0, SageTVDeviceType.DIGITAL_TV_TUNER, 0, null)) {
                return logger.exit(false);
            }

            int offlineDetectionMinBytes = UpnpDiscoverer.getOfflineDetectionMinBytes();
            int timeout = UpnpDiscoverer.getOfflineDetectionSeconds();

            while (sageTVConsumerRunnable != null && sageTVConsumerRunnable.getIsRunning() &&
                    getRecordedBytes() < offlineDetectionMinBytes && timeout-- > 0) {

                if (isLocked()) {
                    stopEncoding();
                    return logger.exit(false);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return logger.exit(false);
                }
            }

            CopyProtection copyProtection = getCopyProtection();

            if (!skipCCI) {
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
            }

            tvChannel.setSignalStrength(getSignalStrength());

            /*if (encoderDeviceType == CaptureDeviceType.DCT_HDHOMERUN) {
                String encoderAddress = parent.getRemoteAddress().getHostAddress();

                try {
                    tvChannel.setModulation(
                            InfiniTVStatus.getModulationString(encoderAddress, encoderNumber));
                } catch (IOException e) {
                    logger.error("Unable to get current modulation because the tuner {} on the" +
                            " device at address {} is unreachable.", encoderNumber, encoderAddress);
                }


                try {
                    int frequency = InfiniTVStatus.getFrequency(encoderAddress, encoderNumber);

                    if (frequency > 0) {
                        tvChannel.setFrequency(frequency);
                    }
                } catch (IOException e) {
                    logger.error("Unable to get current frequency because the tuner {} on the" +
                            " device at address {} is unreachable.", encoderNumber, encoderAddress);
                }

                try {
                    int program = InfiniTVStatus.getProgram(encoderAddress, encoderNumber);

                    if (program > 0) {
                        tvChannel.setProgram(String.valueOf(program));
                    }
                } catch (IOException e) {
                    logger.error("Unable to get current program because the tuner {} on the" +
                            " device at address {} is unreachable.", encoderNumber, encoderAddress);
                }
            }*/

            if (copyProtection == CopyProtection.COPY_FREELY ||
                    copyProtection == CopyProtection.NONE) {

                tvChannel.setTunable(getRecordedBytes() > offlineDetectionMinBytes);
            } else {
                tvChannel.setTunable(false);
            }

            stopEncoding();
        }

        return logger.exit(true);
    }

    @Override
    public void stopDevice() {
        stopEncoding();
    }

    @Override
    public void stopEncoding() {
        synchronized (exclusiveLock) {
            rtpServices.stopProducing(false);
            super.stopEncoding();

            InfiniTVTuning.stopRTSP(parent.getRemoteAddress().getHostAddress(), encoderNumber);
        }
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, SageTVDeviceType deviceType, int uploadID, InetAddress remoteAddress) {
        logger.entry(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);

        // This is used to detect a second tuning attempt while a tuning is still in progress. When
        // the current tuning attempt sees that another thread is waiting, it gives up and lets the
        // waiting thread start tuning.
        tuningThread = Thread.currentThread();

        TVChannel tvChannel = null;

        if(encoderDeviceType == CaptureDeviceType.QAM_INFINITV) {
            tvChannel = ChannelManager.getChannel(encoderLineup, channel);

            if (tvChannel == null) {
                tvChannel = new TVChannelImpl(channel, "Unknown");
            }

            if (tvChannel.getFrequency() <= 0 || tvChannel.getProgram() <= 0) {
                tvChannel = ChannelManager.autoDctToQamMap(this, encoderLineup, tvChannel);
            }

            if (tvChannel == null) {
                logger.error("Unable to tune ClearQAM channel because" +
                        " the virtual channel cannot be mapped.");

                return logger.exit(false);
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
                    tvChannel);
        }
    }

    private boolean startEncodingSync(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress, TVChannel tvChannel) {
        logger.entry(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress, tvChannel);

        boolean retune = false;
        boolean scanOnly = (filename == null);

        if (recordLastFilename != null && recordLastFilename.equals(filename)) {
            retune = true;
            logger.info("Re-tune: {}", getTunerStatusString());
        } else {
            recordLastFilename = filename;
        }

        // The producer and consumer methods are requested to not block. If they don't shut down in
        // time, it will be caught and handled later. This gives us a small gain in speed.
        rtpServices.stopProducing(false);

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (!retune) {
            stopConsuming(false);
        }

        // Get a new producer and consumer.
        RTPProducer newRTPProducer = rtpServices.getNewRTPProducer(propertiesDeviceParent);
        SageTVConsumer newConsumer;

        // If we are trying to restart the stream, we don't need to create a new consumer.
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

        InetAddress iEncoderAddress = parent.getRemoteAddress();
        String encoderAddress = iEncoderAddress.getHostAddress();

        InetAddress iLocalAddress = parent.getLocalAddress();
        String localAddress = iLocalAddress.getHostAddress();

        try {
            switch (encoderDeviceType) {
                case DCT_INFINITV:
                    InfiniTVTuning.tuneVChannel(channel, encoderAddress, encoderNumber, 5);
                    break;
                case QAM_INFINITV:
                    InfiniTVTuning.tuneQamChannel(
                            tvChannel,
                            encoderAddress,
                            encoderNumber,
                            5);
                    break;
                default:
                    logger.error("This device has been assigned an " +
                            "unsupported capture device type: {}", encoderDeviceType);
                    return logger.exit(false);
            }
        } catch (InterruptedException e) {
            logger.debug("Tuning was interrupted => ", e);
            return logger.exit(false);
        }

        logger.info("Configuring and starting the new RTP producer...");

        if (!rtpServices.startProducing(
                newRTPProducer, newConsumer, iEncoderAddress,
                rtpServices.getRtpLocalPort(), encoderName)) {

            logger.error("The producer thread using the implementation '{}' failed to start.",
                    newRTPProducer.getClass().getSimpleName());

            return logger.exit(false);
        }

        if (!InfiniTVTuning.startRTSP(
                localAddress, rtpServices.getRtpLocalPort(), encoderAddress, encoderNumber)) {

            logger.error("Unable to start RTSP. Will try again on re-tune.");
        }

        if (!retune) {
            try {
                int getProgram = InfiniTVStatus.getProgram(encoderAddress, encoderNumber, 5);
                newConsumer.setProgram(getProgram);

                int timeout = 20;

                while (newConsumer.getProgram() == -1) {
                    Thread.sleep(100);
                    getProgram = InfiniTVStatus.getProgram(encoderAddress, encoderNumber, 2);
                    newConsumer.setProgram(getProgram);

                    if (timeout-- < 0) {
                        logger.error("Unable to get program after more than 2 seconds.");
                        return logger.exit(false);
                    }

                    if (tuningThread != Thread.currentThread()) {
                        rtpServices.stopProducing(false);
                        return logger.exit(false);
                    }
                }
            } catch (IOException e) {
                logger.error("Unable to get program number => ", e);
            } catch (InterruptedException e) {
                logger.debug("Interrupted while trying to get program number => ", e);
                return logger.exit(false);
            }

            // If we are trying to restart the stream, we don't need to stop the consumer.
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

        long streamingWaitTime = UpnpDiscoverer.getStreamingWait();
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
                        copyProtection == CopyProtection.COPY_NEVER ||
                        tuningThread != Thread.currentThread()) {

                    break;
                }
            }
        }

        return logger.exit(true);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public BroadcastStandard getBroadcastStandard() {
        BroadcastStandard returnValue = BroadcastStandard.QAM256;

        try {
            returnValue = InfiniTVStatus.getModulation(
                    parent.getRemoteAddress().getHostAddress(), encoderNumber);
        } catch (IOException e) {
            logger.debug("Unable to get broadcast standard from capture device.");
        }

        return returnValue;
    }

    @Override
    public int getSignalStrength() {
        logger.entry();

        int signal = 0;

        try {
            signal = (int)InfiniTVStatus.getSignalNoiseRatio(
                    parent.getRemoteAddress().getHostAddress(), encoderNumber);
        } catch (IOException e) {
            logger.debug("Unable to get signal noise ratio from capture device.");
        }

        return logger.exit(signal);
    }

    @Override
    public CopyProtection getCopyProtection() {
        logger.entry();

        CopyProtection returnValue = CopyProtection.UNKNOWN;

        try {
            returnValue = InfiniTVStatus.getCCIStatus(
                    parent.getRemoteAddress().getHostAddress(), encoderNumber);
        } catch (Exception e) {
            logger.debug("Unable to get CCI status from capture device.");
        }

        return logger.exit(returnValue);
    }

    public String getTunerStatusString() {
        logger.entry();

        StringBuilder stringBuilder = new StringBuilder();

        String encoderAddress = parent.getRemoteAddress().getHostAddress();

        try {
            stringBuilder.append("CarrierLock: ")
                    .append(InfiniTVStatus.getCarrierLock(encoderAddress, encoderNumber));
        } catch (Exception e) {
            logger.debug("Unable to get CarrierLock status from capture device.");
        }

        try {
            stringBuilder.append(", PCRLock: ")
                    .append(InfiniTVStatus.getPCRLock(encoderAddress, encoderNumber));
        } catch (Exception e) {
            logger.debug("Unable to get PCRLock status from capture device.");
        }

        try {
            stringBuilder.append(", StreamingIP: ")
                    .append(InfiniTVStatus.getStreamingIP(encoderAddress, encoderNumber));
        } catch (Exception e) {
            logger.debug("Unable to get StreamingIP status from capture device.");
        }

        try {
            stringBuilder.append(", StreamingPort: ")
                    .append(InfiniTVStatus.getStreamingPort(encoderAddress, encoderNumber));
        } catch (Exception e) {
            logger.debug("Unable to get StreamingPort status from capture device.");
        }

        try {
            stringBuilder.append(", Temperature: ")
                    .append(InfiniTVStatus.getTemperature(encoderAddress, encoderNumber));
        } catch (Exception e) {
            logger.debug("Unable to get Temperature status from capture device.");
        }

        try {
            stringBuilder.append(", TransportState: ")
                    .append(InfiniTVStatus.getTransportState(encoderAddress, encoderNumber));
        } catch (Exception e) {
            logger.debug("Unable to get TransportState status from capture device.");
        }

        return logger.exit(stringBuilder.toString());
    }

    @Override
    public long getProducedPackets() {
        if (rtpServices == null) {
            return 0;
        }

        SageTVProducer producer = rtpServices.getProducer();

        if (producer != null) {
            return producer.getPackets();
        }

        return 0;
    }

    @Override
    public String scanChannelInfo(String channel) {
        return super.scanChannelInfo(channel, true);
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {

    }
}
