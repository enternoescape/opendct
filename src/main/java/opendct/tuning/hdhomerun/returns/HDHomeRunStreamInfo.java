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

package opendct.tuning.hdhomerun.returns;

import java.util.Arrays;

public class HDHomeRunStreamInfo {
    private final String programs[];
    public final String TSID;

    public HDHomeRunStreamInfo(String programs[], String tsid) {
        this.programs = programs;
        TSID = tsid;
    }

    public HDHomeRunStreamInfo(String streaminfo) {
        String programs[] = new String[0];
        String tsid = "";

        if (streaminfo.contains("\n")) {
            String split[] = streaminfo.split("\n");
            programs = new String[split.length];
            int i = 0;

            for (String line : split) {
                if (line.contains(":")) {
                    programs[i++] = line;
                } else if (line.contains("tsid=")) {
                    tsid = line.substring(5);
                }
            }

            if (i != programs.length)
            {
                programs = Arrays.copyOf(programs, i);
            }
        }

        this.programs = programs;
        TSID = tsid;
    }

    /**
     * Returns a program parsed into PROGRAM, CHANNEL, CALLSIGN.
     *
     * @param program Raw program data from stream info.
     * @return An object with <i>null</i> values for things that could not be determined.
     */
    public static HDHomeRunProgram parseProgram(String program) {
        return new HDHomeRunProgram(program);
    }

    public HDHomeRunProgram[] getProgramsParsed() {
        HDHomeRunProgram returnValues[] = new HDHomeRunProgram[programs.length];

        for (int i = 0; i < returnValues.length; i++) {
            returnValues[i] = parseProgram(programs[i]);
        }

        return returnValues;
    }

    public String[] getProgramsRaw() {
        return programs;
    }

    @Override
    public String toString() {
        return "HDHomeRunStreamInfo{" +
                "programs=" + Arrays.toString(programs) +
                ", TSID='" + TSID + '\'' +
                '}';
    }
}
