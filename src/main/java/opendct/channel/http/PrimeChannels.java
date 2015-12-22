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

package opendct.channel.http;

import opendct.channel.ChannelLineup;
import opendct.channel.TVChannel;
import opendct.channel.TVChannelImpl;
import opendct.config.Config;
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
import java.util.HashSet;

public class PrimeChannels {
    private static final Logger logger = LogManager.getLogger(PrimeChannels.class);

    private static final String[] ignoreNamesContaining =
            Config.getStringArray("channels.prime.ignore_names_containing_csv", "Target Ads", "VZ_URL_SOURCE", "VZ_EPG_SOURCE");
    private static final String[] ignoreChannelNumbers =
            Config.getStringArray("channels.prime.ignore_channels_csv", "");
    private static final boolean removeDuplicateChannels =
            Config.getBoolean("channels.prime.remove_duplicate_channels", true);
    private static final boolean enableAllChannels =
            Config.getBoolean("channels.prime.enable_all_channels", true);

    /**
     * This will populate the provided channel lineup with the latest channel information provided
     * by the the Prime DCT.
     *
     * @param channelLineup This is the lineup object.
     * @return <i>true</i> if the update was successful.
     */
    public static boolean populateChannels(ChannelLineup channelLineup) {
        logger.entry();

        boolean returnValue = true;
        // This only applies when ClearQAM is not in use because we can't do anything with the
        // returned information.
        boolean enableAllChannels = PrimeChannels.enableAllChannels;
        boolean isQam = false;

        HttpURLConnection httpURLConnection = null;
        HashSet<String> newChannelList = new HashSet<String>();

        try {
            InetAddress ipAddress = channelLineup.getAddressIP();
            if (ipAddress == null) {
                return logger.exit(false);
            }

            URL url = new URL("http://" + ipAddress.getHostAddress() + ":80/lineup.xml");
            logger.info("Connecting to Prime DCT using the URL '{}'", url);

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

                        if (channel.equals("5000")) {
                            enableAllChannels = false;
                            isQam = true;
                            logger.warn("The HDHomeRun Prime appears to be in ClearQAM mode. You" +
                                    " either need to use a channel lineup from a device with a" +
                                    " CableCARD or manually map the channels with their programs" +
                                    " and frequencies. Auto-mapping is enabled by default and" +
                                    " will find the best match based on other lineups.");
                        }

                        // Check if the name is on the ignore list.
                        boolean ignore = false;
                        for (String ignoreName : ignoreNamesContaining) {
                            if (name.contains(ignoreName)) {
                                logger.debug("Skipping channel {} ({}) because it contains '{}'", channel, name, ignoreName);
                                ignore = true;
                                break;
                            }
                        }

                        for (String ignoreChannel : ignoreChannelNumbers) {
                            if (channel.equals(ignoreChannel)) {
                                logger.debug("Skipping channel {} ({}) because the channel number is '{}'", channel, name, ignoreChannel);
                                ignore = true;
                                break;
                            }
                        }

                        newChannelList.add(channel);

                        boolean isDuplicate = false;

                        if (removeDuplicateChannels && !isQam) {
                            isDuplicate = channelLineup.isDuplicate(channel, name);

                            if (isDuplicate) {
                                try {
                                    channelLineup.removeChannel(channel);
                                } catch (Exception e) {
                                    logger.error("There was a problem removing the duplicate channel => ", e);
                                }
                            }
                        }

                        TVChannel oldChannel = channelLineup.getChannel(channel);

                        if (!isDuplicate) {
                            if (oldChannel == null) {
                                TVChannelImpl primeChannel = new TVChannelImpl(channel, name, channelUrl, ignore);

                                if (enableAllChannels) {
                                    primeChannel.setTunable(true);
                                }

                                channelLineup.addChannel(primeChannel);
                            } else {
                                oldChannel.setUrl(channelUrl);

                                if (enableAllChannels) {
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
