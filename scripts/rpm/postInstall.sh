#!/bin/sh

chown opendct:opendct /opt/opendct

# This should fix a potential SELinux issue.
restorecon /etc/firewalld/services/webmin.xml

echo "To use the provided firewalld rules type:"
echo "firewall-cmd --get-default-zone"
echo "firewall-cmd --permanent --zone=<default_zone> --add-service=opendct"
echo "firewall-cmd --reload"
echo ""
echo "To enable the OpenDCT service at startup type:"
echo "systemctl enable opendct.service"
echo ""
echo "To start the OpenDCT service type:"
echo "systemctl start opendct.service"

exit 0