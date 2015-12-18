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

exit 0