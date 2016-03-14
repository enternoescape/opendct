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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SageTVTuningMonitor {
    private final static Logger logger = LogManager.getLogger(SageTVTuningMonitor.class);

    private static final ReentrantReadWriteLock queueLock = new ReentrantReadWriteLock(true);

    private static Thread monitorThread;
    private static final HashMap<String, MonitoredRecording> recordingQueue = new HashMap<>();


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

    public static void monitorRecording(CaptureDevice captureDevice, String channel, String encodingQuality, long bufferSize) {

        Thread checkThread = monitorThread;
        if (checkThread == null || !checkThread.isAlive()) {
            logger.debug("Tuning monitor is not running." +
                    " This recording will not re-tune automatically.");
            return;
        }

        queueLock.writeLock().lock();

        try {
            MonitoredRecording newRecording = new MonitoredRecording(captureDevice, channel, encodingQuality, bufferSize, -1, null);
            recordingQueue.put(captureDevice.getEncoderName(), newRecording);
        } catch (Throwable e) {
            logger.error("Unexpected exception while tuning '{}' => ",
                    captureDevice.getEncoderName(), e);
        } finally {
            queueLock.writeLock().unlock();
        }
    }

    public static void monitorRecording(CaptureDevice captureDevice, String channel, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {

        Thread checkThread = monitorThread;
        if (checkThread == null || !checkThread.isAlive()) {
            logger.debug("Tuning monitor is not running." +
                    " This recording will not re-tune automatically.");
            return;
        }

        queueLock.writeLock().lock();

        try {
            MonitoredRecording newRecording = new MonitoredRecording(captureDevice, channel, encodingQuality, bufferSize, uploadID, remoteAddress);
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
        protected Thread retuneThread = null;

        protected long nextCheck = System.currentTimeMillis() + 5000;
        protected long lastRecordedBytes = -1;
        protected long lastProducedPackets = -1;
        protected final CaptureDevice captureDevice;
        protected final String channel;
        protected final String encodingQuality;
        protected final long bufferSize;
        protected final int uploadID;
        protected final InetAddress remoteAddress;

        public MonitoredRecording(CaptureDevice captureDevice, String channel, String encodingQuality, long bufferSize, int uploadID, InetAddress remoteAddress) {
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
                        MonitoredRecording recording = entry.getValue();

                        // This keeps the monitoring from holding up new tuning requests.
                        if (queueLock.hasQueuedThreads()) {
                            break;
                        }

                        if (recording.nextCheck > currentTime) {
                            continue;
                        }

                        long producedPackets = recording.captureDevice.getProducedPackets();
                        long recordedBytes = recording.captureDevice.getRecordedBytes();

                        if (recording.lastProducedPackets == producedPackets &&
                                recording.lastRecordedBytes == recordedBytes) {

                            final CaptureDevice captureDevice = recording.captureDevice;
                            final String channel = recording.channel;
                            final String encodingQuality = recording.encodingQuality;
                            final long bufferSize = recording.bufferSize;
                            final int uploadID = recording.uploadID;
                            final InetAddress remoteAddress = recording.remoteAddress;
                            final String filename = captureDevice.getRecordFilename();

                            if (filename == null) {
                                // Wait longer since it's likely this capture device will be removed
                                // soon and we don't want to flood the log with this error.
                                recording.nextCheck = System.currentTimeMillis() + 15000;
                                logger.error("Unable to re-tune because there isn't a filename.");
                                continue;
                            }

                            // This keeps the monitoring from holding up new tuning request and
                            // potentially re-tuning when a tuner is changing channels anyway.
                            if (queueLock.hasQueuedThreads()) {
                                break;
                            }

                            // The last re-tune request is still in progress.
                            if (recording.retuneThread != null && recording.retuneThread.isAlive()) {
                                continue;
                            }

                            recording.retuneThread = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    boolean tuned = false;

                                    if (uploadID > 0) {
                                        tuned = captureDevice.startEncoding(channel, filename, encodingQuality, bufferSize, uploadID, remoteAddress);
                                    } else {
                                        tuned = captureDevice.startEncoding(channel, filename, encodingQuality, bufferSize);
                                    }

                                    // If the channel still won't tune in, start over.
                                    if (!tuned) {
                                        String sFilename = captureDevice.getRecordFilename();

                                        if (sFilename == null) {
                                            return;
                                        }

                                        captureDevice.stopEncoding();

                                        if (uploadID > 0) {
                                            captureDevice.startEncoding(channel, sFilename, encodingQuality, bufferSize, uploadID, remoteAddress);
                                        } else {
                                            captureDevice.startEncoding(channel, sFilename, encodingQuality, bufferSize);
                                        }
                                    }
                                }
                            });

                            recording.retuneThread.setName("Retune-" + recording.retuneThread.getId() + ":" + captureDevice.getEncoderName());
                            recording.retuneThread.start();
                        }

                        if (recording.lastProducedPackets <= 0 && producedPackets > 0) {
                            logger.info("'{}' produced first {} packets.", recording.captureDevice.getEncoderName(), producedPackets);
                        }

                        if (recording.lastRecordedBytes <= 0 && recordedBytes > 0) {
                            logger.info("'{}' recorded first {} bytes.", recording.captureDevice.getEncoderName(), recordedBytes);
                        }

                        recording.lastProducedPackets = producedPackets;
                        recording.lastRecordedBytes = recordedBytes;

                        recording.nextCheck = System.currentTimeMillis() + 5000;
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
