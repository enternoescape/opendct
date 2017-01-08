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
 */

package opendct.util;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StreamLogger implements Runnable {

    private final String prepend;
    private final InputStreamReader reader;
    private final Logger logger;
    private final StringBuilder stringBuilder;

    public StreamLogger(String prepend, InputStream stream, Logger logger, StringBuilder stringBuilder) {
        this.prepend = prepend;
        this.reader = new InputStreamReader(stream);
        this.logger = logger;
        this.stringBuilder = stringBuilder;
        stringBuilder.setLength(0);
    }

    @Override
    public void run() {
        char buffer[] = new char[80];
        int bufferIndex;
        int bytesRead;

        try {
            while (true) {
                bytesRead = reader.read(buffer, 0, buffer.length);

                if (bytesRead == -1) {
                    if (stringBuilder.length() > 0) {
                        logger.debug("{}: {}", prepend, stringBuilder);
                        stringBuilder.setLength(0);
                    }
                    break;
                }

                bufferIndex = 0;
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == '\n' || buffer[i] == '\r') {
                        if (bufferIndex != i) {
                            stringBuilder.append(buffer, bufferIndex, i - bufferIndex);
                        }
                        bufferIndex = i + 1;
                        if (stringBuilder.length() > 0) {
                            logger.debug("{}: {}", prepend, stringBuilder);
                            stringBuilder.setLength(0);
                        }
                    }
                }
                if (bytesRead - bufferIndex > 0) {
                    stringBuilder.append(buffer, bufferIndex, bytesRead - bufferIndex);
                    // Prevent the buffer from getting enormous.
                    if (stringBuilder.length() > 524288) {
                        logger.debug("{}: {}", prepend, stringBuilder);
                        stringBuilder.setLength(0);
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Stream logger terminated => {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Stream logger terminated in an unexpected way => ", e);
        } finally {
            try {
                reader.close();
            } catch (Exception e) {}
        }
    }
}
