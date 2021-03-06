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
    public final int PROGRAM;
    public final String CHANNEL;
    public final String CALLSIGN;
    public final boolean ENCRYPTED;
    public final boolean CONTROL;
    public final boolean NO_DATA;

    public HDHomeRunProgram(int program, String channel, String callsign, boolean encrypted, boolean control, boolean noData) {
        PROGRAM = program;
        CHANNEL = channel;
        CALLSIGN = callsign;
        ENCRYPTED = encrypted;
        CONTROL = control;
        NO_DATA = noData;
    }

    public HDHomeRunProgram(String hdhrProgram) {
        int program = 0;
        String channel = null;
        String callsign = null;

        if (!Util.isNullOrEmpty(hdhrProgram)) {
            int colonIndex = hdhrProgram.indexOf(":");

            //Format Example:
            //3: 8.1 WGAL-TV
            //11: 16 ProSieben
            //5040: 0 Horizon data (control)
            //7005: 501 TC Star (encrypted)
            //7098: 0 TC_SW_Kaon_HD (no data)
            if (colonIndex > 0) {
                try {
                    program = Integer.parseInt(hdhrProgram.substring(0, colonIndex).trim());
                } catch (NumberFormatException e) {
                    program = 0;
                }

                int spaceIndex;

                if (program == 0) {
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

        PROGRAM = program;
        CHANNEL = !Util.isNullOrEmpty(channel) ? channel : null;
        CALLSIGN = !Util.isNullOrEmpty(callsign) ? callsign : null;
        ENCRYPTED = hdhrProgram.trim().endsWith(" (encrypted)");
        CONTROL = hdhrProgram.trim().endsWith(" (control)");
        NO_DATA = hdhrProgram.trim().endsWith(" (no data)");
    }

    public boolean isTunable() {
        return !ENCRYPTED && !CONTROL && !NO_DATA && PROGRAM != 0;
    }

    @Override
    public String toString() {
        return "HDHomeRunProgram{" +
                "PROGRAM='" + PROGRAM + '\'' +
                ", CHANNEL='" + CHANNEL + '\'' +
                ", CALLSIGN='" + CALLSIGN + '\'' +
                ", ENCRYPTED=" + ENCRYPTED +
                ", CONTROL=" + CONTROL +
                ", NO_DATA=" + NO_DATA +
                '}';
    }
}
