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
import org.fourthline.cling.model.action.ActionArgumentValue;

import java.util.Map;

/**
 * Created by joeshutt on 9/21/2015.
 */
public class ActionProcessing {
    private static final Logger logger = LogManager.getLogger(ActionProcessing.class);

    //Extracts the desired value and does all of the error handling. If something goes wrong, a null value is returned.
    public static String getActionVariableValue(Map<String, ActionArgumentValue> values, String value) {
        logger.entry();

        try {
            logger.trace("Attempting to read '{}' value...", value);
            ActionArgumentValue status = values.get(value);

            logger.debug("'{}' value is now '{}'", new Object[]{value, status.toString()});
            return logger.exit(status.toString());
        } catch (Exception e) {
            logger.error("Unable to read '{}' value => {}", new Object[]{value, e.getMessage()});
        }

        return logger.exit(null);
    }

    //Gets the requested lookUpValue and if that value cannot be found, it will return the defaultValue.
    public static String getReturnedActionValue(Map<String, ActionArgumentValue> values, String lookUpValue, String defaultValue) {
        logger.entry(new Object[]{lookUpValue, defaultValue});
        String status;
        if ((status = getActionVariableValue(values, lookUpValue)) != null) {
            return logger.exit(status);
        }
        return logger.exit(defaultValue);
    }

    //Gets the requested lookUpValue and if it matches trueValue, it will return true. If lookUpValue cannot be found, it will return the defaultValue.
    public static Boolean getReturnedActionValue(Map<String, ActionArgumentValue> values, String lookUpValue, String trueValue, Boolean defaultValue) {
        logger.entry(new Object[]{lookUpValue, trueValue, defaultValue});
        String status;
        if ((status = getActionVariableValue(values, lookUpValue)) != null) {
            return logger.exit((Boolean) (status.equals(trueValue)));
        }
        return logger.exit(defaultValue);
    }
}
