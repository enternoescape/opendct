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

package opendct.jetty;

public class JettyManager {
    //TODO: [js] Create a simple web server for communicating with and troubleshooting the network encoder.

    /*
    Idea #1

    Overview layout.
    ================
     Network Encoders |       Status       |
    Network Encoder 0 | Streaming/Inactive |
    Network Encoder 1 | Streaming/Inactive |
    Network Encoder 2 | Streaming/Inactive |
    Network Encoder 3 | Streaming/Inactive |

    Network Encoders - These are links that will take you to the Detail layout.

    Status - Overall this will just be a very general state of the network encoder. Beyond the most obvious status, I
             think we could have things like Ready to indicate that the tuner is in a "hot" state whereby it just needs
             a channel and you will be streaming in less than a second. We can have Broken to indicate the tuner is in a
             very bad state that it doesn't know how to handle.

    Detail layout.
    ==============
    Network Encoders List | Status Tab | Properties Tab | Troubleshoot Tab
    ----------------------------------------------------------------------
    Network Encoder 0     |
    Network Encoder 1     |                      Body
    Network Encoder 2     |
    Network Encoder 3     |

    Network Encoders - List of capture devices by network encoder name available. Clicking on one changes the details
                       displayed in the Body to show the details of that tuner. For mobile devices, this might need to
                       be done a little differently. Maybe a button in the upper left that when tapped shows this list.

    Status - This is a tab. It displays all information obtainable about the current status of the selected network
             encoder. Depending on how much detail is available, we might need some tabs within the tab to keep it from
             looking ridiculous.

    Properties - This is a tab. Displays all properties pertaining to the selected network encoder. Will even allow
                 changes to be made at runtime if possible. Changes might need to be blocked when the network encoder is
                 actively streaming.

    Troubleshoot - This is a tab. Allows you to manipulate the selected network encoder impersonating a SageTV server.
                   Other helpful runtime manipulations related to debugging will be available here too.

     */
}
