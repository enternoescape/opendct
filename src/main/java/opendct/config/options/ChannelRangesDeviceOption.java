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

package opendct.config.options;

import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class ChannelRangesDeviceOption extends BaseDeviceOption {
    private static final Logger logger = LogManager.getLogger(ChannelRangesDeviceOption.class);

    public ChannelRangesDeviceOption(String value, boolean readonly, String name, String property, String description) throws DeviceOptionException {
        super(DeviceOptionType.STRING, readonly, name, property, description);
        super.setValue(value);
    }

    public ChannelRangesDeviceOption(String value, boolean allowEmpty, boolean readonly, String name, String property, String description) throws DeviceOptionException {
        super(DeviceOptionType.STRING, allowEmpty, readonly, name, property, description);
        super.setValue(value);
    }

    @Override
    public void setValue(String... newValues) throws DeviceOptionException {


        super.setValue(newValues);
    }

    /**
     * Check if all provided ranges are valid ranges.
     *
     * @param ranges A string containing channel ranges.
     * @return The values returned are not valid ranges.
     */
    public static String[] validateRanges(String ranges) {
        ArrayList<String> returnValue = new ArrayList<>();
        String split[] = ranges.split("\\s*,\\s*");

        for (String range : split) {
            // This might happen on an empty array.
            if (Util.isNullOrEmpty(range)) {
                continue;
            }

            int dash1 = range.indexOf("-");
            int dash2 = -1;

            if (dash1 > 0) {
                dash2 = range.indexOf("-", dash1 + 1);

                if (dash2 < 0) {
                    // This is a numeric range.
                    String startString = range.substring(0, dash1);
                    String endString = range.substring(dash1 + 1);

                    int start;
                    int end;

                    try {
                        start = Integer.parseInt(startString);
                        end = Integer.parseInt(endString);
                    } catch (NumberFormatException e) {
                        returnValue.add(range);
                    }
                }

                // This is single a channel.
            }

            // This is a single channel.
        }

        return returnValue.toArray(new String[returnValue.size()]);
    }

    /**
     * Returns all valid ranges expanded.
     *
     * @param ranges A string containing channel ranges.
     * @return The values returned are valid ranges.
     */
    public static String[] parseRanges(String ranges) {
        ArrayList<String> returnValue = new ArrayList<>();

        String split[] = ranges.split("\\s*,\\s*");

        for (String range : split) {
            // This might happen on an empty array.
            if (Util.isNullOrEmpty(range)) {
                continue;
            }

            int dash1 = range.indexOf("-");
            int dash2 = -1;

            if (dash1 > 0) {
                dash2 = range.indexOf("-", dash1 + 1);

                if (dash2 < 0) {
                    // This is a numeric range.
                    String startString = range.substring(0, dash1);
                    String endString = range.substring(dash1 + 1);

                    int start;
                    int end;

                    try {
                        start = Integer.parseInt(startString);
                        end = Integer.parseInt(endString);
                    } catch (NumberFormatException e) {
                        logger.warn("Ignoring invalid channel range '{}' => ", range, e);
                        continue;
                    }

                    if (start > end) {
                        int swap = end;
                        end = start;
                        start = swap;
                    } else if (start == end) {
                        logger.warn("The channel range '{}' is not actually a range.", range);
                        returnValue.add(Integer.toString(end));
                        continue;
                    }

                    end += 1;
                    for (int i = start; i < end; i++) {
                        returnValue.add(Integer.toString(i));
                    }
                } else {
                    // This is single a channel.
                    returnValue.add(range);
                }
            } else {
                // This is a single channel.
                returnValue.add(range);
            }
        }

        return returnValue.toArray(new String[returnValue.size()]);
    }
}
