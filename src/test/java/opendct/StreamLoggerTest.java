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

package opendct;

import opendct.util.StreamLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class StreamLoggerTest {

    @Test(groups = { "streamLogger", "lineProcessing" })
    public void executeStreamLogger() throws IOException, InterruptedException {
        byte bytes[] = "Testing1\nTesting2\rTesting3\r\nTesting4\rTesting5\nTesting6\nTesting7\r\nTesting8\r\nTesting9".getBytes();
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        Logger logger = LogManager.getLogger(StreamLoggerTest.class);
        StringBuilder stringBuilder = new StringBuilder();
        StreamLogger streamLogger = new StreamLogger("test", stream, logger, stringBuilder);
        streamLogger.run();
        assert stream.available() == 0;
    }
}
