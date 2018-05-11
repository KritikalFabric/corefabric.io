#!/bin/sh -v

umask 000
chmod 777 /root
cd /root
npm install -g npm@latest
npm install -g concurrently
npm install -g typings
npm install -g lite-server@2.2.2
npm install -g typescript
