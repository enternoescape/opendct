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

package opendct.channel.updater.http;

import opendct.channel.ChannelLineup;
import opendct.channel.TVChannel;
import opendct.channel.TVChannelImpl;
import opendct.config.Config;
import opendct.config.options.BooleanDeviceOption;
import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.config.options.StringDeviceOption;
import opendct.tuning.discovery.DeviceDiscoverer;
import opendct.tuning.discovery.DiscoveryManager;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.hdhomerun.HDHomeRunDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HDHomeRunChannels {
    private static final Logger logger = LogManager.getLogger(HDHomeRunChannels.class);

    private final static Map<String, DeviceOption> deviceOptions;
    private static StringDeviceOption ignoreNamesContaining;
    private static StringDeviceOption ignoreChannelNumbers;
    private static BooleanDeviceOption removeDuplicateChannels;
    private static BooleanDeviceOption enableAllChannels;

    static {
        deviceOptions = new ConcurrentHashMap<>();

        while (true) {
            try {
                ignoreNamesContaining = new StringDeviceOption(
                        Config.getStringArray("channels.prime.ignore_names_containing_csv", "Target Ads", "VZ_URL_SOURCE", "VZ_EPG_SOURCE"),
                        true,
                        false,
                        "Ignore Channel Names Containing",
                        "channels.prime.ignore_names_containing_csv",
                        "All channels containing any of the values in this list will be discarded on update."
                );

                ignoreChannelNumbers = new StringDeviceOption(
                        Config.getStringArray("channels.prime.ignore_channels_csv"),
                        true,
                        false,
                        "Ignore Channels",
                        "channels.prime.ignore_channels_csv",
                        "All channel values in this list will be discarded on update."
                );

                removeDuplicateChannels = new BooleanDeviceOption(
                        Config.getBoolean("channels.prime.remove_duplicate_channels", true),
                        false,
                        "Remove Duplicate Channels",
                        "channels.prime.remove_duplicate_channels",
                        "Removed channels that have the exact same name. Preference is given to the" +
                                " first channel to have the name."
                );

                enableAllChannels = new BooleanDeviceOption(
                        Config.getBoolean("channels.prime.enable_all_channels", true),
                        false,
                        "Enable All Channels",
                        "channels.prime.enable_all_channels",
                        "Enable all channels received by the HDHomeRun device that are not DRM" +
                                " protected."
                );

                Config.mapDeviceOptions(
                        deviceOptions,
                        ignoreNamesContaining,
                        ignoreChannelNumbers,
                        removeDuplicateChannels
                );
            } catch (DeviceOptionException e) {
                logger.error("Unable to configure device options for HDHomeRunChannels reverting to defaults => ", e);

                Config.setStringArray("channels.prime.ignore_names_containing_csv", "Target Ads", "VZ_URL_SOURCE", "VZ_EPG_SOURCE");
                Config.setStringArray("channels.prime.ignore_channels_csv");
                Config.setBoolean("channels.prime.remove_duplicate_channels", true);
                Config.setBoolean("channels.prime.enable_all_channels", true);

                continue;
            }

            break;
        }
    }

    /**
     * This will populate the provided channel lineup with the latest channel information provided
     * by the the Prime DCT.
     *
     * @param channelLineup This is the lineup object.
     * @return <i>true</i> if the update was successful.
     */
    public static boolean populateChannels(ChannelLineup channelLineup) {
        logger.entry(channelLineup);

        boolean returnValue = true;
        // This only applies when ClearQAM is not in use because we can't do anything with the
        // returned information.
        boolean enableAllChannels = HDHomeRunChannels.enableAllChannels.getBoolean();
        boolean isQam = false;
        boolean isAtsc = false;

        HttpURLConnection httpURLConnection = null;
        //HashSet<String> newChannelList = new HashSet<String>();

        try {
            InetAddress ipAddress = null;
            String lookupAddress = channelLineup.getAddress();

            if (lookupAddress.length() == 8 && !lookupAddress.contains("."))
            {
                try {
                    int hex = Integer.parseInt(lookupAddress.toLowerCase(), 16);
                    DeviceDiscoverer discoverer = DiscoveryManager.getDiscoverer("HDHomeRun");

                    if (discoverer instanceof HDHomeRunDiscoverer) {
                        HDHomeRunDevice device =
                                ((HDHomeRunDiscoverer) discoverer).getHDHomeRunDevice(hex);

                        if (device != null) {
                            ipAddress = device.getIpAddress();
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Unable to parse '{}' from hex into an integer.",
                            channelLineup.getAddress());
                }

                if (ipAddress == null) {
                    return logger.exit(false);
                }
            }
            else
            {
                ipAddress = channelLineup.getAddressIP();

                if (ipAddress == null) {
                    return logger.exit(false);
                }
            }


            URL url = new URL("http://" + ipAddress.getHostAddress() + ":80/lineup.xml");
            logger.info("Connecting to HDHomeRun using the URL '{}'", url);

            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.connect();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            Document document = documentBuilder.parse(httpURLConnection.getInputStream());

            NodeList programList = null;

            try {
                programList = document.getChildNodes().item(0).getChildNodes();
            } catch (Exception e) {
                logger.error("Unable to read programs from Prime DCT.");
                return logger.exit(false);
            }

            for (int i = 0; i < programList.getLength(); i++) {

                Node programChild = programList.item(i);
                if (programChild.getNodeName().equals("Program")) {

                    String channel = null;
                    String name = null;
                    String channelUrl = null;

                    NodeList contentNodes = programChild.getChildNodes();
                    for (int x = 0; x < contentNodes.getLength(); x++) {

                        Node contentChild = contentNodes.item(x);
                        if (contentChild.getNodeName().equals("GuideNumber")) {
                            channel = contentChild.getTextContent();
                        } else if (contentChild.getNodeName().equals("GuideName")) {
                            name = contentChild.getTextContent();
                        } else if (contentChild.getNodeName().equals("URL")) {
                            channelUrl = contentChild.getTextContent();
                        }
                    }

                    if (channel != null && name != null && channelUrl != null) {

                        if (channel.contains(".")) {
                            isAtsc = true;
                        }

                        if (channel.equals("5000") && !isAtsc) {
                            isQam = true;
                            logger.warn("The HDHomeRun Prime appears to be in ClearQAM mode. You" +
                                    " either need to use a channel lineup from a device with a" +
                                    " CableCARD or manually map the channels with their programs" +
                                    " and frequencies. Auto-mapping is enabled by default and" +
                                    " will find the best match based on other lineups.");
                        }

                        // Check if the name is on the ignore list.
                        boolean ignore = false;
                        for (String ignoreName : ignoreNamesContaining.getArrayValue()) {
                            if (name.contains(ignoreName)) {
                                logger.debug("Skipping channel {} ({}) because it contains '{}'", channel, name, ignoreName);
                                ignore = true;
                                break;
                            }
                        }

                        for (String ignoreChannel : ignoreChannelNumbers.getArrayValue()) {
                            if (channel.equals(ignoreChannel)) {
                                logger.debug("Skipping channel {} ({}) because the channel number is '{}'", channel, name, ignoreChannel);
                                ignore = true;
                                break;
                            }
                        }

                        boolean isDuplicate = false;

                        if (removeDuplicateChannels.getBoolean() && !isQam) {
                            isDuplicate = channelLineup.isDuplicate(channel, name);

                            if (isDuplicate) {
                                try {
                                    channelLineup.removeChannel(channel);
                                } catch (Exception e) {
                                    logger.error("There was a problem removing the duplicate channel => ", e);
                                }
                            }
                        }

                        TVChannel oldChannel = channelLineup.getOriginalChannel(channel);

                        if (!isDuplicate) {
                            if (oldChannel == null) {
                                TVChannelImpl primeChannel = new TVChannelImpl(channel, name, channelUrl, ignore);

                                if (enableAllChannels && !channelUrl.contains("?CONTENTPROTECTIONTYPE")) {
                                    primeChannel.setTunable(true);
                                }

                                if (isAtsc) {
                                    // This ensures that the mapping will be correct from SageTV's perspective.
                                    primeChannel.setChannelRemap(channel.replace(".", "-"));
                                }

                                channelLineup.addChannel(primeChannel);

                            } else {
                                oldChannel.setUrl(channelUrl);

                                if (enableAllChannels && !channelUrl.contains("?CONTENTPROTECTIONTYPE")) {
                                    oldChannel.setTunable(true);
                                }

                                channelLineup.updateChannel(oldChannel);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("populateChannels created an unexpected exception => ", e);
            returnValue = false;
        } finally {
            try {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception e) {
                logger.trace("Created an exception while disconnecting => ", e);
            }
        }

        return logger.exit(returnValue);
    }

}
