#!/bin/bash
# Script to cross-compile OpenCV for Android NDK
# Specification S-9.5

set -e

export NDK_VERSION="26.1.10909125"
export NDK_ROOT="${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}"
export API_LEVEL=24

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
PREFIX="$(pwd)/build_out"

echo "[*] Building OpenCV for Android..."
git clone https://github.com/opencv/opencv.git || true
cd opencv
git checkout 4.9.0

for ABI in "${ABIS[@]}"; do
    echo " -> Building OpenCV for ${ABI}"
    mkdir -p build_${ABI}
    cd build_${ABI}
    
    cmake -DCMAKE_TOOLCHAIN_FILE="${NDK_ROOT}/build/cmake/android.toolchain.cmake" \
          -DANDROID_ABI="${ABI}" \
          -DANDROID_NATIVE_API_LEVEL=${API_LEVEL} \
          -DBUILD_SHARED_LIBS=ON \
          -DBUILD_TESTS=OFF \
          -DBUILD_PERF_TESTS=OFF \
          -DCMAKE_INSTALL_PREFIX="${PREFIX}/${ABI}/opencv" \
          ..
          
    make -j$(nproc)
    make install
    cd ..
done
cd ..

echo "[+] OpenCV build completed successfully!"
