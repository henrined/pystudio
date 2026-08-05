#!/bin/bash
# =====================================================================================
# Build script for PyStudio Mobile Jupyter Stack (S-10.1)
# Ce script package ipykernel et ses dépendances pour le runtime embarqué.
# =====================================================================================

set -e

PREFIX="$(pwd)/jupyter_sysroot"
mkdir -p "$PREFIX"

echo "[*] S-10.1: Packaging ipykernel and pure Python dependencies..."
# On télécharge les dépendances pure-python de ipykernel
# On ignore pyzmq car le protocole ZMQ sera émulé (S-10.3)
pip install --target "$PREFIX/site-packages" ipykernel jupyter-client tornado traitlets jupyter-core \
    --no-deps \
    --ignore-installed

echo "[*] Jupyter Stack build completed successfully."
