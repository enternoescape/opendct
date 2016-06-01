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
import opendct.channel.ChannelLineup;
import opendct.channel.CopyProtection;
import opendct.channel.TVChannel;
import opendct.channel.TVChannelImpl;
import opendct.nanohttpd.pojo.JsonChannel;
import opendct.nanohttpd.pojo.JsonException;
import opendct.util.Util;

import java.lang.reflect.Type;

public class ChannelSerializer implements JsonSerializer<TVChannelImpl> {
    public static final String TUNABLE = "tunable";
    public static final String IGNORE = "ignore";
    public static final String CCI = "cci";
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String CHANNEL_REMAP = "channelRemap";
    public static final String CHANNEL = "channel";
    public static final String NAME = "name";
    public static final String URL = "url";
    public static final String MODULATION = "modulation";
    public static final String FREQUENCY = "frequency";
    public static final String PROGRAM = "program";

    @Override
    public JsonElement serialize(TVChannelImpl src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject newObject = new JsonObject();

        newObject.addProperty(TUNABLE, src.isTunable());
        newObject.addProperty(IGNORE, src.isIgnore());
        newObject.addProperty(CCI, src.getCci().toString());
        newObject.addProperty(SIGNAL_STRENGTH, src.getSignalStrength());
        newObject.addProperty(CHANNEL_REMAP, src.getChannelRemap());
        newObject.addProperty(CHANNEL, src.getChannel());
        newObject.addProperty(NAME, src.getName());
        newObject.addProperty(URL, src.getUrl());
        newObject.addProperty(MODULATION, src.getModulation());
        newObject.addProperty(FREQUENCY, src.getFrequency());
        newObject.addProperty(PROGRAM, src.getProgram());

        return newObject;
    }

    public static void addProperty(JsonObject object, String property, TVChannel tvChannel) {
        switch (property) {
            case TUNABLE:
                object.addProperty(TUNABLE, tvChannel.isTunable());
                break;
            case IGNORE:
                object.addProperty(IGNORE, tvChannel.isIgnore());
                break;
            case CCI:
                object.addProperty(CCI, tvChannel.getCci().toString());
                break;
            case SIGNAL_STRENGTH:
                object.addProperty(SIGNAL_STRENGTH, tvChannel.getSignalStrength());
                break;
            case CHANNEL_REMAP:
                object.addProperty(CHANNEL_REMAP, tvChannel.getChannelRemap());
                break;
            case CHANNEL:
                object.addProperty(CHANNEL, tvChannel.getChannel());
                break;
            case NAME:
                object.addProperty(NAME, tvChannel.getName());
                break;
            case URL:
                object.addProperty(URL, tvChannel.getUrl());
                break;
            case MODULATION:
                object.addProperty(MODULATION, tvChannel.getModulation());
                break;
            case FREQUENCY:
                object.addProperty(FREQUENCY, tvChannel.getFrequency());
                break;
            case PROGRAM:
                object.addProperty(PROGRAM, tvChannel.getProgram());
                break;
        }
    }

    public static JsonException setProperty(JsonChannel channel, ChannelLineup lineup) {
        if (Util.isNullOrEmpty(channel.getChannel())) {
            return new JsonException(CHANNEL, "The channel to be updated is missing.");
        }

        TVChannel tvChannel = new TVChannelImpl(channel.getChannel(),
                channel.getName() != null ? channel.getName() : channel.getChannel());

        if (channel.isTunable() != null) {
            tvChannel.setTunable(channel.isTunable());
        }

        if (channel.isIgnore() != null) {
            tvChannel.setIgnore(channel.isIgnore());
        }

        if (channel.getCci() != null) {
            tvChannel.setCci(CopyProtection.valueOf(channel.getCci()));
        }

        if (channel.getSignalStrength() != null) {
            tvChannel.setSignalStrength(channel.getSignalStrength());
        }

        if (channel.getChannelRemap() != null) {
            tvChannel.setChannelRemap(channel.getChannelRemap());
        }

        if (channel.getUrl() != null) {
            tvChannel.setUrl(channel.getUrl());
        }

        if (channel.getModulation() != null) {
            tvChannel.setModulation(channel.getModulation());
        }

        if (channel.getFrequency() != null) {
            tvChannel.setFrequency(channel.getFrequency());
        }

        if (channel.getProgram() != null) {
            tvChannel.setProgram(channel.getProgram());
        }

        lineup.updateChannel(tvChannel);

        return null;
    }

    public static JsonException createChannel(JsonChannel channel, ChannelLineup lineup) {
        if (Util.isNullOrEmpty(channel.getChannel())) {
            return new JsonException(CHANNEL, "A new channel cannot be blank.");
        }

        return setProperty(channel, lineup);
    }
}
