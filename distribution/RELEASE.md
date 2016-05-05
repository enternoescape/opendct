## OpenDCT Release Notes

#### 0.3.6-Beta
> *Initial public beta release.

#### 0.3.7-Beta
> *The network encoder will now poll the the incoming data every 4
> seconds for activity. If the data has stopped streaming, it will
> re-tune the DCT. It will continue to re-tune the DCT until SageTV
> tells it to STOP or it successfully re-tunes the channel. This is
> needed because some devices can reboot in the middle of streaming and
> SageTV takes too long to react to the halted recording.

#### 0.3.8-Beta
> *Added firewall configurations to the install packages.
> **Windows will install firewall exceptions for you if you are using
> the Windows Firewall.
> **CentOS 7 and Fedora 22 will provide a firewalld service that can be
> enabled.
> **Ubuntu 14.04 will provide a script to enable rules via ufw.
> **Changed default RTP receiving port change to 8300-8500 since the
> previous default could conflict with the SageTV discovery port.

> *Added optional support for the HDHomeRun lock to be taken even if
> another device has already locked it.

#### 0.3.9-Beta
> *Removed loopback devices from the auto-detection of the local
> interface.

> *Added custom property on a per parent property to manually set the IP
> address of the local interface to use for device communication.

#### 0.3.10-Beta
> *Relaxed the source protocol requirement when creating a new UPnP
> capture device.

> *Added wrapper.exe to firewall exceptions on Windows installer.

#### 0.3.11-Beta
> *Fixed a regression in local interface auto-detection due to a GitHub
> commit failure.

#### 0.3.12-Beta
> *Fixed local interface auto-detection and tested under several
> conditions. It should now work in all normal situations.

#### 0.3.13-Beta
> *Fixed some error handling issues in RTSP configuration.

#### 0.3.14-Beta
> *Fixed error handling issues with corrupt lineups.

#### 0.3.15-Stable
> *No changes. This is a final release. I am considering this stable
> since we have not found any new issues relating to overall program
> functionality and all major issues have been resolved. There will be
> no further updates to this version unless something critical is
> discovered and the current latest version is not yet stable.

#### 0.3.16-Stable
> *Backported: Fixed an unreliable timeout issue with the re-tuning
> monitoring thread under the right conditions.

#### 0.3.17-Stable
> *Upped UDP recieve packet size to 65508.

#### 0.3.18-Stable
> *Ubuntu init.d script was not running on startup for everyone. The
> install script now runs 'update-rc.d -f opendct defaults'. The
> uninstall script runs 'update-rc.d -f opendct remove'.
> Thanks mikejaner.

> *Addressed runlevels warning message when enabling the Ubuntu init.d
> script.

#### 0.4.0-Beta
> *Added tuner pooling as an experimental feature turned off by default.
> Set the property pool.enabled=true to enable it. There are no
> fundamental issues, but this is it's first release, so it will be
> considered experimental for now. ClearQAM and DCT devices are
> automatically put in their own pools. Tuning is based on merit, then
> if the device is locked externally or not. Do not combine capture
> devices in a pool that cannot tune the same channels.  

> *Cleaned up some logging excess with the InfiniTV channel updates.

> *Fixed duplicate detection removing all channels but one when Prime is
> in ClearQAM mode.

> *Added a channels.prime.enable_all_channels=true property that when
> true and and the lineup is detected to not be ClearQAM, all channels
> are assumed tunable. This is a safe setting since the list is a result
> of a channel scan unlike the InfiniTV. This allows you to do a channel
> scan from SageTV and get exactly the list on the Prime returned up to
> 159 channels (SageTV limitation).

> *Added the properties upnp.service.configuration.ignore_interfaces_csv
> and upnp.service.configuration.ignore_local_ip_csv so you can exclude
> interfaces from UPnP detection by name and/or by local IP address.

> *Fixed line endings on Windows for exceptions logged by log4j2.

> *Internal: Removed concurrent connection checking since it doesn't do
> anything helpful and is now one less decision branch.

> *Internal: Fixed a few methods that might not run in all cases on Java
> 1.7.

> *Internal: Fixed the Gradle script so EOL in Linux packages will
> always be correct regardless of how the files were downloaded. 

#### 0.4.1-Beta
> *Added handling for Prime urls with copy protection so they don't get
> enabled in the lineup.

> *Fix a problem when tuning the frequency 0 that could potentially
> cause a device to never unlock.

#### 0.4.2-Alpha
> *Modified circular buffer to accommodate seeking after detection.
> Testing has been very successful, but watch this release very
> carefully and report any issues with a log attached.

#### 0.4.3-Alpha
> *Added and tested seek error handling to the circular buffer. More
> testing needed.

#### 0.4.4-Alpha
> *Still alpha due to the circular buffer changes.

> *Added monitoring and validation for RTCP. Currently no responses are
> generated. This is just so we know if it's happening.

> *Internal: Added channel number to consumer so it can change it's
> behavior optionally based on the currently tuned channel.

> *Internal: Logging line ending and space usage improvements.

#### 0.4.5-Alpha
> *Fixed issue with RTCP port not closing at the right time.

> *Added logging for initial packet losses, less than 12 bytes packet
> losses and packet size limit possibly exceeded warnings.

> *Increased UDP buffer from 1500 to 65508 which is the largest size any
> UDP packet should ever be.

#### 0.4.6-Alpha
> *Changed UDP buffer to start at 1500 and dynamically resize to fit the
> current situation. It saves the final size and uses it the next time
> so detection only needs to happen once.

#### 0.4.7-Alpha
> *Addresses a null pointer issue when tuning using UPnP.

#### 0.4.8-Alpha
> *Fixed an unreliable timeout issue with the re-tuning monitoring
> thread under the right conditions.

> *Changed wrapper.conf to not restart on exit conditions that can't
> possibly be corrected by restarting the program.

> *Fixed an issue with HDHomeRun program detection not returning a value
> when it had not yet reached the timeout.

#### 0.4.9-Beta
> *Returned to beta now that the new buffer has been in use for about a
> week without any complaints relating to the buffer. The changes were
> not intense enough to warrant any further testing. We now have a junit
> test to help confirm the integrity of the buffer.

> *Fixed speed and line termination issue when returning PROPERTIES.

#### 0.4.10-Beta
> *Added feature to clean up log files based on available free space.
> The default minimum free space is 1GB. Free space could be a very real
> issue if logging gets intense for any reason and logging should never
> be the cause of your server failing. This runs after log files are
> deleted by date.

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
> *Windows installer now will not fail on firewall rules on upgrades. If
> you need to roll back to 0.4.12, you will need to install 0.4.11
> first, then 0.4.12 to get around the firewall upgrade issues.

> *Addressed possibility that the RTP stall monitoring thread could
> continue running even after it should have stopped. This has no
> serious consequence other than annoying logged warnings.

> *Fixed a rare null pointer issue in the FFmpeg consumer. [ws]

> *Addressed standby issue with UPnP devices not responding quickly
> enough.

> *Fixed standby network listener to work with the new standby code. It
> will now wait for the network to be available before resuming any
> other activities like it did before the update.

#### 0.4.14-Beta
> *Changed over to a more public beta.

#### 0.4.15-Beta
> *Added tunable thread priorities for consumer and producer threads.

> *Internal: Removed desired PID from consumer interface since it can be
> unreliable.

#### 0.4.16-Beta
> *Limited program detection to 2 seconds. Normally it's detected in
> less than 200ms, so this should not introduce any new issues and
> should keep the capture device from returning so late that the
> circular buffer is overflowing.

> *Added more resilience to the HDHomeRun native communications. It will
> now try up to 5 times at 1 second intervals to reach a device before
> it gives up.

> *Improved fragmented HDHomeRun response support.

#### 0.4.17-Beta
> *HDHomeRun packet length was not accounting for the 4 byte header and
> 4 byte CRC when ensuring we don't have a fragmented packet.

> *Addressed more possible situations for HDHomeRun communication
> failures.

> *Fixed single character HDHomeRun response handling.

> *Added a byte array queue buffer to the circular buffer to help with
> data overflow situations. It will queue up to 4x the size of the
> buffer in a data overflow situation.

#### 0.4.18-Beta
> *To be more compliant with Debian, all Linux future packages now use
> /etc/opendct/conf for configuration. That includes opendct.properties.
> The install scripts will detect old configurations and copy your
> /opt/opendct/conf folder to /etc/opendct/conf, then rename
> /opt/opendct/conf to /opt/opendct/conf.moved in the /opt/opendct/conf
> folder. The copy is triggered on the existence of /opt/opendct/conf so
> it will only trigger once. You can safely delete
> /opt/opendct/conf.moved if you want to clean up.

> *Fedora 23 is now officially supported.

> *Internal: Updated Linux install package build scripts for better
> compliance with current standards.

> *Optimized overflow queue and removed debug code in buffer from production code.

> *Changed how InfiniTV ClearQAM tuning stops the packet stream without
> completely tearing down RTSP. The old way could put InfiniTV 6 devices
> in a state that requires a soft reboot to be able to detect programs
> available on the tuned frequency again. This issue happens with the
> CGMS-A fix firmware installed.

#### 0.4.19-Beta
> *Added detection for old InfiniTV 4 firmwares to prevent http tuning
> from being able to be selected since it will not work.

> *Upload ID is now disabled by default since it might cause Java heap
> problems for some setups. Current installations upgrading will retain
> the enabled setting.

> *Improved UPnP search thread.

> *Improved FFmpeg detection times and stream selection accuracy. [ws]

> *Experimental support for MPEG-PS.

> *OpenDCT when tuning a new channel now doesn't respond to SageTV until
> FFmpeg has completed detection or 5 seconds have passed. This is
> necessary to support proper detection of MPEG-PS by SageTV.

#### 0.4.20-Beta
> *Added check at startup for at least one network interface to be
> present before continuing startup. This has a default timeout of 2
> minutes.

> *Circular buffer now expands dynamically during FFmpeg detection. To
> simplify things, the max probe size is now always the requested
> circular buffer size times 3 then minus 1MB. That way the detection
> will always be able to take advantage of the largest size the buffer
> could possibly be.

> *Official support for MPEG-PS.

> *Experimental support for HDHomeRun ATSC and ClearQAM capture devices.
> Channel scanning is not an option and will likely not be an option for
> a while because of how SageTV maps ATSC channels. To enable support,
> you must enable the new capture device detection method. To do this,
> change discovery.exp_enabled from false to true in opendct.properties.
> The new detection method currently excludes the Prime from detection
> so you will not have any conflicts when UPnP loads the same device.

> *Fixed the extracting of the frequency from a currently tuned channel.
> It was removing the last 3 zeros, while the current approach is to
> keep them. The removal was to keep things consistent between InfiniTV
> and Prime, but now 3 zeros are added to InfiniTV aquired lineups and
> are only removed if tuning an InfiniTV device.

> *Internal: Created framework for capture device detection that enables
> the addition of new detection methods to be more like creating a
> plugin.

> *Internal: HDHomeRun native detection method completely implemented.

> *Internal: Enabled JSW configuration reload on restart for future
> automatic/requested upgrading via web interface. This method of
> updating is not currently in use and will be disabled by default.

#### 0.4.21-Beta
> *HDHomeRun will now use any local UDP port that's available for
> discovery. 

> *HDHomeRun legacy devices can now be tuned in us-bcast mode.

> *Internal: Converted channel update methods to use device options for
> configuration.

#### 0.4.22-Beta
> *HDHomeRun device detection is now broadcasted per available interface
> similar to UPnP.

> *HDHomeRun ATSC channel detection now works for non-legacy devices.
> Legacy may work, but is untested. After running the channel scan, you
> will need to clear remapped channels for the lineup.

#### 0.4.23-Beta
> *Fixed blind HDHomeRun ATSC tuning.

> *Minor improvements to HDHomeRun network interface acquisition.

> *Better handling of upload id connection problems.

> *FFmpeg now loads asynchronously as early as possible in startup. This
> results in devices becoming ready up to 5 seconds sooner.

> *Improved ClearQAM auto-mapping from HDHomeRun DCT by tuning
> performance.

> *Internal: Improved HDHomeRun discovery and capture device logging.

> *Internal: Removed continuity check from 0 to 1 for PID 0 from logs.

#### 0.4.24-Beta
> *Fixed legacy HDHomeRun ATSC tuning.

> *Improved responsiveness for low bit-rate channels when stopping.

#### 0.4.25-Beta
> *Removed 'non monotonically increasing dts to muxer' filtering for
> this release only.

> *Channel scans now mostly work regardless of how many channels are
> returned. Note that there can still be mapping issues depending on
> what channel number SageTV has decided to use for the provided
> channel.

> *HDHomeRun ATSC/QAM devices will use an HTTP URL when available. This
> should help with overall stream integrity. This behavior can be turned
> off by changing the value of hdhr.allow_http_tuning to false.

> *HDHomeRun Extend devices can use a transcoding profile when an HTTP
> URL is available. The profile can be set by changing the value of
> hdhr.extend_transcode_profile to a valid profile. 
>
> Transcode Profiles:
>    * heavy: transcode to AVC with the same resolution, frame-rate, and interlacing as the original stream. For example 1080i60 AVC 1080i60, 720p60 AVC 720p60.
>    * mobile: trancode to AVC progressive not exceeding 1280x720 30fps.
>    * internet720: transcode to low bitrate AVC progressive not exceeding 1280x720 30fps.
>    * internet480: transcode to low bitrate AVC progressive not exceeding 848x480 30fps for 16:9 content, not exceeding 640x480 30fps for 4:3 content.
>    * internet360: transcode to low bitrate AVC progressive not exceeding 640x360 30fps for 16:9 content, not exceeding 480x360 30fps for 4:3 content.
>    * internet240: transcode to low bitrate AVC progressive not exceeding 432x240 30fps for 16:9 content, not exceeding 320x240 30fps for 4:3 content.

> *Upgrades now automatically create a backup of the opendct.properties
> file on first start. Since this is the first release to actually make
> these kinds of backups, the first backup will not know what the last
> version was, so the backup filename will just have a number attached.

> *Internal: Added configuration file versioning for future use. This
> number should be incremented whenever a property default has been
> changed and you would like everyone to use the new default. It must
> always be incremented when any property changes to another format. The
> conversion from the old format to the new format must be automatic if
> possible. Only change property formats when absolutely necessary. Be
> sure to at least mention the change in the log as INFO and it might
> also be nice to post a useful message for the web interface if
> the change might cause previously unexpected behavior. 

> *Internal: Handling of wrapper.log file sizes is now managed and also
> included in the cleanup as a last resort if the disk space goes below
> the default 1GB threshold.

#### 0.4.26-Beta
> *Enhanced handling of DTS non-monotonic situations.

> *Direct file writing in the FFmpeg consumer now forces the data to
> disk on each write and verifies that the file has not been deleted.

#### 0.4.27-Beta
> *Direct file writing now uses a tunable property that is set to flush
> every 1MB instead of every time FFmpeg uses the write callback. The
> property name is consumer.ffmpeg.min_direct_flush_size.

#### 0.4.28-Beta
> *Changed InfiniTV HTTP tuning to not leave RTSP stream open on STOP.
> The timing in the code based on response time from the tuner makes it
> look like it's faster to leave it open, but after looking at total
> tuning time, it looks like it's the same amount of time.

> *DTS non-monotonic frames are dropped again. After looking at the
> results of leaving the frames in, it appears to create more problems
> than it helps.

> *FFmpeg write flushing can be disabled by setting
> consumer.ffmpeg.min_direct_flush_size to -1.

> *Resume logic now includes waiting for all network interfaces that
> SageTV communicates on. Before it only waited for network interfaces
> that had capture devices on them.

> *Fixed firewall script for Ubuntu.

> *Internal: FFmpeg logging will now de-duplicate log entries and can
> filter by parent context.

> *Internal: Fixed a logging issue whereby a listening thread can have
> 'null' when the name should be 'Unknown.'

#### 0.4.29-Beta
> *The FFmpeg default read/write transfer buffer size
> (consumer.ffmpeg.rw_buffer_size) is now 262144 bytes by default with a
> minimum size of 65536. Values less than the maximum RTP packet size
> received occasionally cause continuity problems for an unknown reason
> that might be related to why they come in a non-standard RTP frame
> size from the tuner.

> *The consumer is now set per capture device. After running OpenDCT for
> about 30 seconds and then stopping it. Change
> sagetv.device.\<unique_id\>.consumer to
> opendct.consumer.FFmpegTransSageTVConsumerImpl on the capture devices
> that you want to use the new software transcoding feature for that
> capture device.

> *Software transcoding is now available as an experimental feature.
> Change sagetv.device.\<unique_id\>.consumer to
> opendct.consumer.FFmpegTransSageTVConsumerImpl to enable it. The
> transcoding profiles are per capture device and are not set by
> default. When no transcoding profile is set, the stream is only
> remuxed. 

> *Set the software transcoding profile by changing
> sagetv.device.\<unique_id\>.transcode_profile to one of the available
> profiles are stored under C:\ProgramData\OpenDCT\config\transcode on
> Windows and /etc/opendct/conf/transcode on Linux. See
> profile_example.properties for help on how you can create your own. Do
> not include the .properties extension when setting the profile.
 
> *Software transcoding will limit by weight the number of live
> transcodes at one time. The value of consumer.ffmpeg.transcode_limit
> is by default your ((total CPU core count) - 1) * 2. In all packaged
> profiles content that does not have a height of 480 has a weight of 2.
> This might need to be adjusted on computers with HyperThreading
> enabled.
 
> *HTTP failover to RTP is more responsive for non-Prime HDHomeRun devices.

> *Added forcing unlocking for non-Prime HDHomeRun devices if the device
> is locked by the IP address being used to access the device. HTTP
> tuning can cause the device to be locked by the local IP address, but
> will not unlock or allow it to be changed even though we are on the
> correct IP address.

#### 0.4.30-Beta
> *Fixed the new transcoder HashMap so it doesn't unintentionally retain
> references. The associated RAM with the buffer would never be
> returned.

> *HDHomeRun ongoing discovery only sends one packet per interval after
> the first 3 in a row. This helps prevent UDP traffic from being
> dropped because of all of the responses.

> *Changed periodic flushing so it only happens if it looks like the
> file isn't growing.

> *Changed automatic lineup creation for ATSC HDHomeRun capture devices
> so they create a new lineup per parent device instead of per tuning
> type. Automatic pooling also separates ATSC capture devices by parent
> device. This is required because some people have completely different
> lineups between their devices of the same type.

> *Added new lineup update method: COPY. This will copy another lineup
> to get updates, the tunable/ignored channel statuses in the source
> lineup will not effect the tunable/ignored channels in this lineup.
> The lineup.address property needs to be the name of the lineup to
> copy. If the lineup to be copied does not exist, this lineup will not
> be updated and an error will appear in the log.

> *Added HDHomeRun QAM HTTP streaming. HDHomeRun ClearQAM tuning maps to
> arbitrary virtual channels starting at 5000 that could change between
> channel scans. Since we can't reliably tell what we are going to get,
> we need to scan every single virtual channel until we find the one
> with the frequency and program we are looking for. OpenDCT updates the
> lineup with everything it discovers along the way, so we can reduce
> the possibility of needing to scan again in the future. It is safe to
> run a new scan on the capture device itself since OpenDCT always
> verifies that the frequency and program have not changed before it
> uses the URL associated with the virtual channel.

> *HDHomeRun Prime can now be loaded via the native detection protocol.
> This will not change how the device is named in SageTV. The following
> needs to be changed in opendct.properties to allow this:
> 1) Remove schemas-dkeystone-com including the comma in front of it
>    from upnp.new.device.schema_filter_strings_csv
> 2) Change discovery.exp_enabled from false to true
> 3) Remove HDHR3-CC from hdhr.exp_ignore_models
> *) If you only use HDHomeRun devices, you can also turn off UPnP
>    completely by changing
>    upnp.enabled to false.

> *Internal: Moved offline channel scanning registration from the
> BasicCaptureDevice to SageTVManager.

> *Internal: Cleaned up the layout of several methods.

> *Internal: Added DeviceOptions to the FFmpeg consumers.

#### 0.4.31-Beta
> *Fixed HDHomeRun Prime not checking for the correct string for
> CableCARD presence.

#### 0.4.32-Beta
> *FFmpeg transcoder now also assigns the output stream time base to the
> codec time base.

> *Internal: Cleaned up some FFmpeg transcoder code.

#### 0.4.33-Beta
> *Reversed: FFmpeg transcoder now also assigns the output stream time
> base to the codec time base.

> *Added feature that can make 720p content more compatible with H.264
> decoders that cannot keep up with 60fps broadcast. Set
> consumer.ffmpeg.h264_pts_hack to true in opendct.properties to enable
> the compatibility hack. This feature only works when using
> FFmpegTransSageTVConsumerImpl.

> *Removed trace logging from the FFmpeg transcoder.

> *Audio and video stream metadata such as language is now copied when
> available into the FFmpeg output stream when using
> FFmpegTransSageTVConsumerImpl.

> *Fixed de-duplicating FFmpeg logging. It was broken when phantom
> duplicates where fixed.

> *HDHomeRun native capture device now has it's own property for how
> long it will wait before it returns OK to SageTV while tuning a
> channel. The property is hdhr.wait_for_streaming and defaults to
> 5000(ms).

> *Fixed the profiles in the Windows installer so they are lowercase.

#### 0.4.34-Beta
> *CONFIGURATION UPGRADE: The first time you run this version, it will
> upgrade your current configuration. What this means is a few
> properties in opendct.properties will be removed and some properties
> will be modified so that you are using the best configuration. The
> HDHomeRun Prime will be switched to being discovered via its native
> discovery protocol. This upgrade will only happen once. The new
> configuration is not fully compatible with OpenDCT versions earlier
> than 0.4.30. As of 0.4.25 the properties are backed up on upgrade, so
> if this upgrade breaks anything for you and you did not make a copy of
> your old configuration, you should have a file named
> opendct.properties.0.4.xx-x containing your old configuration that you
> can use to undo the changes.

> *The following properties no longer do anything, will confuse people
> and will be removed: 
> hdhr.always_force_lockkey, 
> sagetv.device.parent.\<parent_id\>.consumer, 
> sagetv.device.parent.\<parent_id\>.channel_scan_consumer

> *'schemas-dkeystone-com' will be removed from
> 'upnp.new.device.schema_filter_strings_csv'

> *'HDHR3-CC' will be removed from 'hdhr.exp_ignore_models'

> *'discovery.exp_enabled' will be set to 'true'

> *Now all HDHomeRun devices available on the network will be discovered
> and monitored by OpenDCT by default.

> *ATSC HDHomeRun support is now a fully supported configuration. It is
> no longer experimental.

> *The channel map can now be set for HDHomeRun devices via the property
> sagetv.device.parent.\<parent_id\>.channel_map if desired. Leave this
> property blank if you do not want OpenDCT to change your channel map
> for you. This is mostly a convenience for legacy devices. This setting
> has no effect on CableCARD devices.

> *HDHomeRun discovery port can be set to a specific port by changing 
> the property hdhr.broadcast_port. If the value is less than 1024, the
> port will be chosen automatically. The default is 64998. The static
> port makes it easier to do port-based firewall rules.

> *Ubuntu init.d script was not running on startup for everyone. The
> install script now runs 'update-rc.d -f opendct defaults'. The
> uninstall script runs 'update-rc.d -f opendct remove'.
> Thanks mikejaner.

> *Addressed runlevels warning message when enabling the Ubuntu init.d
> script.

#### 0.4.35-Beta
> *Fixed a rare null pointer exception in the FFmpeg processor.

#### 0.4.36-RC1
> *Fixed opendct.properties was being version backed up every single
> time the program was started. It now only does a backup on version
> and/or config upgrade like it was supposed to do. 

> *Improved audio stream selection for FFmpeg transcoder. Bitrate when
> available is now a tie-breaker which helps with channels like PBS that
> have three two channel audio streams.

> *Fixed the COPY lineup update method not saving newly added channels.

> *Fixed a potentially very long hang when tuning InfiniTV devices that
> have rebooted.

> *Cleaned up FFmpeg logging a little more and improved atomicity of
> deduplication.

> *Detection speed increase for FFmpeg transcoder.

> *Cleaned up some HDHomeRun communications logging.

> *Improved handling of FFmpeg transcoder direct file writing failures.

> *Improved re-tune monitoring efficiency for HDHomeRun.

#### 0.4.37-RC2
> *FFmpeg Linux logging is now enabled by default. It was disabled
> before because it could cause the JVM to crash. The latest versions of
> FFmpeg do not appear to still cause this issue.

> *Fixed FFmpeg Linux logging filter so null messages are not written to
> the log.

> *Addressed higher than 33-bit dts values breaking the dts ordering
> filter.

> *FFmpeg transcoder if the dts value is not monotonic, but the pts
> value is, the dts value is adjusted to be monotonic.

> *Pre-release support for Ubuntu 16.04 LTS systemd is now a part of the
  Debian package.

#### 0.4.38-RC3
> *FFmpeg transcoding consumer can now fix invalid dts issues.

> *Improved HDHomeRun discovery so it only runs at startup, standby and
  if a device is unreachable. This was done because the discovery
  feedback can still very much interfere with active recordings using
  UDP if you have many HDHomeRun devices on your network.
   
> *Added more checks for unlikely, but allegedly possible null
  conditions in FFmpeg transcoder.
  
> *Auto-ClearQAM mapping by tuning is now enabled by default. It is
  unlikely to be a problem since it only tunes if it doesn't know the 
  channel and it will not unlock an in-use capture device.
  
> *Increased wait from 2 to 5 seconds for InfiniTV http timeout.

> *The default wait before returning OK to SageTV regardless of if the
  consumer says it's streaming or not is now 8500ms for all capture
  device implementations. 5000ms was too low.

> *Fixed the file not being closed before re-opening when the async
  writer in the FFmpeg transcoder fails to write. Thanks troll5501.

> *Fixed a possibility of losing the filename while re-tuning.

> *Fixed another possible InfiniTV tuning long wait while recovering
  from the device not being available. 

> *Capture device pooling is now considered stable.

#### 0.4.39-RC4
> *Reworked FFmpeg transcoder dts correction logic so it will be less
> aggressive.

> *Changed writer for FFmpeg consumer to ensure that files don't
  accidentally get re-opened when closing while a write is still in 
  progress.
  
> *Changed default consumer to the FFmpeg transcoding consumer.

> *Internal: some code cleanup.

#### 0.4.40-RC5
> *Duplicate log entries are now reported at the same log level as the
  duplicates so that you don't see the duplicate entry, yet no previous
  logging associated.
  
> *Dts corrective code will now transparently restart the muxer if more
> than 30 errors are encountered in under a second. Most players can
> handle this, the FFmpeg remuxer cannot deal with timestamps that are
> not monotonic.

> *Removed 33-bit dts limit checking since it appears that FFmpeg will
> take care of that automatically.

#### 0.4.41-RC6
> *Removed remuxer restarting code since it technically isn't needed if
  the dts can go as high as it wants and the muxer will continue to work
  with it.
  
> *Increased dts threshold to differences of over 10 seconds.

> *The threshold will now dynamically increase if more than 50
  corrections are made over 5 seconds.
  
#### 0.4.42-RC7
> *Changed the old FFmpeg consumer to only do == last dts discarding
  since that's the way it used to do it. It is still no longer the
  default.
 
> *Changed the dts fixing code so it will deal with streams
  individually, then sync them when it makes sense or a limit is
  reached.

> *The local IP address override is now created, but not automatically
  populated. If needed, you can set the IP address, but while it is
  blank, it will be determined automatically.
  
> *FFmpeg logger now recycles most temporary objects and avoids creating
  strings as much as possible.
  
> *Adjusted some other polling times to reduce the number of shortly
  lived objects being created and overall CPU utilization.
  
> *Profiled streaming code and optimized when possible.

> *Internal: Removed some logging stack traces that don't need to exist.

#### 0.4.43-RC8
> *Java heap size is now defined at 128MB minimum and 768MB maximum.

> *Removed 33-bit corrections. 

> *FFmpeg logger now calls out what exactly repeated.

> *Internal: Added more commenting on what everything does in the new
> FFmpeg remuxer.

#### 0.4.45-RC9
> *Fixed incorrect variable order for repeat FFmpeg logging.

> *Added additional length check in FFmpeg enhanced logging to prevent
> wasting cycles parsing an entry that isn't likely to be parsed
> successfully.

> *Fixed pts being adjusted incorrectly under some circumstances.

> *Socket server now doesn't try to register the loopback address for
> standby support.

> *Changed over to using G1GC for garbage collection.

> *Defined native crash logging location on Linux.

> *Internal: Removed some logging stack traces that don't need to exist.

#### 0.4.46-Stable
> *Features indicated as experimental in 0.4 may still have issues that
> might only be addressed in 0.5+. 0.4 changes after this release will
> only address major issues that do not require significant changes.

> *Fixed HDHomeRun devices not being re-detected after standby.

> *Added a small performance optimization to reduce the number reads
> performed by the transcoding FFmpeg consumer.

> *Added handling for a native error when getting all currently
> available network interfaces. 
  
> *Removed some tuning code that never executes anymore due to the
  delays already in place to support MPEG-PS.
  
> *Handled initial file creation failure so it returns ERROR instead of
> pointlessly pushing through.

> *Fixed issue with upload id that would cause the file be overwritten
> from the beginning if there was a disconnection.

#### 0.4.47-Stable
> *Changed starting dts tolerance from 450000 cycles to 3500000 to
> support the large gaps in Music Choice.

#### 0.4.48-Stable
> *CONFIGURATION UPGRADE: The first time you run this version, it will
> upgrade your current configuration. Anything still referencing
> FFmpegSageTVConsumerImpl will be changed over to
> FFmpegTransSageTVConsumerImpl. hdhr.wait_for_streaming and
> upnp.dct.wait_for_streaming will be changed to 15000.

> *Renamed FFmpegSageTVConsumerImpl to FFmpegOldSageTVConsumerImpl to
> force old installations to use the newer remuxer unless explicitly set
> otherwise.

> *Increased initial bytes written before returning to SageTV up to 1MB.

> *Removed small performance optimization to reduce the number reads
> performed by the transcoding FFmpeg consumer.

> *Added some logging for producer and consumer stalling.

> *Increased re-tune timeout to 16000ms.

#### 0.5.0-Beta
> *CCExtractor support for FFmpeg transcoder has been added. This
> feature only works when OpenDCT is configured to write directly (not
> upload id). To enable, change consumer.ffmpeg.ccextractor_enabled to
> true. Some channels in H.264 do not work well with CCExtractor. This
> feature was added primarily so that if you transcode the video, you
> don't loose the captions.
  
> *Added opendct.consumer.DynamicConsumerImpl as the new default
> consumer. To change over to the new consumer from a previous
> installation, change the values of sagetv.device.\<unique_id\>.consumer
> to opendct.consumer.DynamicConsumerImpl.

> *InfiniTV devices now have their own capture device and tuning via
> UPnP is considered deprecated. Discovery is still via UPnP.

> *UPnP detection is now on demand by default similar to how the
> HDHomeRun detection currently works. This cuts down on traffic.

> *New HDHomeRun lineups are now assigned the device id instead of IP
> address so if the IP address changes, the lineup knows where to now
> look.

> *All devices will now update their IP address if it changes.
  
> *The internal circular buffer is now based completely off heap. The
> raw stream data now never enters the JVM. The circular buffers are
> also recycled so they don't need to be re-allocated on start and stop.
 
> *Devices that use HTTP for streaming now use a direct byte buffer for
> communications by default. To move to the new communications method,
> change sagetv.device.parent.\<parent_id\>.http.producer to
> opendct.producer.NIOHTTPProducerImpl.

> *Removed EIA information from channels since it is never used.

> *Changed Linux priority from -19 to -5 since -19 puts the process
> higher than some network processes which might be a bad idea.

> *Too many to list optimizations for the remuxer to greatly reduce
> object creation resulting in a measurable increase in overall
> throughput.

> *Trying out fastutils for some of the bigger hash maps where possible.

> *Added Music Choice transcoding profile (ultrafast_mc). This matches
> Music Choice channels based on their relatively unique
> characteristics. It is possible to have an SD channel come in as a
> false positive, but it should be rare.

#### 0.5.1-Beta
> *CONFIGURATION UPGRADE: The first time you run this version, it will
> upgrade your current configuration. Anything still referencing
> FFmpegSageTVConsumerImpl will be changed over to DynamicConsumerImpl.
> This will not change anything set to FFmpegTransSageTVConsumerImpl.

> *Renamed FFmpegSageTVConsumerImpl to FFmpegOldSageTVConsumerImpl to
> force old installations to use the newer remuxer unless explicitly set
> otherwise.

> *Fixed DynamicConsumerImpl not populating the
> sagetv.device.\<unique_id\>.consumer properties if the property did
> not already exist.

> *Changed startup and resume from standby to not rely on a known number
> of capture devices. It will now check every time a new capture device
> is loaded to see if it's the one SageTV is requesting until the
> required devices timeout
> (sagetv.device.global.required_devices_loaded_timeout_ms). This should
> also result in faster resume tuning times since the program no longer
> needs to wait for all required devices to be loaded.

> *Increased wait for response from InfiniTV devices to 10 seconds.

> *Amount of data required to be written before returning OK to SageTV
> is now based on how much was needed for FFmpeg to detect the streams.

> *Added some FFmpeg optimizations to ensure that a key video frame is
> at the very start of the container when possible. This appears to
> provide additional assurance so that MPEG-PS files are detected 
> correctly.  

> *File growth monitoring is now more aggressive.

> *Added some logging to tuning monitor.

> *Fixed reading potentially getting stuck during detection.

> *Added new option to disable HDHomeRun ClearQAM remapping and
> frequency/program verification for configuration without a reference
> tuner with a CableCARD installed. To disable the remapping feature,
> change the value of hdhr.allow_qam_remapping to false.

#### 0.5.2-Beta
> *Fixed null pointer exception related to logging when file doesn't
> exist on re-tune.

> *Write out larger chunks of data per write on FFmpeg transcoder.

> *Added a timing component to checking the file length since it's an
> expensive operation when we are just trying to catch up.

> *Fixed failure to deallocate the input context when detection fails
> and the buffer is reset.

> *Added handling for when Cling is unable to open the requested port.

#### 0.5.3-Beta
> *Added new "Generic HTTP" consumer. This consumer should work with
> any non-SSL URL that is actually a stream when downloaded. To create
> entries for these devices, create names for them under the property
> generic.http.device_names_csv in opendct.properties separated by
> commas. (Ex. generic.http.device_names_csv=Encoder 1,Encoder 2) Start
> the OpenDCT service, let it run for a few seconds, then stop it. You
> should now see entries in opendct.properties that have the property
> sagetv.device.\<unique_id\>.device_name matching the name of the
> devices you requested. You will also see the following properties
> related to setting up this specific capture device (always use the
> full path to any executable):
>
> sagetv.device.\<unique_id\>.channel_padding=0
>
> sagetv.device.\<unique_id\>.custom_channels_csv=
>
> sagetv.device.\<unique_id\>.pretuning_executable=
>
> sagetv.device.\<unique_id\>.stopping_executable=
>
> sagetv.device.\<unique_id\>.streaming_url=
>
> sagetv.device.\<unique_id\>.streaming_url2=
>
> sagetv.device.\<unique_id\>.streaming_url2_channels=
>
> sagetv.device.\<unique_id\>.tuning_delay_ms=0
>
> sagetv.device.\<unique_id\>.tuning_executable=
>
> **channel_padding** is the minimum length to be passed for the %c%
> variable. Values shorter than this length will have zeros (0) appended
> to the left of the channel to make up the difference. (Ex. 8 becomes
> 008 if this is set to 3.)
>
> **custom_channels** is an optional **semicolon** delimited list of
> channels you want to appear in SageTV for this device. This is a
> shortcut around creating an actual OpenDCT lineup. If there are any
> values in the field, they will override the lineup assigned to this
> capture device on a channel scan. This provides an easy way to add
> channels if you are not actually going to use guide data.
>
> **pretuning_executable** is an optional executable that if defined,
> will always be run before actually tuning the channel. You can add the
> channel as an argument by using the variable %c%. Don't forget to
> escape backslashes (\ needs to be \\).
>
> **stopping_executable** is an optional executable that if defined,
> will always be run when the capture device is told to stop. You can
> add the last tuned channel as an argument by using the variable %c%.
> Don't forget to escape backslashes (\ needs to be \\).
>
> **streaming_url** is a URL that points directly to an audio/video
> stream. HLS and m3u8 playlists are not supported at this time.
>
> **streaming_url2** is an alternative URL that points directly to an
> audio/video stream. This stream will only be used if a channel matches
> one of the ranges in **streaming_url2_channels**. HLS and m3u8
> playlists are not supported at this time.
>
> **streaming_url2_channels** are the channel ranges that will use the
> alternative URL. This uses the same formatting as the dynamic consumer
> channel ranges.
>
> **tuning_delay_ms** is the amount of time in milliseconds to wait
> after the program associated with **tuning_executable** has returned.
>
> **tuning_executable** is an optional executable that if defined will
> be used to change the channel being streamed. Insert %c% where the
> channel needs to be provided to the executable. If %c% isn't provided,
> but this property is defined, the channel number will be appended as
> a final parameter. Don't forget to escape backslashes (\ needs to be
> \\).
> Ex. /full/path/tune 0 %c% or C:\\Full\\Path\\tune.exe 0 %c%

> *Added 59 second videos that will be streamed in place of an actual
> recording if the channel is detected to be Copy Once or Copy Never.

> *Added a check after tuning that will return to SageTV early if the
> copy protection status is Copy Once or Copy Never.

> *Added a property to enable the use of the timebase 1/0 instead of
> the standard stream timebase of 90khz. This can make recording more
> compatible with programs that do not work well with VBR MPEG-TS
> streams that FFmpeg generates. Set the property
> consumer.ffmpeg.use_compat_timebase to true if you want to enable
> this.

> *Added a property to enable the use of a constant bitrate for MPEG-TS.
> This can make recording more compatible with programs that do not work
> well with VBR MPEG-TS streams generated by FFmpeg. Set the property
> consumer.ffmpeg.use_mpegts_cbr to true if you want to enable this.

> *Added a few efficiency improvements to the tuning monitor.

> *Added more FFmpeg null condition handling.

> *Changed initial bytes streamed for FFmpeg to be closer to what's
> actually needed to start playback.

> *Changed FFmpeg offset calculations so that all new and switched
> recordings are offset as close to 0 as possible.

> *Changed FFmpeg behind tolerance to no more than four frames, since
> being behind is a much bigger issue than being too far ahead. In CBR,
> this could have the effect of dropping an entire commercial. That may
> not sound like a bad thing, but that ~30 seconds missing could be
> enough to make SageTV think there's a problem.  

> *Improved responsiveness of raw consumer.

> *Fixed FFmpeg audio channel selection so a greater weight is put on
> the bitrate over the number of frames that have arrived.
 
> *Fixed FFmpeg stream detection so that when a program is not provided,
> it will try to get all of the streams associated with the first usable
> video stream when one is found. 

> *Fixed 1/90000 ratio so it uses hex instead of an integer. This keeps
> Java from signing it.

> *Fixed a potential offset calculating error in FFmpeg related to the
> new aggregate write feature.

> *Removed PTS hack property and code. It's not as useful as expected. 

> *Removed file missing and file size checking while directly writing
> files from FFmpeg consumer.

> *Removed waiting for key audio packet(s). Now once the first video key
> frame is written, all audio packets from that point on are written. 

> *Removed ability to use the old UPnP detection method and device.

> *Removed the old FFmpeg implementation.