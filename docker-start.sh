#!/bin/sh

docker-compose stop || true
docker-compose rm -f || true

docker build --no-cache docker/cf_base --tag corefabric:cf_base && \
docker build --no-cache docker/cf_nodejs --tag corefabric:cf_nodejs && \
docker build --no-cache docker/cf_postgres --tag corefabric:cf_postgres && \
docker-compose build && \
docker-compose up

