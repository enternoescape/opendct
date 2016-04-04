/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package opendct.video.ccextractor;

import opendct.config.Config;

import java.io.File;

public class CCExtractorCommon {
    public static final byte[] MAGIC_NUMBER = new byte[] {(byte)0xcc, (byte)0xcc, (byte)0xed };
    public static final byte CCEXTRACTOR_ID = (byte)0xcc;
    public static final byte[] FILE_FORMAT = new byte[] {(byte)0x00, (byte)0x01 };
    public static final byte[] RESERVED = new byte[] {(byte)0x00, (byte)0x00, (byte)0x00 };
    public static final String SRT_RANGE = " --> ";

    public static final Runtime RUNTIME = Runtime.getRuntime();
    public static final String CC_BINARY;
    public static final String STD_BIN_PARAMETERS;
    public static final String STD_SRT_PARAMETERS;
    public static final String SUGGESTED_PARAMETERS;

    static {
        String newExec;

        if (Config.IS_WINDOWS) {
            newExec = Config.BIN_DIR + "ccextractor\\ccextractorwin.exe";

        } else if (Config.IS_LINUX) {
            newExec = Config.BIN_DIR + "ccextractor/ccextractor";

        } else {
            newExec = "";
        }

        if (!(new File(newExec).exists())) {
            // The program is probably running in an IDE.
            if (Config.IS_WINDOWS) {
                if (Config.IS_64BIT) {
                    newExec = Config.BIN_DIR + "windows-x86_64\\ccextractor\\ccextractorwin.exe";
                } else {
                    newExec = Config.BIN_DIR + "windows-x86\\ccextractor\\ccextractorwin.exe";
                }

            } else if (Config.IS_LINUX) {
                if (Config.IS_64BIT) {
                    newExec = Config.BIN_DIR + "linux-x86_64/ccextractor/ccextractor";
                } else {
                    newExec = Config.BIN_DIR + "linux-x86/ccextractor/ccextractor";
                }
            } else {
                newExec = "";
            }
        }

        CC_BINARY = newExec;

        STD_BIN_PARAMETERS = " -stdin -stdout -out=bin --gui_mode_reports --no_progress_bar ";
        STD_SRT_PARAMETERS = " -stdin -out=srt --gui_mode_reports --no_progress_bar ";
        SUGGESTED_PARAMETERS = "-nobi -latin1";
    }
}
