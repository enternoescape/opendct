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

package opendct.nanohttpd.serializer;

import com.google.gson.*;
import opendct.config.options.DeviceOption;

import java.lang.reflect.Type;

public class DeviceOptionSerializer  implements JsonSerializer<DeviceOption[]> {

    @Override
    public JsonElement serialize(DeviceOption[] src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray jsonObject = new JsonArray();

        for (DeviceOption option : src) {
            JsonObject newObject = new JsonObject();
            newObject.addProperty("property", option.getProperty());
            newObject.addProperty("name", option.getName());
            newObject.addProperty("description", option.getDescription());
            newObject.addProperty("readonly", option.isReadOnly());
            newObject.addProperty("type", option.getType().toString());

            JsonArray newArray = new JsonArray();

            for (String value : option.getValidValues()) {
                newArray.add(value);
            }

            newObject.add("validValues", newArray);

            if (option.isArray()) {
                newArray = new JsonArray();

                for (String value : option.getArrayValue()) {
                    newArray.add(value);
                }

                newObject.add("values", newArray);
            } else {
                newObject.addProperty("value", option.getValue());
            }

            jsonObject.add(newObject);
        }

        return jsonObject;
    }
}
