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

import opendct.util.Util;

public class BooleanDeviceOption extends BaseDeviceOption {

    public boolean values[];

    public BooleanDeviceOption(boolean value, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.BOOLEAN, readonly, name, property, description, validValues);
        super.setInitValue(String.valueOf(value));
        values = new boolean[]{value};
    }

    public BooleanDeviceOption(boolean[] values, boolean allowEmpty, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.BOOLEAN, allowEmpty, readonly, name, property, description, validValues);
        super.setInitValue(Util.arrayToStringArray(values));
        this.values = values;
    }

    public BooleanDeviceOption(String value, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.BOOLEAN, readonly, name, property, description, validValues);
        setInitValue(value);
    }

    public BooleanDeviceOption(String[] values, boolean allowEmpty, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.BOOLEAN, allowEmpty, readonly, name, property, description, validValues);
        setInitValue(values);
    }

    protected void setInitValue(String... newValues) throws DeviceOptionException {
        super.setInitValue(newValues);

        values = new boolean[newValues.length];

        for (int i = 0; i < newValues.length; i++) {
            values[i] = Boolean.valueOf(newValues[i]);
        }
    }

    @Override
    public void setValue(String... newValues) throws DeviceOptionException {
        super.setValue(newValues);

        values = new boolean[newValues.length];

        for (int i = 0; i < newValues.length; i++) {
            values[i] = Boolean.valueOf(newValues[i]);
        }
    }

    public boolean[] getBooleanArray() {
        return values;
    }

    public boolean getBoolean() {
        return values.length > 0 && values[0];
    }
}
