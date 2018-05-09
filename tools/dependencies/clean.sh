#!/bin/sh
source ./env.sh

rm -rf .local
mkdir -p .local/share;
mkdir -p .local/lib;
#rm -rf get-pip.py;
#
#curl -O https://bootstrap.pypa.io/get-pip.py

if test "X$COREFABRIC_ENV" == "Xtest"; then

#    cd $COREFABRIC/tools/dependencies && (
#    echo 'CLEANING gcc';
#    ( \
#        rm -rf gcc-4.9.4 &&\
#        tar xjf gcc-4.9.4.tar.bz2 || exit -1; \
#    );
#    echo 'CLEANED  gcc';
#    )

    cd $COREFABRIC/tools/dependencies/libtool && (
    echo 'CLEANING libtool';
    ( \
            (git checkout -f master || true) &&\
            (git pull || true) &&\
            ($MAKE clean || true) \
    );
    echo 'CLEANED  libtool';
    )

fi;

#cd $COREFABRIC/tools/dependencies/protobuf && (
#echo 'CLEANING protobuf compiler';
#( \
#	(git checkout -f master || true) &&\
#	(git pull || true) &&\
#	git apply ../protobuf.patch || exit -1; \
#);
#echo 'CLEANED  protobuf compiler';
#)

#cd $COREFABRIC/tools/dependencies && (
#echo 'CLEANING python';
#( \
#        rm -rf Python-3.4.5;
#        tar xvzf Python-3.4.5.tgz;
#	cd Python-3.4.5 && ( \
#		(cp Makefile.pre.in Makefile.pre.in.orig) && \
#		(cp setup.py setup.py.orig) && \
#		(cp configure.ac configure.ac.orig) && \
#		(cp Modules/getpath.c Modules/getpath.c.orig) && \
#		(cp Modules/posixmodule.c Modules/posixmodule.c.orig) && \
#		(cp Lib/sysconfig.py Lib/sysconfig.py.orig) && \
#		(patch -f -u -i ../Python.patch) && \
#		(cd Modules/ && patch -p1 -f -u -i ../../Python.patch.Modules) && \
#		(cd Lib/ && patch -p1 -f -u -i ../../Python.patch.Lib) \
#		|| exit -1; \
#	) || exit -1;
#);
#echo 'CLEANED  python';
#)
