# ⚠️ PRIORITÉ 4 — S-6 : Git Engine (Rebase, historique, auth)
## PROMPT 4.2 — Fix `GitAuthService` (Android Keystore)

**Fichier** : `android/app/src/main/java/com/pystudio/core/GitServices.kt` — class GitAuthService (L119-132)

**Problème** : Stocke les credentials dans un `mutableMapOf` en mémoire au lieu d'Android Keystore.

---

### EXIGENCES STRICTES :
1. Utilise Android Keystore pour générer une clé AES-256-GCM dédiée ("git_credentials_key").
2. `storeCredential(remoteUrl, token)`:
   - Chiffre le token avec AES-GCM via la clé Keystore
   - Stocke le ciphertext + IV dans SharedPreferences (encodé Base64)
   - Retourne l'alias unique
3. `getCredential(alias)`:
   - Lit le ciphertext + IV depuis SharedPreferences
   - Déchiffre avec la clé Keystore
   - Retourne le token en clair
4. `deleteCredential(alias)`:
   - Supprime l'entrée des SharedPreferences
5. `listStoredRemotes(): List<String>`
   - Liste les remotes qui ont des credentials stockés
6. Supporte aussi le stockage de clés SSH (lecture d'un fichier .pem, stockage chiffré).

### INTERDIT :
`mutableMapOf` en mémoire, credentials en clair dans SharedPreferences.
