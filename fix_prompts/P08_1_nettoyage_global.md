# 🧹 PRIORITÉ 8 — NETTOYAGE FINAL
## PROMPT 8.1 — Nettoyage global des mocks résiduels

Exécute une recherche exhaustive dans tout le projet pour trouver et corriger :

1. `grep -rn "mock" --include="*.kt" --include="*.cpp" --include="*.h" android/ core/`
   → Chaque occurrence doit être remplacée par du vrai code ou supprimée
2. `grep -rn "TODO" --include="*.kt" --include="*.cpp" --include="*.h" android/ core/`
   → Chaque TODO doit être résolu ou documenté dans un issue tracker
3. `grep -rn "promise.resolve(null)" --include="*.kt" android/`
   → Chaque occurrence doit retourner une vraie valeur ou un objet significatif
4. `grep -rn "hardcoded\|hardcode" --include="*.kt" --include="*.cpp" android/ core/`
   → Remplacer par des valeurs dynamiques
5. `grep -rn "assertTrue(true)" --include="*.kt" android/`
   → Remplacer par de vrais tests

Mets à jour `implementation_tree.md` :
- Retire les marqueurs ⚠️ pour chaque module corrigé
- Ajoute la date de correction
