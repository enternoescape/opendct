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
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVUnloadedDevice;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class JsonCaptureDeviceSet {
    private final CaptureDevice captureDevice;

    private String encoderParentName;
    private String encoderName;
    private int merit;
    private String encoderPoolName;
    private boolean alwaysForceExternalUnlock;
    private String channelLineup;
    private boolean offlineScanEnabled;
    private String localAddress;
    private String producer;
    private String consumer;
    private String offlineConsumer;

    public JsonCaptureDeviceSet(CaptureDevice captureDevice) {
        this.captureDevice = captureDevice;
    }

    public void setEncoderParentName(String encoderParentName) throws JsonGetException {
        this.encoderParentName = encoderParentName;

        if (captureDevice.getEncoderParentName().equals(encoderParentName)) {
            return;
        }

        if (captureDevice.isLocked()) {
            throw new JsonGetException("Unable to change the parent name of this device because" +
                    " it is currently in use.");
        }

        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();

        Config.setString("sagetv.device." + captureDevice.getEncoderParentUniqueHash() + ".device_name", encoderParentName);

        for (CaptureDevice captureDeviceMod : captureDevices) {
            if (captureDeviceMod.getEncoderParentName().equals(captureDevice.getEncoderParentName())) {
                SageTVUnloadedDevice unloadedDevice = SageTVManager.removeCaptureDevice(captureDeviceMod.getEncoderName());

                try {
                    SageTVManager.addCaptureDevice(unloadedDevice.ENCODER_NAME);
                } catch (SocketException e) {
                    throw new JsonGetException(e);
                }
            }
        }
    }

    public void setEncoderName(String encoderName) throws JsonGetException {
        this.encoderName = encoderName;

        if (captureDevice.getEncoderName().equals(encoderName)) {
            return;
        }

        if (captureDevice.isLocked()) {
            throw new JsonGetException("Unable to change the name of this device because it is" +
                    " currently in use.");
        }

        SageTVUnloadedDevice unloadedDevice = SageTVManager.removeCaptureDevice(captureDevice.getEncoderName());

        Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".device_name", encoderName);

        try {
            SageTVManager.addCaptureDevice(unloadedDevice.ENCODER_NAME);
        } catch (SocketException e) {
            throw new JsonGetException(e);
        }
    }

    public void setMerit(int merit) {
        this.merit = merit;

        if (!(captureDevice.getMerit() == merit)) {
            captureDevice.setMerit(merit);
        }
    }

    public void setEncoderPoolName(String encoderPoolName) {
        this.encoderPoolName = encoderPoolName;

        if (!captureDevice.getEncoderPoolName().equals(encoderPoolName)) {
            captureDevice.setEncoderPoolName(encoderPoolName);
        }
    }

    public void setAlwaysForceExternalUnlock(boolean alwaysForceExternalUnlock) {
        this.alwaysForceExternalUnlock = alwaysForceExternalUnlock;

        captureDevice.setAlwaysForceExternalUnlock(alwaysForceExternalUnlock);
    }

    public void setChannelLineup(String channelLineup) {
        this.channelLineup = channelLineup;

        if (!captureDevice.getChannelLineup().equals(channelLineup)) {
            captureDevice.setChannelLineup(channelLineup);
        }
    }

    public void setOfflineScanEnabled(boolean offlineScanEnabled) {
        this.offlineScanEnabled = offlineScanEnabled;

        if (!(captureDevice.isOfflineScanEnabled() == offlineScanEnabled)) {
            captureDevice.setOfflineScan(offlineScanEnabled);
        }
    }

    public void setLocalAddress(String localAddress) throws UnknownHostException {
        this.localAddress = localAddress;

        if (localAddress != null && !localAddress.equals("")) {
            captureDevice.setLocalAddress(InetAddress.getByName(localAddress));
        }
    }

    public void setProducer(String producer) {
        this.producer = producer;

        if (captureDevice instanceof RTPCaptureDevice) {
            Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".rtp.producer", producer);
        } else if (captureDevice instanceof HTTPCaptureDevice) {
            Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".http.producer", producer);
        } else {
            // If this happens, we have a new capture device that needs to be added to this list.
            Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".producer", producer);
        }
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;

        Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".consumer", consumer);
    }

    public void setOfflineConsumer(String offlineConsumer) {
        this.offlineConsumer = offlineConsumer;

        Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".channel_scan_consumer", offlineConsumer);
    }
}
