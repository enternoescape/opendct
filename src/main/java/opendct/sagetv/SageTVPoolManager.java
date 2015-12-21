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
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SageTVPoolManager  {
    private static final Logger logger = LogManager.getLogger(SageTVPoolManager.class);

    // Virtual capture devices are the names of the devices as seen by SageTV.
    // Pool capture device are the name of the devices actually being used by OpenDCT.
    //
    // All methods in this class only keep track of capture device names because if for example a
    // capture device is removed and re-initialized this class could be hanging onto a stale
    // instance. This class is strictly for re-ordering request so this should be sufficient.
    //
    // This could get a little confusing, so try to keep it clear when referring to capture devices.
    // The SageTVPoolManager should be the only class that will swap things around if needed.

    private static final ReentrantReadWriteLock captureDeviceMappingLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock poolNameToPoolCaptureDevicesLock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock vCaptureDeviceToPoolNameLock = new ReentrantReadWriteLock();

    private static final HashMap<String, String> vCaptureDeviceToPoolCaptureDevice = new HashMap<>();
    private static final HashMap<String, String> poolCaptureDeviceToVCaptureDevice = new HashMap<>();
    private static final HashMap<String, ArrayList<String>> poolNameToPoolCaptureDevices = new HashMap<>();
    private static final HashMap<String, String> vCaptureDeviceToPoolName = new HashMap<>();

    private static boolean usePools = Config.getBoolean("pool.enabled", false);

    /**
     * Finds the best available capture device in the pool, locks it and puts it on the map, then
     * returns the pool capture device.
     * <p/>
     * If the capture device is not in a pool, it is mapped directly to itself. If the mapping has
     * not been removed for the capture device (usually done on STOP), the last mapping will be
     * returned.
     *
     * @param vCaptureDevice The name of the virtual capture device.
     * @return The name of the pool capture device or <i>null</i> if no device is available.
     */
    public static String getAndLockBestCaptureDevice(String vCaptureDevice) {

        long startTime = System.currentTimeMillis();

        if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
            vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length()).trim();
        }

        final String pCaptureDevice = getVCaptureDeviceToPoolCaptureDevice(vCaptureDevice);

        if (pCaptureDevice != null) {
            return pCaptureDevice;
        }

        final String poolName = getVCaptureDeviceToPoolName(vCaptureDevice);

        if (poolName == null || !usePools) {
            // This device is not associated with any pool so it will just be mapped to itself.
            setCaptureDeviceMapping(vCaptureDevice, vCaptureDevice);

            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(vCaptureDevice, false);

            if (captureDevice == null) {
                return null;
            }

            captureDevice.setLocked(true);

            if (logger.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                logger.debug("'{}' capture device selected in {}ms.", vCaptureDevice, endTime - startTime);
            } else {
                logger.info("'{}' capture device selected.", vCaptureDevice);
            }

            return vCaptureDevice;
        }

        final ArrayList<String> poolCaptureDevices = getPoolNameToPoolCaptureDevices(poolName);

        if (poolCaptureDevices == null) {
            // This device is not associated with any pool so it will just be mapped to itself, but
            // claimed it did in the previous step. Displaying warning so we know something odd has
            // happened, but not strange enough to prevent us from proceeding.
            logger.warn("'{}' claims to be a part of the pool named '{}' but the pool does not exist.", vCaptureDevice, poolName);

            setCaptureDeviceMapping(vCaptureDevice, vCaptureDevice);

            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(vCaptureDevice, false);

            if (captureDevice == null) {
                return null;
            }

            captureDevice.setLocked(true);

            if (logger.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                logger.debug("'{}' capture device selected in {}ms.", vCaptureDevice, endTime - startTime);
            } else {
                logger.info("'{}' capture device selected.", vCaptureDevice);
            }

            return vCaptureDevice;
        }

        boolean tryAgain = true;

        while (tryAgain && !Thread.currentThread().isInterrupted()) {
            tryAgain = false;

            // Temporarily Store all of the capture devices we might come back to so we don't need
            // to look them up twice.
            ArrayList<CaptureDevice> externalLocked = new ArrayList<>();

            // These are already in their order of merit since every time a new device is added,
            // they are re-sorted by merit.
            for (String poolCaptureDevice : poolCaptureDevices) {
                CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(poolCaptureDevice, false);

                if (captureDevice == null) {
                    continue;
                }

                if (captureDevice.isLocked()) {
                    continue;
                }

                if (captureDevice.isExternalLocked()) {
                    externalLocked.add(captureDevice);
                    continue;
                }

                if (!captureDevice.setLocked(true)) {
                    continue;
                }

                // Map device so we can find it later by the name SageTV uses.
                setCaptureDeviceMapping(vCaptureDevice, poolCaptureDevice);

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
                    setCaptureDeviceMapping(vCaptureDevice, captureDevice.getEncoderName());

                    if (!captureDevice.setLocked(true)) {
                        continue;
                    }

                    if (logger.isDebugEnabled()) {
                        long endTime = System.currentTimeMillis();
                        logger.debug("'{}' pool capture device was externally locked and was selected for virtual capture device '{}' in {}ms.", captureDevice.getEncoderName(), vCaptureDevice, endTime - startTime);
                    } else {
                        logger.info("'{}' pool capture device was externally locked and was selected for virtual capture device '{}'.", captureDevice.getEncoderName(), vCaptureDevice);
                    }

                    return captureDevice.getEncoderName();
                }
            }

            // If we can't find a device that's online, let's try to tune the first internally
            // unlocked tuner.
            // If we can't find a device that's not locked, then we need to use one that is.
            for (CaptureDevice captureDevice : externalLocked) {
                if (captureDevice.isLocked()) {
                    // If suddenly a capture device has become locked and all of the other
                    // devices are externally locked, go back to see if anything else is now
                    // unlocked and doesn't have an external lock.
                    tryAgain = true;
                    continue;
                }

                if (!captureDevice.setLocked(true)) {
                    continue;
                }

                captureDevice.setExternalLock(false);

                setCaptureDeviceMapping(vCaptureDevice, captureDevice.getEncoderName());

                if (logger.isDebugEnabled()) {
                    long endTime = System.currentTimeMillis();
                    logger.warn("'{}' pool capture device was unable to be externally unlocked, but we have no other options so it was selected for virtual capture device '{}' in {}ms.", captureDevice.getEncoderName(), vCaptureDevice, endTime - startTime);
                } else {
                    logger.warn("'{}' pool capture device was unable to be externally unlocked, but we have no other options so it was selected for virtual capture device '{}'.", captureDevice.getEncoderName(), vCaptureDevice);
                }

                return captureDevice.getEncoderName();
            }
        }

        logger.error("Unable to locate a free pool capture device for '{}'.");
        return null;

    }

    /**
     * Sets the virtual capture device to pool capture device mapping and the reverse.
     *
     * @param vCaptureDevice The name of the virtual capture device.
     * @param pCaptureDevice The name of the pool capture device.
     */
    private static void setCaptureDeviceMapping(String vCaptureDevice, String pCaptureDevice) {

        captureDeviceMappingLock.writeLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length()).trim();
            }

            vCaptureDeviceToPoolCaptureDevice.put(vCaptureDevice, pCaptureDevice);
            poolCaptureDeviceToVCaptureDevice.put(pCaptureDevice, vCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceMappingLock.writeLock().unlock();
        }
    }

    /**
     * Removes the virtual capture device to pool capture device mapping and the reverse.
     *
     * @param vCaptureDevice The virtual capture device to remove.
     */
    public static void removeCaptureDeviceMapping(String vCaptureDevice) {
        captureDeviceMappingLock.writeLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length()).trim();
            }

            String pCaptureDevice = vCaptureDeviceToPoolCaptureDevice.get(vCaptureDevice);

            if (pCaptureDevice != null) {
                poolCaptureDeviceToVCaptureDevice.remove(pCaptureDevice);
            }

            vCaptureDeviceToPoolCaptureDevice.remove(vCaptureDevice);

            logger.info("Cleared mapping for virtual capture device '{}'.", vCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceMappingLock.writeLock().unlock();
        }
    }

    /**
     * Removes a capture device from the pool manager completely.
     *
     * @param vCaptureDevice The name of the virtual capture device.
     */
    public static void removePoolCaptureDevice(String vCaptureDevice) {
        poolNameToPoolCaptureDevicesLock.writeLock().lock();
        vCaptureDeviceToPoolNameLock.writeLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length()).trim();
            }

            ArrayList<String> removePools = new ArrayList<>();

            for (Map.Entry<String, ArrayList<String>> poolCaptureDeviceKVP : poolNameToPoolCaptureDevices.entrySet()) {

                ArrayList<String> poolCaptureDevices = poolCaptureDeviceKVP.getValue();
                ArrayList<String> removePoolCaptureDevices = new ArrayList<>();

                for (String poolCaptureDevice : poolCaptureDevices) {
                    if (poolCaptureDevice.equals(vCaptureDevice)) {
                        removePoolCaptureDevices.add(poolCaptureDevice);
                    }
                }

                for (String removeDevice : removePoolCaptureDevices) {
                    poolCaptureDevices.remove(removeDevice);
                }

                if (poolCaptureDevices.size() == 0) {
                    removePools.add(poolCaptureDeviceKVP.getKey());
                }

                logger.info("The capture device '{}' has been removed from the '{}' pool.", vCaptureDevice, poolCaptureDeviceKVP.getKey());
            }

            for (String removePool : removePools) {
                logger.info("Removed the pool '{}' since it no longer contains any capture devices.", removePool);
                poolNameToPoolCaptureDevices.remove(removePool);
            }

            vCaptureDeviceToPoolName.remove(vCaptureDevice);

            // Don't clear the mapping since the device might still be in use and we won't be able
            // to find it again. This will clean itself up when SageTV sends a STOP command.
            //vCaptureDeviceToPoolCaptureDevice.remove(vCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            poolNameToPoolCaptureDevicesLock.writeLock().unlock();
            vCaptureDeviceToPoolNameLock.writeLock().unlock();
        }
    }

    /**
     * Add a capture device to a pool.
     * <p/>
     * If a <i>null</i> or empty string is provided for the pool name, the device will be removed.
     * If the device already exists in a pool, and the pool provided is different, it will be moved
     * to the new pool.
     *
     * @param poolName The name of the pool.
     * @param captureDevice The name of the pool capture device.
     */
    public static void addPoolCaptureDevice(String poolName, String captureDevice) {
        if (!isUsePools()) {
            return;
        }

        if (Util.isNullOrEmpty(poolName)) {
            removePoolCaptureDevice(captureDevice);
            return;
        }

        poolNameToPoolCaptureDevicesLock.writeLock().lock();
        vCaptureDeviceToPoolNameLock.writeLock().lock();

        try {
            String oldPool = vCaptureDeviceToPoolName.get(captureDevice);

            if (oldPool != null && oldPool.equals(poolName)) {
                logger.debug("The capture device '{}' has already been added to the '{}' pool.", captureDevice, poolName);
                return;
            } else if (oldPool != null && !oldPool.equals(poolName)) {
                ArrayList<String> poolCaptureDevices = poolNameToPoolCaptureDevices.get(poolName);
                ArrayList<String> removePoolCaptureDevices = new ArrayList<>();

                for (String poolCaptureDevice : poolCaptureDevices) {
                    if (poolCaptureDevice.equals(captureDevice)) {
                        removePoolCaptureDevices.add(poolCaptureDevice);
                    }
                }

                for (String removeDevice : removePoolCaptureDevices) {
                    poolCaptureDevices.remove(removeDevice);
                }

                logger.info("The capture device '{}' has been moved from the '{}' pool to the '{}' pool.", captureDevice, oldPool, poolName);
            }

            vCaptureDeviceToPoolName.put(captureDevice, poolName);
            ArrayList<String> poolCaptureDevices = poolNameToPoolCaptureDevices.get(poolName);

            if (poolCaptureDevices == null) {
                poolCaptureDevices = new ArrayList<>();
                poolNameToPoolCaptureDevices.put(poolName, poolCaptureDevices);
                logger.info("The pool '{}' has been created.", poolName);
            }

            poolCaptureDevices.add(captureDevice);

            Collections.sort(poolCaptureDevices, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    CaptureDevice c1 = SageTVManager.getSageTVCaptureDevice(o1, false);
                    CaptureDevice c2 = SageTVManager.getSageTVCaptureDevice(o2, false);

                    if (c1 == null && c2 == null) {
                        logger.warn("'{}' and '{}' don't exist.", o1, o2);
                        return 0;
                    } else if (c1 == null) {
                        logger.warn("'{}' doesn't exist.", o1);
                        return -1;
                    } else if (c2 == null) {
                        logger.warn("'{}' doesn't exist.", o2);
                        return 1;
                    }

                    if (c1.getMerit() > c2.getMerit()) {
                        return -1;
                    } else if (c1.getMerit() < c2.getMerit()) {
                        return 1;
                    }

                    return 0;
                }
            });

            logger.info("The capture device '{}' has been added to the '{}' pool.", captureDevice, poolName);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
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
    public static void resortAllMerits() {
        captureDeviceMappingLock.writeLock().lock();
        poolNameToPoolCaptureDevicesLock.writeLock().lock();
        vCaptureDeviceToPoolNameLock.writeLock().lock();

        try {
            for (Map.Entry<String, ArrayList<String>> poolKeyValue: poolNameToPoolCaptureDevices.entrySet()) {
                if (poolKeyValue.getValue() != null) {

                    Collections.sort(poolKeyValue.getValue(), new Comparator<String>() {
                        @Override
                        public int compare(String o1, String o2) {
                            CaptureDevice c1 = SageTVManager.getSageTVCaptureDevice(o1, false);
                            CaptureDevice c2 = SageTVManager.getSageTVCaptureDevice(o2, false);

                            if (c1 == null && c2 == null) {
                                logger.warn("'{}' and '{}' don't exist.", o1, o2);
                                return 0;
                            } else if (c1 == null) {
                                logger.warn("'{}' doesn't exist.", o1);
                                return -1;
                            } else if (c2 == null) {
                                logger.warn("'{}' doesn't exist.", o2);
                                return 1;
                            }

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
            captureDeviceMappingLock.writeLock().unlock();
            poolNameToPoolCaptureDevicesLock.writeLock().unlock();
            vCaptureDeviceToPoolNameLock.writeLock().unlock();
        }
    }

    public static void resortMerits(String poolName) {
        ArrayList<String> encoders = poolNameToPoolCaptureDevices.get(poolName);

        if (encoders == null) {
            return;
        }

        captureDeviceMappingLock.writeLock().lock();
        poolNameToPoolCaptureDevicesLock.writeLock().lock();
        vCaptureDeviceToPoolNameLock.writeLock().lock();

        try {
                Collections.sort(encoders, new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        CaptureDevice c1 = SageTVManager.getSageTVCaptureDevice(o1, false);
                        CaptureDevice c2 = SageTVManager.getSageTVCaptureDevice(o2, false);

                        if (c1 == null && c2 == null) {
                            logger.warn("'{}' and '{}' don't exist.", o1, o2);
                            return 0;
                        } else if (c1 == null) {
                            logger.warn("'{}' doesn't exist.", o1);
                            return -1;
                        } else if (c2 == null) {
                            logger.warn("'{}' doesn't exist.", o2);
                            return 1;
                        }

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
            captureDeviceMappingLock.writeLock().unlock();
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
    public static String getVCaptureDeviceToPoolName(String vCaptureDevice) {

        String returnValue = null;

        vCaptureDeviceToPoolNameLock.readLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length()).trim();
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
    public static String getVCaptureDeviceToPoolCaptureDevice(String vCaptureDevice) {

        String returnValue = null;

        captureDeviceMappingLock.readLock().lock();

        try {
            if (vCaptureDevice.endsWith(" Digital TV Tuner")) {
                vCaptureDevice = vCaptureDevice.substring(0, vCaptureDevice.length() - " Digital TV Tuner".length()).trim();
            }

            returnValue = vCaptureDeviceToPoolCaptureDevice.get(vCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceMappingLock.readLock().unlock();
        }

        return returnValue;
    }

    /**
     * Returns the actual virtual SageTV capture device currently associated with the pool capture
     * device.
     *
     * @param pCaptureDevice The name of the pool capture device as provided by the pool.
     * @return The name of the virtual capture device.
     */
    public static String getPoolCaptureDeviceToVCaptureDevice(String pCaptureDevice) {

        String returnValue = null;

        captureDeviceMappingLock.readLock().lock();

        try {
            if (pCaptureDevice.endsWith(" Digital TV Tuner")) {
                pCaptureDevice = pCaptureDevice.substring(0, pCaptureDevice.length() - " Digital TV Tuner".length()).trim();
            }

            returnValue = poolCaptureDeviceToVCaptureDevice.get(pCaptureDevice);
        } catch (Exception e) {
            logger.warn("There was an unhandled exception while using a ReentrantReadWriteLock => ", e);
        } finally {
            captureDeviceMappingLock.readLock().unlock();
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
    public static ArrayList<String> getPoolNameToPoolCaptureDevices(String poolName) {

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

    /**
     * Are pools enabled?
     *
     * @return <i>true</i> if pools are enabled.
     */
    public static boolean isUsePools() {
        return usePools;
    }
}
