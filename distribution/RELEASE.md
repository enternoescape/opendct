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

> *Addressed more possible situations for HDHomeRun communication failures.

> *Fixed single character HDHomeRun response handling.

> *Added a byte array queue buffer to the circular buffer to help with data overflow situations. It 
> will queue up to 4x the size of the buffer in a data overflow situation.

#### 0.4.18-Beta
> *To be more compliant with Debian, all Linux future packages now use /etc/opendct/conf for
> configuration. That includes opendct.properties. The install scripts will detect old
> configurations and copy your /opt/opendct/conf folder to /etc/opendct/conf, then rename
> /opt/opendct/conf to /opt/opendct/conf.moved in the /opt/opendct/conf folder. The copy is
> triggered on the existence of /opt/opendct/conf so it will only trigger once. You can safely
> delete /opt/opendct/conf.moved if you want to clean up.

> *Fedora 23 is now officially supported.

> *Internal: Updated Linux install package build scripts for better compliance with current
> standards.

> *Optimized overflow queue and removed debug code in buffer from production code.

> *Changed how InfiniTV ClearQAM tuning stops the packet stream without completely tearing down 
> RTSP. The old way could put InfiniTV 6 devices in a state that requires a soft reboot to be able
> to detect programs available on the tuned frequency again. This issue happens with the CGMS-A fix
> firmware installed.

#### 0.4.19-Beta
> *Added detection for old InfiniTV 4 firmwares to prevent http tuning from being able to be
> selected since it will not work.

> *Upload ID is now disabled by default since it might cause Java heap problems for some setups.
> Current installations upgrading will retain the enabled setting.

> *Improved UPnP search thread.

> *Improved FFmpeg detection times and stream selection accuracy. [ws]

> *Experimental support for MPEG-PS.

> *OpenDCT when tuning a new channel now doesn't respond to SageTV until FFmpeg has completed
> detection or 5 seconds have passed. This is necessary to support proper detection of MPEG-PS by
> SageTV.

#### 0.4.20-Beta
> *Added check at startup for at least one network interface to be present before continuing
> startup. This has a default timeout of 2 minutes.

> *Circular buffer now expands dynamically during FFmpeg detection. To simplify things, the max
> probe size is now always the requested circular buffer size times 3 then minus 1MB. That way the
> detection will always be able to take advantage of the largest size the buffer could possibly be.

> *Official support for MPEG-PS.

> *Experimental support for HDHomeRun ATSC and ClearQAM capture devices. Channel scanning is not an
> option and will likely not be an option for a while because of how SageTV maps ATSC channels. To
> enable support, you must enable the new capture device detection method. To do this, change
> discovery.exp_enabled from false to true in opendct.properties. The new detection method currently
> excludes the Prime from detection so you will not have any conflicts when UPnP loads the same
> device.

> *Fixed the extracting of the frequency from a currently tuned channel. It was removing the last 3
> zeros, while the current approach is to keep them. The removal was to keep things consistent
> between InfiniTV and Prime, but now 3 zeros are added to InfiniTV aquired lineups and are only
> removed if tuning an InfiniTV device.

> *Internal: Created framework for capture device detection that enables the addition of new 
> detection methods to be more like creating a plugin.

> *Internal: HDHomeRun native detection method completely implemented.

> *Internal: Enabled JSW configuration reload on restart for future automatic/requested upgrading
> via web interface. This method of updating is not currently in use and will be disabled by
> default.

#### 0.4.21-Beta
> *HDHomeRun will now use any local UDP port that's available for discovery. 

> *HDHomeRun legacy devices can now be tuned in us-bcast mode.

> *Internal: Converted channel update methods to use device options for configuration.

#### 0.4.22-Beta
> *HDHomeRun device detection is now broadcasted per available interface similar to UPnP.

> *HDHomeRun ATSC channel detection now works for non-legacy devices. Legacy may work, but is 
> untested. After running the channel scan, you will need to clear remapped channels for the lineup.

#### 0.4.23-Beta
> *Fixed blind HDHomeRun ATSC tuning.

> *Minor improvements to HDHomeRun network interface acquisition.

> *Better handling of upload id connection problems.

> *FFmpeg now loads asynchronously as early as possible in startup. This results in devices becoming
> ready up to 5 seconds sooner.

> *Improved ClearQAM auto-mapping from HDHomeRun DCT by tuning performance.

> *Internal: Improved HDHomeRun discovery and capture device logging.

> *Internal: Removed continuity check from 0 to 1 for PID 0 from logs.

#### 0.4.24-Beta
> *Fixed legacy HDHomeRun ATSC tuning.

> *Improved responsiveness for low bit-rate channels when stopping.

#### 0.4.25-Beta
> *Upgrades now automatically create a backup of the opendct.properties file on first start. Since
> this is the first release to actually make these kinds of backups, the first backup will not know
> what the last version was, so the backup filename will just have a number attached.

> *Internal: Added configuration file versioning for future use. This number should be incremented
> whenever a property default has been changed and you would like everyone to use the new default.
> It must always be incremented when any property changes to another format. The conversion from the
> old format to the new format must be automatic if possible. Only change property formats when
> absolutely necessary. Be sure to at least mention the change in the log as INFO and it might also
> be nice to post a useful message for the web interface if the change might cause previously
> unexpected behavior. 

> *Internal: Handling of wrapper.log file sizes is now managed and also included in the cleanup as
> a last resort if the disk space goes below the specified threshold.
