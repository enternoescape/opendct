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

package opendct.config.options;

public class DeviceOptionException extends Exception {
    public final DeviceOption deviceOption;
    public final String property;

    public DeviceOptionException(DeviceOption option) {
        deviceOption = option;
        property = deviceOption.getProperty();
    }

    public DeviceOptionException(String message, DeviceOption option) {
        super(message);
        deviceOption = option;
        property = deviceOption.getProperty();
    }

    public DeviceOptionException(String message, String property) {
        super(message);
        deviceOption = null;
        this.property = property;
    }

    public DeviceOptionException(String message, Throwable cause, DeviceOption option) {
        super(message, cause);
        deviceOption = option;
        property = deviceOption.getProperty();
    }

    public DeviceOptionException(Throwable cause, DeviceOption option) {
        super(cause);
        deviceOption = option;
        property = deviceOption.getProperty();
    }

    public DeviceOptionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, DeviceOption option) {
        super(message, cause, enableSuppression, writableStackTrace);
        deviceOption = option;
        property = deviceOption.getProperty();
    }
}
