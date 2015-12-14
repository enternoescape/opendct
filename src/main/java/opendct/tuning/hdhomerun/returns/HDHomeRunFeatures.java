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

public class HDHomeRunFeatures {
    private final String channelmap[];
    private final String modulation[];
    private final String autoModulation[];

    public HDHomeRunFeatures(String channelmap[], String modulation[], String autoModulation[]) {
        this.channelmap = channelmap;
        this.modulation = modulation;
        this.autoModulation = autoModulation;
    }

    public HDHomeRunFeatures(String features) {
        String channelmap[] = new String[0];
        String modulation[] = new String[0];
        String autoModulation[] = new String[0];

        String lines[] = features.split("\n");

        for(String line: lines) {
            int breakIndex = line.indexOf(": ");

            if (breakIndex > 0 && line.length() > breakIndex + 2) {
                String value = line.substring(breakIndex + 2);
                String key = line.substring(0, line.length() - value.length() - 2);
                String values[] = value.split(" ");

                if (key.contains("channelmap")) {
                    channelmap = values;
                } else if (key.contains("modulation")) {
                    modulation = values;
                } else if (key.contains("auto-modulation")) {
                    autoModulation = values;
                }
            }
        }

        this.channelmap = channelmap;
        this.modulation = modulation;
        this.autoModulation = autoModulation;
    }

    public String[] getChannelmap() {
        return channelmap;
    }

    public String[] getModulation() {
        return modulation;
    }

    public String[] getAutoModulation() {
        return autoModulation;
    }
}
