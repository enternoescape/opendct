#!/bin/sh

chown opendct:opendct /opt/opendct
ln -fs /opt/opendct/service /etc/init.d/opendct
chmod 755 /etc/init.d/opendct

echo ""
echo "To start the OpenDCT service type:"
echo "service opendct start"

exit 0