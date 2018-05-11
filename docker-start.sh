#!/bin/sh

docker-compose stop || true
docker-compose rm -f || true

docker build --no-cache docker/cf_base --tag corefabric:cf_base && \
docker build --no-cache docker/cf_angular --tag corefabric:cf_angular && \
docker build --no-cache docker/cf_postgres --tag corefabric:cf_postgres && \
docker-compose up

