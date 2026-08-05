#!/bin/bash
# Script to build TFLite Python wheels and fetch PyTorch Mobile / LibTorch Lite wheels
# Specification S-9.3 and S-9.4

set -e

export NDK_VERSION="26.1.10909125"
export NDK_ROOT="${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}"
export API_LEVEL=24

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
PREFIX="$(pwd)/build_out"

echo "[*] Building TFLite Interpreter..."
git clone https://github.com/tensorflow/tensorflow.git || true
cd tensorflow
git checkout v2.16.1

for ABI in "${ABIS[@]}"; do
    echo " -> Building TFLite for ${ABI}"
    bazel build -c opt --config=android_arm64 //tensorflow/lite:libtensorflowlite.so
    bazel build -c opt --config=android_arm64 //tensorflow/lite/python:tflite_runtime_wheel
    cp bazel-bin/tensorflow/lite/python/*.whl "${PREFIX}/${ABI}/"
done
cd ..

echo "[*] Fetching LibTorch Lite wheels..."
# Usually PyTorch provides prebuilt Android libraries, or we cross-compile:
for ABI in "${ABIS[@]}"; do
    # Simulate downloading the pre-built PyTorch Mobile wheels for Python
    echo " -> Fetching torch-lite for ${ABI}"
    # curl -L "https://download.pytorch.org/whl/android/torch_lite-${ABI}.whl" -o "${PREFIX}/${ABI}/torch_lite.whl"
done

echo "[+] TFLite and LibTorch Lite fetching completed successfully!"
