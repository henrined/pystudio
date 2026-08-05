#!/bin/bash
# =====================================================================================
# Build script for PyStudio Mobile AI Stack (S-11.1)
# Ce script compile llama.cpp (GGUF) pour Android via le NDK et télécharge un modèle
# de test léger (ex. Phi-2 ou TinyLlama quantifié) pour les tests on-device.
# =====================================================================================

set -e

NDK_DIR=${ANDROID_NDK_HOME:-"/opt/android-ndk"}
TOOLCHAIN="$NDK_DIR/build/cmake/android.toolchain.cmake"
API_LEVEL=24
ABI="arm64-v8a"
BUILD_DIR="$(pwd)/build_ai"
PREFIX="$(pwd)/ai_sysroot"

mkdir -p "$BUILD_DIR"
mkdir -p "$PREFIX/models"

echo "[*] S-11.1: Cross-compiling llama.cpp for Android..."
cd "$BUILD_DIR"
if [ ! -d "llama.cpp" ]; then
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git
fi
cd llama.cpp

cmake -S . -B build_android \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-$API_LEVEL \
    -DLLAMA_BUILD_TESTS=OFF \
    -DLLAMA_BUILD_EXAMPLES=OFF \
    -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_INSTALL_PREFIX="$PREFIX"

cmake --build build_android -j$(nproc) --target install

echo "[*] Downloading lightweight test model (TinyLlama Q4_K_M)..."
# Download a tiny model to test the integration (only if not already downloaded)
MODEL_PATH="$PREFIX/models/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
if [ ! -f "$MODEL_PATH" ]; then
    wget -qO "$MODEL_PATH" "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.q4_k_m.gguf"
fi

echo "[*] AI Stack build completed successfully."
