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
import opendct.capture.services.UDPCaptureDeviceServices;
import opendct.channel.*;
import opendct.config.Config;
import opendct.consumer.SageTVConsumer;
import opendct.producer.HTTPProducer;
import opendct.producer.SageTVProducer;
import opendct.producer.UDPProducer;
import opendct.sagetv.SageTVDeviceCrossbar;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.GenericHttpDiscoverer;
import opendct.tuning.http.GenericHttpDiscoveredDevice;
import opendct.tuning.http.GenericHttpDiscoveredDeviceParent;
import opendct.util.StreamLogger;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenericHttpCaptureDevice extends BasicCaptureDevice {
    private final static Logger logger = LogManager.getLogger(GenericHttpCaptureDevice.class);

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();
    private final GenericHttpDiscoveredDeviceParent parent;
    private final GenericHttpDiscoveredDevice device;

    private final static Runtime runtime = Runtime.getRuntime();
    private HTTPCaptureDeviceServices httpServices;
    private UDPCaptureDeviceServices udpServices;
    long lastTuneTime = System.currentTimeMillis();

    private Thread stoppingThread;

    private SageTVProducer producer;
    //private HTTPProducer httpProducer;
    //private UDPProducer udpProducer;

    public GenericHttpCaptureDevice(GenericHttpDiscoveredDeviceParent loadParent, GenericHttpDiscoveredDevice loadDevice) throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        super(loadParent.getFriendlyName(), loadDevice.getFriendlyName(), loadParent.getParentId(), loadDevice.getId());

        parent = loadParent;
        device = loadDevice;

        configureServices(device.getStreamingUrl());

        String altStreamingUrl = device.getAltStreamingUrl();
        if (!altStreamingUrl.isEmpty()) {
            configureServices(altStreamingUrl);
        }

        encoderDeviceType = CaptureDeviceType.LIVE_STREAM;

        super.setChannelLineup(Config.getString(propertiesDeviceParent + "lineup", "generic_http"));

        if (ChannelManager.getChannelLineup(encoderLineup) == null) {
            ChannelManager.addChannelLineup(new ChannelLineup(encoderLineup, encoderName, ChannelSourceType.STATIC, ""), false);
        }

        super.setPoolName(Config.getString(propertiesDeviceRoot + "encoder_pool", "generic_http"));

    }

    private void configureServices(String uri) throws CaptureDeviceLoadException {
        try {
            URI testUri = URI.create(uri);
            String testProtocol = testUri.getScheme().toLowerCase();
            if (testProtocol.equals("udp")) {
                int testPort = testUri.getPort();
                if (testPort > 0){
                    InetAddress udpRemoteServer = InetAddress.getByName(testUri.getHost());
                    udpServices = new UDPCaptureDeviceServices(encoderName, propertiesDeviceParent, testPort, udpRemoteServer);
                } else {
                    throw new CaptureDeviceLoadException("UDP URI must contain a specific port.");
                }
            } else if (testProtocol.equals("http") || testProtocol.equals("https")) {
                httpServices = new HTTPCaptureDeviceServices();
            } else {
                throw new CaptureDeviceLoadException("Unsupported protocol: " + testProtocol);
            }
        } catch (UnknownHostException e) {
            throw new CaptureDeviceLoadException(e);
        }
    }

    @Override
    public SageTVDeviceCrossbar[] getSageTVDeviceCrossbars() {
        return new SageTVDeviceCrossbar[] { SageTVDeviceCrossbar.HDMI };
    }

    private SageTVProducer getNewProducer(URI uri) {
        String testProtocol = uri.getScheme().toLowerCase();
        switch (testProtocol) {
            case "http":
                return httpServices.getNewHTTPProducer(propertiesDeviceParent, false);
            case "https":
                return httpServices.getNewHTTPProducer(propertiesDeviceParent, true);
            case "udp":
                return udpServices.getNewUDPProducer(propertiesDeviceParent);
        }

        return null;
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

    private StringBuilder stdOutBuilder = new StringBuilder();
    private StringBuilder errOutBuilder = new StringBuilder();
    private int executeCommand(String execute) throws InterruptedException {
        if (Util.isNullOrEmpty(execute)) {
            return 0;
        }

        if (execute.startsWith("http://") || execute.startsWith("https://")) {
            BufferedReader reader = null;
            InputStream stream = null;
            try {
                URL url = new URL(execute);
                URLConnection connection = url.openConnection();
                connection.setReadTimeout(10000);
                connection.setConnectTimeout(10000);
                stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    logger.info("stdout: {}", line);
                }
            } catch (Exception e) {
                logger.error("Unable to use the URL '{}' => ", execute, e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {}
                } else if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception e) {}
                }
            }
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

            // Ensure we don't retain anything crazy.
            if (stdOutBuilder.capacity() > 1024) {
                if (stdOutBuilder.length() > 1024) {
                    stdOutBuilder.setLength(1024);
                }
                stdOutBuilder.trimToSize();
            }
            if (errOutBuilder.capacity() > 1024) {
                if (errOutBuilder.length() > 1024) {
                    errOutBuilder.setLength(1024);
                }
                errOutBuilder.trimToSize();
            }

            return returnValue;
        } catch (IOException e) {
            logger.error("Unable to run tuning executable '{}' => ", execute, e);
        }

        return -1;
    }

    @Override
    public boolean isInternalLocked() {
        return locked.get();
    }

    @Override
    public boolean setLocked(boolean locked) {
        // This means the lock was already set
        if (this.locked.getAndSet(locked) == locked) {
            logger.info("Capture device was already {}.", (locked ? "locked" : "unlocked"));
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

        if (stoppingThread != null) {
            stoppingThread.interrupt();
            stoppingThread = null;
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

        if (producer instanceof HTTPProducer) {
            httpServices.stopProducing(false);
        } else {
            udpServices.stopProducing(false);
        }


        // If we are trying to restart the stream, we don't need to stop the consumer.
        if (!retune) {
            stopConsuming(false);
        }

        // Get a new producer and consumer.
        URI connectURI;
        try {
            connectURI = device.getURI(channel);
        } catch (URISyntaxException e) {
            logger.error("Unable to start streaming because the URI is invalid.");
            return false;
        }

        SageTVProducer newProducer = getNewProducer(connectURI);
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
            if (newProducer instanceof HTTPProducer) {
                URL connectURL = device.getURL(channel);
                String username = device.getHttpUsername();
                String password = device.getHttpPassword();
                boolean enableAuth = username.length() > 0 && password.length() > 0;
                if (enableAuth) {
                    HTTPCaptureDeviceServices.addCredentials(connectURL, username, password);
                }
                if (!httpServices.startProducing(encoderName, (HTTPProducer) newProducer, newConsumer, enableAuth, connectURL)) {
                    return false;
                }
            } else {
                if (!udpServices.startProducing(encoderName, (UDPProducer) newProducer, newConsumer, connectURI)) {
                    return false;
                }
            }
        } catch (MalformedURLException e) {
            logger.error("Unable to start streaming because the URL is invalid.");
            return false;
        }

        // Now that the producer is running, set this value so we have a way to call back and stop it.
        producer = newProducer;

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
            SageTVProducer localProducer = producer;
            if (localProducer == null) {
                return 0;
            } else {
                return localProducer.getPackets();
            }
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
        stopEncoding(device.getStoppingDelay() == 0);
    }

    public void stopEncoding(boolean immediateStop) {

        logger.debug("Stopping encoding...");

        synchronized (exclusiveLock) {
            if (producer instanceof HTTPProducer) {
                httpServices.stopProducing(false);
            } else {
                udpServices.stopProducing(false);
            }
            producer = null;

            super.stopEncoding();

            final Runnable delayedRunnable = new Runnable() {
                @Override
                public void run() {
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
            };

            if (immediateStop) {
                delayedRunnable.run();
            } else {
                stoppingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int stoppingDelay = device.getStoppingDelay();

                            logger.debug("Stopping executable will be run in {} milliseconds.", stoppingDelay);
                            Thread.sleep(stoppingDelay);

                            synchronized (exclusiveLock) {
                                if (stoppingThread != Thread.currentThread() ||
                                        Thread.currentThread().isInterrupted()) {

                                    return;
                                }

                                delayedRunnable.run();
                            }
                        } catch (InterruptedException e) {
                            logger.debug("Stopping executable was cancelled.");
                        }
                    }
                });
                stoppingThread.setName("StoppingThread-" + stoppingThread.getId());
                stoppingThread.start();
            }
        }
    }

    @Override
    public void stopDevice() {
        logger.debug("Stopping device...");

        stopEncoding(true);
    }
}
