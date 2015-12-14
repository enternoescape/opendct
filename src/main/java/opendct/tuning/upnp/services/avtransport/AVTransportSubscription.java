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

package opendct.tuning.upnp.services.avtransport;

import opendct.tuning.upnp.services.shared.ServiceSubscription;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.meta.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;

public class AVTransportSubscription extends ServiceSubscription {
    private final Logger logger = LogManager.getLogger(AVTransportSubscription.class);

    protected final Object lastChangeLock = new Object();

    //AVTransport subscription returned variables.
    private volatile String avTransportLastChange = "";
    private volatile boolean avTransportTransportState = false; //True when TransportState = PLAYING
    private volatile String avTransportInstanceID = "0"; //This is the Instance ID that will be tracked for our use.

    public AVTransportSubscription(UpnpService upnpService, Service service) {
        super(upnpService, service);
    }

    public String getAVTransportLastChange() {
        String status;
        if ((status = getStateVariableValue("LastChange")) != null) {
            setAVTransportLastChange(status);
        }
        return avTransportLastChange;
    }

    private void setAVTransportLastChange(String message) {
        logger.entry();

        synchronized (lastChangeLock) {
            try {
                avTransportLastChange = message;

                Integer contentStart = message.indexOf("<InstanceID ");
                Integer contentEnd = message.lastIndexOf("</InstanceID>") + 13;
                if (contentStart < 0 || contentEnd < contentStart) {
                    logger.debug("AVTransport/LastChange/InstanceID does not contain any XML.");
                } else {
                    String xml;
                    xml = message.substring(contentStart, contentEnd);

                    Document document;
                    if ((document = stringToDocument(xml)) != null) {
                        NodeList instanceIDList = document.getChildNodes();

                        for (int i = 0; i < instanceIDList.getLength(); i++) {
                            Node instanceIDChild = instanceIDList.item(i);
                            if (instanceIDChild.getNodeName().equals("InstanceID")) {
                                try {
                                    if (!instanceIDChild.getAttributes().getNamedItem("val").getTextContent().equals(avTransportInstanceID)) {
                                        //We are only interested in the InstanceID we were issued by ConnectionManager.
                                        continue;
                                    }
                                } catch (Exception e) {
                                    logger.warn("Unable to find 'val' attribute under AVTransport/LastChange/InstanceID => {}", e);
                                    continue;
                                }
                            }
                            NodeList transportStateList = instanceIDChild.getChildNodes();
                            for (int t = 0; t < transportStateList.getLength(); t++) {
                                Node transportStateChild = transportStateList.item(t);
                                if (transportStateChild.getNodeName().equals("TransportState")) {
                                    try {
                                        String val = transportStateChild.getAttributes().getNamedItem("val").getTextContent();
                                        avTransportTransportState = (Boolean) (val.equalsIgnoreCase("PLAYING"));
                                    } catch (Exception e) {
                                        logger.warn("Unable to find 'val' attribute under AVTransport/LastChange/InstanceID/TransportState => " + e.getStackTrace().toString());
                                    }
                                }
                            }

                        }
                    }
                }
            } catch (Exception e) {
                logger.error("An unexpected error happened while parsing AVTransport/LastChange => {}", e);
            }
        }

        logger.exit();
    }

    //This value is used to select what to store from the LastChange state variable.
    public String getAVTransportInstanceID() {
        return avTransportInstanceID;
    }

    public void setAVTransportInstanceID(String instanceID) {
        logger.entry();

        synchronized (lastChangeLock) {
            avTransportInstanceID = instanceID;
            setAVTransportLastChange(avTransportLastChange);
        }

        logger.exit();
    }

    //This value is extracted from the LastChange state variable.
    public boolean getAVTransportTransportState() {
        String status;
        if ((status = getStateVariableValue("LastChange")) != null) {
            setAVTransportLastChange(status);
        }
        return avTransportTransportState;
    }

    public boolean waitForAVTransportTransportState(boolean expectedState, int timeout) {
        logger.entry(expectedState, timeout);

        long startTime = System.currentTimeMillis();

        boolean returnValue = false;

        while (true) {
            if (getAVTransportTransportState() == expectedState) {
                returnValue = true;
            }

            long currentTime = System.currentTimeMillis();

            if (returnValue) {
                break;
            } else if (currentTime - startTime > timeout) {
                logger.warn("Timeout occurred at {}ms while waiting for 'TransportState' to have the value '{}'.", currentTime - startTime, expectedState);
                break;
            }

            if (!waitForStateVariable("LastChange", null, timeout)) {
                break;
            }
        }


        return logger.exit(returnValue);
    }

    public Document stringToDocument(String xml) {
        logger.entry();

        logger.debug("Parsing string into document => {}", xml);
        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource inputSource = new InputSource();
            inputSource.setCharacterStream(new StringReader(xml));
            try {
                Document doc = documentBuilder.parse(inputSource);
                return logger.exit(doc);
            } catch (SAXException e) {
                logger.error("Unable to parse xml => {}", e);
            } catch (IOException e) {
                logger.error("Unable to read xml => {}", e);
            }
        } catch (ParserConfigurationException e) {
            logger.error("Unable to initialize DocumentBuilder => {}", e);
        }

        return logger.exit(null);
    }
}
