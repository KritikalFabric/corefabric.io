#!/bin/sh

service cron start
service procps start
service syslog-ng start

#(
#    [ -r /root/prestart.once.finished ] || (
#	# do nothing
#    ) && touch /root/prestart.once.finished
#)
