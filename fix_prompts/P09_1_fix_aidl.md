# 🔧 PRIORITÉ 9 — AIDL, CMake et Infrastructure manquante
## PROMPT 9.1 — Vérification et complétion des fichiers AIDL

**Fichiers AIDL nécessaires pour les services Android inter-process :**
**Dossier attendu** : `android/app/src/main/aidl/com/pystudio/`

---

### EXIGENCES STRICTES :
1. Vérifie que les fichiers AIDL suivants existent et sont complets :
   a) `IRunnerService.aidl` :
      - `void executeScript(String scriptPath, String envId, in Bundle options)`
      - `void stopExecution(String sessionId)`
      - `void registerCallback(IRunnerCallback callback)`
   b) `IRunnerCallback.aidl` :
      - `void onStdout(String sessionId, String text)`
      - `void onStderr(String sessionId, String text)`
      - `void onExited(String sessionId, int exitCode)`
   c) `IDebugService.aidl` :
      - `boolean initialize(IDebugCallback callback)`
      - `boolean launchProgram(String programPath, in String[] args)`
      - `boolean attachToProcess(int pid)`
      - `String setBreakpoints(String file, in int[] lines)`
      - `boolean continueExecution()`
      - `boolean stepOver()`
      - `boolean stepInto()`
      - `boolean stepOut()`
      - `boolean pauseExecution()`
      - `boolean disconnect()`
      - `String getStackTrace(int threadId)`
      - `String getScopes(int frameId)`
      - `String getVariables(int variablesReference)`
      - `String evaluate(String expression, int frameId)`
   d) `IDebugCallback.aidl` :
      - `void onDapEvent(String event, String jsonPayload)`
   e) `ILspService.aidl` :
      - `boolean startServer(String language, String serverPath, String workspacePath, ILspCallback callback)`
      - `boolean sendMessage(String jsonRpcMessage)`
      - `void stopServer()`
   f) `ILspCallback.aidl` :
      - `void onMessage(String jsonRpcMessage)`
      - `void onError(String errorMessage)`
2. Chaque fichier AIDL doit être syntaxiquement correct et correspondre exactement aux Stub implémentés dans les services Kotlin.
3. Vérifie que le `build.gradle` inclut le sourceSets aidl.
4. Si des fichiers manquent, crée-les. Si des signatures ne correspondent pas, corrige-les.

### INTERDIT :
Fichiers AIDL vides, signatures qui ne matchent pas les implémentations.
