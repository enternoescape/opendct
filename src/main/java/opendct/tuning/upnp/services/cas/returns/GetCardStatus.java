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

package opendct.tuning.upnp.services.cas.returns;

import opendct.tuning.upnp.services.shared.ActionProcessing;
import org.fourthline.cling.model.action.ActionArgumentValue;

import java.util.Map;

public class GetCardStatus {
    private final String currentCardStatus;
    private final String currentCardManufacturer;
    private final String currentCardVersion;
    private final boolean currentDaylightSaving;
    private final String currentEALocationCode;
    private final String currentRatingRegion;
    private final String currentTimeZone;

    public GetCardStatus(String currentCardStatus,
                         String currentCardManufacturer,
                         String currentCardVersion,
                         boolean currentDaylightSaving,
                         String currentEALocationCode,
                         String currentRatingRegion,
                         String currentTimeZone) {
        this.currentCardStatus = currentCardStatus;
        this.currentCardManufacturer = currentCardManufacturer;
        this.currentCardVersion = currentCardVersion;
        this.currentDaylightSaving = currentDaylightSaving;
        this.currentEALocationCode = currentEALocationCode;
        this.currentRatingRegion = currentRatingRegion;
        this.currentTimeZone = currentTimeZone;
    }

    public GetCardStatus(Map<String, ActionArgumentValue> runAction) {
        currentCardStatus = ActionProcessing.getReturnedActionValue(runAction, "CurrentCardStatus", "Removed");
        currentCardManufacturer = ActionProcessing.getReturnedActionValue(runAction, "CurrentCardManufacturer", "");
        currentCardVersion = ActionProcessing.getReturnedActionValue(runAction, "CurrentCardVersion", "");
        currentDaylightSaving = ActionProcessing.getReturnedActionValue(runAction, "CurrentDaylightSaving", "1", false);
        currentEALocationCode = ActionProcessing.getReturnedActionValue(runAction, "CurrentEALocationCode", "");
        currentRatingRegion = ActionProcessing.getReturnedActionValue(runAction, "CurrentRatingRegion", "");
        currentTimeZone = ActionProcessing.getReturnedActionValue(runAction, "CurrentTimeZone", "");
    }

    public String getCurrentCardStatus() {
        return currentCardStatus;
    }

    public String getCurrentCardManufacturer() {
        return currentCardManufacturer;
    }

    public String getCurrentCardVersion() {
        return currentCardVersion;
    }

    public boolean isCurrentDaylightSaving() {
        return currentDaylightSaving;
    }

    public String getCurrentEALocationCode() {
        return currentEALocationCode;
    }

    public String getCurrentRatingRegion() {
        return currentRatingRegion;
    }

    public String getCurrentTimeZone() {
        return currentTimeZone;
    }
}
