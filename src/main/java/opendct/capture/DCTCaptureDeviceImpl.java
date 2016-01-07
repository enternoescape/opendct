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

package opendct.capture;

import opendct.channel.*;
import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.producer.RTPProducer;
import opendct.sagetv.SageTVManager;
import opendct.tuning.hdhomerun.GetSetException;
import opendct.tuning.hdhomerun.HDHomeRunDevice;
import opendct.tuning.hdhomerun.HDHomeRunTuner;
import opendct.tuning.hdhomerun.returns.HDHomeRunStatus;
import opendct.tuning.hdhomerun.returns.HDHomeRunVStatus;
import opendct.tuning.http.InfiniTVStatus;
import opendct.tuning.http.InfiniTVTuning;
import opendct.tuning.upnp.UpnpManager;
import opendct.tuning.upnp.services.avtransport.AVTransportAction;
import opendct.tuning.upnp.services.avtransport.AVTransportSubscription;
import opendct.tuning.upnp.services.avtransport.returns.GetMediaInfo;
import opendct.tuning.upnp.services.cas.CASAction;
import opendct.tuning.upnp.services.cas.CASSubscription;
import opendct.tuning.upnp.services.cas.returns.GetCardStatus;
import opendct.tuning.upnp.services.connectionmanager.ConnectionManagerAction;
import opendct.tuning.upnp.services.connectionmanager.ConnectionManagerSubscription;
import opendct.tuning.upnp.services.connectionmanager.returns.GetProtocolInfo;
import opendct.tuning.upnp.services.connectionmanager.returns.PrepareForConnection;
import opendct.tuning.upnp.services.mux.MuxAction;
import opendct.tuning.upnp.services.tuner.TunerAction;
import opendct.tuning.upnp.services.tuner.TunerSubscription;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.ServiceType;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.transport.spi.InitializationException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DCTCaptureDeviceImpl extends RTPCaptureDevice implements CaptureDevice {
    private final Logger logger = LogManager.getLogger(DCTCaptureDeviceImpl.class);

    // Direct access to the capture device via UPnP services.
    //private Device dctDevice;

    //Device services.
    private final Service connectionManagerService;
    private final Service casService;
    private final Service avTransportService;
    private final Service tunerService;
    private final Service muxService;

    // DCT GENA event subscriptions.
    private final ConnectionManagerSubscription connectionManagerSubscription;
    private final CASSubscription casSubscription;
    private final AVTransportSubscription avTransportSubscription;
    private final TunerSubscription tunerSubscription;

    // DCT service action classes.
    private final ConnectionManagerAction connectionManagerAction;
    private final CASAction casAction;
    private final AVTransportAction avTransportAction;
    private final TunerAction tunerAction;
    private final MuxAction muxAction;

    // DCT values that should not change or are referenced many times.
    private String connectionManagerSourceProtocol;
    private String connectionManagerAVTransportID;

    // This is the amount of time in seconds that we should wait for a channel to report that it is
    // COPY_FREELY and to wait for data to be output to null.
    private int offlineDetectionWait =
            Config.getInteger("upnp.dct.wait_for_offline_detection_s", 8);
    private long offlineDetectionMinBytes =
            Config.getLong("upnp.dct.offline_detection_min_bytes", 18800);

    private boolean offlineScan = false;

    private int encoderNumber = -1;
    private boolean cableCardPresent = false;
    private String encoderIPAddress = null;
    private InetAddress localIPAddress = null;

    private int retuneTimeout =
            Config.getInteger("upnp.retune_poll_s", 1) * 1000;
    private boolean httpTune =
            Config.getBoolean("upnp.dct.http_tuning", true);
    private boolean hdhrTune =
            Config.getBoolean("upnp.dct.hdhr_tuning", true);
    private boolean autoMapReference =
            Config.getBoolean("upnp.qam.automap_reference_lookup", true);
    private boolean autoMapTuning =
            Config.getBoolean("upnp.qam.automap_tuning_lookup", false);
    private HDHomeRunTuner hdhrTuner = null;
    private boolean forceExternalUnlock =
            Config.getBoolean(propertiesDeviceRoot + "always_force_external_unlock", false);

    private Thread monitorThread = null;
    private volatile Thread tuningThread = null;
    private AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();

    // When this is enabled, the DCT is put into a state that is only waiting for a channel to be
    // requested.
    private boolean fastTune = Config.getBoolean("upnp.dct.fast_tuning", false);
    private boolean hdhrLock = Config.getBoolean("hdhr.locking", true);

    /**
     * Create a new DCT capture device.
     *
     * @param dctDevice The cling UPnP device to create this tuner.
     * @throws InitializationException Thrown if any part of the tuner is missing.
     * @throws CaptureDeviceIgnoredException Thrown if this tuner is being blocked from use.
     */
    public DCTCaptureDeviceImpl(Device dctDevice) throws InitializationException, CaptureDeviceIgnoredException {
        super(dctDevice.getRoot().getDetails().getFriendlyName(), "DCT-" + dctDevice.getDetails().getFriendlyName());
        logger.entry(dctDevice);

        //this.dctDevice = dctDevice;
        UpnpService upnpService = UpnpManager.getUpnpService();

        // Connection to ConnectionManager service.
        if ((connectionManagerService = dctDevice.findService(new UDAServiceType("ConnectionManager", 1))) == null) {
            logger.error("Could not find ConnectionManager service. It is unlikely this device is a tuner.");
            throw new InitializationException("The ConnectionManager service does not exist.");
        }
        connectionManagerSubscription = new ConnectionManagerSubscription(upnpService, connectionManagerService);
        connectionManagerAction = new ConnectionManagerAction(upnpService, connectionManagerService);
        logger.debug("Initialized ConnectionManager service.");

        GetProtocolInfo getProtocolInfo = connectionManagerAction.getGetProtocolInfo();

        if (getProtocolInfo != null && getProtocolInfo.getSource().toLowerCase().equals("rtsp-rtp-udp:*:dri-mp2t:*")) {
            connectionManagerSourceProtocol = getProtocolInfo.getSource();
            connectionManagerAVTransportID = "0";
        } else if (getProtocolInfo == null || getProtocolInfo.getSource() == null) {
            logger.warn("The source protocol could not be determined. Using the default 'rtsp-rtp-udp:*:dri-mp2t:*'");
            connectionManagerSourceProtocol = "rtsp-rtp-udp:*:dri-mp2t:*";
            connectionManagerAVTransportID = "0";
        } else {
            logger.warn("The source protocol returned '{}'. Using the default 'rtsp-rtp-udp:*:dri-mp2t:*'", getProtocolInfo.getSource());
            connectionManagerSourceProtocol = "rtsp-rtp-udp:*:dri-mp2t:*";
            connectionManagerAVTransportID = "0";
            //throw new InitializationException("The ConnectionManager service did not return a supported protocol.");
        }

        // Connection to AVTransport service.
        if ((avTransportService = dctDevice.findService(new UDAServiceType("AVTransport", 1))) == null) {
            logger.error("Could not find AVTransport service.");
            throw new InitializationException("The AVTransport service does not exist.");
        }
        avTransportSubscription = new AVTransportSubscription(upnpService, avTransportService);
        avTransportAction = new AVTransportAction(upnpService, avTransportService);
        logger.debug("Initialized AVTransport service.");

        // Connection to CAS service.
        if ((casService = dctDevice.findService(new ServiceType("schemas-opencable-com", "CAS", 1))) == null) {
            logger.error("Could not find CAS service.");
            throw new InitializationException("The CAS service does not exist.");
        }
        casSubscription = new CASSubscription(upnpService, casService);
        casAction = new CASAction(upnpService, casService);
        logger.debug("Initialized CAS service.");

        // Connection to Tuner service.
        if ((tunerService = dctDevice.findService(new ServiceType("schemas-opencable-com", "Tuner", 1))) == null) {
            logger.error("Could not find Tuner service.");
            throw new InitializationException("The Tuner service does not exist.");
        }
        tunerSubscription = new TunerSubscription(upnpService, tunerService);
        tunerAction = new TunerAction(upnpService, tunerService);
        logger.debug("Initialized Tuner service.");


        // Connection to Mux service.
        if ((muxService = dctDevice.findService(new ServiceType("schemas-opencable-com", "Mux", 1))) == null) {
            logger.error("Could not find Mux service.");
            throw new InitializationException("The Mux service does not exist.");
        }
        muxAction = new MuxAction(upnpService, muxService);
        // There is nothing to subscribe to in the Mux service.
        logger.debug("Initialized Mux service.");


        logger.debug("Determining the presence of a CableCARD...");
        GetCardStatus getCardStatus = casAction.getGetCardStatus();
        cableCardPresent = false;
        if (getCardStatus == null) {
            // We only reference the properties if we can't get the status the best way.
            cableCardPresent = Config.getBoolean(propertiesDeviceParent + "cable_card_inserted", false);
            logger.warn("Unable to read the CableCARD status. Using the value '{}' from properties.", cableCardPresent);
        } else {
            cableCardPresent = getCardStatus.getCurrentCardStatus().equals("Inserted");
        }
        // Update properties with the currently known cable card status.
        Config.setBoolean(propertiesDeviceParent + "cable_card_inserted", cableCardPresent);

        logger.debug("Determining the manufacturer...");
        String manufacturer = dctDevice.getDetails().getManufacturerDetails().getManufacturer();
        // In the event that we actually need to change this for testing.
        manufacturer = Config.getString(propertiesDeviceParent + "manufacturer", manufacturer);

        logger.debug("Determining the encoder number...");
        encoderNumber = Integer.parseInt(encoderName.substring(encoderName.length() - 1));

        if (manufacturer.equals("Ceton Corporation")) {
            encoderIPAddress = dctDevice.getParentDevice().getDetails().getBaseURL().getHost();
        } else {
            // Silicondust doesn't provide a URI fast enough and that means I don't have an easy way
            // to get the IP address and without modifying cling again, I can't get the IP address
            // either. So we're going to hack this one.

            try {
                //(RemoteDeviceIdentity) UDN: uuid:24FB1FB3-38BF-39B9-AAC0-F1A177B8E8D5, Descriptor: http://x.x.x.x:80/dri/device.xml
                encoderIPAddress = dctDevice.getParentDevice().getIdentity().toString();
                encoderIPAddress = encoderIPAddress.substring(encoderIPAddress.lastIndexOf("http://"));
                encoderIPAddress = new URI(encoderIPAddress).getHost();
            } catch (Exception e) {
                logger.error("Unable to parse '{}' into encoder IP address.", encoderIPAddress);
                throw new InitializationException("Cannot parse encoder IP address.");
            }
        }

        try {
            rtpStreamRemoteIP = InetAddress.getByName(encoderIPAddress);
        } catch (UnknownHostException e) {
            logger.error("Unable to parse the IP address in '{}' to populate program/frequency channel map.", encoderIPAddress);
        }

        try {
            localIPAddress = Config.getInetAddress( propertiesDeviceParent + "local_ip_override", Util.getLocalIPForRemoteIP(rtpStreamRemoteIP));
        } catch (SocketException e) {
            logger.error("Unable to get the IP address for localhost => ", e);
        }

        if (manufacturer.equals("Ceton Corporation")) {

            if (cableCardPresent) {
                encoderDeviceType = CaptureDeviceType.DCT_INFINITV;
                setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dct"));
            } else {
                encoderDeviceType = CaptureDeviceType.QAM_INFINITV;
                setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
            }

            setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", String.valueOf(encoderDeviceType).toLowerCase()));
            offlineScan = Config.getBoolean(propertiesDeviceParent + "offline_scan", false);

            if (!ChannelManager.hasChannels(encoderLineup) && encoderLineup.equals(String.valueOf(encoderDeviceType).toLowerCase())) {
                ChannelLineup newChannelLineup = new ChannelLineup(encoderLineup, encoderParentName, ChannelSourceType.INFINITV, encoderIPAddress);
                ChannelManager.updateChannelLineup(newChannelLineup);
                ChannelManager.addChannelLineup(newChannelLineup, true);
                ChannelManager.saveChannelLineup(encoderLineup);
            }

            if (offlineScan) {
                ChannelManager.addDeviceToOfflineScan(encoderLineup, encoderName);
            }

            if (isHttpTune()) {
                logger.info("Using HTTP web interface tuning for this device.");
            } else {
                logger.info("Using UPnP tuning for this device.");
            }
        } else if (manufacturer.equals("Silicondust")) {

            if (cableCardPresent) {
                encoderDeviceType = CaptureDeviceType.DCT_PRIME;
                setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "dct"));
            } else {
                encoderDeviceType = CaptureDeviceType.QAM_PRIME;
                setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "qam"));
            }

            setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", String.valueOf(encoderDeviceType).toLowerCase()));
            offlineScan = Config.getBoolean(propertiesDeviceParent + "offline_scan", false);

            if (!ChannelManager.hasChannels(encoderLineup) && encoderLineup.equals(String.valueOf(encoderDeviceType).toLowerCase())) {
                ChannelLineup newChannelLineup = new ChannelLineup(encoderLineup, encoderParentName, ChannelSourceType.PRIME, encoderIPAddress);
                ChannelManager.updateChannelLineup(newChannelLineup);
                ChannelManager.addChannelLineup(newChannelLineup, true);
                ChannelManager.saveChannelLineup(encoderLineup);
            }

            if (offlineScan) {
                ChannelManager.addDeviceToOfflineScan(encoderLineup, encoderName);
            }

            if (isHDHRTune()) {
                logger.info("Using HDHomeRun native protocol for this device.");
                hdhrTuner = new HDHomeRunTuner(new HDHomeRunDevice(rtpStreamRemoteIP), encoderNumber);
            } else {
                logger.info("Using UPnP tuning for this device.");
            }
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
                manufacturer,
                encoderNumber,
                encoderIPAddress,
                localIPAddress,
                cableCardPresent,
                encoderLineup,
                offlineScan,
                rtpLocalPort);

        logger.exit();
    }

    public boolean isLocked() {
        return locked.get();
    }

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

    public boolean isExternalLocked() {
        if (isHDHRTune()) {
            try {
                boolean returnValue = hdhrTuner.isLocked();

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

        // For devices that don't support locking, we need to just assume they must not be
        // locked or we will have issues tuning channels.
        return false;
    }

    public boolean setExternalLock(boolean locked) {
        if (isHDHRTune()) {
            return setHDHRLock(locked);
        }

        // For devices that don't support locking, there isn't anything to change.
        return true;
    }

    private boolean setHDHRLock(boolean locked) {
        if (isHDHRTune()) {
            if (hdhrLock && locked) {
                try {
                    if (forceExternalUnlock) {
                        hdhrTuner.forceClearLockkey();
                    }

                    hdhrTuner.setLockkey(localIPAddress);
                    logger.info("HDHomeRun is now locked.");

                    return true;
                } catch (IOException e) {
                    logger.error("Unable to lock HDHomeRun because it cannot be reached => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to lock HDHomeRun because the command did not work => ", e);
                }
            } else if (hdhrLock) {
                try {
                    if (forceExternalUnlock) {
                        hdhrTuner.forceClearLockkey();
                    } else {
                        hdhrTuner.clearLockkey();
                    }

                    logger.info("HDHomeRun is now unlocked.");

                    return true;
                } catch (IOException e) {
                    logger.error("Unable to unlock HDHomeRun because it cannot be reached => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to unlock HDHomeRun because the command did not work => ", e);
                }
            }
        }

        return false;
    }

    /**
     * Attempt to automatically map a vchannel to it's corresponding program and frequency based on
     * all available sources.
     * <p/>
     * This will return the first result it finds, so it is possible that it can get it wrong which
     * is why this feature can be disabled. It will also update the channel lineup for this encoder
     * with the discovered frequency and program.
     *
     * @param tvChannel The channel to get the frequency and program for  automatically.
     * @return The new channel with the program and frequency already mapped or <i>null</i> if no
     *         mapping was possible.
     */
    public TVChannel autoMap(TVChannel tvChannel) {
        logger.entry(tvChannel);

        // First check if the value is already from an alternative lineup.
        ArrayList<CaptureDevice> devices = SageTVManager.getAllSageTVCaptureDevices(CaptureDeviceType.DCT_INFINITV);
        devices.addAll(SageTVManager.getAllSageTVCaptureDevices(CaptureDeviceType.DCT_PRIME));
        devices.addAll(SageTVManager.getAllSageTVCaptureDevices(CaptureDeviceType.QAM_PRIME));

        if (autoMapReference) {
            for (CaptureDevice device : devices) {
                if (device == this) {
                    continue;
                }

                TVChannel refChannel = ChannelManager.getChannel(device.getChannelLineup(), tvChannel.getChannel());

                if (refChannel != null && !Util.isNullOrEmpty(refChannel.getFrequency()) &&
                        !Util.isNullOrEmpty(refChannel.getProgram())) {

                    tvChannel.setModulation(refChannel.getModulation());
                    tvChannel.setFrequency(refChannel.getFrequency());
                    tvChannel.setProgram(refChannel.getProgram());
                    ChannelManager.updateChannel(encoderLineup, tvChannel);
                    logger.info(
                            "Auto-mapped the channel '{}'" +
                                    " to the frequency '{}'" +
                                    " and program '{}'" +
                                    " from the lineup '{}'.",
                            tvChannel.getChannel(),
                            tvChannel.getFrequency(),
                            tvChannel.getProgram(),
                            device.getChannelLineup());

                    return tvChannel;
                }
            }
        }

        if (autoMapTuning) {
            for (CaptureDevice device : devices) {
                if (device == this) {
                    continue;
                }

                if (!device.isLocked() &&
                        (device.getEncoderDeviceType() == CaptureDeviceType.DCT_PRIME ||
                                device.getEncoderDeviceType() == CaptureDeviceType.DCT_INFINITV)) {

                    boolean result = device.getChannelInfoOffline(tvChannel);

                    if (result) {
                        ChannelManager.updateChannel(encoderLineup, tvChannel);

                        logger.info(
                                "Auto-mapped the channel '{}'" +
                                        " to the frequency '{}'" +
                                        " and program '{}'" +
                                        " by tuning the device '{}'.",
                                tvChannel.getChannel(),
                                tvChannel.getFrequency(),
                                tvChannel.getProgram(),
                                device.getEncoderName());

                        return tvChannel;
                    }
                }
            }
        }

        logger.info("Auto-map failed to get the frequency and program for the channel '{}'.",
                tvChannel.getChannel());

        return null;
    }

    /**
     * Tunes a channel outside of any requests from the SageTV server and updates information about
     * the channel.
     *
     * @param tvChannel A TVChannel object with at the very least a defined channel or frequency and
     *                  program. Otherwise there is nothing to tune.
     * @return <i>true</i> if the test was complete and successful. <i>false</i> if we should try
     *         again on a different capture device since this one is currently locked.
     */
    public boolean getChannelInfoOffline(TVChannel tvChannel) {
        logger.entry();

        if (isLocked() || isExternalLocked()) {
            return logger.exit(false);
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

            int timeout = offlineDetectionWait;
            if (!isHDHRTune()) {
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
            }

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

            if (isHDHRTune()) {
                try {
                    String frequency = hdhrTuner.getChannel();
                    if (frequency != null) {
                        String split[] = frequency.split(":");
                        if (split.length > 1 && split[split.length - 1].length() > 3) {
                            tvChannel.setModulation(split[0].toUpperCase());

                            tvChannel.setFrequency(split[split.length - 1].substring(0, split.length - 3));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Unable to get frequency from capture device => ", e);
                }

                try {
                    tvChannel.setProgram(String.valueOf(hdhrTuner.getProgram()));
                } catch (Exception e) {
                    logger.error("Unable to get program from HDHomeRun => ", e);
                }

                if (encoderDeviceType == CaptureDeviceType.DCT_PRIME) {
                    try {
                        HDHomeRunVStatus status = hdhrTuner.getVirtualChannelStatus();
                        tvChannel.setTunable(!status.NOT_AVAILABLE && !status.COPY_PROTECTED && !status.NOT_SUBSCRIBED);
                    } catch (Exception e) {
                        logger.error("Unable to get status from HDHomeRun => ", e);

                        // Try one more time to see if anything actually recorded.
                        tvChannel.setTunable(getRecordedBytes() > offlineDetectionMinBytes);
                    }
                }
            } else if (encoderDeviceType == CaptureDeviceType.DCT_PRIME) {

                String modulation = tunerAction.SERVICE_ACTIONS.queryActionVariable("Modulation");
                if (modulation != null) {
                    tvChannel.setModulation(modulation);
                }

                String frequency = tunerAction.SERVICE_ACTIONS.queryActionVariable("Frequency");
                if (frequency != null) {
                    tvChannel.setFrequency(frequency);
                }

                String program = muxAction.SERVICE_ACTIONS.queryActionVariable("ProgramNumber");
                if (program != null) {
                    tvChannel.setProgram(program);
                }
            }

            stopEncoding();
        }

        return logger.exit(true);
    }

    public InetAddress getEncoderIpAddress() {
        return rtpStreamRemoteIP;
    }

    private boolean startEncodingHDHR(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        logger.entry(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
        long startTime = System.currentTimeMillis();
        boolean scanOnly = false;

        if (remoteAddress != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}' via the upload id '{}'...", channel, encoderName, filename, uploadID);
        } else if (filename != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}'...", channel, encoderName, filename);
        } else {
            logger.info("Starting a channel scan for the channel '{}' from the device '{}'...", channel, encoderName);
            scanOnly = true;
        }

        setHDHRLock(true);

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
            case DCT_PRIME:
                try {
                    hdhrTuner.setVirtualChannel(channel);
                } catch (IOException e) {
                    logger.error("Unable to tune into channel '{}' => ", channel, e);
                    return logger.exit(false);
                } catch (GetSetException e) {
                    logger.error("Unable to tune into channel '{}' => ", channel, e);
                    return logger.exit(false);
                }
                break;
            case QAM_PRIME:
                TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);
                if (tvChannel == null) {
                    logger.error("The channel '{}' does not exist on the lineup '{}'.", channel, encoderLineup);
                    return logger.exit(false);
                }

                try {
                    String modulation = tvChannel.getModulation();
                    if (modulation == null) {
                        logger.warn("The channel '{}' does not have a modulation on the lineup '{}'. Using QAM256.", channel, encoderLineup);
                        modulation = "qam256";
                    }

                    String frequency = tvChannel.getFrequency();
                    if (frequency == null) {
                        logger.error("The channel '{}' does not have a frequency on the lineup '{}'.", channel, encoderLineup);
                        return logger.exit(false);
                    }

                    String program = tvChannel.getProgram();
                    if (program == null) {
                        logger.error("The channel '{}' does not have a program on the lineup '{}'.", channel, encoderLineup);
                        return logger.exit(false);
                    }

                    hdhrTuner.setChannel(modulation, frequency, false);

                    boolean foundProgram = false;

                    for (int i = 0; i < 20; i++) {
                        try {
                            hdhrTuner.setProgram(program);
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
                    logger.error("Unable to tune into channel => ", e);
                    return logger.exit(false);
                } catch (GetSetException e) {
                    logger.error("Unable to tune into channel => ", e);
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

        long rtspTime = System.currentTimeMillis();

        try {
            hdhrTuner.setTarget("rtp://" + localIPAddress.getHostAddress() + ":" + rtpLocalPort);
        } catch (IOException e) {
            logger.error("Unable to start RTP => ", e);
            return logger.exit(false);
        } catch (GetSetException e) {
            logger.error("Unable to start RTP => ", e);
            return logger.exit(false);
        }

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
            // If we are buffering this can create too much backlog and overruns the file based buffer.
            if (bufferSize == 0) {
                try {
                    newConsumer.setProgram(hdhrTuner.getProgram());

                    int timeout = 50;

                    while (newConsumer.getProgram() == -1) {
                        Thread.sleep(100);
                        newConsumer.setProgram(hdhrTuner.getProgram());

                        if (timeout-- < 0) {
                            logger.error("Unable to get program after 5 seconds.");
                            newConsumer.setProgram(-1);
                            break;
                        }

                        if (tuningThread != Thread.currentThread()) {
                            stopProducing(false);
                            return logger.exit(false);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Unable to get program => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to get program => ", e);
                } catch (InterruptedException e) {
                    logger.debug("Unable to get program => ", e);
                    return logger.exit(false);
                }

                try {
                    newConsumer.setPids(hdhrTuner.getFilter());

                    int timeout = 50;

                    while (newConsumer.getPids().length <= 1) {
                        Thread.sleep(100);
                        newConsumer.setPids(hdhrTuner.getFilter());

                        if (timeout-- < 0) {
                            logger.error("Unable to get PIDs after 5 seconds.");
                            newConsumer.setPids(new int[0]);
                            break;
                        }

                        if (tuningThread != Thread.currentThread()) {
                            stopProducing(false);
                            return logger.exit(false);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Unable to get PIDs => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to get PIDs => ", e);
                } catch (InterruptedException e) {
                    logger.debug("Unable to get PIDs => ", e);
                    return logger.exit(false);
                }
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
            logger.debug("Time to RTSP: {}ms, Total tuning time: {}ms", rtspTime - startTime, endTime - startTime);
        }

        // If we are trying to restart the stream, we only need one monitoring thread.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
            if (!scanOnly) {
                monitorTuning(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
            }
        }

        return logger.exit(true);
    }

    // This only supports InfiniTV devices.
    private boolean startEncodingHttp(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        logger.entry(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);

        long startTime = System.currentTimeMillis();
        boolean scanOnly = false;

        if (remoteAddress != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}' via the upload id '{}'...", channel, encoderName, filename, uploadID);
        } else if (filename != null) {
            logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}'...", channel, encoderName, filename);
        } else {
            logger.info("Starting a channel scan for the channel '{}' from the device '{}'...", channel, encoderName);
            scanOnly = true;
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

        // If we are trying to restart the stream, we don't need to create a new consumer.
        if (monitorThread != null && monitorThread == Thread.currentThread()) {
            newConsumer = sageTVConsumerRunnable;
        } else if (scanOnly) {
            newConsumer = getNewChannelScanSageTVConsumer();
            newConsumer.consumeToNull(true);
        } else {
            newConsumer = getNewSageTVConsumer();
        }

        // Selects what method to use to get the channel tuned in.
        try {
            switch (encoderDeviceType) {
                case DCT_INFINITV:
                case QAM_INFINITV:
                    InfiniTVTuning.tuneChannel(
                            encoderLineup,
                            channel,
                            encoderIPAddress,
                            encoderNumber,
                            cableCardPresent,
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

        if (!startProducing(newRTPProducer, newConsumer, rtpStreamRemoteIP, rtpLocalPort)) {
            logger.error("The producer thread using the implementation '{}' failed to start.",
                    newRTPProducer.getClass().getSimpleName());

            return logger.exit(false);
        }

        rtpLocalPort = newRTPProducer.getLocalPort();

        long rtspTime = System.currentTimeMillis();

        // Even though it takes another 100ms to perform this step, if it is already configured, we
        // are actually already receiving data while this is checking to be sure that we should be
        // receiving data.
        InfiniTVTuning.startRTSP(localIPAddress.getHostAddress(), rtpLocalPort, encoderIPAddress, encoderNumber);

        // If we are buffering this can create too much backlog and overruns the file based buffer.
        if (bufferSize == 0) {
            // If we are trying to restart the stream, we don't need to change anything on the
            // consumer.
            if (monitorThread == null || monitorThread != Thread.currentThread()) {
                try {
                    int getProgram = InfiniTVStatus.GetProgram(encoderIPAddress, encoderNumber, 5);
                    newConsumer.setProgram(getProgram);

                    int timeout = 50;

                    while (newConsumer.getProgram() == -1) {
                        Thread.sleep(100);
                        getProgram = InfiniTVStatus.GetProgram(encoderIPAddress, encoderNumber, 5);
                        newConsumer.setProgram(getProgram);

                        if (timeout-- < 0) {
                            logger.error("Unable to get program after more than 5 seconds.");
                            return logger.exit(false);
                        }

                        if (tuningThread != Thread.currentThread()) {
                            stopProducing(false);
                            return logger.exit(false);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Unable to get program number => ", e);
                } catch (InterruptedException e) {
                    logger.debug("Unable to get program number => ", e);
                    return logger.exit(false);
                }

                try {
                    int pids[] = InfiniTVStatus.GetPids(encoderIPAddress, encoderNumber, 5);
                    newConsumer.setPids(pids);

                    int timeout = 50;

                    while (newConsumer.getPids().length <= 1) {
                        Thread.sleep(100);
                        pids = InfiniTVStatus.GetPids(encoderIPAddress, encoderNumber, 5);
                        newConsumer.setPids(pids);

                        if (timeout-- < 0) {
                            logger.error("Unable to get PIDs after more than 5 seconds.");
                            return logger.exit(false);
                        }

                        if (tuningThread != Thread.currentThread()) {
                            stopProducing(false);
                            return logger.exit(false);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Unable to get PID numbers => ", e);
                } catch (InterruptedException e) {
                    logger.debug("Unable to get PID numbers => ", e);
                    return logger.exit(false);
                }
            }
        }

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
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
            logger.debug("Time to RTSP: {}ms, Total tuning time: {}ms", rtspTime - startTime, endTime - startTime);
        }

        // Make sure only one monitor thread is running per request.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
            if (!scanOnly) {
                monitorTuning(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
            }
        }

        return logger.exit(true);
    }

    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize) {
        return startEncoding(channel, filename, encodingQuality, bufferSize, -1, null);
    }

    public boolean startEncoding(final String channel, final String filename, final String encodingQuality, final long bufferSize, final int uploadID, final InetAddress remoteAddress) {
        logger.entry(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);

        tuningThread = Thread.currentThread();

        if (monitorThread != null && monitorThread != Thread.currentThread()) {
            monitorThread.interrupt();
        }

        if(encoderDeviceType == CaptureDeviceType.QAM_INFINITV || encoderDeviceType == CaptureDeviceType.QAM_PRIME) {
            TVChannel qamChannel = ChannelManager.getChannel(encoderLineup, channel);

            if (qamChannel == null) {
                qamChannel = new TVChannelImpl(channel, "Unknown");
            }

            if (Util.isNullOrEmpty(qamChannel.getFrequency()) || Util.isNullOrEmpty(qamChannel.getProgram())) {
                autoMap(qamChannel);
            }
        }

        synchronized (exclusiveLock) {
            if (isHttpTune()) {
                return startEncodingHttp(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
            }

            if (isHDHRTune()) {
                return startEncodingHDHR(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
            }

            boolean scanOnly = (filename == null);
            long startTime = System.currentTimeMillis();

            if (remoteAddress != null) {
                logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}' via the upload id '{}'...", channel, encoderName, filename, uploadID);
            } else if (!scanOnly) {
                logger.info("Starting the encoding for the channel '{}' from the device '{}' to the file '{}'...", channel, encoderName, filename);
            } else {
                logger.info("Starting a channel scan for the channel '{}' from the device '{}'...", channel, encoderName);
            }

            stopProducing(false);

            // If we are trying to restart the stream, we don't need to stop the consumer.
            if (monitorThread == null || monitorThread != Thread.currentThread()) {
                stopConsuming(false);
            } else {
                logger.info("Consumer is already running; this is a re-tune and it does not need to restart.");
            }

            RTPProducer newRTPProducer = getNewRTPProducer();
            SageTVConsumer newConsumer;

            // If we are trying to restart the stream, we don't need to stop the consumer and producer.
            if (monitorThread != null && monitorThread == Thread.currentThread()) {
                newConsumer = sageTVConsumerRunnable;
            } else if (scanOnly) {
                newConsumer = getNewChannelScanSageTVConsumer();
                newConsumer.consumeToNull(true);
            } else {
                newConsumer = getNewSageTVConsumer();
            }

            // Verify that the connection is actually in an active state if we are using fast tuning.
            boolean reTune = false;
            if (fastTune) {

                String transportState = avTransportAction.SERVICE_ACTIONS.queryActionVariable("TransportState");

                if (transportState == null || transportState.equals("")) {
                    // This is a warning because this should not be happening when everything is working.
                    logger.warn("TransportState did not return a value." +
                            " Fast tuning is not possible this time. Re-tuning...");
                    reTune = true;
                } else if (!transportState.equals("PLAYING")) {
                    logger.info("TransportState is not currently PLAYING." +
                            " Fast tuning is not possible this time. Re-tuning...");
                    reTune = true;
                } else if (rtpStreamRemoteURI == null) {
                    logger.info("rtpStreamRemoteURI is null." +
                            " Fast tuning is not possible this time. Re-tuning...");
                    reTune = true;
                }
            } else {
                reTune = true;
            }

            if (reTune) {
                // Common steps for all DCT devices.
                logger.debug("Starting ConnectManager subscription...");
                connectionManagerSubscription.start();

                logger.debug("Checking for current connections...");
                String connectionID = connectionManagerSubscription.
                        getConnectionManagerCurrentConnectionIDs();

                if (encoderDeviceType == CaptureDeviceType.DCT_PRIME && connectionID.equals("0")) {
                    connectionID = "";
                }

                // Stop anything currently running. DCT tuners don't like doing things out of order and this
                // puts the tuner in a known state. We are not interested in if these return success values.
                if (!connectionID.equals("")) {
                    logger.info("Closing open sessions...");
                    connectionManagerAction.setConnectionComplete(connectionID);
                    try {
                        rtspClient.stopRTPStream(rtpStreamRemoteURI);
                    } catch (Exception e) {
                        logger.error("An unexpected exception was created while stopping the RTP stream => ", e);
                    }
                }

                logger.debug("Running action PrepareForConnection...");
                PrepareForConnection prepareForConnection =
                        connectionManagerAction.
                                setPrepareForConnection(connectionManagerSourceProtocol);

                logger.debug("Setting AVTransportInstanceID for AVTransport subscription...");
                if (prepareForConnection != null && prepareForConnection.getAVTransportID() != null) {
                    connectionManagerAVTransportID = prepareForConnection.getAVTransportID();
                    avTransportSubscription.setAVTransportInstanceID(connectionManagerAVTransportID);
                } else {
                    logger.error("Unable to get AVTransportID. Using the default 0.");

                    if (encoderDeviceType == CaptureDeviceType.DCT_PRIME ||
                            encoderDeviceType == CaptureDeviceType.QAM_PRIME) {
                        avTransportSubscription.setAVTransportInstanceID("0");
                    } else {
                        avTransportSubscription.setAVTransportInstanceID("0");
                    }
                }

                logger.debug("Starting AVTransport subscription...");
                avTransportSubscription.start();

                logger.debug("Running AVTransport Play action...");
                if (!(avTransportAction.setPlay(connectionManagerAVTransportID))) {
                    logger.error("Error running AVTransport Play action.");
                    subscriptionCleanup();
                    return logger.exit(false);
                }
            }

            // Selects what method to use to get the channel tuned in.
            switch (encoderDeviceType) {
                case DCT_INFINITV:
                case DCT_PRIME:
                    logger.debug("Starting CAS subscription...");
                    casSubscription.start();

                    logger.debug("Running action SetChannel...");
                    casAction.setSetChannel(channel);

                    break;
                default:
                    logger.error("This device has been assigned an " +
                            "unsupported capture device type: {}", encoderDeviceType);
                    break;
            }

            long rtspTime = System.currentTimeMillis();

            if (reTune) {
                // From what I have seen this URL doesn't change, but this
                // is probably the best time to double check.
                logger.debug("Getting the value of AVTransport/{}/GetMediaInfo/CurrentURI...",
                        connectionManagerAVTransportID);

                GetMediaInfo getMediaInfo =
                        avTransportAction.getGetMediaInfo(connectionManagerAVTransportID);

                if (getMediaInfo == null || getMediaInfo.getCurrentURI().equals("")) {
                    logger.error("Error getting the value of AVTransport/{}/GetMediaInfo/CurrentURI.",
                            connectionManagerAVTransportID);

                    subscriptionCleanup();
                    return logger.exit(false);
                }
                String uri = getMediaInfo.getCurrentURI();

                logger.debug("Creating URI object from AVTransport/{}/GetMediaInfo/CurrentURI...",
                        connectionManagerAVTransportID);

                try {
                    rtpStreamRemoteURI = URI.create(uri);
                } catch (Exception e) {
                    logger.error("Error parsing the URI '{}'.", uri, e);
                    subscriptionCleanup();
                    return logger.exit(false);
                }
            }

            logger.info("Configuring and starting the new RTP producer...");
            String ipString = null;
            try {
                if (rtpStreamRemoteURI == null) {
                    logger.error("The URI received was null. Will try again in {} seconds.", retuneTimeout /  1000);
                } else {
                    ipString = rtpStreamRemoteURI.getHost();
                    rtpStreamRemoteIP = InetAddress.getByName(ipString);

                    int localRTPPort = 0;
                    if (fastTune && rtpLocalPort != -1) {
                        localRTPPort = rtpLocalPort;
                    }

                    if (!startProducing(newRTPProducer, newConsumer, rtpStreamRemoteIP, localRTPPort)) {
                        logger.error("The producer thread using the implementation '{}' failed to start.",
                                newRTPProducer.getClass().getSimpleName());

                        subscriptionCleanup();
                        return logger.exit(false);
                    }

                    rtpLocalPort = newRTPProducer.getLocalPort();
                }
            } catch (UnknownHostException e) {
                logger.error("Error parsing an IP address from '{}' => {}", ipString, e);
                subscriptionCleanup();
                return logger.exit(false);
            } catch (Exception e) {
                logger.error("An unexpected error occurred while starting the producer thread => {}", e);
                subscriptionCleanup();
                return logger.exit(false);
            }

            logger.info("Configuring the RTP stream via RTSP...");
            try {
                rtspClient.configureRTPStream(rtpStreamRemoteURI, rtpLocalPort);
            } catch (UnknownHostException e) {
                logger.error("Error parsing an IP address from '{}' => {}", rtpStreamRemoteURI.toString(), e);
                subscriptionCleanup();
                return logger.exit(false);
            } catch (Exception e) {
                logger.error("An unexpected error occurred while configuring via RTSP => {}", e);
                subscriptionCleanup();
                return logger.exit(false);
            }

            subscriptionCleanup();

            // If we are trying to restart the stream, we don't need to stop the consumer.
            if (monitorThread == null || monitorThread != Thread.currentThread()) {
                try {
                    String programString = muxAction.SERVICE_ACTIONS.queryActionVariable("ProgramNumber");
                    int program = Integer.valueOf(programString);
                    newConsumer.setProgram(program);
                } catch (Exception e) {
                    logger.warn("Unable to parse program => ", e);
                }

                try {
                    String pidsString = muxAction.SERVICE_ACTIONS.queryActionVariable("PIDList");
                    String split[] = pidsString.split(",");
                    int pids[] = new int[split.length];

                    for (int i = 0; i < pids.length; i++) {
                        pids[i] = Integer.parseInt(split[i].trim(), 16);
                    }

                    newConsumer.setPids(pids);
                } catch (Exception e) {
                    logger.warn("Unable to parse PIDs => ", e);
                }

                logger.info("Configuring and starting the SageTV consumer...");

                if (uploadID > 0 && remoteAddress != null) {
                    newConsumer.consumeToUploadID(filename, uploadID, remoteAddress);
                } else if (!scanOnly) {
                    newConsumer.consumeToFilename(filename);
                } else {
                    newConsumer.consumeToNull(true);
                }

                startConsuming(channel, newConsumer, encodingQuality, bufferSize);
            } else {
                logger.info("Consumer is already running; this is a re-tune and it does not need to restart.");
            }

            if (logger.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                logger.debug("Time to RTSP: {}ms, Total tuning time: {}ms", rtspTime - startTime, endTime - startTime);
            }

            // Don't start more than one monitoring thread.
            if (monitorThread == null || monitorThread != Thread.currentThread()) {
                if (!scanOnly) {
                    monitorTuning(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
                }
            }
        }

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
                int timeout = 0;
                long lastValue = 0;
                long currentValue = 0;
                boolean firstPass = true;

                if (tvChannel != null && tvChannel.getName().startsWith("MC")) {
                    // Music Choice channels take forever to start and with a 4 second timeout,
                    // they might never start.
                    timeout = retuneTimeout * 4;
                } else {
                    timeout = retuneTimeout;
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

                        logger.error("No data was streamed after {} milliseconds. Re-tuning channel...", timeout);

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

                        logger.info("Copy protection status is '{}' and signal strength is {}.", getCopyProtection(), getSignalStrength());
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

    @Override
    public void stopEncoding() {
        logger.entry();

        logger.debug("Stopping encoding...");

        synchronized (exclusiveLock) {
            if (monitorThread != null && monitorThread != Thread.currentThread()) {
                monitorThread.interrupt();
            }

            super.stopEncoding();

            if (isHttpTune()) {
                // This will silence the RTP stream, but keep it turned on.
                if (cableCardPresent) {
                    try {
                        InfiniTVTuning.tuneVChannel("0", encoderIPAddress, encoderNumber, 5);
                    } catch (InterruptedException e) {
                        logger.warn("Stopping InfiniTV device was interrupted => ", e);
                        return;
                    } catch (Exception e) {
                        logger.error("An unexpected exception was created while stopping the InfiniTV device => ", e);
                        return;
                    }
                } else {
                    try {
                        InfiniTVTuning.tuneFrequency(
                                new TVChannelImpl("0", "0", "QAM256", "0", "0", "0", false),
                                encoderIPAddress,
                                encoderNumber,
                                25);
                    } catch (InterruptedException e) {
                        logger.warn("Stopping InfiniTV device was interrupted => ", e);
                        return;
                    }
                }
            } else if (isHDHRTune()) {
                try {
                    if (encoderDeviceType == CaptureDeviceType.DCT_PRIME) {
                        hdhrTuner.clearVirtualChannel();
                        hdhrTuner.clearTarget();
                    } else if (encoderDeviceType == CaptureDeviceType.QAM_PRIME) {
                        hdhrTuner.clearChannel();
                        hdhrTuner.clearTarget();
                    }
                } catch (IOException e) {
                    logger.error("Unable to stop HDHomeRun Prime capture device => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to stop HDHomeRun Prime capture device => ", e);
                }

                setHDHRLock(false);
            } else {
                //UPnP DCT (not ClearQAM)

                if (fastTune) {
                    casAction.setSetChannel("0");
                } else {
                    if (connectionManagerAVTransportID != null && !connectionManagerAVTransportID.equals("")) {
                        avTransportAction.setStop(connectionManagerAVTransportID);
                        connectionManagerAction.setConnectionComplete(connectionManagerAVTransportID);
                    }

                    if (rtspClient != null && rtpStreamRemoteURI != null) {
                        try {
                            rtspClient.stopRTPStream(rtpStreamRemoteURI);
                        } catch (Exception e) {
                            logger.error("An unexpected exception was created while stopping the RTP stream => ", e);
                        }
                    }
                }
            }
        }

        logger.info("Stopped encoding.");

        logger.exit();
    }

    @Override
    public void stopDevice() {
        logger.entry();

        if (monitorThread != null && monitorThread != Thread.currentThread()) {
            monitorThread.interrupt();
        }

        // Make sure we unlock the device when the capture device is no longer in use.
        if (isHDHRTune()) {
            if (hdhrLock) {
                try {
                    hdhrTuner.clearLockkey();
                } catch (IOException e) {
                    logger.error("Unable to unlock HDHomeRun because it cannot be reached => ", e);
                } catch (GetSetException e) {
                    logger.error("Unable to unlock HDHomeRun because the command did not work => ", e);
                }
            }

            try {
                hdhrTuner.clearTarget();
            } catch (IOException e) {
                logger.error("Unable to clear the HDHomeRun target because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to clear the HDHomeRun target because the command did not work => ", e);
            }
        } else if (isHttpTune()) {
            InfiniTVTuning.stopRTSP(encoderIPAddress, encoderNumber);
        } else if (!isHDHRTune() && !isHttpTune()) {
            //UPnP DCT (not ClearQAM)

            logger.debug("Checking for current connections...");
            connectionManagerAVTransportID = connectionManagerSubscription.
                    getConnectionManagerCurrentConnectionIDs();

            if (encoderDeviceType == CaptureDeviceType.DCT_PRIME && connectionManagerAVTransportID.equals("0")) {
                connectionManagerAVTransportID = null;
            } else if (connectionManagerAVTransportID.equals("")) {
                connectionManagerAVTransportID = null;
            }

            String transportState = avTransportAction.SERVICE_ACTIONS.queryActionVariable("TransportState");

            if (transportState == null || !transportState.equals("PLAYING")) {
                connectionManagerAVTransportID = null;
            }

            if (connectionManagerAVTransportID != null) {
                avTransportAction.setStop(connectionManagerAVTransportID);
                connectionManagerAction.setConnectionComplete(connectionManagerAVTransportID);
                try {
                    if (rtpStreamRemoteURI != null) {
                        rtspClient.stopRTPStream(rtpStreamRemoteURI);
                    }
                } catch (UnknownHostException e) {
                    logger.error("Unable to stop RTSP stream => ", e);
                }
            }
        }

        // If we are trying to restart the stream, we don't need to stop the consumer and producer.
        if (monitorThread == null || monitorThread != Thread.currentThread()) {
            super.stopDevice();
        }

        logger.exit();
    }

    /**
     * Is the HTTP tuning method for InfiniTV devices in use?
     *
     * @return <i>true</i> if HTTP tuning method should be used.
     */
    public boolean isHttpTune() {
        return httpTune && encoderDeviceType == CaptureDeviceType.DCT_INFINITV || encoderDeviceType == CaptureDeviceType.QAM_INFINITV;
    }

    /**
     * Is the native protocol for HDHomeRun devices in use?
     *
     * @return <i>true</i> if the HDHomeRun native protocol is to be used.
     */
    public boolean isHDHRTune() {
        return hdhrTune && encoderDeviceType == CaptureDeviceType.DCT_PRIME || encoderDeviceType == CaptureDeviceType.QAM_PRIME;
    }

    public void tuneToChannel(String channel) {

    }

    public boolean autoTuneChannel(String channel) {
        return false;
    }

    /**
     * If this returns <i>false</i>, the tuning process stops and returns ERROR to SageTV. It
     * doesn't look like SageTV acknowledges this even though the SageTV network encoder code would
     * suggest that it should understand that there is a problem. The end result is a screen that
     * says No Signal.
     *
     * @return <i>true</i> if everything is ready for tuning.
     */
    public boolean isReady() {
        return true;
    }

    public BroadcastStandard getBroadcastStandard() {
        return BroadcastStandard.QAM256;
    }

    public int getSignalStrength() {
        logger.entry();

        int signal = 0;
        // -10 dBmV = 100%
        if (encoderDeviceType == CaptureDeviceType.DCT_INFINITV ||
                encoderDeviceType == CaptureDeviceType.QAM_INFINITV) {

            // http://x.x.x.x/get_var?i=0&s=diag&v=Signal_Level
            // -8.0 dBmV
            try {
                signal = InfiniTVStatus.GetSignalStrength(encoderIPAddress, encoderNumber);
            } catch (Exception e) {
                logger.debug("Unable to get signal strength from capture device.");
            }

        } else if (encoderDeviceType == CaptureDeviceType.DCT_PRIME ||
                encoderDeviceType == CaptureDeviceType.QAM_PRIME) {

            try {
                HDHomeRunStatus status = hdhrTuner.getStatus();
                signal = status.SIGNAL_STRENGTH;
            } catch (Exception e) {
                logger.debug("Unable to get CCI status from HDHomeRun.");
            }
        }

        return logger.exit(signal);
    }

    public CopyProtection getCopyProtection() {
        logger.entry();

        CopyProtection returnValue = CopyProtection.UNKNOWN;

        if (encoderDeviceType == CaptureDeviceType.DCT_INFINITV ||
                encoderDeviceType == CaptureDeviceType.QAM_INFINITV) {

            // http://x.x.x.x//get_var?i=0&s=diag&v=CopyProtectionStatus
            // Copy Control Information: "Copy Free" (00)
            try {
                returnValue = InfiniTVStatus.GetCCIStatus(encoderIPAddress, encoderNumber);
            } catch (Exception e) {
                logger.debug("Unable to get CCI status from capture device.");
            }

        } else if (encoderDeviceType == CaptureDeviceType.DCT_PRIME ||
                encoderDeviceType == CaptureDeviceType.QAM_PRIME) {

            try {
                HDHomeRunVStatus vstatus = hdhrTuner.getVirtualChannelStatus();
                returnValue = vstatus.COPY_PROTECTION;
            } catch (Exception e) {
                logger.debug("Unable to get CCI status from HDHomeRun.");
            }

        }

        return logger.exit(returnValue);
    }


    private void subscriptionCleanup() {
        logger.entry();

        logger.debug("Cleaning up all subscriptions we might have started...");

        if (tunerSubscription != null) {
            tunerSubscription.stop();
        }
        if (connectionManagerSubscription != null) {
            connectionManagerSubscription.stop();
        }
        if (avTransportSubscription != null) {
            avTransportSubscription.stop();
        }
        if (casSubscription != null) {
            casSubscription.stop();
        }

        logger.exit();
    }
}
