#!/bin/sh

if service opendct status | grep -e " is running " >> /dev/null; then
    service opendct stop
fi

if test -e /etc/init.d/opendct; then
    rm /etc/init.d/opendct
fi

update-rc.d -f opendct remove

if test -e /var/run/opendct; then
    rm -rf /var/run/opendct
fi

exit 0