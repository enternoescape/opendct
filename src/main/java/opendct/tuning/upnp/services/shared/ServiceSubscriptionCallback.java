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

package opendct.tuning.upnp.services.shared;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.UnsupportedDataException;
import org.fourthline.cling.model.gena.CancelReason;
import org.fourthline.cling.model.gena.GENASubscription;
import org.fourthline.cling.model.gena.RemoteGENASubscription;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;

public class ServiceSubscriptionCallback extends SubscriptionCallback {
    private final Logger logger = LogManager.getLogger(ServiceSubscriptionCallback.class);

    protected ServiceSubscriptionCallback(Service service, int requestedDurationSeconds) {
        super(service, requestedDurationSeconds);
    }

    @Override
    public void established(GENASubscription sub) {
        logger.entry();
        logger.debug("Subscription established: {}", sub.getSubscriptionId());
        logger.exit();
    }

    @Override
    protected void failed(GENASubscription subscription, UpnpResponse responseStatus, Exception exception, String defaultMsg) {
        logger.entry();
        logger.debug("Subscription failed => {}", defaultMsg);
        logger.exit();
    }

    @Override
    public void ended(GENASubscription sub, CancelReason reason, UpnpResponse response) {
        logger.entry();

        if (reason != null) {
            logger.debug("Subscription ended => {}", reason);
        } else {
            logger.debug("Subscription ended normally.");
        }
        logger.exit();
    }

    @Override
    public void eventReceived(GENASubscription sub) {
        //You need to override this subroutine to do anything useful with this class.
        logger.entry();
        logger.debug("Subscription event received => {}", sub.getCurrentSequence().getValue());
        logger.exit();
    }

    @Override
    public void eventsMissed(GENASubscription sub, int numberOfMissedEvents) {
        logger.entry();
        logger.debug("{} subscription event(s) missed.", numberOfMissedEvents);
        logger.exit();
    }

    @Override
    protected void invalidMessage(RemoteGENASubscription sub, UnsupportedDataException ex) {
        logger.entry();
        logger.error("Received invalid message => {}", ex.getMessage());
        logger.exit();
    }
}
