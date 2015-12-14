#!/bin/sh

if ! whereis -b java | grep -e "/java" >> /dev/null; then
    echo ""
    echo "ERROR: A version of Java >= 1.7 must be installed before installing this package."
    echo ""
	echo "One of the following commands should fix this error:"
	echo ""
	echo "Fedora 22:"
	echo "dnf install java-1.8.0-openjdk-headless"
	echo ""
	echo "CentOS 7:"
	echo "yum install java-1.8.0-openjdk-headless"
    exit 1
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