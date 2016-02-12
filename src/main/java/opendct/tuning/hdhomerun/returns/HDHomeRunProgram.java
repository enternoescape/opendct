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

package opendct.tuning.hdhomerun.returns;

import opendct.util.Util;

public class HDHomeRunProgram {
    public final String PROGRAM;
    public final String CHANNEL;
    public final String CALLSIGN;

    public HDHomeRunProgram(String program, String channel, String callsign) {
        PROGRAM = program;
        CHANNEL = channel;
        CALLSIGN = callsign;
    }

    public HDHomeRunProgram(String hdhrProgram) {
        String program = null;
        String channel = null;
        String callsign = null;

        if (!Util.isNullOrEmpty(hdhrProgram)) {
            int colonIndex = hdhrProgram.indexOf(":");

            //Format Example:
            //3: 8.1 WGAL-TV
            if (colonIndex > 0) {
                program = hdhrProgram.substring(0, colonIndex).trim();

                int spaceIndex;

                if (Util.isNullOrEmpty(program)) {
                    spaceIndex = -1;
                } else {
                    spaceIndex = hdhrProgram.indexOf(" ", colonIndex);
                }


                if (spaceIndex == colonIndex + 1 && spaceIndex + 1 < hdhrProgram.length()) {
                    channel = hdhrProgram.substring(spaceIndex + 1).trim();

                    spaceIndex = channel.indexOf(" ");

                    if (spaceIndex > 0 && spaceIndex + 1 < hdhrProgram.length()) {
                        callsign = channel.substring(spaceIndex + 1).trim();
                        channel = channel.substring(0, spaceIndex).trim();
                    }
                }
            }
        }

        PROGRAM = !Util.isNullOrEmpty(program) ? program : null;
        CHANNEL = !Util.isNullOrEmpty(channel) ? channel : null;
        CALLSIGN = !Util.isNullOrEmpty(callsign) ? callsign : null;
    }

    @Override
    public String toString() {
        return "HDHomeRunProgram{" +
                "PROGRAM='" + PROGRAM + '\'' +
                ", CHANNEL='" + CHANNEL + '\'' +
                ", CALLSIGN='" + CALLSIGN + '\'' +
                '}';
    }
}
