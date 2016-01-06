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

package opendct.jetty.json;

import opendct.capture.CaptureDevice;
import opendct.capture.HTTPCaptureDevice;
import opendct.capture.RTPCaptureDevice;
import opendct.config.Config;
import opendct.jetty.JettyManager;
import opendct.sagetv.SageTVManager;
import opendct.util.Util;

import javax.ws.rs.core.Response;

public class JsonCaptureDeviceDetails {
    private String[] childCaptureDevices;
    private String encoderDeviceType;
    private String encoderParentName;
    private String encoderName;
    private boolean locked;
    private int merit;
    private String encoderPoolName;
    private boolean alwaysForceExternalUnlock;
    private String lastChannel;
    private boolean canEncodeFilename;
    private boolean canEncodeUploadID;
    private boolean canSwitch;
    private long recordStart;
    private long recordedBytes;
    private String channelLineup;
    private String recordFilename;
    private String recordQuality;
    private boolean offlineScanEnabled;
    private boolean networkDevice;
    private String remoteAddress;
    private String localAddress;
    private String producer;
    private String producerBaseImpl;
    private String consumer;
    private String offlineConsumer;
    private int encoderPort;

    /**
     * Sets the details of the object to reflect the current details of this capture device.
     *
     * @param captureDeviceName The name of the capture device to get details.
     * @return <i>true</i> if the capture device was found and everything is populated.
     */
    public Response get(String captureDeviceName) {
        CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(captureDeviceName, false);

        if (captureDevice == null) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.NOT_FOUND,
                            "The capture device requested does not exist.",
                            captureDeviceName,
                            ""
                    )
            ).build();
        }

        try {
            // We are only returning the String values since the referenced devices might be sent twice.
            CaptureDevice captureDevices[] = captureDevice.getChildCaptureDevices();
            String captureDeviceNames[] = new String[captureDevices.length];

            for (int i = 0; i < captureDeviceNames.length; i++) {
                captureDeviceNames[i] = captureDevices[i].getEncoderName();
            }

            childCaptureDevices = captureDeviceNames;

            encoderDeviceType = captureDevice.getEncoderDeviceType().toString();
            encoderParentName = captureDevice.getEncoderParentName();
            encoderName = captureDevice.getEncoderName();
            locked = captureDevice.isLocked();
            merit = captureDevice.getMerit();
            encoderPoolName = captureDevice.getEncoderPoolName();
            alwaysForceExternalUnlock = Config.getBoolean("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".always_force_external_unlock", false);
            lastChannel = captureDevice.getLastChannel();
            canEncodeFilename = captureDevice.canEncodeFilename();
            canEncodeUploadID = captureDevice.canEncodeUploadID();
            canSwitch = captureDevice.canSwitch();
            recordStart = captureDevice.getRecordStart();
            recordedBytes = captureDevice.getRecordedBytes();
            channelLineup = captureDevice.getChannelLineup();
            recordFilename = captureDevice.getRecordFilename();
            recordQuality = captureDevice.getRecordQuality();
            offlineScanEnabled = captureDevice.isOfflineScanEnabled();
            networkDevice = captureDevice.isNetworkDevice();
            remoteAddress = captureDevice.getRemoteAddress().getHostAddress();
            localAddress = captureDevice.getLocalAddress().getHostAddress();
            consumer = Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".consumer");
            offlineConsumer = Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".channel_scan_consumer");

            String encoderPortString = Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".encoder_listen_port");
            if (!Util.isNullOrEmpty(encoderPortString)) {
                try {
                    encoderPort = Integer.valueOf(encoderPortString);
                } catch (NumberFormatException e) {
                    encoderPort = -1;
                }
            } else {
                encoderPort = -1;
            }

            if (captureDevice instanceof RTPCaptureDevice) {
                producerBaseImpl = RTPCaptureDevice.class.getName();
                producer = Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".rtp.producer");
            } else if (captureDevice instanceof HTTPCaptureDevice) {
                producerBaseImpl = HTTPCaptureDevice.class.getName();
                producer = Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".http.producer");
            } else {
                // If this happens, we have a new capture device that needs to be added to this list.
                producerBaseImpl = "Unknown";
            }
        } catch (Exception e) {
            return Response.status(JettyManager.NOT_FOUND).entity(
                    new JsonError(
                            JettyManager.ERROR,
                            "An unexpected error happened while getting details about this capture device.",
                            captureDeviceName,
                            e.toString()
                    )
            ).build();
        }

        return Response.status(JettyManager.OK).entity(this).build();
    }

    public String[] getChildCaptureDevices() {
        return childCaptureDevices;
    }

    public String getEncoderDeviceType() {
        return encoderDeviceType;
    }

    public String getEncoderParentName() {
        return encoderParentName;
    }

    public String getEncoderName() {
        return encoderName;
    }

    public boolean isLocked() {
        return locked;
    }

    public int getMerit() {
        return merit;
    }

    public String getEncoderPoolName() {
        return encoderPoolName;
    }

    public boolean isAlwaysForceExternalUnlock() {
        return alwaysForceExternalUnlock;
    }

    public String getLastChannel() {
        return lastChannel;
    }

    public boolean isCanEncodeFilename() {
        return canEncodeFilename;
    }

    public boolean isCanEncodeUploadID() {
        return canEncodeUploadID;
    }

    public boolean isCanSwitch() {
        return canSwitch;
    }

    public long getRecordStart() {
        return recordStart;
    }

    public long getRecordedBytes() {
        return recordedBytes;
    }

    public String getChannelLineup() {
        return channelLineup;
    }

    public String getRecordFilename() {
        return recordFilename;
    }

    public String getRecordQuality() {
        return recordQuality;
    }

    public boolean isOfflineScanEnabled() {
        return offlineScanEnabled;
    }

    public boolean isNetworkDevice() {
        return networkDevice;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public String getProducer() {
        return producer;
    }

    public String getProducerBaseImpl() {
        return producerBaseImpl;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getOfflineConsumer() {
        return offlineConsumer;
    }

    public int getEncoderPort() {
        return encoderPort;
    }
}
