# 🔴 PRIORITÉ 2 — S-8 : Package Manager (Majoritairement mocké)
## PROMPT 2.2 — Fix `PackageInstallService.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/core/packages/PackageInstallService.kt`

**Problème** : `downloadOrBuildWheel()` crée un fichier texte `"mock wheel content"` et les erreurs pip sont explicitement ignorées.

---

### EXIGENCES STRICTES :
1. `downloadOrBuildWheel()` doit :
   a) Vérifier d'abord le cache L3 (déjà fait)
   b) Tenter de télécharger le .whl depuis PyPI : `https://pypi.org/simple/{package}/`
      - Parser le HTML de l'index simple pour trouver le wheel correspondant au wheelTag
      - Télécharger via HttpURLConnection avec timeout et retry (3 tentatives)
      - Calculer le SHA-256 du fichier téléchargé et le comparer à celui du lockfile
   c) Si aucun wheel n'est disponible pour l'ABI : tenter `pip wheel --no-deps --platform android_21_aarch64`
   d) Retourner null si le téléchargement ET le build échouent
2. `installWheel()` doit :
   - Extraire le contenu du .whl (qui est un ZIP) dans site-packages avec ZipInputStream
   - Créer le répertoire .dist-info avec METADATA et RECORD
   - NE PAS appeler `pip install` (trop lourd pour du offline), extraire directement le ZIP
   - Retourner false et un message d'erreur spécifique si l'extraction échoue
3. Gère les erreurs réseau avec des messages explicites dans `InstallOutcome.Failure`.
4. Émet des événements de progression pour le téléchargement (bytes downloaded / total).

### INTERDIT :
`writeText("mock wheel content")`, ignorer les codes de retour non-zero, `"(mock behavior ignored)"`.
