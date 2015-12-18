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

import opendct.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.StringTokenizer;

public class DCTRTSPClientImpl implements RTSPClient {
    private final Logger logger = LogManager.getLogger(DCTRTSPClientImpl.class);
    private int cSeq = 0;
    private InetAddress streamRemoteIP;
    private URI streamRemoteURI;
    private volatile int streamLocalPort = 0;
    private String sessionString = null;

    // No checks for the status of anything else currently running are
    // needed since this should not cause any exceptions, but if set
    // incorrectly, it will stop the production thread from receiving
    // any packets. The producer should already be running before you
    // can use this method. If there is a configuration problem the
    // method will return false.
    public synchronized boolean configureRTPStream(URI streamOutURI, int streamInPort) throws UnknownHostException {
        logger.entry();

        this.streamLocalPort = streamInPort;
        this.streamRemoteURI = streamOutURI;
        this.streamRemoteIP = InetAddress.getByName(streamOutURI.getHost());

        int retryCount = Config.getInteger("rtsp.retry_count", 3);
        int retryWait = Config.getInteger("rtsp.retry_wait_ms", 500);
        int retry = retryCount;

        /*if (this.streamLocalPort == 0) {
            this.streamLocalPort = OpendctConfig.getFreeRTSPPort(streamOutURI.toString());
        }*/

        while (true) {

            // Only sleeps if the first attempt fails.
            if (retry != retryCount) {
                // DCT's don't like when you're too aggressive.
                try {
                    Thread.sleep(retryWait);
                } catch (InterruptedException e) {
                    return logger.exit(false);
                }
            }

            if (retry-- < 0) {
                logger.error("Failed to configure RTSP after {} attempts.", retryCount + 1);
                return logger.exit(false);
            }

            sessionString = null;

            if (!sendDescribe()) continue;
            if (!sendSetup()) continue;
            if (!sendPlay()) continue;
            break;
        }

        return logger.exit(true);
    }

    public synchronized void stopRTPStream(URI streamOutURI) throws UnknownHostException {
        logger.entry();

        this.streamRemoteURI = streamOutURI;
        this.streamRemoteIP = InetAddress.getByName(streamOutURI.getHost());

        // Send TEARDOWN over RTSP to tuner.
        sendStop();

        // Reset communication variables.
        cSeq = 0;
        streamLocalPort = 0;
        sessionString = null;

        logger.exit();
        return;
    }

    public int getRTPStreamPort() {
        return streamLocalPort;
    }

    private String[] sendContent(String content) {
        logger.entry();
        ArrayList<String> response = new ArrayList<String>();

        Socket socket;
        PrintWriter out;
        BufferedReader in;

        try {
            socket = new Socket(streamRemoteIP, streamRemoteURI.getPort());
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            logger.error("There was a problem connecting to {} => {}", streamRemoteURI.toString(), e);
            return logger.exit(null);
        }


        logger.debug("Sending RTSP request: \n{}", content);
        try {
            out.print(content);
            out.flush();

            Integer contentLength = -1;
            Integer contentConsumed = 0;
            String line = in.readLine();
            while (line != null && line.length() > 0) {
                if (line.startsWith("Content-Length: ")) {
                    String length = line.substring("Content-Length: ".length());
                    contentLength = Integer.parseInt(length);
                }

                response.add(line);

                if (contentLength > 0) {
                    char[] contentChars = new char[contentLength];
                    while (contentConsumed < contentLength) {
                        contentConsumed += in.read(contentChars, contentConsumed, contentLength - contentConsumed);
                    }

                    // Maybe not the most efficient way of doing this, but it works.
                    line = new String(contentChars);

                    // Since we know this will not be binary content, we will parse
                    // it into strings like all of the other content.
                    // This should handle all cases for line endings.
                    response.addAll(Arrays.asList(line.split("\\r\\n|[\\r\\n]")));
                    break;
                }

                line = in.readLine();
            }

            logger.trace("Received RTSP response: \r\n{}", response);

            //We will try to clean up the correct way, but it may not always be successful.
            try {
                logger.trace("Closing inbound connection...");
                in.close();

                logger.trace("Closing outbound connection...");
                out.close();

                logger.trace("Closing socket connection...");
                socket.close();
            } catch (Exception e) {
                logger.trace("Failed to close socket correctly after RTSP.");
            }

        } catch (Exception e) {
            logger.error("There was a problem communicating with {} => ", streamRemoteURI.toString(), e);
            if (response.size() > 0) {
                logger.debug("Received partial RTSP response: \r\n{}", response);
            }
            return logger.exit(null);
        }

        String acceptCodes[];
        if (content.startsWith("TEARDOWN")) {
            acceptCodes = new String[]{"2", "454"};
        } else {
            acceptCodes = new String[]{"2"};
        }

        if (response.size() == 0 || !checkStatusForError(response.get(0), acceptCodes)) {
            logger.error("Server returned an error message.");
            return logger.exit(null);
        }

        return logger.exit(response.toArray(new String[response.size()]));
    }

    private boolean checkStatusForError(String header, String acceptCodes[]) {
        logger.entry(header);
        StringTokenizer tokens = new StringTokenizer(header, " ");

        if (tokens.countTokens() < 3) {
            logger.error("Invalid response header.");
            return logger.exit(false);
        }

        String protocol = tokens.nextToken();
        String statusCode = tokens.nextToken();
        //String statusMessage = tokens.nextToken();

        for (String acceptCode : acceptCodes) {
            if (statusCode.startsWith(acceptCode)) {
                return logger.exit(true);
            }
        }

        return logger.exit(false);
    }

    private Boolean sendDescribe() {
        logger.entry();
        String content = "DESCRIBE " + streamRemoteURI + " RTSP/1.0\r\n" +
                "CSeq: " + cSeq++ + "\r\n" +
                "User-Agent: networkencoder-dct\r\n" +
                "Accept: application/sdp\r\n\r\n";

        String[] response;
        response = sendContent(content);

        if (response == null || response.length < 6) {
            logger.error("Unable to get RTSP service description.");
            return logger.exit(false);
        }

        /*
        RTSP/1.0 200 OK
        CSeq: 0
        Server: libcetonrtsp/1.0
        Date: Sat, 03 Jan 1970 18:05:26 GMT
        Content-Type: application/sdp
        Content-Base: rtsp://x.x.x.x/cetonmpeg0/
        Content-Length: 227

        v=0
        o=- 2209226496 1 IN IP4 127.0.0.1
        s=Session Streamed By libcetonrtsp/1.0
        i=cetonmpeg0
        t=0 0
        a=tool:libcetonrtsp
        a=type:broadcast
        a=control:*
        a=range:npt=0-
        m=video 0 RTP/AVP 33
        c=IN IP4 0.0.0.0
        a=control:track0
        */

        // We could probably parse this information, but we already know what to
        // use for these devices.

        // Ex. video 0 RTP/AVP 33 = MP2T which is the only standard we should be using.

        return logger.exit(true);
    }

    private Boolean sendSetup() {
        logger.entry();

        String content = "SETUP " + streamRemoteURI + " RTSP/1.0\r\n" +
                "CSeq: " + cSeq++ + "\r\n" +
                "User-Agent: networkencoder-dct\r\n" +
                "Transport: RTP/AVP;unicast;client_port=" + streamLocalPort + "-" + (streamLocalPort + 1) + ";mode=PLAY\r\n\r\n";

        String[] response;
        response = sendContent(content);

        if (response == null || response.length < 5) {
            logger.error("Unable to setup RTSP.");
            return logger.exit(false);
        }

        /*
        RTSP/1.0 200 OK
        CSeq: 1
        Server: libcetonrtsp/1.0
        Date: Sat, 03 Jan 1970 18:05:26 GMT
        Session: 95151
        Transport: RTP/AVP;unicast;client_port=5010-5011;server_port=0-0
        */

        for (int i = 3; i < response.length; i++) {
            if (response[i].startsWith("Session: ")) {
                //This will only be reused as a string so there is no
                //benefit in making it a proper Integer type.
                sessionString = response[i];
                break;
            }
        }

        if (sessionString == null) {
            logger.error("No session string found.");
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    private Boolean sendPlay() {
        logger.entry();

        String content = "PLAY " + streamRemoteURI + " RTSP/1.0\r\n" +
                "CSeq: " + cSeq++ + "\r\n" +
                "User-Agent: networkencoder-dct \r\n" +
                sessionString + "\r\n\r\n";

        String[] response;
        response = sendContent(content);

        if (response == null || response.length < 5) {
            logger.error("Unable to play RTSP.");
            return logger.exit(false);
        }

        /*
        RTSP/1.0 200 OK
        CSeq: 2
        Server: libcetonrtsp/1.0
        Date: Sat, 03 Jan 1970 18:05:27 GMT
        Session: 95151
        RTP-info: url=rtsp://x.x.x.x:8554/cetonmpeg0/track0;seq=1621630855;rtptime=55611
         */

        // Nothing to parse here.

        return logger.exit(true);
    }

    // Run this after you have run Stop, then ConnectionComplete via UPnP
    private void sendStop() {
        logger.entry();

        if (sessionString != null) {
            String content = "TEARDOWN " + streamRemoteURI + " RTSP/1.0\r\n" +
                    "CSeq: " + cSeq++ + "\r\n" +
                    "User-Agent: networkencoder-dct\r\n" +
                    sessionString + "\r\n\r\n";

            // We don't really care what response we get. It seems to usually be an error.
            // I'm not even sure this is really required for this type of device since
            // ConnectionComplete over UPnP will terminate the connection.
            sendContent(content);

            /*
            RTSP/1.0 454 Session Not Found
            CSeq: 3
            */
        }
        logger.exit();
    }
}
