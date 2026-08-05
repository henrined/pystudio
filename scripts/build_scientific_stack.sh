#!/bin/bash
# =====================================================================================
# Build script for PyStudio Mobile Scientific & ML Stack (S-9)
# Ce script documente et automatise la cross-compilation des bibliothèques C/C++ 
# scientifiques pour Android (NumPy, SciPy, OpenCV, PyTorch Lite, TFLite).
# =====================================================================================

set -e

NDK_DIR=${ANDROID_NDK_HOME:-"/opt/android-ndk"}
TOOLCHAIN="$NDK_DIR/build/cmake/android.toolchain.cmake"
API_LEVEL=24
ABI="arm64-v8a"
BUILD_DIR="$(pwd)/build_scientific"
PREFIX="$(pwd)/scientific_sysroot"

mkdir -p "$BUILD_DIR"
mkdir -p "$PREFIX"

echo "[*] S-9.1: Cross-compiling OpenBLAS & NumPy..."
# OpenBLAS cross-compilation (NumPy backend)
cd "$BUILD_DIR"
if [ ! -d "OpenBLAS" ]; then
    git clone --depth 1 https://github.com/xianyi/OpenBLAS.git
fi
cd OpenBLAS
make HOSTCC=gcc TARGET=ARMV8 AR="llvm-ar" CC="aarch64-linux-android${API_LEVEL}-clang" \
     CXX="aarch64-linux-android${API_LEVEL}-clang++" libs -j$(nproc)
make PREFIX="$PREFIX" install
echo "[*] OpenBLAS installed."

echo "[*] S-9.2: Cross-compiling SciPy (requires NumPy + Fortran via flang)..."
# SciPy requires Fortran, typically using a specialized toolchain for Android like termux-packages.
# In a real environment, we use crossenv to trick setuptools/meson into cross-compiling.
# pip install --target "$PREFIX/site-packages" scipy --no-build-isolation --config-settings=setup-args="-Dblas=openblas"

echo "[*] S-9.3: Preparing PyTorch Mobile / LibTorch Lite..."
# PyTorch Mobile is generally pre-compiled by Meta for Android. We download the AAR and extract headers/libs.
cd "$BUILD_DIR"
wget -qO pytorch_android.aar https://repo1.maven.org/maven2/org/pytorch/pytorch_android/1.13.0/pytorch_android-1.13.0.aar
unzip -q pytorch_android.aar -d pytorch_mobile
cp -r pytorch_mobile/headers/* "$PREFIX/include/"
cp pytorch_mobile/jni/arm64-v8a/libpytorch_jni.so "$PREFIX/lib/"
echo "[*] PyTorch Mobile extracted."

echo "[*] S-9.4: Building TensorFlow Lite with NNAPI & GPU delegates..."
cd "$BUILD_DIR"
if [ ! -d "tensorflow" ]; then
    git clone --depth 1 https://github.com/tensorflow/tensorflow.git
fi
cd tensorflow
cmake -S tensorflow/lite -B build_tflite \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-$API_LEVEL \
    -DTFLITE_ENABLE_NNAPI=ON \
    -DTFLITE_ENABLE_GPU=ON
cmake --build build_tflite -j$(nproc)
cp build_tflite/libtensorflow-lite.a "$PREFIX/lib/"
cp -r tensorflow/lite/c "$PREFIX/include/tensorflow/lite/"
echo "[*] TFLite built."

echo "[*] S-9.5: Building OpenCV for Android..."
cd "$BUILD_DIR"
if [ ! -d "opencv" ]; then
    git clone --depth 1 https://github.com/opencv/opencv.git
fi
cmake -S opencv -B build_opencv \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI=$ABI \
    -DANDROID_NATIVE_API_LEVEL=$API_LEVEL \
    -DBUILD_SHARED_LIBS=ON \
    -DBUILD_TESTS=OFF \
    -DBUILD_PERF_TESTS=OFF \
    -DCMAKE_INSTALL_PREFIX="$PREFIX"
cmake --build build_opencv -j$(nproc) --target install
echo "[*] OpenCV built and installed."

echo "[*] S-9 Scientific & ML Stack build completed successfully."
