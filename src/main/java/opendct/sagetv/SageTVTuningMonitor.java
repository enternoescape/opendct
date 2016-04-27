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

package opendct.sagetv;

import opendct.capture.CaptureDevice;
import opendct.channel.CopyProtection;
import opendct.util.Util;
import opendct.video.java.VideoUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SageTVTuningMonitor {
    private final static Logger logger = LogManager.getLogger(SageTVTuningMonitor.class);

    private static final ReentrantReadWriteLock queueLock = new ReentrantReadWriteLock(true);

    private static Thread monitorThread;
    private static final Map<String, MonitoredRecording> recordingQueue = new HashMap<>();


    public synchronized static void startMonitor() {
        if (monitorThread != null && monitorThread.isAlive()) {
            return;
        }

        monitorThread = new Thread(new MonitorThread());
        monitorThread.setName("SageTVTuningMonitor-" + monitorThread.getId());
        monitorThread.start();
    }

    public synchronized static void stopMonitor() {
        if (monitorThread != null) {
            monitorThread.interrupt();
        }

        clearQueue();
    }

    public static void pauseMonitorRecording(CaptureDevice captureDevice) {
        queueLock.readLock().lock();

        try {
            MonitoredRecording monitoredRecording = recordingQueue.get(captureDevice.getEncoderName());

            if (monitoredRecording == null) {
                /*logger.warn("Unable to pause the capture device '{}' because it is not currently monitored.",
                        captureDevice.getEncoderName());*/
                return;
            }

            monitoredRecording.active = false;

            // If a re-tune is currently in progress, unfortunately we just have to wait it out.
            if (monitoredRecording.retuneThread != null && monitoredRecording.retuneThread.isAlive()) {
                monitoredRecording.retuneThread.interrupt();
                monitoredRecording.retuneThread.join(15000);

                if (monitoredRecording.retuneThread.isAlive()) {
                    logger.warn("Waited over 15 seconds for the re-tune thread to stop.");

                    monitoredRecording.retuneThread.interrupt();
                    monitoredRecording.retuneThread.join(30000);
                    if (monitoredRecording.retuneThread.isAlive()) {
                        logger.warn("Waited over 45 seconds for the re-tune thread to stop. It is still running.");
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("Unexpected exception while pausing '{}' => ",
                    captureDevice.getEncoderName(), e);
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public static void resumeMonitorRecording(CaptureDevice captureDevice) {
        queueLock.readLock().lock();

        try {
            MonitoredRecording monitoredRecording = recordingQueue.get(captureDevice.getEncoderName());

            if (monitoredRecording == null) {
                logger.warn("Unable to resume the capture device '{}' because it is not currently monitored.",
                        captureDevice.getEncoderName());
                return;
            }

            monitoredRecording.active = true;
        } catch (Throwable e) {
            logger.error("Unexpected exception while pausing '{}' => ",
                    captureDevice.getEncoderName(), e);
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public static void resumeMonitorRecording(CaptureDevice captureDevice, int uploadID, InetAddress remoteAddress) {
        queueLock.readLock().lock();

        try {
            MonitoredRecording monitoredRecording = recordingQueue.get(captureDevice.getEncoderName());

            if (monitoredRecording == null) {
                logger.warn("Unable to resume the capture device '{}' because it is not currently monitored.",
                        captureDevice.getEncoderName());
                return;
            }

            monitoredRecording.uploadID = uploadID;
            monitoredRecording.remoteAddress = remoteAddress;
            monitoredRecording.active = true;
        } catch (Throwable e) {
            logger.error("Unexpected exception while pausing '{}' => ",
                    captureDevice.getEncoderName(), e);
        } finally {
            queueLock.readLock().unlock();
        }
    }

    public static void monitorRecording(CaptureDevice captureDevice, String channel,
                                        String encodingQuality, long bufferSize) {

        Thread checkThread = monitorThread;
        if (checkThread == null || !checkThread.isAlive()) {
            logger.debug("Tuning monitor is not running." +
                    " This recording will not re-tune automatically.");
            return;
        }

        queueLock.writeLock().lock();

        try {
            MonitoredRecording newRecording = new MonitoredRecording(
                    captureDevice, channel, encodingQuality, bufferSize, -1, null);

            recordingQueue.put(captureDevice.getEncoderName(), newRecording);
        } catch (Throwable e) {
            logger.error("Unexpected exception while tuning '{}' => ",
                    captureDevice.getEncoderName(), e);
        } finally {
            queueLock.writeLock().unlock();
        }
    }

    public static void monitorRecording(CaptureDevice captureDevice, String channel,
                                        String encodingQuality, long bufferSize,
                                        int uploadID, InetAddress remoteAddress) {

        Thread checkThread = monitorThread;
        if (checkThread == null || !checkThread.isAlive()) {
            logger.debug("Tuning monitor is not running." +
                    " This recording will not re-tune automatically.");
            return;
        }

        queueLock.writeLock().lock();

        try {
            MonitoredRecording newRecording = new MonitoredRecording(
                    captureDevice, channel, encodingQuality, bufferSize, uploadID, remoteAddress);

            recordingQueue.put(captureDevice.getEncoderName(), newRecording);
        } catch (Throwable e) {
            logger.error("Unexpected exception while tuning '{}' => ",
                    captureDevice.getEncoderName(), e);
        } finally {
            queueLock.writeLock().unlock();
        }
    }

    public static void stopMonitorRecording(CaptureDevice captureDevice) {
        queueLock.writeLock().lock();

        try {
            recordingQueue.remove(captureDevice.getEncoderName());
        } catch (Throwable e) {
            logger.error("Unexpected exception while stopping '{}' => ",
                    captureDevice.getEncoderName(), e);
        } finally {
            queueLock.writeLock().unlock();
        }
    }

    public static void clearQueue() {
        queueLock.writeLock().lock();

        try {
            recordingQueue.clear();
        } finally {
            queueLock.writeLock().unlock();
        }
    }

    public static class MonitoredRecording {
        protected boolean active = true;
        protected Thread retuneThread = null;
        protected String filename = null;
        protected long lessThanRecordedBytes = 0;
        protected long lessThanProducedPackets = 0;
        protected CopyProtection copyProtection = CopyProtection.UNKNOWN;

        protected int checkDelay = 16000;
        // Wait a little longer than usual for the first tuning.
        protected long nextCheck = System.currentTimeMillis() + checkDelay + 4000;
        protected long lastRecordedBytes = -1;
        protected long lastProducedPackets = -1;
        protected int noRecordedBytes = 0;
        protected final CaptureDevice captureDevice;
        protected final String channel;
        protected final String encodingQuality;
        protected final long bufferSize;
        protected int uploadID;
        protected InetAddress remoteAddress;

        public MonitoredRecording(CaptureDevice captureDevice, String channel,
                                  String encodingQuality, long bufferSize,
                                  int uploadID, InetAddress remoteAddress) {

            this.captureDevice = captureDevice;
            this.channel = channel;
            this.encodingQuality = encodingQuality;
            this.bufferSize = bufferSize;
            this.uploadID = uploadID;
            this.remoteAddress = remoteAddress;
        }
    }

    public static class MonitorThread implements Runnable {

        @Override
        public void run() {
            logger.info("Tuning monitor thread started.");

            while(!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.info("Tuning monitor thread interrupted.");
                    return;
                }

                queueLock.writeLock().lock();

                try {
                    if (recordingQueue.size() == 0) {
                        continue;
                    }

                    long currentTime = System.currentTimeMillis();

                    for (Map.Entry<String, MonitoredRecording> entry : recordingQueue.entrySet()) {
                        final MonitoredRecording recording = entry.getValue();

                        // This keeps the monitoring from holding up new tuning requests.
                        if (queueLock.hasQueuedThreads()) {
                            break;
                        }

                        if (recording == null) {
                            continue;
                        } else if (!recording.active) {
                            recording.nextCheck = System.currentTimeMillis() + recording.checkDelay;
                            continue;
                        }

                        if (recording.copyProtection == CopyProtection.UNKNOWN ||
                                (recording.copyProtection == CopyProtection.NONE &&
                                recording.lastRecordedBytes < 104857600)) {

                            recording.copyProtection = recording.captureDevice.getCopyProtection();

                            if (recording.copyProtection == CopyProtection.COPY_NEVER ||
                                    recording.copyProtection == CopyProtection.COPY_ONCE) {

                                logger.info("The capture device has reported that the tuned" +
                                        " channel is {}, stopping monitoring immediately.",
                                        recording.copyProtection);

                                recordingQueue.remove(recording.captureDevice.getEncoderName());

                                // The capture device needs to stream the message because the user
                                // will not see anything if the bytes streamed doesn't increment.
                                if (recording.copyProtection == CopyProtection.COPY_ONCE) {
                                    recording.captureDevice.streamError(VideoUtil.COPY_ONCE_TS);
                                } else {
                                    recording.captureDevice.streamError(VideoUtil.COPY_NEVER_TS);
                                }

                                // We need to break here or the next iteration might fail. The
                                // alternative is to collect what needs to be removed and remove it
                                // after iterating over all of the content, but that may leave a
                                // window open for this action to remove a new tuning request which
                                // we really don't want to happen.
                                break;
                            }
                        }

                        if (recording.nextCheck > currentTime || recording.bufferSize > 0) {
                            continue;
                        }

                        long producedPackets = recording.captureDevice.getProducedPackets();
                        long recordedBytes = recording.captureDevice.getRecordedBytes();

                        if (recording.lastRecordedBytes == recordedBytes) {
                            logger.debug("The consumer appears to be stuck at {}," +
                                    " file system length {}.",
                                    recording.lastRecordedBytes,
                                    recording.filename != null ?
                                            new File(recording.filename).length() : -1);

                            recording.noRecordedBytes += 1;
                        } else {
                            recording.noRecordedBytes = 0;
                        }

                        if (recording.lastProducedPackets == producedPackets) {
                            logger.debug("The producer appears to be stuck at {}.",
                                    recording.lastProducedPackets);
                        }

                        // If both of these appear to be stuck, re-tune.
                        if ((recording.lastProducedPackets == producedPackets &&
                                recording.lastRecordedBytes == recordedBytes) ||
                                recording.noRecordedBytes > 2) {

                            // The last re-tune request is still in progress.
                            if (recording.retuneThread != null &&
                                    recording.retuneThread.isAlive()) {

                                recording.nextCheck = System.currentTimeMillis() + recording.checkDelay;
                                continue;
                            }

                            final boolean consumerStuck = recording.noRecordedBytes > 2;
                            final CaptureDevice captureDevice = recording.captureDevice;
                            final String channel = recording.channel;
                            final String encodingQuality = recording.encodingQuality;
                            final long bufferSize = recording.bufferSize;
                            final int uploadID = recording.uploadID;
                            final InetAddress remoteAddress = recording.remoteAddress;
                            recording.noRecordedBytes = 0;

                            // This keeps the monitoring from holding up new tuning request and
                            // potentially re-tuning when a tuner is changing channels anyway.
                            if (queueLock.hasQueuedThreads()) {
                                break;
                            }

                            recording.retuneThread = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    boolean tuned = false;
                                    String localFilename = captureDevice.getRecordFilename();

                                    logger.info("Current copy protection {}.",
                                            captureDevice.getCopyProtection());

                                    if (Util.isNullOrEmpty(localFilename) && recording.filename == null) {
                                        logger.error(
                                                "Unable to re-tune because there isn't a filename." +
                                                        " Stopping device monitoring."
                                        );

                                        stopMonitorRecording(captureDevice);

                                        return;
                                    }

                                    if (!Util.isNullOrEmpty(localFilename)) {
                                        recording.filename = localFilename;
                                    }

                                    if (!consumerStuck) {
                                        if (captureDevice.isLocked()) {
                                            if (uploadID > 0) {
                                                tuned = captureDevice.startEncoding(
                                                        channel, recording.filename,
                                                        encodingQuality, bufferSize,
                                                        uploadID, remoteAddress);
                                            } else {
                                                tuned = captureDevice.startEncoding(
                                                        channel, recording.filename,
                                                        encodingQuality, bufferSize);
                                            }
                                        } else {
                                            logger.info("Re-tune was cancelled because the capture" +
                                                    " device is no longer internally locked." +
                                                    " Stopping device monitoring.");

                                            stopMonitorRecording(captureDevice);

                                            return;
                                        }
                                    }

                                    // If the channel still won't tune in, start over.
                                    if (!tuned) {
                                        localFilename = captureDevice.getRecordFilename();

                                        if (Util.isNullOrEmpty(localFilename) &&
                                                recording.filename == null) {

                                            logger.error(
                                                    "Unable to tune because there isn't a filename." +
                                                            " Stopping device monitoring."
                                            );

                                            stopMonitorRecording(captureDevice);

                                            return;
                                        }

                                        if (!Util.isNullOrEmpty(localFilename)) {
                                            recording.filename = localFilename;
                                        }

                                        captureDevice.stopEncoding();

                                        if (captureDevice.isLocked()) {
                                            if (uploadID > 0) {
                                                captureDevice.startEncoding(
                                                        channel, recording.filename,
                                                        encodingQuality, bufferSize,
                                                        uploadID, remoteAddress);
                                            } else {
                                                captureDevice.startEncoding(
                                                        channel, recording.filename,
                                                        encodingQuality, bufferSize);
                                            }
                                        }
                                    }
                                }
                            });

                            recording.retuneThread.setName("Retune-" +
                                    recording.retuneThread.getId() + ":" +
                                    captureDevice.getEncoderName());

                            // This keeps the monitoring from holding up new tuning request and
                            // potentially re-tuning when a tuner is changing channels anyway.
                            if (queueLock.hasQueuedThreads()) {
                                break;
                            }

                            // Setting these to the current value will ensure that a log entry is
                            // created if data starts streaming again.
                            recording.lastProducedPackets = producedPackets;
                            recording.lessThanRecordedBytes = recordedBytes;

                            recording.retuneThread.start();
                        }

                        if (recording.lastProducedPackets <= recording.lessThanProducedPackets &&
                                producedPackets > 0) {

                            recording.lastProducedPackets = 0;
                            logger.info("'{}' produced first {} packets.",
                                    recording.captureDevice.getEncoderName(), producedPackets);
                        }

                        if (recording.lastRecordedBytes <= recording.lessThanRecordedBytes &&
                                recordedBytes > 0) {

                            recording.lessThanRecordedBytes = 0;
                            logger.info("'{}' recorded first {} bytes.",
                                    recording.captureDevice.getEncoderName(), recordedBytes);
                        }

                        recording.lastProducedPackets = producedPackets;
                        recording.lastRecordedBytes = recordedBytes;

                        recording.nextCheck = System.currentTimeMillis() + recording.checkDelay;
                    }
                } catch (Throwable e) {
                    logger.error("Unexpected exception while monitoring => ", e);
                } finally {
                    queueLock.writeLock().unlock();
                }
            }

            logger.info("Tuning monitor thread stopped.");
        }
    }
}
