#!/bin/sh -v

umask 000
chmod 777 /root
cd /root
npm install -g less
npm install -g --save-dev @angular/cli
