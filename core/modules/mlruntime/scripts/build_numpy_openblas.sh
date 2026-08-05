#!/bin/bash
# Cross-compilation script for NumPy and OpenBLAS on Android NDK
# Specification S-9.1

set -e

export NDK_VERSION="26.1.10909125"
export NDK_ROOT="${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}"
export TOOLCHAIN="${NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64"
export API_LEVEL=24

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
PREFIX="$(pwd)/build_out"

echo "[*] Building OpenBLAS for Android..."
git clone https://github.com/xianyi/OpenBLAS.git openblas_src || true
cd openblas_src

for ABI in "${ABIS[@]}"; do
    echo " -> Building OpenBLAS for ${ABI}"
    TARGET=""
    if [ "$ABI" == "arm64-v8a" ]; then
        TARGET="ARMV8"
    elif [ "$ABI" == "armeabi-v7a" ]; then
        TARGET="ARMV7"
    elif [ "$ABI" == "x86_64" ]; then
        TARGET="HASWELL"
    fi

    make HOSTCC=gcc \
         CC="${TOOLCHAIN}/bin/clang --target=${ABI}-linux-android${API_LEVEL}" \
         AR="${TOOLCHAIN}/bin/llvm-ar" \
         TARGET=$TARGET \
         OSNAME=Android \
         PREFIX="${PREFIX}/${ABI}" \
         install
done
cd ..

echo "[*] Building NumPy with OpenBLAS..."
git clone https://github.com/numpy/numpy.git numpy_src || true
cd numpy_src
git checkout v1.26.4

for ABI in "${ABIS[@]}"; do
    echo " -> Building NumPy for ${ABI}"
    export OPENBLAS="${PREFIX}/${ABI}"
    export CC="${TOOLCHAIN}/bin/clang --target=${ABI}-linux-android${API_LEVEL}"
    export CXX="${TOOLCHAIN}/bin/clang++ --target=${ABI}-linux-android${API_LEVEL}"
    export AR="${TOOLCHAIN}/bin/llvm-ar"
    
    cat <<EOF > site.cfg
[openblas]
libraries = openblas
library_dirs = ${OPENBLAS}/lib
include_dirs = ${OPENBLAS}/include
EOF

    python3 setup.py bdist_wheel -p "android_${API_LEVEL}_${ABI}"
    cp dist/*.whl "${PREFIX}/${ABI}/"
done
cd ..

echo "[+] NumPy and OpenBLAS build completed successfully!"
