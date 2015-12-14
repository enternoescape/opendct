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
import org.fourthline.cling.controlpoint.ActionCallback;
import org.fourthline.cling.model.action.ActionArgumentValue;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.InvalidValueException;

import java.util.Map;

public class ServiceActions {
    private final Logger logger = LogManager.getLogger(ServiceActions.class);
    private UpnpService upnpService;
    private Service actionService;

    public ServiceActions(UpnpService upnpService, Service actionService) {
        this.upnpService = upnpService;
        this.actionService = actionService;
    }

    // This method is used to query individual action variables available in the service.
    public String queryActionVariable(String varName) throws InvalidValueException {
        logger.entry();
        logger.debug("Using the '{}' service to QueryStateVariable for the varName '{}'.", new Object[]{actionService.getServiceType().getType(), varName});
        Action queryStateVariable = actionService.getQueryStateVariableAction();

        ActionInvocation queryStateVariableInvocation = new ActionInvocation(queryStateVariable);
        queryStateVariableInvocation.setInput("varName", varName); // Can throw InvalidValueException
        new ActionCallback.Default(queryStateVariableInvocation, upnpService.getControlPoint()).run();
        if (queryStateVariableInvocation.getFailure() != null) {
            logger.error("Unable to QueryStateVariable for varName '{}' => {}", varName, queryStateVariableInvocation.getFailure().getMessage());
            return logger.exit(null);
        }
        /*try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            logger.trace("QueryStateVariable for the varName '{}' was interrupted => {}", varName, e);
        }*/

        logger.trace("Attempting to read varName value...");
        ActionArgumentValue varNameValue = queryStateVariableInvocation.getOutput("return");
        logger.debug("varName status is '{}'", varNameValue.toString());

        return logger.exit(varNameValue.toString());
    }

    // This method is used to execute actions with parameters and to return a map of the results.
    // Actions will return an empty map if the action doesn't return values.
    public ActionInvocation runActionCustom(String actionName, ActionParameterPair... actionParameters) {
        logger.entry(actionService, actionName, actionParameters);
        logger.debug("Using the '{}' service to run the action '{}'.", actionService.getServiceType().getType(), actionName);

        Action runAction = actionService.getAction(actionName);
        ActionInvocation runActionInvocation = new ActionInvocation(runAction);
        for (ActionParameterPair actionParameter : actionParameters) {
            try {
                logger.debug("Setting the parameter '{}' to '{}'.", actionParameter.getInput(), actionParameter.getParameter());
                runActionInvocation.setInput(actionParameter.getInput(), actionParameter.getParameter());
            } catch (InvalidValueException e) {
                logger.error("The parameter '{}' cannot be set to '{}' => {}", actionParameter.getInput(), actionParameter.getParameter(), e);
                return logger.exit(null);
            }
        }

        return logger.exit(runActionInvocation);
    }

    public Map<String, ActionArgumentValue> runAction(String actionName, ActionParameterPair... actionParameters) {
        ActionInvocation runActionInvocation = runActionCustom(actionName, actionParameters);

        // This can be an async call, but it is pointless considering how quickly this will return.
        new ActionCallback.Default(runActionInvocation, upnpService.getControlPoint()).run();
        if (runActionInvocation.getFailure() != null) {
            logger.error("Failed to run the action '{}' => {}", actionName, runActionInvocation.getFailure().getMessage());

            return logger.exit(null);
        }
        /*try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            logger.trace("The action '{}' was interrupted => {}", actionName, e);
        }*/

        logger.debug("Successfully ran the action '{}'.", actionName);

        return logger.exit((Map<String, ActionArgumentValue>) runActionInvocation.getOutputMap());
    }
}
