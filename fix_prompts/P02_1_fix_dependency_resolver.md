# 🔴 PRIORITÉ 2 — S-8 : Package Manager (Majoritairement mocké)
## PROMPT 2.1 — Fix `DependencyResolverService.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/core/packages/DependencyResolverService.kt`

**Problème** : Ce fichier log `"Solving dependencies via PubGrub (Mocked)"` et retourne des `sha256 = "mock_hash_$name"`.

---

### EXIGENCES STRICTES :
1. Implémente un algorithme de résolution de dépendances réel. Deux approches acceptables :
   a) PubGrub simplifié : résolution SAT-based avec backtracking
   b) Résolution pip-compatible : résolution greedy depth-first avec détection de conflits
2. Pour chaque package demandé :
   - Interroge un index PyPI local ou distant pour obtenir les métadonnées (versions disponibles, dépendances)
   - Parse les version specifiers PEP 440 (>=, <=, ~=, ==, !=, compatible release)
   - Résout récursivement les dépendances transitives
   - Détecte et reporte les conflits de versions
3. Le résultat doit contenir :
   - name, version EXACTE résolue
   - sha256 RÉEL lu depuis l'index PyPI (ou calculé depuis le wheel téléchargé)
   - wheelTag correspondant à l'ABI cible (cp3xx-cpXxx-android_XX_arm64)
   - dependencies : liste des dépendances transitives résolues
   - signatureVerified : false par défaut (sera vérifié par SecurityGate)
4. Supporte le mode offline (résolution depuis le cache L5) ET online (requête vers pypi.org/simple/).
5. Gère les extras (package[extra]) et les markers d'environnement (sys_platform, python_version).

### INTERDIT :
`sha256 = "mock_hash_$name"`, version strips par regex simple, dépendances vides.
