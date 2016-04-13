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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelLineup {
    private static final Logger logger = LogManager.getLogger(ChannelLineup.class);
    final static public long DEFAULT_UPDATE_INTERVAL = 28800000; // 8 hours
    final static public long DEFAULT_OFFLINE_UPDATE_INTERVAL = 604800000; // 7 days

    final private Map<String, TVChannel> channelMap;
    final private Map<String, TVChannel> remapMap;
    final public String LINEUP_NAME;
    final public ChannelSourceType SOURCE;

    private String friendlyName;
    private String address;
    private URI addressURI;
    private URL addressURL;
    private InetAddress addressIP;

    private long updateInterval;
    private long nextUpdate;

    private long offlineUpdateInterval;
    private long nextOfflineUpdate;


    /**
     * Create a channel lineup object using the default update intervals.
     *
     * @param lineupName This is the name of the lineup that will be used for the filename to save
     *                   and re-load this lineup.
     * @param friendlyName This is the name of the lineup that is to be displayed when this lineup
     *                     is being referenced.
     * @param channelSourceType This is the source used to update the channel list.
     * @param address This is the address of the source. It can be a URL, URI, hostname, IP address
     *                or just a String that means something to the methods to access the source.
     */
    public ChannelLineup(String lineupName, String friendlyName, ChannelSourceType channelSourceType, String address) {
        this(lineupName, friendlyName, channelSourceType, address, DEFAULT_UPDATE_INTERVAL, DEFAULT_OFFLINE_UPDATE_INTERVAL);
    }

    /**
     * Create a channel lineup object.
     *
     * @param lineupName This is the name of the lineup that will be used for the filename to save
     *                   and re-load this lineup.
     * @param friendlyName This is the name of the lineup that is to be displayed when this lineup
     *                     is being referenced.
     * @param channelSourceType This is the source used to update the channel list.
     * @param address This is the address of the source. It can be a URL, URI, hostname, IP address
     *                or just a String that means something to the methods to access the source.
     * @param updateInterval The update interval in milliseconds.
     * @param offlineUpdateInterval The update interval in milliseconds.
     */
    public ChannelLineup(String lineupName, String friendlyName, ChannelSourceType channelSourceType, String address, long updateInterval, long offlineUpdateInterval) {
        SOURCE = channelSourceType;
        LINEUP_NAME = lineupName;
        this.friendlyName = friendlyName;

        this.updateInterval = updateInterval;
        this.offlineUpdateInterval = offlineUpdateInterval;

        // Set these to the current size so they run on the first opportunity.
        nextUpdate = System.currentTimeMillis();
        nextOfflineUpdate = System.currentTimeMillis();

        channelMap = new ConcurrentHashMap<String, TVChannel>();
        remapMap = new ConcurrentHashMap<String, TVChannel>();

        setAddress(address);
    }

    /**
     * Sets the address value of this channel lineup and attempts to parse the channel into objects
     * that may be used to access the available channel updates.
     * <p/>
     * The expected address value varies by channel update source.
     *
     * @param address This is the address value to be set.
     */
    public void setAddress(String address) {
        this.address = address;

        try {
            addressURI = new URI(address);
        } catch (Exception e) {
            addressURI = null;
        }

        try {
            addressURL = new URL(address);
        } catch (Exception e) {
            addressURL = null;
        }

        try {
            addressIP = InetAddress.getByName(address);
            ;
        } catch (Exception e) {
            addressURL = null;
        }

        if (addressIP == null && addressURI != null) {
            try {
                addressIP = InetAddress.getByName(addressURI.getHost());

                if (addressIP.isLoopbackAddress()) {
                    addressIP = null;
                }
            } catch (Exception e) {
                addressIP = null;
            }
        }

        if (addressIP == null && addressURL != null) {
            try {
                addressIP = InetAddress.getByName(addressURL.getHost());
            } catch (Exception e) {
                addressIP = null;
            }
        }
    }

    /**
     * Returns the string value of the address assigned to this lineup.
     *
     * @return The string value of the address assigned to this lineup. It will return an empty
     * string if there is no address.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Returns a URI object created by the address assigned to this lineup.
     *
     * @return A URI object or <i>null</i> if the address was not able to be converted.
     */
    public URI getAddressURI() {
        return addressURI;
    }

    /**
     * Returns a URL object created by the address assigned to this lineup.
     *
     * @return A URL object or <i>null</i> if the address was not able to be converted.
     */
    public URL getAddressURL() {
        return addressURL;
    }

    /**
     * Returns a Inet Address object created by the address assigned to this lineup.
     *
     * @return A InetAddress object or <i>null</i> if the address was not able to be converted.
     */
    public InetAddress getAddressIP() {
        return addressIP;
    }

    /**
     * Adds a channel and remapping if there is a remapping assigned to this channel.
     *
     * @param tvChannel This object must already have a channel assigned or you will not be able to
     *                  find it or you will generate an error if it is <i>null</i>.
     */
    public void addChannel(TVChannel tvChannel) {
        if (tvChannel == null || tvChannel.getChannel() == null) {
            return;
        }

        if (tvChannel.getChannelRemap() == null) {
            tvChannel.setChannelRemap("");
        }

        channelMap.put(tvChannel.getChannel(), tvChannel);

        if (!tvChannel.getChannelRemap().equals("")) {
            setRemap(tvChannel.getChannel(), tvChannel.getChannelRemap());
        } else {
            clearRemap(tvChannel.getChannel());
        }
    }

    /**
     * Updates a channel if it already exists with the assumption that the provided updated
     * information supersedes any former values.
     *
     * @param tvChannel The channel with updated values. <i>null</i> values will be skipped.
     * @return <i>true</i> if any updates actually happened.
     */
    public boolean updateChannel(TVChannel tvChannel) throws IllegalArgumentException {
        String newProperties[] = tvChannel.getAndClearUpdates();

        if (newProperties.length < 11) {
            throw new IllegalArgumentException("The provided array does not contain all parameters required to update a channel.");
        }

        TVChannel oldChannel = channelMap.get(tvChannel.getChannel());

        if (oldChannel == null) {
            addChannel(tvChannel);
            return true;
        }

        boolean updated = false;
        String oldProperties[] = oldChannel.getProperties();

        // Start at one so we can't overwrite the channel. The name is at index 3.
        for (int i = 1; i < oldProperties.length; i++) {
            if (newProperties[i] != null && i != 3) {
                oldProperties[i] = newProperties[i];
                updated = true;
            }
        }

        try {
            addChannel(new TVChannelImpl(oldProperties));
        } catch (Exception e) {
            logger.error("Unable to add new channel => ", e);
            return false;
        }

        return updated;
    }

    /**
     * Removes a channel and any remapping.
     *
     * @param originalChannel This is the original channel.
     */
    public void removeChannel(String originalChannel) {
        clearRemap(originalChannel);
        channelMap.remove(originalChannel);
    }

    /**
     * Checks if we already have a channel with the this name.
     * <p/>
     * If a channel with the same name and channel value exists, this will return <i>false</i> since
     * it is not actually a duplicate.
     *
     * @param channel The channel.
     * @param name The channel name.
     * @return <i>true</i> if there is a name with a different channel.
     */
    public boolean isDuplicate(String channel, String name) {
        ArrayList<TVChannel> channels = new ArrayList<TVChannel>();

        for (Map.Entry<String, TVChannel> keyValue : channelMap.entrySet()) {
            TVChannel tvChannel = keyValue.getValue();

            if (tvChannel != null) {
                if (tvChannel.getName().equals(name)) {
                    channels.add(tvChannel);
                }
            }
        }

        if (channels.size() == 1) {
            if (channels.get(0).getChannel().equals(channel)) {
                return false;
            }
        } else if (channels.size() == 0) {
            return false;
        }

        return true;
    }

    /**
     * Sets/Changes a channel remapping.
     * <p/>
     * When a channel is remapped, when you call <b>getChannel()</b>, you will get the channel by
     * it's remapping. This method also clears any old remappings so we don't have any one to many
     * relationships.
     *
     * @param originalChannel This is the original channel.
     * @param remapChannel    This is the remap channel.
     */
    public void setRemap(String originalChannel, String remapChannel) {
        clearRemap(originalChannel);

        TVChannel tvChannel = channelMap.get(originalChannel);

        if (tvChannel != null && remapChannel != "") {
            tvChannel.setChannelRemap(remapChannel);
            remapMap.put(remapChannel, tvChannel);
        }
    }

    /**
     * Removes a channel remapping.
     *
     * @param originalChannel This is the original channel.
     */
    public void clearRemap(String originalChannel) {
        ArrayList<String> removes = new ArrayList<String>();

        for (Map.Entry<String, TVChannel> remapPair : remapMap.entrySet()) {
            final String remapKey = remapPair.getKey();
            final TVChannel remapValue = remapPair.getValue();

            if (remapValue.getChannel().equals(originalChannel)) {
                removes.add(remapKey);
            }
        }

        for (String remove : removes) {
            remapMap.remove(remove);
        }

        TVChannel tvChannel = channelMap.get(originalChannel);
        if (tvChannel != null) {
            tvChannel.setChannelRemap("");
        }
    }

    /**
     * Get the channel by its original mapping.
     *
     * @param channelNumber This is the channel to lookup.
     * @return Returns a channel object or <i>null</i> if the channel doesn't exist.
     */
    public TVChannel getOriginalChannel(String channelNumber) {
        return channelMap.get(channelNumber);
    }

    /**
     * Get the channel by its remapped mapping.
     *
     * @param channelNumber This is the channel to lookup.
     * @return Returns a channel object or <i>null</i> if the channel isn't remapped.
     */
    public TVChannel getRedirectChannel(String channelNumber) {
        return remapMap.get(channelNumber);
    }

    /**
     * Get the channel by its original mapping and return a remapping instead if it exists.
     *
     * @param channelNumber This is the channel to lookup.
     * @return Returns a channel object or <i>null</i> if the original channel or a remapping
     * do not exist.
     */
    public TVChannel getChannel(String channelNumber) {
        TVChannel returnChannel = remapMap.get(channelNumber);

        if (returnChannel == null) {
            returnChannel = channelMap.get(channelNumber);
        } else {
            logger.info("'{}' was remapped to '{}'.", channelNumber, returnChannel.getChannel());
        }

        return returnChannel;
    }

    /**
     * Gets all of the channels currently in this lineup.
     * <p/>
     * The returned channel objects are copies, so if you make changes, it will not change anything
     * in the lineup.
     *
     * @return An array of all of the available channels in this lineup.
     */
    public ArrayList<TVChannel> getAllChannels(boolean includeIgnored, boolean includeNonTunable) {
        ArrayList<TVChannel> channels = new ArrayList<TVChannel>();

        for (Map.Entry<String, TVChannel> channelMapPair : channelMap.entrySet()) {
            final TVChannel tvChannel = channelMapPair.getValue();

            if (!tvChannel.isIgnore() || (includeIgnored && tvChannel.isIgnore())) {
                if (tvChannel.isTunable() || (includeNonTunable && !tvChannel.isTunable())) {
                    try {
                        channels.add(new TVChannelImpl(tvChannel.getProperties()));
                    } catch (Exception e) {
                        logger.error("Unable to create a new channel => ", e);
                    }
                }
            }
        }

        return channels;
    }

    /**
     * Removes all channels that do not match any names on the list.
     *
     * @param validChannels Valid channel list as a HashSet.
     * @return <i>true</i> if any channels were removed.
     */
    public boolean cleanChannels(HashSet<String> validChannels) {
        ArrayList<TVChannel> tvChannels = getAllChannels(true, true);

        boolean returnValue = false;

        for (TVChannel tvChannel : tvChannels) {
            if (!validChannels.contains(tvChannel.getChannel())) {
                removeChannel(tvChannel.getChannel());
                returnValue = true;
            }
        }

        return returnValue;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public long getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(long updateInterval) {
        this.updateInterval = updateInterval;
    }

    public long getNextUpdate() {
        return nextUpdate;
    }

    public void setNextUpdate(long nextUpdate) {
        this.nextUpdate = nextUpdate;
    }

    public boolean hasChannels() {
        return channelMap.size() > 0;
    }

    public long getOfflineUpdateInterval() {
        return offlineUpdateInterval;
    }

    public void setOfflineUpdateInterval(long offlineUpdateInterval) {
        this.offlineUpdateInterval = offlineUpdateInterval;
    }

    public long getNextOfflineUpdate() {
        return nextOfflineUpdate;
    }

    public void setNextOfflineUpdate(long nextOfflineUpdate) {
        this.nextOfflineUpdate = nextOfflineUpdate;
    }
}
