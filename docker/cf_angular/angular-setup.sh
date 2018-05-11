#!/bin/sh -v

umask 000
chmod 777 /root
cd /root
npm install -g npm@5.6.0
npm install -g --save-dev @angular/cli@latest
