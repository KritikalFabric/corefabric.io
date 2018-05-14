#!/bin/sh -v

rsync -av --delete a2/dist ben@joyent-build.dc1.amb1ent.org:~/corefabric/a2/
