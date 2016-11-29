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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PojoUtil {

    /**
     * Returns a SageTVDeviceCrossbar enum that corresponds with the provided POJO.
     *
     * @param jsonSageTVCrossbar A populated JsonSageTVCrossbar instance.
     * @return The resolved crossbar enum or UNKNOWN if one could not be resolved.
     */
    /*public static SageTVDeviceCrossbar resolveEnum(JsonSageTVCrossbar jsonSageTVCrossbar) {
        try {
            return SageTVDeviceCrossbar.valueOf(jsonSageTVCrossbar.getName());
        } catch (IllegalArgumentException e) {
            int index = jsonSageTVCrossbar.getIndex();

            for (SageTVDeviceCrossbar crossbar : SageTVDeviceCrossbar.values()) {
                if (index == crossbar.INDEX) {
                    return crossbar;
                }
            }

            return SageTVDeviceCrossbar.UNKNOWN;
        }
    }*/

    /**
     * Create a JSON element to be posted to set an option that accepts an array.
     *
     * @param jsonOption The option to set.
     * @param values The values to be set for this option.
     * @return A JSON element ready to be sent.
     */
    public static JsonElement setArrayOption(JsonOption jsonOption, String... values) {
        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("property", jsonOption.getProperty());

        JsonArray jsonArray = new JsonArray();

        for (String value : values) {
            jsonArray.add(value);
        }

        jsonObject.add("values", jsonArray);

        return jsonObject;
    }

    /**
     * Create a JSON element to be posted to set an option that accepts a single value.
     *
     * @param jsonOption The option to set.
     * @param value The value to be set for this option.
     * @return A JSON element ready to be sent.
     */
    public static JsonElement setOption(JsonOption jsonOption, String value) {
        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("property", jsonOption.getProperty());
        jsonObject.addProperty("value", value);

        return jsonObject;
    }

    /**
     * Create a JSON element to be posted to set an option.
     * <p/>
     * This is used to set the value for more general properties such as changing the pool that a
     * capture device is in. Be sure to use the name of the object exactly as it is received from
     * the server. For example, poolName would be the property to change the pool name.
     * <p/>
     * These values when posted on multiple devices will apply to all of them.
     *
     * @param property The property to set.
     * @param value The value to be set for this option.
     * @return A JSON element ready to be sent.
     */
    public static JsonElement setOption(String property, String value) {
        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("property", property);
        jsonObject.addProperty("value", value);

        return jsonObject;
    }
}
