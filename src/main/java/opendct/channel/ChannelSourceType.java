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
     * This will copy another lineup to get updates, the tunable/ignored channel statuses in the
     * source lineup will not effect the tunable/ignored channels in this lineup. The lineup.address
     * property needs to be the name of the lineup to copy. If the lineup to be copied does not
     * exist, this lineup will not be updated and an error will appear in the log.
     */
    COPY,
    /**
     * This is used for lineups that have no method to update the available channels or if it is
     * desired to not have the channels updated ever.
     */
    STATIC
}
