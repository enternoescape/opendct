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

package opendct.config;

import opendct.config.options.DeviceOption;
import opendct.config.options.DeviceOptionException;
import opendct.consumer.FFmpegSageTVConsumerImpl;
import opendct.consumer.RawSageTVConsumerImpl;
import opendct.consumer.SageTVConsumer;
import opendct.producer.HTTPProducer;
import opendct.producer.HTTPProducerImpl;
import opendct.producer.NIORTPProducerImpl;
import opendct.producer.RTPProducer;
import opendct.util.Util;
import opendct.video.rtsp.DCTRTSPClientImpl;
import opendct.video.rtsp.RTSPClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Config {
    private static final Logger logger = LogManager.getLogger(Config.class);

    public static final int VERSION_MAJOR = 0;
    public static final int VERSION_MINOR = 4;
    public static final int VERSION_BUILD = 20;
    public static final String VERSION = VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_BUILD;

    private static final Object getSocketServerPort = new Object();
    private static final Object getRTSPPort = new Object();
    private static Properties properties = new Properties();
    private static volatile boolean isConfigOnly = false;
    private static volatile boolean isShutdown = false;
    private static final HashMap<Integer, String> rtspPortMap = new HashMap<>();

    public static final OSVersion OS_VERSION = getOsVersion();
    public static final boolean IS_WINDOWS = (OS_VERSION == OSVersion.WINDOWS);
    public static final boolean IS_LINUX = (OS_VERSION == OSVersion.LINUX);
    public static final boolean IS_MAC = (OS_VERSION == OSVersion.MAC);
    public static final String NEW_LINE = System.lineSeparator();
    public static final String DIR_SEPARATOR = File.separator;

    private static int exitCode = 0;

    private static final String configFileName = "opendct.properties";

    // We should be using this any time we are converting text to bytes.
    public static final String STD_BYTE = "UTF-8";

    private static String directory;

    public static String getConfigDirectory() {
        return Config.directory;
    }

    public static String getDefaultConfigFilename() {
        return Config.directory + File.separator + configFileName;
    }

    /**
     * Set the directory used for configuration files.
     * <p/>
     * If this cannot be set correctly the configuration data will not be loaded correctly and the
     * program should just exit.
     *
     * @param directory This is the directory to be used for configuration files.
     * @return <i>true</i> if the directory exists or was able to be created.
     */
    public static synchronized boolean setConfigDirectory(String directory) {
        logger.entry(directory);

        File file = new File(directory);

        boolean returnValue = file.exists();

        if (returnValue) {
            Config.directory = directory;
        } else {
            try {
                logger.info("The directory '{}' does not exist. Attempting to create it...", directory);
                returnValue = file.mkdirs();

                if (returnValue) {
                    Config.directory = directory;
                }
            } catch (Exception e) {
                logger.fatal("An exception was created while attempting to create the configuration directory => ", e);
            }
        }

        return logger.exit(returnValue);
    }

    public static synchronized boolean loadConfig() {
        logger.entry();

        if (Config.directory == null) {
            logger.fatal("The configuration directory must be defined before any properties can be loaded.");
            return logger.exit(false);
        }

        String filename = getDefaultConfigFilename();

        if (new File(filename).exists()) {
            FileInputStream fileInputStream;
            try {
                fileInputStream = new FileInputStream(filename);
            } catch (FileNotFoundException e) {
                logger.fatal("Unable to open the configuration file '{}' => ", filename, e);
                return logger.exit(false);
            }

            if (Config.properties != null) {
                // It looks like we have a new configuration. Let's clear out the old one.
                logger.debug("Clearing current OpenDCT configuration...");
                Config.properties.clear();
            }

            try {
                Config.properties.load(fileInputStream);
            } catch (IOException e) {
                logger.fatal("Unable to read the configuration file '{}' => ", filename, e);
                return logger.exit(false);
            }
        } else {
            logger.info("'{}' was not found. A new configuration file will be created with that name on the next save.", filename);
        }

        //If we are doing a config only run, set this variable since it will get queried a lot.
        Config.isConfigOnly = CommandLine.isConfigOnly();

        return logger.exit(true);
    }

    public static synchronized void clearConfig() {
        logger.entry();

        if (properties != null) {
            logger.debug("Clearing current OpenDCT configuration...");
            properties.clear();
        } else {
            properties = new Properties();
        }

        logger.exit();
    }

    public static synchronized boolean saveConfig() {
        logger.entry();

        if (Config.directory == null) {
            logger.fatal("The configuration directory must be defined before any properties can be saved.");
            return logger.exit(false);
        }

        String filename = getDefaultConfigFilename();

        File file = new File(filename);
        File fileBackup = new File(filename + ".backup");
        try {
            Util.copyFile(file, fileBackup, true);
        } catch (IOException e) {
            file.renameTo(fileBackup);
        }

        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            logger.error("Unable to open the configuration file '{}' => {}", filename, e);
            return logger.exit(false);
        }

        try {
            Properties sortedProperties = new Properties() {
                @Override
                public synchronized Enumeration<Object> keys() {
                    return Collections.enumeration(new TreeSet<>(super.keySet()));
                }
            };
            sortedProperties.putAll(properties);

            sortedProperties.store(fileOutputStream, "OpenDCT Configuration File");
            fileOutputStream.close();
        } catch (IOException e) {
            logger.error("Unable to write the configuration file '{}' => {}", filename, e);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    public static boolean isConfigOnly() {
        logger.entry();
        return logger.exit(Config.isConfigOnly);
    }

    public static void logCleanup() {
        long minFreeSpace = Config.getLong("log.min_free_space", 1073741824);
        long days = Config.getLong("log.remove_after_days", 30);

        if (days <= 0) {
            return;
        }

        long retainDays = days * 86400000;

        File logDir = new File(CommandLine.getLogDir() + DIR_SEPARATOR + "archive");
        File files[] = logDir.listFiles();

        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            long daysOld = System.currentTimeMillis() - file.lastModified();

            if (daysOld > retainDays) {
                if (file.delete()) {
                    logger.info("Removed log file '{}' because it is over {} {} old.",
                            file.getName(), days, days == 1 ? "day" : "days");
                } else {
                    logger.info("Unable to remove log file '{}' that is over {} {} old.",
                            file.getName(), days, days == 1 ? "day" : "days");
                }
            }
        }

        if (logDir.getFreeSpace() < minFreeSpace) {
            files = logDir.listFiles();

            if (files == null || files.length == 0) {
                return;
            }

            for (File file : files) {
                if (file.delete()) {
                    logger.info("Removed log file '{}' because free disk space is below {} bytes.",
                            file.getName(), minFreeSpace);
                } else {
                    logger.info("Unable to remove log file '{}' that is contributing to the free disk space being below {} bytes.",
                            file.getName(), minFreeSpace);
                }

                if (logDir.getFreeSpace() < minFreeSpace) {
                    break;
                }
            }
        }
    }

    public static String getLogDir() {
        return CommandLine.getLogDir();
    }

    public static void setExitCode(int exitCode) {
        Config.exitCode = exitCode;
    }

    public static int getExitCode() {
        return exitCode;
    }

    // This will be used to set all string properties so we can do trace logging if there is any
    // configuration related weirdness.
    public static void setString(String key, String value) {
        logger.entry(key, value);

        Config.properties.setProperty(key, value);

        logger.exit();
    }

    // This will be used to request all string properties so we can do trace logging if there is
    // any configuration related weirdness.
    public static String getString(String key, String defaultValue) {
        logger.entry(key, defaultValue);

        String returnValue = Config.properties.getProperty(key, defaultValue);

        setString(key, returnValue);

        return logger.exit(returnValue);
    }


    public static String getString(String key) {
        logger.entry(key);
        return logger.exit(properties.getProperty(key));
    }

    public static void setBoolean(String key, boolean value) {
        logger.entry(key, value);

        Config.properties.setProperty(key, Boolean.toString(value));

        logger.exit();
    }

    public static void setShort(String key, short value) {
        logger.entry(key, value);

        Config.properties.setProperty(key, Short.toString(value));

        logger.exit();
    }

    public static void setInteger(String key, int value) {
        logger.entry(key, value);

        Config.properties.setProperty(key, Integer.toString(value));

        logger.exit();
    }

    public static void setIntegerArray(String key, int... values) {
        logger.entry(key, values);

        StringBuilder mergedArray = new StringBuilder();

        for (int value : values) {
            mergedArray.append(Integer.toString(value));
            mergedArray.append(",");
        }

        // Remove the extra comma at the end.
        if (mergedArray.length() > 0) {
            mergedArray.deleteCharAt(mergedArray.length() - 1);
        }

        Config.properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    public static void setIntegerArray(String key, Integer... values) {
        logger.entry(key, values);

        StringBuilder mergedArray = new StringBuilder();

        for (int value : values) {
            mergedArray.append(Integer.toString(value));
            mergedArray.append(",");
        }

        // Remove the extra comma at the end.
        if (mergedArray.length() > 0) {
            mergedArray.deleteCharAt(mergedArray.length() - 1);
        }

        Config.properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    public static void setLong(String key, long value) {
        logger.entry(key, value);

        Config.properties.setProperty(key, Long.toString(value));

        logger.exit();
    }

    public static void setFloat(String key, float value) {
        logger.entry(key, value);

        Config.properties.setProperty(key, Float.toString(value));

        logger.exit();
    }

    public static void setDouble(String key, double value) {
        logger.entry(key, value);

        Config.properties.setProperty(key, Double.toString(value));

        logger.exit();
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        logger.entry(key, defaultValue);

        boolean returnValue = defaultValue;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        try {
            returnValue = Boolean.valueOf(stringValue.toLowerCase());
        } catch (Exception e) {
            logger.error("The property '{}' should be boolean, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        setBoolean(key, returnValue);

        return logger.exit(returnValue);
    }

    public static short getShort(String key, short defaultValue) {
        logger.entry(key, defaultValue);

        short returnValue = defaultValue;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        try {
            returnValue = Short.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a short, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        setShort(key, returnValue);

        return logger.exit(returnValue);
    }

    public static int getInteger(String key, int defaultValue) {
        logger.entry(key, defaultValue);

        int returnValue = defaultValue;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));

        try {
            returnValue = Integer.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be an integer, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        setInteger(key, returnValue);

        return logger.exit(returnValue);
    }

    public static int[] getIntegerArray(String key, int... defaultValue) {
        logger.entry(key, defaultValue);

        String stringValue[] = properties.getProperty(key, "").split("\\s*,\\s*");
        int returnValue[];

        if (stringValue.length == 1 && stringValue[0].equals("")) {
            returnValue = new int[0];
            stringValue = new String[0];
        } else {
            returnValue = new int[stringValue.length];
        }

        try {
            for (int i = 0; i < returnValue.length; i++) {
                returnValue[i] = Integer.valueOf(stringValue[i]);
            }
        } catch (Exception e) {
            logger.error("The property '{}' should be an integer array, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        setIntegerArray(key, returnValue);

        return logger.exit(returnValue);
    }

    public static long getLong(String key, long defaultValue) {
        logger.entry(key, defaultValue);

        long returnValue = defaultValue;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));

        try {
            returnValue = Long.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a long, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        setLong(key, returnValue);

        return logger.exit(returnValue);
    }

    public static float getFloat(String key, float defaultValue) {
        logger.entry(key, defaultValue);

        float returnValue = defaultValue;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));

        try {
            returnValue = Float.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a float, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        setFloat(key, returnValue);

        return logger.exit(returnValue);
    }

    public static double getDouble(String key, double defaultValue) {
        logger.entry(key, defaultValue);

        double returnValue = defaultValue;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));

        try {
            returnValue = Double.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a float, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        setDouble(key, returnValue);

        return logger.exit(returnValue);
    }

    public static String[] getStringArray(String key, String... defaultValues) {
        logger.entry();

        String returnValue[];
        String stringValue;

        if ((stringValue = properties.getProperty(key)) != null) {
            if (stringValue.equals("")) {
                returnValue = new String[0];
            } else {
                // The parsing regex will tolerate white space between commas.
                returnValue = stringValue.split("\\s*,\\s*");
            }
        } else {
            StringBuilder mergedArray = new StringBuilder();

            for (String value : defaultValues) {
                mergedArray.append(value);
                mergedArray.append(",");
            }

            // Remove the extra comma at the end.
            if (mergedArray.length() > 0) {
                mergedArray.deleteCharAt(mergedArray.length() - 1);
            }

            properties.setProperty(key, mergedArray.toString());
            returnValue = defaultValues;
        }

        return logger.exit(returnValue);
    }

    public static String[] getStringArray(String key, String defaultValue) {
        logger.entry(key, defaultValue);

        // The parsing regex will tolerate white space between commas.
        String returnValue[] = properties.getProperty(key, defaultValue).split("\\s*,\\s*");

        setStringArray(key, returnValue);

        return logger.exit(returnValue);
    }

    public static void setStringArray(String key, String values[]) {
        logger.entry(key, values);

        StringBuilder mergedArray = new StringBuilder();

        for (String value : values) {
            mergedArray.append(value);
            mergedArray.append(",");
        }

        // Remove the extra comma at the end.
        if (mergedArray.length() > 0) {
            mergedArray.deleteCharAt(mergedArray.length() - 1);
        }

        properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    public static void setInetAddress(String key, InetAddress value) {
        logger.entry(key, value);

        properties.setProperty(key, value.getHostAddress());

        logger.exit();
    }

    public static InetAddress getInetAddress(String key, InetAddress defaultValue) {
        logger.entry(key, defaultValue);

        InetAddress returnValue;
        String stringValue = properties.getProperty(key, defaultValue.getHostAddress());
        try {
            returnValue = InetAddress.getByName(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be an IP address, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue.getHostAddress());
            returnValue = defaultValue;
        }

        setInetAddress(key, returnValue);

        return logger.exit(returnValue);
    }

    public static InetAddress[] getInetAddressArray(String key, InetAddress... defaultValue) {
        logger.entry(key, defaultValue);

        InetAddress returnValue[];
        String defaultValueString[] = new String[defaultValue.length];

        for(int i = 0; i < defaultValueString.length; i++) {
            defaultValueString[i] = defaultValue[i].getHostAddress();
        }

        String returnValueString[] = getStringArray(key, defaultValueString);
        returnValue = new InetAddress[returnValueString.length];

        try {
            for (int i = 0; i < returnValue.length; i++) {
                returnValue[i] = InetAddress.getByName(returnValueString[i]);
            }
        } catch (Exception e) {
            logger.error("The property '{}' should be an IP addresses, but '{}' was returned. Using the default values of '{}'", key, returnValueString, defaultValueString);
            returnValue = defaultValue;
        }

        defaultValueString = new String[returnValue.length];

        for(int i = 0; i < defaultValueString.length; i++) {
            defaultValueString[i] = returnValue[i].getHostAddress();
        }

        setStringArray(key, defaultValueString);

        return logger.exit(returnValue);
    }

    public static OSVersion getOsVersion() {
        logger.entry();
        String os = System.getProperty("os.name");

        logger.debug("OSVersion is parsing the name '{}'.", os);

        if (os.toLowerCase().contains("windows")) {

            logger.debug("OSVersion determined that '{}' is WINDOWS.", os);
            return logger.exit(OSVersion.WINDOWS);
        } else if (os.toLowerCase().contains("linux")) {

            logger.debug("OSVersion determined that '{}' is LINUX.", os);
            return logger.exit(OSVersion.LINUX);
        } else if (os.toLowerCase().contains("mac")) {

            logger.debug("OSVersion determined that '{}' is MAC.", os);
            return logger.exit(OSVersion.MAC);
        }

        logger.warn("OSVersion determined that '{}' is UNKNOWN.", os);
        return logger.exit(OSVersion.UNKNOWN);
    }

    public static void setDeviceOption(DeviceOption option) throws DeviceOptionException {
        if (option.isArray()) {
            setStringArray(option.getProperty(), option.getArrayValue());
        } else {
            setString(option.getProperty(), option.getValue());
        }
    }

    public static void mapDeviceOptions(ConcurrentHashMap<String, DeviceOption> optionsMap, DeviceOption... options) {
        for (DeviceOption option : options) {
            optionsMap.put(option.getProperty(), option);
        }
    }

    //=============================================================================================
    // Interface implementation lookups. If you create a new implementation, add it here.
    //=============================================================================================

    // I believe the if statements are faster than reflection at the sacrifice of being less
    // dynamic. Reflection is done as a last resort. We should not be constantly creating new
    // instances in any way that this performance of these methods have a substantial impact. Use
    // classes instead of strings when possible since this will highlight any any issues relating to
    // classes not being present at compile time.
    public static RTSPClient getRTSPClient(String key, String rtspClient) {
        logger.entry(key, rtspClient);

        RTSPClient returnValue;
        String clientName = properties.getProperty(key, rtspClient);

        if (clientName.endsWith(DCTRTSPClientImpl.class.getSimpleName())) {
            returnValue = new DCTRTSPClientImpl();
        } else {
            try {
                returnValue = (RTSPClient) Class.forName(clientName).newInstance();
            } catch (Exception e) {
                logger.error("The property '{}' with the value '{}' does not refer to valid RTSPClient implementation. Using default implementation '{}' => ", key, clientName, rtspClient, e);
                try {
                    returnValue = (RTSPClient) Class.forName(rtspClient).newInstance();
                } catch (Exception e1) {
                    logger.error("The default property '{}' with the value '{}' does not refer to valid RTSPClient implementation. Returning built in default 'DCTRTSPClientImpl' => ", key, clientName, rtspClient, e1);
                    returnValue = new DCTRTSPClientImpl();
                }
            }
        }

        properties.setProperty(key, returnValue.getClass().getName());

        return logger.exit(returnValue);
    }

    public static SageTVConsumer getSageTVConsumer(String key, String sageTVConsumer) {
        logger.entry(key, sageTVConsumer);

        SageTVConsumer returnValue;
        String clientName = properties.getProperty(key, sageTVConsumer);

        if (clientName.endsWith(RawSageTVConsumerImpl.class.getSimpleName())) {
            returnValue = new RawSageTVConsumerImpl();
        } else if (clientName.endsWith(FFmpegSageTVConsumerImpl.class.getSimpleName())) {
            returnValue = new FFmpegSageTVConsumerImpl();
        } else {
            try {
                returnValue = (SageTVConsumer) Class.forName(clientName).newInstance();
            } catch (Exception e) {
                logger.error("The property '{}' with the value '{}' does not refer to a valid SageTVConsumer implementation. Using default implementation '{}' => ", key, clientName, sageTVConsumer, e);
                try {
                    returnValue = (SageTVConsumer) Class.forName(sageTVConsumer).newInstance();
                } catch (Exception e1) {
                    logger.error("The default property '{}' with the value '{}' does not refer to a valid SageTVConsumer implementation. Returning built in default 'NIOSageTVRawConsumerImpl' => ", key, clientName, sageTVConsumer, e1);
                    returnValue = new RawSageTVConsumerImpl();
                }
            }
        }

        properties.setProperty(key, returnValue.getClass().getName());

        return logger.exit(returnValue);
    }

    // When getting a producer we will need to be very specific since the SageTVProducer
    // interface is just so you can plug any producer into any consumer.
    public static RTPProducer getRTProducer(String key, String rtpProducer) {
        logger.entry(key, rtpProducer);

        RTPProducer returnValue;
        String clientName = properties.getProperty(key, rtpProducer);

        if (clientName.endsWith(NIORTPProducerImpl.class.getSimpleName())) {
            returnValue = new NIORTPProducerImpl();
        } else {
            try {
                returnValue = (RTPProducer) Class.forName(clientName).newInstance();
            } catch (Exception e) {
                logger.error("The property '{}' with the value '{}' does not refer to a valid RTPProducer implementation. Using default implementation '{}' => ", key, clientName, rtpProducer, e);
                try {
                    returnValue = (RTPProducer) Class.forName(rtpProducer).newInstance();
                } catch (Exception e1) {
                    logger.error("The default property '{}' with the value '{}' does not refer to a valid RTPProducer implementation. Returning built in default 'NIORTPProducerImpl' => ", key, clientName, rtpProducer, e1);
                    returnValue = new NIORTPProducerImpl();
                }
            }
        }

        properties.setProperty(key, returnValue.getClass().getName());

        return logger.exit(returnValue);
    }

    // When getting a producer we will need to be very specific since the SageTVProducer
    // interface is just so you can plug any producer into any consumer. So far we just
    // have an RTP producer.
    public static HTTPProducer getHTTProducer(String key, String httpProducer) {
        logger.entry(key, httpProducer);

        HTTPProducer returnValue;
        String clientName = properties.getProperty(key, httpProducer);

        if (clientName.endsWith(HTTPProducerImpl.class.getSimpleName())) {
            returnValue = new HTTPProducerImpl();
        } else {
            try {
                returnValue = (HTTPProducer) Class.forName(clientName).newInstance();
            } catch (Exception e) {
                logger.error("The property '{}' with the value '{}' does not refer to a valid HTTProducer implementation. Using default implementation '{}' => ", key, clientName, httpProducer, e);
                try {
                    returnValue = (HTTPProducer) Class.forName(httpProducer).newInstance();
                } catch (Exception e1) {
                    logger.error("The default property '{}' with the value '{}' does not refer to a valid HTTProducer implementation. Returning built in default 'HTTPProducerImpl' => ", key, clientName, httpProducer, e1);
                    returnValue = new HTTPProducerImpl();
                }
            }
        }

        properties.setProperty(key, returnValue.getClass().getName());

        return logger.exit(returnValue);
    }

    /**
     * Get a free RTSP port from the pool.
     *
     * @param encoderName The name of the encoder requesting the port. This could be used later to
     *                    determine what encoder has what reservation.
     * @return The port assigned to this encoder.
     */
    public static int getFreeRTSPPort(String encoderName) {
        logger.entry();

        Random r = new Random();

        // 200 / 2 = 100 ports should be plenty.
        int lowRange = Config.getInteger("rtsp.port_low", 8300);
        int highRange = Config.getInteger("rtsp.port_high", 8500);

        // This must return an even number because RTSP needs a port range even
        // though it always seems to use the first port.
        int returnPort = (r.nextInt(((highRange - lowRange) / 2)) * 2) + lowRange;

        synchronized (getRTSPPort) {

            String mapValue = rtspPortMap.get(returnPort);

            if (mapValue != null) {
                returnPort = 0;
                for (int i = lowRange; i < highRange; i += 2) {
                    if ((mapValue = rtspPortMap.get(i)) == null) {
                        returnPort = i;
                        break;
                    }
                }
            }

            // Store it if it's not zero. If we get a zero as a return value, we
            // can choose how to handle that in the method requesting the port
            // number.
            if (returnPort > 0) {
                rtspPortMap.put(returnPort, encoderName);
            }
        }

        return logger.exit(returnPort);
    }

    /**
     * Returns a reserved RTSP port back to the pool.
     *
     * @param returnPort The port number to return.
     */
    public static void returnFreeRTSPPort(int returnPort) {
        synchronized (getRTSPPort) {
            rtspPortMap.remove(returnPort);
        }
    }

    /**
     * Returns all referenced socket server ports in properties.
     *
     * @return An array of all socket server ports.
     */
    public static int[] getAllSocketServerPorts() {
        HashSet<Integer> ports = new HashSet<>();

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String)entry.getKey();
            String value = (String)entry.getValue();

            if (key.startsWith("sagetv.device.") && key.endsWith(".encoder_listen_port")) {
                try {
                    ports.add(Integer.valueOf(value));
                } catch (NumberFormatException e) {
                    logger.error("The property '{}' with the value '{}' does not contain a valid integer.", key, value);
                }
            }
        }

        int returnPorts[] = new int[ports.size()];
        int counter = 0;
        for (Integer socket : ports) {
            returnPorts[counter++] = socket;
        }

        return returnPorts;
    }

    /**
     * Checks if new device parent name is unique and then sets the new name if it is unique.
     * <p/>
     * This is synchronized to prevent a race condition whereby multiple requests could technically
     * get through and cause a name to not be unique.
     *
     * @param parentId This is the the unique id for the parent device to change.
     * @param newName This is the desired new name for the parent device.
     * @return <i>true</i> if the name was unique and actually changed.
     */
    public static synchronized boolean setUniqueParentName(int parentId, String newName) {
        boolean returnValue = true;
        String oldName = "No Name";
        newName = newName.trim();

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String)entry.getKey();
            String value = (String)entry.getValue();

            if (key.equals("sagetv.device.parent." + parentId + ".device_name")) {
                oldName = value;
            } else if (key.startsWith("sagetv.device.parent.") && key.endsWith(".device_name")) {
                if (value.equals(newName)) {
                    logger.error("The desired new name '{}' for parent id {} conflicts with the" +
                            " parent property '{}'. New parent name not set.",
                            newName, parentId, key);

                    returnValue = false;
                }
            }
        }

        if (returnValue) {
            setString("sagetv.device.parent." + parentId + ".device_name", newName);
            logger.info("Renamed the parent id {} from '{}' to '{}'.", parentId, oldName, newName);
        }

        return returnValue;
    }

    /**
     * Checks if new device name is unique and then sets the new name if it is unique.
     * <p/>
     * This is synchronized to prevent a race condition whereby multiple requests could technically
     * get through and cause a name to not be unique.
     *
     * @param deviceId This is the the unique id for the parent device to change.
     * @param newName This is the desired new name for the parent device.
     * @return <i>true</i> if the name was unique and actually changed.
     */
    public static synchronized boolean setUniqueDeviceName(int deviceId, String newName) {
        boolean returnValue = true;
        String oldName = "No Name";
        newName = newName.trim();

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String)entry.getKey();
            String value = (String)entry.getValue();

            if (key.equals("sagetv.device." + deviceId + ".device_name")) {
                oldName = value;
            } else if (key.startsWith("sagetv.device.") && key.endsWith(".device_name")) {
                if (value.equals(newName)) {
                    logger.error("The desired new name '{}' for device id {} conflicts with the" +
                                    " capture device property '{}'. New device name not set.",
                            newName, deviceId, key);

                    returnValue = false;
                }
            }
        }

        if (returnValue) {
            setString("sagetv.device." + deviceId + ".device_name", newName);
            logger.info("Renamed the parent id {} from '{}' to '{}'.", deviceId, oldName, newName);
        }

        return returnValue;
    }

    /**
     * Returns a valid socket server port for the requested uniqueID.
     * <p/>
     * The value of encoder_listen_port is generated.
     *
     * @param uniqueID This is the unique id of the capture device.
     * @param encoderLevel Provide an encoder level for this server socket. This is used to
     *                     determine if the socket can be shared or not.
     * @return Returns an available socket server port.
     */
    public static int getSocketServerPort(int uniqueID, String encoderLevel) {
        logger.entry(uniqueID, encoderLevel);

        int returnValue;

        // We want to make sure that nothing happens involving port assignment while this is running.
        synchronized (getSocketServerPort) {
            returnValue = getInteger("sagetv.device." + String.valueOf(uniqueID) + ".encoder_listen_port", 0);

            // This will set the value in the properties file and allow you to change it if you see fit.
            encoderLevel = getString("sagetv.device." + String.valueOf(uniqueID) + ".encoder_level", encoderLevel);

            if (returnValue != 0) {
                return logger.exit(returnValue);
            }

            boolean newDeviceIncrement = Config.getBoolean("sagetv.new.device.increment_port", false);
            if (encoderLevel.startsWith("1")) {
                logger.debug("Encoder does not implement network encoder version 3.0. sagetv.new.device.increment_port is being ignored since lower protocols can't share a port.");

                // Encoders lower than 2.0 must have their own ports.
                newDeviceIncrement = true;
            }

            // 99 ports, plus 1 port dedicated to sharing should be plenty.
            int sharedPort = Config.getInteger("sagetv.new.device.socket_server_shared_port", 9000);
            int lowRange = Config.getInteger("sagetv.new.device.socket_server_port_low", 9001);
            int highRange = Config.getInteger("sagetv.new.device.socket_server_port_high", 9100);

            // In an effort to be consistent, we will scale the integer range down to the low/high range.
            // This is checked later for conflicts since obviously scaling from a range of 0 to INTEGER_MAX
            // to a range of 0-100 is bound to decrease entropy significantly.
            if (newDeviceIncrement) {
                returnValue = (((highRange - lowRange) / (Integer.MAX_VALUE - Math.abs(uniqueID))) * uniqueID) + lowRange;
            } else {
                setInteger("sagetv.device." + String.valueOf(uniqueID) + ".encoder_listen_port", sharedPort);
                return logger.exit(sharedPort);
            }

            // Check the configuration file for other port configurations before assuming this one can be used.
            Enumeration keyNames = properties.propertyNames();
            ArrayList<Integer> portArray = new ArrayList<>();

            while (keyNames.hasMoreElements()) {
                String currentKey = (String) keyNames.nextElement();
                if (currentKey.startsWith("sagetv.device.") && currentKey.endsWith(".encoder_listen_port")) {
                    int getValue = getInteger(currentKey, 0);

                    if (getValue != 0) {
                        portArray.add(getValue);
                    }
                }
            }

            for (int i = 0; i < portArray.size(); i++) {
                if (portArray.get(i) != 0) {
                    if (portArray.get(i) == returnValue) {
                        if (lowRange > highRange) {
                            // We don't have any more ports available. If zero
                            // is used, it will do an automatic port assignment
                            // which is better than nothing.
                            return logger.exit(0);
                        }

                        // Keep re-iterating through the array until we don't have a match.
                        returnValue = lowRange++;
                        i = 0;
                    }
                }
            }

            setInteger("sagetv.device." + String.valueOf(uniqueID) + ".encoder_listen_port", returnValue);
        }
        return logger.exit(returnValue);
    }

    public static void setShutdown() {
        isShutdown = true;
    }

    public static boolean isShutdown() {
        return isShutdown;
    }
}
