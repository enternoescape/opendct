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

package opendct.sagetv;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class SageTVClient {
    private final static Logger logger = LogManager.getLogger(SageTVClient.class);

    //TODO:[js] Implement a simple client for testing SageTV communication without SageTV being present.

    private InputStreamReader in;
    private OutputStreamWriter out;
    private String address;
    private int port;
    private Socket socket;

    public void connect(String address, int port) throws IOException {
        disconnect();
        socket = new Socket(address, port);
        this.address = address;
        this.port = port;

        in = new InputStreamReader(socket.getInputStream());
        out = new OutputStreamWriter(socket.getOutputStream());
    }

    public void disconnect() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.debug("Exception while closing socket => {}", e.getMessage());
            }
        }

        socket = null;
        port = -1;
        address = null;
    }


    public String startRecording(String captureDevice, String channel, String filename) throws IOException, InterruptedException {
        if (socket == null) {
            return "ERROR socket not connected.";
        } else if (!socket.isConnected()) {
            connect(address, port);
        }

        out.write("START " + captureDevice + "|0|" + channel + "|0|" + filename + "|Great\r\n");
        out.flush();

        while (!in.ready()) {
            Thread.sleep(100);
        }

        StringBuilder stringBuilder = new StringBuilder(4);
        while (in.ready()) {
            stringBuilder.append((char)in.read());
        }

        return stringBuilder.toString();
    }

    public String stopRecording(String captureDevice) throws IOException, InterruptedException {
        if (socket == null) {
            return "ERROR socket not connected.";
        } else if (!socket.isConnected()) {
            connect(address, port);
        }

        out.write("STOP " + captureDevice + "\r\n");
        out.flush();

        while (!in.ready()) {
            Thread.sleep(100);
        }

        StringBuilder stringBuilder = new StringBuilder(4);
        while (in.ready()) {
            stringBuilder.append((char)in.read());
        }

        return stringBuilder.toString();
    }
}
