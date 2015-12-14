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

package opendct.producer;

import java.io.IOException;
import java.net.InetAddress;

public interface RTPProducer extends SageTVProducer {

    /**
     * This method is used to tell this producer what port to listen on and what IP address to
     * accept UDP packets from. This method should throw an IllegalThreadStateException if the
     * thread is running and this method is called.
     *
     * @param streamRemoteIP  This is the IP address of the RTP packet source.
     * @param streamLocalPort This is the local port that the packets will be sent.
     * @throws IOException If the port cannot be opened.
     */
    public void setStreamingSocket(InetAddress streamRemoteIP, int streamLocalPort) throws IOException;

    /**
     * Returns the port this producer is listening on.
     */
    public int getLocalPort();

    /**
     * Returns the IP address this producer is listening to.
     */
    public InetAddress getRemoteIPAddress();
}
