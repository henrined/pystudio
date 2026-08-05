# 🔧 PRIORITÉ 9 — AIDL, CMake et Infrastructure manquante
## PROMPT 9.3 — Créer les JNI manquants pour les bridges

Les bridges React Native (S-13) appellent des services Kotlin qui à leur tour appellent du C++ natif via JNI. Certaines couches JNI manquent.

---

### EXIGENCES STRICTES :
1. `dbgbridge_jni.cpp` — Vérifie qu'il existe dans `core/modules/dbgbridge/src/` :
   - Doit exposer toutes les méthodes native déclarées dans DebugService.kt :
     nativeInitialize, nativeLaunch, nativeAttach, nativeSetBreakpoints,
     nativeContinue, nativeStepOver, nativeStepInto, nativeStepOut,
     nativePause, nativeDisconnect, nativeGetStackTrace, nativeGetScopes,
     nativeGetVariables, nativeEvaluate
   - Chaque fonction JNI crée une instance DebugBridge ou utilise un singleton
   - Convertit correctement les types JNI ↔ C++ (jstring → std::string, jintArray → std::vector<int>)
   - Le callback onDapEvent doit appeler la méthode Java DebugService.onDapEvent() via JNI CallVoidMethod

2. `cxxtoolchain_jni.cpp` — Ce fichier N'EXISTE PAS et doit être créé :
   **Chemin** : `core/modules/cxxtoolchain/src/cxxtoolchain_jni.cpp`
   - Expose les fonctions JNI pour PyStudioBuildBridgeModule :
     nativeConfigureBuild, nativeBuild, nativeClangFormat, nativeClangTidy,
     nativeGenerateCompileCommands, nativeInstallToolchain, nativeScaffoldProject
   - Chaque fonction crée un ToolchainManager et appelle les méthodes correspondantes
   - Les résultats string (logs, diagnostics) sont convertis en jstring via `env->NewStringUTF()`

3. Mets à jour les CMakeLists.txt de chaque module pour inclure les nouveaux fichiers JNI.

### INTERDIT :
Fonctions JNI vides, conversions de type incorrectes, fuites de références JNI.
