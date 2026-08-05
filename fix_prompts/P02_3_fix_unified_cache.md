# 🔴 PRIORITÉ 2 — S-8 : Package Manager (Majoritairement mocké)
## PROMPT 2.3 — Fix `UnifiedCacheService.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/core/packages/UnifiedCacheService.kt`

**Problème** : `checkL5Resolution()` retourne null inconditionnellement. `storeL5Resolution()` écrit `{"mock": true}`.

---

### EXIGENCES STRICTES :
1. Utilise kotlinx.serialization ou Gson pour sérialiser/désérialiser PystudioLock vers JSON.
2. `checkL5Resolution(tomlHash: String): PystudioLock?`
   - Lit le fichier `$tomlHash.json` dans l5CacheDir
   - Désérialise en PystudioLock
   - Vérifie que le fichier n'est pas expiré (TTL configurable, défaut 24h)
   - Retourne le PystudioLock si valide, null sinon
3. `storeL5Resolution(tomlHash: String, lockfile: PystudioLock)`
   - Sérialise le PystudioLock en JSON complet
   - Écrit dans `l5CacheDir/$tomlHash.json` avec un timestamp de création
4. Ajoute les méthodes de gestion du cache :
   - `clearL3Cache()` / `clearL5Cache()` / `clearAll()`
   - `getCacheSize(): Long` (taille totale en bytes)
   - `evictOldEntries(maxAgeDays: Int)` — supprime les entrées plus vieilles que N jours
5. `checkL3Wheel()` et `storeL3Wheel()` sont déjà corrects, les conserver.

### INTERDIT :
`return null` sans lire le fichier, écrire `{"mock": true}`, ignorer les erreurs de désérialisation.
