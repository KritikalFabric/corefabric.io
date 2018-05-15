#!/bin/sh -v

lessc src/download/maverick-theme/app/assets/less/styles.less src/download/maverick-theme/app/assets/less/maverick-theme.css
ng build --prod --build-optimizer --common-chunk
find a2/dist/a2/ -type f -not -name '*.gz' -exec gzip --best --keep {} \;
echo "Build completed."
