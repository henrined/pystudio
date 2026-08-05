# 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)
## PROMPT 1.7 — Fix `PyStudioRuntimeBridgeModule.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/bridge/PyStudioRuntimeBridgeModule.kt`

**Problème** : `stopExecution` est commenté, `poolStatus` retourne des valeurs hardcodées, `forceGcCollect` est vide.

---

### EXIGENCES STRICTES :
1. Connecte RunnerService (com.pystudio.runner.RunnerService) via AIDL ou Intent.
2. Connecte RunnerClient (com.pystudio.runner.RunnerClient) pour recevoir stdout/stderr.
3. Implémente TOUTES les méthodes requises par S-13.1 :
   - `run(scriptPath: String, options: ReadableMap?, promise: Promise)`
     → Extrait envId, pythonVersion, args de options
     → Crée un Intent avec extras (scriptPath, envId, etc.)
     → Démarre RunnerService
     → Enregistre un callback pour recevoir stdout/stderr
     → Émet "runtimeStdout" et "runtimeStderr" via RCTDeviceEventEmitter en temps réel
     → Retourne {sessionId, pid} dans la promise
   - `stopExecution(sessionId: String, promise: Promise)`
     → Envoie un signal SIGTERM/SIGKILL au process Python via RunnerService
     → Confirme l'arrêt dans la promise
   - `poolStatus(promise: Promise)`
     → Interroge réellement le nombre de process warm dans le pool de RunnerService
     → Retourne des valeurs réelles {warmProcesses, targetSize, memoryUsageMB}
   - `forceGcCollect(envId: String, promise: Promise)`
     → Envoie une commande au process Python pour exécuter gc.collect() via RunnerService
     → Retourne {collected: Int, uncollectable: Int}
   - `getRunningProcesses(promise: Promise)`
     → Liste les exécutions en cours avec PID, état, durée
4. Émet "runtimeExited" avec le code de sortie quand le process se termine.

### INTERDIT :
`promise.resolve(null)`, valeurs hardcodées dans poolStatus, fonctions vides.
