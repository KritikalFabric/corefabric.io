#!/bin/sh

service cron restart
service dbus restart
service procps restart
service syslog-ng restart

#(
#    [ -r /root/prestart.once.finished ] || (
#	# do nothing
#    ) && touch /root/prestart.once.finished
#)
