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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bytedeco.javacpp.avutil.av_log_format_line;

public final class FFmpegLogger extends Callback_Pointer_int_String_Pointer {
    private final static String FFMPEG = "ffmpeg";
    private static final Logger defaultLogger = LogManager.getLogger(FFMPEG);
    private static final int defaultLevel = defaultLogger.getLevel().intLevel();

    public static final boolean linuxLogging = Config.getBoolean("consumer.ffmpeg.linux_logging", true);
    public static final boolean limitLogging = Config.getBoolean("consumer.ffmpeg.limit_logging", true);
    public static final boolean threadRename = Config.getBoolean("consumer.ffmpeg.thread_rename_logging", false);
    public static final boolean enhancedLogging = Config.getBoolean("consumer.ffmpeg.enhanced_logging", true);

    private AtomicInteger repeated = new AtomicInteger(0);
    private volatile String lastMessage;
    private final int addressSize = System.getProperty("sun.arch.data.model").equals("64") ? 16 : 8;
    private BlockingQueue<FFmpegLoggerObject> buffers = new LinkedBlockingQueue<>();
    private final String classNames[] = new String[50];

    @Override
    public void call(Pointer source, int level, String formatStr, Pointer params) {
        if (Config.IS_LINUX && !linuxLogging) {
            return;
        }

        Level callLogLevel = convertLogLevel(level);

        // This should help keep the number of fully resolved log entries to a minimum.
        if (defaultLevel < callLogLevel.intLevel()) {
            return;
        }

        FFmpegLoggerObject loggerObject;

        try {
            loggerObject = buffers.poll(1, TimeUnit.MILLISECONDS);

            if (loggerObject == null) {
                loggerObject = new FFmpegLoggerObject();
            } else {
                loggerObject.reset();
            }
        } catch (InterruptedException e) {
            loggerObject = new FFmpegLoggerObject();
        }

        // This is needed because without it, lastMessage could change at the last minute and it
        // could result in a null pointer exception.
        String holdMessage = lastMessage;
        String message;

        av_log_format_line(source, level, formatStr, params, loggerObject.messageBytes, loggerObject.messageBytes.length, loggerObject.printPrefix);

        try {
            trim(loggerObject);
        } catch (Exception e) {
            defaultLogger.error("There was a problem processing a log entry => ", e);
        }

        Logger logger;

        if (loggerObject.className != null) {
            logger = LogManager.getLogger(loggerObject.className);

            if (logger.getLevel().intLevel() < callLogLevel.intLevel()) {
                buffers.offer(loggerObject);
                return;
            }
        } else {
            logger = defaultLogger;
        }

        message = loggerObject.stringBuilder.toString();

        // Clean up logging. Everything ignored here is expected and does not need to be logged.
        if (limitLogging && message != null && (
                message.endsWith("Invalid frame dimensions 0x0.") ||
                // This message is handled by increasing probe sizes based on the currently
                // available data.
                message.endsWith("Consider increasing the value for the 'analyzeduration' and 'probesize' options" ) ||
                // This message is handled by increasing probe sizes based on the currently
                // available data.
                message.endsWith("not enough frames to estimate rate; consider increasing probesize" ) ||
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

            buffers.offer(loggerObject);
            return;
        }

        if (holdMessage != null && holdMessage.equals(message)) {
            repeated.addAndGet(1);
            buffers.offer(loggerObject);
            return;
        }

        int repeatCount = repeated.get();
        if (repeatCount > 0) {
            logger.log(callLogLevel, "Repeated {} time{}: {}.", holdMessage, repeatCount, repeatCount > 1 ? "s" : "");
            repeated.addAndGet(-repeatCount);
        }

        lastMessage = message;
        logger.log(callLogLevel, message);

        buffers.offer(loggerObject);
    }

    private void trim(FFmpegLoggerObject loggerObject) {

        while (loggerObject.len > 0 && (char)loggerObject.messageBytes[loggerObject.len - 1] <= ' ') {
            loggerObject.len--;
        }

        if (enhancedLogging && (char)loggerObject.messageBytes[0] == '[') {
            loggerObject.classStart = 1;
            loggerObject.index = 2;

            for (; loggerObject.index < loggerObject.len; loggerObject.index++) {
                if ((char) loggerObject.messageBytes[loggerObject.index - 1] == ' ' &&
                        (char) loggerObject.messageBytes[loggerObject.index] == '@') {

                    loggerObject.classEnd = loggerObject.index - 2;
                    loggerObject.classNameLen = loggerObject.classEnd - loggerObject.classStart;
                    loggerObject.noClassName = false;
                    loggerObject.className = getClass(loggerObject);
                    break;
                }
            }

            if (!loggerObject.noClassName) {
                if (threadRename && loggerObject.index + addressSize + 4 < loggerObject.len &&
                        (char) loggerObject.messageBytes[loggerObject.index] == '@' &&
                        (char) loggerObject.messageBytes[loggerObject.index + 1] == ' ') {

                    loggerObject.threadStart = loggerObject.index + 2;

                    if ((char)loggerObject.messageBytes[loggerObject.index + 2 + addressSize] == ']') {
                        loggerObject.threadEnd = loggerObject.index + 2 + addressSize;
                        loggerObject.threadNameLen = loggerObject.threadEnd - loggerObject.threadStart;
                        loggerObject.noThreadName = false;

                        String currentThreadName = Thread.currentThread().getName();
                        loggerObject.threadName = new String(loggerObject.messageBytes, loggerObject.threadStart, loggerObject.threadNameLen, StandardCharsets.UTF_8);

                        if (currentThreadName.startsWith("Thread-")) {
                            Thread.currentThread().setName(loggerObject.threadName);
                            loggerObject.index = loggerObject.index + addressSize + 4;
                        } else if (currentThreadName.equals(loggerObject.threadName)) {
                            loggerObject.index = loggerObject.index + addressSize + 4;
                        } else {
                            loggerObject.index += 1;
                            loggerObject.messageBytes[loggerObject.index] = (byte)'[';
                        }
                    }
                } else {
                    loggerObject.index += 1;
                    loggerObject.messageBytes[loggerObject.index] = (byte)'[';
                }
            } else {
                // Return the index back to 0 or nothing will be displayed in the log.
                loggerObject.index = 0;
            }
        }

        for(; loggerObject.index < loggerObject.len; loggerObject.index++) {
            loggerObject.stringBuilder.append((char)loggerObject.messageBytes[loggerObject.index]);
        }
    }

    private synchronized void addClass(String className) {
        for (int i = 0; i < classNames.length; i++) {
            if (classNames[i] ==  null) {
                classNames[i] = className;
                return;
            } else if (classNames[i].equals(className)) {
                return;
            }
        }
    }

    private String getClass(FFmpegLoggerObject loggerObject) {
        String className;
        boolean match;

        for (int i = 0; i < classNames.length; i++) {
            className = classNames[i];

            if (className == null) {
                className = "ffmpeg." + new String(loggerObject.messageBytes, loggerObject.classStart, loggerObject.classEnd, StandardCharsets.UTF_8);
                addClass(className);
                return className;
            } else if (className.length() != loggerObject.classNameLen + 8) {
                continue;
            }

            match = true;
            for (int j = 0; j < loggerObject.classNameLen; j++) {
                if (loggerObject.messageBytes[j + loggerObject.classStart] != className.charAt(j + 7)) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return className;
            }
        }

        return "ffmpeg." + new String(loggerObject.messageBytes, loggerObject.classStart, loggerObject.classEnd, StandardCharsets.UTF_8);
    }

    private Level convertLogLevel(int level) {
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

        return callLogLevel;
    }

    private class FFmpegLoggerObject {
        private byte messageBytes[] = new byte[1024];
        private StringBuilder stringBuilder = new StringBuilder(1024);
        private boolean noClassName = true;
        private String className = null;
        private int classNameLen = 0;
        private boolean noThreadName = true;
        private String threadName = null;
        private int threadNameLen = 0;
        private int[] printPrefix = new int[]{1};
        int len = messageBytes.length;
        int index = 0;
        int classStart = 0;
        int classEnd = 0;
        int threadStart = 0;
        int threadEnd = 0;

        private void reset() {
            Arrays.fill(messageBytes, (byte) 0);
            stringBuilder.setLength(0);
            noClassName = true;
            className = null;
            classNameLen = 0;
            noThreadName = true;
            threadName = null;
            threadNameLen = 0;
            printPrefix[0] = 1;
            len = messageBytes.length;
            index = 0;
            classStart = 0;
            classEnd = 0;
            threadStart = 0;
            threadEnd = 0;
        }
    }
}