# ⚠️ PRIORITÉ 6 — S-11 : AI Service (applyActionResult vide, clé API mock)
## PROMPT 6.1 — Fix `AIAssistantServiceImpl.kt` et `AICloudClient.kt`

**Fichiers** :
- `android/app/src/main/java/com/pystudio/ai/AIAssistantServiceImpl.kt`
- `android/app/src/main/java/com/pystudio/ai/AICloudClient.kt`

**Problème** : `applyActionResult()` ne fait rien (L60-68). `getApiKey()` retourne `"mock-api-key"`.

---

### EXIGENCES STRICTES :
1. `AIAssistantServiceImpl.applyActionResult()` :
   - Si decision == "accept" :
     → Lit le fichier original via FileSystemService.readFile(filePath)
     → Applique le diff via DiffApplicator.applyDiff(originalText, generatedDiff)
     → Écrit le résultat via FileSystemService.writeFile(filePath, patchedContent)
   - Si decision == "edit" :
     → Applique le editedDiff fourni par l'utilisateur au lieu du diff original
     → Écrit le résultat
   - Si decision == "reject" :
     → Ne fait rien, nettoie l'action de activeActions
   - Émet un événement de progression "applied" ou "rejected"
2. `AICloudClient.getApiKey()` :
   - Lit la clé API chiffrée depuis SharedPreferences
   - Déchiffre avec la clé AES-GCM stockée dans Android Keystore (KEY_ALIAS)
   - Retourne null si aucune clé n'est configurée (au lieu de "mock-api-key")
3. Ajoute `AICloudClient.setApiKey(apiKey: String)` :
   - Chiffre la clé API avec AES-GCM via la clé Keystore
   - Stocke le ciphertext + IV dans SharedPreferences (Base64)
4. Ajoute la dépendance vers FileSystemService dans le constructeur de AIAssistantServiceImpl.
5. Stocke le diff généré dans activeActions (`Map<String, Pair<String, String>>` → actionId to (filePath, diff)).

### INTERDIT :
`"mock-api-key"`, `applyActionResult` vide, commentaire `"This is mocked for now"`.
