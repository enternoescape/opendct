# [OpenDCT](http://forums.sagetv.com/forums/showthread.php?p=581743)

An open source digital cable tuner network encoder for [SageTV](http://forums.sagetv.com/forums/index.php).

## Download

##### Latest Stable
[ ![Download](https://api.bintray.com/packages/opendct/Releases/OpenDCT/images/download.svg) ](https://bintray.com/opendct/Releases/OpenDCT/_latestVersion)

##### Latest Beta
[ ![Download](https://api.bintray.com/packages/opendct/Beta/OpenDCT/images/download.svg) ](https://bintray.com/opendct/Beta/OpenDCT/_latestVersion)

[Java Profiler](https://www.ej-technologies.com/products/jprofiler/overview.html)

*On Linux platforms it is recommended to only use the architecture of your distribution.*

#### Ubuntu 14.04, 16.04
```
apt-get install default-jre-headless
dpkg -i opendct_x.x.x-x_arch.deb
```

#### CentOS 7
```
yum install java-1.8.0-openjdk-headless
rpm -U opendct_x.x.x-x_arch.rpm
```

#### Fedora 23, 24
```
dnf install java-1.8.0-openjdk-headless
rpm -U opendct_x.x.x-x_arch.rpm
```

#### Windows 7+
On Windows platforms, unless you have the 64-bit Java Runtime installed, use the 32-bit (x86)
installer. The Windows installer will do upgrades so it is unnecessary to uninstall before
installing a new version. It is however necessary to stop the service before upgrading.

## Configuration

#### First Time Use
*It is advised that if you use any other network encoders for SageTV that use the same capture
 devices that OpenDCT will provide, that you stop those programs/services and disable them before
 attempting to use OpenDCT.* 

1. Stop the SageTV service.
2. Make a backup copy of Sage.properties and Wiz.bin from your SageTV installation folder.
3. Edit opendct.properties per the section below if you want to limit what it should discover
 otherwise continue to the next step.
  * *Windows:* From the Start Menu, open OpenDCT Properties.
  
  * *Linux:* In versions 0.4.17 and older, the opendct.properties file is located under 
  /opt/opendct/conf/opendct.properties. Starting with 0.4.18 it is now located under 
  /etc/opendct/conf/opendct.properties.
4. Start OpenDCT in console mode the first time.
  * *Windows:* From the Start Menu, open OpenDCT Run as Console.
  
  * *Linux:* From a console as root run:
    
    ```
    /opt/opendct/console-only
    ```
5. Press Ctrl-C after waiting about 30 seconds to stop OpenDCT. *Never run the console and the
 service at the same time.*
6. After the program exits, you can edit opendct.properties per the section below. The [first post
 on the SageTV forums](http://forums.sagetv.com/forums/showthread.php?p=581743&postcount=1) explains
 some of the more detailed options.
  * *Windows:* From the Start Menu, open OpenDCT Properties.
  
  * *Linux:* In versions 0.4.17 and older, the opendct.properties file is located under
   /opt/opendct/conf/opendct.properties. Starting with 0.4.18 it is now located under
    /etc/opendct/conf/opendct.properties.
7. Start the OpenDCT service. *You will always need to stop the OpenDCT service before making any 
 changes to opendct.properties*
  * *Windows:* From the Start Menu, open OpenDCT Start Service.
  
  * *Ubuntu:* From a console as root run:
    
    ```
    service opendct start
    ```
  * *CentOS and Ubuntu 16.04:* From a console as root run: *The first command enables the service so it will always run at startup.*
    
    ```
    systemctl enable opendct.service
    systemctl start opendct.service
    ```
8. Change the property value of network\_encoder\_discovery to true in Sage.properties.
9. Start the SageTV service.
10. New capture devices will now be available to be added within SageTV and will look similar to
 *DCT-HDHomeRun Prime Tuner XXXXXXXX-0 on x.x.x.x:9000*. If you are running OpenDCT on multiple
 computers, always verify that the IP address *x.x.x.x:9000* in SageTV matches the computer you
 intend to use for that capture device.

#### opendct.properties
The majority of the configuration is done inside opendct.properties. The file is automatically
populated after the first time the program is run. Do not make changes to this file while the
program is running since your changes will be overwritten. This list is just the very basics to keep
the program from communicating with undesired devices.

* sagetv.device.global.ignore\_devices\_csv=
  * This is a comma delimited list of tuners or entire devices to be ignored if they are discovered.
    The names on this list must perfectly match the name of the tuner or "parent" device as it is
    named when discovered. When a device is on the list, all tuners on that device will be excluded.
  * Tuner Devices Ex. sagetv.device.global.ignore\_devices\_csv=DCT-Ceton InfiniTV PCIe (00-00-00-00) Tuner 5,DCT-Ceton InfiniTV PCIe (00-00-00-00) Tuner 6
  * Parent Device Ex. sagetv.device.global.ignore\_devices\_csv=Ceton InfiniTV PCIe (00-00-00-00)
* sagetv.device.global.only\_devices\_csv=
  * This property if set always supersedes ignore\_devices\_csv. This is a comma delimited list of
    tuners or entire devices allowed to be initialized if they are discovered. The names on this
    list must perfectly match the name of the tuner or "parent" device as it is named when
    discovered.
  * Tuner Devices Ex. sagetv.device.global.only\_devices\_csv=DCT-HDHomeRun Prime Tuner 1318692C-0,DCT-HDHomeRun Prime Tuner 1318692C-1
  * Parent Device Ex. sagetv.device.global.only\_devices\_csv=HDHomeRun DRI Tuner 1318692C
* sagetv.device.global.required\_devices\_loaded\_timeout\_ms=60000
  * This is the amount of time allowed in milliseconds to pass while waiting for the required number
    of devices before the program will exit with a failure.
* sagetv.device.global.required\_devices\_loaded\_count=0
  * This is the number of devices that need to be detected and loaded before the required timeout. 
    This must be the total number of devices OpenDCT normally detects and loads for standby support
    to work correctly.
* upnp.service.configuration.ignore\_interfaces\_csv=
  * Specify the name of interfaces in a comma delimited list of network interfaces as they are named
    by Java to be ignored when UPnP is performing discovery.
  * Ex. upnp.service.configuration.ignore\_interfaces\_csv=eth2,eth5
* upnp.service.configuration.ignore\_local\_ip\_csv=
  * Specify the IP addresses of local interfaces in a comma delimited list to be ignored when UPnP 
    is performing discovery.
  * Ex. upnp.service.configuration.ignore\_local\_ip\_csv=10.0.0.21,192.168.1.23
 
#### Sage.properties

After configuring OpenDCT, the SageTV service needs to be stopped. Open Sage.properties and find the
property network\_encoder\_discovery and change the value to true. Start the SageTV service again
while OpenDCT is already running to discover the available capture devices.

## Compiling
OpenDCT builds are created using Gradle. The following commands should get you started.

#### Create project files for Eclipse.
```
gradlew eclipse
```

#### Create project files for IntelliJ IDEA.
```
gradlew idea
```

There is a bug in some versions of IDEA 2016. You may need to change the binary dependencies for
ffmpeg:os-arch to Runtime in Project Structure to be able to debug.

#### Compile the project and create packages for installation on Ubuntu 14.04, CentOS 7, Fedora 22, 23 and Windows 7+.
*Note: Building the Windows installer is currently only supported on Windows. Preliminary Linux
 support has been added using WINE, but requires msi.dll from a Windows installation to build
 functional installers and for now is not a recommended way to build msi packages.*

1. Install the latest [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/) for your
   OS architecture.

2. Install the [WiX Toolset](http://wixtoolset.org/) so you can build the packages for Windows.

 * You may need to re-open the console window so the newly set WIX environment will be available.
 * Optionally you can tell the script where the binary is located. See the README.md under
   scripts/wix for details.
 * If the WiX binaries cannot be found, the build will fail after the Linux packages have been
   created.

3. Execute:
   ```
   gradlew packageAll
   ```
*The packages will be found under build/distributions.*

# Reporting Issues
First check the [SageTV forums](http://forums.sagetv.com/forums/index.php) to help eliminate other
possibilities in your environment/configuration that are unrelated to OpenDCT. Also the forums could
help if the issue is actually a problem involving an incorrect configuration of OpenDCT. If it is 
determined that it must be an issue with OpenDCT that should be addressed, please create a [new 
issue on GitHub](https://github.com/enternoescape/opendct/issues/new). Please be very specific and
 attach SageTV and OpenDCT log files if possible.

# Contributing
All contributions are very appreciated and if you have already made any thank you for your time. You
do not need to be a programmer to contribute. For example, if you have extra resources to test
potentially unstable builds, we would like your feedback on issues you encounter even if the issue
is as trivial as intuitiveness of the provided interfaces.

If you would like to contribute to the source code, please create a [new issue on GitHub]
(https://github.com/enternoescape/opendct/issues/new) so everyone knows what you're working on and
we can collaborate on the best way to implement your proposed change. The only exception to this
rule is if you're just making a trivial modification like fixing a typo. Anything that actually
changes how the code works in any way should be posted as an issue before committing the changes.

Contributions must be your own. If your code requires any licensed code, the licensed code must use
a license compatible with the  [Apache License, Version 2.0]
(http://www.apache.org/licenses/LICENSE-2.0.html) license. Libraries must be properly attributed
under distribution/licenses. Licensed source code must be attributed in the header of the source
file using the licensed source code and attributed in compliance with any other attribution
requirements of the licence.

# License
This project is licensed under the [Apache License, Version 2.0]
(http://www.apache.org/licenses/LICENSE-2.0.html) with no warranty (expressed or implied) for any
purpose.
