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

package opendct.nanohttpd.serializer;

import com.google.gson.*;
import opendct.capture.CaptureDevice;
import opendct.channel.ChannelLineup;
import opendct.channel.ChannelManager;
import opendct.config.options.DeviceOption;
import opendct.nanohttpd.pojo.JsonException;
import opendct.nanohttpd.pojo.JsonOption;
import opendct.sagetv.SageTVDeviceCrossbar;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVPoolManager;
import opendct.tuning.discovery.DiscoveredDevice;
import opendct.tuning.discovery.DiscoveryManager;

import java.lang.reflect.Type;

public class CaptureDevicesSerializer implements JsonSerializer<DiscoveredDevice[]> {
    public static final String ID = "id";
    public static final String ENABLED = "enabled";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String LOADED = "loaded";
    public static final String POOL_NAME = "poolName";
    public static final String POOL_MERIT = "poolMerit";
    public static final String LINEUP_NAME = "lineupName";
    public static final String LAST_CHANNEL = "lastChannel";
    public static final String RECORDING_START = "recordingStart";
    public static final String PRODUCED_PACKETS = "producedPackets";
    public static final String RECORDED_BYTES = "recordedBytes";
    public static final String RECORDING_FILENAME = "recordingFilename";
    public static final String RECORDING_QUALITY = "recordingQuality";
    public static final String INTERNAL_LOCKED = "internalLocked";
    public static final String EXTERNAL_LOCKED = "externalLocked";
    public static final String OFFLINE_CHANNEL_SCAN_ENABLED = "offlineChannelScanEnabled";
    public static final String UPLOAD_ID = "uploadId";
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String BROADCAST_STANDARD = "broadcastStandard";
    public static final String COPY_PROTECTION = "copyProtection";
    public static final String CONSUMER = "consumer";
    public static final String TRANSCODE_PROFILE = "transcodeProfile";
    public static final String DEVICE_TYPE = "deviceType";
    public static final String SAGETV_DEVICE_CROSSBAR = "sagetvCrossbars";
    public static final String OPTIONS = "options";


    private final static DeviceOptionSerializer deviceOptionSerializer = new DeviceOptionSerializer();
    private final static SageTVDeviceTypesSerializer deviceTypesSerializer = new SageTVDeviceTypesSerializer();

    @Override
    public JsonElement serialize(DiscoveredDevice[] src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray jsonObject = new JsonArray();

        for (DiscoveredDevice device : src) {

            JsonObject object = new JsonObject();
            object.addProperty(ID, device.getId());
            object.addProperty(ENABLED, DiscoveryManager.isDevicePermitted(device.getId()));
            object.addProperty(NAME, device.getFriendlyName());
            object.addProperty(DESCRIPTION, device.getDescription());

            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(device.getId());

            DeviceOption options[] = device.getOptions();

            if (captureDevice != null) {
                object.addProperty(LOADED, true);
                if (SageTVPoolManager.isUsePools()) {
                    object.addProperty(POOL_NAME, captureDevice.getPoolName());
                    object.addProperty(POOL_MERIT, captureDevice.getPoolMerit());
                }
                object.addProperty(LINEUP_NAME, captureDevice.getChannelLineup());
                object.addProperty(LAST_CHANNEL, captureDevice.getLastChannel());
                object.addProperty(RECORDING_START, captureDevice.getRecordStart());
                object.addProperty(PRODUCED_PACKETS, captureDevice.getProducedPackets());
                object.addProperty(RECORDED_BYTES, captureDevice.getRecordedBytes());
                String recordingFilename = captureDevice.getRecordFilename();
                object.addProperty(RECORDING_FILENAME, recordingFilename != null ? recordingFilename : "");
                object.addProperty(RECORDING_QUALITY, captureDevice.getRecordQuality());
                object.addProperty(INTERNAL_LOCKED, captureDevice.isInternalLocked());
                object.addProperty(EXTERNAL_LOCKED, captureDevice.isExternalLocked());
                object.addProperty(OFFLINE_CHANNEL_SCAN_ENABLED, captureDevice.isOfflineChannelScan());
                object.addProperty(UPLOAD_ID, captureDevice.getRecordUploadID());
                object.addProperty(SIGNAL_STRENGTH, captureDevice.getSignalStrength());
                object.addProperty(BROADCAST_STANDARD, captureDevice.getBroadcastStandard().toString());
                object.addProperty(COPY_PROTECTION, captureDevice.getCopyProtection().toString());
                object.addProperty(CONSUMER, captureDevice.getConsumerName());
                object.addProperty(TRANSCODE_PROFILE, captureDevice.getTranscodeProfile());
                object.addProperty(DEVICE_TYPE, captureDevice.getEncoderDeviceType().toString());
                object.add(SAGETV_DEVICE_CROSSBAR, deviceTypesSerializer.serialize(captureDevice.getSageTVDeviceCrossbars(), SageTVDeviceCrossbar.class, context));
            } else {
                object.addProperty(LOADED, false);
            }

            object.add(OPTIONS, deviceOptionSerializer.serialize(options, DeviceOption.class, context));

            jsonObject.add(object);
        }

        return jsonObject;
    }

    public static void addProperty(JsonObject object, String property, DiscoveredDevice device, CaptureDevice captureDevice) {
        switch (property) {
            case NAME:
                object.addProperty(NAME, device.getFriendlyName());
                break;
            case ENABLED:
                object.addProperty(ENABLED, DiscoveryManager.isDevicePermitted(device.getId()));
                break;
            case DESCRIPTION:
                object.addProperty(DESCRIPTION, device.getDescription());
                break;
            case OPTIONS:
                object.add(OPTIONS, deviceOptionSerializer.serialize(device.getOptions(), DeviceOption.class, null));
                break;
            case LOADED:
                object.addProperty(LOADED, captureDevice != null);
                break;
            case POOL_NAME:
                object.addProperty(POOL_NAME, captureDevice.getPoolName());
                break;
            case POOL_MERIT:
                object.addProperty(POOL_MERIT, captureDevice.getPoolMerit());
                break;
            case LINEUP_NAME:
                object.addProperty(LINEUP_NAME, captureDevice.getChannelLineup());
                break;
            case LAST_CHANNEL:
                object.addProperty(LAST_CHANNEL, captureDevice.getLastChannel());
                break;
            case RECORDING_START:
                object.addProperty(RECORDING_START, captureDevice.getRecordStart());
                break;
            case PRODUCED_PACKETS:
                object.addProperty(PRODUCED_PACKETS, captureDevice.getProducedPackets());
                break;
            case RECORDED_BYTES:
                object.addProperty(RECORDED_BYTES, captureDevice.getRecordedBytes());
                break;
            case RECORDING_FILENAME:
                object.addProperty(RECORDING_FILENAME, captureDevice.getRecordFilename());
                break;
            case RECORDING_QUALITY:
                object.addProperty(RECORDING_QUALITY, captureDevice.getRecordQuality());
                break;
            case INTERNAL_LOCKED:
                object.addProperty(INTERNAL_LOCKED, captureDevice.isInternalLocked());
                break;
            case EXTERNAL_LOCKED:
                object.addProperty(EXTERNAL_LOCKED, captureDevice.isExternalLocked());
                break;
            case OFFLINE_CHANNEL_SCAN_ENABLED:
                object.addProperty(OFFLINE_CHANNEL_SCAN_ENABLED, captureDevice.isOfflineChannelScan());
                break;
            case UPLOAD_ID:
                object.addProperty(UPLOAD_ID, captureDevice.getRecordUploadID());
                break;
            case SIGNAL_STRENGTH:
                object.addProperty(SIGNAL_STRENGTH, captureDevice.getSignalStrength());
                break;
            case BROADCAST_STANDARD:
                object.addProperty(BROADCAST_STANDARD, captureDevice.getBroadcastStandard().toString());
                break;
            case COPY_PROTECTION:
                object.addProperty(COPY_PROTECTION, captureDevice.getCopyProtection().toString());
                break;
            case DEVICE_TYPE:
                object.addProperty(DEVICE_TYPE, captureDevice.getEncoderDeviceType().toString());
                break;
            case SAGETV_DEVICE_CROSSBAR:
                object.add(SAGETV_DEVICE_CROSSBAR, deviceTypesSerializer.serialize(captureDevice.getSageTVDeviceCrossbars(), SageTVDeviceCrossbar.class, null));
                break;
        }
    }

    public static JsonException setProperty(JsonOption jsonOption, CaptureDevice captureDevice) {
        String newValue = jsonOption.getValue();

        switch (jsonOption.getProperty()) {
            case POOL_NAME:
                if (!captureDevice.getPoolName().equals(newValue)) {
                    captureDevice.setPoolName(newValue);
                    SageTVPoolManager.addPoolCaptureDevice(newValue, captureDevice.toString());
                    SageTVPoolManager.resortMerits(newValue);
                }

                break;
            case POOL_MERIT:
                int newMerit;
                try {
                    newMerit = Integer.parseInt(newValue);
                } catch (NumberFormatException e) {
                    return new JsonException(POOL_MERIT, "'" + newValue + "' is not an integer.");
                }

                if (captureDevice.getPoolMerit() != newMerit) {
                    captureDevice.setPoolMerit(newMerit);
                    SageTVPoolManager.resortMerits(captureDevice.getPoolName());
                }

                break;
            case LINEUP_NAME:
                ChannelLineup lineup = ChannelManager.getChannelLineup(newValue);
                if (lineup == null) {
                    return new JsonException(LINEUP_NAME, "The '" + newValue + "' lineup does not exist.");
                }

                if (captureDevice.isOfflineChannelScan()) {
                    ChannelManager.removeDeviceFromOfflineScan(captureDevice.getChannelLineup(), captureDevice.getEncoderName());
                    ChannelManager.addDeviceToOfflineScan(newValue, captureDevice.getEncoderName());
                }

                captureDevice.setChannelLineup(newValue);

                break;
            case OFFLINE_CHANNEL_SCAN_ENABLED:
                if (!newValue.toLowerCase().equals("false") && !newValue.toLowerCase().equals("true")) {
                    return new JsonException(OFFLINE_CHANNEL_SCAN_ENABLED, "'" + newValue + "' is not boolean.");
                }

                boolean channelScan = newValue.toLowerCase().equals("true");

                if (channelScan) {
                    ChannelManager.addDeviceToOfflineScan(captureDevice.getChannelLineup(), captureDevice.getEncoderName());
                } else {
                    ChannelManager.removeDeviceFromOfflineScan(captureDevice.getChannelLineup(), captureDevice.getEncoderName());
                }

                break;
        }

        return null;
    }
}
