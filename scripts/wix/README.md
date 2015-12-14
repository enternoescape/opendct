The Wix binary is not being hosted with this project since it would add 25MB of data for a feature that not all people are interested in having.

Some source files are based on work done by Helge Klein. https://helgeklein.com/blog/2014/09/real-world-example-wix-msi-application-installer/

1) Download and install WiX 3.x toolset from https://wix.codeplex.com/.

2) If the script cannot find it automatically, update the environment variable WixPath in WixConfig.cmd to the binary path of Wix. (Ex. C:\Program Files (x86)\WiX Toolset v3.10\bin)