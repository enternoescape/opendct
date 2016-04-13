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

/*
 * Copyright (c) 2010 Nicolas George
 * Copyright (c) 2011 Stefano Sabatini
 * Copyright (c) 2014 Andrey Utkin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package opendct.video.ffmpeg;

import opendct.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static opendct.video.ffmpeg.FFmpegUtil.*;
import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avfilter.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;

public class FFmpegTranscoder implements FFmpegStreamProcessor {
    private static final Logger logger = LogManager.getLogger(FFmpegTranscoder.class);

    private static final long WRAP_0 = 8589934592L;
    private static final long WRAP_1 = 8589934592L / 2L;
    private static final long WRAP_0_LOW = WRAP_0 - 90000;
    private static final long WRAP_0_HIGH = WRAP_0 + 90000;
    private static final long WRAP_1_LOW = WRAP_1 - 90000;
    private static final long WRAP_1_HIGH = WRAP_1 + 90000;
    private static final int TS_TIME_BASE = 90000;

    private boolean switching = false;
    private long switchTimeout = 0;
    private FFmpegWriter newWriter = null;
    private FFmpegWriter newWriter2 = null;
    private String newFilename = null;
    private final Object switchLock = new Object();

    private static Map<Pointer, Integer> permissionMap = new HashMap<>();
    private static int permissionWeight = 0;
    private static int transcodeLimit =
            Config.getInteger("consumer.ffmpeg.transcode_limit",
                    (Runtime.getRuntime().availableProcessors() - 1) * 2);

    private static final float dts_delta_threshold = 10;
    private long firstDtsByStreamIndex[] = new long[0];
    private long firstPtsByStreamIndex[] = new long[0];
    private long lastDtsByStreamIndex[] = new long[0];
    private long lastPtsByStreamIndex[] = new long[0];
    boolean h264PtsHackEnabled = false;
    private AtomicInteger encodedFrames[] = new AtomicInteger[0];
    private long startTime = 0;

    private FFmpegContext ctx = null;
    private boolean interlaced = false;
    private FilteringContext filter_ctx[] = new FilteringContext[0];

    private class FilteringContext {
        private AVFilterContext buffersink_ctx;
        private AVFilterContext buffersrc_ctx;
        private AVFilterGraph filter_graph;
    }

    /**
     * Request permission to use transcoding resources.
     *
     * @param opaque A unique pointer for the requesting FFmpeg context.
     * @param weight The assigned weight to the transcoding job.
     * @return <i>true</i> if the transcoding is allowed to proceed.
     */
    public static synchronized boolean getTranscodePermission(Pointer opaque, int weight) {
        if (opaque == null) {
            return false;
        }

        Integer checkWeight = permissionMap.get(opaque);

        // The capture device is asking for permission, but the transcode limit has been reached.
        // The stream can only be remuxed now.
        if (checkWeight == null && permissionWeight + weight > transcodeLimit) {
            return false;
        } else if (checkWeight != null) {
            returnTranscodePermission(opaque);

            if (permissionWeight + weight > transcodeLimit) {
                return false;
            }
        }

        permissionWeight += weight;
        permissionMap.put(opaque, weight);
        return true;
    }

    public static synchronized void returnTranscodePermission(Pointer opaque) {
        if (opaque == null) {
            return;
        }

        Integer weight = permissionMap.get(opaque);

        if (weight != null) {
            permissionWeight -= weight;
            permissionMap.remove(opaque);
        }
    }

    @Override
    public boolean switchOutput(String newFilename, FFmpegWriter writer, FFmpegWriter writer2) {
        synchronized (switchLock) {
            logger.info("SWITCH started.");

            this.newFilename = newFilename;
            newWriter = writer;
            newWriter2 = writer2;
            switchTimeout = System.currentTimeMillis() + 10000;
            switching = true;


            while (switching && !ctx.isInterrupted()) {
                try {
                    switchLock.wait(500);

                    // The timeout will also manage a situation whereby this is called and
                    // the other end is not currently running.
                    if (switching && System.currentTimeMillis() > switchTimeout) {
                        logger.warn("SWITCH timed out.");
                        return false;
                    }
                } catch (InterruptedException e) {
                    logger.info("SWITCH wait was interrupted.");
                }
            }
        }

        return true;
    }

    @Override
    public void initStreamOutput(FFmpegContext ctx, String outputFilename,
                                 FFmpegWriter writer, FFmpegWriter writer2)
            throws FFmpegException, InterruptedException {

        initStreamOutput(ctx, outputFilename, writer, writer2, true);
    }

    /**
     * Initialize output stream after detection has already been performed.
     *
     * @param ctx The FFmpeg context with inputs populated from input stream detection.
     * @param outputFilename The name of the file that will be written. This is only a hint to the
     *                       muxer about what file format is desired.
     * @param writer The writer to be used for output.
     * @param firstRun If <i>false</i> this will skip various things such as interlace detection and
     *                 displaying the input stream information.
     * @throws FFmpegException This is thrown if there are any problems that cannot be handled.
     * @throws InterruptedException This is thrown if initialization is is interrupted.
     */
    public void initStreamOutput(FFmpegContext ctx, String outputFilename,
                                 FFmpegWriter writer, FFmpegWriter writer2, boolean firstRun)
            throws FFmpegException, InterruptedException {

        int ret;
        this.ctx = ctx;

        logger.info("Initializing FFmpeg transcoder stream output.");

        if (ctx.isInterrupted()) {
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        if (firstRun) {
            ctx.dumpInputFormat();
        }

        ctx.secondaryStream = writer2 != null;

        int numInputStreams = ctx.avfCtxInput.nb_streams();

        // Creates the output container and AVFormatContext.
        ctx.allocAvfContainerOutputContext(outputFilename);
        if (ctx.secondaryStream) {
            ctx.allocAvfContainerOutputContext2(outputFilename);

            ctx.streamMap2 = new OutputStreamMap[numInputStreams];

            for (int i = 0; i < ctx.streamMap2.length; i++) {
                ctx.streamMap2[i] = new OutputStreamMap();
                ctx.streamMap2[i].iStream = ctx.avfCtxInput.streams(i);
                ctx.streamMap2[i].iCodecContext = ctx.streamMap2[i].iStream.codec();
                ctx.streamMap2[i].iCodecType = ctx.streamMap2[i].iCodecContext.codec_type();
                ctx.streamMap2[i].iCodecRational = ctx.streamMap2[i].iCodecContext.time_base();
                ctx.streamMap2[i].iStreamRational = ctx.streamMap2[i].iStream.time_base();
            }
        }

        lastDtsByStreamIndex = new long[numInputStreams];
        Arrays.fill(lastDtsByStreamIndex, Integer.MIN_VALUE);

        lastPtsByStreamIndex = new long[numInputStreams];
        Arrays.fill(lastPtsByStreamIndex, Integer.MIN_VALUE);

        firstDtsByStreamIndex = new long[numInputStreams];
        Arrays.fill(firstDtsByStreamIndex, Integer.MIN_VALUE);

        firstPtsByStreamIndex = new long[numInputStreams];
        Arrays.fill(firstPtsByStreamIndex, Integer.MIN_VALUE);

        ctx.streamMap = new OutputStreamMap[numInputStreams];

        for (int i = 0; i < ctx.streamMap.length; i++) {
            ctx.streamMap[i] = new OutputStreamMap();
            ctx.streamMap[i].iStream = ctx.avfCtxInput.streams(i);
            ctx.streamMap[i].iCodecContext = ctx.streamMap[i].iStream.codec();
            ctx.streamMap[i].iCodecType = ctx.streamMap[i].iCodecContext.codec_type();
            ctx.streamMap[i].iCodecRational = ctx.streamMap[i].iCodecContext.time_base();
            ctx.streamMap[i].iStreamRational = ctx.streamMap[i].iStream.time_base();
        }

        if (firstRun) {
            encodedFrames = new AtomicInteger[numInputStreams];

            for (int i = 0; i < encodedFrames.length; i++) {
                encodedFrames[i] = new AtomicInteger(0);
            }
        }

        if (ctx.isInterrupted()) {
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        int videoHeight;
        int videoWidth;

        if (ctx.videoInCodecCtx != null) {
            videoHeight = ctx.videoInCodecCtx.height();
            videoWidth = ctx.videoInCodecCtx.width();
        } else {
            videoHeight = 0;
            videoWidth = 0;
        }

        if (firstRun) {
            AVCodec videoCodec = null;

            if (ctx.videoInCodecCtx != null) {
                videoCodec = avcodec_find_decoder(ctx.videoInCodecCtx.codec_id());
            }

            if (ctx.encodeProfile != null && ctx.videoInCodecCtx != null && ctx.preferredVideo > NO_STREAM_IDX) {

                ctx.videoEncodeSettings = ctx.encodeProfile.getVideoEncoderMap(
                        videoWidth,
                        videoHeight,
                        ctx.encodeProfile.getVideoEncoderCodec(
                                videoCodec));

                // Remove the encoder profile if we cannot get permission to transcode. This will
                // prevent any possible future attempts.
                String weightStr = ctx.videoEncodeSettings.get("encode_weight");

                int weight = 2;

                if (weightStr != null) {
                    try {
                        weight = Integer.parseInt(weightStr);
                    } catch (NumberFormatException e) {
                        logger.error("Unable to parse '{}' into an integer, using the default {}.",
                                weightStr, weight);
                    }
                } else {
                    logger.warn("encode_weight is not set. Using default {}.", weight);
                }

                if (!getTranscodePermission(ctx.OPAQUE, weight)) {
                    ctx.encodeProfile = null;
                }
            } else {
                if (ctx.encodeProfile != null) {
                    // Everything needed to make a correct decision is not available. Remux only.
                    logger.warn("ctx.videoCodecCtx was null or there was no preferred video when" +
                            " trying to get permission to transcode. Remuxing instead.");
                    ctx.encodeProfile = null;
                }
            }

            interlaced = ctx.encodeProfile != null &&
                    ctx.encodeProfile.canInterlaceDetect(videoHeight, videoWidth) &&
                    fastDeinterlaceDetection();

            if (FFmpegConfig.getH264PtsHack() &&
                    !interlaced &&
                    videoHeight == 720 &&
                    videoWidth == 1280 &&
                    videoCodec != null &&
                    avcodec_get_name(videoCodec.id()).getString().equals("h264")) {

                logger.debug("H.264 PTS hack enabled on this video stream.");
                h264PtsHackEnabled = true;
            }
        }

        if (ctx.isInterrupted()) {
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        if (ctx.encodeProfile != null &&
                ctx.encodeProfile.canTranscodeVideo(
                        interlaced,
                        avcodec_get_name(ctx.videoInCodecCtx.codec_id()).getString(),
                        videoHeight,
                        videoWidth)) {

            ret = avcodec_open2(ctx.videoInCodecCtx,
                    avcodec_find_decoder(ctx.videoInCodecCtx.codec_id()), (PointerPointer<AVDictionary>) null);

            if (ret < 0) {
                throw new FFmpegException("Failed to open decoder for stream #" + ctx.preferredVideo, ret);
            }

            if ((ctx.videoOutStream = addTranscodeVideoStreamToContext(ctx, ctx.preferredVideo, ctx.encodeProfile)) == null) {

                // If transcoding is not possible, we will just copy it.
                logger.warn("Unable to set up transcoding. The stream will be copied.");
                if ((ctx.videoOutStream = addCopyStreamToContext(ctx.avfCtxOutput, ctx.avfCtxInput.streams(ctx.preferredVideo))) == null) {
                    throw new FFmpegException("Could not find a video stream", -1);
                }
            } else {
                ctx.streamMap[ctx.preferredVideo].transcode = true;
            }
        } else {
            if ((ctx.videoOutStream = addCopyStreamToContext(ctx.avfCtxOutput, ctx.avfCtxInput.streams(ctx.preferredVideo))) == null) {
                throw new FFmpegException("Could not find a video stream", -1);
            }
        }

        if (ctx.secondaryStream) {
            if ((ctx.videoOutStream2 = addCopyStreamToContext(ctx.avfCtxOutput2, ctx.avfCtxInput.streams(ctx.preferredVideo))) == null) {
                throw new FFmpegException("Could not find a video stream for output 2", -1);
            }

            ctx.streamMap2[ctx.preferredVideo].outStreamIndex = ctx.videoOutStream2.id();
            ctx.streamMap2[ctx.preferredVideo].oCodecRational = ctx.videoOutStream2.codec().time_base();
            ctx.streamMap2[ctx.preferredVideo].oStreamRational = ctx.videoOutStream2.time_base();
            ctx.streamMap2[ctx.preferredVideo].oCodecContext = ctx.videoOutStream2.codec();
            ctx.streamMap2[ctx.preferredVideo].oStream = ctx.videoOutStream2;
        }

        if ((ctx.audioOutStream = addCopyStreamToContext(ctx.avfCtxOutput, ctx.avfCtxInput.streams(ctx.preferredAudio))) == null) {
            throw new FFmpegException("Could not find a audio stream", -1);
        }

        ctx.streamMap[ctx.preferredVideo].outStreamIndex = ctx.videoOutStream.id();
        ctx.streamMap[ctx.preferredVideo].oCodecRational = ctx.videoOutStream.codec().time_base();
        ctx.streamMap[ctx.preferredVideo].oStreamRational = ctx.videoOutStream.time_base();
        ctx.streamMap[ctx.preferredVideo].oCodecContext = ctx.videoOutStream.codec();
        ctx.streamMap[ctx.preferredVideo].oStream = ctx.videoOutStream;

        ctx.streamMap[ctx.preferredAudio].outStreamIndex = ctx.audioOutStream.id();
        ctx.streamMap[ctx.preferredAudio].oCodecRational = ctx.videoOutStream.codec().time_base();
        ctx.streamMap[ctx.preferredAudio].oStreamRational = ctx.videoOutStream.time_base();
        ctx.streamMap[ctx.preferredAudio].oCodecContext = ctx.videoOutStream.codec();
        ctx.streamMap[ctx.preferredAudio].oStream = ctx.videoOutStream;

        for (int i = 0; i < numInputStreams; ++i) {
            if (ctx.streamMap[i].outStreamIndex != NO_STREAM_IDX) {
                continue;
            }

            AVCodecContext codecCtx = getCodecContext(ctx.avfCtxInput.streams(i));

            if (codecCtx != null) {
                AVStream avsOutput = addCopyStreamToContext(ctx.avfCtxOutput, ctx.avfCtxInput.streams(i));

                if (avsOutput != null) {
                    ctx.streamMap[i].outStreamIndex = avsOutput.id();
                    ctx.streamMap[i].oCodecRational = ctx.videoOutStream.codec().time_base();
                    ctx.streamMap[i].oStreamRational = ctx.videoOutStream.time_base();
                    ctx.streamMap[i].oCodecContext = ctx.videoOutStream.codec();
                    ctx.streamMap[i].oStream = ctx.videoOutStream;
                }
            }
        }

        if (ctx.isInterrupted()) {
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        filter_ctx = new FilteringContext[numInputStreams];

        if ((ret = initFilters()) < 0) {
            throw new FFmpegException("initFilters: Unable to allocate filters.", ret);
        }

        ctx.dumpOutputFormat();
        ctx.allocIoOutputContext(writer);

        if (ctx.secondaryStream) {
            ctx.dumpOutputFormat2("(CCExtractor)");
            ctx.allocIoOutputContext2(writer2);
            ret = avformat_write_header(ctx.avfCtxOutput2, (PointerPointer<avutil.AVDictionary>) null);

            if (ret < 0) {
                deallocFilterGraphs();
                throw new FFmpegException("Error while writing header to file 2 '" + outputFilename + "'", ret);
            }
        }

        if (ctx.isInterrupted()) {
            deallocFilterGraphs();
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        logger.debug("Writing header");

        ret = avformat_write_header(ctx.avfCtxOutput, (PointerPointer<avutil.AVDictionary>) null);
        if (ret < 0) {
            deallocFilterGraphs();
            throw new FFmpegException("Error while writing header to file '" + outputFilename + "'", ret);
        }

        logger.info("Initialized FFmpeg transcoder stream output.");
        firstRun = false;
    }

    private void deallocFilterGraphs() {
        if (filter_ctx == null) {
            return;
        }

        for (int i = 0; i < filter_ctx.length; i++) {
            if (filter_ctx[i].buffersink_ctx != null) {
                avfilter_free(filter_ctx[i].buffersink_ctx);
                filter_ctx[i].buffersink_ctx = null;
            }

            if (filter_ctx[i].buffersink_ctx != null) {
                avfilter_free(filter_ctx[i].buffersink_ctx);
                filter_ctx[i].buffersink_ctx = null;
            }

            if (filter_ctx[i].filter_graph != null) {
                avfilter_graph_free(filter_ctx[i].filter_graph);
                filter_ctx[i].filter_graph = null;
            }
        }

        filter_ctx = null;
    }

    private boolean fastDeinterlaceDetection() throws FFmpegException {

        int ret = avcodec_open2(ctx.videoInCodecCtx,
                avcodec_find_decoder(ctx.videoInCodecCtx.codec_id()), (PointerPointer<AVDictionary>) null);

        if (ret < 0) {
            throw new FFmpegException("Failed to open decoder for stream #" + ctx.preferredVideo, ret);
        }

        AVPacket packet = new AVPacket();
        packet.data(null);
        packet.size(0);
        int got_frame[] = new int[] { 0 };
        AVFrame frame;


        // This number will increase as interlaced flags are found. If no frames are found after 60
        // frames, give up.
        int frameLimit = 90;

        // This is is the absolute frame limit. Once this number is reached the method will return
        // that this is not interlaced content.
        int absFrameLimit = frameLimit * 2;

        int totalFrames = 0;
        int interThresh = 3;
        int interFrames = 0;

        try {
            if (ctx.SEEK_BUFFER != null) {
                ctx.SEEK_BUFFER.setNoWrap(true);
            }

            long stopTime = System.currentTimeMillis() + 1000;

            while(!ctx.isInterrupted()) {
                if (System.currentTimeMillis() > stopTime) {
                    break;
                }

                ret = av_read_frame(ctx.avfCtxInput, packet);
                if (ret < 0) {
                    if (ret != AVERROR_EOF) {
                        logger.error("Error reading frame during interlaced detection: {}", ret);
                    }
                    break;
                }

                int inputStreamIndex = packet.stream_index();

                AVStream stream = ctx.avfCtxInput.streams(inputStreamIndex);

                if (inputStreamIndex >= ctx.streamMap.length ||
                        inputStreamIndex != ctx.preferredVideo) {

                    // The packet is diverted to a queue to be processed after detection. If it is
                    // determined that re-muxing is preferred over transcoding, these packets will
                    // be fed to the re-muxer. If re-muxing isn't preferred, this queue can be
                    // de-allocated later.
                    av_packet_unref(packet);
                    continue;
                }

                frame = av_frame_alloc();
                if (frame == null) {
                    throw new FFmpegException("av_frame_alloc: Unable to allocate frame.", -1);
                }

                av_packet_rescale_ts(packet,
                        stream.time_base(),
                        stream.codec().time_base());

                logger.debug("Decoding video frame {} for interlace detection. {} frames interlaced.", totalFrames, interFrames);

                ret = avcodec_decode_video2(stream.codec(), frame,
                        got_frame, packet);

                av_packet_rescale_ts(packet,
                        stream.codec().time_base(),
                        stream.time_base());

                if (ret < 0) {
                    av_frame_free(frame);
                    //av_packet_unref(packet);
                    logger.error("Decoding failed");
                    continue;
                }

                if (got_frame[0] != 0) {
                    int interlaced = frame.interlaced_frame();;
                    interFrames += interlaced;
                    frameLimit += interlaced;

                    // Do not retain decoded packets. The RAM usage will get insane very quickly.
                }

                av_frame_free(frame);
                av_packet_unref(packet);

                if (interFrames >= interThresh) {
                    logger.info("Content is interlaced.");
                    return true;
                } else if (totalFrames++ >= frameLimit || totalFrames >= absFrameLimit) {
                    break;
                }
            }
        } catch (FFmpegException e) {
            logger.error("Deinterlace detection exception => ", e);
        } finally {
            avcodec_close(ctx.videoInCodecCtx);

            /*if (interFrames < interThresh) {
                // Return to the start.
                avio_seek(ctx.avfCtxInput.pb(), 0, 0);
            }*/

            if (ctx.SEEK_BUFFER != null) {
                ctx.SEEK_BUFFER.setNoWrap(false);
            }
        }

        return false;
    }

    @Override
    public synchronized void streamOutput() throws FFmpegException {
        int ret = 0;

        // This value will be adjusted as needed to keep the entire stream on the same time code.
        //long tsOffset = 0;
        long tsOffsets[] = new long[firstDtsByStreamIndex.length];
        Arrays.fill(tsOffsets, 0);

        // This value indicates what streams are currently in use and are desirable to be in
        // agreement with the other streams.
        boolean tsActiveOffsets[] = new boolean[firstDtsByStreamIndex.length];
        Arrays.fill(tsActiveOffsets, false);
        // This is set when the offset for any stream has changed, so we know it needs to be synced.
        boolean tsOffsetChanged = false;
        // This value is used to determine when to force the offsets to sync up. Currently the loop
        // will try to sync up for up to 120 frames that are not out of sync before everything if
        // forced to sync to the largest offset.
        int tsOffsetAttempts = 0;
        int tsOffsetAttemptLimit = 120;

        // This is incremented by frame duration. It does not distinguish between streams, so if
        // multiple streams are being corrected, this value will increase faster that the assumed
        // duration. This is an acceptable compromise vs iterating an array or calculating the most
        // accurate value on each update.
        long lastErrorTime = 0;
        long lastErrorTimeLimit = 5 * TS_TIME_BASE;
        // This is the number of time the error time has increased due to an error.
        int errorCounter = 0;
        // This is the maximum number of errors within the error time limit allowed before the
        // discontinuity tolerance is increased.
        int errorLimit = 50;
        // This is the number of times the discontinuity tolerance has been increased.
        int adjustments = 0;
        // This is the last stream that adjusted it's offset. This is used as the correct offset
        // value after everything is synced up.
        int lastToAdjust = 0;
        // This is the number of ticks off +/- from the expected timestamp allowed before corrective
        // action is taken. This number will increase automatically if it is determined to be too
        // low.
        int discontinuityTolerance = 3500000;
        long expectedDts;

        final boolean fixingEnabled = FFmpegConfig.getFixStream();

        int switchFlag;
        long dts;
        long pts;
        long preOffsetDts;
        long preOffsetPts;
        int increment;
        long diff = 0;

        // Used when streaming first starts to keep the first frame from being a known bad frame.
        // This helps with some players that otherwise would just assume it must be a bad video.
        boolean firstFrame[] = new boolean[firstDtsByStreamIndex.length];
        Arrays.fill(firstFrame, true);

        AVPacket packet = new AVPacket();
        packet.data(null);
        packet.size(0);

        AVPacket copyPacket = new AVPacket();
        copyPacket.data(null);
        packet.size(0);

        //AVStream iavStream;
        //AVCodecContext iavCodecContext;
        int inputStreamIndex;
        int outputStreamIndex;
        int codecType;
        int got_frame[] = new int[] { 0 };

        // This needs to start out null or Java complains about the cleanup.
        AVFrame frame = null;

        try {
            startTime = System.currentTimeMillis();

            while (true) {
                ret = av_read_frame(ctx.avfCtxInput, packet);
                if (ret < 0) {
                    break;
                }

                inputStreamIndex = packet.stream_index();

                if (inputStreamIndex >= ctx.streamMap.length ||
                        (outputStreamIndex = ctx.streamMap[inputStreamIndex].outStreamIndex)
                                == NO_STREAM_IDX) {

                    av_packet_unref(packet);
                    continue;
                }

                preOffsetDts = packet.dts();
                preOffsetPts = packet.pts();

                if (firstFrame[outputStreamIndex]) {
                    if ((packet.flags() & AV_PKT_FLAG_CORRUPT) > 0) {

                        av_packet_unref(packet);
                        continue;
                    } else {
                        logger.debug("stream {}, first dts = {}, first pts = {}",
                                inputStreamIndex, preOffsetDts, preOffsetPts);

                        /*codecType = ctx.avfCtxInput.streams(inputStreamIndex).codec().codec_type();
                        if (codecType == AVMEDIA_TYPE_VIDEO ||
                                codecType ==AVMEDIA_TYPE_AUDIO) {

                            tsOffsets[inputStreamIndex] = -Math.min(preOffsetDts, preOffsetPts);
                            tsOffsetChanged = true;
                            tsActiveOffsets[inputStreamIndex] = true;
                        }*/
                    }
                }

                firstFrame[outputStreamIndex] = false;

                // Discard all frames that don't have any timestamps since especially without a
                // presentation timestamp, the frame will never be displayed anyway.
                if (preOffsetDts == AV_NOPTS_VALUE || preOffsetPts == AV_NOPTS_VALUE) {
                    /*logger.debug("stream {}, dts == AV_NOPTS_VALUE || pts == AV_NOPTS_VALUE," +
                            " discarding frame.", inputStreamIndex);*/

                    av_packet_unref(packet);
                    continue;
                }

                if (switching && inputStreamIndex == ctx.preferredVideo) {

                    switchFlag = packet.flags() & AV_PKT_FLAG_KEY;

                    // Check if we are at least on a flagged video key frame. Then switch before the
                    // frame is actually processed. This ensures that if we are muxing, we are
                    // starting with hopefully an I frame and if we are transcoding this is likely a
                    // good transition point.
                    if (switchFlag > 0 || System.currentTimeMillis() >= switchTimeout) {
                        logger.debug("Video key frame flag: {}", switchFlag);

                        synchronized (switchLock) {
                            try {
                                switchStreamOutput();

                                // This will cause the frame timestamps to be displayed again and
                                // also drop any bad frames since we are starting a new file.
                                Arrays.fill(firstFrame, true);
                                errorCounter = 0;
                            } catch (InterruptedException e) {
                                logger.debug("Switching was interrupted.");
                                av_packet_unref(packet);
                                break;
                            }
                            switching = false;

                            switchLock.notifyAll();
                        }

                        logger.info("SWITCH successful: {}ms.",
                                System.currentTimeMillis() - (switchTimeout - 10000));
                    }
                }

                if (lastErrorTime > lastErrorTimeLimit) {
                    errorCounter = 0;
                    adjustments = 0;
                    lastErrorTime = 0;
                }

                if (errorCounter > errorLimit) {

                    discontinuityTolerance *= 2;
                    logger.info("adjusting tolerance to {}. errors = {}, adjustments = {}",
                            discontinuityTolerance, errorCounter, adjustments);

                    errorCounter = 0;
                    adjustments += 1;
                    lastErrorTime = 0;
                }

                dts = preOffsetDts + tsOffsets[inputStreamIndex];
                pts = preOffsetPts + tsOffsets[inputStreamIndex];

                // These are referenced several times. This keeps these variables from constantly
                // being copied into the JVM.
                //iavStream = ctx.streamMap[inputStreamIndex].iStream;
                //iavCodecContext = ctx.streamMap[inputStreamIndex].iCodecContext;
                codecType = ctx.streamMap[inputStreamIndex].iCodecType;

                if ((codecType == AVMEDIA_TYPE_VIDEO ||
                        codecType ==AVMEDIA_TYPE_AUDIO) &&
                        lastDtsByStreamIndex[inputStreamIndex] > 0) {

                    tsActiveOffsets[inputStreamIndex] = true;

                    // There are probably many other situations that could come up making this value
                    // incorrect, but this is only optimizing for typical MPEG-TS input and
                    // MPEG-TS/PS output. This has the potential to introduce a rounding error since
                    // it is not based on the stream time base rational.
                    increment = Math.max(packet.duration(), 0);

                    expectedDts = (lastDtsByStreamIndex[inputStreamIndex] + increment); // & 0x1ffffffffL;
                    diff = dts - expectedDts;

                    if (fixingEnabled &&
                            (diff > discontinuityTolerance || diff < -discontinuityTolerance)) {

                        errorCounter += 1;
                        lastErrorTime += increment;

                        long oldDts = dts;
                        long oldPts = pts;
                        long oldOffset = tsOffsets[inputStreamIndex];

                        dts -= diff;
                        pts -= diff;
                        tsOffsets[inputStreamIndex] -= diff;
                        tsOffsetChanged = true;
                        lastToAdjust = inputStreamIndex;

                        logger.debug("fixing stream {} timestamp discontinuity diff = {}," +
                                        " offset = {}, new offset = {}," +
                                        " preoff dts = {}, dts = {}, new dts {}, last dts = {}," +
                                        " preoff pts = {}, pts = {}, new pts = {}, last pts = {}",
                                inputStreamIndex, diff,
                                oldOffset, tsOffsets[inputStreamIndex],
                                preOffsetDts, oldDts, dts, lastDtsByStreamIndex[inputStreamIndex],
                                preOffsetPts, oldPts, pts, lastPtsByStreamIndex[inputStreamIndex]);
                    } else if (tsOffsetChanged) {
                        // If the offset is changed for any one of the streams, we need to make sure
                        // they are all using the same offset once the event is over or they may
                        // slowly get out of sync.
                        long maxOffset = Long.MIN_VALUE;
                        long minOffset = Long.MAX_VALUE;

                        for (int i = 0; i < tsOffsets.length; i++) {
                            if (!tsActiveOffsets[i]) {
                                continue;
                            }

                            if (minOffset == Long.MAX_VALUE || tsOffsets[i] < minOffset) {
                                minOffset = tsOffsets[i];
                            }

                            if (maxOffset == Long.MIN_VALUE || tsOffsets[i] > maxOffset) {
                                maxOffset = tsOffsets[i];
                            }
                        }

                        long offsetDiff = maxOffset - minOffset;

                        if ((offsetDiff > -discontinuityTolerance &&
                                offsetDiff < discontinuityTolerance) ||
                                tsOffsetAttempts > tsOffsetAttemptLimit) {

                            // This is a good spot to correct the offset before it reaches the long
                            // wrap around limit. If this is going backwards so much that it wraps
                            // around backwards you likely would have noticed just based on the poor
                            // playback of the stream. Subtracting this value will effectively
                            // result in the exact same offset.

                            if (tsOffsetAttempts > tsOffsetAttemptLimit) {
                                logger.debug("force sync offsets {} to {}, diff = {}, attempts = {}",
                                        tsOffsets, tsOffsets[lastToAdjust], offsetDiff, tsOffsetAttempts);
                            } else {
                                logger.debug("sync offsets {} to {}, diff = {}, attempts = {}",
                                        tsOffsets, tsOffsets[lastToAdjust], offsetDiff, tsOffsetAttempts);
                            }

                            tsOffsetChanged = false;
                            tsOffsetAttempts = 0;

                            for (int i = 0; i < tsOffsets.length; i++) {
                                tsOffsets[i] = tsOffsets[lastToAdjust];
                            }

                            lastToAdjust = ctx.preferredVideo;
                        } else {
                            tsOffsetAttempts += 1;
                        }
                    } else if (!fixingEnabled && diff > 162000000) {
                        // If the stream is more than 30 minutes ahead, discard it. Leaving it alone
                        // will do nothing but break things since we are not trying to fix errors.
                        logger.debug("discarding frame stream {}, dts {} - last dts {}" +
                                        " > 162000000, pts {}, last pts {}",
                                inputStreamIndex, dts, lastDtsByStreamIndex[inputStreamIndex],
                                pts, lastPtsByStreamIndex[inputStreamIndex]);

                        errorCounter += 1;
                        lastErrorTime += increment;

                        av_packet_unref(packet);
                        continue;
                    }

                    if (dts <= lastDtsByStreamIndex[inputStreamIndex]) {

                        // If the decode time stamp is equal to the last one, discard the frame.
                        // There isn't a simple way to know if the frame can be put in the assumed
                        // correct place without putting a ripple in the timeline.
                        if (lastDtsByStreamIndex[inputStreamIndex] == dts) {
                            logger.debug("discarding frame stream {}, dts {} == last dts {}," +
                                            " pts {}, last pts {}",
                                    inputStreamIndex, dts, lastDtsByStreamIndex[inputStreamIndex],
                                    pts, lastPtsByStreamIndex[inputStreamIndex]);

                            av_packet_unref(packet);
                            continue;
                        }

                        // If the pts is still greater than the last pts, fix the dts so it can be
                        // muxed. This helps retain H.264 B frames when the decode timestamps are
                        // out of order.
                        if (pts > lastPtsByStreamIndex[inputStreamIndex] &&
                                pts > lastDtsByStreamIndex[inputStreamIndex]) {

                            long oldDts = dts;

                            dts = lastDtsByStreamIndex[inputStreamIndex] + 1;

                            logger.debug("re-ordering stream {}, diff = {}, offset = {}" +
                                            " preoff dts = {}, dts = {}, new dts = {}," +
                                            " last dts = {}," +
                                            " preoff pts = {}, pts = {} > last pts = {}",
                                    diff, inputStreamIndex, tsOffsets[inputStreamIndex],
                                    preOffsetDts, oldDts, dts,
                                    lastDtsByStreamIndex[inputStreamIndex],
                                    preOffsetPts, pts, lastPtsByStreamIndex[inputStreamIndex]);
                        } else {
                            logger.debug("discarding packet stream {}," +
                                            " dts {} < last dts {}," +
                                            " pts {} <= last pts {}",
                                    inputStreamIndex,
                                    dts, lastDtsByStreamIndex[inputStreamIndex],
                                    pts, lastPtsByStreamIndex[inputStreamIndex]);

                            av_packet_unref(packet);
                            continue;
                        }
                    }
                }

                packet.dts(dts);
                packet.pts(pts);

                lastDtsByStreamIndex[inputStreamIndex] = dts;
                lastPtsByStreamIndex[inputStreamIndex] = pts;

                // If this gets enabled on interlaced content, it may remove all of the even or odd
                // frames since they typically have an out of order PTS.
                if (h264PtsHackEnabled) {
                    if (lastPtsByStreamIndex[outputStreamIndex] >= pts) {
                        av_packet_unref(packet);
                        continue;
                    } else {
                        lastPtsByStreamIndex[outputStreamIndex] = pts;
                    }
                }

                if (ctx.secondaryStream && inputStreamIndex == ctx.preferredVideo) {
                    av_copy_packet(copyPacket, packet);
                    av_packet_copy_props(copyPacket, packet);

                    //logPacket(ctx.avfCtxInput, copyPacket, "copy2-in");

                    // remux this frame without re-encoding
                    av_packet_rescale_ts(copyPacket,
                            ctx.streamMap2[inputStreamIndex].iStreamRational,
                            ctx.streamMap2[inputStreamIndex].oStreamRational);

                    //logPacket(ctx.avfCtxInput, copyPacket, "copy2-out");

                    copyPacket.stream_index(ctx.streamMap2[inputStreamIndex].outStreamIndex);

                    ret = av_interleaved_write_frame(ctx.avfCtxOutput2, copyPacket);

                    if (ret < 0) {
                        logger.error("Error from av_interleaved_write_frame output 2: {}", ret);
                    }
                }

                //logger.trace("Demuxer gave frame of streamIndex {}", inputStreamIndex);

                if (filter_ctx[inputStreamIndex].filter_graph != null) {

                    //logger.trace("Going to re-encode & filter the frame");

                    frame = av_frame_alloc();
                    if (frame == null) {
                        throw new FFmpegException("av_frame_alloc: Unable to allocate frame.",
                                ENOMEM);
                    }

                    //logPacket(ctx.avfCtxInput, packet, "trans-dec-in");

                    av_packet_rescale_ts(packet,
                            ctx.streamMap[inputStreamIndex].iStreamRational,
                            ctx.streamMap[inputStreamIndex].iCodecRational);

                    //logPacket(ctx.avfCtxInput, packet, "trans-dec-out");

                    if (codecType == AVMEDIA_TYPE_VIDEO) {
                        ret = avcodec_decode_video2(
                                ctx.streamMap[inputStreamIndex].iCodecContext, frame,
                                got_frame, packet);
                    } else {
                        ret = avcodec_decode_audio4(
                                ctx.streamMap[inputStreamIndex].iCodecContext, frame,
                                got_frame, packet);
                    }

                    if (ret < 0) {
                        av_frame_free(frame);
                        av_packet_unref(packet);
                        logger.error("Decoding failed");
                        continue;
                    }

                    if (got_frame[0] != 0) {
                        frame.pts(av_frame_get_best_effort_timestamp(frame));
                        ret = filterEncodeWriteFrame(frame, inputStreamIndex);
                        av_frame_free(frame);

                        if (ret < 0) {
                            logger.error("Error from filterEncodeWriteFrame: {}", ret);
                            //throw new FFmpegException("Error from filterEncodeWriteFrame.", ret);
                        }
                    } else {
                        av_frame_free(frame);
                    }
                } else {
                    //logPacket(ctx.avfCtxInput, packet, "copy-in");

                    // remux this frame without re-encoding
                    av_packet_rescale_ts(packet,
                            ctx.streamMap[inputStreamIndex].iStreamRational,
                            ctx.streamMap[inputStreamIndex].oStreamRational);

                    //logPacket(ctx.avfCtxInput, packet, "copy-out");

                    packet.stream_index(ctx.streamMap[inputStreamIndex].outStreamIndex);

                    ret = av_interleaved_write_frame(ctx.avfCtxOutput, packet);

                    if (ret < 0) {
                        logger.error("Error from av_interleaved_write_frame: {}", ret);
                    }
                }

                av_packet_unref(packet);
            }

            int numInputStreams = ctx.avfCtxInput.nb_streams();

            // flush filters and encoders
            for (int i = 0; i < numInputStreams; i++) {

                if (filter_ctx != null && i < filter_ctx.length) {
                    // flush filter
                    if (filter_ctx[i].filter_graph == null)
                        continue;

                    ret = filterEncodeWriteFrame(null, i);

                    if (ret < 0) {
                        logger.error("Flushing filter failed: {}", ret);
                    }
                }

                // flush encoder
                ret = flushEncoder(i);
                if (ret < 0) {
                    logger.error("Flushing encoder failed: {}", ret);
                }
            }

            av_write_trailer(ctx.avfCtxOutput);
        } finally {
            returnTranscodePermission(ctx.OPAQUE);

            // Cleanup.
            endStreamOutput(packet, frame);
            logger.info("FFmpeg transcoder ended with code {}", ret);
        }
    }

    private void switchStreamOutput() throws FFmpegException, InterruptedException {
        int ret;
        int numInputStreams = ctx.avfCtxInput.nb_streams();

        // flush filters and encoders
        for (int i = 0; i < numInputStreams; i++) {

            if (filter_ctx != null && i < filter_ctx.length) {
                // flush filter
                if (filter_ctx[i].filter_graph == null)
                    continue;

                ret = filterEncodeWriteFrame(null, i);

                if (ret < 0) {
                    logger.error("Flushing filter failed: {}", ret);
                }
            }

            /* flush encoder */
            ret = flushEncoder(i);
            if (ret < 0) {
                logger.error("Flushing encoder failed: {}", ret);
            }
        }

        av_write_trailer(ctx.avfCtxOutput);

        deallocFilterGraphs();
        ctx.deallocOutputContext();

        if (ctx.secondaryStream) {
            av_write_trailer(ctx.avfCtxOutput2);
            ctx.deallocOutputContext2();
        }

        if (ctx.isInterrupted()) {
            return;
        }

        initStreamOutput(ctx, newFilename, newWriter, newWriter2, false);
    }

    private void endStreamOutput(AVPacket packet, AVFrame frame) {
        av_packet_unref(packet);
        av_frame_free(frame);

        deallocFilterGraphs();
    }

    private int initFilter(FilteringContext fctx, AVCodecContext dec_ctx,
                           AVCodecContext enc_ctx, AVStream out_stream, String filter_spec,
                           AVCodec encoder, AVDictionary dict) throws FFmpegException {

        int ret = 0;
        int decCodecType;
        avfilter.AVFilter buffersrc;
        avfilter.AVFilter buffersink;
        avfilter.AVFilterContext buffersrc_ctx;
        avfilter.AVFilterContext buffersink_ctx;
        avfilter.AVFilterInOut outputs = avfilter_inout_alloc();
        avfilter.AVFilterInOut inputs = avfilter_inout_alloc();
        avfilter.AVFilterGraph filter_graph = avfilter_graph_alloc();

        try {
            decCodecType = dec_ctx.codec_type();

            if (outputs == null || inputs == null || filter_graph == null) {
                throw new FFmpegException("Not enough memory available", ENOMEM);
            }

            if (decCodecType == AVMEDIA_TYPE_VIDEO) {
                buffersrc = avfilter_get_by_name("buffer");
                buffersink = avfilter_get_by_name("buffersink");
                if (buffersrc == null || buffersink == null) {
                    throw new FFmpegException("Filtering source or sink element not found",
                            AVERROR_UNKNOWN);
                }

                String parameters = String.format(
                        "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:frame_rate=%d/%d:pixel_aspect=%d/%d",
                        dec_ctx.width(), dec_ctx.height(), dec_ctx.pix_fmt(),
                        dec_ctx.time_base().num(), dec_ctx.time_base().den(),
                        dec_ctx.framerate().num(), dec_ctx.framerate().den(),
                        dec_ctx.sample_aspect_ratio().num(), dec_ctx.sample_aspect_ratio().den());

                /*String parameters = String.format(
                        "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
                        dec_ctx.width(), dec_ctx.height(), dec_ctx.pix_fmt(),
                        dec_ctx.time_base().num(), dec_ctx.time_base().den(),
                        dec_ctx.sample_aspect_ratio().num(), dec_ctx.sample_aspect_ratio().den());*/

                ret = avfilter_graph_create_filter(buffersrc_ctx = new AVFilterContext(null),
                        buffersrc, "in", parameters, null, filter_graph);
                if (ret < 0) {
                    throw new FFmpegException("Cannot create buffer source", ret);
                }

                ret = avfilter_graph_create_filter(buffersink_ctx = new AVFilterContext(null),
                        buffersink, "out", null, null, filter_graph);
                if (ret < 0) {
                    throw new FFmpegException("Cannot create buffer sink", ret);
                }

                BytePointer setBin = new BytePointer(4);
                setBin.asByteBuffer().putInt(enc_ctx.pix_fmt());
                ret = av_opt_set_bin(buffersink_ctx, "pix_fmts", setBin, 4, AV_OPT_SEARCH_CHILDREN);

                if (ret < 0) {
                    throw new FFmpegException("Cannot set pixel format", ret);
                }

            } else if (decCodecType == AVMEDIA_TYPE_AUDIO) {
                buffersrc = avfilter_get_by_name("abuffer");
                buffersink = avfilter_get_by_name("abuffersink");
                if (buffersrc == null || buffersink == null) {
                    throw new FFmpegException("filtering source or sink element not found", AVERROR_UNKNOWN);
                }

                if (dec_ctx.channel_layout() == 0) {
                    dec_ctx.channel_layout(av_get_default_channel_layout(dec_ctx.channels()));
                }

                String parameters = String.format(
                        "time_base=%d/%d:sample_rate=%d:sample_fmt=%s:channel_layout=0x%x",
                        dec_ctx.time_base().num(), dec_ctx.time_base().den(), dec_ctx.sample_rate(),
                        av_get_sample_fmt_name(dec_ctx.sample_fmt()).getString(),
                        dec_ctx.channel_layout());

                ret = avfilter_graph_create_filter(buffersrc_ctx = new AVFilterContext(), buffersrc, "in",
                        parameters, null, filter_graph);
                if (ret < 0) {
                    throw new FFmpegException("Cannot create audio buffer source", ret);
                }

                ret = avfilter_graph_create_filter(buffersink_ctx = new AVFilterContext(), buffersink, "out",
                        null, null, filter_graph);
                if (ret < 0) {
                    throw new FFmpegException("Cannot create audio buffer sink", ret);
                }

                BytePointer setBin = new BytePointer(4);
                setBin.asByteBuffer().putInt(enc_ctx.sample_fmt());
                av_opt_set_bin(buffersink_ctx, "sample_fmts", setBin, 4, AV_OPT_SEARCH_CHILDREN);

                if (ret < 0) {
                    throw new FFmpegException("Cannot set output sample format", ret);
                }

                setBin = new BytePointer(8);
                setBin.asByteBuffer().putLong(enc_ctx.channel_layout());
                av_opt_set_bin(buffersink_ctx, "channel_layouts", setBin, 8, AV_OPT_SEARCH_CHILDREN);

                if (ret < 0) {
                    throw new FFmpegException("Cannot set output channel layout", ret);
                }

                setBin = new BytePointer(4);
                setBin.asByteBuffer().putInt(enc_ctx.sample_rate());
                av_opt_set_bin(buffersink_ctx, "sample_rates", setBin, 4, AV_OPT_SEARCH_CHILDREN);

                if (ret < 0) {
                    throw new FFmpegException("Cannot set output sample rate", ret);
                }

            } else {
                throw new FFmpegException("initFilter: Not audio or video.", AVERROR_UNKNOWN);
            }

            // Endpoints for the filter graph.
            outputs.name(av_strdup(new BytePointer("in")));
            outputs.filter_ctx(buffersrc_ctx);
            outputs.pad_idx(0);
            outputs.next(null);

            inputs.name(av_strdup(new BytePointer("out")));
            inputs.filter_ctx(buffersink_ctx);
            inputs.pad_idx(0);
            inputs.next(null);

            if (outputs.name() == null || inputs.name() == null) {
                throw new FFmpegException("av_strdup: Not enough memory.", ENOMEM);
            }

            ret = avfilter_graph_parse_ptr(filter_graph, filter_spec,
                    inputs, outputs, null);

            if (ret < 0) {
                throw new FFmpegException("avfilter_graph_parse_ptr: Unable to create.", ret);
            }

            ret = avfilter_graph_config(filter_graph, null);

            if (ret < 0) {
                throw new FFmpegException("avfilter_graph_config: Unable to create.", ret);
            }

            /* Fill FilteringContext */
            fctx.buffersrc_ctx = buffersrc_ctx;
            fctx.buffersink_ctx = buffersink_ctx;
            fctx.filter_graph = filter_graph;


            AVFilterContext outFilterContext = avfilter_graph_get_filter(filter_graph, "out");

            if (outFilterContext == null) {
                throw new FFmpegException("avfilter_graph_get_filter: Unable to get 'out' filter.", AVERROR_UNKNOWN);
            }

            int outFilterInputs = outFilterContext.nb_inputs();

            if (outFilterInputs == 1) {
                if (decCodecType == AVMEDIA_TYPE_VIDEO) {
                    int height;
                    int width;
                    int format;
                    AVRational ar;
                    AVRational fr;
                    AVRational tb;

                    if (logger.isDebugEnabled()) {
                        height = enc_ctx.height();
                        width = enc_ctx.width();
                        format = enc_ctx.pix_fmt();
                        ar = enc_ctx.sample_aspect_ratio();
                        fr = enc_ctx.framerate();
                        tb = enc_ctx.time_base();

                        logger.debug("Before filter: h:{} w:{} fmt:{} ar:{}/{} fr:{}/{} tb:{}/{}",
                                height, width, format, ar.num(), ar.den(), fr.num(), fr.den(), tb.num(), tb.den());
                    }

                    AVFilterLink input = outFilterContext.inputs(0);
                    height = input.h();
                    width = input.w();
                    format = input.format();
                    ar = input.sample_aspect_ratio();
                    fr = input.frame_rate();
                    tb = input.time_base();

                    if (logger.isDebugEnabled()) {
                        logger.debug("After filter: h:{} w:{} fmt:{} ar:{}/{} fr:{}/{} tb: {}/{}",
                                height, width, format, ar.num(), ar.den(), fr.num(), fr.den(), tb.num(), tb.den());
                    }

                    enc_ctx.height(height);
                    enc_ctx.width(width);
                    enc_ctx.pix_fmt(format);
                    enc_ctx.sample_aspect_ratio(ar);
                    enc_ctx.framerate(fr);
                    enc_ctx.time_base(tb);

                    ret = avcodec_open2(enc_ctx, encoder, dict);
                    av_dict_free(dict);

                    if (ret < 0) {
                        logger.error("Cannot open video encoder. Error {}.", ret);
                    }
                }
            } else {
                throw new FFmpegException("nb_inputs: 'out' filter has " + outFilterInputs + " inputs.", AVERROR_UNKNOWN);
            }

        } catch (FFmpegException e) {
            if (filter_graph != null) {
                avfilter_graph_free(filter_graph);
            }
            throw e;
        } finally {
            avfilter_inout_free(inputs);
            avfilter_inout_free(outputs);
        }

        return ret;
    }

    private int initFilters() throws FFmpegException {
        String filter_spec;

        int ret;
        int codecType;

        int nbStreams = ctx.avfCtxInput.nb_streams();

        filter_ctx = new FilteringContext[nbStreams];

        for (int i = 0; i < nbStreams; i++) {

            filter_ctx[i] = new FilteringContext();

            codecType = ctx.avfCtxInput.streams(i).codec().codec_type();

            if ( !ctx.streamMap[i].transcode || !(
                    codecType == AVMEDIA_TYPE_AUDIO ||
                    codecType == AVMEDIA_TYPE_VIDEO)) {

                continue;
            }

            filter_ctx[i].buffersrc_ctx = new AVFilterContext();
            filter_ctx[i].buffersink_ctx = new AVFilterContext();
            filter_ctx[i].filter_graph = new AVFilterGraph();

            if (codecType == AVMEDIA_TYPE_VIDEO) {
                if (interlaced) {
                    filter_spec = ctx.videoEncodeSettings.get("deinterlace_filter");
                } else {
                    filter_spec = ctx.videoEncodeSettings.get("progressive_filter");
                }

                if (filter_spec == null) {
                    filter_spec = "fps=fps=opendct_fps:round=near";
                    logger.warn("No filter was specified. Using 'fps=fps=opendct_fps:round=near'." +
                            " To avoid this message, set 'deinterlace_filter' and" +
                            " 'progressive_filter' to 'null' or 'fps=fps=opendct_fps:round=near'" +
                            " in the profile.");
                } else {

                    if (filter_spec.contains("opendct_")) {
                        AVRational fullRate = ctx.avfCtxInput.streams(i).codec().framerate();
                        AVRational halfRate = av_mul_q(fullRate, av_make_q(1, 2));
                        AVRational doubleRate = av_mul_q(fullRate, av_make_q(2, 1));

                        filter_spec = filter_spec.replace("opendct_hfps", halfRate.num() + "/" + halfRate.den());
                        filter_spec = filter_spec.replace("opendct_fps", fullRate.num() + "/" + fullRate.den());
                        filter_spec = filter_spec.replace("opendct_dfps", doubleRate.num() + "/" + doubleRate.den());
                    }
                }
            } else {
                filter_spec = "anull"; /* passthrough (dummy) filter for audio */
            }

            ret = initFilter(filter_ctx[i], ctx.avfCtxInput.streams(i).codec(),
                    ctx.avfCtxOutput.streams(ctx.streamMap[i].outStreamIndex).codec(),
                    ctx.avfCtxOutput.streams(ctx.streamMap[i].outStreamIndex), filter_spec,
                    ctx.streamMap[i].iCodec, ctx.streamMap[i].iDict);

            if (ret != 0) {
                return ret;
            }
        }

        return 0;
    }

    private int encodeWriteFrame(AVFrame filt_frame, int stream_index, int got_frame[]) {
        int ret = 0;
        avcodec.AVPacket enc_pkt = new avcodec.AVPacket();

        if (got_frame == null || got_frame.length == 0) {
            logger.warn("got_frame will not be able to be used ByRef.");
            got_frame = new int[] { 0 };
        }

        //logger.trace("Encoding frame");
        // encode filtered frame
        enc_pkt.data(null);
        enc_pkt.size(0);
        av_init_packet(enc_pkt);

        if (ctx.streamMap[stream_index].iCodecType == AVMEDIA_TYPE_VIDEO) {
            ret = avcodec_encode_video2(ctx.streamMap[stream_index].oCodecContext, enc_pkt,
                    filt_frame, got_frame);
        } else if (ctx.streamMap[stream_index].iCodecType == AVMEDIA_TYPE_AUDIO) {
            ret = avcodec_encode_audio2(ctx.streamMap[stream_index].oCodecContext, enc_pkt,
                    filt_frame, got_frame);
        }

        av_frame_free(filt_frame);

        if (ret < 0) {
            return ret;
        }

        if (got_frame[0] == 0) {
            return 0;
        }

        //logPacket(ctx.avfCtxOutput, enc_pkt, "trans-enc-in");

        // prepare packet for muxing
        enc_pkt.stream_index(ctx.streamMap[stream_index].outStreamIndex);
        av_packet_rescale_ts(enc_pkt,
                ctx.streamMap[stream_index].oCodecRational,
                ctx.streamMap[stream_index].oStreamRational);

        //logPacket(ctx.avfCtxOutput, enc_pkt, "trans-enc-out");

        //logger.trace("Muxing frame");

        // mux encoded frame
        ret = av_interleaved_write_frame(ctx.avfCtxOutput, enc_pkt);

        if (encodedFrames[stream_index].addAndGet(1) == 1000) {
            long endTime = System.currentTimeMillis();
            if (startTime != endTime) {
                logger.debug("FPS: {}", (double)encodedFrames[stream_index].get() / (double)((endTime - startTime) / 1000));
            }
            encodedFrames[stream_index].set(0);
            startTime = endTime;
        }
        return ret;
    }

    private int filterEncodeWriteFrame(AVFrame frame, int stream_index) {
        int ret;
        AVFrame filt_frame;
        int got_frame[] = new int[] { 0 };

        //logger.trace("Pushing decoded frame to filters");
        // push the decoded frame into the filtergraph
        ret = av_buffersrc_add_frame_flags(filter_ctx[stream_index].buffersrc_ctx,
                frame, 0);
        if (ret < 0) {
            logger.error("Error while feeding the filtergraph");
            return ret;
        }

        // pull filtered frames from the filtergraph
        while (true) {
            filt_frame = av_frame_alloc();

            if (filt_frame == null) {
                ret = ENOMEM;
                break;
            }

            //logger.trace("Pulling filtered frame from filters");
            ret = av_buffersink_get_frame(filter_ctx[stream_index].buffersink_ctx,
                    filt_frame);

            if (ret < 0) {
            /* if no more frames for output - returns AVERROR(EAGAIN)
             * if flushed and no more frames for output - returns AVERROR_EOF
             * rewrite retcode to 0 to show it as normal procedure completion
             */
                if (ret == AVERROR_EOF || ret == EAGAIN) {
                    ret = 0;
                }

                av_frame_free(filt_frame);
                break;
            }

            filt_frame.pict_type(AV_PICTURE_TYPE_NONE);
            ret = encodeWriteFrame(filt_frame, stream_index, got_frame);

            if (ret < 0) {
                break;
            }
        }

        return ret;
    }

    private int flushEncoder(int stream_index) {
        if (ctx.streamMap == null ||
                ctx.streamMap.length <= stream_index ||
                ctx.streamMap[stream_index].outStreamIndex == NO_STREAM_IDX) {

            return 0;
        }

        int ret;
        int got_frame[] = new int[] { 0 };

        if ((ctx.avfCtxOutput.streams(ctx.streamMap[stream_index].outStreamIndex).codec().codec().capabilities() &
                AV_CODEC_CAP_DELAY) == 0) {

            return 0;
        }

        while (true) {
            logger.debug("Flushing stream #{} encoder", stream_index);
            ret = encodeWriteFrame(null, stream_index, got_frame);

            if (ret < 0) {
                break;
            }

            if (got_frame[0] == 0) {
                return 0;
            }
        }

        return ret;
    }
}
