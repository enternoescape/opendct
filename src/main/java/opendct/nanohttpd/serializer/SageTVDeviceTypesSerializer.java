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
import opendct.sagetv.SageTVDeviceCrossbar;

import java.lang.reflect.Type;

public class SageTVDeviceTypesSerializer implements JsonSerializer<SageTVDeviceCrossbar[]> {

    @Override
    public JsonElement serialize(SageTVDeviceCrossbar src[], Type typeOfSrc, JsonSerializationContext context) {
        JsonArray jsonObject = new JsonArray();

        for (SageTVDeviceCrossbar option : src) {
            JsonObject newObject = new JsonObject();

            newObject.addProperty("index", option.INDEX);
            newObject.addProperty("name", option.NAME);

            jsonObject.add(newObject);
        }

        return jsonObject;
    }
}
