# 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)
## PROMPT 1.6 — Fix `PyStudioBuildBridgeModule.kt`

**Fichier** : `android/app/src/main/java/com/pystudio/bridge/PyStudioBuildBridgeModule.kt`

**Problème** : Ce bridge de 65 lignes retourne `"success"` hardcodé pour toutes les méthodes sans connecter cxxtoolchain.

---

### EXIGENCES STRICTES :
1. Charge la bibliothèque native cxxtoolchain via `System.loadLibrary("cxxtoolchain")`.
2. Déclare les méthodes JNI native correspondant aux fonctions de cxxtoolchain.cpp :
   - `nativeConfigureBuild(projectPath: String, preset: String): Boolean`
   - `nativeBuild(projectPath: String, buildDir: String): String` (retourne le log)
   - `nativeClangFormat(filePath: String): Boolean`
   - `nativeClangTidy(filePath: String): String` (retourne les diagnostics)
   - `nativeGenerateCompileCommands(projectPath: String): Boolean`
   - `nativeInstallToolchain(archivePath: String, sha256: String, destPath: String): Boolean`
   - `nativeScaffoldProject(destPath: String, templateName: String): Boolean`
3. Implémente TOUTES les méthodes React requises par S-13.2 :
   - `startBuild(options: ReadableMap, promise: Promise)`
     → Extrait projectPath, preset, abi de options
     → Appelle nativeConfigureBuild() puis nativeBuild() dans un thread IO
     → Émet "buildLog" via RCTDeviceEventEmitter pour chaque ligne de sortie
     → Retourne {buildId, success, outputPath, errors: [...]}
   - `cancelBuild(buildId: String, promise: Promise)`
     → Tue le processus cmake/ninja en cours (via Process.destroy() ou signal)
   - `getBuildState(buildId: String, promise: Promise)`
     → Retourne l'état réel du build en cours ou terminé
   - `formatFile(filePath: String, promise: Promise)`
     → Appelle nativeClangFormat()
   - `lintFile(filePath: String, promise: Promise)`
     → Appelle nativeClangTidy(), retourne les diagnostics
   - `scaffoldProject(options: ReadableMap, promise: Promise)`
     → Appelle nativeScaffoldProject()
4. Gère un `Map<String, Process>` pour les builds en cours et leur annulation.

### INTERDIT :
Retourner `"success"` hardcodé, ignorer les erreurs de compilation, UUID seul comme résultat.
