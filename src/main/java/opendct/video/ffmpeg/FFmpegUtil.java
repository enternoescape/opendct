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

package opendct.video.ffmpeg;

import opendct.config.Config;
import opendct.config.ConfigBag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.avformat.*;
import org.bytedeco.javacpp.avutil.*;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avfilter.avfilter_register_all;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.SWS_BILINEAR;
import static org.bytedeco.javacpp.swscale.sws_getContext;

public abstract class FFmpegUtil {
    private static final Logger logger = LogManager.getLogger(FFmpegUtil.class);

    public static final FFmpegLogger logCallback = new FFmpegLogger();
    private static final Object isInitSync = new Object();
    private static boolean isInit = false;

    public static final int EAGAIN = -11;
    public static final int ENOMEM = -12;
    public static final int NO_STREAM_IDX = -1;
    public static final String FFMPEG_INIT_INTERRUPTED = "FFmpeg initialization was interrupted.";
    public static final String TRYING_AGAIN = " Trying again with an extended probe.";
    private static final boolean LOG_STREAM_DETAILS_FOR_ALL_PROGRAMS = Config.getBoolean("consumer.ffmpeg.log_stream_details_for_all_programs", false);

    /**
     * Initialize all one time FFmpeg common configuration.
     * <p/>
     * Additional calls to this method will just return without doing anything. This method is
     * thread-safe.
     */
    public static void initAll() {

        // This will prevent this method from causing a pileup of waiting threads once everything is
        // one time configured. The sync is needed to make sure that nothing that requires this
        // configuration is allowed to proceed until they are configured.
        if (isInit) {
            return;
        }

        synchronized (isInitSync) {
            if (!isInit) {
                av_log_set_callback(logCallback);

                av_register_all();
                avfilter_register_all();
                avcodec_register_all();

                isInit = true;
            }
        }
    }

    public static boolean findAllStreamsForDesiredProgram(AVFormatContext ic, int desiredProgram) {
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
                    avcodec.AVCodecContext avctx = st.codec();
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

    public static avcodec.AVCodecContext getCodecContext(AVStream inputStream) {
        avcodec.AVCodecContext codecCtxInput = inputStream.codec();
        int codecType = codecCtxInput.codec_type();
        boolean isStreamValid =
                (codecType == AVMEDIA_TYPE_AUDIO || codecType == AVMEDIA_TYPE_VIDEO || codecType == AVMEDIA_TYPE_SUBTITLE) &&
                        ((codecType != AVMEDIA_TYPE_VIDEO || (codecCtxInput.width() != 0 && codecCtxInput.height() != 0)) &&
                                (codecType != AVMEDIA_TYPE_AUDIO || codecCtxInput.channels() != 0));

        return isStreamValid ? codecCtxInput : null;
    }

    public static int findBestAudioStream(AVFormatContext inputContext) {
        int preferredAudio = -1;
        int maxChannels = 0;
        int maxFrames = 0;
        boolean hasAudio = false;
        int numStreams = inputContext.nb_streams();

        for (int streamIndex = 0; streamIndex < numStreams; streamIndex++) {
            AVStream stream = inputContext.streams(streamIndex);
            avcodec.AVCodecContext codec = stream.codec();
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

        if (hasAudio && preferredAudio == -1) {
            preferredAudio = av_find_best_stream(inputContext, AVMEDIA_TYPE_AUDIO, -1, -1, (PointerPointer<avcodec.AVCodec>) null, 0);
        }

        return preferredAudio;
    }

    public static AVStream addCopyStreamToContext(AVFormatContext outputContext, avcodec.AVCodecContext codecCtxInput) {
        AVStream avsOutput = avformat_new_stream(outputContext, codecCtxInput.codec());

        if (avsOutput == null) {
            logger.error("Could not allocate stream");
            return null;
        }

        avcodec.AVCodecContext codecCtxOutput = avsOutput.codec();

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

    /**
     * Adds a stream with transcoding for a video or audio stream.
     *
     * @param ctx This is the FFmpeg context to update.
     * @param stream_id This is the stream to be added
     * @param profile This is a properties file containing the desired settings.
     * @return The new AVStream if successful.
     */
    public static AVStream addTranscodeVideoStreamToContext(FFmpegContext ctx, int stream_id, ConfigBag profile) {
        AVStream out_stream = avformat_new_stream(ctx.avfCtxOutput, null);

        if (out_stream == null) {
            logger.error("Could not allocate output stream");
            return null;
        }

        AVStream in_stream = ctx.avfCtxInput.streams(stream_id);

        AVCodecContext dec_ctx = in_stream.codec();
        AVCodecContext enc_ctx = out_stream.codec();

        int decoderCodecType = dec_ctx.codec_type();

        if (decoderCodecType != AVMEDIA_TYPE_VIDEO
                && decoderCodecType != AVMEDIA_TYPE_AUDIO) {
            logger.error("Transcoding only supports video and audio types.");;
            return null;
        }

        AVCodec encoder = null;
        AVDictionary dict = new AVDictionary(null);

        if (decoderCodecType == AVMEDIA_TYPE_VIDEO) {
            encoder = avcodec_find_encoder(AV_CODEC_ID_MPEG2VIDEO);

            if (encoder == null) {
                logger.fatal("Necessary video encoder not found");
                return null;
            }

            enc_ctx.height(720);
            enc_ctx.width(1280);

            // take first format from list of supported formats
            enc_ctx.sample_aspect_ratio(dec_ctx.sample_aspect_ratio());
            enc_ctx.pix_fmt(encoder.pix_fmts().get(0));

            // video time_base can be set to whatever is handy and supported by encoder
            enc_ctx.time_base(dec_ctx.time_base());

            enc_ctx.framerate(dec_ctx.framerate());

            enc_ctx.bit_rate(14 * 1000 * 1000);

            enc_ctx.gop_size(15);
            enc_ctx.max_b_frames(2);
            av_dict_set(dict, "me_method", "epzs", 0);


            // Determine required buffer size and allocate buffer
            int numBytes = avpicture_get_size(enc_ctx.pix_fmt(),
                    enc_ctx.width(), enc_ctx.height());
            BytePointer buffer = new BytePointer(av_malloc(numBytes));

            ctx.swsCtx[stream_id] = sws_getContext(dec_ctx.width(), dec_ctx.height(),
                    dec_ctx.pix_fmt(), enc_ctx.width(), enc_ctx.height(),
                    enc_ctx.pix_fmt(), SWS_BILINEAR, null, null, (DoublePointer) null);

            // Assign appropriate parts of buffer to image planes in swsFrame
            // Note that swsFrame is an AVFrame, but AVFrame is a superset
            // of AVPicture
            avpicture_fill(new AVPicture(ctx.swsFrame[stream_id] = av_frame_alloc()), buffer, enc_ctx.pix_fmt(),
                    enc_ctx.width(), enc_ctx.height());

        } else {
            encoder = avcodec_find_encoder(dec_ctx.codec_id());

            if (encoder == null) {
                logger.fatal("Necessary audio encoder not found");
                return null;
            }

            enc_ctx.sample_rate(dec_ctx.sample_rate());
            enc_ctx.channel_layout(dec_ctx.channel_layout());
            enc_ctx.channels(av_get_channel_layout_nb_channels(enc_ctx.channel_layout()));
                /* take first format from list of supported formats */
            enc_ctx.sample_fmt(encoder.sample_fmts().get(0));

            enc_ctx.time_base().num(1);
            enc_ctx.time_base().den(enc_ctx.sample_rate());
        }

        int ret = avcodec_open2(enc_ctx, encoder, dict);
        av_dict_free(dict);

        if (ret < 0) {
            logger.error("Cannot open video encoder for stream #{}. Error {}.", in_stream.id(), ret);
            return null;
        }

        if ((ctx.avfCtxOutput.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
            enc_ctx.flags(enc_ctx.flags() | CODEC_FLAG_GLOBAL_HEADER);
        }

        out_stream.id(ctx.avfCtxOutput.nb_streams() - 1);

        return out_stream;
    }

    /**
     * Dump the AVFormatContext info like AVFormat.av_dump_format() does.
     */
    public static void dumpFormat(StringBuilder buf, AVFormatContext ic, int index, String url, boolean isOutput, int desiredProgram) {
        int i;
        int numStreams = ic.nb_streams();

        if (numStreams == 0) {
            buf.append("There are no streams in the input context");
            return;
        }

        boolean[] printed = new boolean[numStreams];

        buf.append(System.lineSeparator());
        buf.append(String.format("%s #%d, %s, %s '%s':",
                isOutput ? "Output" : "Input",
                index,
                isOutput ? ic.oformat().name().getString() : ic.iformat().name().getString(),
                isOutput ? "to" : "from",
                url))
                .append(System.lineSeparator());

        dumpMetadata(buf, ic.metadata(), "  ");

        if (!isOutput) {
            buf.append("  Duration: ");

            if (ic.duration() != AV_NOPTS_VALUE) {
                long duration = ic.duration() + 5000;
                int secs = (int) (duration / AV_TIME_BASE);
                int us = (int) (duration % AV_TIME_BASE);
                int mins = secs / 60;
                int hours = mins / 60;
                secs %= 60;
                mins %= 60;
                buf.append(String.format("%02d:%02d:%02d.%02d", hours, mins, secs,
                        (100 * us) / AV_TIME_BASE));
            } else {
                buf.append("N/A");
            }

            if (ic.start_time() != AV_NOPTS_VALUE) {
                buf.append(", start: ");
                int secs = (int) (ic.start_time() / AV_TIME_BASE);
                int us = (int) Math.abs(ic.start_time() % AV_TIME_BASE);
                buf.append(String.format("%d.%06d", secs, (int) av_rescale(us, 1000000, AV_TIME_BASE)));
            }
            buf.append(", bitrate: ");
            if (ic.bit_rate() != 0) {
                buf.append((ic.bit_rate() / 1000) + " kb/s");
            } else {
                buf.append("N/A");
            }

            buf.append(System.lineSeparator());
        }

        for (i = 0; i < ic.nb_chapters(); i++) {
            AVChapter ch = ic.chapters(i);

            buf.append("    Chapter #").append(index).append(':').append(i).append(": ")
                    .append("start ").append(ch.start() * av_q2d(ch.time_base())).append(", ")
                    .append("end ").append(ch.end() * av_q2d(ch.time_base()))
                    .append(System.lineSeparator());

            dumpMetadata(buf, ch.metadata(), "    ");
        }

        boolean logDesiredProgramOnly = false;

        if (ic.nb_programs() > 0) {
            int total = 0;

            int startProgramIndex = 0;
            int endProgramIndex = ic.nb_programs();

            // If requested via configuration, log only the desired program
            if (desiredProgram > 0 && !LOG_STREAM_DETAILS_FOR_ALL_PROGRAMS) {
                for (int programIndex = startProgramIndex; programIndex < endProgramIndex; programIndex++) {
                    if ( ic.programs(programIndex).id() == desiredProgram) {
                        startProgramIndex = programIndex;
                        endProgramIndex = programIndex + 1;
                        logDesiredProgramOnly = true;
                        break;
                    }
                }
            }

            for (int j = startProgramIndex; j < endProgramIndex; j++) {
                AVDictionaryEntry name = av_dict_get(ic.programs(j).metadata(), "name", null, 0);

                buf.append("  Program ").append(ic.programs(j).id());

                if (name != null) {
                    buf.append(' ').append(name.value().getString());
                }
                buf.append(System.lineSeparator());

                dumpMetadata(buf, ic.programs(j).metadata(), "    ");

                AVProgram program = ic.programs(j);
                IntPointer streamIndexes = program.stream_index();
                int numStreamIndexes = program.nb_stream_indexes();

                for (int k = 0; k < numStreamIndexes; k++) {
                    int streamIndex = streamIndexes.get(k);
                    dumpStreamFormat(buf, ic, streamIndex, index, isOutput);
                    printed[streamIndex] = true;
                }
                total += ic.programs(j).nb_stream_indexes();
            }

            if (!logDesiredProgramOnly && total < ic.nb_streams()) {
                buf.append("  No Program").append(System.lineSeparator());
            }
        }

        if (!logDesiredProgramOnly) {
            for (i = 0; i < ic.nb_streams(); i++) {
                if (!printed[i]) {
                    dumpStreamFormat(buf, ic, i, index, isOutput);
                }
            }
        }
    }

    public static void dumpStreamFormat(StringBuilder buf, AVFormatContext ic, int i, int index, boolean is_output) {
        byte[] bytes = new byte[256];
        int flags = is_output ? ic.oformat().flags() : ic.iformat().flags();
        AVStream st = ic.streams(i);
        AVDictionaryEntry lang = av_dict_get(st.metadata(), "language", null, 0);

        avcodec_string(bytes, bytes.length, st.codec(), is_output ? 1 : 0);
        buf.append("    Stream #").append(index).append(':').append(i);

        if ((flags & AVFMT_SHOW_IDS) != 0) {
            buf.append(String.format("[0x%x]", st.id()));
        }

        if (lang != null) {
            buf.append('(').append(lang.value().getString()).append(')');
        }

        buf.append(String.format(", %d, %d/%d: %s", st.codec_info_nb_frames(), st.time_base().num(), st.time_base().den(), new String(bytes).trim()));

        if (st.sample_aspect_ratio().num() != 0 && // default
                av_cmp_q(st.sample_aspect_ratio(), st.codec().sample_aspect_ratio()) != 0) {
            IntPointer numPtr = new IntPointer();
            IntPointer denPtr = new IntPointer();

            av_reduce(numPtr, denPtr,
                    st.codec().width() * st.sample_aspect_ratio().num(),
                    st.codec().height() * st.sample_aspect_ratio().den(),
                    1024 * 1024);

            buf.append(String.format(", SAR %d:%d DAR %d:%d",
                    st.sample_aspect_ratio().num(),
                    st.sample_aspect_ratio().den(),
                    numPtr.get(),
                    denPtr.get()));
        }

        if (st.codec().codec_type() == AVMEDIA_TYPE_VIDEO) {
            boolean fps = st.avg_frame_rate().den() != 0 && st.avg_frame_rate().num() != 0;
            boolean tbr = st.r_frame_rate().den() != 0 && st.r_frame_rate().num() != 0;
            boolean tbn = st.time_base().den() != 0 && st.time_base().num() != 0;
            boolean tbc = st.codec().time_base().den() != 0 && st.codec().time_base().num() != 0;

            if (fps || tbr || tbn || tbc) {
                buf.append(", ");
            }

            if (fps)
                print_fps(buf, av_q2d(st.avg_frame_rate()), tbr || tbn || tbc ? "fps, " : "fps");
            if (tbr) print_fps(buf, av_q2d(st.r_frame_rate()), tbn || tbc ? "tbr, " : "tbr");
            if (tbn) print_fps(buf, 1 / av_q2d(st.time_base()), tbc ? "tbn, " : "tbn");
            if (tbc) print_fps(buf, 1 / av_q2d(st.codec().time_base()), "tbc");
        }

        int disposition = st.disposition();

        if ((disposition & avformat.AV_DISPOSITION_DEFAULT) != 0) buf.append(" (default)");
        if ((disposition & avformat.AV_DISPOSITION_DUB) != 0) buf.append(" (dub)");
        if ((disposition & avformat.AV_DISPOSITION_ORIGINAL) != 0) buf.append(" (original)");
        if ((disposition & avformat.AV_DISPOSITION_COMMENT) != 0) buf.append(" (comment)");
        if ((disposition & avformat.AV_DISPOSITION_LYRICS) != 0) buf.append(" (lyrics)");
        if ((disposition & avformat.AV_DISPOSITION_KARAOKE) != 0) buf.append(" (karaoke)");
        if ((disposition & avformat.AV_DISPOSITION_FORCED) != 0) buf.append(" (forced)");
        if ((disposition & avformat.AV_DISPOSITION_HEARING_IMPAIRED) != 0)
            buf.append(" (hearing impaired)");
        if ((disposition & avformat.AV_DISPOSITION_VISUAL_IMPAIRED) != 0)
            buf.append(" (visual impaired)");
        if ((disposition & avformat.AV_DISPOSITION_CLEAN_EFFECTS) != 0)
            buf.append(" (clean effects)");

        buf.append(System.lineSeparator());
        dumpMetadata(buf, st.metadata(), "    ");
//        dumpSideData( buf, st, "    " );
    }

    public static void dumpMetadata(StringBuilder buf, AVDictionary dict, String indent) {
        if (dict != null && !(av_dict_count(dict) == 1 && av_dict_get(dict, "language", null, 0) != null)) {
            AVDictionaryEntry tag = null;

            buf.append(indent).append("Metadata:").append(System.lineSeparator());

            while ((tag = av_dict_get(dict, "", tag, AV_DICT_IGNORE_SUFFIX)) != null) {
                if (!"language".equals(tag.key())) {
                    BytePointer p = tag.value();

                    buf.append(String.format("%s  %-16s: ", indent, tag.key()));

                    for (; ; ) {
                        byte b;
                        while ((b = p.get()) != 0 && b != 8 && b != 10 && b != 11 && b != 12 && b != 13) {
                            buf.append((char) b);
                            p.position(p.position() + 1);
                        }

                        if (b == '\r') {
                            buf.append(' ');
                        } else if (b == '\n') {
                            buf.append(System.lineSeparator());
                            buf.append(String.format("%s  %-16s: ", indent, ""));
                        } else if (b == 0) {
                            break;
                        }

                        p.position(p.position() + 1);
                    }

                    buf.append(System.lineSeparator());
                }
            }
        }
    }

    public static void print_fps(StringBuilder buf, double d, String postfix) {
        long v = Math.round(d * 100.0);

        if (v == 0) buf.append(String.format("%1.4f %s", d, postfix));
        else if ((v % 100) != 0) buf.append(String.format("%3.2f %s", d, postfix));
        else if ((v % (100 * 1000)) != 0) buf.append(String.format("%1.0f %s", d, postfix));
        else buf.append(String.format("%1.0fk %s", d / 1000, postfix));
    }
}
