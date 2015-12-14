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

package opendct.video.rtsp;

import java.net.URI;
import java.net.UnknownHostException;

public interface RTSPClient {

    // This is used to talk to the streaming device via the RTSP protocol.
    // It can be called as many times as needed to change the direction of
    // the stream back to the address of this computer. Generally that
    // means you need to call this every time you tune into a new channel.
    // The producer thread should be already be running before this method
    // is called so it will receive the very first packets. Returns false
    // if the configuration failed to be changed or the producer thread is
    // not running.
    public boolean configureRTPStream(URI streamOutURI, int streamInPort) throws UnknownHostException;

    // This will send a TEARDOWN command via RTSP to the streaming device.
    // This stops the UDP stream of RDP packets.
    public void stopRTPStream(URI streamOutURI) throws UnknownHostException;

    // This is used by the capture device so it knows what port should be
    // receiving the RTP data. It will return 0 if no stream has been
    // configured.
    public int getRTPStreamPort();

}
