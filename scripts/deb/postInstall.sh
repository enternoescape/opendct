#!/bin/sh

getent group opendct >/dev/null || groupadd -r opendct
getent passwd opendct >/dev/null || useradd -r -g opendct -d /opt/opendct -s /bin/bash -c "OpenDCT Service Account" opendct

if test ! -e /var/log/opendct; then
    mkdir -p /var/log/opendct
fi

if test ! -e /var/run/opendct; then
    mkdir -p /var/run/opendct
fi

chown opendct:opendct /var/log/opendct
chown opendct:opendct /var/run/opendct
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