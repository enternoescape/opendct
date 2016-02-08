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
import opendct.config.options.IntegerDeviceOption;
import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetAddress;
import java.util.*;

public class ConfigBag {
    private static final Logger logger = LogManager.getLogger(ConfigBag.class);

    public final String CONFIG_NAME;
    public final String CONFIG_CATEGORY;
    public final String FILE_NAME;
    public final String DIR_NAME;
    private final Properties properties;
    private final boolean setOnGet;

    public ConfigBag(String configName, boolean setOnGet) {
        CONFIG_NAME = configName;
        CONFIG_CATEGORY = "";
        FILE_NAME = Config.getConfigDirectory() + Config.DIR_SEPARATOR + configName + ".properties";
        DIR_NAME = Config.getConfigDirectory();
        this.setOnGet = setOnGet;
        properties = new Properties();
    }

    public ConfigBag(String configName, boolean setOnGet, Properties properties) {
        CONFIG_NAME = configName;
        CONFIG_CATEGORY = "";
        FILE_NAME = Config.getConfigDirectory() + Config.DIR_SEPARATOR + configName + ".properties";
        DIR_NAME = Config.getConfigDirectory();
        this.setOnGet = setOnGet;
        this.properties = properties;
    }

    public ConfigBag(String configName, String category, boolean setOnGet) {
        CONFIG_NAME = configName;
        CONFIG_CATEGORY = category;
        DIR_NAME = Config.getConfigDirectory() + Config.DIR_SEPARATOR + category;
        FILE_NAME = Config.getConfigDirectory() + Config.DIR_SEPARATOR + category +
                Config.DIR_SEPARATOR + configName + ".properties";


        this.setOnGet = setOnGet;
        properties = new Properties();
    }

    public ConfigBag(String configName, String category, boolean setOnGet, Properties properties) {
        CONFIG_NAME = configName;
        CONFIG_CATEGORY = category;
        DIR_NAME = Config.getConfigDirectory() + Config.DIR_SEPARATOR + category;
        FILE_NAME = Config.getConfigDirectory() + Config.DIR_SEPARATOR + category +
                Config.DIR_SEPARATOR + configName + ".properties";

        this.setOnGet = setOnGet;
        this.properties = properties;
    }

    /**
     * Loads the configuration from the pre-defined configuration directory.
     * <p/>
     * The filename is created based on the value of <b>CONFIG_NAME</b>.
     *
     * @return <i>true</i> if the configuration was loaded successfully or if the configuration is new.
     */
    public synchronized boolean loadConfig() {
        logger.entry();

        if (Config.getConfigDirectory() == null) {
            logger.fatal("The configuration directory must be defined before any properties can be loaded.");
            return logger.exit(false);
        } else if (!Util.createDirectory(DIR_NAME)) {
            logger.fatal("Unable to create required directories.");
            return logger.exit(false);
        }

        if (new File(FILE_NAME).exists()) {
            FileInputStream fileInputStream;
            try {
                fileInputStream = new FileInputStream(FILE_NAME);
            } catch (FileNotFoundException e) {
                logger.fatal("Unable to open the configuration file '{}' => {}", FILE_NAME, e);
                return logger.exit(false);
            }

            properties.clear();

            try {
                properties.load(fileInputStream);
            } catch (IOException e) {
                logger.fatal("Unable to read the configuration file '{}' => {}", FILE_NAME, e);
                return logger.exit(false);
            }
        } else {
            logger.info("'{}' was not found. A new configuration file will be created with that name on the next save.", FILE_NAME);
        }

        return logger.exit(true);
    }

    /**
     * Clears the current configuration.
     */
    public synchronized void clearConfig() {
        logger.entry();

        logger.debug("Clearing '{}' configuration...", CONFIG_NAME);
        properties.clear();

        logger.exit();
    }

    /**
     * Sorts the properties alphabetically, then saves them.
     *
     * @return <i>true</i> if the properties were successfully saved.
     */
    public synchronized boolean saveConfig() {
        logger.entry();

        if (Config.getConfigDirectory() == null) {
            logger.fatal("The configuration directory must be defined before any properties can be saved.");
            return logger.exit(false);
        } else if (!Util.createDirectory(DIR_NAME)) {
            logger.fatal("Unable to create required directories.");
            return logger.exit(false);
        }

        File file = new File(FILE_NAME);
        File fileBackup = new File(FILE_NAME + ".backup");
        try {
            Util.copyFile(file, fileBackup, true);
        } catch (IOException e) {
            file.renameTo(fileBackup);
        }

        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(FILE_NAME);
        } catch (FileNotFoundException e) {
            logger.error("Unable to open the configuration file '{}' => {}", FILE_NAME, e);
            return logger.exit(false);
        }

        try {
            Properties sortedProperties = new Properties() {
                @Override
                public synchronized Enumeration<Object> keys() {
                    return Collections.enumeration(new TreeSet<Object>(super.keySet()));
                }
            };
            sortedProperties.putAll(properties);

            sortedProperties.store(fileOutputStream, CONFIG_NAME + " Configuration File");
            fileOutputStream.close();
        } catch (IOException e) {
            logger.error("Unable to write the configuration file '{}' => {}", FILE_NAME, e);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    // This will be used to set all string properties so we can do trace logging if there is any
    // configuration related weirdness.
    public void setString(String key, String value) {
        logger.entry(key, value);

        properties.setProperty(key, value);

        logger.exit();
    }

    // This will be used to request all string properties so we can do trace logging if there is
    // any configuration related weirdness.
    public String getString(String key, String defaultValue) {
        logger.entry(key, defaultValue);

        String returnValue = properties.getProperty(key, defaultValue);

        if (setOnGet) {
            setString(key, returnValue);
        }

        return logger.exit(returnValue);
    }


    public String getString(String key) {
        logger.entry(key);

        String returnValue = properties.getProperty(key);

        return logger.exit(returnValue);
    }

    public void setBoolean(String key, boolean value) {
        logger.entry(key, value);

        properties.setProperty(key, Boolean.toString(value));

        logger.exit();
    }

    public void setShort(String key, short value) {
        logger.entry(key, value);

        properties.setProperty(key, Short.toString(value));

        logger.exit();
    }

    public void setInteger(String key, int value) {
        logger.entry(key, value);

        properties.setProperty(key, Integer.toString(value));

        logger.exit();
    }

    public void setLong(String key, long value) {
        logger.entry(key, value);

        properties.setProperty(key, Long.toString(value));

        logger.exit();
    }

    public void setFloat(String key, float value) {
        logger.entry(key, value);

        properties.setProperty(key, Float.toString(value));

        logger.exit();
    }

    public void setDouble(String key, double value) {
        logger.entry(key, value);

        properties.setProperty(key, Double.toString(value));

        logger.exit();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        logger.entry(key, defaultValue);

        boolean returnValue = defaultValue;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        try {
            returnValue = Boolean.valueOf(stringValue.toLowerCase());
        } catch (Exception e) {
            logger.error("The property '{}' should be boolean, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        if (setOnGet) {
            setBoolean(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    public short getShort(String key, short defaultValue) {
        logger.entry(key, defaultValue);

        short returnValue = 0;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        try {
            returnValue = Short.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a short, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        if (setOnGet) {
            setShort(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    public int getInteger(String key, int defaultValue) {
        logger.entry(key, defaultValue);

        int returnValue = 0;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        ;
        try {
            returnValue = Integer.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be an integer, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        if (setOnGet) {
            setInteger(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    public long getLong(String key, long defaultValue) {
        logger.entry(key, defaultValue);

        long returnValue = 0;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        ;
        try {
            returnValue = Long.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a long, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        if (setOnGet) {
            setLong(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    public float getFloat(String key, float defaultValue) {
        logger.entry(key, defaultValue);

        float returnValue = 0;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        ;
        try {
            returnValue = Float.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a float, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        if (setOnGet) {
            setFloat(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    public double getDouble(String key, double defaultValue) {
        logger.entry(key, defaultValue);

        double returnValue = 0;
        String stringValue = properties.getProperty(key, String.valueOf(defaultValue));
        ;
        try {
            returnValue = Double.valueOf(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be a float, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue);
            returnValue = defaultValue;
        }

        if (setOnGet) {
            setDouble(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    public String[] getStringArray(String key, String... defaultValues) {
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
                mergedArray.append(value + ",");
            }

            // Remove the extra comma at the end.
            if (mergedArray.length() > 0) {
                mergedArray.deleteCharAt(mergedArray.length() - 1);
            }

            if (setOnGet) {
                properties.setProperty(key, mergedArray.toString());
            }

            returnValue = defaultValues;
        }

        return logger.exit(returnValue);
    }

    /**
     * Gets a comma separated string array.
     *
     * @param key          This is the property name.
     * @param defaultValue This is the default value to be returned if the property does not already
     *                     exist.
     * @return This will return an empty array if the property is present, but does not contain
     * anything. This will return <i>null</i> only if the property does not already exist
     * and <b>defaultValue</b> was set to <i>null</i>.
     */
    public String[] getStringArray(String key, String defaultValue) {
        logger.entry(key, defaultValue);

        String stringValue = properties.getProperty(key, defaultValue);
        String returnValue[] = new String[0];

        if (stringValue != null) {
            if (!stringValue.equals("")) {
                // The parsing regex will tolerate white space between commas.
                returnValue = stringValue.split("\\s*,\\s*");
            }
        } else if (defaultValue == null) {
            returnValue = null;
        }

        if (setOnGet) {
            if (returnValue != null) {
                setStringArray(key, returnValue);
            } else {
                setStringArray(key, "");
            }
        }

        return logger.exit(returnValue);
    }

    public void setStringArray(String key, String... values) {
        logger.entry(key, values);

        StringBuilder mergedArray = new StringBuilder();

        for (String value : values) {
            mergedArray.append(value + ",");
        }

        // Remove the extra comma at the end.
        if (mergedArray.length() > 0) {
            mergedArray.deleteCharAt(mergedArray.length() - 1);
        }

        properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    public void setIntegerArray(String key, int... values) {
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

        properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    public void setIntegerArray(String key, Integer... values) {
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

        properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    /**
     * This method will convert numbers with commas and hyphens into an array with all of the
     * in-between numbers added.
     *
     * @param key           This is the property name.
     * @param defaultValues This is returns if this property is not yet defined.
     * @return The fully expanded integer array.
     */
    public int[] getIntegerRanges(String key, int... defaultValues) {
        logger.entry(key, defaultValues);

        int returnValue[] = defaultValues;

        // Be sure to use the getStringArray() method that supports null.
        String values[] = getStringArray(key, (String) null);

        boolean errors = false;

        if (values != null) {
            ArrayList<Integer> intArray = new ArrayList<Integer>();

            for (String value : values) {

                // Ensure that the hyphen is not just the start of a negative number.
                if (value.lastIndexOf("-") > 0) {
                    // The parsing regex will tolerate white space between the integers and the hyphen and allows for negative numbers.
                    String split[] = value.split("[0-9]*\\s*-\\s*[-0-9]*");

                    // You could have multiple hyphens, but why?
                    if (split.length > 1) {
                        int start;
                        int end;

                        try {
                            start = Integer.valueOf(split[0]);
                        } catch (Exception e) {
                            logger.error("The property '{}' should be an integer range beginning, but '{}' was returned. Using the default value of '{}'", key, split[0], defaultValues);
                            errors = true;
                            break;
                        }

                        try {
                            end = Integer.valueOf(split[split.length - 1]);
                        } catch (Exception e) {
                            logger.error("The property '{}' should be an integer range ending, but '{}' was returned. Using the default value of '{}'", key, split[split.length - 1], defaultValues);
                            errors = true;
                            break;
                        }

                        if (start > end) {
                            errors = true;
                            break;
                        }

                        // The range is inclusive.
                        for (int i = start; i <= end; i++) {
                            intArray.add(i);
                        }
                    } else {
                        logger.error("The property '{}' should be an integer range, but '{}' was returned. Using the default value of '{}'", key, value, defaultValues);
                        errors = true;
                        break;
                    }
                } else {
                    try {
                        intArray.add(Integer.valueOf(value));
                    } catch (Exception e) {
                        logger.error("The property '{}' should be an integer, but '{}' was returned. Using the default value of '{}'", key, value, defaultValues);
                        errors = true;
                        break;
                    }
                }
            }

            returnValue = new int[intArray.size()];

            for (int i = 0; i < returnValue.length; i++) {
                returnValue[i] = intArray.get(i);
            }
        }

        if (errors) {
            returnValue = defaultValues;
        }

        if (setOnGet) {
            setIntegerRanges(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    /**
     * This method will sort an array of integers and replace contiguous ranges with the first and
     * last number of the range with a hyphen in-between.
     * <p/>
     * Ex. 507,508,509,513 becomes 507-509,513.
     *
     * @param key    This is the property name.
     * @param values The fully expanded integer array.
     */
    public void setIntegerRanges(String key, int... values) {
        StringBuilder mergedArray = new StringBuilder();

        Arrays.sort(values);

        for (int i = 0; i < values.length; i++) {
            int start = values[i];
            int end = values[i];
            int index = 0;

            while (start + index == values[i + index]) {
                end = values[i + index++];

                if (i + index > values.length) {
                    break;
                }
            }

            // If the ranges are more than two integers apart, use a hyphen otherwise it looks a
            // little odd.
            if (end - start > 1) {
                mergedArray.append(start + "-" + end + ",");
                i += end - start;
            } else {
                mergedArray.append(start + ",");
            }
        }

        if (mergedArray.length() > 0) {
            mergedArray.deleteCharAt(mergedArray.length() - 1);
        }

        properties.setProperty(key, mergedArray.toString());
    }

    public void setLongArray(String key, long... values) {
        logger.entry(key, values);

        StringBuilder mergedArray = new StringBuilder();

        for (long value : values) {
            mergedArray.append(Long.toString(value));
            mergedArray.append(",");
        }

        // Remove the extra comma at the end.
        if (mergedArray.length() > 0) {
            mergedArray.deleteCharAt(mergedArray.length() - 1);
        }

        properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    public void setLongArray(String key, Long... values) {
        logger.entry(key, values);

        StringBuilder mergedArray = new StringBuilder();

        for (Long value : values) {
            mergedArray.append(Long.toString(value));
            mergedArray.append(",");
        }

        // Remove the extra comma at the end.
        if (mergedArray.length() > 0) {
            mergedArray.deleteCharAt(mergedArray.length() - 1);
        }

        properties.setProperty(key, mergedArray.toString());

        logger.exit();
    }

    public void setInetAddress(String key, InetAddress value) {
        logger.entry(key, value);

        properties.setProperty(key, value.getHostAddress());

        logger.exit();
    }

    public InetAddress getInetAddress(String key, InetAddress defaultValue) {
        logger.entry(key, defaultValue);

        InetAddress returnValue = null;
        String stringValue = properties.getProperty(key, defaultValue.getHostAddress());
        try {
            returnValue = InetAddress.getByName(stringValue);
        } catch (Exception e) {
            logger.error("The property '{}' should be an IP address, but '{}' was returned. Using the default value of '{}'", key, stringValue, defaultValue.getHostAddress());
            returnValue = defaultValue;
        }

        if (setOnGet) {
            setInetAddress(key, returnValue);
        }

        return logger.exit(returnValue);
    }

    public InetAddress[] getInetAddressArray(String key, InetAddress... defaultValue) {
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

        if (setOnGet) {
            defaultValueString = new String[returnValue.length];

            for(int i = 0; i < defaultValueString.length; i++) {
                defaultValueString[i] = returnValue[i].getHostAddress();
            }

            setStringArray(key, defaultValueString);
        }

        return logger.exit(returnValue);
    }

    public void setDeviceOption(DeviceOption option) throws DeviceOptionException {
        if (option.isArray()) {
            setStringArray(option.getProperty(), option.getArrayValue());
        } else {
            setString(option.getProperty(), option.getValue());
        }

        switch (option.getType()) {
            case INTEGER:
                if (option.isArray()) {
                    if (option instanceof IntegerDeviceOption) {
                        setIntegerRanges(option.getProperty(), ((IntegerDeviceOption) option).getIntegerArray());
                    } else {
                        setStringArray(option.getProperty(), option.getArrayValue());
                    }
                } else {
                    setString(option.getProperty(), option.getValue());
                }
                break;
            default:
                if (option.isArray()) {
                    setStringArray(option.getProperty(), option.getArrayValue());
                } else {
                    setString(option.getProperty(), option.getValue());
                }
                break;
        }
    }

    /**
     * Returns all properties with a common root key as a HashMap.
     * <p/>
     * The root key will be removed from the returned key values in the returned HashMap.
     *
     * @param rootKey This is the root value to be searched for in properties. If you do not want
     *                all of the returned keys to start with a period (.), you must include the
     *                period in this parameter.
     * @return Returns a HashMap containing all of the values found. If no values were found, the
     * HashMap will be empty.
     */
    public HashMap<String, String> getAllByRootKey(String rootKey) {

        HashMap<String, String> returnValue = new HashMap<String, String>();
        Enumeration names = properties.propertyNames();

        while (names.hasMoreElements()) {
            String key = (String) names.nextElement();
            if (key.startsWith(rootKey)) {
                String value = properties.getProperty(key);

                returnValue.put(key.substring(rootKey.length()), value);
            }
        }

        return returnValue;
    }

    /**
     * Removes all properties with a common root key.
     *
     * @param rootKey This is the root value to use for removal.
     */
    public void removeAllByRootKey(String rootKey) {

        ArrayList<String> removes = new ArrayList<String>();
        Enumeration names = properties.propertyNames();

        while (names.hasMoreElements()) {
            String key = (String) names.nextElement();

            if (key.startsWith(rootKey)) {
                removes.add(key);
            }
        }

        for(String remove : removes) {
            properties.remove(remove);
        }
    }
}
