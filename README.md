```
corefabric.io							0.1.0-preview

Project for Raspberry Pi 3 model B and Mac OS X, SmartOS & Linux.

(c) 2014-2016	Ben Gould
(c) 2014-2016   Xela Sokitirk

org.kritikal.fabric:
	Licensed under the Apache Public License version 2.0

Portions are in the public domain.
```
Quick-start!

```
awfulhak:corefabric.io ben$ (cd a2/ && ./clean.sh && ./build.sh) &&\
(cd docker/ && ./create.sh) &&\
./gradlew clean runShadow
```
Features
```
wierd and wonderful message queues

$foo/bar/baz -- is a "system" message queue and as such reserved.  we use this.
|/# & |foo/# -- is a "cluster" message queue and as such shared across the cluster.  love it.
#/| & #/|foo -- is a "cluster" message queue just the same as the previous.
nb. system and cluster can be combined using this 2nd technique.
```
Development environment 
```
node-js is needed for our angular2 web-development environment

awfulhak:corefabric.io ben$ git submodule init
awfulhak:corefabric.io ben$ git submodule update
awfulhak:corefabric.io ben$ ./tools/dependencies/clean.sh
awfulhak:corefabric.io ben$ ./tools/dependencies/install.sh
awfulhak:corefabric.io ben$ (cd a2/ && ./clean.sh && ./run.sh)

& in a second terminal start-up through org.kritikal.fabric.CoreFabric.main():

awfulhak:corefabric.io ben$ ./gradlew runShadow
```
```
[tl;dr]

HOW-TO get it all up and running on a Mac!

Pre-requisites:

awfulhak:corefabric.io ben$ touch jDtnConfig.xml
awfulhak:corefabric.io ben$ ln -s `pwd`/jDtnConfig.xml ~/jDtnConfig.xml

nodejs (angular2 build toolchain)

https://nodejs.org/en/ choose v6.x.y LTS please :)


Google protobuf compiler, correct version 3.0.2 release September 7 2016.
Get from : https://github.com/google/protobuf/archive/v3.0.2.tar.gz
Open a shell
Run ./autogen.sh to generate configure script
Run ./configure to generate make file
Run ./make to compile
Run sudo ./make install to install

TODO: Track / automate installation of this via gradle?
awfulhak:corefabric.io ben$ which protoc
/usr/local/bin/protoc
awfulhak:corefabric.io ben$ protoc --version
libprotoc 3.0.0
awfulhak:corefabric.io ben$ ls -al tools/macosx/bin/protoc
-rwxr-xr-x  1 ben  staff  17392 24 Oct 08:45 tools/macosx/bin/protoc

---8<------8<------8<------8<------8<------8<------8<---

install postgresql 9.5 ... us the provided binaries on mac os x

with defaults the configuration looks like:

awfulhak:corefabric.io ben$ psql postgres
psql (9.5.1)
Type "help" for help.

postgres=# alter role postgres with superuser login password 'password';
ALTER ROLE

NOTE: If this is your first time doing this, the role might not exist and you might get:
ERROR:  role "postgres" does not exist
In that case change the alter to a create as follows:

postgres=# create role postgres with superuser login password 'password';

postgres=# create database corefabric__node_db;
CREATE DATABASE
postgres=# create database corefabric__config_db;
CREATE DATABASE
postgres=# \q

on smartos the configuration looks like:

root ~ (db-amb1ent-unix-0001.): pkg update
root ~ (db-amb1ent-unix-0001.): pkg install postgresql-95/server
root ~ (db-amb1ent-unix-0001.): svcadm enable svc:/application/database/postgresql95
root ~ (db-amb1ent-unix-0001.): su - postgres
postgres ~ (db-amb1ent-unix-0001.): psql postgres
psql (9.5.4)
Type "help" for help.

postgres=# alter role postgres with superuser login password 'password';
ALTER ROLE
postgres=# \q

*now* append this line to /ec/var/postgres/9.5/main/pg_hba.conf and restart the server

host all all 0.0.0.0/0 md5

*and* append this line to /ec/var/postgres/9.5/main/postgresql.conf and restart the server

listen_addresses = '*'

root ~ (db-amb1ent-unix-0001.): svcadm restart svc:/application/database/postgresql95


---8<------8<------8<------8<------8<------8<------8<---

Start the app server (vertx based software stack)

Terminal window #1:

awfulhak:corefabric.io ben$ ./gradlew runShadow
:generateBuildConfig
:compileBuildConfig
:extractContribProto
:extractIncludeContribProto
:generateContribProto UP-TO-DATE
:compileContribJava
/Users/ben/work/corefabric.io/src/contrib/java/org/ibrdtnapi/entities/PayloadBlock.java:6: warning: BASE64Decoder is internal proprietary API and may be removed in a future release
import sun.misc.BASE64Decoder;
               ^
/Users/ben/work/corefabric.io/src/contrib/java/org/ibrdtnapi/entities/PayloadBlock.java:7: warning: BASE64Encoder is internal proprietary API and may be removed in a future release
import sun.misc.BASE64Encoder;
               ^
/Users/ben/work/corefabric.io/src/contrib/java/org/ibrdtnapi/entities/PayloadBlock.java:41: warning: BASE64Decoder is internal proprietary API and may be removed in a future release
			this.decoded = new BASE64Decoder().decodeBuffer(this.encoded);
			                   ^
/Users/ben/work/corefabric.io/src/contrib/java/org/ibrdtnapi/entities/PayloadBlock.java:50: warning: BASE64Encoder is internal proprietary API and may be removed in a future release
		this.encoded = new String(new BASE64Encoder().encodeBuffer(this.decoded)).trim();
		                              ^
4 warnings
:processContribResources UP-TO-DATE
:contribClasses
:extractIncludeProto
:extractProto UP-TO-DATE
:generateProto
:compileJava
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/VertxOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject': class file for io.vertx.codegen.annotations.DataObject not found
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-shell/3.3.2/abdd2dee80b27e4b8499fb7015ea0a0bf4d9e41a/vertx-shell-3.3.2.jar(io/vertx/ext/shell/ShellServiceOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-shell/3.3.2/abdd2dee80b27e4b8499fb7015ea0a0bf4d9e41a/vertx-shell-3.3.2.jar(io/vertx/ext/shell/term/HttpTermOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/http/HttpServerOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/net/NetServerOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/datagram/DatagramSocketOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/DeploymentOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/metrics/Measured.class): warning: Cannot find annotation method 'concrete()' in type 'VertxGen': class file for io.vertx.codegen.annotations.VertxGen not found
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/metrics/MetricsOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/streams/ReadStream.class): warning: Cannot find annotation method 'concrete()' in type 'VertxGen'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/streams/StreamBase.class): warning: Cannot find annotation method 'concrete()' in type 'VertxGen'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/streams/WriteStream.class): warning: Cannot find annotation method 'concrete()' in type 'VertxGen'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/net/TCPSSLOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/net/NetworkOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-shell/3.3.2/abdd2dee80b27e4b8499fb7015ea0a0bf4d9e41a/vertx-shell-3.3.2.jar(io/vertx/ext/shell/ShellServerOptions.class): warning: Cannot find annotation method 'generateConverter()' in type 'DataObject'
/Users/ben/.gradle/caches/modules-2/files-2.1/io.vertx/vertx-core/3.3.2/361c2931be45762af07fd695bfd38d7b58f2d068/vertx-core-3.3.2.jar(io/vertx/core/http/WebSocketBase.class): warning: Cannot find annotation method 'concrete()' in type 'VertxGen'
Note: Some input files use unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
16 warnings
:processResources
:classes
:shadowJar
:startShadowScripts
:installShadowApp
:runShadow
Oct 24, 2016 8:58:38 AM org.kritikal.fabric.CoreFabric
INFO: corefabric.io - 0.0.1-alpha
Oct 24, 2016 8:58:39 AM org.kritikal.fabric.CoreFabric
INFO: ONLINE, press ^C to exit.
> Building 94% > :runShadow

---8<------8<------8<------8<------8<------8<------8<---

Terminal window #2:

Start the development web server (nodejs dev environment)

STEP 1 of 2: scroll through the nodejs garbage jungle
needed to make an angular2 web app!!!

Last login: Mon Oct 24 08:59:52 on ttys000
cd /Users/ben/work/corefabric\.io
awfulhak:~ ben$ cd /Users/ben/work/corefabric\.io
awfulhak:corefabric.io ben$ cd a2
awfulhak:a2 ben$ ./clean.sh
npm WARN deprecated tough-cookie@2.2.2: ReDoS vulnerability parsing Set-Cookie https://nodesecurity.io/advisories/130

> fsevents@1.0.14 install /Users/ben/work/corefabric.io/a2/node_modules/fsevents
> node-pre-gyp install --fallback-to-build

[fsevents] Success: "/Users/ben/work/corefabric.io/a2/node_modules/fsevents/lib/binding/Release/node-v48-darwin-x64/fse.node" is installed via remote

> corefabric.io@1.0.0 postinstall /Users/ben/work/corefabric.io/a2
> typings install

typings WARN deprecated 10/19/2016: "registry:dt/node#6.0.0+20160909174046" is deprecated (updated, replaced or removed)
typings WARN deprecated 10/3/2016: "registry:dt/jasmine#2.2.0+20160621224255" is deprecated (updated, replaced or removed)
typings WARN deprecated 9/14/2016: "registry:dt/core-js#0.0.0+20160725163759" is deprecated (updated, replaced or removed)

├── core-js (global)
├── jasmine (global)
└── node (global)

corefabric.io@1.0.0 /Users/ben/work/corefabric.io/a2
├── @angular/common@2.1.1
├── @angular/compiler@2.1.1
├── @angular/core@2.1.1
├── @angular/forms@2.1.1
├── @angular/http@2.1.1
├── @angular/platform-browser@2.1.1
├── @angular/platform-browser-dynamic@2.1.1
├── @angular/router@3.1.1
├── @angular/upgrade@2.1.1
├── angular-in-memory-web-api@0.1.13
├── bootstrap@3.3.7
├─┬ concurrently@3.1.0
│ ├── bluebird@2.9.6
│ ├─┬ chalk@0.5.1
│ │ ├── ansi-styles@1.1.0
│ │ ├── escape-string-regexp@1.0.5
│ │ ├─┬ has-ansi@0.1.0
│ │ │ └── ansi-regex@0.2.1
│ │ ├── strip-ansi@0.3.0
│ │ └── supports-color@0.2.0
│ ├── commander@2.6.0
│ ├── lodash@4.16.4
│ ├── moment@2.15.2
│ ├── rx@2.3.24
│ ├── spawn-default-shell@1.1.0
│ └── tree-kill@1.1.0
├── core-js@2.4.1
├─┬ lite-server@2.2.2
│ ├─┬ browser-sync@2.17.5
│ │ ├─┬ browser-sync-client@2.4.3
│ │ │ ├── etag@1.7.0
│ │ │ └── fresh@0.3.0
│ │ ├─┬ browser-sync-ui@0.6.1
│ │ │ ├── async-each-series@0.1.1
│ │ │ ├─┬ stream-throttle@0.1.3
│ │ │ │ └── limiter@1.1.0
│ │ │ └─┬ weinre@2.0.0-pre-I0Z7U9OV
│ │ │   ├─┬ express@2.5.11
│ │ │   │ ├─┬ connect@1.9.2
│ │ │   │ │ └── formidable@1.0.17
│ │ │   │ ├── mime@1.2.4
│ │ │   │ ├── mkdirp@0.3.0
│ │ │   │ └── qs@0.4.2
│ │ │   ├── nopt@3.0.6
│ │ │   └── underscore@1.7.0
│ │ ├── bs-recipes@1.2.3
│ │ ├─┬ chokidar@1.6.0
│ │ │ ├── anymatch@1.3.0
│ │ │ ├── async-each@1.0.1
│ │ │ ├─┬ fsevents@1.0.14
│ │ │ │ ├── nan@2.4.0
│ │ │ │ └─┬ node-pre-gyp@0.6.29
│ │ │ │   ├─┬ mkdirp@0.5.1
│ │ │ │   │ └── minimist@0.0.8
│ │ │ │   ├─┬ nopt@3.0.6
│ │ │ │   │ └── abbrev@1.0.9
│ │ │ │   ├─┬ npmlog@3.1.2
│ │ │ │   │ ├─┬ are-we-there-yet@1.1.2
│ │ │ │   │ │ └── delegates@1.0.0
│ │ │ │   │ ├── console-control-strings@1.1.0
│ │ │ │   │ ├─┬ gauge@2.6.0
│ │ │ │   │ │ ├── aproba@1.0.4
│ │ │ │   │ │ ├── has-color@0.1.7
│ │ │ │   │ │ ├── has-unicode@2.0.1
│ │ │ │   │ │ ├── object-assign@4.1.0
│ │ │ │   │ │ ├── signal-exit@3.0.0
│ │ │ │   │ │ ├─┬ string-width@1.0.1
│ │ │ │   │ │ │ ├─┬ code-point-at@1.0.0
│ │ │ │   │ │ │ │ └── number-is-nan@1.0.0
│ │ │ │   │ │ │ └── is-fullwidth-code-point@1.0.0
│ │ │ │   │ │ ├─┬ strip-ansi@3.0.1
│ │ │ │   │ │ │ └── ansi-regex@2.0.0
│ │ │ │   │ │ └── wide-align@1.1.0
│ │ │ │   │ └── set-blocking@2.0.0
│ │ │ │   ├─┬ rc@1.1.6
│ │ │ │   │ ├── deep-extend@0.4.1
│ │ │ │   │ ├── ini@1.3.4
│ │ │ │   │ ├── minimist@1.2.0
│ │ │ │   │ └── strip-json-comments@1.0.4
│ │ │ │   ├─┬ request@2.73.0
│ │ │ │   │ ├── aws-sign2@0.6.0
│ │ │ │   │ ├── aws4@1.4.1
│ │ │ │   │ ├─┬ bl@1.1.2
│ │ │ │   │ │ └── readable-stream@2.0.6
│ │ │ │   │ ├── caseless@0.11.0
│ │ │ │   │ ├─┬ combined-stream@1.0.5
│ │ │ │   │ │ └── delayed-stream@1.0.0
│ │ │ │   │ ├── extend@3.0.0
│ │ │ │   │ ├── forever-agent@0.6.1
│ │ │ │   │ ├─┬ form-data@1.0.0-rc4
│ │ │ │   │ │ └── async@1.5.2
│ │ │ │   │ ├─┬ har-validator@2.0.6
│ │ │ │   │ │ ├─┬ chalk@1.1.3
│ │ │ │   │ │ │ ├── ansi-styles@2.2.1
│ │ │ │   │ │ │ ├── escape-string-regexp@1.0.5
│ │ │ │   │ │ │ ├── has-ansi@2.0.0
│ │ │ │   │ │ │ └── supports-color@2.0.0
│ │ │ │   │ │ ├─┬ commander@2.9.0
│ │ │ │   │ │ │ └── graceful-readlink@1.0.1
│ │ │ │   │ │ ├─┬ is-my-json-valid@2.13.1
│ │ │ │   │ │ │ ├── generate-function@2.0.0
│ │ │ │   │ │ │ ├─┬ generate-object-property@1.2.0
│ │ │ │   │ │ │ │ └── is-property@1.0.2
│ │ │ │   │ │ │ ├── jsonpointer@2.0.0
│ │ │ │   │ │ │ └── xtend@4.0.1
│ │ │ │   │ │ └─┬ pinkie-promise@2.0.1
│ │ │ │   │ │   └── pinkie@2.0.4
│ │ │ │   │ ├─┬ hawk@3.1.3
│ │ │ │   │ │ ├── boom@2.10.1
│ │ │ │   │ │ ├── cryptiles@2.0.5
│ │ │ │   │ │ ├── hoek@2.16.3
│ │ │ │   │ │ └── sntp@1.0.9
│ │ │ │   │ ├─┬ http-signature@1.1.1
│ │ │ │   │ │ ├── assert-plus@0.2.0
│ │ │ │   │ │ ├─┬ jsprim@1.3.0
│ │ │ │   │ │ │ ├── extsprintf@1.0.2
│ │ │ │   │ │ │ ├── json-schema@0.2.2
│ │ │ │   │ │ │ └── verror@1.3.6
│ │ │ │   │ │ └─┬ sshpk@1.8.3
│ │ │ │   │ │   ├── asn1@0.2.3
│ │ │ │   │ │   ├── assert-plus@1.0.0
│ │ │ │   │ │   ├─┬ dashdash@1.14.0
│ │ │ │   │ │   │ └── assert-plus@1.0.0
│ │ │ │   │ │   ├── ecc-jsbn@0.1.1
│ │ │ │   │ │   ├─┬ getpass@0.1.6
│ │ │ │   │ │   │ └── assert-plus@1.0.0
│ │ │ │   │ │   ├── jodid25519@1.0.2
│ │ │ │   │ │   ├── jsbn@0.1.0
│ │ │ │   │ │   └── tweetnacl@0.13.3
│ │ │ │   │ ├── is-typedarray@1.0.0
│ │ │ │   │ ├── isstream@0.1.2
│ │ │ │   │ ├── json-stringify-safe@5.0.1
│ │ │ │   │ ├─┬ mime-types@2.1.11
│ │ │ │   │ │ └── mime-db@1.23.0
│ │ │ │   │ ├── node-uuid@1.4.7
│ │ │ │   │ ├── oauth-sign@0.8.2
│ │ │ │   │ ├── qs@6.2.0
│ │ │ │   │ ├── stringstream@0.0.5
│ │ │ │   │ ├── tough-cookie@2.2.2
│ │ │ │   │ └── tunnel-agent@0.4.3
│ │ │ │   ├─┬ rimraf@2.5.3
│ │ │ │   │ └─┬ glob@7.0.5
│ │ │ │   │   ├── fs.realpath@1.0.0
│ │ │ │   │   ├── inflight@1.0.5
│ │ │ │   │   ├─┬ minimatch@3.0.2
│ │ │ │   │   │ └─┬ brace-expansion@1.1.5
│ │ │ │   │   │   ├── balanced-match@0.4.2
│ │ │ │   │   │   └── concat-map@0.0.1
│ │ │ │   │   └── path-is-absolute@1.0.0
│ │ │ │   ├── semver@5.2.0
│ │ │ │   ├─┬ tar@2.2.1
│ │ │ │   │ ├── block-stream@0.0.9
│ │ │ │   │ ├─┬ fstream@1.0.10
│ │ │ │   │ │ └── graceful-fs@4.1.4
│ │ │ │   │ └── inherits@2.0.1
│ │ │ │   └─┬ tar-pack@3.1.4
│ │ │ │     ├─┬ debug@2.2.0
│ │ │ │     │ └── ms@0.7.1
│ │ │ │     ├── fstream-ignore@1.0.5
│ │ │ │     ├─┬ once@1.3.3
│ │ │ │     │ └── wrappy@1.0.2
│ │ │ │     ├─┬ readable-stream@2.1.4
│ │ │ │     │ ├── buffer-shims@1.0.0
│ │ │ │     │ ├── core-util-is@1.0.2
│ │ │ │     │ ├── isarray@1.0.0
│ │ │ │     │ ├── process-nextick-args@1.0.7
│ │ │ │     │ ├── string_decoder@0.10.31
│ │ │ │     │ └── util-deprecate@1.0.2
│ │ │ │     └── uid-number@0.0.6
│ │ │ ├── glob-parent@2.0.0
│ │ │ ├── inherits@2.0.3
│ │ │ ├─┬ is-binary-path@1.0.1
│ │ │ │ └── binary-extensions@1.7.0
│ │ │ ├── is-glob@2.0.1
│ │ │ ├── path-is-absolute@1.0.1
│ │ │ └─┬ readdirp@2.1.0
│ │ │   ├─┬ readable-stream@2.1.5
│ │ │   │ ├── buffer-shims@1.0.0
│ │ │   │ ├── core-util-is@1.0.2
│ │ │   │ ├── process-nextick-args@1.0.7
│ │ │   │ ├── string_decoder@0.10.31
│ │ │   │ └── util-deprecate@1.0.2
│ │ │   └── set-immediate-shim@1.0.1
│ │ ├─┬ connect@3.5.0
│ │ │ ├─┬ finalhandler@0.5.0
│ │ │ │ ├─┬ on-finished@2.3.0
│ │ │ │ │ └── ee-first@1.1.1
│ │ │ │ ├── statuses@1.3.0
│ │ │ │ └── unpipe@1.0.0
│ │ │ ├── parseurl@1.3.1
│ │ │ └── utils-merge@1.0.0
│ │ ├── dev-ip@1.0.1
│ │ ├─┬ easy-extender@2.3.2
│ │ │ └── lodash@3.10.1
│ │ ├─┬ eazy-logger@3.0.2
│ │ │ └─┬ tfunk@3.0.2
│ │ │   ├─┬ chalk@1.1.3
│ │ │   │ ├── ansi-styles@2.2.1
│ │ │   │ ├─┬ has-ansi@2.0.0
│ │ │   │ │ └── ansi-regex@2.0.0
│ │ │   │ ├── strip-ansi@3.0.1
│ │ │   │ └── supports-color@2.0.0
│ │ │   └── object-path@0.9.2
│ │ ├── emitter-steward@1.0.0
│ │ ├─┬ fs-extra@0.30.0
│ │ │ ├── jsonfile@2.4.0
│ │ │ └── klaw@1.3.0
│ │ ├─┬ http-proxy@1.15.1
│ │ │ ├── eventemitter3@1.2.0
│ │ │ └── requires-port@1.0.0
│ │ ├── immutable@3.8.1
│ │ ├─┬ localtunnel@1.8.1
│ │ │ ├── openurl@1.1.0
│ │ │ ├─┬ request@2.65.0
│ │ │ │ ├── aws-sign2@0.6.0
│ │ │ │ ├─┬ bl@1.0.3
│ │ │ │ │ └── readable-stream@2.0.6
│ │ │ │ ├── caseless@0.11.0
│ │ │ │ ├─┬ combined-stream@1.0.5
│ │ │ │ │ └── delayed-stream@1.0.0
│ │ │ │ ├── extend@3.0.0
│ │ │ │ ├── forever-agent@0.6.1
│ │ │ │ ├─┬ form-data@1.0.1
│ │ │ │ │ └── async@2.1.2
│ │ │ │ ├─┬ har-validator@2.0.6
│ │ │ │ │ ├─┬ chalk@1.1.3
│ │ │ │ │ │ ├── ansi-styles@2.2.1
│ │ │ │ │ │ ├─┬ has-ansi@2.0.0
│ │ │ │ │ │ │ └── ansi-regex@2.0.0
│ │ │ │ │ │ ├── strip-ansi@3.0.1
│ │ │ │ │ │ └── supports-color@2.0.0
│ │ │ │ │ ├─┬ commander@2.9.0
│ │ │ │ │ │ └── graceful-readlink@1.0.1
│ │ │ │ │ └─┬ is-my-json-valid@2.15.0
│ │ │ │ │   ├── generate-function@2.0.0
│ │ │ │ │   ├─┬ generate-object-property@1.2.0
│ │ │ │ │   │ └── is-property@1.0.2
│ │ │ │ │   └── jsonpointer@4.0.0
│ │ │ │ ├─┬ hawk@3.1.3
│ │ │ │ │ ├── boom@2.10.1
│ │ │ │ │ ├── cryptiles@2.0.5
│ │ │ │ │ ├── hoek@2.16.3
│ │ │ │ │ └── sntp@1.0.9
│ │ │ │ ├─┬ http-signature@0.11.0
│ │ │ │ │ ├── asn1@0.1.11
│ │ │ │ │ ├── assert-plus@0.1.5
│ │ │ │ │ └── ctype@0.5.3
│ │ │ │ ├── isstream@0.1.2
│ │ │ │ ├── json-stringify-safe@5.0.1
│ │ │ │ ├── node-uuid@1.4.7
│ │ │ │ ├── oauth-sign@0.8.2
│ │ │ │ ├── qs@5.2.1
│ │ │ │ ├── stringstream@0.0.5
│ │ │ │ └── tunnel-agent@0.4.3
│ │ │ └─┬ yargs@3.29.0
│ │ │   ├── camelcase@1.2.1
│ │ │   └── window-size@0.1.4
│ │ ├─┬ micromatch@2.3.11
│ │ │ ├─┬ arr-diff@2.0.0
│ │ │ │ └── arr-flatten@1.0.1
│ │ │ ├── array-unique@0.2.1
│ │ │ ├─┬ braces@1.8.5
│ │ │ │ ├─┬ expand-range@1.8.2
│ │ │ │ │ └─┬ fill-range@2.2.3
│ │ │ │ │   ├── is-number@2.1.0
│ │ │ │ │   ├── randomatic@1.1.5
│ │ │ │ │   └── repeat-string@1.6.1
│ │ │ │ ├── preserve@0.2.0
│ │ │ │ └── repeat-element@1.1.2
│ │ │ ├─┬ expand-brackets@0.1.5
│ │ │ │ └── is-posix-bracket@0.1.1
│ │ │ ├── extglob@0.3.2
│ │ │ ├── filename-regex@2.0.0
│ │ │ ├── is-extglob@1.0.0
│ │ │ ├─┬ kind-of@3.0.4
│ │ │ │ └── is-buffer@1.1.4
│ │ │ ├── normalize-path@2.0.1
│ │ │ ├─┬ object.omit@2.0.0
│ │ │ │ ├─┬ for-own@0.1.4
│ │ │ │ │ └── for-in@0.1.6
│ │ │ │ └── is-extendable@0.1.1
│ │ │ ├─┬ parse-glob@3.0.4
│ │ │ │ ├── glob-base@0.3.0
│ │ │ │ └── is-dotfile@1.0.2
│ │ │ └─┬ regex-cache@0.4.3
│ │ │   ├── is-equal-shallow@0.1.3
│ │ │   └── is-primitive@2.0.0
│ │ ├─┬ opn@4.0.2
│ │ │ ├── object-assign@4.1.0
│ │ │ └─┬ pinkie-promise@2.0.1
│ │ │   └── pinkie@2.0.4
│ │ ├─┬ portscanner@1.0.0
│ │ │ └── async@0.1.15
│ │ ├── qs@6.2.1
│ │ ├─┬ resp-modifier@6.0.2
│ │ │ └─┬ minimatch@3.0.3
│ │ │   └─┬ brace-expansion@1.1.6
│ │ │     ├── balanced-match@0.4.2
│ │ │     └── concat-map@0.0.1
│ │ ├── rx@4.1.0
│ │ ├─┬ serve-index@1.8.0
│ │ │ ├─┬ accepts@1.3.3
│ │ │ │ └── negotiator@0.6.1
│ │ │ ├── batch@0.5.3
│ │ │ ├── escape-html@1.0.3
│ │ │ ├─┬ http-errors@1.5.0
│ │ │ │ ├── inherits@2.0.1
│ │ │ │ └── setprototypeof@1.0.1
│ │ │ └─┬ mime-types@2.1.12
│ │ │   └── mime-db@1.24.0
│ │ ├─┬ serve-static@1.11.1
│ │ │ ├── encodeurl@1.0.1
│ │ │ └─┬ send@0.14.1
│ │ │   ├── depd@1.1.0
│ │ │   ├── destroy@1.0.4
│ │ │   ├── mime@1.3.4
│ │ │   └── range-parser@1.2.0
│ │ ├── server-destroy@1.0.1
│ │ ├─┬ socket.io@1.5.0
│ │ │ ├─┬ engine.io@1.7.0
│ │ │ │ ├── base64id@0.1.0
│ │ │ │ ├─┬ engine.io-parser@1.3.0
│ │ │ │ │ ├── after@0.8.1
│ │ │ │ │ ├── arraybuffer.slice@0.0.6
│ │ │ │ │ ├── base64-arraybuffer@0.1.5
│ │ │ │ │ ├── blob@0.0.4
│ │ │ │ │ ├─┬ has-binary@0.1.6
│ │ │ │ │ │ └── isarray@0.0.1
│ │ │ │ │ └── wtf-8@1.0.0
│ │ │ │ └─┬ ws@1.1.1
│ │ │ │   ├── options@0.0.6
│ │ │ │   └── ultron@1.0.2
│ │ │ ├─┬ has-binary@0.1.7
│ │ │ │ └── isarray@0.0.1
│ │ │ ├─┬ socket.io-adapter@0.4.0
│ │ │ │ └─┬ socket.io-parser@2.2.2
│ │ │ │   ├── debug@0.7.4
│ │ │ │   ├── isarray@0.0.1
│ │ │ │   └── json3@3.2.6
│ │ │ ├─┬ socket.io-client@1.5.0
│ │ │ │ ├── backo2@1.0.2
│ │ │ │ ├── component-bind@1.0.0
│ │ │ │ ├── component-emitter@1.2.0
│ │ │ │ ├─┬ engine.io-client@1.7.0
│ │ │ │ │ ├── component-inherit@0.0.3
│ │ │ │ │ ├── has-cors@1.1.0
│ │ │ │ │ ├── parsejson@0.0.1
│ │ │ │ │ ├── parseqs@0.0.2
│ │ │ │ │ ├── xmlhttprequest-ssl@1.5.1
│ │ │ │ │ └── yeast@0.1.2
│ │ │ │ ├── indexof@0.0.1
│ │ │ │ ├── object-component@0.0.3
│ │ │ │ ├─┬ parseuri@0.0.4
│ │ │ │ │ └─┬ better-assert@1.0.2
│ │ │ │ │   └── callsite@1.0.0
│ │ │ │ └── to-array@0.1.4
│ │ │ └─┬ socket.io-parser@2.2.6
│ │ │   ├── benchmark@1.0.0
│ │ │   ├── component-emitter@1.1.2
│ │ │   ├── isarray@0.0.1
│ │ │   └── json3@3.3.2
│ │ ├── ua-parser-js@0.7.10
│ │ └─┬ yargs@6.0.0
│ │   ├─┬ cliui@3.2.0
│ │   │ ├─┬ strip-ansi@3.0.1
│ │   │ │ └── ansi-regex@2.0.0
│ │   │ └── wrap-ansi@2.0.0
│ │   ├── decamelize@1.2.0
│ │   ├── get-caller-file@1.0.2
│ │   ├─┬ os-locale@1.4.0
│ │   │ └─┬ lcid@1.0.0
│ │   │   └── invert-kv@1.0.0
│ │   ├─┬ read-pkg-up@1.0.1
│ │   │ ├─┬ find-up@1.1.2
│ │   │ │ └── path-exists@2.1.0
│ │   │ └─┬ read-pkg@1.1.0
│ │   │   ├─┬ load-json-file@1.1.0
│ │   │   │ └── pify@2.3.0
│ │   │   ├─┬ normalize-package-data@2.3.5
│ │   │   │ ├── hosted-git-info@2.1.5
│ │   │   │ ├─┬ is-builtin-module@1.0.0
│ │   │   │ │ └── builtin-modules@1.1.1
│ │   │   │ └─┬ validate-npm-package-license@3.0.1
│ │   │   │   ├─┬ spdx-correct@1.0.2
│ │   │   │   │ └── spdx-license-ids@1.2.2
│ │   │   │   └── spdx-expression-parse@1.0.4
│ │   │   └── path-type@1.1.0
│ │   ├── require-directory@2.1.1
│ │   ├── require-main-filename@1.0.1
│ │   ├── set-blocking@2.0.0
│ │   ├─┬ string-width@1.0.2
│ │   │ ├─┬ code-point-at@1.0.1
│ │   │ │ └── number-is-nan@1.0.1
│ │   │ ├── is-fullwidth-code-point@1.0.0
│ │   │ └─┬ strip-ansi@3.0.1
│ │   │   └── ansi-regex@2.0.0
│ │   ├── which-module@1.0.0
│ │   ├── window-size@0.2.0
│ │   ├── y18n@3.2.1
│ │   └─┬ yargs-parser@4.0.2
│ │     └── camelcase@3.0.0
│ ├── connect-history-api-fallback@1.3.0
│ ├── connect-logger@0.0.1
│ └── minimist@1.2.0
├── reflect-metadata@0.1.8
├─┬ rxjs@5.0.0-beta.12
│ └── symbol-observable@1.0.4
├─┬ systemjs@0.19.39
│ └── when@3.7.7
├── typescript@2.0.3
├─┬ typings@1.4.0
│ ├── any-promise@1.3.0
│ ├── archy@1.0.0
│ ├── bluebird@3.4.6
│ ├─┬ chalk@1.1.3
│ │ ├── ansi-styles@2.2.1
│ │ ├─┬ has-ansi@2.0.0
│ │ │ └── ansi-regex@2.0.0
│ │ ├── strip-ansi@3.0.1
│ │ └── supports-color@2.0.0
│ ├─┬ columnify@1.5.4
│ │ ├─┬ strip-ansi@3.0.1
│ │ │ └── ansi-regex@2.0.0
│ │ └─┬ wcwidth@1.0.1
│ │   └─┬ defaults@1.0.3
│ │     └── clone@1.0.2
│ ├── has-unicode@2.0.1
│ ├── listify@1.0.0
│ ├─┬ typings-core@1.6.0
│ │ ├── array-uniq@1.0.3
│ │ ├─┬ configstore@2.1.0
│ │ │ ├─┬ dot-prop@3.0.0
│ │ │ │ └── is-obj@1.0.1
│ │ │ ├─┬ mkdirp@0.5.1
│ │ │ │ └── minimist@0.0.8
│ │ │ ├── os-tmpdir@1.0.2
│ │ │ ├── osenv@0.1.3
│ │ │ ├── uuid@2.0.3
│ │ │ └─┬ write-file-atomic@1.2.0
│ │ │   ├── imurmurhash@0.1.4
│ │ │   └── slide@1.1.6
│ │ ├─┬ debug@2.2.0
│ │ │ └── ms@0.7.1
│ │ ├─┬ detect-indent@4.0.0
│ │ │ └─┬ repeating@2.0.1
│ │ │   └── is-finite@1.0.2
│ │ ├── graceful-fs@4.1.9
│ │ ├─┬ has@1.0.1
│ │ │ └── function-bind@1.1.0
│ │ ├─┬ invariant@2.2.1
│ │ │ └─┬ loose-envify@1.2.0
│ │ │   └── js-tokens@1.0.3
│ │ ├─┬ is-absolute@0.2.6
│ │ │ ├─┬ is-relative@0.2.1
│ │ │ │ └─┬ is-unc-path@0.1.1
│ │ │ │   └── unc-path-regex@0.1.2
│ │ │ └── is-windows@0.2.0
│ │ ├── lockfile@1.0.2
│ │ ├─┬ make-error-cause@1.2.2
│ │ │ └── make-error@1.2.1
│ │ ├─┬ mkdirp@0.5.1
│ │ │ └── minimist@0.0.8
│ │ ├─┬ object.pick@1.1.2
│ │ │ └─┬ isobject@2.1.0
│ │ │   └── isarray@1.0.0
│ │ ├─┬ parse-json@2.2.0
│ │ │ └─┬ error-ex@1.3.0
│ │ │   └── is-arrayish@0.2.1
│ │ ├─┬ popsicle@8.2.0
│ │ │ ├── arrify@1.0.1
│ │ │ ├─┬ concat-stream@1.5.2
│ │ │ │ ├── readable-stream@2.0.6
│ │ │ │ └── typedarray@0.0.6
│ │ │ ├─┬ form-data@2.1.1
│ │ │ │ └── asynckit@0.4.0
│ │ │ ├── throwback@1.1.1
│ │ │ └── tough-cookie@2.2.2
│ │ ├─┬ popsicle-proxy-agent@3.0.0
│ │ │ ├─┬ http-proxy-agent@1.0.0
│ │ │ │ └─┬ agent-base@2.0.1
│ │ │ │   └── semver@5.0.3
│ │ │ └── https-proxy-agent@1.0.0
│ │ ├── popsicle-retry@3.2.1
│ │ ├── popsicle-rewrite@1.0.0
│ │ ├── popsicle-status@2.0.0
│ │ ├── promise-finally@2.2.1
│ │ ├─┬ rc@1.1.6
│ │ │ ├── deep-extend@0.4.1
│ │ │ ├── ini@1.3.4
│ │ │ └── strip-json-comments@1.0.4
│ │ ├─┬ rimraf@2.5.4
│ │ │ └─┬ glob@7.1.1
│ │ │   ├── fs.realpath@1.0.0
│ │ │   ├─┬ inflight@1.0.6
│ │ │   │ └── wrappy@1.0.2
│ │ │   └── once@1.4.0
│ │ ├─┬ sort-keys@1.1.2
│ │ │ └── is-plain-obj@1.1.0
│ │ ├── string-template@1.0.0
│ │ ├─┬ strip-bom@2.0.0
│ │ │ └── is-utf8@0.2.1
│ │ ├── thenify@3.2.1
│ │ ├── throat@3.0.0
│ │ ├─┬ touch@1.0.0
│ │ │ └─┬ nopt@1.0.10
│ │ │   └── abbrev@1.0.9
│ │ └── zip-object@0.1.0
│ ├─┬ update-notifier@1.0.2
│ │ ├─┬ boxen@0.6.0
│ │ │ ├── ansi-align@1.1.0
│ │ │ ├── camelcase@2.1.1
│ │ │ ├─┬ chalk@1.1.3
│ │ │ │ ├── ansi-styles@2.2.1
│ │ │ │ ├─┬ has-ansi@2.0.0
│ │ │ │ │ └── ansi-regex@2.0.0
│ │ │ │ ├── strip-ansi@3.0.1
│ │ │ │ └── supports-color@2.0.0
│ │ │ ├── cli-boxes@1.0.0
│ │ │ ├── filled-array@1.1.0
│ │ │ └── widest-line@1.0.0
│ │ ├─┬ chalk@1.1.3
│ │ │ ├── ansi-styles@2.2.1
│ │ │ ├─┬ has-ansi@2.0.0
│ │ │ │ └── ansi-regex@2.0.0
│ │ │ ├── strip-ansi@3.0.1
│ │ │ └── supports-color@2.0.0
│ │ ├── is-npm@1.0.0
│ │ ├─┬ latest-version@2.0.0
│ │ │ └─┬ package-json@2.4.0
│ │ │   ├─┬ got@5.6.0
│ │ │   │ ├─┬ create-error-class@3.0.2
│ │ │   │ │ └── capture-stack-trace@1.0.0
│ │ │   │ ├── duplexer2@0.1.4
│ │ │   │ ├── is-redirect@1.0.0
│ │ │   │ ├── is-retry-allowed@1.1.0
│ │ │   │ ├── is-stream@1.1.0
│ │ │   │ ├── lowercase-keys@1.0.0
│ │ │   │ ├── node-status-codes@1.0.0
│ │ │   │ ├── read-all-stream@3.1.0
│ │ │   │ ├── timed-out@2.0.0
│ │ │   │ ├── unzip-response@1.0.1
│ │ │   │ └─┬ url-parse-lax@1.0.0
│ │ │   │   └── prepend-http@1.0.4
│ │ │   ├── registry-auth-token@3.1.0
│ │ │   └── registry-url@3.1.0
│ │ ├── lazy-req@1.1.0
│ │ ├─┬ semver-diff@2.1.0
│ │ │ └── semver@5.3.0
│ │ └─┬ xdg-basedir@2.0.0
│ │   └── os-homedir@1.0.2
│ ├── wordwrap@1.0.0
│ └── xtend@4.0.1
└── zone.js@0.6.26

npm WARN corefabric.io@1.0.0 No description
npm WARN corefabric.io@1.0.0 No repository field.
npm WARN corefabric.io@1.0.0 No license field.

> corefabric.io@1.0.0 typings /Users/ben/work/corefabric.io/a2
> typings "install"

typings WARN deprecated 10/19/2016: "registry:dt/node#6.0.0+20160909174046" is deprecated (updated, replaced or removed)
typings WARN deprecated 9/14/2016: "registry:dt/core-js#0.0.0+20160725163759" is deprecated (updated, replaced or removed)
typings WARN deprecated 10/3/2016: "registry:dt/jasmine#2.2.0+20160621224255" is deprecated (updated, replaced or removed)

├── core-js (global)
├── jasmine (global)
└── node (global)

---8<------8<------8<------8<------8<------8<------8<---

Terminal window #2:

Start the development web server (nodejs dev environment)

STEP 2 of 2: pretty painful, too.

awfulhak:a2 ben$ ./build.sh

> corefabric.io@1.0.0 start /Users/ben/work/corefabric.io/a2
> tsc && concurrently "tsc -w" "lite-server"

[1] Did not detect a `bs-config.json` or `bs-config.js` override file. Using lite-server defaults...
[1] ** browser-sync config **
[1] { injectChanges: false,
[1]   files: [ './**/*.{html,htm,css,js}' ],
[1]   watchOptions: { ignored: 'node_modules' },
[1]   server: { baseDir: './', middleware: [ [Function], [Function] ] } }
[1] [BS] Access URLs:
[1]  ---------------------------------------
[1]        Local: http://localhost:3000
[1]     External: http://172.16.127.135:3000
[1]  ---------------------------------------
[1]           UI: http://localhost:3001
[1]  UI External: http://172.16.127.135:3001
[1]  ---------------------------------------
[1] [BS] Serving files from: ./
[1] [BS] Watching files...


---8<------8<------8<------8<------8<------8<------8<---
```
