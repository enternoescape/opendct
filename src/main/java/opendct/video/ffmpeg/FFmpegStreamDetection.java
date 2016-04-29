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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;

import static opendct.video.ffmpeg.FFmpegUtil.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;

public class FFmpegStreamDetection {
    private static final Logger logger = LogManager.getLogger(FFmpegStreamDetection.class);

    /**
     * Detect at least one video and all audio streams for a program.
     * <p/>
     * If the FFmpeg context provided has a desired program in it, this detection method will focus
     * on getting at least one video and all audio streams in that program. Otherwise it will return
     * a best effort after exhausting the maximum probe size.
     *
     * @param ctx The FFmpeg context to be used for the stream detection.
     * @param nativeFilename The filename to be read if native mode is enabled. Otherwise this can
     *                       be <i>null</i>.
     * @param error An array with at a length of at least 1. If <i>false</i> is returned, an error
     *              message will be put in this array. If no array was provided or had a length of
     *              0, no error can be returned.
     * @return <i>true</i> if at least one video and all audio streams in a program have been
     *         detected.
     * @throws IllegalStateException Thrown if there are any problems allocating contexts. All
     *                               contexts created by this function will already be de-allocated
     *                               before the exception is returned.
     */
    public static boolean detectStreams(FFmpegContext ctx, String nativeFilename, String error[]) throws FFmpegException {

        long startTime = System.currentTimeMillis();

        // This is the smallest probe size allowed.
        final long minProbeSize = FFmpegConfig.getMinProbeSize();

        // This is the smallest probe duration allowed.
        final long minAnalyzeDuration = FFmpegConfig.getMinAnalyseDuration();

        // This is the largest analyze duration allowed. 5,000,000 is the minimum allowed value.
        final long maxAnalyzeDuration = FFmpegConfig.getMaxAnalyseDuration();

        if (error == null || error.length == 0) {
            // Nothing will return in this case, but this also means we don't need to check for this
            // condition throughout the detection process.
            error = new String[1];
        }

        ctx.setProbeData(nativeFilename);

        boolean finalCheck = false;
        long dynamicProbeSize = minProbeSize;
        long dynamicAnalyzeDuration = minAnalyzeDuration;
        final long probeSizeLimit = Math.max(ctx.getProbeMaxSize() - 1123474, 1123474);

        ctx.SEEK_BUFFER.setNoWrap(true);

        long startNanoTime = System.nanoTime();

        while (true) {

            int ret;

            //
            // A new input AVFormatContext must be created for each avformat_find_stream_info probe or the JVM will crash.
            // avformat_open_input seems to even use the probe size/max analyze duration values so they're set too
            // before the avformat_find_stream_info() call.
            // Although the AVIOContext does not need to be allocated/freed for each probe it is done so the counters
            // for bytes read, seeks done, etc are reset.
            //
            try {
                switch (ctx.inputFileMode) {
                    case FFmpegContext.FILE_MODE_MPEGTS:
                        ctx.allocInputIoTsFormatContext();
                        break;
                    case FFmpegContext.FILE_MODE_NATIVE:
                        ctx.allocInputFormatNativeContext(nativeFilename, null);
                        break;
                    default:
                        error[0] = "Invalid file input mode selected: " + ctx.inputFileMode;
                        return false;
                }
            } catch (FFmpegException e) {
                logger.error("Unable to start stream detection => ", e);
                return false;
            }

            // By adding 188 to the available bytes, we can be reasonably sure we will not return
            // here until the available data has increased.
            dynamicProbeSize = Math.max(dynamicProbeSize, ctx.getProbeAvailable() + 188);
            dynamicProbeSize = Math.min(dynamicProbeSize, probeSizeLimit);

            dynamicAnalyzeDuration = Math.max(dynamicAnalyzeDuration, (System.nanoTime() - startNanoTime) / 1000L);

            av_opt_set_int(ctx.avfCtxInput, "probesize", dynamicProbeSize, 0); // Must be set before avformat_open_input
            av_opt_set_int(ctx.avfCtxInput, "analyzeduration", dynamicAnalyzeDuration, 0); // Must be set before avformat_find_stream_info

            logger.debug("Calling avformat_open_input");

            ret = avformat_open_input(ctx.avfCtxInput, ctx.inputFilename, null, null);
            if (ret != 0) {
                error[0] = "avformat_open_input returned error code " + ret;
                return false;
            }

            if (ctx.isInterrupted()) {
                error[0] = FFMPEG_INIT_INTERRUPTED;
                return false;
            }

            logger.info("Before avformat_find_stream_info() pos={} bytes_read={} seek_count={}. probesize: {} analyzeduration: {}.",
                    ctx.avioCtxInput.pos(), ctx.avioCtxInput.bytes_read(), ctx.avioCtxInput.seek_count(), dynamicProbeSize, dynamicAnalyzeDuration);
            ret = avformat_find_stream_info(ctx.avfCtxInput, (PointerPointer<avutil.AVDictionary>) null);
            logger.info("After avformat_find_stream_info() pos={} bytes_read={} seek_count={}. probesize: {} analyzeduration: {}.",
                    ctx.avioCtxInput.pos(), ctx.avioCtxInput.bytes_read(), ctx.avioCtxInput.seek_count(), dynamicProbeSize, dynamicAnalyzeDuration);

            // -541478725 is the error returned when the buffer is underrun which should only be
            // happening if the stream completely stops or we reach the end of the buffer. We pass
            // this though as a success because if we don't, the only way detection will complete
            // successfully if the desired program isn't found is to let this finish.
            if (ret < 0 && -ret != -541478725) {
                error[0] = "avformat_find_stream_info() failed with error code " + -ret + ".";
                if (dynamicProbeSize == probeSizeLimit) {
                    return false;
                }
                logger.info(error[0] + " Trying again with more data.");

                ctx.deallocInputContext();

                try {
                    // For a yet to be determined reason, sleeping here for 1/5 of a second makes
                    // video detection more reliable.
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    if (ctx.isInterrupted()) {
                        error[0] = FFMPEG_INIT_INTERRUPTED;
                        return false;
                    }
                }

                continue;
            }

            if (ctx.isInterrupted()) {
                error[0] = FFMPEG_INIT_INTERRUPTED;
                return false;
            }

            // While we haven't seen all streams for the desired program and we haven't exhausted our attempts, try again...
            if (ctx.desiredProgram > 0 && dynamicProbeSize != probeSizeLimit && !FFmpegUtil.findAllStreamsForDesiredProgram(ctx.avfCtxInput, ctx.desiredProgram)) {
                logger.info("Desired program set. Stream details unavailable for one or more streams. {}", TRYING_AGAIN);

                ctx.deallocInputContext();
                continue;
            }

            ctx.preferredVideo = av_find_best_stream(ctx.avfCtxInput, AVMEDIA_TYPE_VIDEO, NO_STREAM_IDX, NO_STREAM_IDX, (PointerPointer<avcodec.AVCodec>) null, 0);

            if (ctx.preferredVideo != AVERROR_STREAM_NOT_FOUND) {
                ctx.videoInCodecCtx = getCodecContext(ctx.avfCtxInput.streams(ctx.preferredVideo));
            }

            if (ctx.videoInCodecCtx == null) {
                if (ctx.isInterrupted()) {
                    error[0] = FFMPEG_INIT_INTERRUPTED;
                    return false;
                }

                error[0] = "Could not find a video stream.";
                if (dynamicProbeSize == probeSizeLimit) {
                    return false;
                }
                logger.info("{}{}", error[0], TRYING_AGAIN);

                ctx.deallocInputContext();

                try {
                    // For a yet to be determined reason, sleeping here for 1/4 of a second makes
                    // video detection more reliable.
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    if (ctx.isInterrupted()) {
                        error[0] = FFMPEG_INIT_INTERRUPTED;
                        return false;
                    }
                }

                continue;
            }

            if (ctx.isInterrupted()) {
                error[0] = FFMPEG_INIT_INTERRUPTED;
                return false;
            }

            if (ctx.desiredProgram <= 0) {
                int programs = ctx.avfCtxInput.nb_programs();

                for (int i = 0; i < programs; i++) {
                    AVProgram program = ctx.avfCtxInput.programs(i);

                    IntPointer streamIndexes = program.stream_index();
                    int numStreamIndexes = program.nb_stream_indexes();
                    for (int j = 0; j < numStreamIndexes; j++) {
                        int streamIndex = streamIndexes.get(j);

                        if (streamIndex == ctx.preferredVideo) {
                            ctx.desiredProgram = program.id();
                            break;
                        }
                    }

                    if (ctx.desiredProgram > 0) {
                        break;
                    }
                }

                if (ctx.isInterrupted()) {
                    error[0] = FFMPEG_INIT_INTERRUPTED;
                    return false;
                }

                // This really should not be happening because it would mean that we found a valid
                // video stream that doesn't belong to any program.
                if (ctx.desiredProgram <= 0) {
                    logger.info("Unable to determine primary program.{}", TRYING_AGAIN);

                    ctx.deallocInputContext();
                    continue;
                }

                if (dynamicProbeSize != probeSizeLimit && !FFmpegUtil.findAllStreamsForDesiredProgram(ctx.avfCtxInput, ctx.desiredProgram)) {
                    logger.info("Stream details unavailable for one or more streams, program is now {}.{}", ctx.desiredProgram, TRYING_AGAIN);

                    ctx.deallocInputContext();
                    continue;
                } else {
                    logger.info("Primary program has been detected: {}.", ctx.desiredProgram);
                }
            }

            ctx.preferredAudio = findBestAudioStream(ctx.avfCtxInput);

            if (ctx.preferredAudio != AVERROR_STREAM_NOT_FOUND) {
                ctx.audioInCodecCtx = getCodecContext(ctx.avfCtxInput.streams(ctx.preferredAudio));
            }

            if (ctx.audioInCodecCtx == null) {
                if (ctx.isInterrupted()) {
                    error[0] = FFMPEG_INIT_INTERRUPTED;
                    return false;
                }

                error[0] = "Could not find an audio stream.";
                if (dynamicProbeSize == probeSizeLimit) {
                    return false;
                }
                logger.info(error[0] + TRYING_AGAIN);

                ctx.deallocInputContext();

                try {
                    // For a yet to be determined reason, sleeping here for 1/4 of a second makes
                    // audio detection more reliable.
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    if (ctx.isInterrupted()) {
                        error[0] = FFMPEG_INIT_INTERRUPTED;
                        return false;
                    }
                }

                continue;
            }

            if (!finalCheck && dynamicProbeSize != probeSizeLimit) {
                finalCheck = true;

                AVStream videoStream = ctx.avfCtxInput.streams(ctx.preferredVideo);

                int num = videoStream.codec().sample_aspect_ratio().num();
                int den = videoStream.codec().sample_aspect_ratio().den();

                // This should fix most situations whereby the returned aspect ratio might be off.
                if (num <= 1 || den <= 1) {
                    logger.info("SAR is {}/{}. This could be incorrect." +
                            " Trying again one more time.", num, den);
                    ctx.deallocInputContext();
                    continue;
                }
            }

            break;
        }

        if (ctx.isInterrupted()) {
            error[0] = FFMPEG_INIT_INTERRUPTED;
            return false;
        }

        ctx.SEEK_BUFFER.setNoWrap(false);

        long endTime = System.currentTimeMillis();
        logger.debug("FFmpeg stream detection done in {}ms,", endTime - startTime);

        return true;
    }
}
