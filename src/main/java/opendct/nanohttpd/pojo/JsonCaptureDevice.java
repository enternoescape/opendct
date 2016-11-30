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

package opendct.nanohttpd.pojo;

public class JsonCaptureDevice {
    private int id;
    private boolean enabled;
    private String name;
    private String description;
    private boolean loaded;
    private String poolName;
    private int poolMerit;
    private String lineupName;
    private String lastChannel;
    private long recordingStart;
    private long producedPackets;
    private long recordedBytes;
    private String recordingFilename;
    private String recordingQuality;
    private boolean internalLocked;
    private boolean externalLocked;
    private boolean offlineChannelScanEnabled;
    private int uploadId;
    private int signalStrength;
    private String broadcastStandard;
    private String copyProtection;
    private String deviceType;
    private String consumer;
    private String consumerCanonical;
    private String transcodeProfile;
    private JsonSageTVCrossbar[] sagetvCrossbars;
    private JsonOption[] options;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public int getPoolMerit() {
        return poolMerit;
    }

    public void setPoolMerit(int poolMerit) {
        this.poolMerit = poolMerit;
    }

    public String getLineupName() {
        return lineupName;
    }

    public void setLineupName(String lineupName) {
        this.lineupName = lineupName;
    }

    public String getLastChannel() {
        return lastChannel;
    }

    public void setLastChannel(String lastChannel) {
        this.lastChannel = lastChannel;
    }

    public long getRecordingStart() {
        return recordingStart;
    }

    public void setRecordingStart(long recordingStart) {
        this.recordingStart = recordingStart;
    }

    public long getProducedPackets() {
        return producedPackets;
    }

    public void setProducedPackets(long producedPackets) {
        this.producedPackets = producedPackets;
    }

    public long getRecordedBytes() {
        return recordedBytes;
    }

    public void setRecordedBytes(long recordedBytes) {
        this.recordedBytes = recordedBytes;
    }

    public String getRecordingFilename() {
        return recordingFilename;
    }

    public void setRecordingFilename(String recordingFilename) {
        this.recordingFilename = recordingFilename;
    }

    public String getRecordingQuality() {
        return recordingQuality;
    }

    public void setRecordingQuality(String recordingQuality) {
        this.recordingQuality = recordingQuality;
    }

    public boolean isInternalLocked() {
        return internalLocked;
    }

    public void setInternalLocked(boolean internalLocked) {
        this.internalLocked = internalLocked;
    }

    public boolean isExternalLocked() {
        return externalLocked;
    }

    public void setExternalLocked(boolean externalLocked) {
        this.externalLocked = externalLocked;
    }

    public boolean isOfflineChannelScanEnabled() {
        return offlineChannelScanEnabled;
    }

    public void setOfflineChannelScanEnabled(boolean offlineChannelScanEnabled) {
        this.offlineChannelScanEnabled = offlineChannelScanEnabled;
    }

    public int getUploadId() {
        return uploadId;
    }

    public void setUploadId(int uploadId) {
        this.uploadId = uploadId;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(int signalStrength) {
        this.signalStrength = signalStrength;
    }

    public String getBroadcastStandard() {
        return broadcastStandard;
    }

    public void setBroadcastStandard(String broadcastStandard) {
        this.broadcastStandard = broadcastStandard;
    }

    public String getCopyProtection() {
        return copyProtection;
    }

    public void setCopyProtection(String copyProtection) {
        this.copyProtection = copyProtection;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public String getConsumerCanonical() {
        return consumerCanonical;
    }

    public void setConsumerCanonical(String consumerCanonical) {
        this.consumerCanonical = consumerCanonical;
    }

    public String getTranscodeProfile() {
        return transcodeProfile;
    }

    public void setTranscodeProfile(String transcodeProfile) {
        this.transcodeProfile = transcodeProfile;
    }

    public JsonSageTVCrossbar[] getSagetvCrossbars() {
        return sagetvCrossbars;
    }

    public void setSagetvCrossbars(JsonSageTVCrossbar[] sagetvCrossbars) {
        this.sagetvCrossbars = sagetvCrossbars;
    }

    public JsonOption[] getOptions() {
        return options;
    }

    public void setOptions(JsonOption[] options) {
        this.options = options;
    }
}
