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