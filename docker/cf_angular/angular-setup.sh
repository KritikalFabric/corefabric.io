#!/bin/sh -v

umask 000
chmod 777 /root
cd /root
npm install -g npm@latest
npm install -g @angular/cli
