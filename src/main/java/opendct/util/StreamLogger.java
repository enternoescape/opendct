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

public class StreamLogger implements Runnable {

    private final String prepend;
    private final InputStream stream;
    private final Logger logger;
    private final StringBuilder stringBuilder;

    public StreamLogger(String prepend, InputStream stream, Logger logger, StringBuilder stringBuilder) {
        this.prepend = prepend;
        this.stream = stream;
        this.logger = logger;
        this.stringBuilder = stringBuilder;
        stringBuilder.setLength(0);
    }

    @Override
    public void run() {
        int currentByte;

        try {
            while (true) {
                currentByte = stream.read();

                if (currentByte == -1) {
                    break;
                }

                if ((char) currentByte == '\n') {
                    if (stringBuilder.length() > 0) {
                        logger.debug("{}: {}", prepend, stringBuilder);
                        stringBuilder.setLength(0);
                    }
                } else if ((char) currentByte != '\r') {
                    stringBuilder.append((char) currentByte);
                }
            }
        } catch (IOException e) {
            logger.error("Stream logger terminated in an unexpected way => ", e);
        } finally {
            try {
                stream.close();
            } catch (Exception e) {}
        }
    }
}
