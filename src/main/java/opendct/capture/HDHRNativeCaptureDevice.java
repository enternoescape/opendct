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
import opendct.tuning.discovery.CaptureDeviceLoadException;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.hdhomerun.*;
import opendct.tuning.hdhomerun.returns.HDHomeRunFeatures;
import opendct.tuning.hdhomerun.returns.HDHomeRunStatus;
import opendct.tuning.hdhomerun.returns.HDHomeRunVStatus;
import opendct.tuning.hdhomerun.types.HDHomeRunChannelMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
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
    public HDHRNativeCaptureDevice(HDHomeRunDiscoveredDeviceParent discoveredDeviceParent, HDHomeRunDiscoveredDevice discoveredDevice) throws CaptureDeviceIgnoredException, CaptureDeviceLoadException {
        super(discoveredDeviceParent.getFriendlyName(), discoveredDevice.getFriendlyName(), discoveredDeviceParent.getParentId(), discoveredDevice.getId());

        this.discoveredDeviceParent = discoveredDeviceParent;
        this.discoveredDevice = discoveredDevice;
        device = discoveredDeviceParent.getDevice();
        tuner = device.getTuner(discoveredDevice.getTunerNumber());

        // =========================================================================================
        // Print out diagnostic information for troubleshooting.
        // =========================================================================================
        if (logger.isDebugEnabled()) {
            try {
                logger.debug("HDHomeRun details: {}, {}, {}, {}", device.getSysHwModel(), device.getSysModel(), device.getSysVersion(), device.getSysFeatures());
                logger.debug("HDHomeRun help: {}", Arrays.toString(device.getHelp()));
            } catch (IOException e) {
                logger.error("Unable to get help from HDHomeRun because the device cannot be reached => ", e);
            } catch (GetSetException e) {
                logger.error("Unable to get help from the HDHomeRun because the command did not work => ", e);
            }
        }

        // =========================================================================================
        // Initialize and configure options.
        // =========================================================================================
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
            throw new CaptureDeviceLoadException(e);
        }

        // =========================================================================================
        // Determine device tuning mode.
        // =========================================================================================
        encoderDeviceType = CaptureDeviceType.HDHOMERUN;

        try {
            if (device.isCableCardTuner()) {
                if (device.getCardStatus().toLowerCase().equals("inserted")) {
                    encoderDeviceType = CaptureDeviceType.DCT_HDHOMERUN;
                } else {
                    encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                }
            } else {
                String channelMapName = tuner.getChannelmap();
                HDHomeRunChannelMap channelMap = HDHomeRunFeatures.getEnumForChannelmap(channelMapName);

                switch (channelMap) {
                    case US_BCAST:
                        encoderDeviceType = CaptureDeviceType.ATSC_HDHOMERUN;
                        break;

                    case US_CABLE:
                        encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
                        break;

                    case US_IRC:
                    case US_HRC:
                    case UNKNOWN:
                        throw new CaptureDeviceLoadException("The program currently does not know how to use the channel map '" + channelMapName + "'.");
                }
                encoderDeviceType = CaptureDeviceType.QAM_HDHOMERUN;
            }
        } catch (IOException e) {
            logger.error("Unable to check HDHomeRun configuration because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get channel map from HDHomeRun because the command did not work => ", e);
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
    public boolean isReady() {
        return true;
    }

    @Override
    public BroadcastStandard getBroadcastStandard() {
        //TODO: Get the actual broadcast standard in use.

        try {
            String lockStr = tuner.getStatus().LOCK_STR;
            logger.debug("getBroadcastStandard: {}", lockStr);
        } catch (IOException e) {
            logger.error("Unable to get broadcast standard from HDHomeRun because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get broadcast standard from HDHomeRun because the command did not work => ", e);
        }

        return BroadcastStandard.QAM256;
    }

    @Override
    public int getSignalStrength() {
        int signal = 0;

        try {
            HDHomeRunStatus status = tuner.getStatus();
            signal = status.SIGNAL_STRENGTH;
        } catch (IOException e) {
            logger.error("Unable to get signal strength from HDHomeRun because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get signal strength from HDHomeRun because the command did not work => ", e);
        }

        return signal;
    }

    @Override
    public CopyProtection getCopyProtection() {
        CopyProtection returnValue = CopyProtection.UNKNOWN;

        try {
            HDHomeRunVStatus vstatus = tuner.getVirtualChannelStatus();
            returnValue = vstatus.COPY_PROTECTION;
        } catch (IOException e) {
            logger.error("Unable to get CCI from HDHomeRun because it cannot be reached => ", e);
        } catch (GetSetException e) {
            logger.error("Unable to get CCI from HDHomeRun because the command did not work => ", e);
        }

        return returnValue;
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
