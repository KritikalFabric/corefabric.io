#!/bin/sh

export DOCKER_REPO=registry.dc1.amb1ent.org:443

docker build --no-cache docker/cf_base --tag corefabric:cf_base && \
docker tag corefabric:cf_base $DOCKER_REPO/cf_base && \
docker push $DOCKER_REPO/cf_base && \

docker build --no-cache docker/cf_postgres --tag corefabric:cf_postgres && \
docker tag corefabric:cf_postgres $DOCKER_REPO/cf_postgres && \
docker push $DOCKER_REPO/cf_postgres && \

echo "Done!"