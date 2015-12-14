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

public class DoubleDeviceOption extends BaseDeviceOption {
    public double values[];

    public DoubleDeviceOption(double value, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, readonly, name, property, description, validValues);
        super.setValue(String.valueOf(value));
        values = new double[]{value};
    }

    public DoubleDeviceOption(double[] values, boolean allowEmpty, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, allowEmpty, readonly, name, property, description, validValues);
        super.setValue(Util.arrayToStringArray(values));
        this.values = values;
    }

    public DoubleDeviceOption(String value, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, readonly, name, property, description, validValues);
        setValue(value);
    }

    public DoubleDeviceOption(String[] values, boolean allowEmpty, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, allowEmpty, readonly, name, property, description, validValues);
        setValue(values);
    }

    @Override
    public void setValue(String... newValues) throws DeviceOptionException {
        super.setValue(newValues);

        values = new double[newValues.length];

        for (int i = 0; i < newValues.length; i++) {
            values[i] = Double.valueOf(newValues[i]);
        }
    }

    public double[] getDoubleArray() {
        return values;
    }

    public double getDouble() {
        return values.length > 0 ? values[0] : -1;
    }
}
