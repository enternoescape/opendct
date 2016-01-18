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

import static org.bytedeco.javacpp.avutil.av_log_format_line;

//import org.bytedeco.javacpp.PointerPointer;
//import org.bytedeco.javacpp.avutil.AVClass;

public final class FFmpegLogger extends Callback_Pointer_int_String_Pointer {
    public static final boolean linuxLogging = Config.getBoolean("consumer.ffmpeg.linux_logging", false);

    int[] printPrefix = new int[]{1};
    StringBuilder lineBuf = new StringBuilder();

    @Override
    public void call(Pointer source, int level, String formatStr, Pointer params) {
        String className = "ffmpeg"; // TODO: get the class by something like this: getString( (new PointerPointer<AVClass>( source )).get( AVClass.class ).class_name(), 100 );
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

        byte[] bytes = new byte[1024];

        if (Config.IS_WINDOWS || Config.IS_LINUX && linuxLogging) {
            av_log_format_line(source, level, formatStr, params, bytes, bytes.length, printPrefix);
        }

        String message = trim(bytes);

//        if ( printPrefix[ 0 ] == 1 && lineBuf.length() > 0 )
//        {
//            String line = lineBuf.toString();
//            lineBuf.setLength( 0 );
//            lineBuf.append( message );
//            message = line;
//        }
//        else
//        {
//            lineBuf.append( message );
//            return;
//        }
//
//        logger.info( "class_name=" + className );
//
//        message = "level=" + level + ", callLogLevel=" + callLogLevel + ", configuredLogLevel=" + configuredLogLevel + "(" + configuredLogLevel.intValue() + "): " + message;

        if (message.endsWith("Invalid frame dimensions 0x0.") ||
                message.endsWith("Consider increasing the value for the 'analyzeduration' and 'probesize' options" ) ||
                message.endsWith("is not set in estimate_timings_from_pts")) {

            return;
        }

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