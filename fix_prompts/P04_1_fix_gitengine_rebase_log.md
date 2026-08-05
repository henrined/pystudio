# ⚠️ PRIORITÉ 4 — S-6 : Git Engine (Rebase, historique, auth)
## PROMPT 4.1 — Ajouter rebase et historique à `gitengine.cpp`

**Fichier** : `core/modules/gitengine/src/gitengine.cpp`

**Problème** : Rebase (S-6.3.5) et historique de commits (S-6.3.7) ne sont pas implémentés.

---

### EXIGENCES STRICTES :
1. Ajoute `bool GitEngine::Rebase(const std::string& targetBranch)` :
   - Utilise `git_rebase_init()` avec la branche cible
   - Itère sur les opérations avec `git_rebase_next()`
   - Gère les conflits (retourne false avec un message si conflit non résolu)
   - Finalise avec `git_rebase_finish()`
   - Libère proprement le `git_rebase*` avec `git_rebase_free()`
2. Ajoute `std::vector<CommitInfo> GitEngine::GetLog(int maxCount)` :
   - Struct `CommitInfo { string oid, string author, string email, string message, int64_t timestamp }`
   - Utilise `git_revwalk_new()` / `git_revwalk_push_head()` / `git_revwalk_sorting(GIT_SORT_TIME)`
   - Itère avec `git_revwalk_next()` jusqu'à maxCount commits
   - Pour chaque OID, appelle `git_commit_lookup()` pour extraire author, message, timestamp
   - Libère proprement tous les objets `git_*`
3. Ajoute `std::string GitEngine::GetDiff(const std::string& filePath)` :
   - Utilise `git_diff_index_to_workdir()` pour un diff de l'index vs workdir
   - Filtre par filePath si non-vide
   - Formate avec `git_diff_print()` ou itère les hunks
4. Mets à jour `gitengine.h` avec les nouvelles déclarations.
5. Mets à jour `gitengine_jni.cpp` avec les fonctions JNI correspondantes.
6. Mets à jour `GitServices.kt` (GitRepositoryService et GitMergeService) avec les nouvelles méthodes native.

### INTERDIT :
Fonctions vides, stubs, TODO.
