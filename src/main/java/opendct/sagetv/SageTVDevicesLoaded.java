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

package opendct.sagetv;

import opendct.capture.CaptureDevice;
import opendct.config.Config;
import opendct.config.ExitCode;
import opendct.config.messages.MessageManager;
import opendct.config.messages.MessageTitle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class SageTVDevicesLoaded extends Thread {
    private static final Logger logger = LogManager.getLogger(SageTVDevicesLoaded.class);

    private final long timeout =
            Config.getLong("sagetv.device.global.required_devices_loaded_timeout_ms", 60000);
    private final int requiredDevices =
            Config.getInteger("sagetv.device.global.required_devices_loaded_count", 0);

    // This time is used at startup and when resuming from standby if the device count is unknown.
    private long expireTime = System.currentTimeMillis() + timeout;
    private long minExpireTime = System.currentTimeMillis() + 15000;
    private final Object deviceLoaded = new Object();
    private int devicesLoaded = 0;
    private int lastDevicesLoaded = 0;

    private final CountDownLatch blockUntilLoaded = new CountDownLatch(requiredDevices);

    private volatile boolean allowFailure = true;

    @Override
    public void run() {
        // If the number of devices to wait for is none, then there is no reason to wait.
        if (requiredDevices <= 0) {
            expireTime = System.currentTimeMillis() + timeout;
            return;
        } else {
            expireTime = 0;
        }

        logger.info("Waiting for all capture devices to become available...");

        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            logger.trace("SageTVDevicesLoaded was interrupted => ", e);
        }

        ArrayList<CaptureDevice> captureDevices = SageTVManager.getAllSageTVCaptureDevices();
        int devicesLoaded = captureDevices.size();

        if (devicesLoaded < requiredDevices) {
            if (allowFailure) {
                MessageManager.error(this, MessageTitle.DEVICES_LOADED_FAILURE,
                        "OpenDCT is restarting because it was unable to load all " + requiredDevices
                                + " required capture devices within " + timeout + " milliseconds.");

                ExitCode.SAGETV_NO_DEVICES.terminateJVM();
            } else {
                logger.info("Stopped waiting for devices to become available.");
            }
        } else {
            // This be called to make ensure we cannot still be blocking in the event that we
            // somehow are still blocking at this point.
            deviceAdded();
            logger.info("All required capture devices are now available.");
        }
    }

    @Override
    public void start() {
        allowFailure = true;

        super.start();
    }

    public void stopNoError() {
        allowFailure = false;

        if (this.isAlive()) {
            this.interrupt();
        }
    }

    public synchronized void deviceAdded() {
        int loadedDevices = SageTVManager.getAllSageTVCaptureDevices().size();

        // This will catch us up in case somehow a device gets loaded before this class is
        // initialized.
        while (blockUntilLoaded.getCount() > requiredDevices - loadedDevices) {
            blockUntilLoaded.countDown();

            // Without this if we load more than the expected number of devices, the count down
            // latch will continue to run countDown(), but it will never be a negative number and it
            // can never be equal to a negative number causing an endless loop.
            if (requiredDevices - loadedDevices <= 0) {
                if (this.isAlive()) {
                    logger.debug("Interrupting the device detection timeout thread...");
                    this.interrupt();
                }
                break;
            }
        }

        synchronized (deviceLoaded) {
            devicesLoaded += 1;
            deviceLoaded.notifyAll();
        }
    }

    public void blockUntilAllDevicesLoaded() throws InterruptedException {
        blockUntilLoaded.await();

        if (this.isAlive()) {
            this.interrupt();
        }
    }

    /**
     * Blocks until the next device is loaded.
     *
     * @return <i>true</i> if a new device was added. <i>false</i> if the timeout has expired and no
     *         new devices are likely to have been added. Check one more time for the device even if
     *         <i>false</i> is returned, but don't use this method again after that attempt because
     *         no new devices are expected.
     */
    public boolean blockUntilNextDeviceLoaded() throws InterruptedException {
        long currentTime = System.currentTimeMillis();

        if (currentTime > expireTime) {
            return false;
        }

        // Wait at least a little while so that more than one device hopefully will have been
        // detected in case there are any interdependence.
        if (currentTime < minExpireTime) {
            logger.info("Waiting for devices to load...");
            Thread.sleep(minExpireTime - currentTime);
            return true;
        }

        int currentCount;

        synchronized (deviceLoaded) {
            currentCount = devicesLoaded;

            if (currentCount == lastDevicesLoaded) {
                while (currentCount == devicesLoaded &&
                        System.currentTimeMillis() < expireTime) {

                    logger.debug("Waiting for next device...");
                    deviceLoaded.wait(500);
                }
            }

            lastDevicesLoaded = currentCount;
        }

        return currentCount != devicesLoaded;
    }
}
