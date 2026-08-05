# 🔴 PRIORITÉ 2 — S-8 : Package Manager (Majoritairement mocké)
## PROMPT 2.4 — Fix `SecurityGateService.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/core/packages/SecurityGateService.kt`

**Problème** : 16 lignes. Retourne OK inconditionnellement sans aucune vérification cryptographique.

---

### EXIGENCES STRICTES :
1. Implémente la vérification SHA-256 du fichier wheel :
   - Calcule le hash SHA-256 du fichier via MessageDigest
   - Compare avec le hash attendu dans l'ArtifactRef
   - Retourne FAILED si les hash ne correspondent pas
2. Implémente la vérification de signature optionnelle :
   - Si l'artifact contient une signature (.asc ou inline), vérifie-la
   - Utilise java.security.Signature avec une clé publique de confiance stockée dans les assets
   - Si pas de signature et allowUnsignedLocal=false, retourne FAILED
3. Gère les cas :
   - Fichier local sans signature + allowUnsignedLocal=true → SKIPPED_LOCAL (vérifie quand même le SHA-256)
   - Fichier distant sans signature → FAILED
   - Fichier avec SHA-256 invalide → FAILED
   - Fichier avec signature invalide → FAILED
   - Fichier avec SHA-256 et signature valides → OK
4. Log le résultat de chaque vérification avec le nom du package et la raison du verdict.

### INTERDIT :
`return VerificationResult.OK` sans vérification, vérifier uniquement le chemin du fichier.
