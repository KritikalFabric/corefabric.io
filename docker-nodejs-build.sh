#!/bin/sh

export DOCKER_REPO=registry.dc1.amb1ent.org:443

docker pull $DOCKER_REPO/cf_base
docker pull $DOCKER_REPO/cf_nodejs

docker tag $DOCKER_REPO/cf_base corefabric:cf_base
docker tag $DOCKER_REPO/cf_nodejs corefabric:cf_nodejs

docker run -v `pwd`/a2:/a2 corefabric:cf_nodejs /bin/bash -c 'cd /a2; ./clean.sh; ./build.sh'
