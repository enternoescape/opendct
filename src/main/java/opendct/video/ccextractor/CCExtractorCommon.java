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

public class CCExtractorCommon {
    public static final Runtime RUNTIME = Runtime.getRuntime();
    public static final String CC_BINARY;
    public static final String STD_SRT_PARAMETERS;

    static {
        String newExec;

        if (Config.IS_WINDOWS) {
            newExec = Config.BIN_DIR + "ccextractor\\ccextractor.exe";

        } else if (Config.IS_LINUX) {
            newExec = Config.BIN_DIR + "ccextractor/ccextractor";

        } else {
            newExec = "";
        }

        // Linux does not like this path being put in quotes, but Windows needs it.
        if (Config.IS_WINDOWS && !newExec.equals("")) {
            newExec = "\"" + newExec + "\"";
        } else {
            // This will likely never be needed, but this is only done once.
            newExec = newExec.replace(" ", "\\ ");
        }

        CC_BINARY = newExec;

        STD_SRT_PARAMETERS = " -out=srt --stream --no_progress_bar";
    }
}
