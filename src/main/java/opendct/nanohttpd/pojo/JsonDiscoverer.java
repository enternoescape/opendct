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

public class JsonDiscoverer {
    private String name;
    private String description;
    private String errorMessage;
    // This must be able to be null because if it is not populated, we should not assume the
    // discovery method is disabled.
    private Boolean enabled = null;
    private String running;
    private String supportedOS[];
    private JsonOption options[];

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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRunning() {
        return running;
    }

    public void setRunning(String running) {
        this.running = running;
    }

    public String[] getSupportedOS() {
        return supportedOS;
    }

    public void setSupportedOS(String supportedOS[]) {
        this.supportedOS = supportedOS;
    }

    public JsonOption[] getOptions() {
        return options;
    }

    public void setOptions(JsonOption[] options) {
        this.options = options;
    }
}
