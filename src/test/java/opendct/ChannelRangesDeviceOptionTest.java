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

package opendct;

import opendct.config.options.ChannelRangesDeviceOption;
import org.testng.annotations.Test;

public class ChannelRangesDeviceOptionTest {

    @Test(groups = { "util", "channelRanges" })
    public void parseTestNullOrEmpty() {
        String returned[] = ChannelRangesDeviceOption.parseRanges("");
        String expected[] = new String[] { };

        verifyValues(returned, expected);
    }

    private void verifyValues(String returned[], String expected[]) {
        assert returned.length == expected.length : "returned.length = " + returned.length + " != expected.length = " + expected.length;

        for (int i = 0; i < returned.length; i++) {
            assert returned[i].equals(expected[i]) : "index " + i + " returned = " + returned[i] + " != expected = " + expected[i];
        }
    }
}
