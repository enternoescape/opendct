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

import opendct.config.Config;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;

import static opendct.video.ccextractor.CCExtractorCommon.*;

public class CCExtractorBinaryInstance {
    //TODO: This is incomplete and I'm not sure it's needed anymore. Consider removing this file
    // once CCExtractor integration is completed.
    private static final Logger logger = LogManager.getLogger(CCExtractorBinaryInstance.class);

    private Process ccExtractor;
    private BufferedInputStream inputStream;
    private BufferedInputStream errInputStream;
    private OutputStream outputStream;
    private byte streamOutBuffer[];
    private Thread streamOut;
    private final String filenames[];
    private final FileChannel fileChannel[];

    public CCExtractorBinaryInstance(String parameters, String... filenames) throws IOException {
        if (CC_BINARY.equals("")) {
            throw new IOException("Unable to use CCExtractor because the OS is not supported.");
        }

        this.filenames = filenames;
        fileChannel = new FileChannel[filenames.length];

        if (Util.isNullOrEmpty(parameters)) {
            parameters = SUGGESTED_PARAMETERS;
        }

        ccExtractor = RUNTIME.exec(CC_BINARY + STD_BIN_PARAMETERS + parameters);

        inputStream = new BufferedInputStream(ccExtractor.getInputStream());
        errInputStream = new BufferedInputStream(ccExtractor.getErrorStream());
        outputStream = ccExtractor.getOutputStream();

        for (int i = 0; i < filenames.length; i++) {
            fileChannel[i] = FileChannel.open(Paths.get(filenames[i]));
        }
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

        if (streamOut == null) {
            streamOut = new Thread(new StreamOut());
            streamOut.start();
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

        if (streamOut == null) {
            streamOut = new Thread(new StreamOut());
            streamOut.start();
        }

        try {
            outputStream.write(data, position, length);
        } catch (IOException e) {
            logger.error("Unable to write to CCExtractor => ", e);
        }
    }

    private class StreamOut implements Runnable {
        private byte streamInBuffer[];
        private byte errStreamInBuffer[];

        private int streamInWritePos = 0;
        private int errStreamWritePos = 0;
        private int streamInReadPos = 0;
        private int errStreamReadPos = 0;

        @Override
        public void run() {
            streamInBuffer = new byte[1048576];
            errStreamInBuffer = new byte[1048576];
            CaptionData captionData[] = new CaptionData[Byte.MAX_VALUE];
            long currentTimestamp = 0;
            int remainingBlocks = 0;
            int ccType = 0;
            int ccValid = 0;
            boolean goodHeader = false;
            int bytesAvailable;

            while (!Thread.currentThread().isInterrupted()) {
                if (bytesAvailable() == 0) {
                    streamInReadPos = 0;
                    streamInWritePos = 0;
                }

                try {
                    while ((bytesAvailable = errInputStream.available()) > 0 && errStreamWritePos < errStreamInBuffer.length) {
                        errStreamWritePos += errInputStream.read(errStreamInBuffer, errStreamWritePos, bytesAvailable);
                    }
                } catch (IOException e) {
                    logger.error("Unable to read stderr from CCExtractor => ", e);
                }

                try {
                    while ((bytesAvailable = inputStream.available()) > 0 && streamInWritePos < streamInBuffer.length) {
                        streamInWritePos += inputStream.read(streamInBuffer, streamInWritePos, bytesAvailable);
                    }
                } catch (IOException e) {
                    logger.error("Unable to read stdin from CCExtractor => ", e);
                }

                if (errBytesAvailable() > 0) {
                    logger.info("CCExtractor: {}", new String(errStreamInBuffer, errStreamReadPos, errStreamWritePos, Config.STD_BYTE));
                    errStreamReadPos = 0;
                    errStreamWritePos = 0;
                }

                if (bytesAvailable() > 0) {
                    if (!goodHeader) {
                        if (streamInWritePos > 11) {
                            if (streamInBuffer[0] != MAGIC_NUMBER[0] ||
                                    streamInBuffer[1] != MAGIC_NUMBER[1] ||
                                    streamInBuffer[2] != MAGIC_NUMBER[2]) {
                                logger.error("Magic number mismatch. Expected {}, got {}, {}, {}." +
                                        " Terminating reader.",
                                        MAGIC_NUMBER,
                                        streamInBuffer[0], streamInBuffer[1], streamInBuffer[2]);

                                return;
                            }

                            if (streamInBuffer[3] != CCEXTRACTOR_ID) {
                                logger.error("The program did not identify itself as CCExtractor." +
                                        " Expected {}, got {}. Terminating reader.",
                                        CCEXTRACTOR_ID, streamInBuffer[3]);
                            }

                            int programVersion = (streamInBuffer[4] & 0xff) << 8 & (streamInBuffer[5] & 0xff);
                            logger.info("CCExtractor program version = {}", programVersion);

                            if (streamInBuffer[6] != FILE_FORMAT[0] ||
                                    streamInBuffer[7] != FILE_FORMAT[1]) {
                                logger.error("Unexpected file format. Expected {}, got {}, {}." +
                                                " Terminating reader.",
                                        FILE_FORMAT,
                                        streamInBuffer[6], streamInBuffer[7]);

                                return;
                            }

                            if (streamInBuffer[8] != RESERVED[0] ||
                                    streamInBuffer[9] != RESERVED[1] ||
                                    streamInBuffer[10] != RESERVED[2]) {
                                logger.error("Reserved number mismatch." +
                                        " Expected {}, got {}, {}, {}." +
                                                " Terminating reader.",
                                        RESERVED,
                                        streamInBuffer[8], streamInBuffer[9], streamInBuffer[10]);

                                return;
                            }

                            logger.info("CCExtractor provided a good header.");
                            goodHeader = true;
                        } else {
                            continue;
                        }
                    } else {
                        if (remainingBlocks == 0) {
                            if (bytesAvailable() > 9) {
                                currentTimestamp = Util.getLongMSB(streamInBuffer, streamInReadPos);
                                streamInReadPos += 8;
                                remainingBlocks = Util.getShortMSB(streamInBuffer, streamInReadPos) & 0xffff;
                                streamInReadPos += 2;
                            } else {
                                moveData(10);
                                continue;
                            }
                        } else {
                            // The caption data is read in groupings of 3, so don't read less than 3
                            // at a time or the alignment will be off.
                            while (bytesAvailable() > 2 && remainingBlocks > 0) {
                                ccType = streamInBuffer[streamInReadPos] & 0x3;
                                ccValid = (streamInBuffer[streamInReadPos++] & 0x4) >> 2;

                                if (captionData[ccType] == null) {
                                    captionData[ccType] = new CaptionData();
                                }

                                if (captionData[ccType].lastTimestamp == 0) {
                                    captionData[ccType].lastTimestamp = currentTimestamp;
                                }

                                captionData[ccType].characters.append((char)streamInBuffer[streamInReadPos++]);
                                captionData[ccType].characters.append((char)streamInBuffer[streamInReadPos++]);

                                if (ccValid > 0) {
                                    logger.info("CC: {}", getSrt(captionData[ccType], currentTimestamp));
                                    captionData[ccType].newCaption();
                                }

                                remainingBlocks -= 1;

                                if (remainingBlocks == 0) {
                                    if (bytesAvailable() > 9) {
                                        currentTimestamp = Util.getLongMSB(streamInBuffer, streamInReadPos);
                                        streamInReadPos += 8;
                                        remainingBlocks = Util.getShortMSB(streamInBuffer, streamInReadPos) & 0xffff;
                                        streamInReadPos += 2;
                                    } else {
                                        moveData(10);
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        private void moveData(int bytesNeeded) {
            // For this to work, the data needs to be completely on the other half of the array.
            if (bytesNeeded * 2 > streamInBuffer.length) {
                logger.error("Cannot move data because bytesNeeded * 2 = {} > streamInBuffer.length = {}",
                        bytesNeeded * 2, streamInBuffer.length);

                return;
            }

            if (streamInBuffer.length - streamInReadPos < bytesNeeded) {
                System.arraycopy(streamInBuffer, streamInReadPos, streamInBuffer, 0, bytesAvailable());
                streamInWritePos = bytesAvailable();
                streamInReadPos = 0;
            }
        }

        private int bytesAvailable() {
            return streamInWritePos - streamInReadPos;
        }

        private int errBytesAvailable() {
            return errStreamWritePos - errStreamReadPos;
        }
    }

    private StringBuilder srtBuilder = new StringBuilder();
    private String getSrt(CaptionData captionData, long fts) {

        captionData.sequence += 1;

        srtBuilder.setLength(0);
        srtBuilder.append(captionData.sequence).append(Config.NEW_LINE);
        srtBuilder.append(captionData.lastTimestamp).append(SRT_RANGE).append(fts);
        srtBuilder.append(captionData.characters).append(Config.NEW_LINE);
        srtBuilder.append(Config.NEW_LINE);

        return srtBuilder.toString();
    }

    private class CaptionData {
        protected long sequence = 0;
        protected long lastTimestamp = 0;
        protected StringBuilder characters = new StringBuilder();

        protected void newCaption() {
            characters.setLength(0);
            lastTimestamp = 0;
        }

        @Override
        public String toString() {
            return "CaptionData{" +
                    "sequence=" + sequence +
                    ", lastTimestamp=" + lastTimestamp +
                    ", characters=" + characters +
                    '}';
        }
    }
}
