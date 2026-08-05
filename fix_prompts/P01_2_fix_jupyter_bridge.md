# 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)
## PROMPT 1.2 — Fix `PyStudioJupyterBridgeModule.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/bridge/PyStudioJupyterBridgeModule.kt`

**Problème** : Ce bridge est une coquille vide de 21 lignes qui retourne `{"status": "running"}` hardcodé.

---

### EXIGENCES STRICTES :
1. Instancie JupyterKernelService (com.pystudio.notebook.JupyterKernelService).
2. Implémente TOUTES les méthodes requises par S-13.6 :
   - `executeCell(notebookId: String, cellId: String, code: String, promise: Promise)`
     → Appelle JupyterKernelService.executeCell(), collecte le Flow<CellOutputEvent>, retourne un ReadableMap contenant {status, outputs: [{type, data, mimeType}], executionCount}
   - `interruptKernel(notebookId: String, promise: Promise)`
     → Appelle JupyterKernelService.interrupt()
   - `restartKernel(notebookId: String, promise: Promise)`
     → Détruit et recrée le kernel via JupyterKernelService
   - `getKernelStatus(notebookId: String, promise: Promise)`
     → Retourne {status: "idle"|"busy"|"starting"|"dead"}
   - `listVariables(notebookId: String, promise: Promise)`
     → Appelle JupyterKernelService.listVariables(), retourne un ReadableArray
   - `inspectVariable(notebookId: String, varName: String, promise: Promise)`
     → Appelle JupyterKernelService.inspect(), retourne un ReadableMap
3. Émet des événements temps réel vers JS via RCTDeviceEventEmitter pendant l'exécution :
   - "jupyterCellOutput" : stream des outputs au fur et à mesure
   - "jupyterKernelStatus" : changement de statut du kernel
4. Gère un `Map<String, JupyterKernelService>` pour supporter plusieurs notebooks simultanés.
5. Toutes les opérations asynchrones dans `CoroutineScope(Dispatchers.IO)`.

### INTERDIT :
Retourner des valeurs hardcodées, ignorer les erreurs, `promise.resolve(null)`.
