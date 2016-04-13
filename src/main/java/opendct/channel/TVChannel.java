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

package opendct.channel;

public interface TVChannel {

    /**
     * This is the array to be written to the properties file.
     *
     * @return Returns the array to write to a properties file.
     */
    public String[] getProperties();

    public boolean isTunable();

    public void setTunable(boolean tunable);

    public String getChannelRemap();

    public void setChannelRemap(String channelRemap);

    public void setCci(CopyProtection cci);

    public CopyProtection getCci();

    public void setSignalStrength(int signalStrength);

    public int getSignalStrength();

    public void setFrequency(int frequency);

    public int getFrequency();

    public void setProgram(String program);

    public String getProgram();

    public String getChannel();

    public String getName();

    public void setModulation(String modulation);

    public String getModulation();

    public String getUrl();

    public void setUrl(String url);

    public boolean isIgnore();

    public void setIgnore(boolean ignore);

    public void setUpdateAll();

    public String[] getAndClearUpdates();
}
