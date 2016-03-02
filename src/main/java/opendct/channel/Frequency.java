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

package opendct.channel;

public class Frequency {
    public final FrequencyType STANDARD;
    public final int CHANNEL;
    public final int FREQUENCY;
    public final int LOW_FREQUENCY;
    public final int HIGH_FREQUENCY;

    /**
     * Create a new frequency.
     * <p/>
     * FREQUENCY is calculated as the average of the provided low frequency and the high frequency.
     *
     * @param standard The video standard that should be in use on this frequency.
     * @param channel The channel number associated with this frequency.
     * @param lowFrequency The lower edge of this frequency in Hz.
     * @param highFrequency The upper edge of this frequency in Hz.
     */
    public Frequency(FrequencyType standard, int channel, int lowFrequency, int highFrequency) {
        STANDARD = standard;
        CHANNEL = channel;
        LOW_FREQUENCY = lowFrequency;
        HIGH_FREQUENCY = highFrequency;
        FREQUENCY = (LOW_FREQUENCY + HIGH_FREQUENCY) / 2;
    }
}
