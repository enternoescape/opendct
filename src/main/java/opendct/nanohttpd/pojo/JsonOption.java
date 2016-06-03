/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.nanohttpd.pojo;

import opendct.config.options.DeviceOptionType;

public class JsonOption {
    private String property;
    private String name;
    private String description;
    private boolean readonly;
    private String type;
    private String validValues[];
    private String value;
    private String values[];

    public JsonOption() {

    }

    public JsonOption(String property, String name, String description, boolean readonly, DeviceOptionType type, String validValues[], String values[]) {
        this.property = property;
        this.name = name;
        this.description = description;
        this.readonly = readonly;
        this.type = type.toString();
        this.validValues = validValues;
        this.values = values;
    }

    public JsonOption(String property, String name, String description, boolean readonly, DeviceOptionType type, String validValues[], String value) {
        this.property = property;
        this.name = name;
        this.description = description;
        this.readonly = readonly;
        this.type = type.toString();
        this.validValues = validValues;
        this.value = value;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArray() {
        return value == null;
    }

    public boolean isReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String[] getValidValues() {
        return validValues;
    }

    public void setValidValues(String[] validValues) {
        this.validValues = validValues;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }
}
