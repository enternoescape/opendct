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

package opendct.power;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LinuxPowerMessagePump implements PowerMessagePump {
    private final Logger logger = LogManager.getLogger(LinuxPowerMessagePump.this);

    //TODO: [js] Make this work.

    /*
    https://wiki.archlinux.org/index.php/Power_management#Suspend.2Fresume_service_files
    Something else to look at. This is a little more confusing than I expected.
     */

    /*
    https://wiki.archlinux.org/index.php/Pm-utils#Creating_your_own_hooks
    This is from arch linux, but it applies to any distro that has the pm-utils package.

    Install the pm-utils package if it isn't already installed. Most distros come with this out of
    the box. Create a script and make it executable under /etc/pm/sleep.d prepended with a number
    less than 50 so that network resources are still available. It should accept any of the
    following as the first parameter:

    hibernate
    suspend
    thaw
    resume

    My first version is very simple and somewhat hacky depending on who you ask. I would like to
    find a way for direct communication with the program. I'm open to ideas. The script creates
    these flags /tmp/sagedct/hibernate|suspend|thaw|resume corresponding to the state the computer
    is going into or coming out of. the flag expires automatically after ~15 seconds in case
    something isn't working correctly so that Linux will eventually go into a power saving mode
    regardless of what this program is/isn't doing. This program monitors that folder for files
    being created (should be very light since it will use io notify). The shell script will check to
    see if the folder /tmp/sagedct exists before creating the flag file and waiting for it to be
    removed. If the folder doesn't exist, it is assumed that the program is not running and the
    script just exits. Additional checks would be nice so we don't needlessly hang the computer for
    15 seconds.
    */


    // This is a working script as an example of how to implement this. More checks should be added
    // for certainty that the program is running and desires to have these flags created by the
    // script. For example since we should be running as a daemon, it can check for the existence of
    // '/var/run/opendct.pid' and if permissible verify that the pid corresponds with a running
    // process.
/*
#!/bin/bash
# 20-opendct

# Place this file under /etc/pm/sleep.d and make it executable.
# The pm-utils package must already be installed for this
# directory to exist.

# OpenDCT monitors the path /tmp/opendct for files being created
# and generates an event based on the name of the file generated.
# When OpenDCT has completed everything for this event, it will
# delete the file. There is a 15 second delay in the event that
# the opendct folder exists and the program is not actually
# checking for flags. Other checks should be added to verify if
# the script should create a flag and wait or not.

valid_parms=("hibernate" "suspend" "thaw" "resume")
touch_path=/tmp/opendct

help() {
    echo
    echo "You must provide a valid parameter."
    echo
    echo "Value parameter values are:"
    echo "${valid_parms[@]}."
    echo
}

if [ ! -d $touch_path ]; then
    # Silently exit since this is how we are determining if
    # this script needs to create a flag and wait or not and
    # we don't want to be a nuisance if the OpenDCT isn't in
    # use.
    exit 0
fi


if [ "$#" -ne 1 ]; then
    help
    exit 1
fi

validate=1
for valid_parm in ${valid_parms[@]}; do
    if [[ $1 ==  $valid_parm ]]; then
        validate=0
        break
    fi
done


if [[ $validate -ne 0 ]]; then
    help
    exit 1
fi

echo "Creating $1 flag file under $touch_path..."
touch $touch_path/$1

timeout=15

echo "Waiting $1 flag file to clear for $timeout seconds..."
while [[ -f $touch_path/$1 && timeout -gt 0 ]]; do
    sleep 1s
    let "timeout -= 1"
done

# Should we clean this file up?
if [ -f $touch_path/$1 ]; then
    # \ is used to override any aliases.
    \rm -f $touch_path/$1
fi

exit 0
     */

    public void addListener(PowerEventListener listener) {

    }

    public void removeListener(PowerEventListener listener) {

    }

    public boolean startPump() {
        logger.warn("System power state messages are currently not implemented for Linux.");
        return true;
    }

    public void stopPump() {
        logger.warn("System power state messages are currently not implemented for Linux.");
    }

    public void testSuspend() {

    }

    public void testResume() {

    }
}
