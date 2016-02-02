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
    protected double values[];
    protected double minValue;
    protected double maxValue;

    /**
     * Create a new device option for the type <i>double</i>.
     *
     * @param value This is the initial value for this option.
     * @param readonly If this is <i>true</i>, changes to this option will not be allowed.
     * @param name This the display name for this option.
     * @param property This refers to the actual property this option is persisted on.
     * @param description This is a description of what this option is.
     * @param minValue This is the minimum value accepted. <i>value</i> will automatically be
     *                 adjusted to be equal to or greater than this value.
     * @param maxValue This is the maximum value accepted. <i>value</i> will automatically be
     *                 adjusted to be equal to or greater than this value.
     * @throws DeviceOptionException Thrown if the provided <i>value</i> doesn't meet the specified
     *                               requirements for this option.
     */
    public DoubleDeviceOption(double value, boolean readonly, String name, String property, String description, double minValue, double maxValue) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, readonly, name, property, description);
        super.setValue(String.valueOf(value));

        this.minValue = minValue;
        this.maxValue = maxValue;
        values = new double[]{value};
    }

    /**
     * Create a new device option for the type <i>double</i>.
     *
     * @param values These are the initial values for this option.
     * @param allowEmpty <i>true</i> if the array is allowed to be empty.
     * @param readonly If this is <i>true</i>, changes to this option will not be allowed.
     * @param name This the display name for this option.
     * @param property This refers to the actual property this option is persisted on.
     * @param description This is a description of what this option is.
     * @param minValue This is the minimum value accepted. <i>value</i> will automatically be
     *                 adjusted to be equal to or greater than this value.
     * @param maxValue This is the maximum value accepted. <i>value</i> will automatically be
     *                 adjusted to be equal to or greater than this value.
     * @throws DeviceOptionException Thrown if the provided <i>value</i> doesn't meet the specified
     *                               requirements for this option.
     */
    public DoubleDeviceOption(double[] values, boolean allowEmpty, boolean readonly, String name, String property, String description, double minValue, double maxValue) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, allowEmpty, readonly, name, property, description);
        super.setValue(Util.arrayToStringArray(values));

        this.minValue = minValue;
        this.maxValue = maxValue;
        this.values = values;
    }

    /**
     * Create a new device option for the type <i>double</i>.
     *
     * @param value This is the initial value for this option.
     * @param readonly If this is <i>true</i>, changes to this option will not be allowed.
     * @param name This the display name for this option.
     * @param property This refers to the actual property this option is persisted on.
     * @param description This is a description of what this option is.
     * @param validValues This option will only accept values in this array when it is not empty.
     * @throws DeviceOptionException Thrown if the provided <i>value</i> doesn't meet the specified
     *                               requirements for this option.
     */
    public DoubleDeviceOption(double value, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, readonly, name, property, description, validValues);

        this.minValue = Double.MIN_VALUE;
        this.maxValue = Double.MAX_VALUE;
        setValue(value);
    }

    /**
     * Create a new device option for the type <i>double</i>.
     *
     * @param values These are the initial values for this option.
     * @param allowEmpty <i>true</i> if the array is allowed to be empty.
     * @param readonly If this is <i>true</i>, changes to this option will not be allowed.
     * @param name This the display name for this option.
     * @param property This refers to the actual property this option is persisted on.
     * @param description This is a description of what this option is.
     * @param validValues This option will only accept values in this array when it is not empty.
     * @throws DeviceOptionException Thrown if the provided <i>value</i> doesn't meet the specified
     *                               requirements for this option.
     */
    public DoubleDeviceOption(double[] values, boolean allowEmpty, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, allowEmpty, readonly, name, property, description, validValues);

        this.minValue = Double.MIN_VALUE;
        this.maxValue = Double.MAX_VALUE;
        setValue(values);
    }

    public DoubleDeviceOption(String value, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, readonly, name, property, description, validValues);

        this.minValue = Double.MIN_VALUE;
        this.maxValue = Double.MAX_VALUE;
        setValue(value);
    }

    public DoubleDeviceOption(String[] values, boolean allowEmpty, boolean readonly, String name, String property, String description, String... validValues) throws DeviceOptionException {
        super(DeviceOptionType.DOUBLE, allowEmpty, readonly, name, property, description, validValues);

        this.minValue = Double.MIN_VALUE;
        this.maxValue = Double.MAX_VALUE;
        setValue(values);
    }

    @Override
    public void setValue(String... newValues) throws DeviceOptionException {
        double proposedValues[] = new double[newValues.length];

        for (int i = 0; i < newValues.length; i++) {
            proposedValues[i] = Math.min(maxValue, Math.max(minValue, Double.valueOf(newValues[i])));
        }

        super.setValue(Util.arrayToStringArray(proposedValues));

        values = proposedValues;
    }

    public void setValue(double... newValues) throws DeviceOptionException {
        double proposedValues[] = new double[newValues.length];

        for (int i = 0; i < newValues.length; i++) {
            proposedValues[i] = Math.min(maxValue, Math.max(minValue, newValues[i]));
        }

        super.setValue(Util.arrayToStringArray(proposedValues));

        values = proposedValues;
    }

    public double[] getDoubleArray() {
        return values;
    }

    public double getDouble() {
        return values.length > 0 ? values[0] : -1;
    }
}
