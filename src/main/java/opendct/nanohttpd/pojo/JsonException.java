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

import opendct.config.options.DeviceOptionException;

public class JsonException {
    private boolean exception = true;
    private String object;
    private String message;

    public JsonException() {

    }

    public JsonException(String object, String message) {
        this.object = object;
        this.message = message;
    }

    public JsonException(DeviceOptionException e) {
        this.object = e.property;
        this.message = e.getMessage();
    }

    public String getObject() {
        return object;
    }

    public String getMessage() {
        return message;
    }
}
