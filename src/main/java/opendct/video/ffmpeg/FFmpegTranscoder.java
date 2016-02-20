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

import opendct.config.ConfigBag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static opendct.video.ffmpeg.FFmpegUtil.*;
import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avfilter.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.sws_scale;

public class FFmpegTranscoder implements FFmpegStreamProcessor {
    private static final Logger logger = LogManager.getLogger(FFmpegTranscoder.class);

    private static boolean trancodeOnInterlace = true;

    private AtomicLong totalVideoFrames = new AtomicLong(0);
    private long startTime = 0;


    private FFmpegContext ctx = null;
    private boolean interlaced = false;
    private FilteringContext filter_ctx[];

    private class FilteringContext {
        private avfilter.AVFilterContext buffersink_ctx;
        private avfilter.AVFilterContext buffersrc_ctx;
        private avfilter.AVFilterGraph filter_graph;

        private int height;
        private int width;
    }

    @Override
    public void initStreamOutput(FFmpegContext ctx, String outputFilename) throws FFmpegException, InterruptedException {

        logger.info("Initializing FFmpeg transcoder stream output.");

        if (ctx.isInterrupted()) {
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        ctx.dumpInputFormat();

        // Creates the output container and AVFormatContext.
        ctx.allocAvfContainerOutputContext(outputFilename);

        //TODO: Base this on an actual content check.
        if (ctx.videoCodecCtx != null && (ctx.videoCodecCtx.height() == 1080 || ctx.videoCodecCtx.height() == 480)) {
            interlaced = true;
        }

        int numInputStreams = ctx.avfCtxInput.nb_streams();

        ctx.streamMap = new int[numInputStreams];
        Arrays.fill(ctx.streamMap, NO_STREAM_IDX);

        ctx.transcodeMap = new boolean[numInputStreams];
        Arrays.fill(ctx.transcodeMap, false); // It should be this by default, but let's not assume.

        ctx.swsCtx = new swscale.SwsContext[numInputStreams];
        ctx.swsFrame = new AVFrame[numInputStreams];
        filter_ctx = new FilteringContext[numInputStreams];

        if (trancodeOnInterlace && interlaced) {
            int ret = avcodec_open2(ctx.videoCodecCtx,
                    avcodec_find_decoder(ctx.videoCodecCtx.codec_id()), (PointerPointer<AVDictionary>) null);

            if (ret < 0) {
                throw new FFmpegException("Failed to open decoder for stream #" + ctx.preferredVideo, ret);
            }

            if ((ctx.videoStream = addTranscodeVideoStreamToContext(ctx, ctx.preferredVideo, new ConfigBag("mpeg2", "ffvideo", false))) == null) {
                throw new FFmpegException("Could not find a video stream", -1);
            }
        } else {
            if ((ctx.videoStream = addCopyStreamToContext(ctx.avfCtxOutput, ctx.videoCodecCtx)) == null) {
                throw new FFmpegException("Could not find a video stream", -1);
            }
        }

        if ((ctx.audioStream = addCopyStreamToContext(ctx.avfCtxOutput, ctx.audioCodecCtx)) == null) {
            throw new FFmpegException("Could not find a audio stream", -1);
        }

        ctx.streamMap[ctx.preferredVideo] = ctx.videoStream.id();
        ctx.transcodeMap[ctx.preferredVideo] = trancodeOnInterlace && interlaced;
        ctx.streamMap[ctx.preferredAudio] = ctx.audioStream.id();

        for (int idx = 0; idx < numInputStreams; ++idx) {
            if (ctx.streamMap[idx] != NO_STREAM_IDX) {
                continue;
            }

            avcodec.AVCodecContext codecCtx = getCodecContext(ctx.avfCtxInput.streams(idx));

            if (codecCtx != null) {
                avformat.AVStream avsOutput = addCopyStreamToContext(ctx.avfCtxOutput, codecCtx);

                if (avsOutput != null) {
                    ctx.streamMap[idx] = avsOutput.id();
                }
            }
        }

        if (ctx.isInterrupted()) {
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        ctx.dumpOutputFormat();

        ctx.allocIoOutputContext();

        if (ctx.isInterrupted()) {
            throw new InterruptedException(FFMPEG_INIT_INTERRUPTED);
        }

        logger.debug("Writing header");

        int ret = avformat_write_header(ctx.avfCtxOutput, (PointerPointer<avutil.AVDictionary>) null);
        if (ret < 0) {
            throw new FFmpegException("Error while writing header to file '" + outputFilename + "'", ret);
        }

        this.ctx = ctx;

        logger.info("Initialized FFmpeg transcoder stream output.");
    }

    @Override
    public void streamOutput() throws FFmpegException {
        int ret = 0;

        AVPacket packet = new AVPacket();
        packet.data(null);
        packet.size(0);

        int streamIndex;
        int type;
        int got_frame[] = new int[] { 0 };

        AVFrame frame = av_frame_alloc();

        try {
            if ((ret = initFilters()) < 0) {
                throw new FFmpegException("Unable to allocate filters.", ret);
            }

            while (true) {

                ret = av_read_frame(ctx.avfCtxInput, packet);
                if (ret < 0) {
                    break;
                }

                streamIndex = packet.stream_index();

                if (streamIndex >= ctx.streamMap.length ||
                        ctx.streamMap[streamIndex] == NO_STREAM_IDX) {

                    av_packet_unref(packet);
                    continue;
                }

                type = ctx.avfCtxInput.streams(streamIndex).codec().codec_type();
                logger.trace("Demuxer gave frame of streamIndex {}",
                        streamIndex);

                if (filter_ctx[streamIndex].filter_graph != null) {

                    logger.trace("Going to re-encode & filter the frame");

                    frame = av_frame_alloc();
                    if (frame == null) {
                        throw new FFmpegException("av_frame_alloc: Unable to allocate frame.", -1);
                    }

                    av_packet_rescale_ts(packet,
                            ctx.avfCtxInput.streams(streamIndex).time_base(),
                            ctx.avfCtxInput.streams(streamIndex).codec().time_base());

                    if (type == AVMEDIA_TYPE_VIDEO) {
                        ret = avcodec_decode_video2(ctx.avfCtxInput.streams(streamIndex).codec(), frame,
                                got_frame, packet);
                    } else {
                        ret = avcodec_decode_audio4(ctx.avfCtxInput.streams(streamIndex).codec(), frame,
                                got_frame, packet);
                    }

                    if (ret < 0) {
                        av_frame_free(frame);
                        logger.error("Decoding failed");
                        continue;
                    }

                    if (got_frame[0] != 0) {
                        frame.pts(av_frame_get_best_effort_timestamp(frame));
                        ret = filterEncodeWriteFrame(frame, streamIndex);
                        av_frame_free(frame);

                        if (ret < 0) {
                            logger.error("Error from filterEncodeWriteFrame: {}", ret);
                            continue;
                            //throw new FFmpegException("Error from filterEncodeWriteFrame.", ret);
                        }
                    } else {
                        av_frame_free(frame);
                    }
                } else {
                    // remux this frame without re-encoding
                    av_packet_rescale_ts(packet,
                            ctx.avfCtxInput.streams(streamIndex).time_base(),
                            ctx.avfCtxOutput.streams(ctx.streamMap[streamIndex]).time_base());

                    packet.stream_index(ctx.streamMap[streamIndex]);

                    ret = av_interleaved_write_frame(ctx.avfCtxOutput, packet);

                    if (ret < 0) {
                        logger.error("Error from av_interleaved_write_frame: {}", ret);
                    }
                }

                av_packet_unref(packet);
            }

            int nbStreams = ctx.avfCtxInput.nb_streams();

        /* flush filters and encoders */
            for (int i = 0; i < nbStreams; i++) {

            /* flush filter */
                if (filter_ctx[i].filter_graph == null)
                    continue;

                ret = filterEncodeWriteFrame(null, i);

                if (ret < 0) {
                    throw new FFmpegException("Flushing filter failed", ret);
                }

            /* flush encoder */
                ret = flushEncoder(i);
                if (ret < 0) {
                    throw new FFmpegException("Flushing encoder failed", ret);
                }
            }

            av_write_trailer(ctx.avfCtxOutput);
        } finally {
            // Cleanup.
            endStreamOutput(packet, frame);
            logger.info("FFmpeg transcoder ended with code {}", ret);
        }
    }


    private void endStreamOutput(AVPacket packet, AVFrame frame) {
        av_packet_unref(packet);
        av_frame_free(frame);

        int nbStreams = ctx.avfCtxInput.nb_streams();

        for (int i = 0; i < nbStreams; i++) {
            avcodec_close(ctx.avfCtxInput.streams(i).codec());


            if (ctx.streamMap[i] != NO_STREAM_IDX && ctx.avfCtxOutput != null &&
                    ctx.avfCtxOutput.nb_streams() > i &&
                    ctx.avfCtxOutput.streams(ctx.streamMap[i]) != null &&
                    ctx.avfCtxOutput.streams(ctx.streamMap[i]).codec() != null) {
                avcodec_close(ctx.avfCtxOutput.streams(ctx.streamMap[i]).codec());
            }

            if (filter_ctx != null && filter_ctx[i].filter_graph != null)
                avfilter_graph_free(filter_ctx[i].filter_graph);

        }

        avformat_close_input(ctx.avfCtxInput);
        ctx.avfCtxInput = null;
        ctx.avioCtxInput = null;

        if (ctx.avfCtxOutput != null && (ctx.avfCtxOutput.oformat().flags() & AVFMT_NOFILE) != 0)
            avio_closep(ctx.avfCtxOutput.pb());
        avformat_free_context(ctx.avfCtxOutput);
        ctx.avfCtxOutput = null;
        ctx.avioCtxOutput = null;

    }

    private int initFilter(FilteringContext fctx, avcodec.AVCodecContext dec_ctx,
                           avcodec.AVCodecContext enc_ctx, final String filter_spec) throws FFmpegException {

        int ret = 0;
        avfilter.AVFilter buffersrc;
        avfilter.AVFilter buffersink;
        avfilter.AVFilterContext buffersrc_ctx;
        avfilter.AVFilterContext buffersink_ctx;
        avfilter.AVFilterInOut outputs = avfilter_inout_alloc();
        avfilter.AVFilterInOut inputs = avfilter_inout_alloc();
        avfilter.AVFilterGraph filter_graph = avfilter_graph_alloc();

        try {

            if (outputs == null || inputs == null || filter_graph == null) {
                throw new FFmpegException("Not enough memory available", ENOMEM);
            }

            if (dec_ctx.codec_type() == AVMEDIA_TYPE_VIDEO) {
                buffersrc = avfilter_get_by_name("buffer");
                buffersink = avfilter_get_by_name("buffersink");
                if (buffersrc == null || buffersink == null) {
                    throw new FFmpegException("Filtering source or sink element not found", AVERROR_UNKNOWN);
                }

            /*String parameters = String.format(
                    "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:frame_rate=%d/%d:pixel_aspect=%d/%d",
                    dec_ctx.width(), dec_ctx.height(), dec_ctx.pix_fmt(),
                    dec_ctx.time_base().num(), dec_ctx.time_base().den(),
                    dec_ctx.framerate().num(), dec_ctx.framerate().den(),
                    dec_ctx.sample_aspect_ratio().num(), dec_ctx.sample_aspect_ratio().den());*/

                String parameters = String.format(
                        "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
                        dec_ctx.width(), dec_ctx.height(), dec_ctx.pix_fmt(),
                        dec_ctx.time_base().num(), dec_ctx.time_base().den(),
                        dec_ctx.framerate().num(), dec_ctx.framerate().den(),
                        dec_ctx.sample_aspect_ratio().num(),
                        dec_ctx.sample_aspect_ratio().den());

                ret = avfilter_graph_create_filter(buffersrc_ctx = new AVFilterContext(null), buffersrc, "in",
                        parameters, null, filter_graph);
                if (ret < 0) {
                    throw new FFmpegException("Cannot create buffer source", ret);
                }

                ret = avfilter_graph_create_filter(buffersink_ctx = new AVFilterContext(null), buffersink, "out",
                        null, null, filter_graph);
                if (ret < 0) {
                    throw new FFmpegException("Cannot create buffer sink", ret);
                }

                BytePointer setBin = new BytePointer(4);
                setBin.asByteBuffer().putInt(enc_ctx.pix_fmt());
                av_opt_set_bin(buffersink_ctx, "pix_fmts", setBin, 4, AV_OPT_SEARCH_CHILDREN);

                if (ret < 0) {
                    throw new FFmpegException("Cannot set output pixel format", ret);
                }
            } else if (dec_ctx.codec_type() == AVMEDIA_TYPE_AUDIO) {
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

        } catch (FFmpegException e) {
            if (filter_graph != null) {
                avfilter_graph_free(filter_graph);
            }
        } finally {
            endInitFilter(inputs, outputs);
        }

        return ret;
    }

    protected void endInitFilter(AVFilterInOut inputs, AVFilterInOut outputs) {
        avfilter_inout_free(inputs);
        avfilter_inout_free(outputs);
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

            if ( !ctx.transcodeMap[i] || !(
                    codecType == AVMEDIA_TYPE_AUDIO ||
                    codecType == AVMEDIA_TYPE_VIDEO)) {

                continue;
            }

            filter_ctx[i].buffersrc_ctx = new AVFilterContext();
            filter_ctx[i].buffersink_ctx = new AVFilterContext();
            filter_ctx[i].filter_graph = new AVFilterGraph();

            if (codecType == AVMEDIA_TYPE_VIDEO) {
                filter_spec = "null"; /* passthrough (dummy) filter for video */
                //filter_spec = "yadif=mode=1, scale=w=1280:h=720, setpts=0.5*PTS";
                //filter_spec = "yadif=mode=1, setpts=0.5*PTS";
                filter_spec = "yadif=mode=2, scale=w=1280:h=720, setpts=0.5*PTS";

                filter_ctx[i].width = 720;
                filter_ctx[i].height = 1280;
            } else {
                filter_spec = "anull"; /* passthrough (dummy) filter for audio */
            }

            ret = initFilter(filter_ctx[i], ctx.avfCtxInput.streams(i).codec(),
                    ctx.avfCtxOutput.streams(ctx.streamMap[i]).codec(), filter_spec);

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

        logger.info("Encoding frame");
    /* encode filtered frame */
        enc_pkt.data(null);
        enc_pkt.size(0);
        av_init_packet(enc_pkt);

        int codecType = ctx.avfCtxInput.streams(stream_index).codec().codec_type();

        if (codecType == AVMEDIA_TYPE_VIDEO) {
            ret = avcodec_encode_video2(ctx.avfCtxOutput.streams(ctx.streamMap[stream_index]).codec(), enc_pkt,
                    filt_frame, got_frame);
        } else if (codecType == AVMEDIA_TYPE_AUDIO) {
            ret = avcodec_encode_audio2(ctx.avfCtxOutput.streams(ctx.streamMap[stream_index]).codec(), enc_pkt,
                    filt_frame, got_frame);
        }

        av_frame_free(filt_frame);

        if (ret < 0) {
            return ret;
        }

        if (got_frame[0] == 0) {
            return 0;
        }

    /* prepare packet for muxing */
        enc_pkt.stream_index(ctx.streamMap[stream_index]);
        av_packet_rescale_ts(enc_pkt,
                ctx.avfCtxOutput.streams(ctx.streamMap[stream_index]).codec().time_base(),
                ctx.avfCtxOutput.streams(ctx.streamMap[stream_index]).time_base());

        logger.debug("Muxing frame");
    /* mux encoded frame */
        ret = av_interleaved_write_frame(ctx.avfCtxOutput, enc_pkt);
        return ret;
    }

    private int filterEncodeWriteFrame(AVFrame frame, int stream_index) {
        int ret;
        AVFrame filt_frame;
        int got_frame[] = new int[] { 0 };

        logger.info("Pushing decoded frame to filters");
    /* push the decoded frame into the filtergraph */
        ret = av_buffersrc_add_frame_flags(filter_ctx[stream_index].buffersrc_ctx,
                frame, 0);
        if (ret < 0) {
            logger.error("Error while feeding the filtergraph");
            return ret;
        }

    /* pull filtered frames from the filtergraph */
        while (true) {
            filt_frame = av_frame_alloc();

            if (filt_frame == null) {
                ret = ENOMEM;
                break;
            }

            logger.info("Pulling filtered frame from filters");
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

            if (stream_index == ctx.preferredVideo) {
                AVCodecContext enc_ctx = ctx.avfCtxOutput.streams(ctx.streamMap[stream_index]).codec();

                // This converts the image to the correct pixel format before encoding. Without this
                // step, MPEG-2 encoded video looks terrible.
                sws_scale(ctx.swsCtx[stream_index], filt_frame.data(), filt_frame.linesize(), 0,
                        enc_ctx.height(), ctx.swsFrame[stream_index].data(), ctx.swsFrame[stream_index].linesize());

                // This only copies the data from the scaled frame back. This way we don't need to
                // keep re-allocating the swscaler target frame.
                av_frame_copy(filt_frame, ctx.swsFrame[stream_index]);
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
        if (ctx.streamMap[stream_index] == NO_STREAM_IDX) {
            return 0;
        }

        int ret;
        int got_frame[] = new int[] { 0 };

        if ((ctx.avfCtxOutput.streams(ctx.streamMap[stream_index]).codec().codec().capabilities() &
                AV_CODEC_CAP_DELAY) == 0) {

            return 0;
        }

        while (true) {
            logger.info("Flushing stream #{} encoder", stream_index);
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
