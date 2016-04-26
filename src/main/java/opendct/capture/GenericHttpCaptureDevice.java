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

import opendct.channel.*;
import opendct.config.Config;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.SageTVConsumer;
import opendct.producer.HTTPProducer;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.GenericHttpDiscoverer;
import opendct.tuning.http.GenericHttpDiscoveredDevice;
import opendct.tuning.http.GenericHttpDiscoveredDeviceParent;
import opendct.util.StreamLogger;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenericHttpCaptureDevice extends BasicCaptureDevice {
    private final static Logger logger = LogManager.getLogger(GenericHttpCaptureDevice.class);

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();
    private final GenericHttpDiscoveredDeviceParent parent;
    private final GenericHttpDiscoveredDevice device;

    private final static Runtime runtime = Runtime.getRuntime();
    private final HTTPCaptureDeviceServices httpServices = new HTTPCaptureDeviceServices();
    private URL sourceUrl;
    private Thread tuningThread;

    private HTTPProducer httpProducer;

    public GenericHttpCaptureDevice(GenericHttpDiscoveredDeviceParent loadParent, GenericHttpDiscoveredDevice loadDevice) throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        super(loadParent.getFriendlyName(), loadDevice.getFriendlyName(), loadParent.getParentId(), loadDevice.getId());

        parent = loadParent;
        device = loadDevice;

        try {
            getURL();
        } catch (MalformedURLException e) {
            throw new CaptureDeviceLoadException(e);
        }

        encoderDeviceType = CaptureDeviceType.LIVE_STREAM;

        super.setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", "generic_http"));

        if (ChannelManager.getChannelLineup(encoderLineup) == null) {
            ChannelManager.addChannelLineup(new ChannelLineup(encoderLineup, encoderName, ChannelSourceType.STATIC, ""), false);
        }

        super.setEncoderPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "generic_http"));
    }

    private URL getURL() throws MalformedURLException {
        String loadUrl = device.getStreamingUrl();
        URL newUrl;

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
    private boolean executeCommand(String execute) throws InterruptedException {
        if (Util.isNullOrEmpty(execute)) {
            return true;
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
        } catch (IOException e) {
            logger.error("Unable to run tuning executable '{}' => ", execute, e);
            return false;
        }

        return true;
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

            if (!startEncoding(tvChannel.getChannel(), null, "", 0)) {
                return logger.exit(false);
            }

            // There isn't any CCI to check since this is always an analog source.

            //TODO: Verify that the video is actually doing something other than showing a blank screen.
            //tvChannel.setTunable(true);
        }

        return logger.exit(true);
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize) {
        return startEncoding(channel, filename, encodingQuality, bufferSize, -1, null);
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        TVChannel tvChannel = ChannelManager.getChannel(encoderLineup, channel);

        tuningThread = Thread.currentThread();

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
        boolean scanOnly = false;

        if (recordLastFilename != null && recordLastFilename.equals(filename)) {
            retune = true;
            //logger.info("Re-tune: {}", getTunerStatusString());
        } else {
            recordLastFilename = filename;
        }

        if (remoteAddress != null) {
            logger.info("{} the encoding for the channel '{}' from the device '{}' to the file '{}' via the upload id '{}'...", retune ? "Retuning" : "Starting", channel, encoderName, filename, uploadID);
        } else if (filename != null) {
            logger.info("{} the encoding for the channel '{}' from the device '{}' to the file '{}'...", retune ? "Retuning" : "Starting", channel, encoderName, filename);
        } else {
            logger.info("Starting a channel scan for the channel '{}' from the device '{}'...", channel, encoderName);
            scanOnly = true;
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

        if (tvChannel == null) {
            tvChannel = new TVChannelImpl(channel, channel);
            ChannelManager.addChannel(encoderLineup, tvChannel);
        }

        String execute = getExecutionString(device.getPretuneExecutable(), channel, false);
        try {
            if (!executeCommand(execute)) {
                logger.error("Failed to run pre-tune executable.");
                return false;
            }
        } catch (InterruptedException e) {
            logger.debug("Pre-tune was interrupted => {}", e.getMessage());
            return false;
        }

        if (tuningThread != Thread.currentThread()) {
            return false;
        }

        execute = getExecutionString(device.getTuningExecutable(), channel, true);
        try {
            if (!executeCommand(execute)) {
                logger.error("Failed to run tuning executable.");
                return false;
            }
        } catch (InterruptedException e) {
            logger.debug("Tuning was interrupted => {}", e.getMessage());
            return false;
        }

        if (tuningThread != Thread.currentThread()) {
            return false;
        }

        try {
            Thread.sleep(device.getTuningDelay());
        } catch (InterruptedException e) {
            logger.warn("Tuning delay was interrupted => ", e.getMessage());
            return false;
        }

        if (tuningThread != Thread.currentThread()) {
            return false;
        }

        logger.info("Configuring and starting the new SageTV producer...");

        httpProducer = httpServices.getNewHTTPProducer(propertiesDeviceParent);

        try {
            if (!httpServices.startProducing(encoderName, httpProducer, newConsumer, getURL())) {
                return false;
            }
        } catch (MalformedURLException e) {
            logger.error("Unable to start streaming because the URL is invalid.");
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
    }

    @Override
    public void stopEncoding() {

        logger.debug("Stopping encoding...");

        synchronized (exclusiveLock) {
            httpServices.stopProducing(false);
            httpProducer = null;

            super.stopEncoding();

            String execute = getExecutionString(device.getPretuneExecutable(), lastChannel, false);
            try {
                if (!executeCommand(execute)) {
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
