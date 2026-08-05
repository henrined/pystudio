# 🔧 PRIORITÉ 9 — AIDL, CMake et Infrastructure manquante
## PROMPT 9.2 — Vérification et complétion du build CMake natif

**Fichier principal** : `core/CMakeLists.txt`
**Fichiers modules** : `core/modules/*/CMakeLists.txt`

---

### EXIGENCES STRICTES :
1. Vérifie que le CMakeLists.txt racine :
   - Définit le projet "pystudio_core" avec C++20
   - Inclut TOUS les modules : pyembed, cxxtoolchain, dbgbridge, gitengine, mlruntime
   - Configure les ABI filters : arm64-v8a, armeabi-v7a, x86_64
   - Link contre android log (-llog)
   - Active ASan en mode Debug
   - Configure FetchContent pour Google Test
   - Génère les cibles de test via `enable_testing()` + `add_test()`
   - Installe les .so dans les bons répertoires pour le packaging Android

2. Vérifie chaque CMakeLists.txt de module :
   a) `pyembed/CMakeLists.txt` :
      - Link libpython3.14.so (ou la version active)
      - Produit libpyembed.so ET librunner_jni.so
   b) `cxxtoolchain/CMakeLists.txt` :
      - Produit libcxxtoolchain.so
      - Link pystudio_core
   c) `dbgbridge/CMakeLists.txt` :
      - Produit libdbgbridge.so ET libdbgbridge_jni.so (pour DebugService)
      - Link pystudio_core, pthread
   d) `gitengine/CMakeLists.txt` :
      - Link libgit2 (find_package ou FetchContent)
      - Produit libgitengine.so (incluant gitengine_jni.cpp)
   e) `mlruntime/CMakeLists.txt` :
      - Options conditionnelles PYSTUDIO_USE_TFLITE, PYSTUDIO_USE_OPENCV, PYSTUDIO_USE_LIBTORCH
      - NE link PAS deps_stubs.cpp par défaut en production
      - find_package conditionnel pour chaque dépendance

3. Vérifie que le `build.gradle` Android configure externalNativeBuild avec le CMakeLists.txt racine.

4. Tente une compilation dry-run : `cmake -B core/build -S core/ && cmake --build core/build`
   Corrige chaque erreur de compilation.

### INTERDIT :
Modules manquants dans le CMake, libraries non linkées, erreurs de compilation ignorées.
