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

import opendct.channel.BroadcastStandard;
import opendct.channel.CopyProtection;
import opendct.channel.TVChannel;
import opendct.config.options.DeviceOptions;

import java.net.InetAddress;

public interface CaptureDevice extends DeviceOptions {
/*
Capture Device Flow
  *based on DCT network encoder, the first two steps could be
   different for another type of device.

====================================================================================================
 Discovery and initialization of a capture device
====================================================================================================

RegistryListener - Returns when a remote device is ready for use. It should only do this once
                   per device.

RegisterDevice - Checks if the device is already being used by this program. Creates
                 CaptureDevice based on if the remote device is something we know how to use. This
                 is also the point when the unique device name should already be decided. The device
                 name can be customized after the capture device has been uniquely identified.

    DCTCaptureDeviceImpl - This is currently the only implementation available for CableCARD based
                           UPnP devices.

    RTPCaptureDevice - DCTCaptureDeviceImpl is based on this class. Anything generic about creating
                       and capturing an RTP connection arguably should be in this class. This class
                       should not contain anything that is only used for a specific type of capture
                       device.

    BasicCaptureDevice - RTPCaptureDevice is based on this class. This abstract class should be used
                         as the basis for all new capture devices. It contains methods that manage
                         many of the most trivial parts when designing a capture device. During
                         initialization is also checks for if the device should be loaded or not
                         based on preferences in the configuration properties file.

SageTVManager - Receives any CaptureDevice implementation and gives it a port number for the SageTV
                server to begin communication. If allowed in the properties and the implementation
                is a network encoder version >2.0 it will get a shared port. This enables server
                connection concurrency based on properties and if there is more than one
                CaptureDevice available on that port. That means multiple SageTV servers will be
                able to communicate using the same port. When there is only one tuning device on the
                port all new connections supersede the current connection and the current connection
                is dropped.

SageTVSocketServer - Receives connections from SageTV servers and starts a SageTVRequestHandler per
                     server connection on the assigned listening port. If there is only one
                     CaptureDevice available to this port, the server will always replace the
                     connection with the most recent connection with the assumption that the server
                     must have informally dropped the old connection. If there is more than one
                     capture device available, the server will start a new listening thread for the
                     new connection to allow for multiple servers to connect at the same time. It is
                     up to the user to make sure they don't use the same tuner on different servers
                     at the same time.

====================================================================================================
 Processing a request from a SageTV server
====================================================================================================

SageTVRequestHandler - This can be assigned a default capture device or at the request of the SageTV
                       server, it will retrieve the capture device by name from the pool in
                       SageTVManager in a thread-safe way. Currently this will not prevent SageTV
                       from requesting a tuner that it should not be requesting on that port. It
                       will attempt to use the CaptureDevice based on what the capture device says
                       it can do.

CaptureDevice - This interface needs to implement everything needed to have a functional capture
                device. Make certain that your CaptureDevice implementation is thread-safe because
                for example, the request to stopDevice can be issued at any time and even though it
                will likely mean the program is shutting down, it would be prudent to not have the
                possibility that your device is left in a undesirable state (Ex. you need to reboot
                before everything starts working again).

                In the DCTCaptureDeviceImpl implementation, tuning a channel will tune in a channel
                on the DCT. The consumer is then initialized and started. This is required because
                the producer has nowhere to place data until the consumer at least exists. Then the
                producer is initialized and started. It selects a port to listen for UDP. That port
                is then used to configure the DCT over RTSP to start broadcasting to a local port
                and the local IP address that discovered the DCT. Now that data is currently being
                written to the consumer from the producer, the consumer will start data processing
                and transfer that data to directly to a file or via uploadID.

====================================================================================================
 Tuning a channel for playback
====================================================================================================

SageTVRequestHandler - Requests by a SageTV server for anything regarding a CaptureDevice always
                       start here.

DCTCaptureDeviceImpl - Receives the tune request. Tunes the channel.

RawSageTVConsumer - Provides a buffer via the write() method for the producer to buffer the data it
                    produces. When this is created, its own thread is also started. It will begin
                    processing data as soon as bytes are available to be read from its buffer.

NIORTPProducerImpl - Starts receiving UDP packets on the port that RTSPClient will configure in the
                     next step. The goal of this implementation is to make sure that no packet is
                     missed, only queue packets from the intended IP address and filter out non-RTP
                     packets. The consumer in this case should only be looking at MPEG-TS. The RTP
                     headers can not still be present. All other processing is done by the consumer.

DCTRTSPClientImpl - Configures the connection for RTP streaming to this IP address and within a
                    specific port range or automatically selected by the socket method. If
                    successful, streaming starts immediately and the producer which is already
                    listening is buffering data.
 */


    /**
     * Retrieve child capture devices.
     * <p/>
     * You can create a capture device that contains other capture devices. SageTV will not be aware
     * of the child devices. This would help in a situation where you have a device that you can
     * access in two different ways, but not at the same time. For example, this could allow you to
     * use a DCT as a primary, but if the channel is copy protected, it would switch to an analog
     * capture using the same DCT. You need to create a "master" device implementing this
     * functionality. You could also use this to run two tuners simultaneously on the same
     * recording. Some people do this in MythTV when using an HD-PVR to get closed captioning from
     * another source such as an analog tuner with S-Video input. If there are no child capture
     * devices, you should return an empty list.
     *
     * @return A list of all child capture devices. If none exist, an empty array will be returned.
     */
    public CaptureDevice[] getChildCaptureDevices();

    /**
     * This is used to identify devices that all should have the same capabilities.
     * <p/>
     * This is also used for special devices that might need to be handled a little differently.
     *
     * @return The encoder type as an enum of <b>CaptureDeviceType</b>.
     */
    public CaptureDeviceType getEncoderDeviceType();

    /**
     * This is used to identify devices that are all on the same parent device.
     * <p/>
     * These devices operate independently, but are all part of the same device. This is used
     * primarily to spread out offline channel scanning. This is not the same as child capture
     * devices since that is more of combination tuner and this is just indicating that if any other
     * capture devices have the same parent name they are on the same physical "board" and should be
     * able to tune into the exact same content.
     *
     * @return The name of the parent device or just the name of the capture device if there is no
     * parent.
     */
    public String getEncoderParentName();

    /**
     * A unique hash exclusive to the parent of this capture device.
     * <p/>
     * This is used for the properties file. Make sure that the hash will always be the same every
     * time the device is detected, or it may cause problems. If there is more than one capture
     * device that could possibly end up with the same hash, you will need to address that.
     *
     * @return The hash as an integer.
     */
    public int getEncoderParentUniqueHash();

    /**
     * This is the name your tuning device will use when it is displayed in SageTV.
     * <p/>
     * It is recommended to prepend the prefix of your SageTVCaptureDevice implementation. For
     * example, if the implementation name is DCTCaptureDeviceImpl, the name would be:
     * "DCT-Ceton InfiniTV PCIe (xx-xx-xx-xx) Tuner 1"
     * <p/>
     * Ensure that you are enumerating your capture devices so naming conflicts are unlikely.
     *
     * @return The name of the capture device as it is to be displayed in SageTV.
     */
    public String getEncoderName();

    /**
     * A unique hash exclusive to this capture device.
     * <p/>
     * This is used for the properties file and what is sent with the properties in auto-discovery.
     * Make sure that the hash will always be the same every time the device is detected, or it may
     * cause problems. If there is more than one capture device that could possibly end up with the
     * same hash, you will need to address that.
     *
     * @return The hash as an integer.
     */
    public int getEncoderUniqueHash();

    /**
     * Is the encoder is currently locked?
     * <p/>
     * This value should be set and cleared by SageTVManager. When this is set, the tuner is not to
     * be doing anything other than the recording that was requested by the SageTV server. This
     * should be checked before an attempt to do anything that might interrupt a recording is
     * performed. Additional checks and synchronized locks must be a part of any method intended
     * to be used based off of the results of this value.
     *
     * @return <i>true</i> if the encoder is in progress.
     */
    public boolean isLocked();

    /**
     * Set the locked status for the capture device.
     * <p/>
     * When <i>true</i> is passed as a parameter, this method must block until any activity that
     * could prevent successful encoding has ceased. This needs to return safely as quickly as
     * possible or there will be delays on the SageTV server side of things. This method will only
     * be used by SageTVRequestHandler. No other methods should ever use this.
     *
     * @param locked Put the capture device into locked status.
     * @return <i>true</i> if successfully locked. This allows for multiple tuning requests to try
     *         to lock the same device at the same time, but only one will be successful and the
     *         others will need to try the next device.
     */
    public boolean setLocked(boolean locked);

    /**
     * Returns merit for this encoder. A higher merit means this device will have a higher priority
     * over other encoders.
     *
     * @return Merit value.
     */
    public int getMerit();

    /**
     * Sets the merit for this encoder. This is used to change the merit value at runtime.
     *
     * @param merit New merit value.
     */
    public void setMerit(int merit);

    /**
     * Is this encoder allowed to participate in offline channel scanning.
     *
     * @return <i>true</i> if it is allowed.
     */
    public boolean isOfflineChannelScan();

    /**
     * Enable this encoder to participate in offline channel scanning.
     *
     * @param offlineChannelScan <i>true</i> to enable offline channel scanning.
     */
    public void setOfflineChannelScan(boolean offlineChannelScan);



    /**
     * Returns the name of the tuner pool for this encoder.
     *
     * @return The name of the tuner pool.
     */
    public String getEncoderPoolName();

    /**
     * Sets the name of the tuner pool for this encoder.
     * <p/>
     * This value should only be changed by this method via SageTVManager. If changed any other way
     * the results could be unpredictable.
     *
     * @param poolName The new name of the tuner pool.
     */
    public void setEncoderPoolName(String poolName);

    /**
     * Is the encoder is currently locked by an external program?
     * <p/>
     * This feature is mostly for HDHomeRun devices, but it could be extended for other devices if
     * we find a use.
     *
     * @return <i>true</i> if the encoder is locked by an external program.
     */
    public boolean isExternalLocked();

    /**
     * Set the tuner to be locked/unlocked.
     * <p/>
     * This feature is mostly for HDHomeRun devices, but it could be extended for other devices if
     * we find a use. Forcing an unlock will be a per tuner feature that this method will only
     * execute if the tuner itself will allow it. This possibility is the reason for a return value
     * to indicate if the device was unlocked or not.
     *
     * @param locked <i>true</i> to lock the encoder via it's external "native" method.
     * @return <i>true</i> if the device was successfully locked/unlocked.
     */
    public boolean setExternalLock(boolean locked);

    /**
     * Tunes a channel outside of any requests from the SageTV server and updates information about
     * the channel.
     * <p/>
     * The provided TVChannel is tuned and then updated with current information about the channel
     * such as CCI and signal strength. If the capture device is locked for any reason, this will
     * not attempt to tune the channel. This method should never be used by the
     * SageTVRequestHandler. The execution of this method should be as brief as is possible and it
     * should never actually do anything if <b>isLocked()</b> is <i>true</i> when the method is
     * called. If the value of <b>isLocked()</b> becomes <i>true</i> in mid-progress, this method
     * must return <i>false</i>.
     *
     * @param tvChannel A TVChannel object with at the very least a defined channel. Otherwise there
     *                  is nothing to tune.
     * @return <i>true</i> if the test was complete and successful. <i>false</i> if we should try
     * again on a different capture device since this one is currently locked.
     */
    public boolean getChannelInfoOffline(TVChannel tvChannel, boolean skipCCI);

    /**
     * Stop anything currently happening and put the capture device in a safe state for shutdown.
     */
    public void stopDevice();

    /**
     * Stop the capture device from recording.
     * <p/>
     * This method does not need to block until the stop encoding request is complete. Just make
     * sure that if a new recording request comes in that you make sure this process is done or you
     * wait for it to be done before continuing with the new recording.
     */
    public void stopEncoding();

    /**
     * Returns the last tuned channel. Default to -1 if there is no last channel.
     *
     * @return The last channel tuned or -1 as a string.
     */
    public String getLastChannel();

    /**
     * Start a new recording directly to the provided file.
     * <p/>
     * This method must work correctly as a bare minimum for a capture device to be able to stream
     * anything to SageTV.
     *
     * @param channel         A string representation of the channel to be tuned. Any needed translations
     *                        must be implemented by the capture device.
     * @param filename        This is the full path and filename to be used for recording.
     * @param encodingQuality A string representation of the quality/codec to be used.
     * @param bufferSize      The file size at which point we circle back to the beginning of the file
     *                        again.
     * @return <i>true</i> if the the recording started was successfully.
     */
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize);

    /**
     * According to the consumer being used, can this capture device write to a file directly for
     * recordings.
     *
     * @return <i>true</i> if the consumer being used supports direct file writing.
     */
    public boolean canEncodeFilename();

    /**
     * Start a new recording using the provided uploadID.
     * <p/>
     * This method must work correctly as a bare minimum for a capture device to be able to stream
     * anything to SageTV.
     *
     * @param channel         A string representation of the channel to be tuned. Any needed translations
     *                        must be implemented by the capture device.
     * @param filename        This is the full path and filename to be used for recording.
     * @param encodingQuality A string representation of the quality/codec to be used.
     * @param bufferSize      The file size at which point we circle back to the beginning of the file
     *                        again.
     * @param uploadID        This is the uploadID provided by SageTV to permit you to record via the
     *                        MediaServer.
     * @param remoteAddress   This is the IP address of the SageTV server requesting the recording.
     * @return <i>true</i> if the the recording started was successfully.
     */
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress);

    /**
     * According to the consumer being used, can this capture device use uploadID for recordings.
     *
     * @return <i>true</i> if the consumer being used supports uploadID file writing.
     */
    public boolean canEncodeUploadID();

    /**
     * Switch out the current recording and transition into a new recording.
     * <p/>
     * Typically you will pass this request on to the consumer. It will need to determine a good
     * place within the stream to stop writing to the current stream and to start writing to the new
     * stream. The streams should be able to be combined and playback smoothly. In the case of
     * MPEG-TS is is recommended to start the new stream with an I frame. This should be implemented
     * by the consumer, but it is passed through the capture device in case we need to handle a
     * channel change too.
     *
     * @param channel    The channel number should not be getting changed, but this is here because
     *                   SageTV could use it.
     * @param filename   This is the new filename to be used.
     * @param bufferSize The file size at which point we circle back to the beginning of the file
     *                   again.
     * @return <i>true</i> if the transition was successful.
     */
    public boolean switchEncoding(String channel, String filename, long bufferSize);

    /**
     * Switch out the current recording and transition into a new recording.
     * <p/>
     * You will need to determine a good place within the stream to stop writing to the current
     * stream and to start writing to the new stream. The streams should be able to be combined and
     * playback smoothly. In the case of MPEG-TS is is recommended to start the new stream with an I
     * frame. This should be implemented by the consumer, but it is passed through the capture
     * device in case we need to handle a channel change too.
     *
     * @param channel       The channel number should not be getting changed, but this is here
     *                      because SageTV could use it.
     * @param filename      This is the new filename which probably would not be in use if you're
     *                      using uploadID, but it is here in case we find a situation that requires
     *                      it.
     * @param bufferSize    The file size at which point we circle back to the beginning of the file
     *                      again.
     * @param uploadID      This is the new uploadID to be used.
     * @param remoteAddress This is the IP address of the server that made the request. This should
     *                      not change either, but it is here in case we find a situation that
     *                      requires it.
     * @return Returns <i>true</i> if the transition was successful.
     */
    public boolean switchEncoding(String channel, String filename, long bufferSize, int uploadID, InetAddress remoteAddress);

    /**
     * This is used to determine if SWITCH works with the consumer implementation is use on this capture device.
     * <p/>
     * If this returns <i>true</i> that means that the methods <b>switchEncoding(String,String)</b> and
     * <b>switchEncoding(String,String,int,InetAddress)</b> will work if called. The methods <b>canEncodeFilename</b>
     * and <b>canEncodeUploadID</b> followed by the preference in properties if both are supported will determine which
     * switch method is used.
     *
     * @return Returns <i>true</i> if this capture device is using a consumer implementation that supports the SWITCH
     * command correctly.
     */
    // Either you support switching for both methods or you
    // don't support it at all.
    public boolean canSwitch();

    /**
     * This is the current time minus the recording start time in milliseconds.
     * <p/>
     * When starting a recording, be sure to note the start time to support this method.
     *
     * @return The total number of milliseconds passed since this recording started.
     */
    public long getRecordStart();

    /**
     * Gets the current number of bytes written to the directly to a file or via uploadID.
     * <p/>
     * This must work correctly and accurately as it is critical for proper SageTV recording. If this value is not
     * growing SageTV can appear to hang. Also if this cannot return a value quickly SageTV will also appear to hang. I
     * recommend retrieving a value from an atomic type since it takes two cycles to update a 64 bit number on the 32
     * bit JVM and the thread could potentially sleep at just the wrong time returning a strange value.
     *
     * @return Returns the total number of bytes written.
     */
    public long getRecordedBytes();

    /**
     * Return a valid incremental channel number for the provided index.
     * <p/>
     * Prior to this, SageTV will send the BUFFER command to the capture device, telling it to tune into the last
     * channel. You can use this to aid in scanning or just leave it alone.
     * <p/>
     * SageTV will send you an index number via AUTOINFOSCAN. You need to reply with a channel number. I'm not sure
     * there are any limitations on naming here, but note that if it's not a number of a channel in the EPG it will not
     * map to it automatically. The very first request will be 0. The response to that request is ignored. It is
     * followed by another request of 0. The response to this one is added as a tunable channel It does not need to
     * match the index number that SageTV sent you. When there are no more channels, return null and that will signal
     * the request handler to send ERROR to SageTV to end the scan. The scan will only iterate over 159 channels and for
     * some reason will send a request for 0 twice.
     * <p/>
     * If the index number is -1, it's just telling you to move your tunable channel index back to the beginning. You
     * can reply anything to this request to acknowledge it.
     * <p/>
     * After the scan is completed, SageTV will send the STOP command.
     *
     * @param channel The index of the tunable channel being requested.
     * @return The name of the tunable channel.
     */
    public String scanChannelInfo(String channel);

    /**
     * Is the tuner going to work as configured?
     * <p/>
     * Return <i>true</i> if the tuner is configured in a manner that it will be able to produce a
     * recording. You can have it already tuned and buffering, but this is just making sure that we
     * did not request an invalid configuration.
     *
     * @return Returns <i>true</i> if the tuner will work as configured.
     */
    public boolean isReady();

    /**
     * Gets the channel lineup currently in use.
     *
     * @return The name of the channel lineup.
     */
    public String getChannelLineup();

    /**
     * Change the channel lineup.
     *
     * @param lineup The name of the channel Lineup.
     */
    public void setChannelLineup(String lineup);

    /**
     * Get the current filename being recorded.
     *
     * @return A full path to the file. <i>null</i> if nothing is currently recording.
     */
    public String getRecordFilename();

    /**
     * Get the current uploadID in use.
     *
     * @return A number or 0 if there is no uploadID.
     */
    public int getRecordUploadID();

    /**
     * Get the current quality in use.
     *
     * @return The name of the quality in use.
     */
    public String getRecordQuality();

    /**
     * Returns the broadcast standard for this capture device.
     * <p/>
     * If there isn't a standard or there isn't a consistent way to determine it (Ex. a web feed or
     * analog encoder), return the encoding type.
     *
     * @return Returns the determined broadcast standard in use for this capture device.
     */
    public BroadcastStandard getBroadcastStandard();

    /**
     * Get the signal strength for a channel.
     * <p/>
     * The scale being assumed is 0-100. Anything above 100 should be considered 100.
     *
     * @return Returns the signal strength of the currently tuned channel. You could just return 100 for a channel
     * producing a stream and 0 for a channel isn't producing data.
     */
    public int getSignalStrength();

    /**
     * Returns the CCI protection flag value for the currently tuned channel.
     * <p/>
     * If the device cannot be copy protected return NONE. Maybe we can playback the channel on another device in
     * this situation and record the analog output.
     *
     * @return The current level of copy protection on the currently tuned channel.
     */
    public CopyProtection getCopyProtection();

}
