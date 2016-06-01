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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import opendct.config.options.DeviceOption;
import opendct.consumer.SageTVConsumer;

import java.lang.reflect.Type;

public class ConsumerSerializer implements JsonSerializer<SageTVConsumer> {
    private static final DeviceOptionSerializer deviceOptionSerializer = new DeviceOptionSerializer();

    public static final String NAME = "name";
    public static final String OPTIONS = "options";

    @Override
    public JsonElement serialize(SageTVConsumer src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject object = new JsonObject();

        object.addProperty("name", src.getClass().getCanonicalName());

        DeviceOption options[] = src.getOptions();

        object.add(OPTIONS, deviceOptionSerializer.serialize(options, DeviceOption.class, context));

        return object;
    }
}
