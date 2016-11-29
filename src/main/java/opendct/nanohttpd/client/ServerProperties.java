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

package opendct.nanohttpd.client;

import java.net.*;

import static opendct.config.StaticConfig.*;

public class ServerProperties {
    private int majorVersion;
    private int minorVersion;
    private int buildVersion;

    private InetAddress address;
    private int port;
    private String serverName;
    private boolean isLocal;
    private boolean isRemote;

    protected ServerProperties() {
        isLocal = false;
        isRemote = false;
    }

    void setMajorVersion(int majorVersion) {
        this.majorVersion = majorVersion;
    }

    void setMinorVersion(int minorVersion) {
        this.minorVersion = minorVersion;
    }

    void setBuildVersion(int buildVersion) {
        this.buildVersion = buildVersion;
    }

    boolean isVersionCompatible() {
        return majorVersion > ENCODER_COMPATIBLE_MAJOR_VERSION ||
                majorVersion == ENCODER_COMPATIBLE_MAJOR_VERSION &&
                        minorVersion < ENCODER_COMPATIBLE_MINOR_VERSION ||
                majorVersion == ENCODER_COMPATIBLE_MAJOR_VERSION &&
                        minorVersion == ENCODER_COMPATIBLE_MINOR_VERSION &&
                        buildVersion == ENCODER_COMPATIBLE_MICRO_VERSION;
    }

    void setAddress(InetAddress address) {
        this.address = address;
    }

    void setPort(int port) {
        this.port = port;
    }

    void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getBuildVersion() {
        return buildVersion;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getServerName() {
        return serverName;
    }

    public boolean isLocal() {
        if (isLocal) {
            return true;
        }

        if (!isRemote) {
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                isLocal = true;
            } else {
                try {
                    if (NetworkInterface.getByInetAddress(address) != null) {
                        isLocal = true;
                    }
                } catch (SocketException e) {}
            }
            isRemote = !isLocal;
        }

        return isLocal;
    }

    public URL getURL(String path) {
        if (!path.startsWith("/"))
            path = "/" + path;

        String url = "http://" + serverName + ":" + port + path;
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            System.out.println("OpenDCT - ERROR: Unable to get URL from " + url + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public String toString() {
        return "ServerProperties{" +
                "majorVersion=" + majorVersion +
                ", minorVersion=" + minorVersion +
                ", buildVersion=" + buildVersion +
                ", address=" + address +
                ", port=" + port +
                ", serverName='" + serverName + '\'' +
                ", isLocal=" + isLocal +
                '}';
    }
}
