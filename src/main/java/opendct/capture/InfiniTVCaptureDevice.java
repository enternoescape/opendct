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

import opendct.channel.BroadcastStandard;
import opendct.channel.CopyProtection;
import opendct.channel.TVChannel;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.http.InfiniTVStatus;
import opendct.tuning.upnp.InfiniTVDiscoveredDeviceParent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.model.meta.Device;

import java.io.IOException;
import java.net.InetAddress;

public class InfiniTVCaptureDevice extends BasicCaptureDevice {
    private final static Logger logger = LogManager.getLogger(InfiniTVCaptureDevice.class);

    private final Device upnpDevice;
    private final InfiniTVDiscoveredDeviceParent parent;

    private final int encoderNumber;


    public InfiniTVCaptureDevice(String deviceParentName,
                                 String deviceName,
                                 int encoderParentHash,
                                 int encoderHash,
                                 Device device,
                                 InfiniTVDiscoveredDeviceParent parent)
            throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {

        super(deviceParentName, deviceName, encoderParentHash, encoderHash);

        this.upnpDevice = device;
        this.parent = parent;

        try {
            logger.debug("Determining the encoder number...");
            encoderNumber = Integer.parseInt(deviceName.substring(deviceName.length() - 1));
        } catch (NumberFormatException e) {
            logger.error("Unable to parse the encoder number from '{}'", deviceName);
            throw new CaptureDeviceLoadException("Unable to parse the encoder number.");
        }

        try {
            InfiniTVStatus.getVar(parent.getRemoteAddress().getHostAddress(), encoderNumber, "diag", "Streaming_IP");
        } catch (IOException e) {
            logger.warn("HTTP tuning has been requested on this capture device, but it can't support it. Disabling feature...");
            if (encoderDeviceType == CaptureDeviceType.QAM_INFINITV) {
                logger.error("This device is configured for QAM and HTTP tuning is not available, you may not be able to use it.");
            }


        }
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public boolean setLocked(boolean locked) {
        return false;
    }

    @Override
    public boolean isExternalLocked() {
        return false;
    }

    @Override
    public boolean setExternalLock(boolean locked) {
        return true;
    }

    @Override
    public boolean getChannelInfoOffline(TVChannel tvChannel, boolean skipCCI) {
        return false;
    }

    @Override
    public void stopDevice() {

    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize) {
        return false;
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    @Override
    public String scanChannelInfo(String channel) {
        return null;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public BroadcastStandard getBroadcastStandard() {
        return null;
    }

    @Override
    public int getSignalStrength() {
        return 0;
    }

    @Override
    public CopyProtection getCopyProtection() {
        return null;
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {

    }
}
