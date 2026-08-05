# 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)
## PROMPT 1.3 — Fix `PyStudioLSPBridgeModule.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/bridge/PyStudioLSPBridgeModule.kt`

**Problème** : Ce bridge est une coquille vide de 29 lignes qui retourne `{"success": true}` hardcodé.

---

### EXIGENCES STRICTES :
1. Connecte LspService (com.pystudio.lsp.LspService) via AIDL ServiceConnection.
2. Implémente TOUTES les méthodes requises par S-13.9 :
   - `initialize(options: ReadableMap, promise: Promise)`
     → Extrait language, serverPath, workspacePath de options
     → Appelle LspService.startServer()
     → Envoie le message JSON-RPC "initialize" avec les capabilities
     → Retourne les serverCapabilities reçues
   - `didOpen(params: ReadableMap, promise: Promise)`
     → Construit et envoie la notification textDocument/didOpen
   - `didChange(params: ReadableMap, promise: Promise)`
     → Construit et envoie la notification textDocument/didChange
   - `didClose(params: ReadableMap, promise: Promise)`
     → Construit et envoie la notification textDocument/didClose
   - `completion(params: ReadableMap, promise: Promise)`
     → Envoie la requête textDocument/completion, attend la réponse, retourne les items
   - `hover(params: ReadableMap, promise: Promise)`
     → Envoie la requête textDocument/hover, retourne le contenu
   - `definition(params: ReadableMap, promise: Promise)`
     → Envoie la requête textDocument/definition
   - `references(params: ReadableMap, promise: Promise)`
     → Envoie la requête textDocument/references
   - `shutdown(promise: Promise)`
     → Envoie "shutdown" puis "exit", appelle LspService.stopServer()
3. Enregistre un ILspCallback pour recevoir les messages JSON-RPC du serveur :
   - Parse les notifications (diagnostics) et les émet via RCTDeviceEventEmitter : "lspDiagnostics", "lspLogMessage"
   - Route les réponses aux requêtes vers les promises correspondantes (corrélation par id JSON-RPC)
4. Gère un compteur de seq pour les requêtes JSON-RPC.
5. Gère un `Map<Int, Promise>` pour corréler les réponses aux requêtes en attente.

### INTERDIT :
Retourner des objets vides, ignorer les réponses du serveur LSP, `promise.resolve(null)`.
