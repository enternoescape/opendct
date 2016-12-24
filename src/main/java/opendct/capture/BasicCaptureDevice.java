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

import opendct.channel.ChannelManager;
import opendct.channel.TVChannel;
import opendct.config.Config;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.DynamicConsumerImpl;
import opendct.consumer.FFmpegTransSageTVConsumerImpl;
import opendct.consumer.SageTVConsumer;
import opendct.sagetv.SageTVDeviceCrossbar;
import opendct.sagetv.SageTVManager;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class BasicCaptureDevice implements CaptureDevice {
    private final Logger logger = LogManager.getLogger(BasicCaptureDevice.class);

    protected SageTVConsumer sageTVConsumerRunnable = null;
    protected Thread sageTVConsumerThread;
    protected final ReentrantReadWriteLock sageTVConsumerLock = new ReentrantReadWriteLock();

    // Identification
    protected CaptureDeviceType encoderDeviceType;
    protected final String encoderParentName;
    protected final int encoderParentUniqueHash;
    protected final String encoderName;
    protected final int encoderUniqueHash;

    // Capabilities
    protected final boolean canSwitch;

    // Consumer parameters
    protected CaptureDevice[] childCaptureDevices = new CaptureDevice[0];
    protected long recordBufferSize = 0;
    protected String recordEncodingQuality = "";
    protected String recordLastFilename = null;
    protected int recordLastUploadID = 0;
    private long errorBytesStreamed = 0;

    // SageTV properties
    protected String lastChannel = "";
    protected AtomicLong recordingStartTime = new AtomicLong(0);
    protected AtomicLong lastRecordedBytes = new AtomicLong(0);
    protected int encoderMerit = 0;
    protected String encoderPoolName = "";
    protected String encoderLineup = "unknown";

    protected static boolean streamErrors;
    protected boolean offlineChannelScan;

    // Pre-pend this value for saving and getting properties related to just this tuner.
    protected final String propertiesDeviceRoot;

    // Pre-pend this value for saving and getting properties related to all tuners with this parent.
    protected final String propertiesDeviceParent;

    // Pre-pend this value for saving and getting properties related to all tuners.
    protected static final String propertiesDevicesGlobal = "sagetv.device.global.";

    static {
        streamErrors = Config.getBoolean("error_videos_enabled", true);
    }

    /**
     * Create a new version 3.0 basic capture device.
     *
     * @param deviceParentName This is the name of the device containing this capture device. This
     *                         is used for identifying groupings of devices.
     * @param deviceName       This name is used to uniquely identify this capture device. The encoder
     *                         version is defaulted to 3.0.
     * @throws CaptureDeviceIgnoredException If the configuration indicates that this device should
     *                                       not be loaded, this exception will be thrown.
     */
    public BasicCaptureDevice(String deviceParentName, String deviceName) throws CaptureDeviceIgnoredException {
        this(deviceParentName, deviceName, deviceParentName.hashCode(), deviceName.hashCode());
    }

    /**
     * Create a new basic capture device.
     *
     * @param deviceParentName This is the name of the device containing this capture device. This
     *                         is used for identifying groupings of devices.
     * @param deviceName       This name is used to uniquely identify this capture device.
     * @param encoderParentHash This is a unique integer for the parent device.
     * @param encoderHash This is a unique integer for the encoder device.
     * @throws CaptureDeviceIgnoredException If the configuration indicates that this device should
     *                                       not be loaded, this exception will be thrown.
     */
    public BasicCaptureDevice(String deviceParentName, String deviceName, int encoderParentHash, int encoderHash) throws CaptureDeviceIgnoredException {
        logger.entry(deviceParentName, deviceName, encoderParentHash, encoderHash);

        encoderDeviceType = CaptureDeviceType.UNKNOWN;

        encoderUniqueHash = encoderHash;
        encoderParentUniqueHash = encoderParentHash;
        propertiesDeviceRoot = "sagetv.device." + encoderUniqueHash + ".";
        propertiesDeviceParent = "sagetv.device.parent." + encoderParentUniqueHash + ".";

        // This allows you to customize your capture device names.
        encoderName = Config.getString(propertiesDeviceRoot + "device_name", deviceName);
        encoderParentName = Config.getString(propertiesDeviceParent + "device_name", deviceParentName);

        if (SageTVManager.getSageTVCaptureDevice(encoderName, false) != null) {
            logger.info("Skipping capture device '{}' because it is already initialized.", deviceName);
            throw new CaptureDeviceIgnoredException("Capture device cannot be loaded because it is already in use.");
        }

        // Check if this device is to be ignored.
        String[] ignoreDevices = Config.getStringArray(propertiesDevicesGlobal + "ignore_devices_csv", new String[0]);

        // If there are any entries on this list, only devices and parents on this list will be
        // loaded. All other discovered devices will be discarded.
        String[] onlyDevices = Config.getStringArray(propertiesDevicesGlobal + "only_devices_csv", new String[0]);

        if (onlyDevices.length > 0) {
            boolean deviceListed = false;
            for (String dctIgnoreDevice : onlyDevices) {
                if (deviceName.equals(dctIgnoreDevice) || deviceParentName.equals(dctIgnoreDevice)) {
                    deviceListed = true;
                    break;
                }
            }

            if (!deviceListed) {
                logger.info("Skipping capture device '{}' because it is not on the only load devices list.", deviceName);
                throw new CaptureDeviceIgnoredException("Capture device cannot be loaded because it is not on the only load devices list.");
            }
        } else {
            for (String ignoreDevice : ignoreDevices) {
                if (deviceName.equals(ignoreDevice)) {
                    logger.info("Skipping capture device '{}' because it is on the ignore list.", deviceName);
                    throw new CaptureDeviceIgnoredException("Capture device cannot be loaded because it is on the ignore list.");
                } else if (deviceParentName.equals(ignoreDevice)) {
                    logger.info("Skipping capture device '{}' under the parent '{}' because the parent is on the ignore list.", deviceName, encoderParentName);
                    throw new CaptureDeviceIgnoredException("Capture device cannot be loaded because the parent is on the ignore list.");
                }
            }
        }

        sageTVConsumerRunnable = getNewSageTVConsumer("");
        canSwitch = Config.getBoolean(propertiesDeviceRoot + "fast_network_encoder_switch", sageTVConsumerRunnable.canSwitch());

        // Populates the transcode quality field.
        getTranscodeProfile();

        lastChannel = Config.getString(propertiesDeviceRoot + "last_channel", "-1");
        encoderMerit = Config.getInteger(propertiesDeviceRoot + "encoder_merit", 0);
        offlineChannelScan = Config.getBoolean(propertiesDeviceParent + "offline_scan", false);

        //encoderLineup must be configured elsewhere or the lineup name will be "unknown."

        logger.exit();
    }

    /**
     * Gets an array containing any child capture devices.
     * <p/>
     * This is usually an empty array unless this is a hybrid capture device.
     *
     * @return An array of capture devices contained within this capture device.
     */
    public CaptureDevice[] getChildCaptureDevices() {
        return childCaptureDevices;
    }

    /**
     * Gets an the type of capture device.
     * <p/>
     * The word encoder is often used when describing a capture device.
     *
     * @return The assigned capture device type.
     */
    public CaptureDeviceType getEncoderDeviceType() {
        return encoderDeviceType;
    }

    /**
     * This is used to let SageTV know what ways it can request to use this device.
     * <p/>
     * Typically the device will be a Digital TV Tuner, but it could also be HDMI for example. This
     * will provide that option from withing SageTV.
     *
     * @return Available devices types.
     */
    public SageTVDeviceCrossbar[] getSageTVDeviceCrossbars() {
        return new SageTVDeviceCrossbar[] { SageTVDeviceCrossbar.DIGITAL_TV_TUNER };
    }

    /**
     * This is the displayed encoder name.
     * <p/>
     * This name is customizable in properties. If any encoder shares the same parent name as another
     * encoder, it is assumed that they are a part of the same device. Sometimes this information
     * can be helpful and potentially time-saving. The original name of the encoder will always be
     * used to generate the unique hash.
     *
     * @return The displayable encoder parent name.
     */
    public String getEncoderParentName() {
        return encoderName;
    }

    /**
     * This is a unique hash that will always only indicate this the parent of this encoder
     * globally.
     * <p/>
     * This hash must be globally unique so the encoder parent can be re-identified consistently.
     *
     * @return The encoder unique hash.
     */
    public int getEncoderParentUniqueHash() {
        return encoderParentUniqueHash;
    }

    /**
     * This is the displayed encoder name.
     * <p/>
     * This name is customizable in properties, but cannot share the name of any other devices. If
     * any device shares the same name as another device, the program will close with an appropriate
     * error. The original name of the encoder will always be used to generate the unique hash.
     *
     * @return The displayable encoder name.
     */
    public String getEncoderName() {
        return encoderName;
    }

    /**
     * This is a unique hash that will always only indicate this encoder globally.
     * <p/>
     * This hash must be globally unique so the encoder can be re-identified consistently.
     *
     * @return The encoder unique hash.
     */
    public int getEncoderUniqueHash() {
        return encoderUniqueHash;
    }

    /**
     * Get the last tuned channel.
     *
     * @return The last tuned channel or -1 if no channels have been tuned on this capture device.
     */
    public String getLastChannel() {
        return lastChannel;
    }

    /**
     * Use this to assign the last channel.
     * <p/>
     * It will update the current variable and set the last channel variable in properties.
     *
     * @param lastChannel The last channel tuned.
     */
    protected void setLastChannel(String lastChannel) {
        this.lastChannel = lastChannel;
        Config.setString(propertiesDeviceRoot + "last_channel", this.lastChannel);
    }

    /**
     * Can this capture device support switching?
     * <p/>
     * This value is derived at initialization from the selected consumer.
     *
     * @return <i>true</i> if the consumer supports switching.
     */
    @Override
    public boolean canSwitch() {
        return canSwitch;
    }

    /**
     * Get the current merit value for this encoder.
     *
     * @return The current merit value.
     */
    public int getPoolMerit() {
        return encoderMerit;
    }

    /**
     * Sets the current encoder merit value and saves it.
     * <p/>
     * Don't forget to call SageTVPoolManager.resortMerits(encoderPoolName) to resort the merits.
     *
     * @param merit New merit value.
     */
    public void setPoolMerit(int merit) {
        Config.setInteger("encoder_merit", merit);
        encoderMerit = merit;
    }

    /**
     * Gets the current encoder pool.
     *
     * @return The the name of the current pool.
     */
    public String getPoolName() {
        return encoderPoolName;
    }

    /**
     * Sets the current encoder pool value and saves it.
     * <p/>
     * Do not forget to call SageTVPoolManager.addPoolCaptureDevice(encoderPoolName, encoderName)
     * after changing this value or the the capture device will remain in the old pool.
     *
     * @param poolName The new name of the tuner pool.
     */
    public void setPoolName(String poolName) {
        Config.setString(propertiesDeviceRoot + "encoder_pool", poolName);
        encoderPoolName = poolName;
    }

    /**
     * Is this capture device allowed to participate in offline scanning?
     *
     * @return <i>true</i> if it is allowed.
     */
    public boolean isOfflineChannelScan() {
        return offlineChannelScan;
    }

    /**
     * Sets the offline channel scanning mode.
     * <p/>
     * This is currently a per parent setting, so if this value is changed here, on restart, it will
     * be changed for all devices on this parent device.
     * <p/>
     * Don't forget to add this capture device to the offline scanning pool or it will not be used
     * for offline scanning.
     *
     * @param offlineChannelScan <i>true</i> to enable offline channel scanning.
     */
    public void setOfflineChannelScan(boolean offlineChannelScan) {
        Config.getBoolean(propertiesDeviceParent + "offline_scan", false);
        this.offlineChannelScan = offlineChannelScan;
    }

    /**
     * Get the time in milliseconds since this recording started.
     *
     * @return The time in milliseconds since the recording started.
     */
    public long getRecordStart() {
        logger.entry();

        long returnValue = System.currentTimeMillis() - recordingStartTime.get();

        return logger.exit(returnValue);
    }

    /**
     * Returns the number of bytes currently recorded.
     * <p/>
     * This value is provided by the consumer.
     * <p/>
     * When a recording is switched, this number is reset to 0 and continues to increment as soon as
     * the new file is being written. This value does not increment until the incremented amount of
     * data in bytes has been sent to storage.
     *
     * @return The number of bytes currently written.
     */
    public long getRecordedBytes() {
        logger.entry();

        long returnValue = 0;

        sageTVConsumerLock.readLock().lock();

        try {
            if (errorBytesStreamed != 0) {
                returnValue = errorBytesStreamed;
            } else if (sageTVConsumerRunnable != null && sageTVConsumerRunnable.getIsRunning()) {
                returnValue = sageTVConsumerRunnable.getBytesStreamed();
            }
        } catch (Exception e) {
            logger.error("getRecordedBytes created an unexpected exception => ", e);
        } finally {
            sageTVConsumerLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    @Override
    public void streamError(File sourceFile) {

        if (!streamErrors) {
            return;
        }

        String currentFile = getRecordFilename();

        if (Util.isNullOrEmpty(currentFile)) {
            logger.debug("Recording appears to have already stopped." +
                    " Returning without streaming error message.");
            return;
        }

        boolean inProgress = getRecordedBytes() > 10485760;
        if (inProgress) {
            logger.warn("Appending message since the recording appears to" +
                    " actually be in progress. Bytes recorded: {}", getRecordedBytes());

            logger.info("Appending message to end of the recording '{}'", currentFile);
        } else {
            logger.info("Displaying message in place of the recording '{}'", currentFile);
            stopConsuming(true);
        }

        sageTVConsumerLock.readLock().lock();

        try {
            int retry = 25;

            while (retry-- > 0) {
                try {
                    long sourceLength = sourceFile.length();

                    if (!inProgress) {
                        Util.copyFile(sourceFile, new File(currentFile), true);
                        errorBytesStreamed = sourceLength;
                    }

                    for (int i = 0; i < 7; i++) {
                        Util.appendFile(sourceFile, new File(currentFile));
                        errorBytesStreamed += sourceLength;
                    }

                    break;
                } catch (IOException e) {
                    logger.error("Unable to write video message '{}' to '{}' => ", sourceFile, currentFile, e);
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }

                if (!isInternalLocked()) {
                    break;
                }
            }
        } finally {
            sageTVConsumerLock.readLock().unlock();
        }
    }

    /**
     * Get the filename currently in use.
     *
     * @return A full path to the file. <i>null</i> if nothing is currently recording.
     */
    public String getRecordFilename() {
        return recordLastFilename;
    }

    /**
     * Get the uploadID currently in use.
     *
     * @return A number or 0 if there is no uploadID.
     */
    public int getRecordUploadID() {
        return recordLastUploadID;
    }

    /**
     * Get the encoder quality currently in use.
     * <p/>
     * This value does not mean anything unless the consumer is actually capable of transcoding.
     *
     * @return A string representing the in use encoder quality.
     */
    public String getRecordQuality() {
        return recordEncodingQuality;
    }

    private int scanChannelIndex = 0;
    private int scanIncrement = 20;
    private TVChannel scanChannels[];

    /**
     * This pulls from the last offline channel scan data. If an offline scan has never happened,
     * this will likely return that none of the channels are tunable.
     *
     * @param channel The index of the tunable channel being requested.
     * @param combine This will return all of the tunable channels across 79 large semi-colon
     *                delimited strings. SageTV will accept the data in this format in one request.
     * @return The next channel that is tunable or ERROR if we are at the end of the list.
     */
    public String scanChannelInfo(String channel, boolean combine) {
        if (scanChannels == null ||
                channel.equals("0") || channel.equals("-1") || channel.equals("-2")) {
            // Always update the channels with the latest from the capture device when starting a
            // new channel scan.
            if (channel.equals("0")) {
                ChannelManager.updateChannelLineup(ChannelManager.getChannelLineup(getChannelLineup()));
            }
            scanChannels = ChannelManager.getChannelList(encoderLineup, false, false);
            scanChannelIndex = 0;

            if (scanChannels.length > 0) {
                // Ensure the number is at least 1.
                scanIncrement = scanChannels.length / 79 + 1;
            }

            return "OK";
        }

        if (!combine) {
            if (scanChannelIndex < scanChannels.length) {
                String returnChannel = scanChannels[scanChannelIndex].getChannelRemap();

                if (returnChannel == null) {
                    returnChannel = scanChannels[scanChannelIndex].getChannel();
                }

                scanChannelIndex += 1;

                return returnChannel;
            }
        } else if (scanChannelIndex < scanChannels.length) {
            StringBuilder stringBuilder = new StringBuilder();

            while (scanChannelIndex < scanChannels.length) {
                String returnChannel = scanChannels[scanChannelIndex].getChannelRemap();

                if (Util.isNullOrEmpty(returnChannel)) {
                    returnChannel = scanChannels[scanChannelIndex].getChannel();
                }

                stringBuilder.append(returnChannel);

                stringBuilder.append("(").append(scanChannels[scanChannelIndex].getName()).append(")");

                switch (encoderDeviceType) {
                    case DVBC_HDHOMERUN:
                         stringBuilder.append("DVB");
                    case DVBT_HDHOMERUN:
                         stringBuilder.append("DVB");
                    case ATSC_HDHOMERUN:
                         stringBuilder.append("ATSC");
                    case QAM_HDHOMERUN:
                         stringBuilder.append("QAM");
                    case DCT_HDHOMERUN:
                         stringBuilder.append("DCT");
                }

                stringBuilder.append(";");

                scanChannelIndex += 1;

                if (scanChannelIndex % scanIncrement == 0) {
                    break;
                }
            }

            if (stringBuilder.length() > 0) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }

            return stringBuilder.toString();
        }

        return "ERROR";
    }

    /**
     * Get the name of the channel lineup for this encoder.
     *
     * @return The name of the channel lineup in use on this encoder.
     */
    public final String getChannelLineup() {
        return encoderLineup;
    }

    /**
     * Set the name of the channel lineup for this encoder.
     *
     * @param lineup The name of the channel Lineup.
     */
    public final void setChannelLineup(String lineup) {
        encoderLineup = lineup.toLowerCase();
        Config.setString(propertiesDeviceParent + "lineup", encoderLineup);
    }

    /**
     * Switch the recording to a new uploadID without interrupting the stream.
     *
     * @param channel The channel number should not be getting changed, but this is here because
     *                SageTV could use it.
     * @param filename This is the new filename which probably would not be in use if you're using
     *                 uploadID, but it is here in case we find a situation that requires it.
     * @param recordBufferSize Buffer sizes greater than 0 will create a circular file-based buffer
     *                         of the requested length.
     * @param deviceType    The crossbar being requested from this capture device. It is assumed
     *                      that only one crossbar can be used at a time.
     * @param crossbarIndex The index of the crossbar being requested. If more than one crossbar of
     *                      the same type exists, this index will indicate which one is to be used.
     * @param uploadID      This is the new uploadID to be used.
     * @param remoteAddress This is the IP address of the server that made the request. This should
     *                      not change either, but it is here in case we find a situation that
     *                      requires it.
     * @return <i>true</i> if the switch was successful.
     */
    public boolean switchEncoding(String channel, String filename, long recordBufferSize,
                                  SageTVDeviceCrossbar deviceType, int crossbarIndex,
                                  int uploadID, InetAddress remoteAddress) {
        logger.entry();
        if (!canSwitch) {
            logger.error("'{}' was requested to switch, but the consumer thread" +
                    " does not support it.", BasicCaptureDevice.class.toString());
            return logger.exit(false);
        }

        boolean returnValue = true;

        sageTVConsumerLock.writeLock().lock();

        try {
            if (sageTVConsumerRunnable != null) {
                setLastChannel(channel);
                sageTVConsumerRunnable.setChannel(channel);

                if (sageTVConsumerRunnable.acceptsUploadID()) {
                    sageTVConsumerRunnable.switchStreamToUploadID(filename, recordBufferSize, uploadID);
                } else {
                    sageTVConsumerRunnable.switchStreamToFilename(filename, recordBufferSize);
                }

                recordingStartTime.getAndSet(System.currentTimeMillis());
                recordLastFilename = filename;
                recordLastUploadID = uploadID;
            } else {
                logger.error("'{}' was requested to switch, but the consumer thread is null.",
                        BasicCaptureDevice.class.toString());

                returnValue = false;
            }
        } finally {
            sageTVConsumerLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    /**
     * Start consuming the data provided by the producer.
     * <p/>
     * Call this method after the producer is already running to start consuming content to SageTV.
     *
     * @param sageTVConsumer  This is the consumer to be used to consume content to SageTV.
     * @param encodingQuality A string representation of the quality/codec to be used.
     * @param bufferSize      The file size at which point we circle back to the beginning of the file
     *                        again.
     * @return Returns <i>false</i> if there was a problem starting consumption.
     */
    protected boolean startConsuming(String channel, SageTVConsumer sageTVConsumer, String encodingQuality, long bufferSize) {
        logger.entry();

        boolean returnValue = true;

        //In case we left the last consumer running.
        if (!stopConsuming(true)) {
            logger.warn("Waiting for consumer thread to exit was interrupted.");
            return logger.exit(false);
        }

        sageTVConsumerLock.writeLock().lock();

        try {
            errorBytesStreamed = 0;
            recordBufferSize = bufferSize;
            recordEncodingQuality = encodingQuality;
            recordLastFilename = sageTVConsumer.getEncoderFilename();
            recordLastUploadID = sageTVConsumer.getEncoderUploadID();

            sageTVConsumerRunnable = sageTVConsumer;
            setLastChannel(channel);
            sageTVConsumerRunnable.setChannel(channel);
            sageTVConsumerRunnable.setRecordBufferSize(recordBufferSize);

            if (sageTVConsumer instanceof FFmpegTransSageTVConsumerImpl) {
                sageTVConsumerRunnable.setEncodingQuality(getTranscodeProfile());
            } else {
                sageTVConsumerRunnable.setEncodingQuality(recordEncodingQuality);
            }
            sageTVConsumerThread = new Thread(sageTVConsumerRunnable);
            sageTVConsumerThread.setName(sageTVConsumerRunnable.getClass().getSimpleName() + "-" +
                    sageTVConsumerThread.getId() + ":" + encoderName);

            sageTVConsumerThread.start();

        } catch (Exception e) {
            logger.error("startConsuming created an unexpected exception => ", e);
            returnValue = false;
        } finally {
            sageTVConsumerLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    /**
     * Get a new consumer per the preferences in properties.
     *
     * @return A new consumer.
     */
    protected SageTVConsumer getNewSageTVConsumer(String channel) {
        return Config.getSageTVConsumer(
                propertiesDeviceRoot + "consumer",
                Config.getString("sagetv.new.default_consumer_impl",
                        DynamicConsumerImpl.class.getName()),
                channel);
    }

    /**
     * Get a new consumer for channel scanning per the preferences in properties.
     *
     * @return A new consumer.
     */
    protected SageTVConsumer getNewChannelScanSageTVConsumer() {
        return Config.getSageTVConsumer(
                propertiesDeviceRoot + "channel_scan_consumer",
                Config.getString("sagetv.new.default_channel_scan_consumer_impl",
                        DynamicConsumerImpl.class.getName()), "");
    }

    @Override
    public void setTranscodeProfile(String transcodeProfile) throws DeviceOptionException {
        // There isn't any good validation we can do here, so we just set the value.
        Config.setString(propertiesDeviceRoot + "transcode_profile", transcodeProfile);
    }

    @Override
    public String getTranscodeProfile() {
        return Config.getString(
                propertiesDeviceRoot + "transcode_profile",
                Config.getString("sagetv.new.default_transcode_profile",
                        ""));
    }

    @Override
    public synchronized void setConsumerName(String consumerName) throws DeviceOptionException {
        String oldValue = getConsumerName();
        Config.setString(propertiesDeviceRoot + "consumer", consumerName);
        if (!getConsumer().getClass().getName().endsWith(consumerName))
        {
            Config.setString(propertiesDeviceRoot + "consumer", oldValue);
            throw new DeviceOptionException(consumerName + " is not a valid consumer.", "consumer");
        }
    }

    @Override
    public String getConsumerName() {
        return Config.getString(
                propertiesDeviceRoot + "consumer",
                Config.getString("sagetv.new.default_consumer_impl",
                        DynamicConsumerImpl.class.getName()));
    }

    /**
     * Get if the consumer is running.
     *
     * @return <i>false</i> if the consumer is not running or one has not been initialized.
     */
    public Boolean isConsuming() {
        logger.entry();

        boolean returnValue = false;

        sageTVConsumerLock.readLock().lock();

        try {
            if (sageTVConsumerRunnable != null) {
                returnValue = sageTVConsumerRunnable.getIsRunning();
            }
        } finally {
            sageTVConsumerLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    /**
     * Get the currently in use consumer.
     *
     * @return The current consumer or <i>null</i> if one has not been initialized.
     */
    public SageTVConsumer getConsumer() {
        return sageTVConsumerRunnable;
    }

    /**
     * Stops the consumer if it is running.
     *
     * @param wait Set this <i>true</i> if you want to wait for the consumer to completely stop.
     * @return <i>false</i> if blocking the consumer was interrupted.
     */
    protected boolean stopConsuming(boolean wait) {
        logger.entry();

        boolean returnValue = true;

        sageTVConsumerLock.writeLock().lock();

        try {
            if (sageTVConsumerRunnable != null && sageTVConsumerRunnable.getIsRunning()) {
                logger.debug("Stopping consumer thread...");
                sageTVConsumerRunnable.stopConsumer();
                sageTVConsumerThread.interrupt();

                int counter = 0;
                while (sageTVConsumerThread.isAlive()) {
                    if (counter++ < 5) {
                        logger.debug("Waiting for consumer thread to stop...");
                    } else {
                        // It should never take 5 seconds for the consumer to stop. This should make
                        // everyone aware that something abnormal is happening.
                        logger.warn("Waiting for consumer thread to stop for over {} seconds...", counter);
                    }
                    sageTVConsumerThread.join(1000);
                }

                recordEncodingQuality = null;
                recordLastFilename = null;
                recordLastUploadID = 0;
            } else {
                logger.debug("Consumer is was not in progress.");
            }
        } catch (InterruptedException e) {
            logger.debug("'{}' thread was interrupted => {}",
                    Thread.currentThread().getClass().toString(), e);
            returnValue = false;
        } finally {
            sageTVConsumerLock.writeLock().unlock();
        }


        return logger.exit(returnValue);
    }

    /**
     * Stops encoding from the capture device.
     * <p/>
     * This does not stop the producer. The producer should override this method and call this
     * method along with the needed actions to stop the producer.
     */
    @Override
    public void stopEncoding() {
        logger.entry();

        recordLastFilename = null;
        stopConsuming(false);

        logger.exit();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BasicCaptureDevice that = (BasicCaptureDevice) o;

        if (encoderParentUniqueHash != that.encoderParentUniqueHash) return false;
        return encoderUniqueHash == that.encoderUniqueHash;

    }

    @Override
    public int hashCode() {
        int result = encoderParentUniqueHash;
        result = 31 * result + encoderUniqueHash;
        return result;
    }
}
