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

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandLine {
    private static final Logger logger = LogManager.getLogger(CommandLine.class);

    private static HelpFormatter helpFormatter = new HelpFormatter();
    private static CommandLineParser commandLineParser = new DefaultParser();
    private static Options options = new Options();
    private static org.apache.commons.cli.CommandLine commandLine = null;

    private static String configDir = null;
    private static boolean configOnly = false;
    private static long runSeconds = -1;
    private static boolean daemon = false;
    private static String logDir = null;
    private static boolean logToConsole = false;
    private static String logUpnpLogLevel = "severe";
    private static boolean logUpnpToConsole = false;
    private static boolean suspendTest = false;

    private static Options generateCommandLineOptions() {
        Options newOptions = new Options();
        newOptions.addOption("h", "help", false, "Display this help screen.");
        newOptions.addOption("c", "config-dir", true, "Specify directory to use for the" +
                " configuration files. (default: current jar directory.");

        newOptions.addOption("C", "config-only", false, "This will run a discovery, then save the" +
                " generated configuration. (requires --run-seconds to specify the length of the" +
                " discovery");
        newOptions.addOption("s", "run-seconds", true, "Specify a time in seconds to run before" +
                " quiting.");

        newOptions.addOption("S", "suspend-test", false, "Test the suspend feature without" +
                " actually suspending the computer. This does nothing in daemon/service mode.");

        newOptions.addOption("D", "daemon", false, "Starts in daemon/service mode.");

        newOptions.addOption("l", "log-dir", true, "Sets location of the log files." +
                " (default: current jar directory). To avoid warnings about a directory not" +
                " existing at the start of the program, set the environment variable" +
                " 'opendct_log_root'. 'opendct_log_root' will always override this parameter.");
        newOptions.addOption("L", "log-to-console", true, "Output log information to the console." +
                " (default: false) (true,false)");
        newOptions.addOption("U", "log-upnp-level", false, "Sets logging level for Cling UPnP." +
                " (default: severe) (off,severe,warning,info,fine,finer,finest)");
        newOptions.addOption("u", "log-upnp-to-console", true, "Output Cling UPnP log information" +
                " to the console. (default: false) (true,false)");

        return newOptions;
    }

    public static boolean parseCommandLineOptions(String args[]) {
        logger.entry();

        options = CommandLine.generateCommandLineOptions();

        boolean returnValue = false;

        try {
            commandLine = commandLineParser.parse(options, args);
        } catch (ParseException e) {
            logger.fatal("Unable to parse command line arguments => {}{}", Config.NEW_LINE, Config.NEW_LINE, e);
            helpFormatter.printHelp("java -jar opendct-" + Config.VERSION_PROGRAM + ".jar", options, true);
            return logger.exit(false);
        }

        if (hasOption("help")) {
            helpFormatter.printHelp("java -jar opendct-" + Config.VERSION_PROGRAM + ".jar", options, true);
            return logger.exit(false);
        }

        // Check for any parameters missing other needed parameters.
        if (hasOption("config-only") && !hasOption("run-seconds")) {
            logger.fatal("Config only requires the parameter --run-seconds to be defined.");
            helpFormatter.printHelp("java -jar opendct-" + Config.VERSION_PROGRAM + ".jar", options, true);
            return logger.exit(false);
        }

        //Assign values
        try {
            configDir = CommandLine.getOption("config-dir", System.getProperty("user.dir"));
            configOnly = hasOption("config-only");
            if (hasOption("run-seconds")) {
                runSeconds = Long.getLong(commandLine.getOptionValue("run-seconds"));
            }
            daemon = hasOption("daemon");
            suspendTest = hasOption("suspend-test");

            if (System.getProperty("opendct_log_root") == null) {
                logDir = CommandLine.getOption("log-dir", System.getProperty("user.dir"));
                System.setProperty("opendct_log_root", logDir);

                logger.info("To avoid log4j2 warnings when OpenDCT starts, set the Java" +
                        " environment variable 'opendct_log_root' to the desired logging path.");
            } else {
                logDir = System.getProperty("opendct_log_root");
            }

            logToConsole = CommandLine.getOption("log-to-console", "false").equals("true");
            logUpnpLogLevel = CommandLine.getOption("log-upnp-level", "severe");
            logUpnpToConsole = CommandLine.getOption("log-upnp-to-console", "false").equals("true");


        } catch (Exception e) {
            logger.fatal("Unable to parse command line argument values => {}{}", Config.NEW_LINE, Config.NEW_LINE, e);
            helpFormatter.printHelp("java -jar yadct.jar", options, true);
            return logger.exit(false);
        }

        return logger.exit(true);
    }

    // Hierarchy:
    // Command Prompt > Properties > Defaults
    public static String getOption(String key, String defaultValue) {
        logger.entry();

        String configProperty = Config.getString(key);

        if (commandLine != null) {
            if (configProperty != null) {
                // Return the configuration value if not defined by parameter.
                return commandLine.getOptionValue(key, configProperty);
            } else {
                return commandLine.getOptionValue(key, defaultValue);
            }
        } else if (configProperty != null) {
            return logger.exit(configProperty);
        }

        return logger.exit(defaultValue);
    }

    public static boolean hasOption(String key) {
        logger.entry();
        if (commandLine != null) {
            return logger.exit(commandLine.hasOption(key));
        }
        return logger.exit(false);
    }

    public static String getConfigDir() {
        return configDir;
    }

    public static boolean isConfigOnly() {
        return configOnly;
    }

    public static long getRunSeconds() {
        return runSeconds;
    }

    public static boolean isDaemon() {
        return daemon;
    }

    public static String getLogDir() {
        return logDir;
    }

    public static boolean isLogToConsole() {
        return logToConsole;
    }

    public static String getLogUpnpLogLevel() {
        return logUpnpLogLevel;
    }

    public static boolean isLogUpnpToConsole() {
        return logUpnpToConsole;
    }

    public static boolean isSuspendTest() {
        return suspendTest;
    }
}
