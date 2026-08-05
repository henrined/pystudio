# ⚠️ PRIORITÉ 5 — S-9 : ML Runtime (Stubs de dépendances)
## PROMPT 5.1 — Rendre `mlruntime.cpp` production-ready

**Fichiers** :
- `core/modules/mlruntime/src/mlruntime.cpp`
- `core/modules/mlruntime/src/deps_stubs.cpp`
- `core/modules/mlruntime/include/mlruntime.h`

**Problème** : `deps_stubs.cpp` fournit des implémentations factices (`TfLiteModelCreateFromFile` retourne `(TfLiteModel*)1`). `mlruntime.cpp` hardcode `outFloats = 2` et utilise une API LibTorch mockée.

---

### EXIGENCES STRICTES :
1. Stratégie de compilation conditionnelle :
   - Ajoute des guards CMake et preprocessor : `#ifdef PYSTUDIO_HAS_TFLITE`, `#ifdef PYSTUDIO_HAS_OPENCV`, `#ifdef PYSTUDIO_HAS_LIBTORCH`
   - Si la dépendance réelle est disponible, linke contre elle
   - Si elle n'est pas disponible, désactive la fonctionnalité proprement (retourne une erreur "TFLite not available" au lieu de simuler le succès)
   - Supprime `deps_stubs.cpp` du build par défaut (ne le garder QUE comme option de build pour les tests unitaires)
2. `RunTFLiteInference()` :
   - Récupère la taille de sortie dynamiquement via `TfLiteTensorByteSize(outputTensor) / sizeof(float)`
   - Ne hardcode PAS `outFloats = 2`
3. `RunTorchInference()` :
   - Utilise la vraie API LibTorch : `torch::from_blob()` pour créer le tensor d'entrée
   - Appelle `module.forward({input_tensor}).toTensor()`
   - Convertit le tensor de sortie en `std::vector<float>`
4. Mets à jour le CMakeLists.txt du module :
   - Option `PYSTUDIO_USE_TFLITE=ON/OFF`
   - Option `PYSTUDIO_USE_OPENCV=ON/OFF`
   - Option `PYSTUDIO_USE_LIBTORCH=ON/OFF`
   - `find_package()` conditionnel pour chaque dépendance
5. Les tests doivent aussi être conditionnels (`#ifdef`).

### INTERDIT :
`(TfLiteModel*)1`, `Mat(100,100,0)`, `outFloats = 2`, `forward(vector<float>)`.
