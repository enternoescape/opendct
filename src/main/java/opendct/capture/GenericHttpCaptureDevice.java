/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
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
import opendct.channel.*;
import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.SageTVConsumer;
import opendct.producer.HTTPProducer;
import opendct.sagetv.SageTVDeviceType;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.GenericHttpDiscoverer;
import opendct.tuning.http.GenericHttpDiscoveredDevice;
import opendct.tuning.http.GenericHttpDiscoveredDeviceParent;
import opendct.util.StreamLogger;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenericHttpCaptureDevice extends BasicCaptureDevice {
    private final static Logger logger = LogManager.getLogger(GenericHttpCaptureDevice.class);

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();
    private final GenericHttpDiscoveredDeviceParent parent;
    private final GenericHttpDiscoveredDevice device;

    //private final Map<String, String> resolutionMap = new ConcurrentHashMap<>();
    private final Map<String, URL> channelMap = new ConcurrentHashMap<>();
    private final static Runtime runtime = Runtime.getRuntime();
    private final HTTPCaptureDeviceServices httpServices = new HTTPCaptureDeviceServices();
    private URL sourceUrl;
    long lastTuneTime = System.currentTimeMillis();

    private HTTPProducer httpProducer;

    public GenericHttpCaptureDevice(GenericHttpDiscoveredDeviceParent loadParent, GenericHttpDiscoveredDevice loadDevice) throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        super(loadParent.getFriendlyName(), loadDevice.getFriendlyName(), loadParent.getParentId(), loadDevice.getId());

        parent = loadParent;
        device = loadDevice;

        try {
            getURL(null);
        } catch (MalformedURLException e) {
            throw new CaptureDeviceLoadException(e);
        }

        encoderDeviceType = CaptureDeviceType.LIVE_STREAM;

        super.setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", "generic_http"));

        if (ChannelManager.getChannelLineup(encoderLineup) == null) {
            ChannelManager.addChannelLineup(new ChannelLineup(encoderLineup, encoderName, ChannelSourceType.STATIC, ""), false);
        }

        super.setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "generic_http"));

        String tuningUrl = device.getAltStreamingUrl();

        if (!Util.isNullOrEmpty(tuningUrl)) {
            try {
                URL altStreamingUrl = new URL(tuningUrl);
                String channels[] = device.getAltStreamingChannels();

                for (String channel : channels) {
                    channelMap.put(channel, altStreamingUrl);
                }

                logger.info("The secondary URL '{}'" +
                        " will be used for the channels: {}", tuningUrl, channels);
            } catch (MalformedURLException e) {
                logger.warn("The secondary URL '{}' is not a valid URL." +
                                " Defaulting to primary URL.",
                        tuningUrl);
            }
        }
    }

    @Override
    public SageTVDeviceType[] getSageTVDeviceTypes() {
        return new SageTVDeviceType[] { SageTVDeviceType.DIGITAL_TV_TUNER, SageTVDeviceType.HDMI };
    }

    private URL getURL(String channel) throws MalformedURLException {
        URL newUrl;

        if (channel != null) {
            newUrl = channelMap.get(channel);

            if (newUrl != null) {
                return newUrl;
            }
        }

        String loadUrl = device.getStreamingUrl();

        try {
            newUrl = new URL(loadUrl);
            sourceUrl = newUrl;
        } catch (MalformedURLException e) {
            if (sourceUrl == null) {
                throw new MalformedURLException(
                        "Unable to start capture device because '" +
                                loadUrl + "' is not a valid URL.");
            }
        }

        return sourceUrl;
    }

    private String getExecutionString(String execute, String channel, boolean appendChannel) {
        if (Util.isNullOrEmpty(execute)) {
            return "";
        }

        int minLength = device.getPadChannel();

        if (channel.length() < minLength) {
            minLength = minLength - channel.length();

            // With all of the appending, I'm not sure if this is any better than a for loop on
            // small numbers. Also we can't assume the channel is a number, so we loose a little
            // efficiency there too.
            channel = String.format(Locale.ENGLISH, "%0" + minLength + "d", 0) + channel;
        }

        if (!execute.contains("%c%") && appendChannel) {
            return execute + " " + channel;
        } else {
            return execute.replace("%c%", channel);
        }
    }

    StringBuilder stdOutBuilder = new StringBuilder();
    StringBuilder errOutBuilder = new StringBuilder();
    private int executeCommand(String execute) throws InterruptedException {
        if (Util.isNullOrEmpty(execute)) {
            return 0;
        }

        try {
            logger.debug("Executing: '{}'", execute);
            Process tunerProcess = runtime.exec(execute);

            Runnable stdRunnable =
                    new StreamLogger("std", tunerProcess.getInputStream(), logger, stdOutBuilder);
            Thread errThread = new Thread(
                    new StreamLogger("err", tunerProcess.getErrorStream(), logger, errOutBuilder));

            errThread.setName("StreamLogger-" + errThread.getId());
            errThread.start();
            stdRunnable.run();

            tunerProcess.waitFor();

            int returnValue = tunerProcess.exitValue();
            logger.debug("Exit code: {}", returnValue);
            return returnValue;
        } catch (IOException e) {
            logger.error("Unable to run tuning executable '{}' => ", execute, e);
        }

        return -1;
    }

    private static final String hdmiRoot[] = { "status", "hdmi" };
    private static final String hdmiVideoSize[] = { "video", "size" };
    private static final String chnVideoSizeWidth[] = { "chn_stat", "width" };
    private static final String chnVideoSizeHeight[] = { "chn_stat", "height" };

    /**
     * Check if the currently detected resolution matches the encoded resolution.
     *
     * @param getStatus The URL to check.
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @param match If this is not <i>null</i>, it will be checked against the detected resolution
     *              instead of using the encoded resolution.
     * @return The matching resolution if they match or the device cannot be reached. Empty string
     *         if URL is unreachable/parsable. <i>null</i> if they do not match.
     */
    private static String getVideoMatches(URL getStatus, String username, String password, String match) {
        Document document = null;
        try {
            document = Util.getUrlXml(getStatus, username, password, 5000);
        } catch (IOException e) {
            logger.error("Unable to download/parse the XML from the URL '{}' => ", getStatus, e);

            // If the URL can't be opened/parsed, return that it's a match since it is likely that
            // the device doesn't support the way we are trying to access it.
            return "";
        }

        if (document != null) {
            Node hdmiRootNode = Util.getDeepNode(hdmiRoot, document.getChildNodes());

            if (hdmiRootNode != null) {
                Node videoSizeNode = Util.getDeepNode(hdmiVideoSize, hdmiRootNode.getChildNodes());

                if (match == null) {
                    Node chnWidthNode = Util.getDeepNode(chnVideoSizeWidth, hdmiRootNode.getChildNodes());
                    Node chnHeightNode = Util.getDeepNode(chnVideoSizeHeight, hdmiRootNode.getChildNodes());

                    if (chnWidthNode != null && chnHeightNode != null) {
                        String chnWidth = chnWidthNode.getTextContent();
                        String chnHeight = chnHeightNode.getTextContent();
                        match = chnWidth + "*" + chnHeight;
                    }
                }

                if (videoSizeNode != null && match != null) {
                    String videoSize = videoSizeNode.getTextContent();

                    if (videoSize.startsWith(match)) {
                        return videoSize;
                    }
                }
            }
        }

        return null;
    }

    /*private void waitForResolutionChange(String channel) {
        try {
            String username = device.getResolutionChangeUsername();
            String password = device.getResolutionChangePassword();

            URL getStatus = new URL("http", getURL(channel).getHost(), 80, "get_status");
            String expectedResolution = resolutionMap.get(channel);

            // If we have a known value for this channel, this can speed things up.
            if (expectedResolution != null) {
                if (getVideoMatches(getStatus, username, password, expectedResolution) != null) {
                    return;
                }
            }

            Thread.sleep(5000);

            int retry = 20;
            while (retry-- > 0) {
                String returnedValue = getVideoMatches(getStatus, username, password, expectedResolution);
                if (returnedValue != null) {
                    if (returnedValue.length() > 0) {
                        resolutionMap.put(channel, returnedValue);
                        return;
                    }
                }

                Thread.sleep(1000);
            }


        } catch (InterruptedException e) {
            logger.debug("Interrupted while waiting for resolution to change.");
        } catch (MalformedURLException e) {
            logger.error("Unable to create a valid URL to get the current status of the device.");
        }
    }*/

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
        // There isn't a way to lock this device from an external perspective.
        return true;
    }

    @Override
    public boolean setExternalLock(boolean locked) {
        // There isn't a way to lock this device from an external perspective.
        return true;
    }

    @Override
    public boolean getChannelInfoOffline(TVChannel tvChannel, boolean skipCCI) {
        logger.entry(tvChannel, skipCCI);

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

            // There isn't any CCI to check since this is always an analog source.

            //TODO: Verify that the video is actually doing something other than showing a blank screen.
            //tvChannel.setTunable(true);
        }

        return logger.exit(true);
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, SageTVDeviceType deviceType, int uploadID, InetAddress remoteAddress) {
        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);

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

    private boolean startEncodingSync(String channel, String filename, String encodingQuality,
                                      long bufferSize, int uploadID, InetAddress remoteAddress,
                                      TVChannel tvChannel) {

        boolean retune = false;
        boolean scanOnly = (filename == null);

        if (recordLastFilename != null && recordLastFilename.equals(filename)) {
            retune = true;
            //logger.info("Re-tune: {}", getTunerStatusString());
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

        httpServices.stopProducing(false);

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (!retune) {
            stopConsuming(false);
        }

        // Get a new producer and consumer.
        HTTPProducer newHTTPProducer = httpServices.getNewHTTPProducer(propertiesDeviceParent);
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

        if (tvChannel == null) {
            tvChannel = new TVChannelImpl(channel, channel);
            ChannelManager.addChannel(encoderLineup, tvChannel);
        }

        String execute = getExecutionString(device.getPretuneExecutable(), channel, false);
        try {
            if (executeCommand(execute) == -1) {
                logger.error("Failed to run pre-tune executable.");
                return false;
            }
        } catch (InterruptedException e) {
            logger.debug("Pre-tune was interrupted => {}", e.getMessage());
            return false;
        }

        logger.info("Configuring and starting the new SageTV producer...");

        try {
            if (!httpServices.startProducing(encoderName, newHTTPProducer, newConsumer, getURL(channel))) {
                return false;
            }

            httpProducer = newHTTPProducer;
        } catch (MalformedURLException e) {
            logger.error("Unable to start streaming because the URL is invalid.");
            return false;
        }

        int returnCode;
        execute = getExecutionString(device.getTuningExecutable(), channel, true);
        try {
            if ((returnCode = executeCommand(execute)) == -1) {
                logger.error("Failed to run tuning executable.");
                return false;
            }
        } catch (InterruptedException e) {
            logger.debug("Tuning was interrupted => {}", e.getMessage());
            return false;
        }

        /*if (device.getResolutionChangeDelay()) {
            waitForResolutionChange(channel);
        }*/

        if (returnCode == 12000) {
            logger.debug("Clearing buffer.");
            newConsumer.clearBuffer();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                logger.debug("Interrupted while emptying buffer.");
            }
        }

        try {
            Thread.sleep(device.getTuningDelay());
        } catch (InterruptedException e) {
            logger.warn("Tuning delay was interrupted => ", e.getMessage());
            return false;
        }

        if (!retune) {
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

        sageTVConsumerRunnable.isStreaming(GenericHttpDiscoverer.getStreamingWait());

        lastTuneTime = System.currentTimeMillis();

        return true;
    }

    private boolean firstPass = true;
    private boolean customChannels = false;
    private String returnChannels = null;

    @Override
    public String scanChannelInfo(String channel) {
        String scanInfo = scanChannelInfo(channel, true);

        if (firstPass) {
            returnChannels = device.getCustomChannels();

            if (!Util.isNullOrEmpty(returnChannels)) {
                customChannels = true;
            }

            firstPass = false;
            return "OK";
        }

        if (channel.equals("-1")) {
            firstPass = true;
            return "OK";
        }

        if (customChannels && returnChannels != null) {
            String returnValue = returnChannels;
            returnChannels = null;
            return returnValue;
        } else if (!customChannels) {
            // TODO: Implement analog channel scanning.
            return scanInfo;
        }

        return "ERROR";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public long getProducedPackets() {
        synchronized (exclusiveLock) {
            if (httpProducer == null) {
                httpProducer = httpServices.getHttpProducerRunnable();

                if (httpProducer == null) {
                    return 0;
                }
            }

            return httpProducer.getPackets();
        }
    }

    @Override
    public BroadcastStandard getBroadcastStandard() {
        return BroadcastStandard.H264;
    }

    @Override
    public int getSignalStrength() {
        return 100;
    }

    @Override
    public CopyProtection getCopyProtection() {
        return CopyProtection.NONE;
    }

    @Override
    public DeviceOption[] getOptions() {
        return device.getOptions();
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        device.setOptions(deviceOptions);

        // Update channel map.
        String tuningUrl = device.getAltStreamingUrl();
        channelMap.clear();

        if (!Util.isNullOrEmpty(tuningUrl)) {
            try {
                URL altStreamingUrl = new URL(tuningUrl);
                String channels[] = device.getAltStreamingChannels();

                for (String channel : channels) {
                    channelMap.put(channel, altStreamingUrl);
                }

                logger.info("The secondary URL '{}'" +
                        " will be used for the channels: {}", tuningUrl, channels);
            } catch (MalformedURLException e) {
                logger.warn("The secondary URL '{}' is not a valid URL." +
                                " Defaulting to primary URL.",
                        tuningUrl);
            }
        }
    }

    @Override
    public void stopEncoding() {

        logger.debug("Stopping encoding...");

        synchronized (exclusiveLock) {
            httpServices.stopProducing(false);
            httpProducer = null;

            super.stopEncoding();

            String execute = getExecutionString(device.getStoppingExecutable(), lastChannel, false);
            try {
                if (executeCommand(execute) == -1) {
                    logger.error("Failed to run stop executable.");
                    return;
                }
            } catch (InterruptedException e) {
                logger.debug("Stop was interrupted => {}", e.getMessage());
                return;
            }
        }
    }

    @Override
    public void stopDevice() {
        logger.debug("Stopping device...");

        stopEncoding();
    }
}