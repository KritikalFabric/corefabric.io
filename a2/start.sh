#!/bin/sh -v

lessc src/download/maverick-theme/app/assets/less/styles.less src/download/maverick-theme/app/assets/less/maverick-theme.css
exec ng serve --host 0.0.0.0 --port 4200 --disableHostCheck
