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

#### 0.3.15-Stable
> *No changes. This is a final release. I am considering this stable since we have not found any new
> issues relating to overall program functionality and all major issues have been resolved. There
> will be no further updates to this version unless something critical is discovered and the current
> latest version is not yet stable.

#### 0.3.16-Stable
> *Backported: Fixed an unreliable timeout issue with the re-tuning monitoring thread under the
> right conditions.

#### 0.4.0-Beta
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

#### 0.4.1-Beta
> *Added handling for Prime urls with copy protection so they don't get enabled in the lineup.

> *Fix a problem when tuning the frequency 0 that could potentially cause a device to never unlock.

#### 0.4.2-Alpha
> *Modified circular buffer to accommodate seeking after detection. Testing has been very
> successful, but watch this release very carefully and report any issues with a log attached.

#### 0.4.3-Alpha
> *Added and tested seek error handling to the circular buffer. More testing needed.

#### 0.4.4-Alpha
> *Still alpha due to the circular buffer changes.

> *Added monitoring and validation for RTCP. Currently no responses are generated. This is just so
> we know if it's happening.

> *Internal: Added channel number to consumer so it can change it's behavior optionally based on the
> currently tuned channel.

> *Internal: Logging line ending and space usage improvements.

#### 0.4.5-Alpha
> *Fixed issue with RTCP port not closing at the right time.

> *Added logging for initial packet losses, less than 12 bytes packet losses and packet size limit
> possibly exceeded warnings.

> *Increased UDP buffer from 1500 to 65508 which is the largest size any UDP packet should ever be.

#### 0.4.6-Alpha
> *Changed UDP buffer to start at 1500 and dynamically resize to fit the current situation. It saves 
> the final size and uses it the next time so detection only needs to happen once.

#### 0.4.7-Alpha
> *Addresses a null pointer issue when tuning using UPnP.

#### 0.4.8-Alpha
> *Fixed an unreliable timeout issue with the re-tuning monitoring thread under the right
> conditions.

> *Changed wrapper.conf to not restart on exit conditions that can't possibly be corrected by
> restarting the program.

> *Fixed an issue with HDHomeRun program detection not returning a value when it had not yet reached
> the timeout.

#### 0.4.9-Beta
> *Returned to beta now that the new buffer has been in use for about a week without any complaints
> relating to the buffer. The changes were not intense enough to warrant any further testing. We now
> have a junit test to help confirm the integrity of the buffer.

> *Fixed speed and line termination issue when returning PROPERTIES.

#### 0.4.10-Beta
> *Added feature to clean up log files based on available free space. The default minimum free space
> is 1GB. Free space could be a very real issue if logging gets intense for any reason and logging
> should never be the cause of your server failing. This runs after log files are deleted by date.

> *Increased logging for re-tuning to help determine why it happened.

> *Internal: Changed to TestNG for greater flexibility in testing.

#### 0.4.11-Beta
> *Fixed race condition in circular buffer.

> *Added suspend logic to handle unexpected states better.

#### 0.4.12-Beta
> *Additional fixes for seeking in the circular buffer logic.

> *Added more suspend logic to handle unexpected states.

> *Windows installer now applies firewall settings the official way.

#### 0.4.13-Beta
> *Windows installer now will not fail on firewall rules on upgrades. If you need to roll back to
> 0.4.12, you will need to install 0.4.11 first, then 0.4.12 to get around the firewall upgrade
> issues.

> *Addressed possibility that the RTP stall monitoring thread could continue running even after it
> should have stopped. This has no serious consequence other than annoying logged warnings.

> *Fixed a rare null pointer issue in the FFmpeg consumer. [ws]

> *Addressed standby issue with UPnP devices not responding quickly enough.

> *Fixed standby network listener to work with the new standby code. It will now wait for the 
> network to be available before resuming any other activities like it did before the update.

#### 0.4.14-Beta
> *Changed over to a more public beta.

#### 0.4.15-Beta
> *Added tunable thread priorities for consumer and producer threads.

> *Internal: Removed desired PID from consumer interface since it can be unreliable.

#### 0.4.16-Beta
> *Limited program detection to 2 seconds. Normally it's detected in less than 200ms, so this should
> not introduce any new issues and should keep the capture device from returning so late that the
> circular buffer is overflowing.

> *Added more resilience to the HDHomeRun native communications. It will now try up to 5 times at 1
> second intervals to reach a device before it gives up.

> *Improved fragmented HDHomeRun response support.

#### 0.4.17-Beta
> *HDHomeRun packet length was not accounting for the 4 byte header and 4 byte CRC when ensuring we
> don't have a fragmented packet.