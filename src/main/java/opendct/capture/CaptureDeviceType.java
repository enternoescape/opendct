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

package opendct.capture;

public enum CaptureDeviceType {
    UNKNOWN,       // This is the default value for the BasicCaptureDevice class. No device can have this value.
    DCT_INFINITV,  // Ceton InfiniTV
    DCT_HDHOMERUN, // Silicondust HDHomeRun Prime
    QAM_INFINITV,  // Ceton InfiniTV without a cable card.
    QAM_HDHOMERUN, // Silicondust HDHomeRun Prime without a cable card or other device configured for ClearQAM.
    ATSC_HDHOMERUN,// Silicondust HDHomeRun configured for US ATSC broadcast.
    DVBC_HDHOMERUN,// Silicondust HDHomeRun configured for EU cable.
    DVBT_HDHOMERUN,// Silicondust HDHomeRun configured for EU over the air.
    HDHOMERUN,     // Silicondust HDHomeRun devices.
    PI_HDPVR,      // HD-PVR connected to a Raspberry Pi
    PI_TUNER,      // any USB tuner connected to a Raspberry Pi
    SCREENGRABBER, // any source that is actually just recording content via screen capture.
    LIVE_STREAM,   // any Internet sourced live feed.
    AUDIO_ONLY,    // any source that's just audio. The consumer may need to add blank video for playback to work.
    HYBRID,        // any capture device that uses child capture devices. the child devices need to have a type other than HYBRID.
    NATIVE,        // any encoder that requires native binary to function and supports both Windows and Linux.
    NATIVE_WINDOWS,// any encoder that requires native binary to function and only supports Windows.
    NATIVE_LINUX   // any encoder that requires native binary to function and only supports Linux.
}
