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
import opendct.power.PowerEventListener;
import opendct.tuning.upnp.config.DCTDefaultUpnpServiceConfiguration;
import opendct.tuning.upnp.listener.DCTRegistryListener;
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

public class UpnpManager implements PowerEventListener {
    private static final Logger logger = LogManager.getLogger(UpnpManager.class);
    public static PowerEventListener POWER_EVENT_LISTENER = new UpnpManager();

    private static final ReentrantReadWriteLock upnpServiceLock = new ReentrantReadWriteLock();
    private static volatile boolean running;
    private static UpnpService upnpService = null;
    private static RegistryListener registryListener = null;
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

    public static boolean startUpnpServices() {
        // If this is set to false, the default unmodified configuration is used
        // instead. It could be useful for testing, but will positively break
        // the ability to detect if a DCT device is streaming or not.
        boolean useDCTServiceConfiguration = Config.getBoolean("upnp.service.configuration.use_dct", true);

        if (useDCTServiceConfiguration) {
            // This starts services immediately. The only supported configuration
            // right now is DCT, so we can default to this one. We would need to
            // enhance or replace DCTDefaultUpnpServiceConfiguration for any
            // additional parsing modifications. Since consistency doesn't here,
            // the port used for discovery is selected automatically.
            return UpnpManager.startUpnpServices(DCTDefaultUpnpServiceConfiguration.getDCTDefault(), new DCTRegistryListener());
        } else {
            // This starts the services immediately with the default configuration.
            return UpnpManager.startUpnpServices(new DefaultUpnpServiceConfiguration(), new DCTRegistryListener());
        }
    }

    // Returns true as long as the service is actually running.
    public static boolean startUpnpServices(DefaultUpnpServiceConfiguration defaultUpnpServiceConfiguration, RegistryListener registryListener) {
        logger.entry(defaultUpnpServiceConfiguration, registryListener);

        boolean returnValue = false;

        upnpServiceLock.writeLock().lock();

        try {
            if (running) {
                logger.debug("UPnP services have already started.");
            } else {
                logger.info("Starting UPnP services...");

                // This starts network services immediately.
                upnpService = new UpnpServiceImpl(defaultUpnpServiceConfiguration, registryListener);

                running = true;
            }

            returnValue = true;
        } catch (Exception e) {
            logger.error("UPnP services were unable to start => ", e);
            running = false;
            returnValue = false;
        } finally {
            upnpServiceLock.writeLock().unlock();
        }

        // This property will give us the ability to add new schema descriptors
        // as they are changed/discovered. Note that this will not actually
        // limit the responses from UPnP devices on the network, so it is not a
        // filter.
        String secureContainers[] = Config.getStringArray("upnp.new.device.search_strings_csv", "schemas-cetoncorp-com", "schemas-dkeystone-com");
        searchSecureContainers(secureContainers);

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
            upnpService.getControlPoint().search(new DeviceTypeHeader(type));
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

    public void onSuspendEvent() {
        if (suspend.getAndSet(true)) {
            logger.error("onSuspendEvent: The computer is going into suspend mode and UpnpManager has possibly not recovered from the last suspend event.");
        } else {
            logger.debug("onSuspendEvent: Stopping services due to a suspend event.");
            stopUpnpServices();
        }
    }

    public void onResumeSuspendEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeSuspendEvent: The computer returned from suspend mode and UpnpManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeSuspendEvent: Starting services due to a resume event.");
            startUpnpServices();
        }
    }

    public void onResumeCriticalEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeCriticalEvent: The computer returned from suspend mode and UpnpManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeCriticalEvent: Starting services due to a resume event.");
            startUpnpServices();
        }
    }

    public void onResumeAutomaticEvent() {
        if (!suspend.getAndSet(false)) {
            logger.error("onResumeAutomaticEvent: The computer returned from suspend mode and UpnpManager possibly did not shutdown since the last suspend event.");
        } else {
            logger.debug("onResumeAutomaticEvent: Starting services due to a resume event.");
            startUpnpServices();
        }
    }
}
