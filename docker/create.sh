#!/bin/sh

docker ps | awk '{ print $1; }'  | grep -v CONTAINER | xargs docker kill

docker build -t corefabric-database corefabric-database/

docker run -d -p 54321:5432 corefabric-database

docker ps

docker ps | grep corefabric-database | awk '{ print $1; }' | xargs docker port
