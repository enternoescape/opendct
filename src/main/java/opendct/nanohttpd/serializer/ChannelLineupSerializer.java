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
import opendct.channel.ChannelSourceType;
import opendct.nanohttpd.pojo.JsonChannelLineup;
import opendct.util.Util;

import java.lang.reflect.Type;

public class ChannelLineupSerializer implements JsonSerializer<ChannelLineup> {
    public static final String LINEUP_NAME = "lineupName";
    public static final String SOURCE = "source";
    public static final String FRIENDLY_NAME = "friendlyName";
    public static final String ADDRESS = "address";
    public static final String UPDATE_INTERVAL = "updateInterval";
    public static final String NEXT_UPDATE = "nextUpdate";
    public static final String OFFLINE_UPDATE_INTERVAL = "offlineUpdateInterval";
    public static final String NEXT_OFFLINE_UPDATE = "nextOfflineUpdate";
    public static final String CHANNELS = "channels";

    public static final ChannelSerializer TV_CHANNEL_SERIALIZER = new ChannelSerializer();

    @Override
    public JsonElement serialize(ChannelLineup src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject newObject = new JsonObject();

        newObject.addProperty(LINEUP_NAME, src.LINEUP_NAME);
        newObject.addProperty(SOURCE, src.SOURCE.toString());
        newObject.addProperty(FRIENDLY_NAME, src.getFriendlyName());
        newObject.addProperty(ADDRESS, src.getAddress());
        newObject.addProperty(UPDATE_INTERVAL, src.getUpdateInterval());
        newObject.addProperty(NEXT_UPDATE, src.getNextUpdate());
        newObject.addProperty(OFFLINE_UPDATE_INTERVAL, src.getOfflineUpdateInterval());
        newObject.addProperty(NEXT_OFFLINE_UPDATE, src.getNextOfflineUpdate());

        return newObject;
    }

    public static void addProperty(JsonObject object, String property, ChannelLineup lineup) {
        switch (property) {
            case LINEUP_NAME:
                object.addProperty(LINEUP_NAME, lineup.LINEUP_NAME);
                break;
            case SOURCE:
                object.addProperty(SOURCE, lineup.SOURCE.toString());
                break;
            case FRIENDLY_NAME:
                object.addProperty(FRIENDLY_NAME, lineup.getFriendlyName());
                break;
            case ADDRESS:
                object.addProperty(ADDRESS, lineup.getAddress());
                break;
            case UPDATE_INTERVAL:
                object.addProperty(UPDATE_INTERVAL, lineup.getUpdateInterval());
                break;
            case NEXT_UPDATE:
                object.addProperty(NEXT_UPDATE, lineup.getNextUpdate());
                break;
            case OFFLINE_UPDATE_INTERVAL:
                object.addProperty(OFFLINE_UPDATE_INTERVAL, lineup.getOfflineUpdateInterval());
                break;
            case NEXT_OFFLINE_UPDATE:
                object.addProperty(NEXT_OFFLINE_UPDATE, lineup.getNextOfflineUpdate());
                break;
        }
    }

    public static void setProperties(JsonChannelLineup object, ChannelLineup lineup) {

        if (object.getFriendlyName() != null) {
            lineup.setFriendlyName(object.getFriendlyName());
        }

        if (object.getAddress() != null) {
            lineup.setAddress(object.getAddress());
        }

        if (object.getUpdateInterval() != null) {
            lineup.setUpdateInterval(object.getUpdateInterval());
        }

        if (object.getNextUpdate() != null) {
            lineup.setNextUpdate(object.getNextUpdate());
        }

        if (object.getOfflineUpdateInterval() != null) {
            lineup.setUpdateInterval(object.getOfflineUpdateInterval());
        }

        if (object.getNextOfflineUpdate() != null) {
            lineup.setNextOfflineUpdate(object.getNextOfflineUpdate());
        }
    }

    public static ChannelLineup createLineup(JsonChannelLineup object) throws IllegalArgumentException, NullPointerException {
        if (Util.isNullOrEmpty(object.getLineupName())) {
            throw new IllegalArgumentException("the lineup ID must exist.");
        }

        if (Util.isNullOrEmpty(object.getFriendlyName())) {
            object.setFriendlyName(object.getLineupName());
        }

        if (object.getUpdateInterval() == null) {
            object.setUpdateInterval(ChannelLineup.DEFAULT_UPDATE_INTERVAL);
        }

        if (object.getOfflineUpdateInterval() == null) {
            object.setOfflineUpdateInterval(ChannelLineup.DEFAULT_OFFLINE_UPDATE_INTERVAL);
        }

        ChannelSourceType sourceType;
        try {
            sourceType = ChannelSourceType.valueOf(object.getSource().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + object.getSource() + "' is not a valid source.");
        }

        return new ChannelLineup(object.getLineupName(), object.getFriendlyName(), sourceType, object.getAddress(), object.getUpdateInterval(), object.getOfflineUpdateInterval());
    }
}
