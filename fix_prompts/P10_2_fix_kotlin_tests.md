# 🧪 PRIORITÉ 10 — Tests d'intégration et validation
## PROMPT 10.2 — Tests Kotlin unitaires pour les services corrigés

**Fichiers** : `android/app/src/test/java/com/pystudio/`

---

### EXIGENCES STRICTES :
1. `PackageManagerServiceTest.kt` — Complet (voir PROMPT 2.5)

2. `AIAssistantServiceTest.kt` — Compléter :
   - Test runAction() avec un ContextBuilder mocké (mock autorisé UNIQUEMENT dans les tests, pas dans le code de prod)
   - Test applyActionResult("accept") : vérifie que DiffApplicator est appelé et le fichier modifié
   - Test applyActionResult("reject") : vérifie que le fichier n'est pas modifié
   - Test le fallback cloud quand le modèle local échoue

3. `JupyterKernelServiceTest.kt` — Compléter :
   - Test executeAll() avec 3 cellules : vérifie que chaque cellule produit un résultat
   - Test listVariables() : exécute "x = 42" puis vérifie que "x" apparaît dans les variables
   - Test inspect("x") : vérifie type="int", repr="42"

4. `WorkspaceServiceTest.kt` — Vérifier qu'il est complet

5. `MarketplaceServiceTest.kt` — Vérifier qu'il est complet

6. Ajoute `LspServiceTest.kt` :
   - Test startServer() avec un serveur mock (process echo)
   - Test sendMessage() : vérifie le format JSON-RPC (Content-Length header)
   - Test readLoop() : injecte un message JSON-RPC et vérifie la callback

### INTERDIT :
Tests vides, tests commentés, `assertTrue(true)`, tests qui n'appellent aucune méthode du SUT.
