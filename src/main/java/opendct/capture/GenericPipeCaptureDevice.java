/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.capture;

import opendct.capture.services.InputStreamCaptureDeviceServices;
import opendct.channel.*;
import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.producer.InputStreamProducer;
import opendct.sagetv.SageTVDeviceCrossbar;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.GenericPipeDiscoverer;
import opendct.tuning.pipe.GenericPipeDiscoveredDevice;
import opendct.tuning.pipe.GenericPipeDiscoveredDeviceParent;
import opendct.util.StreamLogger;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenericPipeCaptureDevice extends BasicCaptureDevice {
    private final static Logger logger = LogManager.getLogger(GenericPipeCaptureDevice.class);

    private final int STOP_LIMIT = 15000;

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();
    private final GenericPipeDiscoveredDeviceParent parent;
    private final GenericPipeDiscoveredDevice device;

    private final static Runtime runtime = Runtime.getRuntime();
    private final InputStreamCaptureDeviceServices inputStreamServices =
            new InputStreamCaptureDeviceServices();
    long lastTuneTime = System.currentTimeMillis();

    private InputStreamProducer inputStreamProducer;
    private Process currentProcess;

    public GenericPipeCaptureDevice(GenericPipeDiscoveredDeviceParent loadParent, GenericPipeDiscoveredDevice loadDevice) throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        super(loadParent.getFriendlyName(), loadDevice.getFriendlyName(), loadParent.getParentId(), loadDevice.getId());

        parent = loadParent;
        device = loadDevice;

        encoderDeviceType = CaptureDeviceType.INPUT_STREAM;

        super.setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", "generic_pipe"));

        if (ChannelManager.getChannelLineup(encoderLineup) == null) {
            ChannelManager.addChannelLineup(new ChannelLineup(encoderLineup, encoderName, ChannelSourceType.STATIC, ""), false);
        }

        super.setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "generic_pipe"));
    }

    @Override
    public SageTVDeviceCrossbar[] getSageTVDeviceCrossbars() {
        return new SageTVDeviceCrossbar[] { SageTVDeviceCrossbar.HDMI };
    }

    private StringBuilder stdOutBuilder = new StringBuilder();
    private StringBuilder errOutBuilder = new StringBuilder();
    private int executeStopCommand(String execute) throws InterruptedException {
        if (Util.isNullOrEmpty(execute)) {
            return 0;
        }

        try {
            logger.debug("Executing: '{}'", execute);
            Process tunerProcess = runtime.exec(execute);

            Thread stdThread = new Thread(
                    new StreamLogger("std", tunerProcess.getInputStream(), logger, stdOutBuilder,
                            tunerProcess, System.currentTimeMillis() + STOP_LIMIT));
            Thread errThread = new Thread(
                    new StreamLogger("err", tunerProcess.getErrorStream(), logger, errOutBuilder));

            errThread.setName("StreamLogger-" + errThread.getId());
            errThread.start();
            stdThread.setName("StreamLogger-" + errThread.getId());
            stdThread.start();

            tunerProcess.waitFor();
            int returnValue = tunerProcess.exitValue();
            logger.debug("Exit code: {}", returnValue);
            return returnValue;
        } catch (IOException e) {
            logger.error("Unable to run stop executable '{}' => ", execute, e);
        }

        return -1;
    }

    private StringBuilder errStreamOutBuilder = new StringBuilder();
    private InputStream executeStreamCommand(String execute) throws InterruptedException {
        if (Util.isNullOrEmpty(execute)) {
            return null;
        }

        try {
            logger.debug("Executing: '{}'", execute);
            Process tunerProcess = runtime.exec(execute);

            Thread errThread = new Thread(
                    new StreamLogger("err", tunerProcess.getErrorStream(), logger, errStreamOutBuilder));

            errThread.setName("StreamLogger-" + errThread.getId());
            errThread.start();

            currentProcess = tunerProcess;
            return tunerProcess.getInputStream();
        } catch (IOException e) {
            logger.error("Unable to run streaming executable '{}' => ", execute, e);
        }

        return null;
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
        // There isn't a way to lock this device from an external perspective.
        return false;
    }

    @Override
    public boolean setExternalLock(boolean locked) {
        // There isn't a way to lock this device from an external perspective.
        return true;
    }

    @Override
    public boolean getChannelInfoOffline(TVChannel tvChannel, boolean skipCCI) {
        logger.entry(tvChannel, skipCCI);

        if (isInternalLocked() || isExternalLocked()) {
            return logger.exit(false);
        }

        synchronized (exclusiveLock) {
            // Return immediately if an exclusive lock was set between here and the first check if
            // there is an exclusive lock set.
            if (isInternalLocked()) {
                return logger.exit(false);
            }

            if (!startEncoding(tvChannel.getChannel(), null, "", 0, SageTVDeviceCrossbar.HDMI, 0, 0, null)) {
                return logger.exit(false);
            }

            // There isn't any CCI to check since this is always an analog source.

            //TODO: Verify that the video is actually doing something other than showing a blank screen.
            //tvChannel.setTunable(true);
        }

        return logger.exit(true);
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, SageTVDeviceCrossbar deviceType, int crossbarIndex, int uploadID, InetAddress remoteAddress) {
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

        if (currentProcess != null) {
            try {
                String stopCommand = device.getStoppingExecutable();
                if (!Util.isNullOrEmpty(stopCommand)) {
                    executeStopCommand(stopCommand);
                }
                currentProcess.destroy();
                currentProcess = null;
            } catch (Exception e) {
                logger.warn("There was an exception stopping the last execution => ", e);
            }
        }

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

        inputStreamServices.stopProducing(false);

        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (!retune) {
            stopConsuming(false);
        }

        // Get a new producer and consumer.
        InputStreamProducer newInputStreamProducer =
                inputStreamServices.getNewInputStreamProducer(propertiesDeviceParent);
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

        InputStream stream;
        try {
            stream = executeStreamCommand(device.getStreamingExecutable() + " " + channel);
            if (stream == null) {
                logger.error("Failed to run streaming executable.");
                return false;
            }
        } catch (InterruptedException e) {
            logger.debug("Streaming start was interrupted => {}", e.getMessage());
            return false;
        }

        logger.info("Configuring and starting the new SageTV producer...");
        if (!inputStreamServices.startProducing(encoderName, newInputStreamProducer, newConsumer, stream)) {
            return false;
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

        sageTVConsumerRunnable.isStreaming(GenericPipeDiscoverer.getStreamingWait());

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
            customChannels = !Util.isNullOrEmpty(returnChannels);
            firstPass = false;
            return "OK";
        }

        if (channel.equals("-1") || channel.equals("-2")) {
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
            if (inputStreamProducer == null) {
                inputStreamProducer = inputStreamServices.getInputStreamProducerRunnable();

                if (inputStreamProducer == null) {
                    return 0;
                }
            }

            return inputStreamProducer.getPackets();
        }
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
    public void stopEncoding() {

        logger.debug("Stopping encoding...");

        synchronized (exclusiveLock) {
            inputStreamServices.stopProducing(false);
            inputStreamProducer = null;

            super.stopEncoding();
            String stopCommand = device.getStoppingExecutable();
            if (!Util.isNullOrEmpty(stopCommand)) {
                try {
                    executeStopCommand(stopCommand);
                } catch (InterruptedException e) {
                    logger.debug("Stop was interrupted => ", e);
                }
            }
            currentProcess.destroy();
            currentProcess = null;
        }
    }

    @Override
    public void stopDevice() {
        logger.debug("Stopping device...");
        stopEncoding();
    }
}
