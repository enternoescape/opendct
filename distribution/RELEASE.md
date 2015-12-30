## OpenDCT Release Notes

#### 0.3.6-Beta
> *Initial public beta release.

#### 0.3.7-Beta
> *The network encoder will now poll the the incoming data every 4 seconds for activity. If the data
> has stopped streaming, it will re-tune the DCT. It will continue to re-tune the DCT until SageTV
> tells it to STOP or it successfully re-tunes the channel. This is needed because some devices can
> reboot in the middle of streaming and SageTV takes too long to react to the halted recording.

#### 0.3.8-Beta
> *Added firewall configurations to the install packages.
> **Windows will install firewall exceptions for you if you are using the Windows Firewall.
> **CentOS 7 and Fedora 22 will provide a firewalld service that can be enabled.
> **Ubuntu 14.04 will provide a script to enable rules via ufw.
> **Changed default RTP receiving port change to 8300-8500 since the previous default could conflict
> with the SageTV discovery port.

> *Added optional support for the HDHomeRun lock to be taken even if another device has already
> locked it.

#### 0.3.9-Beta
> *Removed loopback devices from the auto-detection of the local interface.

> *Added custom property on a per parent property to manually set the IP address of the local
> interface to use for device communication.

#### 0.3.10-Beta
> *Relaxed the source protocol requirement when creating a new UPnP capture device.

> *Added wrapper.exe to firewall exceptions on Windows installer.

#### 0.3.11-Beta
> *Fixed a regression in local interface auto-detection due to a GitHub commit failure.

#### 0.3.12-Beta
> *Fixed local interface auto-detection and tested under several conditions. It should now work in
> all normal situations.

#### 0.3.13-Beta
> *Fixed some error handling issues in RTSP configuration.

#### 0.3.14-Beta
> *Fixed error handling issues with corrupt lineups.

#### 0.4.0-Beta (compile only; not released)
> *Added tuner pooling as an experimental feature turned off by default. Set the property
> pool.enabled=true to enable it. There are no fundamental issues, but this is it's first release,
> so it will be considered experimental for now. ClearQAM and DCT devices are automatically put in 
> their own pools. Tuning is based on merit, then if the device is locked externally or not. Do not
> combine capture devices in a pool that cannot tune the same channels.  

> *Cleaned up some logging excess with the InfiniTV channel updates.

> *Fixed duplicate detection removing all channels but one when Prime is in ClearQAM mode.

> *Added a channels.prime.enable_all_channels=true property that when true and and the lineup is
> detected to not be ClearQAM, all channels are assumed tunable. This is a safe setting since the
> list is a result of a channel scan unlike the InfiniTV. This allows you to do a channel scan from
> SageTV and get exactly the list on the Prime returned up to 159 channels (SageTV limitation).

> *Added the properties upnp.service.configuration.ignore_interfaces_csv and
> upnp.service.configuration.ignore_local_ip_csv so you can exclude interfaces from UPnP detection
> by name and/or by local IP address.

> *Fixed line endings on Windows for exceptions logged by log4j2.

> *Internal: Removed concurrent connection checking since it doesn't do anything helpful and is now
> one less decision branch.

> *Internal: Fixed a few methods that might not run in all cases on Java 1.7.

> *Internal: Fixed the Gradle script so EOL in Linux packages will always be correct regardless of
> how the files were downloaded. 

#### 0.4.1-Beta  (compile only; not released)
> *Added handling for Prime urls with copy protection so they don't get enabled in the lineup.