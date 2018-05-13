#!/bin/sh

export DOCKER_REPO=registry.dc1.amb1ent.org:443

docker-compose stop || true
docker-compose rm -f || true

docker pull $DOCKER_REPO/cf_base
docker pull $DOCKER_REPO/cf_angular
docker pull $DOCKER_REPO/cf_postgres

docker tag $DOCKER_REPO/cf_base corefabric:cf_base
docker tag $DOCKER_REPO/cf_angular corefabric:cf_angular
docker tag $DOCKER_REPO/cf_postgres corefabric:cf_postgres

docker-compose up

