#!/bin/sh -v

npm install rxjs@5.0.0-beta.12 && \
npm install && \
npm run typings install && \
npm run tsc
# && npm run gulp
