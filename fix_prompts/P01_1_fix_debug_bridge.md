# 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)
## PROMPT 1.1 — Fix `PyStudioDebugBridgeModule.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/bridge/PyStudioDebugBridgeModule.kt`

**Problème** : Ce bridge est une coquille vide de 22 lignes. Il ne connecte aucun service réel.

---

### EXIGENCES STRICTES :
1. Injecte ou instancie `DebugService` (com.pystudio.debug.DebugService) via AIDL ServiceConnection.
2. Implémente TOUTES les méthodes DAP requises par la spécification S-13.3 :
   - `launch(config: ReadableMap, promise: Promise)` → appelle DebugService.launchProgram()
   - `attach(config: ReadableMap, promise: Promise)` → appelle DebugService.attachToProcess()
   - `setBreakpoints(params: ReadableMap, promise: Promise)` → appelle DebugService.setBreakpoints(), retourne la liste des breakpoints vérifiés
   - `continue(threadId: Int, promise: Promise)` → appelle DebugService.continueExecution()
   - `stepOver(threadId: Int, promise: Promise)` → appelle DebugService.stepOver()
   - `stepInto(threadId: Int, promise: Promise)` → appelle DebugService.stepInto()
   - `stepOut(threadId: Int, promise: Promise)` → appelle DebugService.stepOut()
   - `pause(threadId: Int, promise: Promise)` → appelle DebugService.pauseExecution()
   - `disconnect(promise: Promise)` → appelle DebugService.disconnect()
   - `getStackTrace(threadId: Int, promise: Promise)` → appelle DebugService.getStackTrace(), parse le JSON et retourne un ReadableArray
   - `getVariables(variablesReference: Int, promise: Promise)` → appelle DebugService.getVariables(), parse le JSON et retourne un ReadableArray
   - `getScopes(frameId: Int, promise: Promise)` → appelle DebugService.getScopes(), parse le JSON et retourne un ReadableArray
   - `evaluate(expression: String, frameId: Int, promise: Promise)` → appelle DebugService.evaluate(), retourne un ReadableMap
3. Enregistre un IDebugCallback pour émettre les événements DAP vers JS via RCTDeviceEventEmitter :
   - "debugStopped", "debugExited", "debugOutput", "debugBreakpointHit"
4. Gère le cycle de vie du ServiceConnection (bind dans initialize, unbind dans onCatalystInstanceDestroy).
5. Toutes les opérations AIDL sont exécutées dans scope(Dispatchers.IO).
6. Gère les erreurs avec promise.reject() et des codes d'erreur explicites.

### INTERDIT :
`promise.resolve(null)`, `promise.resolve(Arguments.createArray())`, valeurs hardcodées, commentaires `// TODO`.
