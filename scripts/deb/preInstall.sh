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

exit 0