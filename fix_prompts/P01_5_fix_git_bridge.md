# 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)
## PROMPT 1.5 — Fix `PyStudioGitBridgeModule.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/bridge/PyStudioGitBridgeModule.kt`

**Problème** : Ce bridge est une coquille vide de 56 lignes. `getStatus` retourne `"main"` hardcodé, `commit` retourne un UUID aléatoire.

---

### EXIGENCES STRICTES :
1. Instancie GitRepositoryService, GitSyncService, GitMergeService (com.pystudio.core).
2. Implémente TOUTES les méthodes requises par S-13.5 :
   - `clone(options: ReadableMap, promise: Promise)`
     → Extrait url, destinationPath, username, token
     → Appelle GitRepositoryService.clone()
     → Retourne {repoId, success}
   - `getStatus(repoId: String, promise: Promise)`
     → Appelle GitRepositoryService.status(repoId)
     → Retourne {currentBranch, ahead, behind, modifiedFiles: [...], untrackedFiles: [...], stagedFiles: [...], conflictedFiles: [...]}
   - `stage(repoId: String, filePath: String, promise: Promise)`
     → Appelle GitRepositoryService.stageFile()
   - `unstage(repoId: String, filePath: String, promise: Promise)`
     → Appelle GitRepositoryService.unstageFile()
   - `commit(repoId: String, message: String, options: ReadableMap?, promise: Promise)`
     → Extrait authorName, authorEmail de options
     → Appelle GitRepositoryService.commit()
     → Retourne {success: Boolean}
   - `createBranch(repoId: String, name: String, promise: Promise)`
   - `checkoutBranch(repoId: String, name: String, promise: Promise)`
   - `deleteBranch(repoId: String, name: String, promise: Promise)`
   - `listBranches(repoId: String, promise: Promise)` → retourne un ReadableArray réel
   - `merge(repoId: String, sourceBranch: String, promise: Promise)`
     → Appelle GitMergeService.merge()
   - `push(repoId: String, options: ReadableMap?, promise: Promise)`
     → Extrait remoteName, username, token
     → Appelle GitSyncService.push()
   - `pull(repoId: String, options: ReadableMap?, promise: Promise)`
     → Appelle GitSyncService.pull()
   - `diff(repoId: String, filePath: String?, promise: Promise)`
     → Retourne le diff réel (à implémenter côté GitEngine si absent)
   - `log(repoId: String, maxCount: Int, promise: Promise)`
     → Retourne l'historique des commits (nécessite git_revwalk côté C++)
3. Émet "gitTransferProgress" via RCTDeviceEventEmitter pendant clone/push/pull.
4. Gère un `Map<String, GitRepositoryService>` pour supporter plusieurs repos ouverts.

### INTERDIT :
Retourner `"main"` hardcodé, `UUID.randomUUID()` comme commitId, `promise.resolve(Arguments.createMap())` vide.
