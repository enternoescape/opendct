netsh advfirewall firewall add rule name="OpenDCT (SageTV Media Server)" dir=out localport=7818 protocol=TCP action=allow
netsh advfirewall firewall add rule name="OpenDCT (SageTV Network Encoder)" dir=in localport=9000-9100 protocol=TCP action=allow
netsh advfirewall firewall add rule name="OpenDCT (SageTV Discovery)" dir=in localport=8271 protocol=UDP action=allow
netsh advfirewall firewall add rule name="OpenDCT (RTP)" dir=in localport=8300-8500 protocol=UDP action=allow
netsh advfirewall firewall add rule name="OpenDCT (Cling HTTP TCP)" dir=in localport=8501 protocol=TCP action=allow