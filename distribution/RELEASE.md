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