#!/bin/sh
MAKE=make; export MAKE
COREFABRIC=`pwd`; export COREFABRIC
COREFABRIC_DEST=`pwd`/.local; export COREFABRIC_DEST

if test -z "$COREFABRIC_ENV"; then
	 echo "please set ENV var 'COREFABRIC_ENV' to '[joyent|pi|linux|mac]'";
	 exit -1;
fi

if test -z "$COREFABRIC_OAUTH2_GOOGLE_CLIENT_ID"; then
	echo "please set ENV var 'COREFABRIC_OAUTH2_GOOGLE_CLIENT_ID' to a google oauth2 client id";
	exit -1;
fi

if test -z "$COREFABRIC_OAUTH2_GOOGLE_CLIENT_SECRET"; then
	echo "please set ENV var 'COREFABRIC_OAUTH2_GOOGLE_CLIENT_SECRET' to a google oauth2 client secret";
	exit -1;
fi

###############################################################################
#
# This file is automatically included by the toolchain build scripts;
# as such please take care not to screw up because you're fixing it.
#
# corefabric.io$ ./tools/dependencies/clean.sh &&\
#                ./tools/dependencies/install.sh
#
###############################################################################

CPPFLAGS="-DCOREFABRIC_ENV__$COREFABRIC_ENV=1"; export CPPFLAGS

case "X$COREFABRIC_ENV" in
    Xtest)
###############################################################################
# everycity smartos                                                           #
###############################################################################
COREFABRIC_SPEED=' -j '
COREFABRIC_BUILD='i386-pc-solaris2.11'; export COREFABRIC_BUILD
COREFABRIC_HOST='x86_64-pc-solaris2.11'; export COREFABRIC_HOST
COREFABRIC_TARGET='x86_64-pc-solaris2.11'; export COREFABRIC_TARGET
COREFABRIC_PFX='x86_64-solaris2.11'; export COREFABRIC_PFX
PATH="$COREFABRIC_DEST/bin:/ec/x86_64-pc-solaris2.11/bin:/usr/bin/amd64:/usr/ccs/bin/amd64:/ec/bin/amd64:/ec/bin:/usr/bin"; export PATH
CPPFLAGS="$CPPFLAGS -std=c1x -I/ec/lib/gcc/i386-pc-solaris2.11/4.4.5/include-fixed/ -L/ec/lib/amd64 -I$JAVA_HOME/include/solaris -I$COREFABRIC_DEST/include -I$COREFABRIC_DEST/include/openssl -I/ec/include/c++/4.4.5 -Wl,-rpath=$COREFABRIC_DEST/lib -Wl,-rpath=$COREFABRIC_DEST/lib64 -Wl,-rpath=$JAVA_HOME/lib -mtune=native -march=native -m64"; export CPPFLAGS
CFLAGS=$CPPFLAGS; export CFLAGS
LDFLAGS="-L/ec/lib/amd64 -L$JAVA_HOME/lib -L$COREFABRIC_DEST/lib -L$COREFABRIC_DEST/lib64"; export LDFLAGS
LD_LIBRARY_PATH="/ec/lib/amd64:$JAVA_HOME/lib:$COREFABRIC_DEST/lib:$COREFABRIC_DEST/lib64"; export LD_LIBRARY_PATH
AR="/ec/bin/amd64/ar"; export AR
LD="/ec/bin/amd64/ld"; export LD
RANLIB="/ec/bin/amd64/ranlib"; export RANLIB
CXX="/ec/bin/g++"; export CXX
CC="/ec/bin/gcc"; export CC
HELP2MAN="$COREFABRIC_DEST/bin/help2man"; export HELP2MAN
MAKEINFO="/ec/bin/amd64/makeinfo"; export MAKEINFO
LIBTOOL="$COREFABRIC_DEST/bin/$COREFABRIC_PFX-libtool"; export LIBTOOL
LIBTOOLIZE="$COREFABRIC_DEST/bin/$COREFABRIC_PFX-libtoolize"; export LIBTOOLIZE # TODO --force
LTCC="$CC -m64"; export LTCC
BUILDPYTHON="/ec/bin/python2.6"; export BUILDPYTHON
PYTHON_FOR_BUILD="/ec/bin/python2.6 -E"; export PYTHON_FOR_BUILD
CONFIG_SITE="$COREFABRIC_DEST/share/config.site"; export CONFIG_SITE
READELF=/ec/bin/amd64/readelf; export READELF
MAKE=make; export MAKE
###############################################################################
# everycity smartos                                                           #
###############################################################################
    ;;
    Xmac)
###############################################################################
# macOS sierra                                                                #
###############################################################################
COREFABRIC_SPEED=''
COREFABRIC_BUILD='x86_64-apple-darwin`uname -r`'; export COREFABRIC_BUILD
COREFABRIC_HOST='x86_64-apple-darwin`uname -r`'; export COREFABRIC_HOST
COREFABRIC_TARGET='x86_64-apple-darwin`uname -r`'; export COREFABRIC_TARGET
COREFABRIC_PFX='x86_64-apple-darwin`uname -r`'; export COREFABRIC_PFX
COREFABRIC_PYTHON_CONFIGURE_OPTS="--with-universal-archs=64-bit"; export COREFABRIC_PYTHON_CONFIGURE_OPTS
PATH="$COREFABRIC_DEST/bin:$PATH"; export PATH
LIBTOOL="glibtool"; export LIBTOOL
LIBTOOLIZE="glibtoolize"; export LIBTOOLIZE
BUILDPYTHON="/usr/bin/python2.7"; export BUILDPYTHON
PYTHON_FOR_BUILD="/usr/bin/python2.7 -E"; export PYTHON_FOR_BUILD
CONFIG_SITE="$COREFABRIC_DEST/share/config.site"; export CONFIG_SITE
###############################################################################
# macOS sierra                                                                #
###############################################################################
    ;;
    Xlinux)
###############################################################################
# linux ubuntu yakkity 64-bit                                                 #
###############################################################################
COREFABRIC_SPEED=' -j '
COREFABRIC_BUILD='x86_64-linux-gnu'; export COREFABRIC_BUILD
COREFABRIC_HOST='x86_64-linux-gnu'; export COREFABRIC_HOST
COREFABRIC_TARGET='x86_64-linux-gnu'; export COREFABRIC_TARGET
COREFABRIC_PFX='x86_64-linux-gnu'; export COREFABRIC_PFX
PATH="$COREFABRIC_DEST/bin:$PATH"; export PATH
LD_LIBRARY_PATH="$JAVA_HOME/lib:$COREFABRIC_DEST/lib"; export LD_LIBRARY_PATH
LIBTOOL="libtool"; export LIBTOOL
LIBTOOLIZE="libtoolize"; export LIBTOOLIZE
BUILDPYTHON="/usr/bin/python2.7"; export BUILDPYTHON
PYTHON_FOR_BUILD="/usr/bin/python2.7 -E"; export PYTHON_FOR_BUILD
CONFIG_SITE="$COREFABRIC_DEST/share/config.site"; export CONFIG_SITE

CPPFLAGS="$CPPFLAGS -w -fPIC -L$COREFABRIC_DEST/lib/ -Wl,-rpath=$COREFABRIC_DEST/lib -Wl,-rpath=$JAVA_HOME/lib"; export CPPFLAGS
LDFLAGS="-L$COREFABRIC_DEST/lib/ -Wl,-rpath=$COREFABRIC_DEST/lib";export LDFLAGS
CFLAGS="$CPPFLAGS"; export CFLAGS
CXX="/usr/bin/g++"; export CXX
CC="/usr/bin/gcc"; export CC
CPP="/usr/bin/cpp"; export CPP # some filenames are sacred mr shuttleworth
###############################################################################
# linux ubuntu yakkity 64-bit                                                 #
###############################################################################
    ;;
    Xexperimental-linux)
###############################################################################
# linux ubuntu yakkity 64-bit                                                 #
###############################################################################
COREFABRIC_SPEED=' -j '
COREFABRIC_BUILD='x86_64-linux-gnu'; export COREFABRIC_BUILD
COREFABRIC_HOST='x86_64-linux-gnu'; export COREFABRIC_HOST
COREFABRIC_TARGET='x86_64-linux-gnu'; export COREFABRIC_TARGET
COREFABRIC_PFX='x86_64-linux-gnu'; export COREFABRIC_PFX
PATH="$COREFABRIC_DEST/bin:$PATH"; export PATH
LD_LIBRARY_PATH="$JAVA_HOME/lib:$COREFABRIC_DEST/lib"; export LD_LIBRARY_PATH
LIBTOOL="libtool"; export LIBTOOL
LIBTOOLIZE="libtoolize"; export LIBTOOLIZE
BUILDPYTHON="/usr/bin/python2.7"; export BUILDPYTHON
PYTHON_FOR_BUILD="/usr/bin/python2.7 -E"; export PYTHON_FOR_BUILD
CONFIG_SITE="$COREFABRIC_DEST/share/config.site"; export CONFIG_SITE

CPPFLAGS="$CPPFLAGS -w -fPIC -L$COREFABRIC_DEST/lib/ -Wl,-rpath=$COREFABRIC_DEST/lib -Wl,-rpath=$JAVA_HOME/lib"; export CPPFLAGS
LDFLAGS="-L$COREFABRIC_DEST/lib/ -Wl,-rpath=$COREFABRIC_DEST/lib";export LDFLAGS
CFLAGS="$CPPFLAGS"; export CFLAGS
CXX="/usr/bin/g++"; export CXX
CC="/usr/bin/gcc"; export CC
CPP="/usr/bin/cpp"; export CPP # some filenames are sacred mr shuttleworth
###############################################################################
# linux ubuntu yakkity 64-bit                                                 #
###############################################################################
    ;;
    Xjoyent)
###############################################################################
# joyent-base 16.3.x 64-bit only (not multiarch, not 32-bit)                  #
###############################################################################
COREFABRIC_SPEED=' -j '
COREFABRIC_BUILD='x86_64-pc-solaris2.11'; export COREFABRIC_BUILD
COREFABRIC_HOST='x86_64-pc-solaris2.11'; export COREFABRIC_HOST
COREFABRIC_TARGET='x86_64-pc-solaris2.11'; export COREFABRIC_TARGET
COREFABRIC_PFX='x86_64-pc-solaris2.11'; export COREFABRIC_PFX
PATH="$COREFABRIC_DEST/bin:$PATH"; export PATH
#LD_LIBRARY_PATH="$JAVA_HOME/lib:$COREFABRIC_DEST/lib"; export LD_LIBRARY_PATH
LIBTOOL="libtool"; export LIBTOOL
LIBTOOLIZE="libtoolize"; export LIBTOOLIZE
BUILDPYTHON="/opt/local/bin/python2.7"; export BUILDPYTHON
PYTHON_FOR_BUILD="/opt/local/bin/python2.7 -E"; export PYTHON_FOR_BUILD
CONFIG_SITE="$COREFABRIC_DEST/share/config.site"; export CONFIG_SITE

CPPFLAGS="$CPPFLAGS -pthread -D_REENTRANT -I$JAVA_HOME/include/solaris -I$COREFABRIC_DEST/include -I$COREFABRIC_DEST/include/openssl -Wl,-R=$COREFABRIC_DEST/lib -Wl,-R=$JAVA_HOME/lib -mtune=native -march=native -m64"; export CPPFLAGS
LDFLAGS="-L$COREFABRIC_DEST/lib -Wl,-R=$COREFABRIC_DEST/lib";export LDFLAGS
CFLAGS="$CPPFLAGS"; export CFLAGS
CXX="/opt/local/bin/g++"; export CXX
CC="/opt/local/bin/gcc"; export CC
CPP="/opt/local/bin/cpp"; export CPP
MAKE="/opt/local/bin/gmake"; export MAKE
###############################################################################
# joyent-base 16.3.x 64-bit only (not multiarch, not 32-bit)                  #
###############################################################################
    ;;
    *)
        echo "ERROR: unsupported environment $COREFABRIC_ENV";
        exit -1;
    ;;
esac

echo '###############################################################################'
echo '# environment                                                                 #'
echo '###############################################################################'
set | grep '^COREFABRIC' | grep -v AUTH
set | grep '^[A-Z_]*FLAGS'
set | grep '^[A-Z_]*PATH'
echo '###############################################################################'
echo "# corefabric.io toolchain build                  `date` #"
echo '###############################################################################'
echo ''

