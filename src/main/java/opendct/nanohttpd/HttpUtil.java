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

package opendct.nanohttpd;

import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;
import opendct.config.options.DeviceOptionException;
import opendct.nanohttpd.pojo.JsonException;

import java.io.IOException;
import java.io.InputStream;

public class HttpUtil {
    private static final Gson gson = new Gson();

    public static String getPostContent(NanoHTTPD.IHTTPSession session) {
        InputStream inputStream = session.getInputStream();
        int contentLength;

        try {
            contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
        } catch (NumberFormatException e) {
            try {
                contentLength = inputStream.available();
            } catch (IOException e1) {
                return "";
            }
        }

        if (contentLength == 0) {
            return "";
        }

        int index = 0;
        byte response[] = new byte[contentLength];

        while (contentLength > index) {
            int readBytes = 0;
            try {
                readBytes = inputStream.read(response, index, contentLength - index);
            } catch (IOException e) {
                break;
            }
            index += readBytes;
        }

        return new String(response).trim();
    }

    public static NanoHTTPD.Response returnException(DeviceOptionException e) {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.FORBIDDEN, "application/json",
                gson.toJson(new JsonException(e)));
    }

    public static NanoHTTPD.Response returnException(JsonException e) {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.FORBIDDEN, "application/json",
                gson.toJson(e));
    }

    public static NanoHTTPD.Response returnException(String object, String message) {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.FORBIDDEN, "application/json",
                gson.toJson(new JsonException(object, message)));
    }
}
