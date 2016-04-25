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

package opendct.tuning.upnp;

import opendct.Main;
import opendct.config.CommandLine;
import opendct.config.Config;
import opendct.config.ExitCode;
import opendct.sagetv.SageTVManager;
import opendct.tuning.discovery.discoverers.UpnpDiscoverer;
import opendct.tuning.upnp.config.DCTDefaultUpnpServiceConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.message.header.DeviceTypeHeader;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.registry.RegistryListener;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Handler;

public class UpnpManager {
    private static final Logger logger = LogManager.getLogger(UpnpManager.class);

    private static final ReentrantReadWriteLock upnpServiceLock = new ReentrantReadWriteLock();
    private static volatile boolean running;
    private static UpnpService upnpService = null;
    private static RegistryListener registryListener = null;
    private static Thread discoveryThread = null;

    private AtomicBoolean suspend = new AtomicBoolean(false);

    // This object is thread-safe and the code on the other side
    // should handle if this returns a null value.
    public static UpnpService getUpnpService() {
        return upnpService;
    }

    // This object is thread-safe and the code on the other side
    // should handle if this returns a null value.
    public static RegistryListener getRegistryListener() {
        return registryListener;
    }

    public static boolean isRunning() {
        logger.entry();

        boolean returnValue = false;

        upnpServiceLock.readLock().lock();
        try {
            returnValue = running;
        } finally {
            upnpServiceLock.readLock().unlock();
        }

        return logger.exit(returnValue);
    }

    // Returns true as long as the service is actually running.
    public static boolean startUpnpServices(final DefaultUpnpServiceConfiguration defaultUpnpServiceConfiguration, final RegistryListener... registryListeners) {
        logger.entry(defaultUpnpServiceConfiguration, registryListeners);

        boolean returnValue = false;

        upnpServiceLock.writeLock().lock();

        try {
            if (running) {
                logger.debug("UPnP services have already started.");
            } else {
                logger.info("Starting UPnP services...");

                try {
                    // This starts network services immediately.
                    upnpService = new UpnpServiceImpl(defaultUpnpServiceConfiguration, registryListeners);
                } catch (Exception e) {
                    // If the expected configuration fails, try any port available.
                    upnpService = new UpnpServiceImpl(DCTDefaultUpnpServiceConfiguration.getDCTDefault(0), registryListeners);
                }

                running = true;
            }

            returnValue = true;

            // This property will give us the ability to add new schema descriptors as they are
            // changed/discovered. Note that this will not actually limit the responses from UPnP
            // devices on the network, so it is not a filter.
            final String secureContainers[] = Config.getStringArray("upnp.new.device.search_strings_csv", "schemas-cetoncorp-com");
            final int searchInterval = Config.getInteger("upnp.new.device.search_interval_s", 4) * 1000;
            searchSecureContainers(secureContainers);

            discoveryThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("UPnP discovery thread has started.");
                    while (!Thread.currentThread().isInterrupted()) {
                        long endTime = System.currentTimeMillis() + 30000;

                        while (!SageTVManager.captureDevicesLoaded() ||
                                System.currentTimeMillis() < endTime) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            try {
                                Thread.sleep(searchInterval);
                            } catch (InterruptedException e) {
                                logger.info("UPnP discovery thread was interrupted => {}", e.toString());
                                return;
                            }

                            upnpServiceLock.readLock().lock();

                            try {
                                if (!running) {
                                    break;
                                }

                                searchSecureContainers(secureContainers);
                            } finally {
                                upnpServiceLock.readLock().unlock();
                            }


                        }

                        if (!UpnpDiscoverer.getSmartBroadcast()) {
                            break;
                        } else {
                            upnpServiceLock.writeLock().lock();

                            try {
                                logger.debug("Stopping broadcast.");
                                upnpService.shutdown();
                            } finally {
                                upnpServiceLock.writeLock().unlock();
                            }

                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    logger.info("UPnP discovery thread was interrupted => {}", e.toString());
                                    return;
                                }

                                if (UpnpDiscoverer.needBroadcast()) {
                                    logger.debug("Broadcast requested.");

                                    upnpServiceLock.writeLock().lock();

                                    try {
                                        upnpService = new UpnpServiceImpl(
                                                defaultUpnpServiceConfiguration, registryListeners);
                                    } finally {
                                        upnpServiceLock.writeLock().unlock();
                                    }

                                    break;
                                }
                            }
                        }
                    }

                    logger.info("UPnP discovery thread has stopped.");
                }
            });

            discoveryThread.setName("UPnPDiscovery-" + discoveryThread.getId());
            discoveryThread.start();

        } catch (Exception e) {
            logger.error("UPnP services were unable to start => ", e);
            running = false;
            returnValue = false;
        } finally {
            upnpServiceLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    // Returns true as long as the service stopped gracefully.
    public static boolean stopUpnpServices() {
        logger.entry();

        boolean returnValue = false;

        upnpServiceLock.writeLock().lock();

        try {
            if (!running) {
                logger.debug("UPnP services have already stopped.");
            } else {
                if (discoveryThread != null) {
                    discoveryThread.interrupt();
                }

                upnpService.shutdown();
            }
            returnValue = true;
        } catch (Exception e) {
            logger.error("UPnP services were unable to stop => {}", e);
            returnValue = false;
        } finally {
            running = false;
            upnpServiceLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public static boolean addRegistryListener(RegistryListener registryListener) {
        logger.entry(registryListener);

        logger.debug("Adding the '{}' registry listener.", registryListener.getClass().toString());

        boolean returnValue = true;

        upnpServiceLock.writeLock().lock();

        try {
            upnpService.getRegistry().addListener(registryListener);
        } catch (Exception e) {
            logger.error("Unable to add UPnP registry listener => {}", e);
            returnValue = false;
        } finally {
            upnpServiceLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public static boolean removeRegistryListener(RegistryListener registryListener) {
        logger.entry(registryListener);

        logger.debug("Removing the '{}' registry listener.", registryListener.getClass().toString());

        boolean returnValue = true;

        upnpServiceLock.writeLock().lock();

        try {
            upnpService.getRegistry().removeListener(registryListener);
        } catch (Exception e) {
            logger.error("Unable to remove UPnP registry listener => {}", e);
            returnValue = false;
        } finally {
            upnpServiceLock.writeLock().unlock();
        }

        return logger.exit(returnValue);
    }

    public static void searchSecureContainers(String schema[]) {
        // Send out a search request to all secure containers implementing the requested schemas.

        for (String secureContainer : schema) {
            UpnpManager.searchSecureContainers(secureContainer);
        }
    }

    public static boolean searchSecureContainers(String schema) {
        logger.debug("Sending a SecureContainer search message for '{}' devices...", schema);
        DeviceType type = new DeviceType(schema, "SecureContainer", 1);

        boolean returnValue = true;

        try {
            upnpService.getControlPoint().search(new DeviceTypeHeader(type), 1);
        } catch (Exception e) {
            logger.error("The SecureContainer search message for '{}' devices was not sent => {}", schema, e);
            returnValue = false;
        }

        return logger.exit(returnValue);
    }

    public static void configureUPnPLogging() {
        String loggingUpnpLogLevel = CommandLine.getLogUpnpLogLevel();
        String loggingUpnpFilename =
                CommandLine.getLogDir() + File.separator + "opendct_cling.log";

        boolean logUPnPToConsole = (CommandLine.isLogUpnpToConsole());

        // Remove all of the built in logging handlers so we can define our own.
        java.util.logging.Logger upnpLogger = java.util.logging.Logger.getLogger(Main.class.getName()).getParent();
        Handler[] handlers = upnpLogger.getHandlers();
        for (Handler handler : handlers) {
            upnpLogger.removeHandler(handler);
        }

        upnpLogger.setUseParentHandlers(true);
        if (loggingUpnpLogLevel.equals("finest")) {
            upnpLogger.setLevel(java.util.logging.Level.FINEST);
        } else if (loggingUpnpLogLevel.equals("finer")) {
            upnpLogger.setLevel(java.util.logging.Level.FINER);
        } else if (loggingUpnpLogLevel.equals("fine")) {
            upnpLogger.setLevel(java.util.logging.Level.FINE);
        } else if (loggingUpnpLogLevel.equals("info")) {
            upnpLogger.setLevel(java.util.logging.Level.INFO);
        } else if (loggingUpnpLogLevel.equals("warning")) {
            upnpLogger.setLevel(java.util.logging.Level.WARNING);
        } else if (loggingUpnpLogLevel.equals("severe")) {
            upnpLogger.setLevel(java.util.logging.Level.SEVERE);
        } else {
            upnpLogger.setLevel(java.util.logging.Level.OFF);
        }

        if (logUPnPToConsole) {
            java.util.logging.ConsoleHandler consoleHandler = new java.util.logging.ConsoleHandler();
            consoleHandler.setLevel(java.util.logging.Level.FINEST);
            upnpLogger.addHandler(consoleHandler);
        }

        try {
            java.util.logging.FileHandler fileHandler = new java.util.logging.FileHandler(loggingUpnpFilename);
            fileHandler.setLevel(java.util.logging.Level.FINEST);
            upnpLogger.addHandler(fileHandler);
        } catch (Exception e) {
            logger.fatal("The location provided for Cling UPnP logging could not be used => {}", e);
            ExitCode.UPNP_LOGGER.terminateJVM();
            return;
        }

        logger.info("Logging Cling UPnP to '{}'.", loggingUpnpFilename);
        logger.debug("Logging level for Cling UPnP is set to '{}'.", loggingUpnpLogLevel);
    }
}
