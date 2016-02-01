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

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.avformat;
import org.bytedeco.javacpp.avformat.AVChapter;
import org.bytedeco.javacpp.avformat.AVFormatContext;
import org.bytedeco.javacpp.avformat.AVProgram;
import org.bytedeco.javacpp.avformat.AVStream;
import org.bytedeco.javacpp.avutil.*;

import opendct.config.Config;

import static org.bytedeco.javacpp.avcodec.avcodec_string;
import static org.bytedeco.javacpp.avformat.AVFMT_SHOW_IDS;
import static org.bytedeco.javacpp.avutil.*;

public abstract class FFmpegUtil {
    private static final boolean LOG_STREAM_DETAILS_FOR_ALL_PROGRAMS = Config.getBoolean("consumer.ffmpeg.log_stream_details_for_all_programs", false);

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
