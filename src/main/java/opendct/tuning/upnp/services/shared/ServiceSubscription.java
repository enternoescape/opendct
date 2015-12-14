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
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.gena.GENASubscription;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.state.StateVariableValue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class ServiceSubscription {
    private final Logger logger = LogManager.getLogger(ServiceSubscription.class);
    private static final ReentrantReadWriteLock serviceSubscriptionLock = new ReentrantReadWriteLock();
    public volatile Map<String, StateVariableValue> returnedEventValues = null;
    private final String serviceName;
    private final UpnpService upnpService;
    private final Service service;
    private ServiceSubscriptionCallback subscriptionCallback;
    private CountDownLatch eventLatch = new CountDownLatch(1);

    public ServiceSubscription(UpnpService upnpService, Service service) {
        this.upnpService = upnpService;
        this.service = service;
        this.serviceName = service.getServiceType().getType();
    }

    public void start() {
        logger.entry();

        while (subscriptionCallback != null) {
            logger.debug("The '{}' subscription was not stopped before starting a new subscription.", serviceName);
            stop();
        }

        subscriptionCallback = new ServiceSubscriptionCallback(service, 1800) {

            @Override
            public void eventReceived(GENASubscription sub) {
                logger.entry();
                logger.debug("The '{}' subscription received an event => {}", serviceName, sub.getCurrentSequence().getValue());

                serviceSubscriptionLock.writeLock().lock();

                try {
                    logger.trace("Attempting to get values returned from event...");

                    Map<String, StateVariableValue> values = sub.getCurrentValues();

                    returnedEventValues = values;

                    eventLatch.countDown();

                    if (eventLatch.getCount() < 1) {
                        eventLatch = new CountDownLatch(1);
                    }
                } catch (Exception e) {
                    logger.error("Unable to get values returned from event => {}", e.getMessage());
                } finally {
                    serviceSubscriptionLock.writeLock().unlock();
                }

                logger.exit();
            }
        };

        logger.debug("Starting async subscription for the '{}' service...", serviceName);
        upnpService.getControlPoint().execute(subscriptionCallback);

        logger.exit();
    }

    public void stop() {
        logger.entry();

        // This needs to called outside of the lock to free up blocked read locks.
        eventLatch.countDown();

        serviceSubscriptionLock.writeLock().lock();

        try {
            logger.debug("Stopping async subscription for the '{}' service...", serviceName);
            if (subscriptionCallback != null) {
                subscriptionCallback.end();
                subscriptionCallback = null;
                logger.debug("Stopped async subscription for the '{}' service...", serviceName);
            } else {
                logger.debug("The '{}' service is not subscribed.", serviceName);
            }
        } catch (Exception e) {
            logger.debug("Unable to stop the async subscription for the '{}' service => {}", serviceName, e);
        } finally {
            serviceSubscriptionLock.writeLock().unlock();
        }


        logger.exit();
    }

    public boolean waitForStateVariable(String stateVariable, String expectedValue, int timeout) {
        logger.entry(stateVariable, expectedValue, timeout);

        boolean returnValue = false;

        long startTime = System.currentTimeMillis();

        String stringValue = null;

        while (true) {

            // Check if the current value is the expected value.
            stringValue = getStateVariableValue(stateVariable);

            if (stringValue != null) {
                if (expectedValue == null) {
                    returnValue = true;
                } else if (stringValue.equals(expectedValue)) {
                    returnValue = true;
                }
            }

            long currentTime = System.currentTimeMillis();

            if (returnValue) {
                logger.debug("'{}' now has the value '{}' after {}ms.", stateVariable, expectedValue, currentTime - startTime);
                break;
            } else if (currentTime - startTime > timeout) {
                logger.warn("Timeout occurred at {}ms while waiting for '{}' to have the value '{}'. The value is still '{}'.", currentTime - startTime, stateVariable, expectedValue, stateVariable);
                break;
            }

            // Wait for the next event. This is reset and cleared by the event.
            try {
                eventLatch.await(timeout - (currentTime - startTime), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.debug("Interrupted while waiting for '{}' to have the value '{}' => ", stateVariable, expectedValue, e);
                returnValue = false;
                break;
            }
        }

        return logger.exit(returnValue);
    }

    // Extracts the requested value and does all of the error handling. If if there is no value, a
    // value of null is returned
    public String getStateVariableValue(String value) {
        logger.entry();

        String returnValue = null;

        serviceSubscriptionLock.readLock().lock();

        try {
            logger.trace("Attempting to read the value of '{}'...", value);
            if (returnedEventValues == null) {
                logger.trace("'{}' does not have a value yet.", value, returnValue);
                return logger.exit(returnValue);
            }
            StateVariableValue status = returnedEventValues.get(value);
            returnValue = status.toString();

            logger.debug("'{}' value is now '{}'", value, returnValue);
            return logger.exit(status.toString());
        } catch (Exception e) {
            logger.error("Unable to read the value of '{}' => {}", value, e);
        } finally {
            serviceSubscriptionLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    // Gets the requested lookUpValue and if that value cannot be found, it will return the defaultValue.
    public String getReturnedEventValue(String lookUpValue, String defaultValue) {
        logger.entry(lookUpValue, defaultValue);

        String status;
        if ((status = getStateVariableValue(lookUpValue)) != null) {
            return logger.exit(status);
        }

        return logger.exit(defaultValue);
    }

    // Gets the requested lookUpValue and if it matches trueValue, it will return true. If lookUpValue
    // cannot be found, it will return the defaultValue.
    public Boolean getReturnedEventValue(String lookUpValue, String trueValue, Boolean defaultValue) {
        logger.entry(new Object[]{lookUpValue, trueValue, defaultValue});

        String status;
        if ((status = getStateVariableValue(lookUpValue)) != null) {
            return logger.exit((Boolean) (status.equals(trueValue)));
        }

        return logger.exit(defaultValue);
    }
}
