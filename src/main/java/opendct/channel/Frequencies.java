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

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Frequencies {
    public final static Frequency US_BCAST[];
    public final static Frequency US_CABLE[];

    /**
     * Returns the first channel that corresponds with the requested frequency.
     *
     * @param standard Frequency standard.
     * @param frequency Frequency in Hz.
     * @return The channel that corresponds with the requested frequency. You can get -1 if no
     *         channel was found or no table for the requested standard exists.
     */
    public static int getChannelForFrequency(FrequencyType standard, int frequency) {
        Frequency searchTable[];

        switch (standard) {
            case US_BCAST:
                searchTable = US_BCAST;
                break;
            case US_CABLE:
                searchTable = US_CABLE;
                break;
            default:
                return -1;
        }

        for (Frequency searchFrequency : searchTable) {
            if (searchFrequency != null && frequency > searchFrequency.LOW_FREQUENCY && frequency < searchFrequency.HIGH_FREQUENCY) {
                return searchFrequency.CHANNEL;
            }
        }

        return -1;
    }

    /**
     * Get a HashMap with all of the channels mapped to their respective frequencies.
     * <p/>
     * This is to speed up channel scanning by having a list of all of the expected channels that
     * can be checked for within the same frequency.
     * <p/>
     * If there is no frequency associated with a channel it will be added as the frequency 0, so
     * be sure to account for that if this is being used for a channel scan.
     *
     * @param lineup The lineup to be parsed.
     * @return The HashMap with frequencies mapped to the channels they contain.
     */
    public static Map<Integer, ArrayList<TVChannel>> getFrequenciesToChannelsMap(ChannelLineup lineup) {
        if (lineup == null) {
            return null;
        }

        Map<Integer, ArrayList<TVChannel>> returnValues = new Int2ObjectOpenHashMap<>(200);

        for (TVChannel tvChannel : lineup.getAllChannels(true, true)) {

            Integer lookupFreq = tvChannel.getFrequency();

            if (tvChannel.getFrequency() <= 0) {
                lookupFreq = 0;
            }

            ArrayList<TVChannel> currentFreq = returnValues.get(lookupFreq);

            if (currentFreq == null) {
                currentFreq = new ArrayList<>();
                returnValues.put(lookupFreq, currentFreq);
            }

            currentFreq.add(tvChannel);
        }

        return returnValues;
    }

    static {
        // Source: https://en.wikipedia.org/wiki/North_American_television_frequencies#Broadcast_television
        // Converted into code on spreadsheet.

        US_BCAST = new Frequency[52];

        // Mapping the channels directly with the index number.
        US_BCAST[0] = null;
        US_BCAST[1] = null;

        // VHF low-band
        US_BCAST[2] = new Frequency(FrequencyType.US_BCAST, 2, 54000000, 60000000);
        US_BCAST[3] = new Frequency(FrequencyType.US_BCAST, 3, 60000000, 66000000);
        US_BCAST[4] = new Frequency(FrequencyType.US_BCAST, 4, 66000000, 72000000);
        US_BCAST[5] = new Frequency(FrequencyType.US_BCAST, 5, 76000000, 82000000);
        US_BCAST[6] = new Frequency(FrequencyType.US_BCAST, 6, 82000000, 88000000);

        // VHF high-band
        US_BCAST[7] = new Frequency(FrequencyType.US_BCAST, 7, 174000000, 180000000);
        US_BCAST[8] = new Frequency(FrequencyType.US_BCAST, 8, 180000000, 186000000);
        US_BCAST[9] = new Frequency(FrequencyType.US_BCAST, 9, 186000000, 192000000);
        US_BCAST[10] = new Frequency(FrequencyType.US_BCAST, 10, 192000000, 198000000);
        US_BCAST[11] = new Frequency(FrequencyType.US_BCAST, 11, 198000000, 204000000);
        US_BCAST[12] = new Frequency(FrequencyType.US_BCAST, 12, 204000000, 210000000);
        US_BCAST[13] = new Frequency(FrequencyType.US_BCAST, 13, 210000000, 216000000);

        // UHF band
        US_BCAST[14] = new Frequency(FrequencyType.US_BCAST, 14, 470000000, 476000000);
        US_BCAST[15] = new Frequency(FrequencyType.US_BCAST, 15, 476000000, 482000000);
        US_BCAST[16] = new Frequency(FrequencyType.US_BCAST, 16, 482000000, 488000000);
        US_BCAST[17] = new Frequency(FrequencyType.US_BCAST, 17, 488000000, 494000000);
        US_BCAST[18] = new Frequency(FrequencyType.US_BCAST, 18, 494000000, 500000000);
        US_BCAST[19] = new Frequency(FrequencyType.US_BCAST, 19, 500000000, 506000000);
        US_BCAST[20] = new Frequency(FrequencyType.US_BCAST, 20, 506000000, 512000000);
        US_BCAST[21] = new Frequency(FrequencyType.US_BCAST, 21, 512000000, 518000000);
        US_BCAST[22] = new Frequency(FrequencyType.US_BCAST, 22, 518000000, 524000000);
        US_BCAST[23] = new Frequency(FrequencyType.US_BCAST, 23, 524000000, 530000000);
        US_BCAST[24] = new Frequency(FrequencyType.US_BCAST, 24, 530000000, 536000000);
        US_BCAST[25] = new Frequency(FrequencyType.US_BCAST, 25, 536000000, 542000000);
        US_BCAST[26] = new Frequency(FrequencyType.US_BCAST, 26, 542000000, 548000000);
        US_BCAST[27] = new Frequency(FrequencyType.US_BCAST, 27, 548000000, 554000000);
        US_BCAST[28] = new Frequency(FrequencyType.US_BCAST, 28, 554000000, 560000000);
        US_BCAST[29] = new Frequency(FrequencyType.US_BCAST, 29, 560000000, 566000000);
        US_BCAST[30] = new Frequency(FrequencyType.US_BCAST, 30, 566000000, 572000000);
        US_BCAST[31] = new Frequency(FrequencyType.US_BCAST, 31, 572000000, 578000000);
        US_BCAST[32] = new Frequency(FrequencyType.US_BCAST, 32, 578000000, 584000000);
        US_BCAST[33] = new Frequency(FrequencyType.US_BCAST, 33, 584000000, 590000000);
        US_BCAST[34] = new Frequency(FrequencyType.US_BCAST, 34, 590000000, 596000000);
        US_BCAST[35] = new Frequency(FrequencyType.US_BCAST, 35, 596000000, 602000000);
        US_BCAST[36] = new Frequency(FrequencyType.US_BCAST, 36, 602000000, 608000000);
        US_BCAST[37] = new Frequency(FrequencyType.US_BCAST, 37, 608000000, 614000000);
        US_BCAST[38] = new Frequency(FrequencyType.US_BCAST, 38, 614000000, 620000000);
        US_BCAST[39] = new Frequency(FrequencyType.US_BCAST, 39, 620000000, 626000000);
        US_BCAST[40] = new Frequency(FrequencyType.US_BCAST, 40, 626000000, 632000000);
        US_BCAST[41] = new Frequency(FrequencyType.US_BCAST, 41, 632000000, 638000000);
        US_BCAST[42] = new Frequency(FrequencyType.US_BCAST, 42, 638000000, 644000000);
        US_BCAST[43] = new Frequency(FrequencyType.US_BCAST, 43, 644000000, 650000000);
        US_BCAST[44] = new Frequency(FrequencyType.US_BCAST, 44, 650000000, 656000000);
        US_BCAST[45] = new Frequency(FrequencyType.US_BCAST, 45, 656000000, 662000000);
        US_BCAST[46] = new Frequency(FrequencyType.US_BCAST, 46, 662000000, 668000000);
        US_BCAST[47] = new Frequency(FrequencyType.US_BCAST, 47, 668000000, 674000000);
        US_BCAST[48] = new Frequency(FrequencyType.US_BCAST, 48, 674000000, 680000000);
        US_BCAST[49] = new Frequency(FrequencyType.US_BCAST, 49, 680000000, 686000000);
        US_BCAST[50] = new Frequency(FrequencyType.US_BCAST, 50, 686000000, 692000000);
        US_BCAST[51] = new Frequency(FrequencyType.US_BCAST, 51, 692000000, 698000000);


        US_CABLE = new Frequency[159];

        // Mapping the channels directly with the index number.
        US_CABLE[0] = null;
        US_CABLE[1] = null;

        US_CABLE[2] = new Frequency(FrequencyType.US_CABLE, 2, 54000000, 60000000);
        US_CABLE[3] = new Frequency(FrequencyType.US_CABLE, 3, 60000000, 66000000);
        US_CABLE[4] = new Frequency(FrequencyType.US_CABLE, 4, 66000000, 72000000);
        US_CABLE[5] = new Frequency(FrequencyType.US_CABLE, 5, 76000000, 82000000);
        US_CABLE[6] = new Frequency(FrequencyType.US_CABLE, 6, 82000000, 88000000);
        US_CABLE[7] = new Frequency(FrequencyType.US_CABLE, 7, 174000000, 180000000);
        US_CABLE[8] = new Frequency(FrequencyType.US_CABLE, 8, 180000000, 186000000);
        US_CABLE[9] = new Frequency(FrequencyType.US_CABLE, 9, 186000000, 192000000);
        US_CABLE[10] = new Frequency(FrequencyType.US_CABLE, 10, 192000000, 198000000);
        US_CABLE[11] = new Frequency(FrequencyType.US_CABLE, 11, 198000000, 204000000);
        US_CABLE[12] = new Frequency(FrequencyType.US_CABLE, 12, 204000000, 210000000);
        US_CABLE[13] = new Frequency(FrequencyType.US_CABLE, 13, 210000000, 216000000);
        US_CABLE[14] = new Frequency(FrequencyType.US_CABLE, 14, 120000000, 126000000);
        US_CABLE[15] = new Frequency(FrequencyType.US_CABLE, 15, 126000000, 132000000);
        US_CABLE[16] = new Frequency(FrequencyType.US_CABLE, 16, 132000000, 138000000);
        US_CABLE[17] = new Frequency(FrequencyType.US_CABLE, 17, 138000000, 144000000);
        US_CABLE[18] = new Frequency(FrequencyType.US_CABLE, 18, 144000000, 150000000);
        US_CABLE[19] = new Frequency(FrequencyType.US_CABLE, 19, 150000000, 156000000);
        US_CABLE[20] = new Frequency(FrequencyType.US_CABLE, 20, 156000000, 162000000);
        US_CABLE[21] = new Frequency(FrequencyType.US_CABLE, 21, 162000000, 168000000);
        US_CABLE[22] = new Frequency(FrequencyType.US_CABLE, 22, 168000000, 174000000);
        US_CABLE[23] = new Frequency(FrequencyType.US_CABLE, 23, 216000000, 222000000);
        US_CABLE[24] = new Frequency(FrequencyType.US_CABLE, 24, 222000000, 228000000);
        US_CABLE[25] = new Frequency(FrequencyType.US_CABLE, 25, 228000000, 234000000);
        US_CABLE[26] = new Frequency(FrequencyType.US_CABLE, 26, 234000000, 240000000);
        US_CABLE[27] = new Frequency(FrequencyType.US_CABLE, 27, 240000000, 246000000);
        US_CABLE[28] = new Frequency(FrequencyType.US_CABLE, 28, 246000000, 252000000);
        US_CABLE[29] = new Frequency(FrequencyType.US_CABLE, 29, 252000000, 258000000);
        US_CABLE[30] = new Frequency(FrequencyType.US_CABLE, 30, 258000000, 264000000);
        US_CABLE[31] = new Frequency(FrequencyType.US_CABLE, 31, 264000000, 270000000);
        US_CABLE[32] = new Frequency(FrequencyType.US_CABLE, 32, 270000000, 276000000);
        US_CABLE[33] = new Frequency(FrequencyType.US_CABLE, 33, 276000000, 282000000);
        US_CABLE[34] = new Frequency(FrequencyType.US_CABLE, 34, 282000000, 288000000);
        US_CABLE[35] = new Frequency(FrequencyType.US_CABLE, 35, 288000000, 294000000);
        US_CABLE[36] = new Frequency(FrequencyType.US_CABLE, 36, 294000000, 300000000);
        US_CABLE[37] = new Frequency(FrequencyType.US_CABLE, 37, 300000000, 306000000);
        US_CABLE[38] = new Frequency(FrequencyType.US_CABLE, 38, 306000000, 312000000);
        US_CABLE[39] = new Frequency(FrequencyType.US_CABLE, 39, 312000000, 318000000);
        US_CABLE[40] = new Frequency(FrequencyType.US_CABLE, 40, 318000000, 324000000);
        US_CABLE[41] = new Frequency(FrequencyType.US_CABLE, 41, 324000000, 330000000);
        US_CABLE[42] = new Frequency(FrequencyType.US_CABLE, 42, 330000000, 336000000);
        US_CABLE[43] = new Frequency(FrequencyType.US_CABLE, 43, 336000000, 342000000);
        US_CABLE[44] = new Frequency(FrequencyType.US_CABLE, 44, 342000000, 348000000);
        US_CABLE[45] = new Frequency(FrequencyType.US_CABLE, 45, 348000000, 354000000);
        US_CABLE[46] = new Frequency(FrequencyType.US_CABLE, 46, 354000000, 360000000);
        US_CABLE[47] = new Frequency(FrequencyType.US_CABLE, 47, 360000000, 366000000);
        US_CABLE[48] = new Frequency(FrequencyType.US_CABLE, 48, 366000000, 372000000);
        US_CABLE[49] = new Frequency(FrequencyType.US_CABLE, 49, 372000000, 378000000);
        US_CABLE[50] = new Frequency(FrequencyType.US_CABLE, 50, 378000000, 384000000);
        US_CABLE[51] = new Frequency(FrequencyType.US_CABLE, 51, 384000000, 390000000);
        US_CABLE[52] = new Frequency(FrequencyType.US_CABLE, 52, 390000000, 396000000);
        US_CABLE[53] = new Frequency(FrequencyType.US_CABLE, 53, 396000000, 402000000);
        US_CABLE[54] = new Frequency(FrequencyType.US_CABLE, 54, 402000000, 408000000);
        US_CABLE[55] = new Frequency(FrequencyType.US_CABLE, 55, 408000000, 414000000);
        US_CABLE[56] = new Frequency(FrequencyType.US_CABLE, 56, 414000000, 420000000);
        US_CABLE[57] = new Frequency(FrequencyType.US_CABLE, 57, 420000000, 426000000);
        US_CABLE[58] = new Frequency(FrequencyType.US_CABLE, 58, 426000000, 432000000);
        US_CABLE[59] = new Frequency(FrequencyType.US_CABLE, 59, 432000000, 438000000);
        US_CABLE[60] = new Frequency(FrequencyType.US_CABLE, 60, 438000000, 444000000);
        US_CABLE[61] = new Frequency(FrequencyType.US_CABLE, 61, 444000000, 450000000);
        US_CABLE[62] = new Frequency(FrequencyType.US_CABLE, 62, 450000000, 456000000);
        US_CABLE[63] = new Frequency(FrequencyType.US_CABLE, 63, 456000000, 462000000);
        US_CABLE[64] = new Frequency(FrequencyType.US_CABLE, 64, 462000000, 468000000);
        US_CABLE[65] = new Frequency(FrequencyType.US_CABLE, 65, 468000000, 474000000);
        US_CABLE[66] = new Frequency(FrequencyType.US_CABLE, 66, 474000000, 480000000);
        US_CABLE[67] = new Frequency(FrequencyType.US_CABLE, 67, 480000000, 486000000);
        US_CABLE[68] = new Frequency(FrequencyType.US_CABLE, 68, 486000000, 492000000);
        US_CABLE[69] = new Frequency(FrequencyType.US_CABLE, 69, 492000000, 498000000);
        US_CABLE[70] = new Frequency(FrequencyType.US_CABLE, 70, 498000000, 504000000);
        US_CABLE[71] = new Frequency(FrequencyType.US_CABLE, 71, 504000000, 510000000);
        US_CABLE[72] = new Frequency(FrequencyType.US_CABLE, 72, 510000000, 516000000);
        US_CABLE[73] = new Frequency(FrequencyType.US_CABLE, 73, 516000000, 522000000);
        US_CABLE[74] = new Frequency(FrequencyType.US_CABLE, 74, 522000000, 528000000);
        US_CABLE[75] = new Frequency(FrequencyType.US_CABLE, 75, 528000000, 534000000);
        US_CABLE[76] = new Frequency(FrequencyType.US_CABLE, 76, 534000000, 540000000);
        US_CABLE[77] = new Frequency(FrequencyType.US_CABLE, 77, 540000000, 546000000);
        US_CABLE[78] = new Frequency(FrequencyType.US_CABLE, 78, 546000000, 552000000);
        US_CABLE[79] = new Frequency(FrequencyType.US_CABLE, 79, 552000000, 558000000);
        US_CABLE[80] = new Frequency(FrequencyType.US_CABLE, 80, 558000000, 564000000);
        US_CABLE[81] = new Frequency(FrequencyType.US_CABLE, 81, 564000000, 570000000);
        US_CABLE[82] = new Frequency(FrequencyType.US_CABLE, 82, 570000000, 576000000);
        US_CABLE[83] = new Frequency(FrequencyType.US_CABLE, 83, 576000000, 582000000);
        US_CABLE[84] = new Frequency(FrequencyType.US_CABLE, 84, 582000000, 588000000);
        US_CABLE[85] = new Frequency(FrequencyType.US_CABLE, 85, 588000000, 594000000);
        US_CABLE[86] = new Frequency(FrequencyType.US_CABLE, 86, 594000000, 600000000);
        US_CABLE[87] = new Frequency(FrequencyType.US_CABLE, 87, 600000000, 606000000);
        US_CABLE[88] = new Frequency(FrequencyType.US_CABLE, 88, 606000000, 612000000);
        US_CABLE[89] = new Frequency(FrequencyType.US_CABLE, 89, 612000000, 618000000);
        US_CABLE[90] = new Frequency(FrequencyType.US_CABLE, 90, 618000000, 624000000);
        US_CABLE[91] = new Frequency(FrequencyType.US_CABLE, 91, 624000000, 630000000);
        US_CABLE[92] = new Frequency(FrequencyType.US_CABLE, 92, 630000000, 636000000);
        US_CABLE[93] = new Frequency(FrequencyType.US_CABLE, 93, 636000000, 642000000);
        US_CABLE[94] = new Frequency(FrequencyType.US_CABLE, 94, 642000000, 648000000);
        US_CABLE[95] = new Frequency(FrequencyType.US_CABLE, 95, 90000000, 96000000);
        US_CABLE[96] = new Frequency(FrequencyType.US_CABLE, 96, 96000000, 102000000);
        US_CABLE[97] = new Frequency(FrequencyType.US_CABLE, 97, 102000000, 108000000);
        US_CABLE[98] = new Frequency(FrequencyType.US_CABLE, 98, 108000000, 114000000);
        US_CABLE[99] = new Frequency(FrequencyType.US_CABLE, 99, 114000000, 120000000);
        US_CABLE[100] = new Frequency(FrequencyType.US_CABLE, 100, 648000000, 654000000);
        US_CABLE[101] = new Frequency(FrequencyType.US_CABLE, 101, 654000000, 660000000);
        US_CABLE[102] = new Frequency(FrequencyType.US_CABLE, 102, 660000000, 666000000);
        US_CABLE[103] = new Frequency(FrequencyType.US_CABLE, 103, 666000000, 672000000);
        US_CABLE[104] = new Frequency(FrequencyType.US_CABLE, 104, 672000000, 678000000);
        US_CABLE[105] = new Frequency(FrequencyType.US_CABLE, 105, 678000000, 684000000);
        US_CABLE[106] = new Frequency(FrequencyType.US_CABLE, 106, 684000000, 690000000);
        US_CABLE[107] = new Frequency(FrequencyType.US_CABLE, 107, 690000000, 696000000);
        US_CABLE[108] = new Frequency(FrequencyType.US_CABLE, 108, 696000000, 702000000);
        US_CABLE[109] = new Frequency(FrequencyType.US_CABLE, 109, 702000000, 708000000);
        US_CABLE[110] = new Frequency(FrequencyType.US_CABLE, 110, 708000000, 714000000);
        US_CABLE[111] = new Frequency(FrequencyType.US_CABLE, 111, 714000000, 720000000);
        US_CABLE[112] = new Frequency(FrequencyType.US_CABLE, 112, 720000000, 726000000);
        US_CABLE[113] = new Frequency(FrequencyType.US_CABLE, 113, 726000000, 732000000);
        US_CABLE[114] = new Frequency(FrequencyType.US_CABLE, 114, 732000000, 738000000);
        US_CABLE[115] = new Frequency(FrequencyType.US_CABLE, 115, 738000000, 744000000);
        US_CABLE[116] = new Frequency(FrequencyType.US_CABLE, 116, 744000000, 750000000);
        US_CABLE[117] = new Frequency(FrequencyType.US_CABLE, 117, 750000000, 756000000);
        US_CABLE[118] = new Frequency(FrequencyType.US_CABLE, 118, 756000000, 762000000);
        US_CABLE[119] = new Frequency(FrequencyType.US_CABLE, 119, 762000000, 768000000);
        US_CABLE[120] = new Frequency(FrequencyType.US_CABLE, 120, 768000000, 774000000);
        US_CABLE[121] = new Frequency(FrequencyType.US_CABLE, 121, 774000000, 780000000);
        US_CABLE[122] = new Frequency(FrequencyType.US_CABLE, 122, 780000000, 786000000);
        US_CABLE[123] = new Frequency(FrequencyType.US_CABLE, 123, 786000000, 792000000);
        US_CABLE[124] = new Frequency(FrequencyType.US_CABLE, 124, 792000000, 798000000);
        US_CABLE[125] = new Frequency(FrequencyType.US_CABLE, 125, 798000000, 804000000);
        US_CABLE[126] = new Frequency(FrequencyType.US_CABLE, 126, 804000000, 810000000);
        US_CABLE[127] = new Frequency(FrequencyType.US_CABLE, 127, 810000000, 816000000);
        US_CABLE[128] = new Frequency(FrequencyType.US_CABLE, 128, 816000000, 822000000);
        US_CABLE[129] = new Frequency(FrequencyType.US_CABLE, 129, 822000000, 828000000);
        US_CABLE[130] = new Frequency(FrequencyType.US_CABLE, 130, 828000000, 834000000);
        US_CABLE[131] = new Frequency(FrequencyType.US_CABLE, 131, 834000000, 840000000);
        US_CABLE[132] = new Frequency(FrequencyType.US_CABLE, 132, 840000000, 846000000);
        US_CABLE[133] = new Frequency(FrequencyType.US_CABLE, 133, 846000000, 852000000);
        US_CABLE[134] = new Frequency(FrequencyType.US_CABLE, 134, 852000000, 858000000);
        US_CABLE[135] = new Frequency(FrequencyType.US_CABLE, 135, 858000000, 864000000);
        US_CABLE[136] = new Frequency(FrequencyType.US_CABLE, 136, 864000000, 870000000);
        US_CABLE[137] = new Frequency(FrequencyType.US_CABLE, 137, 870000000, 876000000);
        US_CABLE[138] = new Frequency(FrequencyType.US_CABLE, 138, 876000000, 882000000);
        US_CABLE[139] = new Frequency(FrequencyType.US_CABLE, 139, 882000000, 888000000);
        US_CABLE[140] = new Frequency(FrequencyType.US_CABLE, 140, 888000000, 894000000);
        US_CABLE[141] = new Frequency(FrequencyType.US_CABLE, 141, 894000000, 900000000);
        US_CABLE[142] = new Frequency(FrequencyType.US_CABLE, 142, 900000000, 906000000);
        US_CABLE[143] = new Frequency(FrequencyType.US_CABLE, 143, 906000000, 912000000);
        US_CABLE[144] = new Frequency(FrequencyType.US_CABLE, 144, 912000000, 918000000);
        US_CABLE[145] = new Frequency(FrequencyType.US_CABLE, 145, 918000000, 924000000);
        US_CABLE[146] = new Frequency(FrequencyType.US_CABLE, 146, 924000000, 930000000);
        US_CABLE[147] = new Frequency(FrequencyType.US_CABLE, 147, 930000000, 936000000);
        US_CABLE[148] = new Frequency(FrequencyType.US_CABLE, 148, 936000000, 942000000);
        US_CABLE[149] = new Frequency(FrequencyType.US_CABLE, 149, 942000000, 948000000);
        US_CABLE[150] = new Frequency(FrequencyType.US_CABLE, 150, 948000000, 954000000);
        US_CABLE[151] = new Frequency(FrequencyType.US_CABLE, 151, 954000000, 960000000);
        US_CABLE[152] = new Frequency(FrequencyType.US_CABLE, 152, 960000000, 966000000);
        US_CABLE[153] = new Frequency(FrequencyType.US_CABLE, 153, 966000000, 972000000);
        US_CABLE[154] = new Frequency(FrequencyType.US_CABLE, 154, 972000000, 978000000);
        US_CABLE[155] = new Frequency(FrequencyType.US_CABLE, 155, 978000000, 984000000);
        US_CABLE[156] = new Frequency(FrequencyType.US_CABLE, 156, 984000000, 990000000);
        US_CABLE[157] = new Frequency(FrequencyType.US_CABLE, 157, 990000000, 996000000);
        US_CABLE[158] = new Frequency(FrequencyType.US_CABLE, 158, 996000000, 1002000000);


    }
}
