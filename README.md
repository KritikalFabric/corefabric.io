corefabric.io
=============

```
corefabric.io							0.2.0-angular6

Project for Raspberry Pi 3 model B and Mac OS X, SmartOS & Linux.

(c) 2014-2018   Ben Gould
(c) 2014-2018   Alex Kritikos

org.kritikal.fabric:
	Licensed under the Apache Public License version 2.0

Portions are in the public domain.
```

Quick-start!
------------

Build docker containers locally via ```./docker-start.sh``` 
or download pre-built docker containers ```./docker-quick.sh```
if you have access to our docker registry.

```
awfulhak:corefabric.io ben$ ./docker-quick.sh
```

& in another terminal:

```
awfulhak:corefabric.io ben$ ./gradlew clean runShadow
```

nb. https://stackoverflow.com/questions/32808215/where-to-set-the-insecure-registry-flag-on-mac-os

this goes in your daemon.json:

```
{
  "debug" : true,
  "experimental" : true,
  "insecure-registries": ["registry.dc1.amb1ent.org:443"]
}
```

and you probably want to make a configuration file:

```
awfulhak:corefabric.io ben$ ln -s config.json.example config.json
```

Easy Links!!!
-------------

1. <http://localhost:1080/>      - webserver embeddded in core fabric
2. <http://localhost:4200/>      - angular "ng start" dev environment

Features
--------

```
wierd and wonderful message queues

$foo/bar/baz -- is a "system" message queue and as such reserved.  we use this.
|/# & |foo/# -- is a "cluster" message queue and as such shared across the cluster.  love it.
#/| & #/|foo -- is a "cluster" message queue just the same as the previous.
nb. system and cluster can be combined using this 2nd technique.
```

Build information
-----------------

Before trying the build we need some tooling.  You will need to set-up
some environment and also install the correct version of protoc to match
our protocol buffers libraries:

1.  ```$ git submodule init```

2.  ```$ git submodule update```

3.  ```$ ./tools/dependencies/install.sh```

To complete this task you need the UNIX admin badge.

Building the software is a two step process.

1.  first, fetch docker containers [or modify your script to build them] and
    build the "a2" sub-project; uses nodejs 10; modified local files by loopback.
    run ```$ ./docker-nodejs-build.sh```.
    
2.  secondly, build the jar file via ```$ ./gradlew build``` (or embed the a2 content and 
    run via ```$ ./gradlew runShadow```).
