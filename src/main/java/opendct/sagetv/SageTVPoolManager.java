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
import opendct.power.PowerEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SageTVPoolManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(SageTVPoolManager.class);
    public static PowerEventListener POWER_EVENT_LISTENER = new SageTVPoolManager();
    private AtomicBoolean suspend = new AtomicBoolean(false);

    // Virtual capture devices are the names of the devices as seen by SageTV.
    // Pool capture device are the name of the devices actually being used by OpenDCT.
    //
    // All methods in this class only keep track of capture device names because if for example a
    // capture device is removed and re-initialized this class could be hanging onto a stale
    // instance. This class is strictly for re-ordering request so this should be sufficient.
    //
    // This could get a little confusing, so try to keep it clear when referring to capture devices.
    // The SageTVPoolManager should be the only class that will swap things around if needed.

    private static final ReentrantReadWriteLock vCaptureDeviceToPoolCaptureDeviceLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock poolNameToPoolCaptureDevicesLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock vCaptureDeviceToPoolNameLock = new ReentrantReadWriteLock();

    private static final HashMap<String, String> vCaptureDeviceToPoolCaptureDevice = new HashMap<>();
    private static final HashMap<String, ArrayList<String>> poolNameToPoolCaptureDevices = new HashMap<>();
    private static final HashMap<String, String> vCaptureDeviceToPoolName = new HashMap<>();


    private static boolean usePools = Config.getBoolean("pool.enabled", false);
    private static final Object bestDeviceWriteLock = new Object();
    private static Thread poolMonitor;
    private static long poolMonitorPolling;

    /**
     * Finds the best available capture device in the pool, locks it and puts it on the map, then
     * returns the pool capture device.
     * <p/>
     * If the capture device is not in a pool, it is mapped directly to itself.
     *
     * @param vCaptureDevice The name of the virtual capture device.
     * @return The name of the pool capture device or <i>null</i> if no device is available.
     */
    public static String getAndLockBestCaptureDevice(String vCaptureDevice) {

        long startTime = System.currentTimeMillis();

        if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
            vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length());
        }

        final String poolName = vCaptureDeviceToPoolName(vCaptureDevice);

        if (poolName == null || !usePools) {
            // This device is not associated with any pool so it will just be mapped to itself.
            setVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, vCaptureDevice);

            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(vCaptureDevice, true);
            captureDevice.setLocked(true);

            if (logger.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                logger.debug("'{}' capture device selected in {}ms.", vCaptureDevice, endTime - startTime);
            } else {
                logger.info("'{}' capture device selected.", vCaptureDevice);
            }

            return vCaptureDevice;
        }

        final ArrayList<String> poolCaptureDevices = poolNameToPoolCaptureDevices(poolName);

        if (poolCaptureDevices == null) {
            // This device is not associated with any pool so it will just be mapped to itself, but
            // claimed it did in the previous step. Displaying warning so we know something odd has
            // happened, but not strange enough to prevent us from proceeding.
            logger.warn("'{}' claims to be a part of the pool named '{}' but the pool does not exist.", vCaptureDevice, poolName);

            setVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, vCaptureDevice);

            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(vCaptureDevice, true);
            captureDevice.setLocked(true);

            if (logger.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                logger.debug("'{}' capture device selected in {}ms.", vCaptureDevice, endTime - startTime);
            } else {
                logger.info("'{}' capture device selected.", vCaptureDevice);
            }

            return vCaptureDevice;
        }

        // We only need to synchronize per pool.
        synchronized (poolName) {
            boolean tryAgain = true;

            while (tryAgain && !Thread.currentThread().isInterrupted()) {
                tryAgain = false;

                // Temporarily Store all of the capture devices we might come back to so we don't need
                // to look them up twice.
                ArrayList<CaptureDevice> externalLocked = new ArrayList<>();

                // These are already in their order of merit since every time a new device is added,
                // they are re-sorted by merit.
                for (String poolCaptureDevice : poolCaptureDevices) {
                    CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(poolCaptureDevice, true);

                    if (captureDevice.isLocked()) {
                        continue;
                    }

                    if (captureDevice.isExternalLocked()) {
                        externalLocked.add(captureDevice);
                        continue;
                    }

                    // Map device so we can find it later by the name SageTV uses.
                    setVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, poolCaptureDevice);

                    captureDevice.setLocked(true);

                    if (logger.isDebugEnabled()) {
                        long endTime = System.currentTimeMillis();
                        logger.debug("'{}' pool capture device selected for virtual capture device '{}' in {}ms.", poolCaptureDevice, vCaptureDevice, endTime - startTime);
                    } else {
                        logger.info("'{}' pool capture device selected for virtual capture device '{}'.", poolCaptureDevice, vCaptureDevice);
                    }

                    return poolCaptureDevice;
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.warn("The thread was interrupted before a pool capture device could be found.");
                    return null;
                }

                // If we can't find a device that's not locked, then we need to use one that is.
                for (CaptureDevice captureDevice : externalLocked) {
                    if (captureDevice.isLocked()) {
                        // If suddenly a capture device has become locked and all of the other
                        // devices are externally locked, go back to see if anything else is now
                        // unlocked and doesn't have an external lock.
                        tryAgain = true;
                        continue;
                    }

                    if (captureDevice.setExternalLock(false)) {

                        // Map device so we can find it later by the name SageTV uses.
                        setVCaptureDeviceToPoolCaptureDevice(vCaptureDevice, captureDevice.getEncoderName());

                        captureDevice.setLocked(true);

                        if (logger.isDebugEnabled()) {
                            long endTime = System.currentTimeMillis();
                            logger.debug("'{}' pool capture device selected for virtual capture device '{}' in {}ms.", captureDevice.getEncoderName(), vCaptureDevice, endTime - startTime);
                        } else {
                            logger.info("'{}' pool capture device selected for virtual capture device '{}'.", captureDevice.getEncoderName(), vCaptureDevice);
                        }

                        return captureDevice.getEncoderName();
                    }
                }
            }

            logger.error("Unable to locate a free pool capture device for '{}'.");
            return null;
        }
    }

    private static void setVCaptureDeviceToPoolCaptureDevice(String vCaptureDevice, String pCaptureDevice) {

        vCaptureDeviceToPoolCaptureDeviceLock.writeLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length());
            }

            vCaptureDeviceToPoolCaptureDevice.put(vCaptureDevice, pCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            vCaptureDeviceToPoolCaptureDeviceLock.writeLock().unlock();
        }
    }

    /**
     * Add a capture device to a pool.
     *
     * @param poolName The name of the pool.
     * @param captureDevice The name of the pool capture device.
     */
    public static void addPoolCaptureDevice(String poolName, String captureDevice) {
        vCaptureDeviceToPoolCaptureDeviceLock.writeLock().lock();
        poolNameToPoolCaptureDevicesLock.writeLock().lock();
        vCaptureDeviceToPoolNameLock.writeLock().lock();

        try {
            vCaptureDeviceToPoolCaptureDevice.put(captureDevice, poolName);
            ArrayList<String> poolCaptureDevices = poolNameToPoolCaptureDevices.get(poolName);

            if (poolCaptureDevices == null) {
                poolCaptureDevices = new ArrayList<>();
                poolNameToPoolCaptureDevices.put(poolName, poolCaptureDevices);
            }

            poolCaptureDevices.add(captureDevice);

            poolCaptureDevices.sort(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    CaptureDevice c1 = SageTVManager.getSageTVCaptureDevice(o1, true);
                    CaptureDevice c2 = SageTVManager.getSageTVCaptureDevice(o2, true);

                    if (c1.getMerit() > c2.getMerit()) {
                        return -1;
                    } else if (c1.getMerit() < c2.getMerit()) {
                        return 1;
                    }

                    return 0;
                }
            });
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            vCaptureDeviceToPoolCaptureDeviceLock.writeLock().unlock();
            poolNameToPoolCaptureDevicesLock.writeLock().unlock();
            vCaptureDeviceToPoolNameLock.writeLock().unlock();
        }
    }

    /**
     * This is will go through all available pools and re-sort them based on merit
     * <p/>
     * Run this any time you change the merit of any tuner at runtime for the new merit to take
     * effect.
     */
    public void resortMerits() {
        vCaptureDeviceToPoolCaptureDeviceLock.writeLock().lock();
        poolNameToPoolCaptureDevicesLock.writeLock().lock();
        vCaptureDeviceToPoolNameLock.writeLock().lock();

        try {
            for (Map.Entry<String, ArrayList<String>> poolKeyValue: poolNameToPoolCaptureDevices.entrySet()) {
                if (poolKeyValue.getValue() != null) {
                    poolKeyValue.getValue().sort(new Comparator<String>() {
                        @Override
                        public int compare(String o1, String o2) {
                            CaptureDevice c1 = SageTVManager.getSageTVCaptureDevice(o1, true);
                            CaptureDevice c2 = SageTVManager.getSageTVCaptureDevice(o2, true);

                            if (c1.getMerit() > c2.getMerit()) {
                                return -1;
                            } else if (c1.getMerit() < c2.getMerit()) {
                                return 1;
                            }

                            return 0;
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            vCaptureDeviceToPoolCaptureDeviceLock.writeLock().unlock();
            poolNameToPoolCaptureDevicesLock.writeLock().unlock();
            vCaptureDeviceToPoolNameLock.writeLock().unlock();
        }
    }

    /**
     * Returns the pool associated with this capture device.
     * <p/>
     * This can be used to determine what pool if any can be queried for the best capture device to
     * use for encoding. It will remove " Digital TV Tuner" automatically if present.
     *
     * @param vCaptureDevice The name of the virtual SageTV capture device.
     * @return Returns the name of the pool associated with this capture device. If no pool is
     *         associated <i>null</i> will be returned.
     */
    public static String vCaptureDeviceToPoolName(String vCaptureDevice) {

        String returnValue = null;

        vCaptureDeviceToPoolNameLock.readLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length());
            }

            returnValue = vCaptureDeviceToPoolName.get(vCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            vCaptureDeviceToPoolNameLock.readLock().unlock();
        }

        return returnValue;
    }

    /**
     * Returns the actual pool capture device currently associated with the virtual SageTV capture
     * device.
     * <p/>
     * This can be used to ensure that we get the right capture device for things like SWITCH and
     * GET_FILE_SIZE commands. It will remove " Digital TV Tuner" automatically if present.
     *
     * @param vCaptureDevice The name of the virtual capture device as provided by SageTV.
     * @return The name of the pool capture device.
     */
    public static String vCaptureDeviceToPoolCaptureDevice(String vCaptureDevice) {

        String returnValue = null;

        vCaptureDeviceToPoolCaptureDeviceLock.readLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length());
            }

            returnValue = vCaptureDeviceToPoolCaptureDevice.get(vCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            vCaptureDeviceToPoolCaptureDeviceLock.readLock().unlock();
        }

        return returnValue;
    }

    /**
     * Returns a list of all pool devices associated with the provided pool name.
     * <p/>
     * If no pool exists a <i>null</i> value will be returned. If the pool contains no devices, an
     * empty array will be returned.
     *
     * @param poolName The name of the pool requested.
     * @return An array of the devices associated with the pool or 'null' if the pool doesn't exist.
     */
    public static ArrayList<String> poolNameToPoolCaptureDevices(String poolName) {

        ArrayList<String> returnValue = null;

        poolNameToPoolCaptureDevicesLock.readLock().lock();

        try {
            returnValue = poolNameToPoolCaptureDevices.get(poolName);

            if (returnValue != null) {
                // Create a new array so we don't accidentally modify it outside of this class.
                returnValue = new ArrayList<String>(returnValue);
            }
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            poolNameToPoolCaptureDevicesLock.readLock().unlock();
        }

        return returnValue;
    }

    public void onSuspendEvent() {
        if (suspend.getAndSet(true)) {
            logger.error("onSuspendEvent: The computer is going into suspend mode and SageTVPoolManager has possibly not recovered from the last suspend event.");
        } else {
            logger.debug("onSuspendEvent: Stopping services due to a suspend event.");
        }
    }

    public void onResumeSuspendEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeSuspendEvent: The computer returned from suspend mode and SageTVPoolManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeSuspendEvent: Starting services due to a resume event.");
        }
    }

    public void onResumeCriticalEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeCriticalEvent: The computer returned from suspend mode and SageTVPoolManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeCriticalEvent: Starting services due to a resume event.");
        }
    }

    public void onResumeAutomaticEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeAutomaticEvent: The computer returned from suspend mode and SageTVPoolManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeAutomaticEvent: Starting services due to a resume event.");
        }
    }
}
