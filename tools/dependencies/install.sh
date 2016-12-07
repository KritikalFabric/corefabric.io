#!/bin/sh

source ./env.sh



if test "X$COREFABRIC_ENV" == "Xtest"; then			# gcc, libtool

cat << EOF > $COREFABRIC_DEST/share/config.site
### this file is maintained by tools/dependencies/install.sh
export ac_cv_file__dev_ptmx=no
export ac_cv_file__dev_ptc=no
EOF


cd $COREFABRIC/tools/dependencies/gcc-4.9.4 && (
echo 'INSTALLING gcc';
( \
        (
		./contrib/download_prerequisites \
		cd .. \
		mkdir gcc-objdir \
		cd gcc-objdir \
		$PWD/../gcc-4.9.4/configure --prefix=$COREFABRIC_DEST \
                	&& $MAKE $COREFABRIC_SPEED && $MAKE install) || (echo 'FAILED' && exit -1) \
) || exit -1;
echo 'INSTALLED  gcc';
)

echo 'BYE!'
exit -1;

cd $COREFABRIC/tools/dependencies/help2man-1.47.4 && (
echo 'INSTALLING help2man';
( \
        (./configure --build=$COREFABRIC_BUILD --host=$COREFABRIC_HOST --prefix=$COREFABRIC_DEST \
		&& $MAKE $COREFABRIC_SPEED && $MAKE install) || (echo 'FAILED' && exit -1) \
) || exit -1;
echo 'INSTALLED  help2man';
)

cd $COREFABRIC/tools/dependencies/libtool && (
echo 'INSTALLING libtool';
( \
	./bootstrap && \
	(./configure --build=$COREFABRIC_BUILD --host=$COREFABRIC_HOST --prefix=$COREFABRIC_DEST \
		&& $MAKE $COREFABRIC_SPEED && $MAKE install) || (echo 'FAILED' && exit -1) \
) || exit -1;
echo 'INSTALLED  libtool';
)

fi; # end gcc, libtool (Xtest)

cd $COREFABRIC/tools/dependencies/protobuf && (
echo 'INSTALLING protobuf compiler';
( \
	./autogen.sh && \
	$LIBTOOLIZE && \
	(./configure --prefix=$COREFABRIC_DEST && $MAKE $COREFABRIC_SPEED && $MAKE install) || \
	(echo 'FAILED' && exit -1) \
) || exit -1;
echo 'INSTALLED  protobuf compiler';
)

if test "X$COREFABRIC_ENV" == "Xinsane"; then       # openssl

cd $COREFABRIC/tools/dependencies/openssl && (
echo 'INSTALLING openssl';
( \
       (./config \
	 --prefix=$COREFABRIC_DEST --openssldir=$COREFABRIC_DEST/.ssl \
	&& $MAKE $COREFABRIC_SPEED && $MAKE install) || \
       (echo 'FAILED' && exit -1) \
) || exit -1;
echo 'INSTALLED  openssl ';
)

fi; # end openssl (Xinsane)

if test "X$COREFABRIC_JEP" == "Xjep"; then
if test "X$COREFABRIC_ENV" == "Xlinux"; then # python/jep only reliably builds on linunx at the moment

cd $COREFABRIC/tools/dependencies/Python-3.4.5 && (
echo 'INSTALLING python';
( \
	(autoreconf && aclocal && $LIBTOOLIZE && ./configure --build=$COREFABRIC_BUILD --host=$COREFABRIC_HOST --target=$COREFABRIC_TARGET --without-pydebug --enable-shared --disable-profiling --enable-ipv6 --enable-big-digits --disable-loadable-sqlite-extensions --prefix=$COREFABRIC_DEST && $MAKE $COREFABRIC_SPEED && $MAKE install) || \
	(echo 'FAILED' && exit -1) \
) || exit -1;
echo 'INSTALLED  python';
)

cd $COREFABRIC/tools/dependencies/jep && (
echo 'INSTALLING jep';
( \
	($COREFABRIC_DEST/bin/python3.4 setup.py clean build install) || \
	(echo 'FAILED' && exit -1) \
) || exit -1;
echo 'INSTALLED  jep';
);

fi;
fi; # end python/jep
