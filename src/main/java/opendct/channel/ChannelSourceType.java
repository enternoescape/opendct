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
     * This will use the an HDHomeRun to get updates to the available channels on the lineup.
     */
    HDHOMERUN,
    /**
     * TODO: HDHOMERUN_HTTP_QAM
     *
     * When you perform a channel scan on an HDHomeRun device for ClearQAM, it creates channels
     * starting at 5000. This source type when selected and a Digital Cable Tuner is available and
     * not in use will create re-maps of the > 5000 channels to their virtual channels according to
     * the referenced DCT.
     */
    HDHOMERUN_HTTP_QAM,
    /**
     * This will copy another lineup to get updates, the enable/disabled channel statuses in the
     * source lineup will not effect the enabled/disabled channels in this lineup. The address needs
     * to be the name of the lineup to copy. If the lineup to be copied does not exist, it will not
     * be updated.
     */
    COPY,
    /**
     * This is used for lineups that have no method to update the available channels or if you do
     * not want the channels to ever be updated.
     */
    STATIC
}
