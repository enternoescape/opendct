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

public class JsonChannel {
    private Boolean tunable = null;
    private Boolean ignore = null;
    private String cci;
    private Integer signalStrength = null;
    private String channelRemap;
    private String channel;
    private String name;
    private String url;
    private String modulation;
    private Integer frequency = null;
    private Integer program = null;

    /**
     * Get if this channel is tunable.
     *
     * @return <i>true</i> if this channel is tunable.
     */
    public Boolean isTunable() {
        return tunable;
    }

    /**
     * Set if this channel is tunable.
     *
     * @param tunable <i>true</i> if this channel is tunable.
     */
    public void setTunable(Boolean tunable) {
        this.tunable = tunable;
    }

    /**
     * Get if this channel is to be ignored in offline channel scans.
     *
     * @return <i>true</i> if this channel is to be ignored in offline channel scans.
     */
    public Boolean isIgnore() {
        return ignore;
    }

    /**
     * Set if this channel is to be ignored in offline channel scans.
     *
     * @param ignore <i>true</i> if this channel is to be ignored in offline channel scans.
     */
    public void setIgnore(Boolean ignore) {
        this.ignore = ignore;
    }

    public String getCci() {
        return cci;
    }

    public void setCci(String cci) {
        this.cci = cci;
    }

    public Integer getSignalStrength() {
        return signalStrength;
    }

    public void setSignalStrength(Integer signalStrength) {
        this.signalStrength = signalStrength;
    }

    public String getChannelRemap() {
        return channelRemap;
    }

    public void setChannelRemap(String channelRemap) {
        this.channelRemap = channelRemap;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getModulation() {
        return modulation;
    }

    public void setModulation(String modulation) {
        this.modulation = modulation;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }

    public Integer getProgram() {
        return program;
    }

    public void setProgram(Integer program) {
        this.program = program;
    }
}
