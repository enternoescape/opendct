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

package opendct.channel;

public enum ChannelSourceType {
    /**
     * This will use the InfiniTV to get updates to the available channels on the lineup.
     */
    INFINITV,
    /**
     * This will use the Prime to get updates to the available channels on the lineup.
     */
    HDHOMERUN,
    /**
     * This will pull the channel map from the Prime, then tune each channel to get the frequency
     * and program. It will then match that with the channel map from an InfiniTV to remap the
     * channels to VChannels.
     */
    PRIME_QAM_INFINITV_REMAP,
    /**
     * This is used for lineups that have no method to update the available channels.
     */
    STATIC
}
