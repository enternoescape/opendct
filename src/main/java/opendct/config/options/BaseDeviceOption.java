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

import opendct.config.Config;

public abstract class BaseDeviceOption implements DeviceOption {
    private final DeviceOptionType optionType;
    private final boolean readonly;
    private final String description;
    private final String name;
    private final String property;
    private final String[] validValues;
    private final boolean allowEmpty;
    private final boolean isArray;

    private String values[] = new String[0];

    /**
     * Create an option.
     *
     * @param optionType  This is the type of array to be validated against.
     * @param readonly    If <i>true</i>, the provided values will never be changed.
     * @param name        This is the short name for this option for display.
     * @param property    This is the internal property value.
     * @param description This is a description of the option for display.
     * @param validValues If this is not an empty array, the provided values will be the only valid
     *                    values accepted when setting the value.
     */
    public BaseDeviceOption(DeviceOptionType optionType, boolean readonly, String name, String property, String description, String... validValues) {
        this.optionType = optionType;
        this.readonly = readonly;
        this.description = description;
        this.validValues = validValues;
        this.name = name;
        this.property = property;
        this.allowEmpty = false;
        this.isArray = false;
    }

    /**
     * Create an option array.
     *
     * @param optionType  This is the type of array to be validated against.
     * @param allowEmpty  If <i>true</i>, this will allow an empty array to be submitted as a value.
     * @param readonly    If <i>true</i>, the provided values will never be changed.
     * @param name        This is the short name for this option for display.
     * @param property    This is the internal property value.
     * @param description This is a description of the option for display.
     * @param validValues If this is not an empty array, the provided values will be the only valid
     *                    values accepted when setting the value.
     */
    public BaseDeviceOption(DeviceOptionType optionType, boolean allowEmpty, boolean readonly, String name, String property, String description, String... validValues) {
        this.optionType = optionType;
        this.readonly = readonly;
        this.description = description;
        this.validValues = validValues;
        this.name = name;
        this.property = property;
        this.allowEmpty = allowEmpty;
        this.isArray = true;
    }

    public void setValue(String... newValues) throws DeviceOptionException {
        if (readonly) {
            throw new DeviceOptionException("The value cannot be set because this is a read-only option.", this);
        }

        setInitValue(newValues);
    }

    /**
     * Validates the provided value or values, then sets the new value or values.
     *
     * @param newValues This is the value or values to be validated, they are only set if they pass
     *                  validation.
     * @throws DeviceOptionException Thrown if any of the any of the provided values do not pass
     *                               validation.
     */
    protected void setInitValue(String... newValues) throws DeviceOptionException {
        if (newValues.length > 1 && !isArray) {
            throw new DeviceOptionException("The value cannot be set because an array was provided and this is not an array based option.", this);
        } else if (newValues.length == 0 && !isArray) {
            throw new DeviceOptionException("The value cannot be set because an empty array was provided and this is not an array based option.", this);
        } else if (newValues.length == 0 && !allowEmpty) {
            throw new DeviceOptionException("The value cannot be set because an empty array was provided and this option will not accept an empty array.", this);
        }

        if (validValues.length > 0 && newValues.length > 0) {
            // This way we can gather all of the incorrect settings instead of the just showing one
            // at a time.
            StringBuilder stringBuilder = null;

            for (String newValue : newValues) {
                boolean valid = false;

                switch (optionType) {
                    case BOOLEAN:
                        if (!newValue.equalsIgnoreCase("true") && !newValue.equalsIgnoreCase("false")) {
                            if (stringBuilder == null) {
                                stringBuilder = new StringBuilder("The value '" + newValue + "' is not equal to 'true' or 'false'.");
                            } else {
                                stringBuilder.append(Config.NEW_LINE).append("The value '").append(newValue).append("' is not equal to 'true' or 'false'.");
                            }
                        }
                        break;
                    case INTEGER:
                        try {
                            Integer.parseInt(newValue);
                        } catch (Exception e) {
                            if (stringBuilder == null) {
                                stringBuilder = new StringBuilder("The value '" + newValue + "' is not an integer number.");
                            } else {
                                stringBuilder.append(Config.NEW_LINE).append("The value '").append(newValue).append("' is not an integer number.");
                            }
                        }
                        break;
                    case LONG:
                        try {
                            Long.parseLong(newValue);
                        } catch (Exception e) {
                            if (stringBuilder == null) {
                                stringBuilder = new StringBuilder("The value '" + newValue + "' is not a long number.");
                            } else {
                                stringBuilder.append(Config.NEW_LINE).append("The value '").append(newValue).append("' is not a long number.");
                            }
                        }
                        break;
                    case FLOAT:
                        try {
                            Float.parseFloat(newValue);
                        } catch (Exception e) {
                            if (stringBuilder == null) {
                                stringBuilder = new StringBuilder("The value '" + newValue + "' is not a float number.");
                            } else {
                                stringBuilder.append(Config.NEW_LINE).append("The value '").append(newValue).append("' is not a float number.");
                            }
                        }
                        break;
                    case DOUBLE:
                        try {
                            Double.parseDouble(newValue);
                        } catch (Exception e) {
                            if (stringBuilder == null) {
                                stringBuilder = new StringBuilder("The value '" + newValue + "' is not a double number.");
                            } else {
                                stringBuilder.append(Config.NEW_LINE).append("The value '").append(newValue).append("' is not a double number.");
                            }
                        }
                        break;
                }

                for (String validValue : validValues) {
                    if (newValue.equals(validValue)) {
                        valid = true;
                        break;
                    }
                }

                if (!valid) {
                    if (stringBuilder == null) {
                        stringBuilder = new StringBuilder("The value '" + newValue + "' is not a valid option.");
                    } else {
                        stringBuilder.append(Config.NEW_LINE).append("The value '").append(newValue).append("' is not a valid option.");
                    }
                }
            }

            if (stringBuilder != null) {
                throw new DeviceOptionException(stringBuilder.toString(), this);
            }
        }

        this.values = newValues;

    }

    public String getProperty() {
        return property;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return values.length > 0 ? values[0] : null;
    }

    public String[] getArrayValue() {
        return values;
    }

    public String[] getValidValues() {
        return validValues;
    }

    public boolean isReadOnly() {
        return readonly;
    }

    public boolean isArray() {
        return isArray;
    }

    public DeviceOptionType getType() {
        return optionType;
    }
}
