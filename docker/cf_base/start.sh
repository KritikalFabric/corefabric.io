#!/bin/sh

/root/prestart.sh

(
    [ -r /root/setup.finished ] || /root/setup.sh && touch /root/setup.finished
)

# does nothing; your services go here.

/usr/bin/tail -f /var/log/syslog
