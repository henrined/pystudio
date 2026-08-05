# 🧪 PRIORITÉ 10 — Tests d'intégration et validation
## PROMPT 10.1 — Tests Google Test C++ pour les modules corrigés

**Fichiers** : `core/tests/`

---

### EXIGENCES STRICTES :
1. `test_dbgbridge.cpp` — NOUVEAU, à créer :
   - Test que Initialize() retourne true
   - Test que SetBreakpoints() retourne des breakpoints avec verified=true
   - Test que GetStackTrace() retourne des frames non-vides (au moins quand un processus est lancé en test)
   - Test que Evaluate("2+2") retourne une valeur
   - Test le cycle complet : Initialize → Launch → SetBreakpoint → Continue → Disconnect

2. `test_gitengine.cpp` — Compléter les tests existants :
   - Test Clone() dans un répertoire temporaire (utilise un repo file:// local)
   - Test le cycle complet : Open → StageFile → Commit → GetLog → vérifier le message
   - Test CreateBranch → CheckoutBranch → ListBranches → vérifier la branche
   - Test Merge de deux branches avec des modifications non-conflictuelles
   - Test Rebase (nouveau) si implémenté
   - Test GetDiff (nouveau) si implémenté
   - Cleanup : suppression du répertoire temporaire dans TearDown

3. `test_mlruntime.cpp` — Compléter :
   - Test ProcessImageOpenCV avec une vraie image de test (créer un BMP simple en mémoire)
   - Test que LoadTFLiteModel retourne false pour un fichier inexistant
   - Test conditionnel (#ifdef PYSTUDIO_HAS_TFLITE) pour les tests réels TFLite

4. Vérifie que TOUS les tests compilent : `cd core/build && ctest --output-on-failure`

### INTERDIT :
Tests qui passent sans rien vérifier, `ASSERT_TRUE(true)`, tests commentés.
