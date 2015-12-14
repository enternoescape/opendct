#!/bin/sh

if whereis -b java | grep -e "java: /usr/share/java" >> /dev/null; then
    echo ""
    echo "ERROR: A version of Java >= 1.7 must be installed before installing this package."
    echo ""
	echo "The following command should fix this error:"
	echo "apt-get install default-jre-headless"
    exit 1
fi

if test -e /etc/init.d/opendct; then
    if service opendct status | grep -e " is running "; then
        service opendct stop
    fi
fi

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

exit 0