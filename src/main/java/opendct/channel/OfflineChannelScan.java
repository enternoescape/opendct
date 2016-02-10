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

package opendct.channel;

import opendct.capture.CaptureDevice;
import opendct.capture.CaptureDeviceType;
import opendct.sagetv.SageTVManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OfflineChannelScan {
    private final Logger logger = LogManager.getLogger(OfflineChannelScan.class);

    // This indicates if a complete scan has completed.
    private volatile boolean complete = false;
    // This indicates if the offline scan is in progress.
    private AtomicBoolean running = new AtomicBoolean(false);
    // This tells the offline scanning threads to stop.
    private volatile boolean stop = false;

    private ExecutorService executorService = null;
    private CountDownLatch completeLatch = null;

    private long startTime = 0;
    private long endTime = 0;
    private long totalChannels = 0;
    private int totalCaptureDevices = 0;
    private ArrayList<TVChannel> scannedChannels = new ArrayList<TVChannel>();
    private final Object scannedChannelsLock = new Object();

    public final String SCAN_NAME;
    public final CaptureDeviceType[] CAPTURE_DEVICE_TYPES;
    public final String[] CAPTURE_DEVICE_NAMES;

    /**
     * Create a new offline scanning device group.
     *
     * @param scanName           This is the name of the group to be created.
     * @param captureDeviceTypes These are the capture device type to be used for this scan. All
     *                           currently available devices of this type will be used in the
     *                           channel scan.
     */
    public OfflineChannelScan(String scanName, CaptureDeviceType... captureDeviceTypes) {
        SCAN_NAME = scanName;
        CAPTURE_DEVICE_TYPES = captureDeviceTypes;
        CAPTURE_DEVICE_NAMES = new String[0];
    }

    /**
     * Create a new offline scanning device group.
     *
     * @param scanName           This is the name of the group to be created.
     * @param captureDeviceNames These are the names of the capture devices to be used for this
     *                           scan. These names will be used to look up the capture devices. A
     *                           String is used instead of the actual object so that we don't
     *                           accidentally retain "stale" capture devices. The scan will only
     *                           fail if it is interrupted or there are no capture devices
     *                           available.
     */
    public OfflineChannelScan(String scanName, String... captureDeviceNames) {
        SCAN_NAME = scanName;
        CAPTURE_DEVICE_TYPES = new CaptureDeviceType[0];
        this.CAPTURE_DEVICE_NAMES = captureDeviceNames;
    }

    /**
     * Has a channel scan been successfully completed?
     * <p/>
     * If you started a channel scan and it stopped, but this value is still <i>false</i> then the
     * channel scan failed. This value should only be checked after checking <b>isRunning()</b> to
     * be sure the scan is no longer in progress. Otherwise it will be hard to tell what this value
     * is really telling you.
     *
     * @return
     */
    public synchronized boolean isComplete() {
        return complete;
    }

    /**
     * Is a channel scan currently in progress?
     *
     * @return <i>true</i> if a channel scan is currently in progress.
     */
    public synchronized boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the current average scanning time per channel.
     * <p/>
     * This is the aggregate average of the parallel processing. That means that if it takes 1 tuner
     * individually 6 seconds to scan a channel and we have 6 capture devices, the average will be 1
     * second since they are being processed in parallel resulting in overall higher throughput.
     *
     * @return The average time to scan a channel or -1 if a scan has never been executed.
     */
    public long getScanAverage() {
        long channels;
        long end;

        if (completeLatch != null) {
            channels = totalChannels - completeLatch.getCount();
        } else {
            return -1;
        }

        if (isRunning()) {
            end = System.currentTimeMillis();
        } else if (startTime < endTime) {
            end = endTime;
        } else {
            return -1;
        }

        // This means we are at the start of offline processing, so we don't really have an average
        // yet, but we do have the time elapsed for the first scan which is better than nothing.
        if (channels == 0 && totalCaptureDevices > 0) {
            return (end - startTime) / totalCaptureDevices;
        } else if (channels == 0) {
            return end - startTime;
        }

        return ((end - startTime) / channels);
    }

    /**
     * Returns the time in milliseconds the scan has taken.
     * <p/>
     * If the scan has completed, this will be the total time of the scan to that time.
     *
     * @return The time in milliseconds the scan has taken. If no scan was ever started, this value
     * should be 0.
     */
    public long getScanTime() {
        long end;

        if (isRunning()) {
            end = System.currentTimeMillis();
        } else if (startTime < endTime) {
            end = endTime;
        } else {
            return 0;
        }

        return end - startTime;
    }

    /**
     * Get the number of capture devices used in the current/last scan.
     *
     * @return The number of capture devices. It will return 0 if a scan was never executed or if
     * the last scan couldn't find any capture devices.
     */
    public int getTotalCaptureDevices() {
        return totalCaptureDevices;
    }

    /**
     * Returns the currently scanned channels for the most recent offline scan request and then
     * clears the list.
     * <p/>
     * This method ensures that all channels returned are new ones.
     *
     * @return The channels that have been scanned so far, not in any particular order.
     */
    public ArrayList<TVChannel> getScannedChannelsAndClear() {
        ArrayList<TVChannel> returnList = null;

        // Once the array list pointer is copied to another array list and a new array is
        // initialized in it's place, we no longer need the lock.
        synchronized (scannedChannelsLock) {
            returnList = scannedChannels;
            scannedChannels = new ArrayList<TVChannel>();
        }

        return returnList;
    }

    /**
     * All scanned channels are added through this method.
     * <p/>
     * This method is thread-safe, but can represent a choking point for all channel scanning
     * threads since they can only access this one at a time.
     *
     * @param tvChannel The channel to be added to the list.
     */
    private void addScannedChannel(TVChannel tvChannel) {
        synchronized (scannedChannelsLock) {
            scannedChannels.add(tvChannel);
        }
    }

    /**
     * Get the numer of channels remaining to be scanned.
     *
     * @return The number of channels remaining or -1 if a scan has never been executed.
     */
    public long getRemainingChannels() {
        if (completeLatch != null) {
            return completeLatch.getCount();
        } else {
            return -1;
        }
    }

    public long getTotalChannels() {
        return totalChannels;
    }

    /**
     * Runs an offline channel scan using the provided channel map.
     * <p/>
     * This method will also automatically register this instance with the channel manager to handle
     * suspend and shutdown correctly.
     *
     * @param channels  This is a list of all of the channels to be scanned. If <i>false</i> is
     *                  returned this list may be partially updated. If this is not a copy of the
     *                  in use channel map, you must ensure that channels are not added or removed
     *                  during the scan.
     * @param scanDelay This is the delay in milliseconds between querying the capture device for
     *                  channel information.
     * @return <i>true</i> if the channel scan was started.
     */
    public synchronized boolean start(TVChannel channels[], final long scanDelay) {
        logger.entry(channels, scanDelay);

        // This needs to come first or this scan will think it's already in progres when all we did was set the flag.
        OfflineChannelScan currentChannelScan = ChannelManager.getOfflineChannelScan(this.SCAN_NAME);
        if (currentChannelScan != null && currentChannelScan.isRunning()) {
            logger.warn("An offline channel scan with the same name, '{}' is already in progress.", this.SCAN_NAME);
            return logger.exit(false);
        }

        if (running.getAndSet(true)) {
            logger.warn("An offline channel scan is already in progress.");
            return logger.exit(false);
        }

        startTime = System.currentTimeMillis();

        HashSet<CaptureDevice> devices = new HashSet<CaptureDevice>();

        for (CaptureDeviceType captureDeviceType : CAPTURE_DEVICE_TYPES) {
            devices.addAll(SageTVManager.getAllSageTVCaptureDevices(captureDeviceType));
        }

        for (String captureDeviceName : CAPTURE_DEVICE_NAMES) {
            CaptureDevice captureDevice = SageTVManager.getSageTVCaptureDevice(captureDeviceName, true);

            if (devices.contains(captureDevice)) {
                continue;
            }

            devices.add(captureDevice);
        }

        if (devices.size() == 0) {
            logger.warn("Offline channel scan could not find any capture devices.");
            running.set(false);
            return logger.exit(false);
        }

        if (executorService != null) {
            try {
                while (!executorService.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
                    logger.info("Waiting for last channel scan to completely stop...");
                }
            } catch (InterruptedException e) {
                logger.debug("Offline channel scan was interrupted.");
                running.set(false);
                return logger.exit(false);
            }
        }

        final ArrayBlockingQueue<CaptureDevice> captureDevices = new ArrayBlockingQueue<CaptureDevice>(devices.size());

        boolean devicesUnlocked = false;

        // Since we separate ClearQAM from CableCARD we can safely assume all
        // DCT_INFINITV can tune the same channels unless you have multiple cable
        // providers which we will look at if that ever actually comes up.
        for (CaptureDevice device : devices) {
            if (!device.isLocked()) {
                devicesUnlocked = true;
            }

            try {
                captureDevices.put(device);
            } catch (InterruptedException e) {
                logger.debug("Offline channel scan was interrupted.");
                running.set(false);
                return logger.exit(false);
            }
        }

        // If there are no capture devices that will be able to do the scan right this instant,
        // there is no point in starting at this time.
        if (!devicesUnlocked) {
            logger.warn("Offline channel scan could not find any capture devices not currently in use.");
            running.set(false);
            return logger.exit(false);
        }

        executorService = Executors.newFixedThreadPool(devices.size());
        totalCaptureDevices = devices.size();

        completeLatch = new CountDownLatch(channels.length);
        totalChannels = channels.length;
        scannedChannels.clear();

        complete = false;
        stop = false;

        // After this point, if we don't register with the channel manager, we cannot correctly stop
        // things if need to suspend or shutdown.
        ChannelManager.addOfflineChannelScan(this);

        for (final TVChannel channel : channels) {

            executorService.submit(new Runnable() {
                public void run() {
                    Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                    Thread.currentThread().setName(SCAN_NAME + "-" + channel.getChannel() + "-" + Thread.currentThread().getId() + ":Unknown");

                    boolean result = false;
                    CaptureDevice captureDevice = null;

                    if (stop) {
                        executorService.shutdown();
                        while (completeLatch.getCount() > 0) {
                            completeLatch.countDown();
                        }
                    }


                    int retry = 10;
                    while (!result && !stop && retry-- > 0) {
                        try {
                            captureDevice = captureDevices.take();
                        } catch (InterruptedException e) {
                            logger.debug("The offline channel scan has been interrupted => ", e);
                            stop = true;
                            completeLatch.countDown();
                            break;
                        }

                        Thread.currentThread().setName(SCAN_NAME + "-" + channel.getChannel() + "-" + Thread.currentThread().getId() + ":" + captureDevice.getEncoderName());

                        try {
                            Thread.sleep(scanDelay);
                        } catch (InterruptedException e) {
                            logger.debug("The offline channel scan has been interrupted => ", e);
                            stop = true;
                            completeLatch.countDown();
                            break;
                        }

                        if (!captureDevice.isLocked()) {
                            logger.info("Scanning the channel '{}' ({}).", channel.getChannel(), channel.getName());
                            result = captureDevice.getChannelInfoOffline(channel, false);
                        } else {
                            // If the device is locked, it's not really fair to call this a failure.
                            retry++;
                        }

                        try {
                            captureDevices.put(captureDevice);
                        } catch (InterruptedException e) {
                            logger.debug("The offline channel scan has been interrupted => ", e);
                            stop = true;
                            completeLatch.countDown();
                            break;
                        }
                    }

                    if (retry == 0) {
                        logger.error("Unable to Scan channel {} ({}).", channel.getChannel(), channel.getName());
                        channel.setTunable(false);
                        addScannedChannel(channel);
                    } else if (!stop) {
                        logger.info("Scanned channel {} ({}). Signal strength is now '{}', CCI is now '{}' and tunable is now '{}'.", channel.getChannel(), channel.getName(), channel.getSignalStrength(), channel.getCci(), channel.isTunable());
                        addScannedChannel(channel);
                    }

                    completeLatch.countDown();

                    // This will free up the completion CountDownLatch if it's waiting.
                    if (stop) {
                        executorService.shutdown();
                        while (completeLatch.getCount() > 0) {
                            completeLatch.countDown();
                        }
                    }
                }
            });
        }

        // This is the always the last thread to stop because it waits for all other thread to
        // complete first.
        executorService.submit(new Runnable() {
            public void run() {
                Thread.currentThread().setName(SCAN_NAME + "-" + Thread.currentThread().getId() + ":ChannelScanMonitor");

                try {
                    completeLatch.await();
                } catch (InterruptedException e) {
                    stop = true;
                    logger.debug("The offline channel scan has been interrupted => ", e);
                }

                endTime = System.currentTimeMillis();

                if (!stop) {
                    if (totalChannels > 0) {
                        logger.info("Completed offline channel scan after {}ms. The aggregate average was {}ms per channel.", endTime - startTime, (endTime - startTime) / totalChannels);
                    } else {
                        logger.info("Completed offline channel scan after {}ms.", endTime - startTime);
                    }
                    complete = true;
                } else {
                    logger.info("Failed offline channel scan after {}ms. There are no available tuning devices at this time.", endTime - startTime);
                    complete = false;
                }

                executorService.shutdown();
                running.set(false);
            }
        });

        return logger.exit(true);
    }

    /**
     * Call this method to block until the channel scan has completed.
     *
     * @throws InterruptedException Thrown if the current thread is interrupted.
     */
    public void blockUntilComplete() throws InterruptedException {
        logger.entry();

        if (completeLatch != null) {
            completeLatch.await();
        }

        running.set(false);

        logger.exit();
    }

    /**
     * Stop the current channel scan if it is in progress.
     * <p/>
     * This method will not wait for the scan completely stop before returning.
     */
    public synchronized void stop() {
        stop = true;
        if (executorService != null) {
            executorService.shutdown();
        }

        running.set(false);
    }
}
