## OpenDCT Compiled Binary

### CCExtractor
*Files and directories unless otherwise stated are using the root
 directory ./compile/ccextractor which is relative to this document.*

Since CCExtractor for Windows cannot be compiled under Linux and setting
up a Linux build environment in Windows is more trouble than it's worth
the binary will be provided in a pre-compiled form. For now, the binary
will be stored with the project on GitHub since it is fairly small with
an eventual goal of hosting on Bintray.
 
##### Updating the Binary for Linux
*Git and make must be installed before proceeding.*

1. Run ./download-git
2. Run ./compile-linux

##### Updating the Binary for Windows
*Git and Visual Studio 2013 must be be installed before proceeding.*

1. Run download-git.cmd
2. Open the project ccextractor.sln under .\ccextractor\windows
3. Compile the project.
4. Copy resulting binary into the directories
   ..\windows-x86\ccextractor and ..\windows-x86_64\ccextractor.
   
### Video Messages
*Files and directories unless otherwise stated are using the root
 directory ./compile/video which is relative to this document.*
 
##### Updating the video messages under Linux
*A distribution of FFmpeg must be installed or a compiled version must
 be placed this the video folder before proceeding.*

1. Run ./generate
 
##### Updating the video messages under Windows

1. Download the latest ffmpeg.exe build for Windows and place it in the
   video folder.
2. Run Generate.cmd