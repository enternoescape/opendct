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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.avutil.Callback_Pointer_int_String_Pointer;

import java.util.Arrays;

import static org.bytedeco.javacpp.avutil.av_log_format_line;

public final class FFmpegLogger extends Callback_Pointer_int_String_Pointer {
    public static final boolean linuxLogging = Config.getBoolean("consumer.ffmpeg.linux_logging", false);
    public static final boolean limitLogging = Config.getBoolean("consumer.ffmpeg.limit_logging", true);
    public static final boolean threadRename = Config.getBoolean("consumer.ffmpeg.thread_rename_logging", false);
    public static final boolean enhancedLogging = Config.getBoolean("consumer.ffmpeg.enhanced_logging", true);

    private volatile int repeated = 0;
    private volatile String lastMessage;
    private final static String FFMPEG = "ffmpeg";
    private int[] printPrefix = new int[]{1};
    private byte[] bytes = new byte[1024];

    @Override
    public void call(Pointer source, int level, String formatStr, Pointer params) {
        String className = FFMPEG;
        String message = null;
        String initMessage = null;

        Arrays.fill(bytes, (byte)0);

        // This is not the greatest way to better manage logging, but the C++ code behind this is
        // very fast compared to making multiple calls back and forth to get the customization we
        // are looking for.
        if (enhancedLogging && source != null) {
            if (Config.IS_WINDOWS || Config.IS_LINUX && linuxLogging) {
                av_log_format_line(source, level, formatStr, params, bytes, bytes.length, printPrefix);
                initMessage = trim(bytes);
                message = initMessage;

                if (lastMessage != null && lastMessage.equals(initMessage)) {
                    repeated += 1;
                    return;
                }

                if (message.startsWith("[")) {
                    int end = message.indexOf(" @ ");
                    if (end > 0) {
                        className = "ffmpeg." + message.substring(1, end);
                        if (threadRename && message.length() > end + 3 + 18) {
                            end += 3;
                            Thread.currentThread().setName(message.substring(end, end + 16));
                            message = message.substring(end + 18);
                        } else {
                            message = "[" + message.substring(end + 3);
                        }
                    }
                }
            }
        }

        Logger logger = LogManager.getLogger(className);
        Level configuredLogLevel = logger.getLevel();

        if (configuredLogLevel == null) {
            configuredLogLevel = Level.INFO;
        }

        Level callLogLevel;

        switch (level) {
            case avutil.AV_LOG_QUIET:

                callLogLevel = Level.OFF;
                break;

            case avutil.AV_LOG_PANIC:
            case avutil.AV_LOG_FATAL:

                callLogLevel = Level.FATAL;
                break;

            case avutil.AV_LOG_ERROR:

                callLogLevel = Level.ERROR;
                break;

            case avutil.AV_LOG_WARNING:

                callLogLevel = Level.WARN;
                break;

            case avutil.AV_LOG_INFO:

                callLogLevel = Level.INFO;
                break;

            case avutil.AV_LOG_VERBOSE:
            case avutil.AV_LOG_DEBUG:

                callLogLevel = Level.DEBUG;
                break;

            case avutil.AV_LOG_TRACE:

                callLogLevel = Level.TRACE;
                break;

            default:

                callLogLevel = Level.INFO;
                break;
        }

        if (configuredLogLevel.intLevel() < callLogLevel.intLevel()) {
            return;
        }


        if (message == null && (Config.IS_WINDOWS || Config.IS_LINUX && linuxLogging)) {

            av_log_format_line(source, level, formatStr, params, bytes, bytes.length, printPrefix);
            initMessage = trim(bytes);

            if (lastMessage != null && lastMessage.equals(initMessage)) {
                repeated += 1;
                return;
            }

            message = initMessage;
        }

        switch (className) {
            case "libx264":

        }

        // Clean up logging. Everything ignored here is expected and does not need to be logged.
        if (limitLogging && message != null && (
                message.endsWith("Invalid frame dimensions 0x0.") ||
                // We handle this message by increasing probe sizes based on the currently available
                // data.
                message.endsWith("Consider increasing the value for the 'analyzeduration' and 'probesize' options" ) ||
                // This is the PAT packet and sometimes it doesn't get incremented which makes
                // FFmpeg unhappy.
                message.endsWith("Continuity check failed for pid 0 expected 1 got 0") ||
                // This happens when the buffer gets really behind. The video output never appears
                // to be affected by this notification, but it generates a lot of log entries.
                message.endsWith(" > 10000000: forcing output") ||
                message.endsWith("is not set in estimate_timings_from_pts") ||
                // Seen in H.264 stream init. It means a key frame has not be processed yet. A
                // key frame can take over 5 seconds in some situations to arrive.
                message.endsWith("non-existing PPS 0 referenced") ||
                // Seen in H.264 stream init. This is a resulting error from the non-existing
                // reference above. These errors stop as soon as a key frame is received.
                message.endsWith("decode_slice_header error") ||
                // Seen when writing MPEG-PS. This basically means the file being created will
                // potentially not play on a hardware DVD player which is not really a concern.
                message.contains(" buffer underflow st="))) {


            return;
        }

        if (repeated > 0) {
            logger.info("Last message repeated {} time{}.", repeated, repeated > 1 ? "s" : "");
            repeated = 0;
        }

        lastMessage = initMessage;
        logger.log(callLogLevel, message);
    }

    String trim(byte[] bytes) {
        String message = new String(bytes);
        int len = message.length();

        while (len > 0 && message.charAt(len - 1) <= ' ') {
            len--;
        }

        return len < message.length() ? message.substring(0, len) : message;
    }
//
//    public String getString( BytePointer bytePtr, int maxLen )
//    {
//        byte[] buffer = new byte[ 16 ];
//        int i = 0, j = bytePtr.position();
//        while ( ( buffer[ i ] = bytePtr.position( j ).get() ) != 0 && i < maxLen )
//        {
//            i++;
//            j++;
//            if ( i >= buffer.length )
//            {
//                byte[] newbuffer = new byte[ 2 * buffer.length ];
//                System.arraycopy( buffer, 0, newbuffer, 0, buffer.length );
//                buffer = newbuffer;
//            }
//        }
//        byte[] newbuffer = new byte[ i ];
//        System.arraycopy( buffer, 0, newbuffer, 0, i );
//        return new String( newbuffer );
//    }
}