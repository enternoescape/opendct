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

import java.net.InetAddress;

public class DummyCaptureDevice implements CaptureDevice {

    public final String parentName;
    public final String encoderName;
    public final String encoderVersion;
    public String lineup;
    public int merit;

    public DummyCaptureDevice(String parentName, String encoderName, String encoderVersion, String lineup) {
        this.parentName = parentName;
        this.encoderName = encoderName;
        this.encoderVersion = encoderVersion;
        this.lineup = lineup;
        merit = 0;
    }

    public CaptureDevice[] getChildCaptureDevices() {
        return new CaptureDevice[0];
    }

    public CaptureDeviceType getEncoderDeviceType() {
        return CaptureDeviceType.UNKNOWN;
    }

    public String getEncoderParentName() {
        return parentName;
    }

    public int getEncoderParentUniqueHash() {
        return parentName.hashCode();
    }

    public String getEncoderName() {
        return encoderName;
    }

    public int getEncoderUniqueHash() {
        return encoderName.hashCode();
    }

    public String getEncoderVersion() {
        return encoderVersion;
    }

    private volatile boolean locked = false;

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public int getMerit() {
        return merit;
    }

    public void setMerit(int merit) {
        this.merit = merit;
    }

    public boolean isExternalLocked() {
        return false;
    }

    public boolean setExternalLock(boolean locked) {
        return true;
    }

    public boolean getChannelInfoOffline(TVChannel tvChannel) {
        return false;
    }

    public void stopDevice() {

    }

    public void stopEncoding() {

    }

    public String getLastChannel() {
        return "-1";
    }

    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize) {
        return false;
    }


    public boolean canEncodeFilename() {
        return false;
    }

    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    public boolean canEncodeUploadID() {
        return false;
    }

    public boolean switchEncoding(String channel, String filename, long bufferSize) {
        return false;
    }

    public boolean switchEncoding(String channel, String filename, long bufferSize, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    public boolean canSwitch() {
        return false;
    }

    public long getRecordStart() {
        return System.currentTimeMillis();
    }

    public long getRecordedBytes() {
        return 0;
    }

    public void tuneToChannel(String channel) {

    }

    public boolean autoTuneChannel(String channel) {
        return false;
    }

    public boolean autoScanChannel(String channel) {
        return false;
    }

    public String scanChannelInfo(String channel) {
        return channel;
    }

    public boolean isReady() {
        return false;
    }

    public String getChannelLineup() {
        return lineup;
    }

    public void setChannelLineup(String lineup) {
        this.lineup = lineup;
    }

    public String getRecordFilename() {
        return null;
    }

    public int getRecordUploadID() {
        return 0;
    }

    public String getRecordQuality() {
        return null;
    }

    public BroadcastStandard getBroadcastStandard() {
        return BroadcastStandard.UNKNOWN;
    }

    public int getSignalStrength() {
        return 100;
    }

    public CopyProtection getCopyProtection() {
        return CopyProtection.UNKNOWN;
    }
}
