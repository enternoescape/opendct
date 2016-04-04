/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package opendct.video.ccextractor;

import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;

import static opendct.video.ccextractor.CCExtractorCommon.*;

public class CCExtractorSrtInstance {
    private static final Logger logger = LogManager.getLogger(CCExtractorSrtInstance.class);

    private Process ccExtractor;
    private InputStreamReader inputStdStream;
    private InputStreamReader inputErrStream;
    private BufferedOutputStream outputStream;
    private ReadOutput outputStdStream;
    private ReadOutput outputErrStream;
    private byte streamOutBuffer[];

    public CCExtractorSrtInstance(String parameters, String baseFilename) throws IOException {
        if (CC_BINARY.equals("")) {
            throw new IOException("Unable to use CCExtractor because the OS is not supported.");
        }

        if (Util.isNullOrEmpty(parameters)) {
            parameters = SUGGESTED_PARAMETERS;
        }

        ccExtractor = RUNTIME.exec(CC_BINARY + STD_SRT_PARAMETERS + parameters + "-o \"" + baseFilename + ".srt\"");

        inputStdStream = new InputStreamReader(ccExtractor.getInputStream());
        inputErrStream = new InputStreamReader(ccExtractor.getErrorStream());
        outputStream = new BufferedOutputStream(ccExtractor.getOutputStream());

        // Create these files ahead of time so SageTV will report that the subtitles exist when the
        // recording first starts.
        new File(baseFilename + ".CC1.srt").createNewFile();
        if (parameters.contains("-12")) {
            new File(baseFilename + ".CC2.srt").createNewFile();
        }

        startReadOutput();
    }

    /**
     * Write data to be processed by CCExtractor.
     *
     * @param data The incoming data needs to already be flipped. The parameter will return
     *             unmodified.
     */
    public synchronized void streamIn(ByteBuffer data) {
        int length = data.remaining();

        if (length == 0) {
            return;
        }

        if (streamOutBuffer == null || streamOutBuffer.length < length) {
            streamOutBuffer = new byte[length * 2];
        }

        ByteBuffer slice = data.slice();
        slice.get(streamOutBuffer, 0, length);

        try {
            outputStream.write(streamOutBuffer, 0, length);
        } catch (IOException e) {
            logger.error("Unable to write to CCExtractor => ", e);
        }
    }

    /**
     * Write data to be processed by CCExtractor.
     *
     * @param data The incoming data needs to already be flipped. The parameter will return
     *             unmodified.
     * @param position The position to start reading from the array.
     * @param length The number of bytes to read from the array.
     */
    public synchronized void streamIn(byte data[], int position, int length) {
        if (length == 0) {
            return;
        }

        try {
            outputStream.write(data, position, length);
        } catch (IOException e) {
            logger.error("Unable to write to CCExtractor => ", e);
        }
    }

    /**
     * Stops the current instance of CCExtractor and closes all open streams.
     */
    public synchronized void setClosed() {
        try {
            ccExtractor.destroy();
        } catch (Exception e) {
            logger.debug("Exception while waiting for CCExtractor to exit =>, e");
        }

        try {
            outputStream.close();
        } catch (IOException e) {
            logger.error("Error while closing CCExtractor stdin stream => ", e);
        }

        outputStdStream.setClosed();
        outputErrStream.setClosed();
    }

    private void startReadOutput() {
        Thread streamOut;
        outputStdStream = new ReadOutput(inputStdStream, "stdout");
        outputErrStream = new ReadOutput(inputErrStream, "errout");

        streamOut = new Thread(outputStdStream);
        streamOut.setName("CCExtractor-" + streamOut.getId());;
        streamOut.start();

        streamOut = new Thread(outputErrStream);
        streamOut.setName("CCExtractor-" + streamOut.getId());;
        streamOut.start();
    }

    public class ReadOutput implements Runnable {
        private boolean closed = false;
        private final InputStreamReader reader;
        private final String streamType;

        public ReadOutput(InputStreamReader reader, String streamType) {
            this.reader = reader;
            this.streamType = streamType;
        }

        public void setClosed() {
            closed = true;

            try {
                reader.close();
            } catch (IOException e) {
                logger.error("Error while closing CCExtractor {} stream => ", streamType, e);
            }
        }

        @Override
        public void run() {
            logger.debug("CCExtractor {} thread started.", streamType);

            char buffer[] = new char[2048];
            StringBuilder builder = new StringBuilder(2048);
            int readLen;

            while (!closed) {
                try {
                    readLen = reader.read(buffer, 0, buffer.length);
                } catch (IOException e) {
                    logger.error("Error while reading from CCExtractor {} stream => ", streamType, e);
                    break;
                }

                if (readLen == -1) {
                    break;
                } else if (readLen == 0) {
                    continue;
                }

                for (int i = 0; i < readLen; i++) {
                    if (buffer[i] == '\n') {
                        logger.info("{}: {}", streamType, builder.toString());
                        builder.setLength(0);
                    }

                    if (buffer[i] == '\r') {
                        continue;
                    }

                    builder.append(buffer[i]);
                }

                //logger.info(new String(buffer, 0, readLen));
            }

            logger.info("CCExtractor {} thread stopped.", streamType);
        }
    }
}
