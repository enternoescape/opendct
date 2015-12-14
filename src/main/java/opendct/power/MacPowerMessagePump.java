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

package opendct.power;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MacPowerMessagePump implements PowerMessagePump {
    private final Logger logger = LogManager.getLogger(MacPowerMessagePump.this);

    public void addListener(PowerEventListener listener) {

    }

    public void removeListener(PowerEventListener listener) {

    }

    public boolean startPump() {
        logger.warn("System power state messages are currently not implemented for Mac.");
        return true;
    }

    public void stopPump() {
        logger.warn("System power state messages are currently not implemented for Mac.");
    }

    public void testSuspend() {

    }

    public void testResume() {

    }
}
