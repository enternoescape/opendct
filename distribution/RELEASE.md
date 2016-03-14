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

#### 0.3.17-Stable
> *Upped UDP recieve packet size to 65508.

#### 0.3.18-Stable
> *Ubuntu init.d script was not running on startup for everyone. The install script now runs
> 'update-rc.d -f opendct defaults'. The uninstall script runs 'update-rc.d -f opendct remove'.
> Thanks mikejaner.

> *Addressed runlevels warning message when enabling the Ubuntu init.d script.

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
> *Removed 'non monotonically increasing dts to muxer' handling for this release only.

> *Channel scans now mostly work regardless of how many channels are returned. Note that there can
> still be mapping issues depending on what channel number SageTV has decided to use for the
> provided channel.

> *HDHomeRun ATSC/QAM devices will use an HTTP URL when available. This should help with overall
> stream integrity. This behavior can be turned off by changing the value of hdhr.allow_http_tuning
> to false.

> *HDHomeRun Extend devices can use a transcoding profile when an HTTP URL is available. The profile
> can be set by changing the value of hdhr.extend_transcode_profile to a valid profile. 
>
> Transcode Profiles:
>    * heavy: transcode to AVC with the same resolution, frame-rate, and interlacing as the
>    original stream. For example 1080i60 AVC 1080i60, 720p60 AVC 720p60.
>    * mobile: trancode to AVC progressive not exceeding 1280x720 30fps.
>    * internet720: transcode to low bitrate AVC progressive not exceeding 1280x720 30fps.
>    * internet480: transcode to low bitrate AVC progressive not exceeding 848x480 30fps for
>    16:9 content, not exceeding 640x480 30fps for 4:3 content.
>    * internet360: transcode to low bitrate AVC progressive not exceeding 640x360 30fps for
>    16:9 content, not exceeding 480x360 30fps for 4:3 content.
>    * internet240: transcode to low bitrate AVC progressive not exceeding 432x240 30fps for
>    16:9 content, not exceeding 320x240 30fps for 4:3 content.

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
> a last resort if the disk space goes below the default 1GB threshold.

#### 0.4.26-Beta
> *Enhanced handling of DTS non-monotonic situations.

> *Direct file writing in the FFmpeg consumer now forces the data to disk on each write and verifies
> that the file has not been deleted.

#### 0.4.27-Beta
> *Direct file writing now uses a tunable property that is set to flush every 1MB instead of every
> time FFmpeg uses the write callback. The property name is consumer.ffmpeg.min_direct_flush_size.

#### 0.4.28-Beta
> *Changed InfiniTV HTTP tuning to not leave RTSP stream open on STOP. The timing in the code based 
> on response time from the tuner makes it look like it's faster to leave it open, but after looking
> at total tuning time, it looks like it's the same amount of time.

> *DTS non-monotonic frames are dropped again. After looking at the results of leaving the frames
> in, it appears to create more problems than it helps.

> *FFmpeg write flushing can be disabled by setting consumer.ffmpeg.min_direct_flush_size to -1.

> *Resume logic now includes waiting for all network interfaces that SageTV communicates on. Before 
> it only waited for network interfaces that had capture devices on them.

> *Fixed firewall script for Ubuntu.

> *Internal: FFmpeg logging will now de-duplicate log entries and can filter by parent context.

> *Internal: Fixed a logging issue whereby a listening thread can have 'null' when the name should
> be 'Unknown.'

#### 0.4.29-Beta
> *The FFmpeg read/write transfer buffer size (consumer.ffmpeg.rw_buffer_size) is now 262144 bytes
> by default with a minimum size of 65536. Values less than the maximum RTP packet size received 
> occasionally cause continuity problems for an unknown reason that might be related to why they
> come in a non-standard RTP frame size from the tuner.

> *The consumer is now set per capture device. After running OpenDCT for about 30 seconds and then
> stopping it. Change sagetv.device.\<unique_id\>.consumer to 
> opendct.consumer.FFmpegTransSageTVConsumerImpl on the capture devices that you want to use the new
> software transcoding feature for that capture device.

> *Software transcoding is now available as an experimental feature. Change
> sagetv.device.\<unique_id\>.consumer to opendct.consumer.FFmpegTransSageTVConsumerImpl to enable it.
> The transcoding profiles are per capture device and are not set by default. When no transcoding
> profile is set, the stream is only remuxed. 

> *Set the software transcoding profile by changing sagetv.device.\<unique_id\>.transcode_profile to
> one of the available profiles are stored under C:\ProgramData\OpenDCT\config\transcode on Windows
> and /etc/opendct/conf/transcode on Linux. See profile_example.properties for help on how you can
> create your own. Do not include the .properties extension when setting the profile.
 
> *Software transcoding will limit by weight the number of live transcodes at one time. The value of
> consumer.ffmpeg.transcode_limit is by default your ((total CPU core count) - 1) * 2. In all
> packaged profiles content that does not have a height of 480 has a weight of 2. This might need
> to be adjusted on computers with HyperThreading enabled.
 
> *HTTP failover to RTP is more responsive for non-Prime HDHomeRun devices.

> *Added forcing unlocking for non-Prime HDHomeRun devices if the device is locked by the IP address
> being used to access the device. HTTP tuning can cause the device to be locked by the local IP
> address, but will not unlock or allow it to be changed even though we are on the correct IP
> address.

#### 0.4.30-Beta
> *Fixed the new transcoder HashMap so it doesn't unintentionally retain references. The associated 
> RAM with the buffer would never be returned.

> *HDHomeRun ongoing discovery only sends one packet per interval after the first 3 in a row. This
> helps prevent UDP traffic from being dropped because of all of the responses.

> *Changed periodic flushing so it only happens if it looks like the file isn't growing.

> *Changed automatic lineup creation for ATSC HDHomeRun capture devices so they create a new
> lineup per parent device instead of per tuning type. Automatic pooling also separates ATSC capture
> devices by parent device. This is required because some people have completely different lineups
> between their devices of the same type.

> *Added new lineup update method: COPY. This will copy another lineup to get updates, the
> tunable/ignored channel statuses in the source lineup will not effect the tunable/ignored channels 
> in this lineup. The lineup.address property needs to be the name of the lineup to copy. If the
> lineup to be copied does not exist, this lineup will not be updated and an error will appear in
> the log.

> *Added HDHomeRun QAM HTTP streaming. HDHomeRun ClearQAM tuning maps to arbitrary virtual channels
> starting at 5000 that could change between channel scans. Since we can't reliably tell what we are
> going to get, we need to scan every single virtual channel until we find the one with the
> frequency and program we are looking for. OpenDCT updates the lineup with everything it discovers
> along the way, so we can reduce the possibility of needing to scan again in the future. It is safe
> to run a new scan on the capture device itself since OpenDCT always verifies that the frequency
> and program have not changed before it uses the URL associated with the virtual channel.

> *HDHomeRun Prime can now be loaded via the native detection protocol. This will not change how the
> device is named in SageTV. The following needs to be changed in opendct.properties to allow this:
> 1) Remove schemas-dkeystone-com including the comma in front of it from
>    upnp.new.device.schema_filter_strings_csv
> 2) Change discovery.exp_enabled from false to true
> 3) Remove HDHR3-CC from hdhr.exp_ignore_models
> *) If you only use HDHomeRun devices, you can also turn off UPnP completely by changing
>    upnp.enabled to false.

> *Internal: Moved offline channel scanning registration from the BasicCaptureDevice to
> SageTVManager.

> *Internal: Cleaned up the layout of several methods.

> *Internal: Added DeviceOptions to the FFmpeg consumers.

#### 0.4.31-Beta
> *Fixed HDHomeRun Prime not checking for the correct string for CableCARD presence.

#### 0.4.32-Beta
> *FFmpeg transcoder now also assigns the output stream time base to the codec time base.

> *Internal: Cleaned up some FFmpeg transcoder code.

#### 0.4.33-Beta
> *Reversed: FFmpeg transcoder now also assigns the output stream time base to the codec time base.

> *Added feature that can make 720p content more compatible with H.264 decoders that do not play
> nicely with out of order presentation time stamps. Set consumer.ffmpeg.h264_pts_hack to true in
> opendct.properties to enable the compatibility hack. This feature only works when using
> FFmpegTransSageTVConsumerImpl.

> *Removed trace logging from the FFmpeg transcoder.

> *Audio and video stream metadata such as language is now copied when available into the FFmpeg
> output stream when using FFmpegTransSageTVConsumerImpl.

> *Fixed de-duplicating FFmpeg logging. It was broken when phantom duplicates where fixed.

> *HDHomeRun native capture device now has it's own property for how long it will wait before it
> returns OK to SageTV while tuning a channel. The property is hdhr.wait_for_streaming and defaults
> to 5000(ms).

> *Fixed the profiles in the Windows installer so they are lowercase.

#### 0.4.34-Beta
> *CONFIGURATION UPGRADE: The first time you run this version, it will upgrade your current
> configuration. What this means is a few properties in opendct.properties will be removed and some
> properties will be modified so that you are using the best configuration. The HDHomeRun Prime will
> be switched to being discovered via its native discovery protocol. This upgrade will only happen
> once. The new configuration is not fully compatible with OpenDCT versions earlier than 0.4.30. As
> of 0.4.25 the properties are backed up on upgrade, so if this upgrade breaks anything for you and
> you did not make a copy of your old configuration, you should have a file named
> opendct.properties.0.4.xx-x containing your old configuration that you can use to undo the 
> changes.

> *The following properties no longer do anything, will confuse people and will be removed: 
> hdhr.always_force_lockkey, 
> sagetv.device.parent.\<parent_id\>.consumer, 
> sagetv.device.parent.\<parent_id\>.channel_scan_consumer

> *'schemas-dkeystone-com' will be removed from 'upnp.new.device.schema_filter_strings_csv'

> *'HDHR3-CC' will be removed from 'hdhr.exp_ignore_models'

> *'discovery.exp_enabled' will be set to 'true'

> *Now all HDHomeRun devices available on the network will be discovered and monitored by OpenDCT
> by default.

> *ATSC HDHomeRun support is now a fully supported configuration. It is no longer experimental.

> *The channel map can now be set for HDHomeRun devices via the property
> sagetv.device.parent.\<parent_id\>.channel_map if desired. Leave this property blank if you do not
> want OpenDCT to change your channel map for you. This is mostly a convenience for legacy devices.
> This setting has no effect on CableCARD devices.

> *HDHomeRun discovery port can be set to a specific port by changing the property 
> hdhr.broadcast_port. If the value is less than 1024, the port will be chosen automatically. The
> default is 64998. The static port makes it easier to do port-based firewall rules.

> *Ubuntu init.d script was not running on startup for everyone. The install script now runs
> 'update-rc.d -f opendct defaults'. The uninstall script runs 'update-rc.d -f opendct remove'.
> Thanks mikejaner.

> *Addressed runlevels warning message when enabling the Ubuntu init.d script.

#### 0.4.35-Beta
> *Fixed a rare null pointer exception in the FFmpeg processor.

#### 0.4.36-RC1
> *Fixed opendct.properties was being version backed up every single time the program was started.
> It now only does a backup on version and/or config upgrade like it was supposed to do. 

> *Improved audio stream selection for FFmpeg transcoder. Bitrate when available is now a
> tie-breaker which helps with channels like PBS that have three two channel audio streams.

> *Fixed the COPY lineup update method not saving newly added channels.

> *Fixed a potentially very long hang when tuning InfiniTV devices that have rebooted.

> *Cleaned up FFmpeg logging a little more and improved atomicity of deduplication.

> *Detection speed increase for FFmpeg transcoder.

> *Cleaned up some HDHomeRun communications logging.

> *Improved handling of FFmpeg transcoder direct file writing failures.

> *Improved re-tuning efficiency.