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

package opendct.jetty;

public class JsonError {
    private int code;
    private String message;
    private String url;
    private String exception;

    public JsonError() {

    }

    /**
     * Create a new JSON error.
     *
     * @param code The code number associated with this error. This is most likely going to match
     *             the HTTP error code unless there's a reason to make it more specific. If this
     *             code is 0 or in the 2xx range, it will not be considered an error.
     * @param message A message describing what happened and possible a suggestion on how to fix it.
     * @param url The URL that the request came in on.
     * @param exception This is a technical description of the problem and probably a stack trace if
     *                  it makes sense.
     */
    public JsonError(int code, String message, String url, String exception) {
        this.code = code;
        this.message = message;
        this.url = url;
        this.exception = exception;
    }

    /**
     * Get the code number associated with this error.
     * <p/>
     * This is most likely going to match the HTTP error code unless there's a reason to make it
     * more specific.
     *
     * @return The error code.
     */
    public int getCode() {
        return code;
    }

    /**
     * Set the code number associated with this error.
     * <p/>
     * This is most likely going to match the HTTP error code unless there's a reason to make it
     * more specific.
     *
     * @param code
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * Get a message describing what happened and possible a suggestion on how to fix it.
     *
     * @return The message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set a message describing what happened and possible a suggestion on how to fix it.
     *
     * @param message The message.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Get the URL that the request came in on.
     *
     * @return The URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Get the URL that the request came in on.
     *
     * @param url The URL.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Get a technical description of the problem.
     * <p/>
     * It can also contain a stack trace if it makes sense. This is not supposed to be displayed in
     * the browser by default.
     *
     * @return The exception description.
     */
    public String getException() {
        return exception;
    }

    /**
     * Set a technical description of the problem.
     * <p/>
     * It can also contain a stack trace if it makes sense. This is not supposed to be displayed in
     * the browser by default.
     *
     * @param exception The exception description.
     */
    public void setException(String exception) {
        this.exception = exception;
    }
}
