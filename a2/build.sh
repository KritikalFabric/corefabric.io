#!/bin/sh -v

lessc src/download/maverick-theme/app/assets/less/styles.less src/download/maverick-theme/app/assets/less/maverick-theme.css
exec ng build --prod --build-optimizer --common-chunk
