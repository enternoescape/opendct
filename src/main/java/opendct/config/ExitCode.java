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

import opendct.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public enum ExitCode {
    // If we have all exit messages passing through one place, we can control their behavior if
    // needed and can make sure error codes returned always mean something specific.

    // Failures that can prevent the program from even starting.
    SUCCESS(0, "OpenDCT is closing without any critical errors."),
    PARAMETER_ISSUE(1, "OpenDCT was unable to use the provided command line parameters."),
    CONFIG_DIRECTORY(2, "OpenDCT was unable to open or create the configuration directory."),
    CONFIG_ISSUE(3, "OpenDCT was unable to open or create configuration data."),
    UPNP_LOGGER(4, "Cling UPnP is unable to open or create a log file at the provided location."),
    RESTART(5, "OpenDCT is restarting..."),
    PARAMETER_CONFLICT(6, "OpenDCT had detected a parameter conflict in opendct.properties that must be corrected."),
    NO_NETWORK_INTERFACES(7, "OpenDCT did not detect any network interfaces within the allowed time."),

    // Failures from the power message pump.
    PM_INIT(10, "PowerMessageManager was unable to determine what PowerMessagePump to use."),
    PM_EXCEPTION(11, "PowerMessagePump encountered an unhandled exception."),
    PM_GET_MESSAGE(12, "PowerMessagePump encountered a problem receiving messages."),
    PM_NETWORK_RESUME(13, "NetworkPowerMessagePump what unable to resume within the allowed time."),

    // Failures from the SageTV related code.
    SAGETV_DISCOVERY(20, "SageTVDiscovery was unable to open a required listening port."),
    SAGETV_SOCKET(21, "SageTVManager was unable to open a required socket server listening port."),
    SAGETV_NO_DEVICES(22, "SageTVManager did not load all of the expected capture devices within the allowed time."),
    SAGETV_DUPLICATE(23, "SageTVManager attempted to load a device with a duplicate name.");

    private final Logger logger = LogManager.getLogger(ExitCode.class);
    public final int CODE;
    public final String DESCRIPTION;

    ExitCode(int exitCode, String exitDescription) {
        CODE = exitCode;
        DESCRIPTION = exitDescription;
    }

    @Override
    public String toString() {
        if (this == SUCCESS || this == RESTART) {
            return DESCRIPTION;
        }

        return "OpenDCT experienced a fatal error: " + DESCRIPTION;
    }

    /**
     * Terminates the program providing the selected ExitCode as the reason with a description.
     */
    public void terminateJVM() {
        terminateJVM(null);
    }

    /**
     * Terminates the program providing the selected ExitCode as the reason with a description.
     * <p/>
     * It also prints to the console in case the person might not have any visible logging turned on
     * and might not understand why the program keeps stopping without any error messages.
     *
     * @param help Provide a specific hint as to what could correct the situation.
     */
    public void terminateJVM(String help) {

        // This will only restart the program if it is being run as a service. The service checks
        // for the restart file in the configuration directory. If the file exists, the service will
        // restart the program. When the program is started, right after the configuration folder is
        // set, the restart file is cleared.
        if (this == RESTART) {
            File restartFile = new File(Config.CONFIG_DIR + Config.DIR_SEPARATOR + "restart");
            if (!restartFile.exists()) {
                try {
                    if (restartFile.createNewFile()) {
                        logger.info("Created the file '{}'.", restartFile.getName());
                    }
                } catch (IOException e) {
                    if (!restartFile.exists()) {
                        logger.error("Unable to create the file '{}' => ", restartFile.getName(), e);
                    }
                }
            }
        }

        Config.setExitCode(CODE);
        logger.fatal(toString());
        System.err.print(toString());

        if (help != null && !help.equals("")) {
            logger.info(help);
            System.err.print(help);
        }

        if (this != SUCCESS && this != RESTART) {
            try {
                File source = new File(Config.LOG_DIR + Config.DIR_SEPARATOR + "opendct.log");
                File destination = new File(Config.LOG_DIR + Config.DIR_SEPARATOR + "opendct-crash-0.log");

                int increment = 1;
                while (destination.exists()) {
                    destination = new File(Config.LOG_DIR + Config.DIR_SEPARATOR + "opendct-crash-" + increment++ + ".log");

                    // If we actually manage to reach the max positive integer number, this will ensure we don't loop endlessly.
                    if (increment < 0) {
                        destination = new File(Config.LOG_DIR + Config.DIR_SEPARATOR + "opendct.crash.log");
                        break;
                    }
                }

                Util.copyFile(source, destination, true);
            } catch (IOException e) {
                logger.error("Unable to create a copy of the crash log => ", e);
            } catch (Exception e) {
                logger.error("Unable to create a copy of the crash log => ", e);
            }
        }

        System.exit(CODE);
    }
}
