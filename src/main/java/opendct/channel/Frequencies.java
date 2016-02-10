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

public class Frequencies {
    public final static Frequency CHANNELS_8VSB[];

    /**
     * Returns the first channel that corresponds with the requested frequency.
     *
     * @param standard Frequency standard.
     * @param frequency Frequency in Hz.
     * @return The channel that corresponds with the requested frequency. You can get -1 if no
     *         channel was found or no table for the requested standard exists.
     */
    public static int getChannelForFrequency(FrequencyType standard, int frequency) {
        Frequency searchTable[] = null;

        switch (standard) {
            case _8VSB:
                searchTable = CHANNELS_8VSB;
                break;
        }

        if (searchTable == null) {
            return -1;
        }

        for (Frequency searchFrequency : searchTable) {
            if (frequency > searchFrequency.LOW_FREQUENCY && frequency < searchFrequency.HIGH_FREQUENCY) {
                return searchFrequency.CHANNEL;
            }
        }

        return -1;
    }

    static {
        // Source: https://en.wikipedia.org/wiki/North_American_television_frequencies#Broadcast_television
        // Converted into code on spreadsheet.

        CHANNELS_8VSB = new Frequency[52];

        // We are mapping things so the channel directly correlates with the index entry.
        CHANNELS_8VSB[0] = null;
        CHANNELS_8VSB[1] = null;

        // VHF low-band
        CHANNELS_8VSB[2] = new Frequency(FrequencyType._8VSB, 2, 54000000, 60000000);
        CHANNELS_8VSB[3] = new Frequency(FrequencyType._8VSB, 3, 60000000, 66000000);
        CHANNELS_8VSB[4] = new Frequency(FrequencyType._8VSB, 4, 66000000, 72000000);
        CHANNELS_8VSB[5] = new Frequency(FrequencyType._8VSB, 5, 76000000, 82000000);
        CHANNELS_8VSB[6] = new Frequency(FrequencyType._8VSB, 6, 82000000, 88000000);

        // VHF high-band
        CHANNELS_8VSB[7] = new Frequency(FrequencyType._8VSB, 7, 174000000, 180000000);
        CHANNELS_8VSB[8] = new Frequency(FrequencyType._8VSB, 8, 180000000, 186000000);
        CHANNELS_8VSB[9] = new Frequency(FrequencyType._8VSB, 9, 186000000, 192000000);
        CHANNELS_8VSB[10] = new Frequency(FrequencyType._8VSB, 10, 192000000, 198000000);
        CHANNELS_8VSB[11] = new Frequency(FrequencyType._8VSB, 11, 198000000, 204000000);
        CHANNELS_8VSB[12] = new Frequency(FrequencyType._8VSB, 12, 204000000, 210000000);
        CHANNELS_8VSB[13] = new Frequency(FrequencyType._8VSB, 13, 210000000, 216000000);

        // UHF band
        CHANNELS_8VSB[14] = new Frequency(FrequencyType._8VSB, 14, 470000000, 476000000);
        CHANNELS_8VSB[15] = new Frequency(FrequencyType._8VSB, 15, 476000000, 482000000);
        CHANNELS_8VSB[16] = new Frequency(FrequencyType._8VSB, 16, 482000000, 488000000);
        CHANNELS_8VSB[17] = new Frequency(FrequencyType._8VSB, 17, 488000000, 494000000);
        CHANNELS_8VSB[18] = new Frequency(FrequencyType._8VSB, 18, 494000000, 500000000);
        CHANNELS_8VSB[19] = new Frequency(FrequencyType._8VSB, 19, 500000000, 506000000);
        CHANNELS_8VSB[20] = new Frequency(FrequencyType._8VSB, 20, 506000000, 512000000);
        CHANNELS_8VSB[21] = new Frequency(FrequencyType._8VSB, 21, 512000000, 518000000);
        CHANNELS_8VSB[22] = new Frequency(FrequencyType._8VSB, 22, 518000000, 524000000);
        CHANNELS_8VSB[23] = new Frequency(FrequencyType._8VSB, 23, 524000000, 530000000);
        CHANNELS_8VSB[24] = new Frequency(FrequencyType._8VSB, 24, 530000000, 536000000);
        CHANNELS_8VSB[25] = new Frequency(FrequencyType._8VSB, 25, 536000000, 542000000);
        CHANNELS_8VSB[26] = new Frequency(FrequencyType._8VSB, 26, 542000000, 548000000);
        CHANNELS_8VSB[27] = new Frequency(FrequencyType._8VSB, 27, 548000000, 554000000);
        CHANNELS_8VSB[28] = new Frequency(FrequencyType._8VSB, 28, 554000000, 560000000);
        CHANNELS_8VSB[29] = new Frequency(FrequencyType._8VSB, 29, 560000000, 566000000);
        CHANNELS_8VSB[30] = new Frequency(FrequencyType._8VSB, 30, 566000000, 572000000);
        CHANNELS_8VSB[31] = new Frequency(FrequencyType._8VSB, 31, 572000000, 578000000);
        CHANNELS_8VSB[32] = new Frequency(FrequencyType._8VSB, 32, 578000000, 584000000);
        CHANNELS_8VSB[33] = new Frequency(FrequencyType._8VSB, 33, 584000000, 590000000);
        CHANNELS_8VSB[34] = new Frequency(FrequencyType._8VSB, 34, 590000000, 596000000);
        CHANNELS_8VSB[35] = new Frequency(FrequencyType._8VSB, 35, 596000000, 602000000);
        CHANNELS_8VSB[36] = new Frequency(FrequencyType._8VSB, 36, 602000000, 608000000);
        CHANNELS_8VSB[37] = new Frequency(FrequencyType._8VSB, 37, 608000000, 614000000);
        CHANNELS_8VSB[38] = new Frequency(FrequencyType._8VSB, 38, 614000000, 620000000);
        CHANNELS_8VSB[39] = new Frequency(FrequencyType._8VSB, 39, 620000000, 626000000);
        CHANNELS_8VSB[40] = new Frequency(FrequencyType._8VSB, 40, 626000000, 632000000);
        CHANNELS_8VSB[41] = new Frequency(FrequencyType._8VSB, 41, 632000000, 638000000);
        CHANNELS_8VSB[42] = new Frequency(FrequencyType._8VSB, 42, 638000000, 644000000);
        CHANNELS_8VSB[43] = new Frequency(FrequencyType._8VSB, 43, 644000000, 650000000);
        CHANNELS_8VSB[44] = new Frequency(FrequencyType._8VSB, 44, 650000000, 656000000);
        CHANNELS_8VSB[45] = new Frequency(FrequencyType._8VSB, 45, 656000000, 662000000);
        CHANNELS_8VSB[46] = new Frequency(FrequencyType._8VSB, 46, 662000000, 668000000);
        CHANNELS_8VSB[47] = new Frequency(FrequencyType._8VSB, 47, 668000000, 674000000);
        CHANNELS_8VSB[48] = new Frequency(FrequencyType._8VSB, 48, 674000000, 680000000);
        CHANNELS_8VSB[49] = new Frequency(FrequencyType._8VSB, 49, 680000000, 686000000);
        CHANNELS_8VSB[50] = new Frequency(FrequencyType._8VSB, 50, 686000000, 692000000);
        CHANNELS_8VSB[51] = new Frequency(FrequencyType._8VSB, 51, 692000000, 698000000);
    }
}
