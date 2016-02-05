/*
 * Copyright 2016 The OpenDCT Authors. All Rights Reserved
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
import opendct.config.Config;
import opendct.config.options.BooleanDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.hdhomerun.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class HDHRNativeCaptureDevice extends RTPCaptureDevice {
    private final Logger logger = LogManager.getLogger(HDHRNativeCaptureDevice.class);

    private final HDHomeRunDiscoveredDeviceParent discoveredDeviceParent;
    private final HDHomeRunDiscoveredDevice discoveredDevice;
    private final HDHomeRunDevice device;
    private final HDHomeRunTuner tuner;

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Object exclusiveLock = new Object();

    private final ConcurrentHashMap<String, DeviceOption> deviceOptions;
    private BooleanDeviceOption forceExternalUnlock;

    /**
     * Create a new HDHomeRun capture device.
     *
     * @param discoveredDeviceParent This is the name of the device containing this capture device. This
     *                         is used for identifying groupings of devices.
     * @param discoveredDevice This name is used to uniquely identify this capture device. The encoder
     *               version is defaulted to 3.0.
     * @throws CaptureDeviceIgnoredException If the configuration indicates that this device should
     *                                       not be loaded, this exception will be thrown.
     */
    public HDHRNativeCaptureDevice(HDHomeRunDiscoveredDeviceParent discoveredDeviceParent, HDHomeRunDiscoveredDevice discoveredDevice) throws CaptureDeviceIgnoredException, CaptureDeviceInitException {
        super(discoveredDeviceParent.getFriendlyName(), discoveredDevice.getFriendlyName(), discoveredDeviceParent.getParentId(), discoveredDevice.getId());

        this.discoveredDeviceParent = discoveredDeviceParent;
        this.discoveredDevice = discoveredDevice;
        device = discoveredDeviceParent.getDevice();
        tuner = device.getTuner(discoveredDevice.getTunerNumber());

        deviceOptions = new ConcurrentHashMap<>();

        try {
            forceExternalUnlock = new BooleanDeviceOption(
                    Config.getBoolean(propertiesDeviceRoot + "always_force_external_unlock", false),
                    false,
                    "Always Force Unlock",
                    propertiesDeviceRoot + "always_force_external_unlock",
                    "This will allow the program to always override the HDHomeRun lock when" +
                            " SageTV requests a channel to be tuned."
            );

            Config.mapDeviceOptions(
                    deviceOptions,
                    forceExternalUnlock
            );
        } catch (DeviceOptionException e) {
            logger.error("Unable to configure device options for HDHRNativeCaptureDevice => ", e);
        }
    }

    @Override
    public boolean isLocked() {
        return locked.get();
    }

    @Override
    public boolean setLocked(boolean locked) {
        boolean messageLock = this.locked.get();

        // This means the lock was already set
        if (this.locked.getAndSet(locked) == locked) {
            logger.info("Capture device is was already {}.", (locked ? "locked" : "unlocked"));
            return false;
        }

        synchronized (exclusiveLock) {
            this.locked.set(locked);

            if (messageLock != locked) {
                logger.info("Capture device is now {}.", (locked ? "locked" : "unlocked"));
            } else {
                logger.debug("Capture device is now re-{}.", (locked ? "locked" : "unlocked"));
            }
        }

        return true;
    }

    @Override
    public boolean isExternalLocked() {
        try {
            boolean returnValue = tuner.isLocked();

            logger.info("HDHomeRun is currently {}.", (returnValue ? "locked" : "unlocked"));

            return returnValue;
        } catch (IOException e) {
            logger.error("Unable to get the locked status of HDHomeRun because it cannot be reached => ", e);

            // If we can't reach it, it's as good as locked.
            return true;
        } catch (GetSetException e) {
            logger.error("Unable to get the locked status of HDHomeRun because the command did not work => ", e);

            // The device must not support locking.
            return false;
        }
    }

    @Override
    public boolean setExternalLock(boolean locked) {
        if (HDHomeRunDiscoverer.getHdhrLock() && locked) {
            try {
                if (forceExternalUnlock.getBoolean()) {
                    tuner.forceClearLockkey();
                }

                tuner.setLockkey(discoveredDeviceParent.getLocalAddress());
                logger.info("HDHomeRun is now locked.");

                return true;
            } catch (IOException e) {
                logger.error("Unable to lock HDHomeRun because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to lock HDHomeRun because the command did not work => ", e);
            }
        } else if (HDHomeRunDiscoverer.getHdhrLock()) {
            try {
                if (forceExternalUnlock.getBoolean()) {
                    tuner.forceClearLockkey();
                } else {
                    tuner.clearLockkey();
                }

                logger.info("HDHomeRun is now unlocked.");

                return true;
            } catch (IOException e) {
                logger.error("Unable to unlock HDHomeRun because it cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to unlock HDHomeRun because the command did not work => ", e);
            }
        }

        return false;
    }

    @Override
    public boolean getChannelInfoOffline(TVChannel tvChannel) {

        return false;
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize) {
        return false;
    }

    @Override
    public boolean startEncoding(String channel, String filename, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
        return false;
    }

    @Override
    public void tuneToChannel(String channel) {

    }

    @Override
    public boolean autoTuneChannel(String channel) {
        return false;
    }

    @Override
    public boolean isReady() {
        return false;
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
    public DeviceOption[] getOptions() {
        return new DeviceOption[] {
                forceExternalUnlock
        };
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        for (DeviceOption option : deviceOptions) {
            DeviceOption optionReference = this.deviceOptions.get(option.getProperty());

            if (optionReference == null) {
                continue;
            }

            if (optionReference.isArray()) {
                optionReference.setValue(option.getArrayValue());
            } else {
                optionReference.setValue(option.getValue());
            }

            Config.setDeviceOption(optionReference);
        }

        Config.saveConfig();
    }
}
