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

import opendct.channel.ChannelManager;
import opendct.config.Config;
import opendct.config.ExitCode;
import opendct.nanohttpd.NanoHTTPDManager;
import opendct.power.NetworkPowerEventManger;
import opendct.power.PowerMessageManager;
import opendct.sagetv.SageTVManager;
import opendct.tuning.discovery.DiscoveryManager;
import opendct.tuning.discovery.discoverers.GenericHttpDiscoverer;
import opendct.tuning.discovery.discoverers.HDHomeRunDiscoverer;
import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import opendct.tuning.upnp.UpnpManager;
import opendct.video.ffmpeg.FFmpegUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.File;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting OpenDCT {}...", Config.VERSION_PROGRAM);

        Runtime.getRuntime().addShutdownHook(new Thread("Shutdown") {
            @Override
            public void run() {
                shutdown();
            }
        });

        File restartFile = new File(Config.CONFIG_DIR + Config.DIR_SEPARATOR + "restart");
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
        if (Config.CONSOLE_LOG) {
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

        logger.info("OpenDCT logging to the directory '{}'.", Config.LOG_DIR);

        // This takes care of everything to do with logging from cling. The default configuration
        // only reports severe errors.
        UpnpManager.configureUPnPLogging();

        Thread ffmpegAsyncInit = new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                logger.info("FFmpeg loading...");

                // FFmpeg takes a while to initialize, it is accessed now to reduce delays. Creating
                // this immediately disposed object can reduce the first device load time by up to 5
                // seconds. This should not wait until the first time a device is tuned since it
                // will add a very measurable delay.
                FFmpegUtil.initAll();

                long endTime = System.currentTimeMillis();
                logger.info("FFmpeg loaded in {}ms.", endTime - startTime);
            }
        });

        // This makes sure that it loads as fast as possible, so there are no delays when devices
        // start loading.
        ffmpegAsyncInit.setPriority(Thread.MAX_PRIORITY);
        ffmpegAsyncInit.setName("FFmpegAsyncInit-" + ffmpegAsyncInit.getId());
        ffmpegAsyncInit.start();

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

        // When this is set to true SageTV will open all ports assigned to any capture device in the
        // configuration properties.
        boolean earlyPortAssignment = Config.getBoolean("sagetv.early_port_assignment", false);

        // When this is set to true, all new devices will receive the same communication port
        // number. This is intelligently handled when SageTVManager creates instances of
        // SageTVSocketServer. When a new instance is being added, if it shares the same port
        // number as an instance that has already started, it will just be added to that
        // instances pool of capture devices.
        boolean incrementPortForNewDevices = Config.getBoolean("sagetv.new.device.increment_port", false);

        int sageTVDefaultEncoderMerit = Config.getInteger("sagetv.new.device.default_encoder_merit", 0);
        int sageTVDefaultTuningDelay = Config.getInteger("sagetv.new.device.default_tuning_delay", 0);

        int sageTVDefaultDiscoveryPort = Config.getInteger("sagetv.encoder_discovery_port", 8271);

        // If this is enabled the program will use the discovery manager. If this is disabled, no
        // capture devices will be loaded.
        boolean useDiscoveryManager = Config.getBoolean("discovery.enabled", true);

        // This starts the timer for all of the capture devices to be loaded. The default timeout is
        // 30 seconds. The default device count is 0. These values are saved after the first run and
        // can be changed after stopping the program.
        SageTVManager.startWaitingForCaptureDevices();

        Config.saveConfig();

        if (earlyPortAssignment) {
            logger.info("Early port assignment is enabled.");
            SageTVManager.addAndStartSocketServers(Config.getAllSocketServerPorts());
        }

        if (useDiscoveryManager) {
            DiscoveryManager.addDiscoverer(new UpnpDiscoverer());
            DiscoveryManager.addDiscoverer(new HDHomeRunDiscoverer());
            DiscoveryManager.addDiscoverer(new GenericHttpDiscoverer());
            DiscoveryManager.startDeviceDiscovery();

            PowerMessageManager.EVENTS.addListener(DiscoveryManager.POWER_EVENT_LISTENER);

            Runtime.getRuntime().addShutdownHook(new Thread("DiscoveryManagerShutdown") {
                @Override
                public void run() {
                    logger.info("Stopping device discovery services...");
                    try {
                        DiscoveryManager.stopDeviceDiscovery();
                    } catch (InterruptedException e) {
                        logger.debug("Stopping device discovery services was interrupted => ", e);
                    }
                }
            });
        }

        if (NanoHTTPDManager.startWebServer()) {
            PowerMessageManager.EVENTS.addListener(NanoHTTPDManager.POWER_EVENT_LISTENTER);

            Runtime.getRuntime().addShutdownHook(new Thread("NanoHTTPDShutdown") {
                @Override
                public void run() {
                    logger.info("Stopping web server...");
                    try {
                        NanoHTTPDManager.stopWebServer();
                    } catch (Exception e) {
                        logger.debug("Stopping web server created an exception => ", e);
                    }
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

        if (Config.IS_DAEMON) {
            logger.info("Running in daemon mode...");

            // Maybe we can do more with this thread than just wait for the program to terminate.
            while (!Config.isShutdown()) {
                Thread.sleep(60000);
                Config.logCleanup();
            }
        } else {
            if (Config.SUSPEND_TEST) {
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
