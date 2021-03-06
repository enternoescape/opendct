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

package opendct.video.ffmpeg;

import opendct.consumer.buffers.FFmpegCircularBufferNIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.avformat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;

public class FFmpegContext {
    private final static Logger logger = LogManager.getLogger(FFmpegContext.class);

    private final static ReentrantReadWriteLock contextLock = new ReentrantReadWriteLock();
    private final static ReentrantReadWriteLock writeLock = new ReentrantReadWriteLock();

    private static FFmpegContext contextMap[] = new FFmpegContext[1025];
    private static int nextReadCallbackAddress = 1;

    private static FFmpegWriter writerMap[] = new FFmpegWriter[2049];
    private static int nextWriteCallbackAddress = 1;

    public final Pointer OPAQUE;
    protected Pointer writerOpaque;
    protected Pointer writerOpaque2;

    public final StringBuilder DUMP_BUFFER;
    public final FFmpegCircularBufferNIO SEEK_BUFFER;
    public final FFmpegStreamProcessor STREAM_PROCESSOR;
    public final int RW_BUFFER_SIZE;

    private final AVIOInterruptCB interruptCB;
    private boolean interrupted;
    private boolean disposed;

    // These are left protected because a getter and setter will just add latency.
    protected AVFormatContext avfCtxInput;
    protected AVFormatContext avfCtxOutput;
    protected AVFormatContext avfCtxOutput2;
    protected AVIOContext avioCtxInput;
    protected AVIOContext avioCtxOutput;
    protected AVIOContext avioCtxOutput2;

    protected int preferredVideo;
    protected int preferredAudio;
    protected long detectionBytes;

    // These are freed on avformat_close_input.
    protected AVStream videoOutStream;
    protected AVStream videoOutStream2;
    protected boolean secondaryStream;
    protected AVStream audioOutStream;
    protected AVCodecContext videoInCodecCtx;
    protected AVCodecContext audioInCodecCtx;
    protected AVRational videoFramerate;

    protected FFmpegProfile encodeProfile;
    protected Map<String, String> videoEncodeSettings;

    // Used for returning increasing probe sizes so we don't probe more than we need to probe.
    private final Object nativeSync = new Object();
    private int nativeFileProbeLimit;
    private int nativeLastProbeSize;
    private int nativeIncrement;

    public static final int FILE_MODE_MPEGTS = 0;
    public static final int FILE_MODE_NATIVE = 1;
    protected int inputFileMode;
    protected String inputFilename;
    protected String outputFilename;
    protected String outputFilename2;
    protected int desiredProgram;

    OutputStreamMap streamMap[];
    OutputStreamMap streamMap2[];

    static {
        FFmpegUtil.initAll();
    }

    /**
     * Create a new FFmpeg context.
     *
     * @param bufferSize The buffer size to used. This value will be overridden to 2246948 if it is
     *                   less than 2246948.
     * @param rwBufferSize The native buffer size to be used for reading and writing. This value
     *                     will be overridden to 32000 if it is less than 32000.
     */
    public FFmpegContext(int bufferSize, int rwBufferSize, FFmpegStreamProcessor streamProcessor) {
       this(
               new FFmpegCircularBufferNIO(bufferSize < 2246948 ? 2246948 : bufferSize),
               rwBufferSize,
               streamProcessor
       );
    }

    /**
     * Create a new FFmpeg context.
     *
     * @param seekBuffer An already initialized buffer or <i>null</i> if the native reading method
     *                   will be used.
     * @param rwBufferSize The native buffer size to be used for reading and writing. This value
     *                     will be overridden to 32000 if it is less than 32000.
     */
    public FFmpegContext(FFmpegCircularBufferNIO seekBuffer, int rwBufferSize, FFmpegStreamProcessor streamProcessor) {
        rwBufferSize = rwBufferSize < 32000 ? 32000 : rwBufferSize;
        disposed = false;

        SEEK_BUFFER = seekBuffer;
        STREAM_PROCESSOR = streamProcessor;
        OPAQUE = setContext();
        writerOpaque = null;
        writerOpaque2 = null;

        DUMP_BUFFER = new StringBuilder(2000);

        interrupted = false;
        interruptCB = new avformat.AVIOInterruptCB();
        interruptCB.callback(interruptCallback);
        interruptCB.opaque(OPAQUE);

        RW_BUFFER_SIZE = rwBufferSize;

        nativeFileProbeLimit = 0;
        nativeLastProbeSize = 0;
        nativeIncrement = 2246948;

        inputFileMode = SEEK_BUFFER != null ? FILE_MODE_MPEGTS : FILE_MODE_NATIVE;
        inputFilename = null;
        outputFilename = null;
        outputFilename2 = null;
        desiredProgram = 0;

        preferredVideo = -1;
        preferredAudio = -1;
        detectionBytes = 65536;
        videoOutStream = null;
        videoOutStream2 = null;
        secondaryStream = false;
        audioOutStream = null;
        videoInCodecCtx = null;
        audioInCodecCtx = null;
        videoFramerate = new AVRational();

        streamMap = new OutputStreamMap[0];
        streamMap2 = new OutputStreamMap[0];
        encodeProfile = null;
        videoEncodeSettings = new HashMap<>();
    }

    public static FFmpegContext getContext(Pointer opaque) {
        FFmpegContext returnValue;

        contextLock.readLock().lock();

        try {
            returnValue = contextMap[(int)opaque.address()];
        } finally {
            contextLock.readLock().unlock();
        }

        return returnValue;
    }

    private Pointer setContext() {
        Pointer opaque;

        contextLock.writeLock().lock();

        try {
            opaque = new Pointer(new FFmpegContextPointer(this));
        } finally {
            contextLock.writeLock().unlock();
        }

        return opaque;
    }

    public static FFmpegWriter getWriterContext(Pointer opaque) {
        FFmpegWriter returnValue;

        writeLock.readLock().lock();

        try {
            returnValue = writerMap[(int)opaque.address()];
        } finally {
            writeLock.readLock().unlock();
        }

        return returnValue;
    }

    public void setEncodeProfile(FFmpegProfile profile) {
        encodeProfile = profile;
    }

    private Pointer setWriterContext(FFmpegWriter writer) {
        Pointer opaque;

        writeLock.writeLock().lock();

        try {
            opaque = new Pointer(new FFmpegWriterPointer(writer));
        } finally {
            writeLock.writeLock().unlock();
        }

        return opaque;
    }

    public void removeWriterContext(Pointer opaque) {
        writeLock.writeLock().lock();

        try {
            writerMap[(int)opaque.address()] = null;
        } finally {
            writeLock.writeLock().unlock();
        }
    }

    private static long getFFmpegCallbackAddress(FFmpegContext context) {

        int startingIndex = nextReadCallbackAddress;

        while (contextMap[nextReadCallbackAddress] != null) {
            nextReadCallbackAddress++;

            if (nextReadCallbackAddress >= contextMap.length) {
                nextReadCallbackAddress = 1;
            }

            if (nextReadCallbackAddress == startingIndex) {
                FFmpegContext newContextMap[] = new FFmpegContext[contextMap.length * 2];
                System.arraycopy(contextMap, 0, newContextMap, 0, contextMap.length);
                contextMap = newContextMap;

                if (newContextMap.length > 4096) {
                    logger.warn("The FFmpeg context map is now {}.", newContextMap.length);
                }
            }
        }

        contextMap[nextReadCallbackAddress] = context;

        return nextReadCallbackAddress;
    }

    private class FFmpegContextPointer extends Pointer {
        public FFmpegContextPointer(FFmpegContext context) {
            address = getFFmpegCallbackAddress(context);
        }
    }

    private static long getFFmpegWriterCallbackAddress(FFmpegWriter context) {

        int startingIndex = nextWriteCallbackAddress;

        while (writerMap[nextWriteCallbackAddress] != null) {
            nextWriteCallbackAddress++;

            if (nextWriteCallbackAddress >= writerMap.length) {
                nextWriteCallbackAddress = 1;
            }

            if (nextWriteCallbackAddress == startingIndex) {
                FFmpegWriter newWriterMap[] = new FFmpegWriter[writerMap.length * 2];
                System.arraycopy(writerMap, 0, newWriterMap, 0, writerMap.length);
                writerMap = newWriterMap;
            }
        }

        writerMap[nextWriteCallbackAddress] = context;

        return nextWriteCallbackAddress;
    }

    private class FFmpegWriterPointer extends Pointer {
        public FFmpegWriterPointer(FFmpegWriter context) {
            address = getFFmpegWriterCallbackAddress(context);
        }
    }

    /**
     * Is this thread currently interrupted?
     *
     * @return <i>true</i> if the thread is interrupted or has been interrupted.
     */
    public boolean isInterrupted() {
        boolean isInterrupted = Thread.currentThread().isInterrupted() || SEEK_BUFFER.isClosed();
        if (isInterrupted) {
            interrupted = true;
        }
        return isInterrupted;
    }

    /**
     * Sets the FFmpeg interrupt flag to stop processing.
     */
    public void interrupt() {
        interrupted = true;
    }

    protected static final avformat.AVIOInterruptCB.Callback_Pointer interruptCallback = new InterruptCallable();

    protected static class InterruptCallable extends avformat.AVIOInterruptCB.Callback_Pointer {
        @Override
        public int call(Pointer opaque) {
            FFmpegContext context = getContext(opaque);

            if (context.interrupted) {
                logger.info("Interrupt callback is returning 1");
                return 1;
            }

            return 0;
        }
    }

    protected static Seek_Pointer_long_int seekCallback = new SeekCallback();

    protected static class SeekCallback extends Seek_Pointer_long_int {
        @Override
        public long call(Pointer opaque, long offset, int whence) {
            FFmpegContext context = getContext(opaque);

            long returnValue = -1;

            try {
                returnValue = context.SEEK_BUFFER.seek(whence, offset);
            } catch (Exception e) {
                logger.error("There was an exception while seeking => ", e);
            }

            return returnValue;
        }
    }

    protected static Read_packet_Pointer_BytePointer_int readCallback = new ReadCallback();

    protected long lastReadAddress = 0;
    protected int lastReadCapacity = 0;
    protected int minRead = 0;
    protected long readAddress = 0;
    protected ByteBuffer readBuffer = null;

    protected static class ReadCallback extends Read_packet_Pointer_BytePointer_int {
        @Override
        public int call(Pointer opaque, BytePointer buf, int bufSize) {

            opaque.address();
            FFmpegContext context = getContext(opaque);

            int nBytes = -1;

            if (!context.isInterrupted()) {
                try {
                    context.readAddress = buf.address();
                    if (context.readBuffer == null || context.readAddress != context.lastReadAddress || context.lastReadCapacity < bufSize) {
                        context.readBuffer = buf.position(0).limit(bufSize).asByteBuffer();
                        context.lastReadAddress = context.readAddress;
                        context.lastReadCapacity = bufSize;
                        context.minRead = bufSize / 16;
                    } else {
                        context.readBuffer.limit(bufSize).position(0);
                    }

                    if (!context.SEEK_BUFFER.isNoWrap()) {
                        context.SEEK_BUFFER.waitForBytes();

                        while (context.SEEK_BUFFER.readAvailable() < context.minRead  &&
                                !context.isInterrupted()) {

                            Thread.sleep(25);
                        }

                        nBytes = context.SEEK_BUFFER.read(context.readBuffer);
                    } else {
                        // Smaller chunks of data are ok for initialization.

                        // Limit the time spent here to 2000ms at most.
                        int passes = 80;

                        while (passes-- > 0 &&
                                context.SEEK_BUFFER.readAvailable() < context.minRead &&
                                !context.isInterrupted()) {

                            Thread.sleep(25);
                        }

                        // When the buffer is frozen, new data will never be added to the
                        // seekable part of the buffer which could cause it to stall out.
                        if (context.SEEK_BUFFER.readAvailable() > 0) {
                            nBytes = context.SEEK_BUFFER.read(context.readBuffer);
                        }
                    }

                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        context.interrupted = true;
                        context.SEEK_BUFFER.close();
                        logger.debug("FFmpeg consumer was interrupted while reading.");
                    } else {
                        logger.error("FFmpeg consumer was closed while reading by an exception => ", e);
                    }
                }
            }

            if (nBytes == -1) {
                logger.info("Returning AVERROR_EOF in readCallback.call()");
                return AVERROR_EOF;
            }

            return nBytes;
        }
    }

    protected static Write_packet_Pointer_BytePointer_int writeCallback = new WriteCallback();

    protected static class WriteCallback extends Write_packet_Pointer_BytePointer_int {

        @Override
        public int call(Pointer opaque, BytePointer buf, int bufSize) {
            FFmpegWriter context = getWriterContext(opaque);

            int numBytesWritten = 0;

            try {
                numBytesWritten = context.write(buf, bufSize);
            } catch (IOException e) {
                Logger logger = context.getLogger();
                if (logger != null) {
                    logger.error("'{}' experienced an IOException => ", context.getClass(), e);
                }
            } catch (Exception e) {
                Logger logger = context.getLogger();
                if (logger != null) {
                    logger.error("'{}' experienced an unhandled exception => ", context.getClass(), e);
                }
            }

            if (numBytesWritten < 0) {
                Logger logger = context.getLogger();
                if (logger != null) {
                    logger.info("Returning AVERROR_EOF in writeCallback.call()");
                }
                return AVERROR_EOF;
            }

            return numBytesWritten;
        }
    }

    public void setProbeData(String filename) {
        if (filename != null && (inputFilename == null || !inputFilename.equals(filename))) {
            long fileLength = new File(filename).length();
            nativeFileProbeLimit = fileLength > 41943040 ? 41943040 : (int) fileLength;
            nativeIncrement = 2246948;

            inputFilename = filename;
        }
    }

    public int getProbeAvailable() {
        switch (inputFileMode) {
            case FILE_MODE_MPEGTS:
                return SEEK_BUFFER.readAvailable();
            case FILE_MODE_NATIVE:
                synchronized (nativeSync) {
                    if (inputFilename != null) {
                        if (nativeLastProbeSize == nativeFileProbeLimit) {
                            return nativeLastProbeSize;
                        }

                        nativeLastProbeSize += nativeIncrement;

                        // The data available increment is increased with each call to this method
                        // so we will only read the same data up to 6 times instead of 20.
                        nativeIncrement *= 2;

                        // This makes sure we never return a number greater than the probe limit or
                        // a negative number.
                        nativeLastProbeSize = Math.max(
                                1123474,
                                Math.min(
                                        nativeLastProbeSize,
                                        nativeFileProbeLimit)
                        );

                        return nativeLastProbeSize;
                    }
                }

                return 0;
            default:
                return 0;
        }
    }

    public int getProbeMaxSize() {
        switch (inputFileMode) {
            case FILE_MODE_MPEGTS:
                return SEEK_BUFFER.getBufferMaxSize() - 1123474;
            case FILE_MODE_NATIVE:
                synchronized (nativeSync) {
                    if (inputFilename != null) {
                        return nativeFileProbeLimit;
                    }
                }

                return 0;
            default:
                return 0;
        }
    }

    /**
     * This is the number of bytes that were read during detection.
     *
     * @return The number of bytes read during detection.
     */
    public long getDetectionBytes() {
        return detectionBytes;
    }

    /**
     * Detect if we have enough stream data to start transcoding/remuxing.
     * <p/>
     * If needed, ensure that the desired program is set before running this method.
     *
     * @return <i>true</i> if all required streams are available. If <i>false</i> is returned, all
     *         allocated resources will already be de-allocated.
     */
    public boolean initTsStream(String filename) {
        if (SEEK_BUFFER == null) return false;
        inputFileMode = FILE_MODE_MPEGTS;

        int attempts = 0;
        String error[] = new String[1];

        while (!isInterrupted()) {
            try {
                if (!FFmpegStreamDetection.detectStreams(this, filename, error)) {
                    if (!isInterrupted()) {
                        logger.error("initTsStream: {}", error[0]);

                        // Clear the buffer in hopes the new data will work out better.
                        SEEK_BUFFER.clear();

                        // If this is our 3rd attempt, wait 10 seconds before doing anything.
                        if (attempts++ == 1) {
                            int wait = 10;
                            while (!isInterrupted() && wait-- > 0) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    return false;
                                }
                            }
                        } else if (attempts > 2) {
                            return false;
                        }

                        continue;
                    } else {
                        logger.debug("initTsStream: {}", error[0]);
                        return false;
                    }
                }
            } catch (FFmpegException e) {
                logger.error("initTsStream: {} => ", error[0], e);
                return false;
            }

            break;
        }

        return true;
    }

    /**
     * Detect if we have enough stream data to start transcoding/remuxing.
     * <p/>
     * If needed, ensure that the desired program is set before running this method.
     *
     * @param nativeFile The full path to the file to be opened.
     * @return <i>true</i> if all required streams are available. If <i>false</i> is returned, all
     *         allocated resources will already be de-allocated.
     */
    public boolean initNativeFile(String nativeFile) {
        String error[] = new String[1];
        inputFileMode = FILE_MODE_NATIVE;

        try {
            if (!FFmpegStreamDetection.detectStreams(this, nativeFile, error)) {
                if (!isInterrupted()) {
                    logger.error("initNativeFile: {}", error[0]);
                } else {
                    logger.debug("initNativeFile: {}", error[0]);
                }

                // This is a static file and nothing will change between passes, so stop here.
                return false;
            }
        } catch (FFmpegException e) {
            logger.error("initNativeFile: {} => ", error[0], e);
            return false;
        }

        return true;
    }

    /**
     * Create a new input AVIOContext assign it and the interrupt callback to a new AVFormatContext.
     * <p/>
     * If AVIOContext or AVFormatContext are already allocated, they will be de-allocated. This is
     * built in because these Contexts are constantly being allocated and de-allocated during stream
     * detection.
     * <p/>
     * The input filename is implicitly set to input-mpeg-ts.ts since this method is only intended
     * for the use of bringing data in from MPEG-TS streams.
     * <p/>
     * Calling this method will change the file input mode to MPEG-TS.
     *
     * @throws IllegalStateException Thrown if any of the contexts cannot be allocated. AVIOContext
     *                               and AVIOContext contexts are de-allocated on exception.
     */
    protected void allocInputIoTsFormatContext() throws FFmpegException {
        //deallocInputContext();

        avioCtxInput = allocIoContext(OPAQUE, readCallback, null, seekCallback, 0);

        if (avioCtxInput == null) {
            deallocInputContext();
            throw new FFmpegException("FFmpeg input AVIOContext could not be allocated.", -1);
        }

        avfCtxInput = avformat_alloc_context();

        if (avfCtxInput == null) {
            deallocInputContext();
            throw new FFmpegException("FFmpeg input AVFormatContext could not be allocated.", -1);
        }

        avfCtxInput.pb(avioCtxInput);
        avfCtxInput.interrupt_callback(interruptCB);

        inputFileMode = FILE_MODE_MPEGTS;
        inputFilename = "input-mpeg-ts.ts";
    }

    /**
     * Create a new input AVFormatContext using native file input and assigns the interrupt
     * callback to it.
     * <p/>
     * If AVIOContext or AVFormatContext are already allocated, they will be de-allocated. This is
     * built in because these contexts are constantly being allocated and de-allocated during stream
     * detection.
     * <p/>
     * Successfully calling this method will change the file input mode to native and the input
     * filename will be change to the provided filename.
     *
     * @throws IllegalStateException Thrown if any of the contexts cannot be allocated. AVIOContext
     *                               and AVIOContext contexts are de-allocated on exception.
     */
    protected void allocInputFormatNativeContext(String filename, AVDictionary dict) throws FFmpegException {
        //deallocInputContext();

        int ret;

        avfCtxInput = new AVFormatContext(null);

        ret = avformat_open_input(avfCtxInput, filename, null, dict);
        if (ret < 0) {
            deallocInputContext();
            throw new FFmpegException("Cannot open input file.", ret);
        }

        avfCtxInput.interrupt_callback(interruptCB);
        avioCtxInput = avfCtxInput.pb();

        setProbeData(filename);
        inputFileMode = FILE_MODE_NATIVE;
        inputFilename = filename;
    }

    /**
     * Dump the input format information for this context into the log.
     */
    public void dumpInputFormat() {
        DUMP_BUFFER.setLength(0);
        FFmpegUtil.dumpFormat(DUMP_BUFFER, avfCtxInput, 0, inputFilename, false, desiredProgram);
        logger.info("Primary: {}", DUMP_BUFFER.toString());
    }

    /**
     * Dump the output format information for this context into the log.
     */
    public void dumpOutputFormat() {
        DUMP_BUFFER.setLength(0);
        FFmpegUtil.dumpFormat(DUMP_BUFFER, avfCtxOutput, 0, outputFilename, true, desiredProgram);
        logger.info("Primary: {}", DUMP_BUFFER.toString());
    }

    /**
     * Dump the secondary output format information for this context into the log.
     *
     * @param comment Replaced the filename with a comment. Leave blank to just display the
     *                filename.
     */
    public void dumpOutputFormat2(String comment) {
        DUMP_BUFFER.setLength(0);
        FFmpegUtil.dumpFormat(DUMP_BUFFER, avfCtxOutput2, 0, comment, true, desiredProgram);
        logger.info("Secondary: {}", DUMP_BUFFER.toString());
    }

    /**
     * Create a new output AVFormatContext and initialize it for a specific container based on the
     * provided filename. This method only allows for MPEG-TS (default) and MPEG-PS (*.mpg) if the
     * codec ID is compatible.
     * <p/>
     * If AVFormatContext is already allocated, it must be de-allocated before using this method.
     *
     * @param filename The filename to use to determine the output format. This value can be
     *                 <i>null</i> if the default of MPEG-TS is desired.
     * @param codecId The codec ID to be used to determine if an MPEG-PS container can be used.
     * @throws IllegalStateException Thrown if any of the contexts cannot be allocated. AVIOContext
     *                               context is de-allocated on exception.
     */
    public void allocAvfContainerOutputContext(String filename, int codecId) throws FFmpegException {
        avfCtxOutput = new AVFormatContext(null);

        boolean isMpeg = codecId == AV_CODEC_ID_MPEG1VIDEO || codecId == AV_CODEC_ID_MPEG2VIDEO;

        outputFilename = filename != null ? filename : "output.ts";

        logger.debug("Calling avformat_alloc_output_context2");

        int ret;
        if (outputFilename.endsWith(".mpg") && isMpeg) {
            ret = avformat_alloc_output_context2(avfCtxOutput, null, "vob", null);
        } else {
            ret = avformat_alloc_output_context2(avfCtxOutput, null, null, "output.ts");
        }

        if (ret < 0) {
            deallocOutputContext();
            outputFilename = null;
            throw new FFmpegException("avformat_alloc_output_context2 returned error code ", ret);
        }
    }

    /**
     * Create a new secondary output AVFormatContext and initialize it for a specific container
     * based on the provided filename. This method only allows for MPEG-TS (default) and MPEG-PS
     * (*.mpg) if the codec ID is compatible.
     * <p/>
     * If AVFormatContext is already allocated, it must be de-allocated before using this method.
     *
     * @param filename2 The filename to use to determine the output format. This value can be
     *                 <i>null</i> if the default of MPEG-TS is desired.
     * @param codecId The codec ID to be used to determine if an MPEG-PS container can be used.
     * @throws IllegalStateException Thrown if any of the contexts cannot be allocated. AVIOContext
     *                               context is de-allocated on exception.
     */
    public void allocAvfContainerOutputContext2(String filename2, int codecId) throws FFmpegException {
        avfCtxOutput2 = new AVFormatContext(null);

        boolean isMpeg = codecId == AV_CODEC_ID_MPEG1VIDEO || codecId == AV_CODEC_ID_MPEG2VIDEO;

        outputFilename2 = filename2 != null ? filename2 : "output2.ts";

        logger.debug("Calling avformat_alloc_output_context2");

        int ret;
        if (outputFilename2.endsWith(".mpg") && isMpeg) {
            ret = avformat_alloc_output_context2(avfCtxOutput2, null, "vob", null);
        } else {
            ret = avformat_alloc_output_context2(avfCtxOutput2, null, null, "output2.ts");
        }

        if (ret < 0) {
            deallocOutputContext2();
            outputFilename2 = null;
            throw new FFmpegException("avformat_alloc_output_context2 returned error code ", ret);
        }
    }

    /**
     * Allocates output AVIOContext and assigns it a write callback.
     * <p/>
     * The output AVFormatContext context must already be allocated before calling the method. If
     * the output AVIOContext is already allocated, it must be de-allocated before calling this method.
     *
     * @param writer The writer implementation to be used.
     * @throws IllegalStateException Thrown if any of the contexts cannot be allocated or output
     *                               AVFContext context is not already allocated. AVIOContext
     *                               context is de-allocated on exception.
     */
    public void allocIoOutputContext(FFmpegWriter writer) throws FFmpegException {

        if (writerOpaque != null) {
            removeWriterContext(writerOpaque);
        }

        if (avfCtxOutput == null) {
            throw new FFmpegException("FFmpeg output AVFContext must already be allocated.", -1);
        }

        Pointer opaque = setWriterContext(writer);
        avioCtxOutput = allocIoContext(opaque, null, writeCallback, null, 1);

        if (avioCtxOutput == null) {
            removeWriterContext(opaque);
            throw new FFmpegException("FFmpeg output AVIOContext could not be allocated.", -1);
        }

        writerOpaque = opaque;

        avfCtxOutput.pb(avioCtxOutput);
        avfCtxOutput.interrupt_callback(interruptCB);
    }

    /**
     * Allocates secondary output AVIOContext and assigns it a write callback.
     * <p/>
     * The output AVFormatContext context must already be allocated before calling the method. If
     * the output AVIOContext is already allocated, it must be de-allocated before calling this method.
     *
     * @param writer2 The writer implementation to be used.
     * @throws IllegalStateException Thrown if any of the contexts cannot be allocated or output
     *                               AVFContext context is not already allocated. AVIOContext
     *                               context is de-allocated on exception.
     */
    public void allocIoOutputContext2(FFmpegWriter writer2) throws FFmpegException {

        if (writerOpaque2 != null) {
            removeWriterContext(writerOpaque2);
        }

        if (avfCtxOutput2 == null) {
            throw new FFmpegException("FFmpeg output AVFContext must already be allocated.", -1);
        }

        Pointer opaque = setWriterContext(writer2);
        avioCtxOutput2 = allocIoContext(opaque, null, writeCallback, null, 2);

        if (avioCtxOutput2 == null) {
            removeWriterContext(opaque);
            throw new FFmpegException("FFmpeg output AVIOContext could not be allocated.", -1);
        }

        writerOpaque2 = opaque;

        avfCtxOutput2.pb(avioCtxOutput2);
        avfCtxOutput2.interrupt_callback(interruptCB);
    }

    /**
     * Allocates a custom writer that will not be de-allocated by this context.
     * <p/>
     * When this AVFormatContext is de-allocated, don't forget to remove it from the writer HashMap
     * or it may never be garbage collected.
     *
     * @param writer The writer implementation.
     * @param avfOutput The pre-allocated output AVFormatContext to assign the callback.
     * @return The pointer to be used for the writer callback. Retain this pointer to remove it
     *         from the writer hashmap once it is no longer needed.
     * @throws FFmpegException Thrown if the context cannot be allocated. AVFormatContext will not
     *                         be changed.
     */
    public Pointer allocCustomIoOutputContext(FFmpegWriter writer, AVFormatContext avfOutput) throws FFmpegException {
        if (avfOutput == null) {
            throw new FFmpegException("av_mallocz: FFmpeg output Pointer could not be allocated.", -1);
        }

        Pointer opaque = setWriterContext(writer);

        Pointer ptr = av_mallocz(RW_BUFFER_SIZE + AV_INPUT_BUFFER_PADDING_SIZE);

        if (ptr.isNull()) {
            removeWriterContext(opaque);
            throw new FFmpegException("av_mallocz: FFmpeg output Pointer could not be allocated.", -1);
        }

        AVIOContext avioCtx = avio_alloc_context(new BytePointer(ptr), RW_BUFFER_SIZE, AVIO_FLAG_WRITE, opaque, null, writeCallback, null);

        if (avioCtx.isNull()) {
            removeWriterContext(opaque);
            av_free(ptr);
            throw new FFmpegException("avio_alloc_context: FFmpeg output AVIOContext could not be allocated.", -1);
        }

        avfOutput.pb(avioCtx);
        avfOutput.interrupt_callback(interruptCB);

        return opaque;
    }

    private AVIOContext allocIoContext(Pointer opaque, avformat.Read_packet_Pointer_BytePointer_int readCallback, avformat.Write_packet_Pointer_BytePointer_int writeCallback, avformat.Seek_Pointer_long_int seekCallback, int contextNumber) {
        Pointer ptr = av_mallocz(RW_BUFFER_SIZE + AV_INPUT_BUFFER_PADDING_SIZE);

        if (ptr == null || ptr.isNull()) {
            return null;
        }

        AVIOContext avioCtx;

        if (writeCallback == null) {
            avioCtx = avio_alloc_context(new BytePointer(ptr), RW_BUFFER_SIZE, 0, opaque, readCallback, null, seekCallback);
        } else {
            avioCtx = avio_alloc_context(new BytePointer(ptr), RW_BUFFER_SIZE, AVIO_FLAG_WRITE, opaque, readCallback, writeCallback, seekCallback);
        }

        if (avioCtx.isNull()) {
            av_free(ptr);

            if (writeCallback == null) {
                deallocInputContext();
            } else {
                if (contextNumber == 1) {
                    deallocOutputContext();
                } else {
                    deallocOutputContext2();
                }
            }

            return null;
        }

        return avioCtx;
    }

    public void deallocInputContext() {
        if (avfCtxInput != null && !avfCtxInput.isNull()) {

            avformat_close_input(avfCtxInput); // This call already sets avfCtxInput's pointer to null

            // These are all de-allocated when avformat_close_input is called.
            avfCtxInput = null;
            videoInCodecCtx = null;
            videoOutStream = null;
            audioInCodecCtx = null;
            audioOutStream = null;
            readBuffer = null;
        }
    }

    public void deallocOutputContext() {
        if (avfCtxOutput != null && !avfCtxOutput.isNull()) {

            logger.debug("avcodec_close");
            int numStreams = avfCtxOutput.nb_streams();
            for (int idx = 0; idx < numStreams; ++idx) {
                if (avfCtxOutput.streams(idx) != null &&
                        avfCtxOutput.streams(idx).codec() != null) {

                    avcodec_close(avfCtxOutput.streams(idx).codec());
                }
            }

            logger.debug("avio_closep");
            if ((avfCtxOutput.oformat().flags() & AVFMT_NOFILE) != 0) {
                avio_closep(avfCtxOutput.pb());
                avioCtxOutput = null;
            }

            for (OutputStreamMap aStreamMap : streamMap) {
                aStreamMap.iCodec = null;

                if (aStreamMap.iDict != null && !aStreamMap.iDict.isNull()) {
                    logger.debug("Calling av_dict_free");
                    av_dict_free(aStreamMap.iDict);
                }
                aStreamMap.iDict = null;
            }

            logger.debug("avformat_free_context");
            avformat_free_context(avfCtxOutput);

            avfCtxOutput = null;
        }
    }

    public void deallocOutputContext2() {
        if (avfCtxOutput2 != null && !avfCtxOutput2.isNull()) {

            logger.debug("avcodec_close");
            int numStreams = avfCtxOutput2.nb_streams();
            for (int idx = 0; idx < numStreams; ++idx) {
                if (avfCtxOutput2.streams(idx) != null &&
                        avfCtxOutput2.streams(idx).codec() != null) {

                    avcodec_close(avfCtxOutput2.streams(idx).codec());
                }
            }

            logger.debug("avio_closep");
            if ((avfCtxOutput2.oformat().flags() & AVFMT_NOFILE) != 0) {
                avio_closep(avfCtxOutput2.pb());
                avioCtxOutput2 = null;
            }

            for (OutputStreamMap aStreamMap : streamMap2) {
                aStreamMap.iCodec = null;

                if (aStreamMap.iDict != null && !aStreamMap.iDict.isNull()) {
                    logger.debug("Calling av_dict_free");
                    av_dict_free(aStreamMap.iDict);
                }
                aStreamMap.iDict = null;
            }

            logger.debug("avformat_free_context");
            avformat_free_context(avfCtxOutput2);

            avfCtxOutput2 = null;
        }
    }

    /**
     * De-allocates any objects that may be allocated.
     */
    public synchronized void deallocAll() {
        deallocInputContext();
        deallocOutputContext();
        deallocOutputContext2();
    }

    public int getProgram() {
        return desiredProgram;
    }

    public void setProgram(int desiredProgram) {
        this.desiredProgram = desiredProgram;
    }

    /**
     * This releases anything that could lead to a memory leak from this context.
     * <p/>
     * After calling this method you can no longer use this context. Attempts to use this context
     * after calling this method will be unpredictable.
     */
    public synchronized void dispose() {
        if (!disposed) {
            contextLock.writeLock().lock();

            try {
                contextMap[(int)OPAQUE.address()] = null;
            } finally {
                contextLock.writeLock().unlock();
            }

            if (writerOpaque != null) {
                removeWriterContext(writerOpaque);
            }

            if (writerOpaque2 != null) {
                removeWriterContext(writerOpaque2);
            }

            deallocAll();
        }
    }
}
