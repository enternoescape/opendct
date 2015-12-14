#!/bin/sh

chown opendct:opendct /opt/opendct

echo ""
echo "To enable the OpenDCT service at startup type:"
echo "systemctl enable opendct.service"
echo ""
echo "To start the OpenDCT service type:"
echo "systemctl start opendct.service"

exit 0