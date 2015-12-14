# [OpenDCT](http://forums.sagetv.com/forums/showthread.php?t=62570)

An open source digital cable tuner network encoder for [SageTV](http://forums.sagetv.com/forums/index.php).

## Download
OpenDCT packaged releases are hosted on [Bintray.com](https://bintray.com/opendct/Releases/OpenDCT/view#files/releases/).

*On Linux platforms it is recommended to only use the architecture of your distribution.*

#### Ubuntu 14.04
```
apt-get install default-jre-headless
dpkg -i opendct_x.x.x-x_arch.deb
```

#### CentOS 7
```
yum install java-1.8.0-openjdk-headless
rpm -i opendct_x.x.x-x_arch.rpm
```

#### Fedora 22
```
dnf install java-1.8.0-openjdk-headless
rpm -i opendct_x.x.x-x_arch.rpm
```

#### Windows 7+
On Windows platforms, unless you have the 64-bit Java Runtime installed, use the 32-bit (x86) installer. The Windows installer will do upgrades so it is unnecessary to uninstall before installing a new version. It is however necessary to stop the service before upgrading.

## Configuration

#### opendct.properties
The majority of the configuration is done inside opendct.properties. The file is automatically populated after the first time the program is run. Do not make changes to this file while the program is running since your changes will be overwritten. This list is just the very basics to keep the program from communicating with undesired devices.

* sagetv.device.global.ignore\_devices\_csv=
 * This is a comma delimited list of tuners or entire devices to be ignored if they are discovered. The names on this list must perfectly match the name of the tuner or "parent" device as it is named when discovered. When a device is on the list, all tuners on that device will be excluded.
* sagetv.device.global.only\_devices\_csv=
 * This property if set always supersedes ignore\_devices\_csv. This is a comma delimited list of tuners or entire devices allowed to be initialized if they are discovered. The names on this list must perfectly match the name of the tuner or "parent" device as it is named when discovered.
* sagetv.device.global.required\_devices\_loaded\_timeout\_ms=30000
 * This is the amount of time allowed in milliseconds to pass while waiting for the required number of devices before the program will exit with a failure.
* sagetv.device.global.required\_devices\_loaded\_count=0
 * This is the number of devices that need to be detected and loaded before the required timeout.

#### Sage.properties

After configuring OpenDCT, the SageTV service needs to be stopped. Open Sage.properties and find the property network\_encoder\_discovery and change the value to true. Start the SageTV service again while OpenDCT is running to discover the available capture devices.

## Compiling
OpenDCT builds are created using Gradle. The following commands should get you started.

#### Create project files for Eclipse.
```
gradlew eclipse
```

#### Create project files for IntelliJ.
```
gradlew ideal
```

#### Compile the project and create packages for installation on Ubuntu 14.04, CentOS 7, Fedora 22 and Windows 7+.
*Note: Building the Windows installer is currently only supported on Windows.*

1. Install the latest [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/) for your OS architecture.

2. Install the [Wix Toolset](http://wixtoolset.org/) so you can build the packages for Windows.

 * You may need to re-open the console window so the newly set WIX environment will be available.
 * Optionally you can tell the script where the binary is located. See the README.md under scripts/wix for details.
 * If the Wix binaries cannot be found, the build will fail after the Linux packages have been created.

3. Execute:
```
gradlew packageAll
```
*The packages will be found under build/distributions.*

# Reporting Issues
First check the [SageTV forums](http://forums.sagetv.com/forums/index.php) to help eliminate other possibilities in your environment/configuration that are unrelated to OpenDCT. Also the forums could help if the issue is actually a problem involving an incorrect configuration of OpenDCT. If it is determined that it must be an issue with OpenDCT that should be addressed, please create a [new issue on GitHub](https://github.com/enternoescape/opendct/issues/new). Please be very specific and attach SageTV and OpenDCT log files if possible.

# Contributing
All contributions are very appreciated and if you have already made any thank you for your time. You do not need to be a programmer to contribute. For example, if you have extra resources to test potentially unstable builds, we would like your feedback on issues you encounter even if the issue is as trivial as intuitiveness of the provided interfaces.

If you would like to contribute to the source code, please create a [new issue on GitHub](https://github.com/enternoescape/opendct/issues/new) so everyone knows what you're working on and we can collaborate on the best way to implement your proposed change. The only exception to this rule is if you're just making a trivial modification like fixing a typo. Anything that actually changes how the code works in any way should be posted as an issue before committing the changes.

Contributions must be your own. If your code requires any licensed code, the licensed code must use a license compatible with the  [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) license. Libraries must be properly attributed under distribution/licenses. Licensed source code must be attributed in the header of the source file using the licensed source code and attributed in compliance with any other attribution requirements of the licence.

# License
This project is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) with no warranty (expressed or implied) for any purpose.