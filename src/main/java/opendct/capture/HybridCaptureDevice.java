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

import opendct.channel.BroadcastStandard;
import opendct.channel.CopyProtection;
import opendct.channel.TVChannel;
import opendct.config.options.DeviceOptionException;
import opendct.sagetv.SageTVDeviceCrossbar;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVPoolManager;
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.hybrid.HybridDiscoveredDevice;
import opendct.tuning.hybrid.HybridDiscoveredDeviceParent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class HybridCaptureDevice implements CaptureDevice {
    private static final Logger logger = LogManager.getLogger(HybridCaptureDevice.class);

    private CaptureDevice primaryCaptureDevice;
    private CaptureDevice secondaryCaptureDevice;
    private CaptureDevice poolCaptureDevice;

    private final HybridDiscoveredDevice device;
    private final HybridDiscoveredDeviceParent parent;

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();

    public HybridCaptureDevice(HybridDiscoveredDevice device, HybridDiscoveredDeviceParent parent) throws CaptureDeviceLoadException {
        this.device = device;
        this.parent = parent;

        primaryCaptureDevice = SageTVManager.getSageTVCaptureDevice(device.getPrimaryDevice(), false);
        if (primaryCaptureDevice == this || device.getFriendlyName().equals(device.getPrimaryDevice())) {
            throw new CaptureDeviceLoadException("The primary capture device cannot be this device.");
        }

        secondaryCaptureDevice = SageTVManager.getSageTVCaptureDevice(device.getSecondaryDevice(), false);
        if (secondaryCaptureDevice == this || device.getFriendlyName().equals(device.getSecondaryDevice())) {
            throw new CaptureDeviceLoadException("The secondary capture device cannot be this device.");
        }
    }

    private CaptureDevice getPrimaryCaptureDevice() {
        if (primaryCaptureDevice == null) {
            primaryCaptureDevice = SageTVManager.getSageTVCaptureDevice(device.getPrimaryDevice(), true);
        }

        return primaryCaptureDevice;
    }

    private CaptureDevice getSecondaryCaptureDevice() {
        if (secondaryCaptureDevice == null) {
            secondaryCaptureDevice = SageTVManager.getSageTVCaptureDevice(device.getSecondaryDevice(), true);
        }

        if (secondaryCaptureDevice != null && device.getSecondaryPooling()) {
            String poolDevice = SageTVPoolManager.getAndLockBestCaptureDevice(secondaryCaptureDevice.getEncoderName());

            if (poolDevice != null) {
                poolCaptureDevice = SageTVManager.getSageTVCaptureDevice(poolDevice, false);

                if (poolCaptureDevice != null) {
                    return poolCaptureDevice;
                }

                logger.warn("The pool device '{}' does not exist. Using the reference capture device.", poolDevice);
            }

            logger.warn("Unable to get a pool device for '{}', using the reference capture device.", secondaryCaptureDevice.getEncoderName());
        }

        return secondaryCaptureDevice;
    }

    private void removeSecondaryCaptureDevice() {
        if (poolCaptureDevice != null && secondaryCaptureDevice != null) {
            SageTVPoolManager.removeCaptureDeviceMapping(poolCaptureDevice.getEncoderName());
        }
    }

    @Override
    public CaptureDevice[] getChildCaptureDevices() {
        if (primaryCaptureDevice == null && secondaryCaptureDevice == null) {
            return new CaptureDevice[0];
        } else if (primaryCaptureDevice == null) {
            return new CaptureDevice[] { secondaryCaptureDevice };
        } else if (secondaryCaptureDevice == null) {
            return new CaptureDevice[] { primaryCaptureDevice };
        }

        if (poolCaptureDevice != null) {
            return new CaptureDevice[] { primaryCaptureDevice, poolCaptureDevice };
        }

        return new CaptureDevice[] { primaryCaptureDevice, secondaryCaptureDevice };
    }

    @Override
    public CaptureDeviceType getEncoderDeviceType() {
        return CaptureDeviceType.HYBRID;
    }

    @Override
    public SageTVDeviceCrossbar[] getSageTVDeviceCrossbars() {
        return new SageTVDeviceCrossbar[] { SageTVDeviceCrossbar.DIGITAL_TV_TUNER };
    }

    @Override
    public String getEncoderParentName() {
        return parent.getFriendlyName();
    }

    @Override
    public int getEncoderParentUniqueHash() {
        return parent.getParentId();
    }

    @Override
    public String getEncoderName() {
        return device.getFriendlyName();
    }

    @Override
    public int getEncoderUniqueHash() {
        return device.getId();
    }

    public boolean isInternalLocked() {
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
    public int getPoolMerit() {
        return 0;
    }

    @Override
    public void setPoolMerit(int merit) {

    }

    @Override
    public boolean isOfflineChannelScan() {
        return false;
    }

    @Override
    public void setOfflineChannelScan(boolean offlineChannelScan) {

    }

    @Override
    public String getPoolName() {
        return null;
    }

    @Override
    public void setPoolName(String poolName) {

    }

    @Override
    public boolean isExternalLocked() {
        return false;
    }

    @Override
    public boolean setExternalLock(boolean locked) {
        return false;
    }

    @Override
    public boolean getChannelInfoOffline(TVChannel tvChannel, boolean skipCCI) {
        return false;
    }

    @Override
    public void stopDevice() {

    }

    @Override
    public void stopEncoding() {

    }

    @Override
    public String getLastChannel() {
        return null;
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, SageTVDeviceCrossbar deviceType, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    @Override
    public boolean switchEncoding(String channel, String filename, long bufferSize, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    @Override
    public boolean canSwitch() {
        return false;
    }

    @Override
    public long getRecordStart() {
        return 0;
    }

    @Override
    public long getRecordedBytes() {
        return 0;
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
    public long getProducedPackets() {
        return 0;
    }

    @Override
    public String getChannelLineup() {
        return null;
    }

    @Override
    public void setChannelLineup(String lineup) {

    }

    @Override
    public String getRecordFilename() {
        return null;
    }

    @Override
    public int getRecordUploadID() {
        return 0;
    }

    @Override
    public String getRecordQuality() {
        return null;
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
    public void streamError(File sourceFile) {

    }

    @Override
    public String getConsumerName() {
        return null;
    }

    @Override
    public void setConsumerName(String consumerName) throws DeviceOptionException {

    }

    @Override
    public String getTranscodeProfile() {
        return null;
    }

    @Override
    public void setTranscodeProfile(String transcodeProfile) throws DeviceOptionException {

    }
}
