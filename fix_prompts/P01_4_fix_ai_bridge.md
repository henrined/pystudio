# 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)
## PROMPT 1.4 — Fix `PyStudioAIBridgeModule.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/bridge/PyStudioAIBridgeModule.kt`

**Problème** : Ce bridge est une coquille vide de 32 lignes. `sendMessage` retourne null.

---

### EXIGENCES STRICTES :
1. Instancie AIAssistantServiceImpl avec ses dépendances réelles (ContextBuilderServiceImpl, InferenceRuntimeGateway).
2. Implémente TOUTES les méthodes requises par S-13.7 :
   - `sendMessage(conversationId: String, message: String, contextParams: ReadableMap?, promise: Promise)`
     → Construit un AIActionRequest à partir du message et du contexte
     → Appelle AIAssistantServiceImpl.runAction()
     → Collecte le Flow actionProgress() et émet les événements "aiProgress" via RCTDeviceEventEmitter
     → Retourne {actionId, status} dans la promise
   - `applyPatch(actionId: String, decision: String, editedDiff: String?, promise: Promise)`
     → Appelle AIAssistantServiceImpl.applyActionResult()
     → Retourne {applied: Boolean, filePath: String}
   - `cancelRequest(actionId: String, promise: Promise)`
     → Annule l'action en cours (cancel le job coroutine correspondant)
   - `getConversationHistory(conversationId: String, promise: Promise)`
     → Retourne l'historique sous forme de ReadableArray
   - `setModel(modelConfig: ReadableMap, promise: Promise)`
     → Configure le modèle (local vs cloud, chemin du modèle GGUF, endpoint API)
3. Émet des événements streaming vers JS : "aiToken", "aiProgress", "aiError".
4. Gère un `Map<String, Job>` pour pouvoir annuler les requêtes en cours.

### INTERDIT :
`promise.resolve(null)`, `UUID.randomUUID()` comme seul retour, ignorer le contenu du message.
