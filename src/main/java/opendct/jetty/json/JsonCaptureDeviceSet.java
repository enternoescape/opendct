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
import opendct.sagetv.SageTVUnloadedDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.core.Response;
import java.net.SocketException;

public class JsonCaptureDeviceSet {
    private static final Logger logger = LogManager.getLogger(JsonCaptureDeviceSet.class);

    // This ensures that if more than one browser tries to apply updates at the same time, they
    // don't do it at the same time since it could cause a race condition.
    private static final Object updateLock = new Object();

    private String encoderName;
    private Integer merit;
    private String encoderPoolName;
    private Boolean alwaysForceExternalUnlock;
    private String channelLineup;
    private Boolean offlineScanEnabled;
    private String producer;
    private String consumer;
    private String offlineConsumer;
    private Integer encoderPort;

    public JsonCaptureDeviceSet() {
        encoderName = null;
        merit = null;
        encoderPoolName = null;
        alwaysForceExternalUnlock = null;
        channelLineup = null;
        offlineScanEnabled = null;
        producer = null;
        offlineConsumer = null;
        encoderPort = null;
    }

    public Response applyUpdates(CaptureDevice captureDevice) {

        synchronized (updateLock) {
            if (encoderName != null && !captureDevice.getEncoderName().equals(encoderName)) {
                if (captureDevice.isLocked()) {
                    JsonError jsonError = new JsonError(
                            JettyManager.ERROR,
                            "Unable to change the name of this device" +
                                    " because it is currently in use.",
                            captureDevice.getEncoderName(),
                            ""
                    );

                    logger.error("{}", jsonError);

                    return Response.status(JettyManager.ERROR).entity(jsonError).build();
                }

                SageTVUnloadedDevice unloadedDevice = SageTVManager.removeCaptureDevice(captureDevice.getEncoderName());

                Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".device_name", encoderName);

                try {
                    SageTVManager.addCaptureDevice(unloadedDevice.ENCODER_NAME);
                } catch (SocketException e) {
                    JsonError jsonError = new JsonError(
                            JettyManager.ERROR,
                            "An unexpected error happened while setting the name property for this capture device.",
                            captureDevice.getEncoderName(),
                            e.toString()
                    );

                    logger.error("{}", jsonError, e);

                    return Response.status(JettyManager.ERROR).entity(jsonError).build();
                }
            }


            if (merit != null && !(captureDevice.getMerit() == merit)) {
                captureDevice.setMerit(merit);
            }

            if (encoderPoolName != null && !captureDevice.getEncoderPoolName().equals(encoderPoolName)) {
                captureDevice.setEncoderPoolName(encoderPoolName);
            }

            if (alwaysForceExternalUnlock != null) {
                captureDevice.setAlwaysForceExternalUnlock(alwaysForceExternalUnlock);
            }

            if (channelLineup != null && !captureDevice.getChannelLineup().equals(channelLineup)) {
                captureDevice.setChannelLineup(channelLineup);
            }

            if (offlineScanEnabled != null && !(captureDevice.isOfflineScanEnabled() == offlineScanEnabled)) {
                captureDevice.setOfflineScan(offlineScanEnabled);
            }

            if (producer != null) {
                if (captureDevice instanceof RTPCaptureDevice) {
                    Config.getString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".rtp.producer", producer);
                } else if (captureDevice instanceof HTTPCaptureDevice) {
                    Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".http.producer", producer);
                } else {
                    // If this happens, we have a new capture device that needs to be added to this list.
                    Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".producer", producer);
                }
            }

            if (consumer != null) {
                Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".consumer", consumer);
            }

            if (offlineConsumer != null) {
                Config.setString("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".channel_scan_consumer", offlineConsumer);
            }

            if (encoderPort != null) {
                // The old port will be closed the next time the program is restarted if no
                // other devices are using it.
                SageTVManager.addAndStartSocketServers(new int[]{encoderPort});
                Config.setInteger("sagetv.device." + captureDevice.getEncoderUniqueHash() + ".encoder_listen_port", encoderPort);
            }

            try {
                Config.saveConfig();
            } catch (Exception e) {
                JsonError jsonError = new JsonError(
                        JettyManager.ERROR,
                        "An unexpected error happened while saving the new properties.",
                        captureDevice.getEncoderName(),
                        e.toString()
                );

                logger.error("{}", jsonError, e);

                return Response.status(JettyManager.ERROR).entity(jsonError).build();
            }

            return Response.status(JettyManager.OK).entity(captureDevice.getEncoderName()).build();
        }
    }

    public void setEncoderName(String encoderName) {
        this.encoderName = encoderName;
    }

    public void setMerit(int merit) {
        this.merit = merit;
    }

    public void setEncoderPoolName(String encoderPoolName) {
        this.encoderPoolName = encoderPoolName;
    }

    public void setAlwaysForceExternalUnlock(boolean alwaysForceExternalUnlock) {
        this.alwaysForceExternalUnlock = alwaysForceExternalUnlock;
    }

    public void setChannelLineup(String channelLineup) {
        this.channelLineup = channelLineup;
    }

    public void setOfflineScanEnabled(boolean offlineScanEnabled) {
        this.offlineScanEnabled = offlineScanEnabled;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public void setOfflineConsumer(String offlineConsumer) {
        this.offlineConsumer = offlineConsumer;
    }

    public void setEncoderPort(int encoderPort) {
        this.encoderPort = encoderPort;
    }
}
