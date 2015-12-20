IF EXIST %1 (
	rem Re-create this rule every time in case the install directory changed.
	netsh advfirewall firewall delete rule name="OpenDCT (JSW)"
	netsh advfirewall firewall add rule name="OpenDCT (JSW)" dir=in action=allow program=%1
	netsh advfirewall firewall add rule name="OpenDCT (JSW)" dir=out action=allow program=%1
)

netsh advfirewall firewall show rule name="OpenDCT (SageTV Media Server)" >nul
IF ERRORLEVEL 1 netsh advfirewall firewall add rule name="OpenDCT (SageTV Media Server)" dir=out localport=7818 protocol=TCP action=allow

netsh advfirewall firewall show rule name="OpenDCT (SageTV Network Encoder)" >nul
IF ERRORLEVEL 1 netsh advfirewall firewall add rule name="OpenDCT (SageTV Network Encoder)" dir=in localport=9000-9100 protocol=TCP action=allow

netsh advfirewall firewall show rule name="OpenDCT (SageTV Discovery)" >nul
IF ERRORLEVEL 1 netsh advfirewall firewall add rule name="OpenDCT (SageTV Discovery)" dir=in localport=8271 protocol=UDP action=allow

netsh advfirewall firewall show rule name="OpenDCT (RTP)" >nul
IF ERRORLEVEL 1 netsh advfirewall firewall add rule name="OpenDCT (RTP)" dir=in localport=8300-8500 protocol=UDP action=allow

netsh advfirewall firewall show rule name="OpenDCT (Cling HTTP TCP)" >nul
IF ERRORLEVEL 1 netsh advfirewall firewall add rule name="OpenDCT (Cling HTTP TCP)" dir=in localport=8501 protocol=TCP action=allow