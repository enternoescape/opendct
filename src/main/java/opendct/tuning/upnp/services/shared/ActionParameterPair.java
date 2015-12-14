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

/**
 * This class is used to get around needing to go through a lot of typing and object creation to
 * create a list that could be directly imported into an Action for the UPnP service. It's much
 * easier to pass it strings and have it throw an error. We just need to make sure the conversion is
 * in try catch brackets.
 */
public class ActionParameterPair {
    private final String inputName;
    private final String parameterName;

    public ActionParameterPair(String inputName, String parameterName) {
        this.inputName = inputName;
        this.parameterName = parameterName;
    }

    public String getInput() {
        return inputName;
    }

    public String getParameter() {
        return parameterName;
    }
}
