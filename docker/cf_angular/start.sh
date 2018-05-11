#!/bin/sh

/root/prestart.sh

(
    [ -r /root/setup.finished ] || /root/setup.sh && touch /root/setup.finished
)

# does nothing; your services go here.

(cd /a2 && ng serve)