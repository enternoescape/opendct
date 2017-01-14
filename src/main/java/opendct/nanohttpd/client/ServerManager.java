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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static opendct.config.StaticConfig.*;

public class ServerManager {

    private final static int CONNECTION_TIMEOUT = 10000;

    private static class InstanceHolder {
        private static final ServerManager instance = new ServerManager();
    }

    public static ServerManager getInstance() {
        return InstanceHolder.instance;
    }

    private final Map<String, ServerProperties> servers = new ConcurrentHashMap<>();
    private final Gson gson;

    private ServerManager() {
        // This is not required, but if we need to add special deserializers in the future, this
        // will make it a little easier to determine where they should go.
        GsonBuilder builder = new GsonBuilder();
        gson = builder.create();
    }

    /**
     * Discover OpenDCT servers on the network and get their web ports.
     * <p/>
     * This method will not return until discovery has completed or timed out. For reference, it
     * should return around 3-5 seconds.
     *
     * @param remotePort The remote port to broadcast at for discovery.
     */
    public void discoverServers(final int remotePort) {
        List<InetAddress> addresses = Util.getBroadcastAddresses();

        Thread discoveryThreads[] = new Thread[addresses.size()];
        int index = 0;
        for (final InetAddress address : addresses) {
            discoveryThreads[index] = new Thread(new Runnable() {
                @Override
                public void run() {
                    DatagramSocket socket = null;
                    try {
                        socket = new DatagramSocket();
                        socket.setBroadcast(true);

                        byte packetBytes[] = new byte[1500];

                        packetBytes[0] = 'W';
                        packetBytes[1] = 'T';
                        packetBytes[2] = 'N';
                        packetBytes[3] = ENCODER_COMPATIBLE_MAJOR_VERSION;
                        packetBytes[4] = ENCODER_COMPATIBLE_MINOR_VERSION;
                        packetBytes[5] = ENCODER_COMPATIBLE_MICRO_VERSION;

                        DatagramPacket packet = new DatagramPacket(packetBytes, 0, packetBytes.length);
                        packet.setAddress(address);
                        packet.setPort(remotePort);
                        // Otherwise the data doesn't flush immediately
                        packet.setLength(32);

                        System.out.println("OpenDCT - INFO: Broadcasting discovery to "
                                + address.getHostAddress() + " port " + remotePort);
                        socket.send(packet);

                        long timeout = System.currentTimeMillis() + 3000;
                        do {
                            packet.setLength(packetBytes.length);
                            socket.setSoTimeout((int)Math.min(Math.max(timeout - System.currentTimeMillis(), 1000), 3000));
                            socket.receive(packet);

                            if (packet.getLength() >= 9) {
                                if (packetBytes[0] != 'W') continue;
                                if (packetBytes[1] != 'T') continue;
                                if (packetBytes[2] != 'N') continue;

                                ServerProperties newServer = new ServerProperties();
                                newServer.setMajorVersion(packetBytes[3]);
                                newServer.setMinorVersion(packetBytes[4]);
                                newServer.setBuildVersion(packetBytes[5]);
                                if (!newServer.isVersionCompatible()) continue;
                                newServer.setPort(((packetBytes[6] & 0xff) << 8) + (packetBytes[7] & 0xff));
                                int descriptionLength = packetBytes[8] & 0xff;
                                if (descriptionLength > packet.getLength() - 9) continue;
                                newServer.setServerName(new String(packetBytes, 9, descriptionLength, StandardCharsets.UTF_8));
                                newServer.setAddress(packet.getAddress());
                                // Determines if this address is an interface on this server or not.
                                newServer.isLocal();

                                System.out.println("OpenDCT - INFO: Discovered server: " + newServer.toString());
                                servers.put(newServer.getServerName(), newServer);
                            }
                        } while (timeout < System.currentTimeMillis());
                    } catch (Exception e) {
                        System.out.println("OpenDCT - ERROR: Discovery exception => ");
                        e.printStackTrace(System.out);
                    } finally {
                        try {
                            if (socket != null) {
                                socket.close();
                            }
                        } catch (Exception e) {}
                    }
                }
            }, "OpenDCTServerDiscovery-" + index);
            discoveryThreads[index++].start();
        }

        for (Thread discoveryThread : discoveryThreads) {
            if (discoveryThread == null)
                continue;
            try {
                discoveryThread.join(5000);
            } catch (InterruptedException e) {}
        }
    }

    /**
     * Add a server manually.
     * <p/>
     * While this should probably be accessible to the user, it generally shouldn't be needed.
     *
     * @param address The hostname or IP address of the server.
     * @param port The port that the web server is running on. Generally this will be the default
     *             9091, but the property can be changed.
     * @return <code>null</code> if the server was added successfully. Otherwise a string of text
     *         will be returned explaining why the server could not be added.
     */
    public String addServer(String address, int port)
    {
        if (address == null || address.length() == 0 || port <= 0)
            return "A hostname or IP address is required.";
        if (port <= 0)
            return "A valid web server port is required.";
        ServerProperties newServer = new ServerProperties();
        try {
            newServer.setAddress(InetAddress.getByName(address));
            try {
                newServer.setServerName(newServer.getAddress().getHostName());
            } catch (Exception e) {
                newServer.setServerName(address);
            }
        } catch (UnknownHostException e) {
            System.out.println("OpenDCT - ERROR: Unable to resolve server name => " + e.getMessage());
            e.printStackTrace(System.out);
            return "The provided hostname or IP address is invalid.";
        }
        newServer.setPort(port);
        newServer.setMajorVersion(ENCODER_COMPATIBLE_MAJOR_VERSION);
        newServer.setMinorVersion(ENCODER_COMPATIBLE_MINOR_VERSION);
        newServer.setBuildVersion(ENCODER_COMPATIBLE_MICRO_VERSION);
        servers.put(newServer.getServerName(), newServer);
        return null;
    }

    /**
     * Get all servers currently discovered.
     *
     * @return A String array containing all servers currently discovered.
     */
    public String[] getServers() {
        return servers.keySet().toArray(new String[0]);
    }

    /**
     * Get a specific server's properties by name.
     * <p/>
     * This method will also attempt to detect the server if the provided name does not match
     * currently know servers. If it cannot detect the server, it will attempt to create the server
     * using the default port 9091 if the name actually resolves to a network accessible device.
     *
     * @param serverName The server name.
     * @return The server properties if the server exists.
     */
    public ServerProperties getServer(String serverName) {
        ServerProperties server = servers.get(serverName);
        // Try to discover the server. This is better than just forcing the entry in case the server
        // is not using the default port 9091.
        if (server == null) {
            discoverServers(Plugin.DISCOVERY_PORT);
            server = servers.get(serverName);
            // Try to create the assumed server properties if the server does not exist.
            if (server == null) {
                addServer(serverName, 9091);
                server = servers.get(serverName);
            }
        }
        return server;
    }

    /**
     * Returns the requested JSON object deserialized from the reply to the requested file.
     *
     * @param serverName The server to connect to.
     * @param returnObject The class to be returned with the deserialized JSON object.
     * @param file The file path to be used on the server.
     * @param <T> The type of object desired.
     * @return The requested object type or <code>null</code> if there was a problem with the
     *         connection or deserializing.
     */
    public <T> T getJson(String serverName, Class<T> returnObject, String file) {
        ServerProperties server = servers.get(serverName);
        if (server == null) {
            return null;
        }
        URL url = server.getURL(file);
        if (url == null) {
            return null;
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(CONNECTION_TIMEOUT);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            return  gson.fromJson(new InputStreamReader(new BufferedInputStream(connection.getInputStream())), returnObject);
        } catch (IOException e) {
            System.out.println("OpenDCT - ERROR: HTTP GET exception for server " + server + " => " + e.getMessage());
            e.printStackTrace(System.out);
        }
        return null;
    }

    /**
     * Returns the requested JSON object deserialized from the reply to the requested file.
     *
     * @param serverName The server to connect to.
     * @param returnObject The class to be returned with the deserialized JSON object.
     * @param file The file path to be used on the server.
     * @param post The JSON element to be posted.
     * @param <T> The type of object desired.
     * @return The requested object type or <code>null</code> if there was a problem with the
     *         connection or deserializing.
     */
    public <T> T postJson(String serverName, Class<T> returnObject, String file, JsonElement post) {
        ServerProperties server = servers.get(serverName);
        if (server == null) {
            return null;
        }
        URL url = server.getURL(file);
        if (url == null) {
            return null;
        }
        OutputStream outputStream = null;
        InputStreamReader inputStream = null;

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
            OutputStreamWriter writer = new OutputStreamWriter(byteStream, StandardCharsets.UTF_8);
            gson.toJson(post, writer);
            writer.flush();
            byte transmit[] = byteStream.toByteArray();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setReadTimeout(CONNECTION_TIMEOUT);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            // POST must have a length or we will not get a reply.
            connection.setRequestProperty("Content-Length", Integer.toString(transmit.length));

            outputStream = connection.getOutputStream();
            outputStream.write(transmit, 0, transmit.length);
            inputStream = new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8);
            return gson.fromJson(new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8), returnObject);
        } catch (IOException e) {
            System.out.println("OpenDCT - ERROR: HTTP POST exception for server " + server + " => " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {}
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    /**
     * Returns the requested JSON object deserialized from the reply to the requested file.
     *
     * @param serverName The server to connect to.
     * @param returnObject The class to be returned with the deserialized JSON object.
     * @param file The file path to be used on the server.
     * @param put The JSON element to be put.
     * @param <T> The type of object desired.
     * @return The requested object type or <code>null</code> if there was a problem with the
     *         connection or deserializing.
     */
    public <T> T putJson(String serverName, Class<T> returnObject, String file, JsonElement put) {
        ServerProperties server = servers.get(serverName);
        if (server == null) {
            return null;
        }
        URL url = server.getURL(file);
        if (url == null) {
            return null;
        }
        OutputStream outputStream = null;
        InputStreamReader inputStream = null;

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
            OutputStreamWriter writer = new OutputStreamWriter(byteStream, StandardCharsets.UTF_8);
            gson.toJson(put, writer);
            writer.flush();
            byte transmit[] = byteStream.toByteArray();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setReadTimeout(CONNECTION_TIMEOUT);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            // PUT must have a length or we will not get a reply.
            connection.setRequestProperty("Content-Length", Integer.toString(transmit.length));

            outputStream = connection.getOutputStream();
            outputStream.write(transmit, 0, transmit.length);
            inputStream = new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8);
            return gson.fromJson(new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8), returnObject);
        } catch (IOException e) {
            System.out.println("OpenDCT - ERROR: HTTP PUT exception for server " + server + " => " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {}
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    /**
     * Returns the requested JSON object deserialized from the reply to the requested file.
     *
     * @param serverName The server to connect to.
     * @param returnObject The class to be returned with the deserialized JSON object.
     * @param file The file path to be used on the server.
     * @param delete The JSON element to be deleted.
     * @param <T> The type of object desired.
     * @return The requested object type or <code>null</code> if there was a problem with the
     *         connection or deserializing.
     */
    public <T> T deleteJson(String serverName, Class<T> returnObject, String file, JsonElement delete) {
        ServerProperties server = servers.get(serverName);
        if (server == null) {
            return null;
        }
        URL url = server.getURL(file);
        if (url == null) {
            return null;
        }
        OutputStream outputStream = null;
        InputStreamReader inputStream = null;

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
            OutputStreamWriter writer = new OutputStreamWriter(byteStream, StandardCharsets.UTF_8);
            gson.toJson(delete, writer);
            writer.flush();
            byte transmit[] = byteStream.toByteArray();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setReadTimeout(CONNECTION_TIMEOUT);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            // DELETE must have a length or we will not get a reply.
            connection.setRequestProperty("Content-Length", Integer.toString(transmit.length));

            outputStream = connection.getOutputStream();
            outputStream.write(transmit, 0, transmit.length);
            inputStream = new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8);
            return gson.fromJson(new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8), returnObject);
        } catch (IOException e) {
            System.out.println("OpenDCT - ERROR: HTTP DELETE exception for server " + server + " => " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {}
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }
}
