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

package opendct.tuning.upnp.services.tuner.returns;

import opendct.tuning.upnp.services.shared.ActionProcessing;
import org.fourthline.cling.model.action.ActionArgumentValue;

import java.util.Map;

public class SetTunerParameters {
    private final String currentFrequency;
    private final String currentModulation;
    private final Boolean pcrLockStatus;

    public SetTunerParameters(String currentFrequency, String currentModulation, Boolean pcrLockStatus) {
        this.currentFrequency = currentFrequency;
        this.currentModulation = currentModulation;
        this.pcrLockStatus = pcrLockStatus;
    }

    public SetTunerParameters(Map<String, ActionArgumentValue> runAction) {
        currentFrequency = ActionProcessing.getActionVariableValue(runAction, "CurrentFrequency");
        currentModulation = ActionProcessing.getActionVariableValue(runAction, "CurrentModulation");
        pcrLockStatus = ActionProcessing.getReturnedActionValue(runAction, "PCRLockStatus", "1", false);
    }

    public String getCurrentFrequency() {
        return currentFrequency;
    }

    public String getCurrentModulation() {
        return currentModulation;
    }

    public Boolean getPCRLockStatus() {
        return pcrLockStatus;
    }
}
