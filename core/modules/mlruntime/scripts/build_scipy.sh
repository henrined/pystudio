#!/bin/bash
# Cross-compilation script for SciPy on Android NDK
# Specification S-9.2

set -e

export NDK_VERSION="26.1.10909125"
export NDK_ROOT="${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}"
export TOOLCHAIN="${NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64"
export API_LEVEL=24

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
PREFIX="$(pwd)/build_out"

echo "[*] Building SciPy..."
git clone https://github.com/scipy/scipy.git scipy_src || true
cd scipy_src
git checkout v1.12.0

for ABI in "${ABIS[@]}"; do
    echo " -> Building SciPy for ${ABI}"
    export OPENBLAS="${PREFIX}/${ABI}"
    export CC="${TOOLCHAIN}/bin/clang --target=${ABI}-linux-android${API_LEVEL}"
    export CXX="${TOOLCHAIN}/bin/clang++ --target=${ABI}-linux-android${API_LEVEL}"
    export F77="${TOOLCHAIN}/bin/aarch64-linux-android-gfortran"
    export F90="${TOOLCHAIN}/bin/aarch64-linux-android-gfortran"
    
    # Needs a custom cross-file for Meson
    cat <<EOF > cross_file_${ABI}.txt
[binaries]
c = '${CC}'
cpp = '${CXX}'
ar = '${TOOLCHAIN}/bin/llvm-ar'
strip = '${TOOLCHAIN}/bin/llvm-strip'
pkgconfig = 'pkg-config'

[properties]
sys_root = '${TOOLCHAIN}/sysroot'
needs_exe_wrapper = true

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8'
endian = 'little'
EOF

    python3 -m build --wheel -Csetup-args="--cross-file=cross_file_${ABI}.txt"
    cp dist/*.whl "${PREFIX}/${ABI}/"
done
cd ..
echo "[+] SciPy build completed successfully!"
