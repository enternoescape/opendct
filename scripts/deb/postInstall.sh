#!/bin/sh

chown opendct:opendct /opt/opendct
ln -fs /opt/opendct/service /etc/init.d/opendct
chmod 755 /etc/init.d/opendct

echo "To use the provided ufw rules type:"
echo "/opt/opendct/enable-ufw-ports"
echo ""
echo "To delete the provided ufw rules type:"
echo "/opt/opendct/disable-ufw-ports"
echo ""
echo "To start the OpenDCT service type:"
echo "service opendct start"

exit 0