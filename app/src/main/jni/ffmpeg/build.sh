#!/bin/bash

if [ "$NDK" = "" ]; then
echo NDK variable not set, assuming NDK=/home/sinsc/Downloads/android-ndk-r8e/
export NDK=/home/sinsc/Downloads/android-ndk-r8e/
fi

SYSROOT=$NDK/platforms/android-9/arch-arm
TOOLCHAIN=`echo $NDK/toolchains/arm-linux-androideabi-4.4.3/prebuilt/*`
export PATH=$TOOLCHAIN/bin:$PATH
ANDROID_SOURCE=../android-source
ANDROID_LIBS=../android-libs
ABI="armeabi-v7a"

rm -rf ./build/ffmpeg
mkdir -p ./build/ffmpeg

DEST=./build/ffmpeg
FLAGS="--target-os=linux --cross-prefix=arm-linux-androideabi- --arch=arm --cpu=armv7-a"
FLAGS="$FLAGS --sysroot=$SYSROOT"
FLAGS="$FLAGS --target-os=linux \
--arch=arm \
--enable-cross-compile \
--cross-prefix=arm-linux-androideabi- \
--enable-asm --enable-version3 --enable-shared"

EXTRA_CFLAGS="$EXTRA_CFLAGS -I$NDK/sources/cxx-stl/gnu-libstdc++/4.4.3/include -I$NDK/sources/cxx-stl/gnu-libstdc++/4.4.3/libs/$ABI/include"

EXTRA_CFLAGS="$EXTRA_CFLAGS -march=armv7-a  -mfpu=neon -mfloat-abi=softfp"
EXTRA_LDFLAGS="-Wl,--fix-cortex-a8 -L$ANDROID_LIBS -Wl,-rpath-link,$ANDROID_LIBS -L$NDK/sources/cxx-stl/gnu-libstdc++/4.4.3/libs/$ABI"
EXTRA_CXXFLAGS="-Wno-multichar -fno-exceptions -fno-rtti"
DEST="$DEST/$ABI"
FLAGS="$FLAGS --prefix=$DEST"

mkdir -p $DEST

echo $FLAGS --extra-cflags="$EXTRA_CFLAGS" --extra-ldflags="$EXTRA_LDFLAGS" --extra-cxxflags="$EXTRA_CXXFLAGS" > $DEST/info.txt
./configure $FLAGS --extra-cflags="$EXTRA_CFLAGS" --extra-ldflags="$EXTRA_LDFLAGS" --extra-cxxflags="$EXTRA_CXXFLAGS" | tee $DEST/configuration.txt
[ $PIPESTATUS == 0 ] || exit 1
make clean
make -j4 || exit 1
make install || exit 1

