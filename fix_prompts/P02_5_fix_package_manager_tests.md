# 🔴 PRIORITÉ 2 — S-8 : Package Manager (Majoritairement mocké)
## PROMPT 2.5 — Fix `PackageManagerServiceTest.kt`

**Fichier** : `android/app/src/test/java/com/pystudio/core/packages/PackageManagerServiceTest.kt`

**Problème** : 100% commenté. Seule instruction : `assertTrue(true)`.

---

### EXIGENCES STRICTES :
1. Décommente et implémente TOUS les tests.
2. Utilise Robolectric pour simuler le contexte Android.
3. Tests requis (minimum) :
   - `testResolveSimpleDependency()` : résout "requests>=2.28" → vérifie version, hash non-mock
   - `testResolveConflictingDependencies()` : deux packages demandant des versions incompatibles → vérifie le message d'erreur
   - `testInstallPurePythonPackage()` : installe un wheel réel, vérifie que les fichiers sont extraits dans site-packages
   - `testInstallWithCacheHit()` : premier install → cache miss, deuxième install → cache hit
   - `testSecurityGateRejectsCorruptedWheel()` : modifie le contenu du wheel après hash → vérifie FAILED
   - `testUninstallPackage()` : installe puis désinstalle, vérifie que le répertoire est supprimé
   - `testEnvironmentCreation()` : crée un environnement, vérifie la structure de répertoires
4. Chaque test doit avoir des assertions explicites (pas de `assertTrue(true)`).
5. Utilise des fichiers wheel de test (créés dans `@Before`) pour les tests d'installation.

### INTERDIT :
`assertTrue(true)`, code commenté, tests vides.
