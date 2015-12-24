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

package opendct.jetty;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceType;
import opendct.channel.BroadcastStandard;
import opendct.channel.CopyProtection;
import opendct.channel.TVChannel;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;

import java.net.InetAddress;

/**
 * Implements
 */
public class JsonCaptureDevice implements CaptureDevice{

    @Override
    public CaptureDevice[] getChildCaptureDevices() {
        return new CaptureDevice[0];
    }

    @Override
    public CaptureDeviceType getEncoderDeviceType() {
        return null;
    }

    @Override
    public String getEncoderParentName() {
        return null;
    }

    @Override
    public int getEncoderParentUniqueHash() {
        return 0;
    }

    @Override
    public String getEncoderName() {
        return null;
    }

    @Override
    public int getEncoderUniqueHash() {
        return 0;
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
    public int getMerit() {
        return 0;
    }

    @Override
    public void setMerit(int merit) {

    }

    @Override
    public String getEncoderPoolName() {
        return null;
    }

    @Override
    public void setEncoderPoolName(String poolName) {

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
    public void setAlwaysForceExternalUnlock(boolean forceLock) {

    }

    @Override
    public boolean setAlwaysForceExternalUnlock() {
        return false;
    }

    @Override
    public boolean getChannelInfoOffline(TVChannel tvChannel) {
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
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize) {
        return false;
    }

    @Override
    public boolean canEncodeFilename() {
        return false;
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    @Override
    public boolean canEncodeUploadID() {
        return false;
    }

    @Override
    public boolean switchEncoding(String channel, String filename, long bufferSize) {
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
    public void tuneToChannel(String channel) {

    }

    @Override
    public boolean autoTuneChannel(String channel) {
        return false;
    }

    @Override
    public boolean autoScanChannel(String channel) {
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
    public void setOfflineScan(boolean enabled) {

    }

    @Override
    public boolean isOfflineScanEnabled() {
        return false;
    }

    @Override
    public boolean isNetworkDevice() {
        return false;
    }

    @Override
    public InetAddress getRemoteAddress() {
        return null;
    }

    @Override
    public InetAddress getLocalAddress() {
        return null;
    }

    @Override
    public void setLocalAddress(InetAddress localAddress) {

    }

    @Override
    public int compareTo(CaptureDevice o) {
        return 0;
    }

    @Override
    public DeviceOption[] getOptions() {
        return new DeviceOption[0];
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {

    }
}
