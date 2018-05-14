#!/bin/sh -v

exec ng build --prod --build-optimizer --vendor-chunk=true
