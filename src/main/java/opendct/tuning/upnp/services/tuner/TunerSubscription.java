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

package opendct.tuning.upnp.services.tuner;

import opendct.tuning.upnp.services.shared.ServiceSubscription;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Service;

public class TunerSubscription extends ServiceSubscription {
    private final Logger logger = LogManager.getLogger(TunerSubscription.class);

    //Tuner subscription returned variables.
    private Boolean tunerPCRLock = false;
    private Boolean tunerSeeking = false;

    public TunerSubscription(UpnpService upnpService, Service service) {
        super(upnpService, service);
    }

    public Boolean getTunerPCRLock() {
        tunerPCRLock = getReturnedEventValue("PCRLock", "1", tunerPCRLock);
        return tunerPCRLock;
    }

    public Boolean getTunerSeeking() {
        tunerSeeking = getReturnedEventValue("Seeking", "1", tunerSeeking);
        return tunerPCRLock;
    }

    public boolean waitForTunerPCRLock(boolean expectedState, int timeout) {
        logger.entry(expectedState, timeout);

        long startTime = System.currentTimeMillis();

        boolean returnValue = false;

        while (true) {
            if (getTunerPCRLock() == expectedState) {
                returnValue = true;
            }

            long currentTime = System.currentTimeMillis();

            if (returnValue) {
                break;
            } else if (currentTime - startTime > timeout) {
                logger.warn("Timeout occurred at {}ms while waiting for 'TransportState' to have the value '{}'.", currentTime - startTime, expectedState);
                break;
            }

            if (!waitForStateVariable("PCRLock", null, timeout)) {
                break;
            }
        }


        return logger.exit(returnValue);
    }
}
