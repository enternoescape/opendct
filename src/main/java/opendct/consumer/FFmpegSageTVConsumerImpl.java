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

package opendct.consumer;

import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.buffers.FFmpegCircularBuffer;
import opendct.consumer.upload.NIOSageTVUploadID;
import opendct.video.ffmpeg.FFmpegConfig;
import opendct.video.ffmpeg.FFmpegUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.avformat.*;
import org.bytedeco.javacpp.avformat.AVIOInterruptCB.Callback_Pointer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;

public class FFmpegSageTVConsumerImpl implements SageTVConsumer {
    private final Logger logger = LogManager.getLogger(FFmpegSageTVConsumerImpl.class);

    private final boolean acceptsUploadID = FFmpegConfig.getUploadIdEnabled();

    // We must have at a minimum a 5 MB buffer plus 1MB to catch up. This ensures that if
    // someone changes this setting to a lower value, it will be overridden.
    private final int circularBufferSize = FFmpegConfig.getCircularBufferSize();

    // This is the smallest probe size allowed.
    private final long minProbeSize = FFmpegConfig.getMinProbeSize();

    // This is the smallest probe duration allowed.
    private final long minAnalyzeDuration = FFmpegConfig.getMinAnalyseDuration();

    // This is the largest probe size allowed. 5MB is the minimum allowed value.
    private final long maxProbeSize = (circularBufferSize * 3) - 1123474;

    // This is the largest analyze duration allowed. 5,000,000 is the minimum allowed value.
    private final long maxAnalyzeDuration = FFmpegConfig.getMaxAnalyseDuration();

    // This value cannot go any lower than 65536. Lower values result in stream corruption when the
    // RTP packets are larger than the buffer size.
    private final int RW_BUFFER_SIZE = FFmpegConfig.getRwBufferSize();

    // This is the smallest amount of data that will be transferred to the SageTV server.
    private final int minUploadIDTransfer = FFmpegConfig.getMinUploadIdTransferSize(RW_BUFFER_SIZE);

    // This is the smallest amount of data that will be flushed to the SageTV server.
    private final int minDirectFlush = FFmpegConfig.getMinDirectFlush();

    private final int ffmpegThreadPriority = FFmpegConfig.getThreadPriority();

    // Atomic because long values take two clocks just to store in 32-bit. We could get incomplete
    // values otherwise. Don't ever forget to set this value and increment it correctly. This is
    // crucial to playback in SageTV.
    private AtomicLong bytesStreamed = new AtomicLong(0);
    private long bytesFlushCounter = 0;

    private FileChannel currentFile = null;
    private FileOutputStream currentFileOutputStream = null;
    private FileOutputStream switchFileOutputStream = null;
    private String currentRecordingFilename = null;
    private String switchRecordingFilename = null;
    private int currentUploadID = -1;
    private int switchUploadID = -1;
    private String currentRecordingQuality = null;
    private int desiredProgram = -1;
    private String tunedChannel = "";

    private AtomicBoolean running = new AtomicBoolean(false);
    private long stvRecordBufferSize = 0;
    private AtomicLong stvRecordBufferPos = new AtomicLong(0);

    private final Object switchMonitor = new Object();
    private boolean switchFile = false;
    private boolean switchNextWrite = false;
    private int switchBytesPassed = 0;
    private boolean uploadEnabled = false;
    private boolean consumeToNull = false;

    private ByteBuffer streamBuffer = ByteBuffer.allocateDirect(RW_BUFFER_SIZE + minUploadIDTransfer);
    private FFmpegCircularBuffer seekableBuffer = new FFmpegCircularBuffer(circularBufferSize);

    private NIOSageTVUploadID nioSageTVUploadID = null;

    private int uploadIDPort = FFmpegConfig.getUploadIdPort();
    private SocketAddress uploadIDSocket = null;

    private String stateMessage = "Detecting stream...";
    private boolean stalled = false;
    private boolean streaming = false;
    private final Object streamingMonitor = new Object();
    private long initBufferedData = 1048576;

    private static ConcurrentHashMap<Pointer, FFmpegSageTVConsumerImpl> instanceMap = new ConcurrentHashMap<Pointer, FFmpegSageTVConsumerImpl>();
    private static final AtomicLong callbackAddress = new AtomicLong(0);

    public static final String FFMPEG_INIT_INTERRUPTED = "FFmpeg initialization was interrupted.";
    public static final int NO_STREAM_IDX = -1;
    public static final String TRYING_AGAIN = " Trying again with an extended probe.";

    private volatile boolean ffmpegInterrupted;
    private Pointer opaquePointer;
    private AVFormatContext avfCtxInput;
    private AVFormatContext avfCtxOutput;
    private AVIOContext avioCtxInput;
    private AVIOContext avioCtxOutput;
    private AVIOInterruptCB interruptCB;
    private int[] streamMap;

    static {
        FFmpegUtil.initAll();
    }

    public void run() {
        logger.entry();
        if (running.getAndSet(true)) {
            logger.error("FFmpeg consumer is already running.");
            return;
        }

        logger.info("FFmpeg consumer thread is now running.");

        stateMessage = "Opening file...";
        stalled = false;
        streaming = false;

        logger.debug("Thread priority is {}.", ffmpegThreadPriority);
        Thread.currentThread().setPriority(ffmpegThreadPriority);

        opaquePointer = new Pointer(new TmpPointer());
        instanceMap.put(opaquePointer, this);

        interruptCB = new AVIOInterruptCB();
        interruptCB.callback(interruptCallback);
        interruptCB.opaque(opaquePointer);

        uploadEnabled = false;
        switchFile = false;

        try {
            avioCtxOutput = allocIoContext("output");

            if (currentUploadID > 0) {
                boolean ableToWrite = false;

                while (!ableToWrite) {

                    if (nioSageTVUploadID == null) {
                        nioSageTVUploadID = new NIOSageTVUploadID();
                    } else {
                        nioSageTVUploadID.reset();
                    }

                    boolean uploadIDConfigured = false;

                    try {
                        uploadIDConfigured = nioSageTVUploadID.startUpload(
                                uploadIDSocket, currentRecordingFilename, currentUploadID);

                        ableToWrite = true;
                    } catch (IOException e) {
                        logger.error("Unable to connect to SageTV server to start transfer via uploadID.");

                        stalled = true;
                        stateMessage = "ERROR: Unable to connect to SageTV server to start transfer via uploadID.";
                    }

                    if (!uploadIDConfigured) {

                        logger.error("FFmpeg consumer did not receive OK from SageTV server to start" +
                                        " uploading to the file '{}' via the upload id '{}'" +
                                        " using the socket '{}'.",
                                currentRecordingFilename, currentUploadID, uploadIDSocket);

                        if (currentRecordingFilename != null) {
                            logger.info("Attempting to write the file directly.");
                            try {
                                this.currentFileOutputStream = new FileOutputStream(currentRecordingFilename);
                                currentFile = currentFileOutputStream.getChannel();

                                ableToWrite = true;
                            } catch (FileNotFoundException e) {
                                logger.error("Unable to create the recording file '{}'.", currentRecordingFilename);

                                stalled = true;
                                stateMessage = "ERROR: Unable to create the recording file '" + currentRecordingFilename + "'.";
                            }
                        }
                    } else {
                        uploadEnabled = true;
                    }

                    if (isInterrupted()) {
                        throw new InterruptedIOException("Interrupted while attempting to start writing via upload id.");
                    }
                }
            } else if (currentFileOutputStream != null) {
                currentFile = currentFileOutputStream.getChannel();
            } else if (consumeToNull) {
                logger.debug("Consuming to a null output.");
            } else {
                throw new Exception("FFmpeg consumer does not have a file or UploadID to use.");
            }

            long startTime = System.currentTimeMillis();

            seekableBuffer.setNoWrap(true);
            stalled = false;
            stateMessage = "Detecting stream...";
            String error = initRemuxer();
            seekableBuffer.setNoWrap(false);

            if (error == null && logger.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                logger.debug("FFmpeg remux init success after {}ms.", endTime - startTime);
            } else if (error != null) {
                long endTime = System.currentTimeMillis();
                logger.error("FFmpeg remux init failed after {}ms.", endTime - startTime);
            }

            if (error != null) {
                if (!error.equals(FFMPEG_INIT_INTERRUPTED)) {
                    logger.error("FFmpeg remux failed: {}", error);

                    stateMessage = error;
                    stalled = true;
                    streaming = false;

                    synchronized (streamingMonitor) {
                        streamingMonitor.notifyAll();
                    }
                }
            } else {
                stateMessage = "Streaming...";
                stalled = false;
                streaming = true;

                // This will loop until the thread is interrupted.
                remuxRtpPackets();
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                logger.debug("FFmpeg consumer was interrupted => ", e);
            } else {
                logger.error("FFmpeg consumer was closed by an unexpected exception => ", e);
            }
        } finally {
            logger.info("FFmpeg consumer thread is stopping.");

            freeAndSetNull(avioCtxOutput);
            freeAndSetNullAttemptData();
            freeAndSetNull(avfCtxOutput);

            instanceMap.remove(opaquePointer);

            bytesStreamed.set(0);

            currentRecordingFilename = null;
            if (currentFile != null && currentFile.isOpen()) {
                try {
                    currentFile.close();
                } catch (IOException e) {
                    logger.debug("FFmpeg consumer created an exception while closing the current file => ", e);
                } finally {
                    currentFile = null;
                }
            }

            if (nioSageTVUploadID != null) {
                try {
                    nioSageTVUploadID.endUpload(true);
                } catch (IOException e) {
                    logger.debug("FFmpeg consumer created an exception while ending the current upload id session => ", e);
                } finally {
                    nioSageTVUploadID = null;
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Bytes available to be read from buffer = {}", seekableBuffer.readAvailable());
                logger.trace("Space available for writing in bytes = {}", seekableBuffer.writeAvailable());
            }

            seekableBuffer.clear();

            stateMessage = "Stopped.";
            streaming = false;
            stalled = true;

            logger.info("FFmpeg consumer thread has stopped.");
            running.set(false);
        }
    }

    private AVIOContext allocIoContext(String inputOutput) {
        Pointer ptr = av_mallocz(RW_BUFFER_SIZE + AV_INPUT_BUFFER_PADDING_SIZE);

        if (ptr.isNull()) {
            return null;
        }

        AVIOContext avioCtx = inputOutput.equals("input") ?
                avio_alloc_context(new BytePointer(ptr), RW_BUFFER_SIZE, 0, opaquePointer, readCallback, null, seekCallback) :
                avio_alloc_context(new BytePointer(ptr), RW_BUFFER_SIZE, AVIO_FLAG_WRITE, opaquePointer, null, writeCallback, null);

        if (avioCtx.isNull()) {
            throw new IllegalStateException("FFmpeg " + inputOutput + " AVIOContext could not be allocated.");
        }

        return avioCtx;
    }

    public void write(byte[] bytes, int offset, int length) throws IOException {
        seekableBuffer.write(bytes, offset, length);
    }

    public void setRecordBufferSize(long bufferSize) {
        this.stvRecordBufferSize = bufferSize;
    }

    public boolean canSwitch() {
        return true;
    }

    public boolean getIsRunning() {
        return running.get();
    }

    public void stopConsumer() {
        ffmpegInterrupted = true;
        seekableBuffer.close();
    }

    public long getBytesStreamed() {
        return bytesStreamed.get();
    }

    public boolean acceptsUploadID() {
        return acceptsUploadID;
    }

    public boolean acceptsFilename() {
        return true;
    }

    public void setEncodingQuality(String encodingQuality) {
        currentRecordingQuality = encodingQuality;
    }

    public void consumeToNull(boolean consumerToNull) {
        this.consumeToNull = consumerToNull;
    }

    public boolean consumeToUploadID(String filename, int uploadId, InetAddress inetAddress) {
        logger.entry(filename, uploadId, inetAddress);

        this.currentRecordingFilename = filename;
        this.currentUploadID = uploadId;

        uploadIDSocket = new InetSocketAddress(inetAddress, uploadIDPort);

        return logger.exit(true);
    }


    public boolean consumeToFilename(String filename) {
        logger.entry(filename);

        try {
            this.currentFileOutputStream = new FileOutputStream(filename);
            this.currentRecordingFilename = filename;
        } catch (FileNotFoundException e) {
            logger.error("Unable to create the recording file '{}'.", filename);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    public synchronized boolean switchStreamToUploadID(String filename, long bufferSize, int uploadId) {
        logger.entry(filename, bufferSize, uploadId);

        logger.info("SWITCH to '{}' via upload id '{}' was requested.", filename, uploadId);

        synchronized (switchMonitor) {
            this.switchUploadID = uploadId;
            this.switchRecordingFilename = filename;
            this.switchFile = true;

            while (switchFile && this.getIsRunning()) {
                try {
                    switchMonitor.wait(500);
                } catch (Exception e) {
                    break;
                }
            }
        }

        return logger.exit(false);
    }

    public boolean switchStreamToFilename(String filename, long bufferSize) {
        logger.entry(filename, bufferSize);

        logger.info("SWITCH to '{}' was requested.", filename);

        try {
            synchronized (switchMonitor) {
                this.switchFileOutputStream = new FileOutputStream(filename);
                this.switchRecordingFilename = filename;
                this.switchFile = true;

                while (switchFile && this.getIsRunning()) {
                    try {
                        switchMonitor.wait(500);
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Unable to create the recording file '{}'.", filename);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    public String getEncoderQuality() {
        return currentRecordingQuality;
    }

    public String getEncoderFilename() {
        return currentRecordingFilename;
    }

    public int getEncoderUploadID() {
        return currentUploadID;
    }

    public boolean isInterrupted() {
        boolean isInterrupted = Thread.currentThread().isInterrupted() || seekableBuffer.isClosed();
        if (isInterrupted) {
            ffmpegInterrupted = true;
        }
        return isInterrupted;
    }

    public class TmpPointer extends Pointer {
        TmpPointer() {
            address = callbackAddress.incrementAndGet();
        }
    }

    private static Callback_Pointer interruptCallback = new InterruptCallable();

    private static class InterruptCallable extends Callback_Pointer {
        @Override
        public int call(Pointer opaque) {
            FFmpegSageTVConsumerImpl consumer = instanceMap.get(opaque);

            if (consumer.ffmpegInterrupted) {
                consumer.logger.info("Interrupt callback is returning 1");
            }

            return consumer.ffmpegInterrupted ? 1 : 0;
        }
    }

    private static Seek_Pointer_long_int seekCallback = new SeekCallback();

    private static class SeekCallback extends Seek_Pointer_long_int {
        @Override
        public long call(Pointer opaque, long offset, int whence) {
            FFmpegSageTVConsumerImpl consumer = instanceMap.get(opaque);

            long returnValue = -1;

            try {
                returnValue = consumer.seekableBuffer.seek(whence, offset);
            } catch (Exception e) {
                consumer.logger.error("There was an unhandled exception while seeking => ", e);
            }

            return returnValue;
        }
    }

    private static Read_packet_Pointer_BytePointer_int readCallback = new ReadCallback();

    private static class ReadCallback extends Read_packet_Pointer_BytePointer_int {
        @Override
        public int call(Pointer opaque, BytePointer buf, int bufSize) {
            FFmpegSageTVConsumerImpl consumer = instanceMap.get(opaque);

            int nBytes = -1;

            try {
                ByteBuffer readBuffer = buf.position(0).limit(bufSize).asBuffer();

                nBytes = consumer.seekableBuffer.read(readBuffer);

            } catch (InterruptedException e) {
                consumer.ffmpegInterrupted = true;
                consumer.seekableBuffer.close();
                consumer.logger.debug("FFmpeg consumer was interrupted while reading by an exception => ", e);
            } catch (Exception e) {
                consumer.logger.error("FFmpeg consumer was closed while reading by an exception => ", e);
            }

            if (nBytes == -1) {
                consumer.logger.info("Returning AVERROR_EOF in readCallback.call()");
                return AVERROR_EOF;
            }

            return nBytes;
        }
    }

    private static Write_packet_Pointer_BytePointer_int writeCallback = new WriteCallback();

    private static class WriteCallback extends Write_packet_Pointer_BytePointer_int {
        @Override
        public int call(Pointer opaque, BytePointer bytePtr, int numBytesRequested) {
            FFmpegSageTVConsumerImpl consumer = instanceMap.get(opaque);

            int numBytesWritten = 0;

            numBytesWritten = consumer.writeBuffer(bytePtr, 0, numBytesRequested);

            if (consumer.logger.isTraceEnabled()) {
                consumer.logger.trace("writeCallback called to write {} bytes. Wrote {} bytes.", numBytesRequested, numBytesWritten);
            }

            if (numBytesWritten < 0) {
                consumer.logger.info("Returning AVERROR_EOF in writeCallback.call()");
                return AVERROR_EOF;
            }

            return numBytesWritten;
        }
    }

    private int writeBuffer(BytePointer bytePtr, int offset, int length) {
        logger.entry(offset, length);

        streamBuffer.put(bytePtr.position(offset).limit(length).asByteBuffer());

        int switchIndex = -1;

        if (switchFile) {
            switchBytesPassed += length;

            if (switchBytesPassed > RW_BUFFER_SIZE) {
                switchIndex = streamBuffer.position();
            } else if (length >= RW_BUFFER_SIZE) {
                switchNextWrite = true;
                switchIndex += 188;
            } else if (switchNextWrite && switchBytesPassed <= length) {
                switchIndex = streamBuffer.position();
                switchNextWrite = false;
            }
        } else {
            switchBytesPassed = 940;
        }

        // Placing data into the internal byte array as expected, will not increment any of the
        // counters or indexes within the ByteBuffer. The fastest way to fix this is to just
        // increment the position relative to the current position.
        //streamBuffer.position(streamBuffer.position() + length);

        try {
            if (uploadEnabled) {
                if (streamBuffer.position() < minUploadIDTransfer) {
                    return logger.exit(length);
                }

                streamBuffer.flip();

                if (!Thread.currentThread().isInterrupted()) {
                    if (switchFile) {

                        if (switchIndex > -1) {
                            synchronized (switchMonitor) {
                                int lastBytesToStream = 0;

                                if (switchIndex > streamBuffer.position()) {
                                    ByteBuffer lastWriteBuffer = streamBuffer.duplicate();
                                    lastWriteBuffer.limit(switchIndex - 1);
                                    streamBuffer.position(switchIndex);

                                    lastBytesToStream = lastWriteBuffer.remaining();

                                    if (stvRecordBufferSize > 0) {
                                        nioSageTVUploadID.uploadAutoBuffered(stvRecordBufferSize, lastWriteBuffer);
                                    } else {
                                        nioSageTVUploadID.uploadAutoIncrement(lastWriteBuffer);
                                    }
                                }

                                bytesStreamed.addAndGet(lastBytesToStream);

                                switchFile = false;
                                switchMonitor.notifyAll();

                                if (!nioSageTVUploadID.switchUpload(
                                        switchRecordingFilename, switchUploadID)) {

                                    logger.error("Consumer did not receive OK from SageTV server" +
                                            " to switch to the file '{}' via the upload id" +
                                            " '{}'.", switchRecordingFilename, switchUploadID);

                                } else {
                                    currentRecordingFilename = switchRecordingFilename;
                                    currentUploadID = switchUploadID;
                                    bytesStreamed.set(0);

                                    logger.info("SWITCH was successful.");
                                }
                            }
                        }
                    }

                    int bytesToStream = streamBuffer.remaining();
                    if (stvRecordBufferSize > 0) {
                        nioSageTVUploadID.uploadAutoBuffered(stvRecordBufferSize, streamBuffer);
                    } else {
                        nioSageTVUploadID.uploadAutoIncrement(streamBuffer);
                    }

                    if (bytesStreamed.addAndGet(bytesToStream) > initBufferedData) {
                        synchronized (streamingMonitor) {
                            streamingMonitor.notifyAll();
                        }
                    }
                }

                streamBuffer.clear();
            } else if (!consumeToNull) {

                streamBuffer.flip();

                if (switchFile) {
                    if (switchIndex > -1) {
                        synchronized (switchMonitor) {
                            if (switchIndex > streamBuffer.position()) {
                                ByteBuffer lastWriteBuffer = streamBuffer.duplicate();
                                lastWriteBuffer.limit(switchIndex - 1);
                                streamBuffer.position(switchIndex);

                                while (lastWriteBuffer.hasRemaining() && !Thread.currentThread().isInterrupted()) {
                                    int savedSize = currentFile.write(lastWriteBuffer);
                                    bytesStreamed.addAndGet(savedSize);

                                    if (stvRecordBufferSize > 0 &&
                                            stvRecordBufferPos.get() > stvRecordBufferSize) {

                                        currentFile.position(0);
                                    }
                                    stvRecordBufferPos.set(currentFile.position());
                                }
                            }

                            currentFile.force(true);
                            switchFile = false;
                            switchMonitor.notifyAll();

                            if (switchFileOutputStream != null) {
                                if (currentFile != null && currentFile.isOpen()) {
                                    try {
                                        currentFile.close();
                                    } catch (IOException e) {
                                        logger.error("Consumer created an exception" +
                                                " while closing the current file => {}", e);
                                    } finally {
                                        currentFile = null;
                                    }
                                }
                                currentFile = switchFileOutputStream.getChannel();
                                currentFileOutputStream = switchFileOutputStream;
                                currentRecordingFilename = switchRecordingFilename;
                                switchFileOutputStream = null;
                                bytesStreamed.set(0);

                                logger.info("SWITCH was successful.");
                            }
                        }
                    }
                }

                if (minDirectFlush != -1 && bytesFlushCounter >= minDirectFlush) {
                    bytesFlushCounter = 0;

                    File recordingFile = new File(currentRecordingFilename);

                    // If the file should have some data, but it doesn't, flush the data to disk
                    // to verify that the file in fact taking data.

                    if (bytesStreamed.get() > 0 && recordingFile.length() == 0) {
                        currentFileOutputStream.flush();
                    }

                    // According to many sources, if the file is deleted an IOException will not be
                    // thrown. This handles the possible scenario. This also means previously
                    // written data is now lost.

                    if (!recordingFile.exists() || (bytesStreamed.get() > 0 && recordingFile.length() == 0) ) {
                        try {
                            currentFile.close();
                        } catch (Exception e) {
                            logger.debug("Exception while closing missing file => ", e);
                        }

                        while (!isInterrupted()) {
                            try {
                                currentFileOutputStream = new FileOutputStream(currentRecordingFilename);
                                currentFile = currentFileOutputStream.getChannel();
                                bytesStreamed.set(0);
                                logger.warn("The file '{}' is missing and was re-created.",
                                        currentRecordingFilename);
                            } catch (Exception e) {
                                logger.error("The file '{}' is missing and cannot be re-created => ",
                                        currentRecordingFilename, e);

                                Thread.sleep(500);

                                // Continue to re-try until interrupted.
                            }
                        }
                    }
                }

                long currentBytes = 0;

                while (streamBuffer.hasRemaining()) {
                    int savedSize = currentFile.write(streamBuffer);

                    bytesFlushCounter += savedSize;
                    currentBytes = bytesStreamed.addAndGet(savedSize);

                    if (currentBytes > initBufferedData) {
                        synchronized (streamingMonitor) {
                            streamingMonitor.notifyAll();
                        }
                    }

                    if (stvRecordBufferSize > 0 && stvRecordBufferPos.get() >
                            stvRecordBufferSize) {

                        currentFile.position(0);
                    }
                    stvRecordBufferPos.set(currentFile.position());
                }

                streamBuffer.clear();
            } else {
                synchronized (streamingMonitor) {
                    streamingMonitor.notifyAll();
                }

                // Write to null.
                bytesStreamed.addAndGet(streamBuffer.position());
                streamBuffer.clear();
            }
        } catch (InterruptedIOException e) {
            logger.debug("Consumer was interrupted => ", e);

            // Setting the interrupt for FFmpeg or the JVM might crash.
            ffmpegInterrupted = true;
            seekableBuffer.close();
            return logger.exit(-1);
        } catch (IOException e) {
            logger.warn("Consumer created an IO exception => ", e);

            // Setting the interrupt for FFmpeg or the JVM might crash.
            ffmpegInterrupted = true;
            seekableBuffer.close();
            return logger.exit(-1);
        } catch (Exception e) {
            logger.error("Consumer created an unhandled exception => ", e);
        }

        return logger.exit(length);
    }

    private String initRemuxer() {

        // This can be anything ending with the same extension as the output file which I'm assuming
        // is .ts for now.
        String inputFilename = "rtp-output-as-input.ts";

        long dynamicProbeSize = minProbeSize;
        long dynamicAnalyzeDuration = minAnalyzeDuration;
        final long probeSizeLimit = maxProbeSize;
        final long analyzeDurationLimit = maxAnalyzeDuration;

        AVStream videoStream = null;
        AVStream audioStream = null;

        int preferredVideo = AVERROR_STREAM_NOT_FOUND;
        int preferredAudio = AVERROR_STREAM_NOT_FOUND;

        AVCodecContext audioCodecCtx = null;
        AVCodecContext videoCodecCtx = null;

        long startNanoTime = System.nanoTime();

        while (true) {

            // By adding 188 to the available bytes, we can be reasonably sure we will not return
            // here until the available data has increased.
            dynamicProbeSize = Math.max(dynamicProbeSize, seekableBuffer.readAvailable() + 188);
            dynamicProbeSize = Math.min(dynamicProbeSize, probeSizeLimit);

            dynamicAnalyzeDuration = Math.max(dynamicAnalyzeDuration, (System.nanoTime() - startNanoTime) / 1000L);
            dynamicAnalyzeDuration = Math.min(dynamicAnalyzeDuration, analyzeDurationLimit);

            //
            // A new input AVFormatContext must be created for each avformat_find_stream_info probe or the JVM will crash.
            // avformat_open_input seems to even use the probe size/max analyze duration values so they're set too
            // before the avformat_find_stream_info() call.
            // Although the AVIOContext does not need to be allocated/freed for each probe it is done so the counters
            // for bytes read, seeks done, etc are reset.
            //
            avioCtxInput = allocIoContext("input");
            avfCtxInput = avformat_alloc_context();

            avfCtxInput.pb(avioCtxInput);
            avfCtxInput.interrupt_callback(interruptCB);

            av_opt_set_int(avfCtxInput, "probesize", dynamicProbeSize, 0); // Must be set before avformat_open_input
            av_opt_set_int(avfCtxInput, "analyzeduration", dynamicAnalyzeDuration, 0); // Must be set before avformat_find_stream_info

            logger.debug("Calling avformat_open_input");

            int ret = avformat_open_input(avfCtxInput, inputFilename, null, null);
            if (ret != 0) {
                return "avformat_open_input returned error code " + ret;
            }

            if (isInterrupted()) {
                return FFMPEG_INIT_INTERRUPTED;
            }

            logger.info("Before avformat_find_stream_info() pos={} bytes_read={} seek_count={}. probesize: {} analyzeduration: {}.",
                    avioCtxInput.pos(), avioCtxInput.bytes_read(), avioCtxInput.seek_count(), dynamicProbeSize, dynamicAnalyzeDuration);
            ret = avformat_find_stream_info(avfCtxInput, (PointerPointer<AVDictionary>) null);
            logger.info("After avformat_find_stream_info() pos={} bytes_read={} seek_count={}. probesize: {} analyzeduration: {}.",
                    avioCtxInput.pos(), avioCtxInput.bytes_read(), avioCtxInput.seek_count(), dynamicProbeSize, dynamicAnalyzeDuration);

            if (isInterrupted()) {
                return FFMPEG_INIT_INTERRUPTED;
            }

            if (ret < 0) {
                String error = "avformat_find_stream_info() failed with error code " + -ret + ".";
                if (dynamicProbeSize == probeSizeLimit) {
                    return error;
                }
                logger.info(error + " Trying again with more data.");

                freeAndSetNullAttemptData();
                continue;
            }

            // While we haven't seen all streams for the desired program and we haven't exhausted our attempts, try again...
            if (!findAllStreamsForDesiredProgram(avfCtxInput) && dynamicProbeSize != probeSizeLimit) {
                logger.info("Stream details unavailable for one or more streams. " + TRYING_AGAIN);
                freeAndSetNullAttemptData();
                continue;
            }

            preferredVideo = av_find_best_stream(avfCtxInput, AVMEDIA_TYPE_VIDEO, NO_STREAM_IDX, NO_STREAM_IDX, (PointerPointer<AVCodec>) null, 0);

            if (preferredVideo != AVERROR_STREAM_NOT_FOUND) {
                videoCodecCtx = getCodecContext(avfCtxInput.streams(preferredVideo));
            }

            if (videoCodecCtx == null) {
                if (isInterrupted()) {
                    return FFMPEG_INIT_INTERRUPTED;
                }

                String error = "Could not find a video stream.";
                if (dynamicProbeSize == probeSizeLimit) {
                    return error;
                }
                logger.info(error + TRYING_AGAIN);

                freeAndSetNullAttemptData();
                continue;
            }

            if (isInterrupted()) {
                return FFMPEG_INIT_INTERRUPTED;
            }

            preferredAudio = findBestAudioStream(avfCtxInput);

            if (preferredAudio != AVERROR_STREAM_NOT_FOUND) {
                audioCodecCtx = getCodecContext(avfCtxInput.streams(preferredAudio));
            }

            if (audioCodecCtx == null) {
                if (isInterrupted()) {
                    return FFMPEG_INIT_INTERRUPTED;
                }

                String error = "Could not find an audio stream.";
                if (dynamicProbeSize == probeSizeLimit) {
                    return error;
                }
                logger.info(error + TRYING_AGAIN);

                freeAndSetNullAttemptData();
                continue;
            }

            break;
        }

        avfCtxOutput = new AVFormatContext(null);

        String outputFilename = currentRecordingFilename != null ? currentRecordingFilename : "output.ts";
        logger.debug("Calling avformat_alloc_output_context2");

        int ret;
        if (outputFilename.endsWith(".mpg")) {
            ret = avformat_alloc_output_context2(avfCtxOutput, null, "vob", null/*outputFilename*/);
        } else {
            ret = avformat_alloc_output_context2(avfCtxOutput, null, null, "output.ts"/*outputFilename*/);
        }

        if (ret < 0) {
            return "avformat_alloc_output_context2 returned error code " + ret;
        }

        if ((videoStream = addStreamToContext(avfCtxOutput, videoCodecCtx)) == null) {
            return "Could not find a video stream";
        }

        if ((audioStream = addStreamToContext(avfCtxOutput, audioCodecCtx)) == null) {
            return "Could not find an audio stream";
        }

        int numInputStreams = avfCtxInput.nb_streams();

        streamMap = new int[numInputStreams];
        Arrays.fill(streamMap, NO_STREAM_IDX);

        streamMap[preferredVideo] = videoStream.id();
        streamMap[preferredAudio] = audioStream.id();

        StringBuilder buf = new StringBuilder(2000);
        FFmpegUtil.dumpFormat(buf, avfCtxInput, 0, inputFilename, /*isOutput*/false, desiredProgram);
        if (isInterrupted()) {
            return FFMPEG_INIT_INTERRUPTED;
        }
        logger.info(buf.toString());

        for (int idx = 0; idx < numInputStreams; ++idx) {
            if (streamMap[idx] != NO_STREAM_IDX) {
                continue;
            }

            AVCodecContext codecCtx = getCodecContext(avfCtxInput.streams(idx));

            if (codecCtx != null) {
                AVStream avsOutput = addStreamToContext(avfCtxOutput, codecCtx);

                if (avsOutput != null) {
                    streamMap[idx] = avsOutput.id();
                }
            }
        }

        buf.setLength(0);
        FFmpegUtil.dumpFormat(buf, avfCtxOutput, 0, outputFilename, /*isOutput*/true, /*desiredProgram*/0);

        if (isInterrupted()) {
            return FFMPEG_INIT_INTERRUPTED;
        }
        logger.info(buf.toString());

        avfCtxOutput.pb(avioCtxOutput);

        logger.debug("Writing header");

        ret = avformat_write_header(avfCtxOutput, (PointerPointer<AVDictionary>) null);
        if (ret < 0) {
            return "Error " + ret + " occurred while writing header to output file: " + outputFilename;
        }

        initBufferedData = seekableBuffer.readAvailable();

        return null;
    }

    private boolean findAllStreamsForDesiredProgram(AVFormatContext ic) {
        int numPrograms = ic.nb_programs();

        for (int programIndex = 0; programIndex < numPrograms; programIndex++) {
            if ( ic.programs(programIndex).id() == desiredProgram) {
                AVProgram program = ic.programs(programIndex);
                IntPointer streamIndexArray = program.stream_index();
                int streamIndexArrayLength = program.nb_stream_indexes();
                boolean foundAllCodecs = true;

                for (int streamIndexArrayIndex = 0; streamIndexArrayIndex < streamIndexArrayLength; streamIndexArrayIndex++) {
                    int streamIndex = streamIndexArray.get(streamIndexArrayIndex);
                    AVStream st = ic.streams(streamIndex);
                    AVCodecContext avctx = st.codec();
                    int codecType = avctx.codec_type();
                    if (codecType == AVMEDIA_TYPE_AUDIO && (avctx.channels() == 0 || avctx.sample_rate() == 0)) {
                        logger.info("Audio stream " + streamIndex + " has no channels or no sample rate.");
                        foundAllCodecs = false;
                    }
                    else if (codecType == AVMEDIA_TYPE_VIDEO && (avctx.width() == 0 || avctx.height() == 0)) {
                        logger.info("Video stream " + streamIndex + " has no width or no height.");
                        foundAllCodecs = false;
                    }
                }

                return foundAllCodecs;
            }
        }

        return false;
    }
    private AVCodecContext getCodecContext(AVStream inputStream) {
        if (inputStream == null) {
            return null;
        }

        AVCodecContext codecCtxInput = inputStream.codec();

        if (codecCtxInput == null) {
            return null;
        }

        int codecType = codecCtxInput.codec_type();
        boolean isStreamValid =
                (codecType == AVMEDIA_TYPE_AUDIO || codecType == AVMEDIA_TYPE_VIDEO || codecType == AVMEDIA_TYPE_SUBTITLE) &&
                        ((codecType != AVMEDIA_TYPE_VIDEO || (codecCtxInput.width() != 0 && codecCtxInput.height() != 0)) &&
                                (codecType != AVMEDIA_TYPE_AUDIO || codecCtxInput.channels() != 0));

        return isStreamValid ? codecCtxInput : null;
    }

    private AVStream addStreamToContext(AVFormatContext outputContext, AVCodecContext codecCtxInput) {
        AVStream avsOutput = avformat_new_stream(outputContext, codecCtxInput.codec());

        if (avsOutput == null) {
            logger.error("Could not allocate stream");
            return null;
        }

        AVCodecContext codecCtxOutput = avsOutput.codec();

        if (avcodec_copy_context(codecCtxOutput, codecCtxInput) < 0) {
            logger.error("Failed to copy context from input to output stream codec context");
            return null;
        }

        //
        // This prevents FFmpeg messages like this: Ignoring attempt to set invalid timebase 1/0 for st:1
        //
        avsOutput.time_base(av_add_q(codecCtxInput.time_base(), av_make_q(0, 1)));

        codecCtxOutput.codec_tag(0);

        if ((outputContext.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
            codecCtxOutput.flags(codecCtxOutput.flags() | CODEC_FLAG_GLOBAL_HEADER);
        }

        avsOutput.id(outputContext.nb_streams() - 1);

        return avsOutput;
    }

    private int findBestAudioStream(AVFormatContext inputContext) {
        int preferredAudio = NO_STREAM_IDX;
        int maxChannels = 0;
        int maxFrames = 0;
        boolean hasAudio = false;
        int numStreams = inputContext.nb_streams();

        for (int streamIndex = 0; streamIndex < numStreams; streamIndex++) {
            AVStream stream = inputContext.streams(streamIndex);
            AVCodecContext codec = stream.codec();
            // AVDictionaryEntry lang = av_dict_get( stream.metadata(), "language", (PointerPointer<AVDictionaryEntry>) null, 0);

            if (codec.codec_type() == AVMEDIA_TYPE_AUDIO) {
                hasAudio = true;
                int cChannels = codec.channels();
                int cFrames = stream.codec_info_nb_frames();

                if (cChannels > maxChannels || (cChannels == maxChannels && cFrames > maxFrames)) {
                    maxChannels = cChannels;
                    maxFrames = cFrames;
                    preferredAudio = streamIndex;
                }
            }
        }

        if (hasAudio && preferredAudio == NO_STREAM_IDX) {
            preferredAudio = av_find_best_stream(inputContext, AVMEDIA_TYPE_AUDIO, NO_STREAM_IDX, NO_STREAM_IDX, (PointerPointer<AVCodec>) null, 0);
        }

        return preferredAudio;
    }

    private void remuxRtpPackets() {
        AVPacket pkt = new AVPacket();

        long[] lastDtsByStreamIndex = new long[streamMap.length];
        Arrays.fill(lastDtsByStreamIndex, -1L);

        logger.debug("remuxing RTP packets");

        while (av_read_frame(avfCtxInput, pkt) >= 0) {
            boolean freePacket = true;
            logger.trace("Read a frame");

            int inputStreamIndex = pkt.stream_index();
            int outputStreamIndex;

            if (inputStreamIndex < streamMap.length && (outputStreamIndex = streamMap[inputStreamIndex]) != NO_STREAM_IDX) {
                //
                // The following error was logged from time to time:
                //    Application provided invalid, non monotonically increasing dts to muxer in stream 1: 1344770579 >= 1344770579
                // It causes av_interleaved_write_frame to return error code -22 which we log as:
                //    Error -22 while writing packet at input stream offset 420932.
                // So to avoid these two errors av_interleaved_write_frame is only called if the
                // decompression timestamp has changed.
                // ^Old solution for this situation. [js]
                //
                // Some streams will provide a dts value that's less than the last value; not just
                // equal to it. Sometimes they don't even have a dts value. The new way of handling
                // this situation is taking the last dts timestamp and adding one to it. This is
                // similar to what recent copies of FFmpeg will do when run from the command line.
                // This change was needed because sometimes when we discard these problem frames,
                // the result is video corruption. [js]
                //
                // Returned to old behavior since it turns out that missing these frames was not the
                // source of video corruption. The program will discard anything that's == to the
                // last dts and frames that do not have a dts value instead of just checking for the
                // same dts. [js]
                long dts = pkt.dts();
                boolean dtsChanged = true;

                if (lastDtsByStreamIndex[outputStreamIndex] == dts) {
                    dtsChanged = false;
                } else {
                    lastDtsByStreamIndex[outputStreamIndex] = dts;
                }

                if (dtsChanged) {
                    AVStream avStreamIn = avfCtxInput.streams(inputStreamIndex);
                    AVStream avStreamOut = avfCtxOutput.streams(outputStreamIndex);

                    if (logger.isTraceEnabled()) {
                        logPacket(avfCtxInput, pkt, "in");
                    }

                    long oldPos = pkt.pos();
                    AVRational timeBaseIn = avStreamIn.time_base();
                    AVRational timeBaseOut = avStreamOut.time_base();

                    pkt.pts(av_rescale_q_rnd(pkt.pts(), timeBaseIn, timeBaseOut, AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
                    pkt.dts(av_rescale_q_rnd(pkt.dts(), timeBaseIn, timeBaseOut, AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));
                    pkt.duration((int) av_rescale_q(pkt.duration(), timeBaseIn, timeBaseOut));
                    pkt.stream_index(outputStreamIndex);
                    pkt.pos(-1);

                    if (logger.isTraceEnabled()) {
                        logPacket(avfCtxOutput, pkt, "out");
                    }

                    int ret = av_interleaved_write_frame(avfCtxOutput, pkt);

                    freePacket = false; // av_interleaved_write_frame has taken ownership of this packet so don't free it below.

                    if (ret != 0) {
                        logger.error("Error {} while writing packet at input stream offset {}.", ret, oldPos);
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Read frame with dts <= last frame. Skipping this frame. dts = {}", dts);
                    }
                }
            }

            if (freePacket) {
                av_free_packet(pkt); // This call also sets the pkt.data(null) and pkt.size(0)
            }
        }

        av_write_trailer(avfCtxOutput);
    }

    private void logPacket(AVFormatContext fmt_ctx, AVPacket pkt, String tag) {
        AVRational tb = fmt_ctx.streams(pkt.stream_index()).time_base();

        logger.trace(String.format("%s: pts:%s pts_time:%s dts:%s dts_time:%s duration:%s duration_time:%s stream_index:%d",
                tag,
                av_ts2str(pkt.pts()), av_ts2timestr(pkt.pts(), tb),
                av_ts2str(pkt.dts()), av_ts2timestr(pkt.dts(), tb),
                av_ts2str(pkt.duration()), av_ts2timestr(pkt.duration(), tb),
                pkt.stream_index()));
    }

    private String av_ts2str(long pts) {
        return pts == AV_NOPTS_VALUE ? "NOPTS" : Long.toString(pts);
    }

    private String av_ts2timestr(long pts, AVRational tb) {
        return pts == AV_NOPTS_VALUE ? "NOPTS" : String.format("%.6g", av_q2d(tb) * pts);
    }

    private void freeAndSetNullAttemptData() {
        if (avfCtxInput != null) {
            freeAndSetNull(avioCtxInput);
            avformat_close_input(avfCtxInput); // This call already sets avfCtxInput's pointer to null
            freeAndSetNull(avfCtxInput);
        }
    }

    private void freeAndSetNull(AVIOContext avioCtx) {
        if (avioCtx != null && !avioCtx.isNull()) {
            if (avioCtx.buffer() != null) {
                av_free(avioCtx.buffer());
                avioCtx.buffer(null);
            }
            av_free(avioCtx);
            avioCtx.setNull();
        }
    }

    private void freeAndSetNull(AVFormatContext avfCtx) {
        if (avfCtx != null && !avfCtx.isNull()) {
            int numStreams = avfCtx.nb_streams();
            for (int idx = 0; idx < numStreams; ++idx) {
                avcodec_close(avfCtx.streams(idx).codec());
            }
            avformat_free_context(avfCtx);
            avfCtx.setNull();
        }
    }

    public void setProgram(int program) {
        desiredProgram = program;
    }

    public int getProgram() {
        return desiredProgram;
    }

    public String getChannel() {
        return tunedChannel;
    }

    public void setChannel(String tunedChannel) {
        this.tunedChannel = tunedChannel;
    }

    @Override
    public boolean isStalled() {
        return stalled;
    }

    @Override
    public String stateMessage() {
        return stateMessage;
    }

    @Override
    public boolean isStreaming(long timeout) {
        synchronized (streamingMonitor) {
            try {
                streamingMonitor.wait(timeout);
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for consumer to start streaming.");
            }
        }

        return streaming;
    }

    @Override
    public DeviceOption[] getOptions() {
        return FFmpegConfig.getFFmpegOptions();
    }

    @Override
    public void setOptions(DeviceOption... deviceOptions) throws DeviceOptionException {
        FFmpegConfig.setOptions(deviceOptions);
    }
}