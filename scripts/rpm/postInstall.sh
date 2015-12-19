#!/bin/sh

getent group opendct >/dev/null || groupadd -r opendct
getent passwd opendct >/dev/null || useradd -r -g opendct -d /opt/opendct -s /bin/bash -c "OpenDCT Service Account" opendct

if test -e /usr/lib/systemd/system/opendct.service; then
	systemctl disable opendct.service
	systemctl stop opendct.service
fi

if test ! -e /var/log/opendct; then
    mkdir -p /var/log/opendct
fi

if test ! -e /var/run/opendct; then
    mkdir -p /var/run/opendct
fi

chown opendct:opendct /var/log/opendct
chown opendct:opendct /var/run/opendct
chown opendct:opendct /opt/opendct

# This should fix a potential SELinux issue.
restorecon /etc/firewalld/services/opendct.xml

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