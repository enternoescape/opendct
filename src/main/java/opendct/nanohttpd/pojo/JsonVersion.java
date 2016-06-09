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

public class JsonVersion {
    private String versionProgram;
    private Integer versionMajor;
    private Integer versionMinor;
    private Integer versionBuild;
    private Integer versionConfig;
    private String versionOS;
    private Boolean is64Bit;
    private String configDir;
    private String logDir;
    private Integer versionJson;

    public String getVersionProgram() {
        return versionProgram;
    }

    public void setVersionProgram(String versionProgram) {
        this.versionProgram = versionProgram;
    }

    public Integer getVersionMajor() {
        return versionMajor;
    }

    public void setVersionMajor(Integer versionMajor) {
        this.versionMajor = versionMajor;
    }

    public Integer getVersionMinor() {
        return versionMinor;
    }

    public void setVersionMinor(Integer versionMinor) {
        this.versionMinor = versionMinor;
    }

    public Integer getVersionBuild() {
        return versionBuild;
    }

    public void setVersionBuild(Integer versionBuild) {
        this.versionBuild = versionBuild;
    }

    public Integer getVersionConfig() {
        return versionConfig;
    }

    public void setVersionConfig(Integer versionConfig) {
        this.versionConfig = versionConfig;
    }

    public String getVersionOS() {
        return versionOS;
    }

    public void setVersionOS(String versionOS) {
        this.versionOS = versionOS;
    }

    public Boolean getIs64Bit() {
        return is64Bit;
    }

    public void setIs64Bit(Boolean is64Bit) {
        this.is64Bit = is64Bit;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public String getLogDir() {
        return logDir;
    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    public Integer getVersionJson() {
        return versionJson;
    }

    public void setVersionJson(Integer versionJson) {
        this.versionJson = versionJson;
    }
}
