#!/bin/sh

if test -e /var/run/opendct; then
    rm -rf /var/run/opendct
fi

if test -e /usr/lib/systemd/system/opendct.service; then
	systemctl disable opendct.service
	systemctl stop opendct.service
fi

exit 0