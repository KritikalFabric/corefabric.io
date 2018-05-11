#!/bin/sh

export DOCKER_REPO=registry.dc1.amb1ent.org:443

docker pull $DOCKER_REPO/cf_base
docker pull $DOCKER_REPO/cf_angular

docker tag $DOCKER_REPO/cf_base corefabric:cf_base
docker tag $DOCKER_REPO/cf_angular corefabric:cf_angular

docker run -v `pwd`/a2:/a2 corefabric:cf_angular /bin/bash -c 'cd /a2; ./clean.sh'
