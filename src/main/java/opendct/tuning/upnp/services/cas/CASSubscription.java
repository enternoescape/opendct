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

package opendct.tuning.upnp.services.cas;

import opendct.tuning.upnp.services.shared.ServiceSubscription;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Service;

public class CASSubscription extends ServiceSubscription {
    private final Logger logger = LogManager.getLogger(CASSubscription.class);

    //CAS subscription returned variables.
    private boolean casCardStatus = false;
    private boolean casDescramblingStatus = false;
    private String casCardMessage = "";

    public CASSubscription(UpnpService upnpService, Service service) {
        super(upnpService, service);
    }

    public boolean getCASCardStatus() {
        casCardStatus = getReturnedEventValue("CardStatus", "Inserted", casCardStatus);
        return casCardStatus;
    }

    public boolean getCASDescramblingStatus() {
        casDescramblingStatus = getReturnedEventValue("DescramblingStatus", "Possible", casDescramblingStatus);
        return casDescramblingStatus;
    }

    public String getCASCardMessage() {
        casCardMessage = getReturnedEventValue("CardMessage", casCardMessage);
        return casCardMessage;
    }
}
