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

import opendct.channel.CopyProtection;
import opendct.channel.TVChannel;
import opendct.config.options.DeviceOptionException;
import opendct.sagetv.SageTVDeviceCrossbar;

import java.io.File;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class DummyCaptureDevice implements CaptureDevice {

    private final String parentName;
    private final String encoderName;
    private final String encoderVersion;
    private String lineup;
    private int merit;
    private String poolName;
    private boolean offlineChannelScan;

    public DummyCaptureDevice(String parentName, String encoderName, String encoderVersion, String lineup, String tunerPool) {
        this.parentName = parentName;
        this.encoderName = encoderName;
        this.encoderVersion = encoderVersion;
        this.lineup = lineup;
        this.poolName = tunerPool;
        this.offlineChannelScan = false;
        merit = 0;
    }

    @Override
    public CaptureDevice[] getChildCaptureDevices() {
        return new CaptureDevice[0];
    }

    @Override
    public CaptureDeviceType getEncoderDeviceType() {
        return CaptureDeviceType.UNKNOWN;
    }

    @Override
    public SageTVDeviceCrossbar[] getSageTVDeviceCrossbars() {
        return new SageTVDeviceCrossbar[] { SageTVDeviceCrossbar.DIGITAL_TV_TUNER };
    }

    @Override
    public String getEncoderParentName() {
        return parentName;
    }

    @Override
    public int getEncoderParentUniqueHash() {
        return parentName.hashCode();
    }

    @Override
    public String getEncoderName() {
        return encoderName;
    }

    @Override
    public int getEncoderUniqueHash() {
        return encoderName.hashCode();
    }

    private AtomicBoolean locked = new AtomicBoolean(false);

    @Override
    public boolean isInternalLocked() {
        return locked.get();
    }

    @Override
    public boolean setLocked(boolean locked) {
        if (this.locked.getAndSet(locked) == locked) {
            return false;
        }

        return true;
    }

    @Override
    public int getPoolMerit() {
        return merit;
    }

    @Override
    public void setPoolMerit(int merit) {
        this.merit = merit;
    }

    @Override
    public boolean isOfflineChannelScan() {
        return offlineChannelScan;
    }

    @Override
    public void setOfflineChannelScan(boolean offlineChannelScan) {
        this.offlineChannelScan = offlineChannelScan;
    }

    @Override
    public String getPoolName() {
        return poolName;
    }

    @Override
    public void setPoolName(String poolName) {
        this.poolName = poolName;
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
    public void stopEncoding() {

    }

    @Override
    public String getLastChannel() {
        return "-1";
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, SageTVDeviceCrossbar deviceType, int crossbarIndex, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    @Override
    public boolean switchEncoding(String channel, String filename, long bufferSize, SageTVDeviceCrossbar deviceType, int crossbarIndex, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    @Override
    public boolean canSwitch() {
        return false;
    }

    @Override
    public long getRecordStart() {
        return System.currentTimeMillis();
    }

    @Override
    public long getRecordedBytes() {
        return 0;
    }

    @Override
    public String scanChannelInfo(String channel) {
        return channel;
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
        return lineup;
    }

    @Override
    public void setChannelLineup(String lineup) {
        this.lineup = lineup;
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
    public int getSignalStrength() {
        return 100;
    }

    @Override
    public CopyProtection getCopyProtection() {
        return CopyProtection.UNKNOWN;
    }

    @Override
    public void streamError(File sourceFile, SocketAddress address, int uploadId) {

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
