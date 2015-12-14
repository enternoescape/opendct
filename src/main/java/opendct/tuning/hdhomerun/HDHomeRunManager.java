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

package opendct.tuning.hdhomerun;

import opendct.power.PowerEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class HDHomeRunManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(HDHomeRunManager.class);
    public static PowerEventListener POWER_EVENT_LISTENER = new HDHomeRunManager();

    private static HDHomeRunDiscovery discovery = new HDHomeRunDiscovery(HDHomeRunDiscovery.getBroadcast());

    private static ConcurrentHashMap<Integer, HDHomeRunDevice> devicesById =
            new ConcurrentHashMap<Integer, HDHomeRunDevice>();

    public static void startDeviceDetection() throws IOException {
        discovery.start();
    }

    public static synchronized void addDevices(HashSet<HDHomeRunDevice> devices) {
        for (HDHomeRunDevice device : devices) {
            if (devicesById.get(device.getDeviceId()) == null) {
                updateDevice(device);
                devicesById.put(device.getDeviceId(), device);

                //TODO: Create HDHR capture device and register new devices with SageTV.
                //SageTVManager.addCaptureDevice();
            }
        }
    }

    public static void updateDevice(HDHomeRunDevice device) {

    }

    public static synchronized void removeAllDevices() {
        devicesById.clear();
    }

    public void onSuspendEvent() {
        try {
            discovery.stop();
        } catch (InterruptedException e) {
            logger.debug("Interrupted while stopping thread for suspend => ", e);
        }
        removeAllDevices();
    }

    public void onResumeSuspendEvent() {
        try {
            startDeviceDetection();
        } catch (IOException e) {
            logger.error("Unable to re-detect HDHomeRun devices => {}");
        }
    }

    public void onResumeCriticalEvent() {
        try {
            startDeviceDetection();
        } catch (IOException e) {
            logger.error("Unable to re-detect HDHomeRun devices => {}");
        }
    }

    public void onResumeAutomaticEvent() {
        try {
            startDeviceDetection();
        } catch (IOException e) {
            logger.error("Unable to re-detect HDHomeRun devices => {}");
        }
    }
}
