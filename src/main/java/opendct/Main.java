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

package opendct;

import com.sun.beans.finder.ConstructorFinder;
import javafx.beans.binding.ListBinding;
import opendct.channel.ChannelManager;
import opendct.config.CommandLine;
import opendct.config.Config;
import opendct.config.ExitCode;
import opendct.power.NetworkPowerEventManger;
import opendct.power.PowerMessageManager;
import opendct.sagetv.SageTVManager;
import opendct.sagetv.SageTVPoolManager;
import opendct.tuning.hdhomerun.HDHomeRunManager;
import opendct.tuning.upnp.UpnpManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting OpenDCT {}...", Config.VERSION);

        Runtime.getRuntime().addShutdownHook(new Thread("Shutdown") {
            @Override
            public void run() {
                shutdown();
            }
        });

        if (!CommandLine.parseCommandLineOptions(args)) {
            // The method will automatically print out the valid parameters if there is a
            // problem and then return false.
            logger.exit();
            ExitCode.PARAMETER_ISSUE.terminateJVM();
            return;
        }

        if (!Config.setConfigDirectory(CommandLine.getConfigDir())) {
            logger.exit();
            ExitCode.CONFIG_DIRECTORY.terminateJVM();
            return;
        }

        File restartFile = new File(Config.getConfigDirectory() + Config.DIR_SEPARATOR + "restart");
        if (restartFile.exists()) {
            if (!restartFile.delete()) {
                logger.error("Unable to delete the file '{}'.", restartFile.getName());
            }
        }

        if (!Config.loadConfig()) {
            logger.exit();
            ExitCode.CONFIG_ISSUE.terminateJVM();
            return;
        }

        //==========================================================================================
        // This is all of the log4j2 runtime configuration.
        //==========================================================================================

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        final LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);

        ctx.reconfigure();

        // Turn off console logging. With this turned off, all you will might see is that the
        // program started.
        if (CommandLine.isLogToConsole()) {
            if (config.getConfigurationSource().getFile() != null) {
                try {
                    System.out.close();
                    logger.info("Console logging is disabled.");
                } catch (Exception e) {
                    logger.error("There was an unexpected exception while trying to turn off console logging => ", e);
                }
            } else {
                logger.info("Unable to turn off console due to missing configuration.");
            }
        }

        logger.info("OpenDCT logging to the directory '{}'.", CommandLine.getLogDir());

        // This takes care of everything to do with logging from cling. The default configuration
        // only reports severe errors.
        UpnpManager.configureUPnPLogging();

        // I think this should be turned on by default for now so it actually gets tested.
        boolean enablePowerManagement = Config.getBoolean("pm.enabled", true);

        if (enablePowerManagement) {
            if (!PowerMessageManager.EVENTS.startPump()) {
                // Save the configuration data as it is so the pm.enabled property will already be
                // in the file.
                Config.saveConfig();
                System.exit(ExitCode.PM_INIT.CODE);
                ExitCode.PM_INIT.terminateJVM(
                        "If you do not want to use power state features, set pm.enabled=false in '" +
                                Config.getDefaultConfigFilename() + "'");
                return;
            }

            Runtime.getRuntime().addShutdownHook(new Thread("PowerMessageManagerShutdown") {
                @Override
                public void run() {
                    logger.info("Stopping power messages...");
                    PowerMessageManager.EVENTS.stopPump();
                }
            });
        }

        // This will enable us to wait for the network to become available first after a standby
        // event.
        PowerMessageManager.EVENTS.addListener(NetworkPowerEventManger.POWER_EVENT_LISTENER);

        // This will place the SageTVManager second in the queue for standby events. It will prevent
        // any further communication from SageTV that might activate the capture devices when we are
        // trying to stop them.
        PowerMessageManager.EVENTS.addListener(SageTVManager.POWER_EVENT_LISTENER);

        Runtime.getRuntime().addShutdownHook(new Thread("SageTVManagerShutdown") {
            @Override
            public void run() {
                logger.info("Stopping all SageTV socket servers...");
                SageTVManager.stopAllSocketServers();
                SageTVManager.stopAndClearAllCaptureDevices();
            }
        });

        // This will place the ChannelManager third in the queue for standby events. It will prevent
        // offline scanning from further interaction with the capture devices when we are trying to
        // stop them.
        PowerMessageManager.EVENTS.addListener(ChannelManager.POWER_EVENT_LISTENER);

        // This loads all of the currently saved channel lineups from the lineups folder.
        ChannelManager.loadChannelLineups();

        Runtime.getRuntime().addShutdownHook(new Thread("ChannelManagerShutdown") {
            @Override
            public void run() {
                logger.info("Stopping all channel update threads...");
                ChannelManager.stopAllOfflineScansAndWait();
                ChannelManager.saveChannelLineups();
            }
        });

        // This starts the timer for all of the capture devices to be loaded. The default timeout is
        // 30 seconds. The default device count is 0. These values are saved after the first run and
        // can be changed after stopping the program.
        SageTVManager.startWaitingForCaptureDevices();

        // When this is set to true, all new devices will receive the same communication port
        // number. This is intelligently handled when SageTVManager creates instances of
        // SageTVSocketServer. When a new instance is being added, if it shares the same port
        // number as an instance that has already started, it will just be added to that
        // instances pool of capture devices.
        boolean incrementPortForNewDevices = Config.getBoolean("sagetv.new.device.increment_port", false);

        int sageTVdefaultEncoderMerit = Config.getInteger("sagetv.new.device.default_encoder_merit", 0);
        int sageTVdefaultTuningDelay = Config.getInteger("sagetv.new.device.default_tuning_delay", 0);

        int sageTVdefaultDiscoveryPort = Config.getInteger("sagetv.encoder_discovery_port", 8271);

        // Currently the program doesn't do much without this part, but in the future we might have
        // a capture device that doesn't use UPnP so we would want it disabled if we don't need it.
        boolean useUPnP = Config.getBoolean("upnp.enabled", true);

        // If this is enabled, this will discover for HDHomeRun devices. At the moment this won't
        // actually create any devices based on discovery, but it will find devices and prevent
        // duplicates.
        boolean useHDHR = Config.getBoolean("hdhr.enabled", false);

        Config.saveConfig();

        if (useUPnP) {
            UpnpManager.startUpnpServices();

            PowerMessageManager.EVENTS.addListener(UpnpManager.POWER_EVENT_LISTENER);

            Runtime.getRuntime().addShutdownHook(new Thread("UpnpManagerShutdown") {
                @Override
                public void run() {
                    logger.info("Stopping UPnP services...");
                    UpnpManager.stopUpnpServices();
                }
            });
        }

        if (useHDHR) {
            HDHomeRunManager.startDeviceDetection();

            PowerMessageManager.EVENTS.addListener(HDHomeRunManager.POWER_EVENT_LISTENER);

            Runtime.getRuntime().addShutdownHook(new Thread("HDHomeRunManagerShutdown") {
                @Override
                public void run() {
                    logger.info("Stopping HDHomeRun services...");
                    HDHomeRunManager.removeAllDevices();
                }
            });
        }

        // Don't proceed until we have every required device loaded.
        SageTVManager.blockUntilCaptureDevicesLoaded();

        boolean channelUpdates = Config.getBoolean("channels.update", true);

        if (channelUpdates) {
            ChannelManager.startUpdateChannelsThread();
            PowerMessageManager.EVENTS.addListener(ChannelManager.POWER_EVENT_LISTENER);
        }

        Config.saveConfig();
        if (CommandLine.isConfigOnly()) {
            logger.info("Running in config only mode for '{}' seconds...", CommandLine.getRunSeconds());
            Thread.sleep(CommandLine.getRunSeconds() * 1000);
            Config.logCleanup();
        } else if (CommandLine.isDaemon()) {
            logger.info("Running in daemon mode...");

            // Maybe we can do more with this thread than just wait for the program to terminate.
            while (!Config.isShutdown()) {
                Thread.sleep(60000);
                Config.logCleanup();
            }
        } else {
            if (CommandLine.isSuspendTest()) {
                logger.info("Press ENTER at any time to suspend...");
                System.in.read();
                PowerMessageManager.EVENTS.testSuspend();

                logger.info("Press ENTER at any time to resume...");
                System.in.read();
                PowerMessageManager.EVENTS.testResume();
            }

            logger.info("Press ENTER at any time to exit...");
            System.in.read();
            Config.logCleanup();
        }

        System.exit(0);
    }

    public static void shutdown() {
        logger.info("OpenDCT has received a signal to stop.");

        logger.info("Saving current configuration...");

        Config.saveConfig();

        // This will allow the main thread to stop.
        Config.setShutdown();

        logger.exit();
    }

}
