#!/bin/sh
rm -rfv node_modules
rm -rfv typings
npm install
npm run typings install
