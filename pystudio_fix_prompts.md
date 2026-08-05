# PyStudio — Prompts de Correction Server-Side

> **Contexte** : Audit du 2026-08-02. Chaque prompt ci-dessous est autonome et doit produire du code **complet, production-ready, sans mock, sans stub, sans TODO, sans placeholder, sans valeur hardcodée**.
> **Règle absolue** : Si un prompt te demande de corriger un fichier, tu dois livrer le fichier **entier** corrigé et fonctionnel. Aucun `// TODO`, aucun `promise.resolve(null)`, aucun retour hardcodé n'est acceptable.

---

## 📌 PRÉAMBULE — Contexte système à injecter AVANT chaque prompt

> **Copie-colle ce bloc au début de ta conversation avant d'envoyer n'importe quel prompt ci-dessous.**

```
Tu travailles sur PyStudio Mobile, un IDE Android natif inspiré de VS Code.
Stack backend : C++17/20 (NDK), Kotlin (Android), JNI, AIDL, React Native Bridges.
Racine du projet : ~/pystudio/

Structure clé :
- core/              → Code C++ natif (modules: pyembed, cxxtoolchain, dbgbridge, gitengine, mlruntime)
- core/include/      → Headers publics C++
- core/tests/        → Tests Google Test
- android/app/src/main/java/com/pystudio/
    bridge/          → React Native Bridge Modules (frontière backend↔frontend)
    core/            → Services Kotlin (Git, FS, Workspace, Packages)
    debug/           → DebugService + LldbServerService (AIDL)
    lsp/             → LspService + LspProtocol (AIDL)
    ai/              → AIAssistantService, ContextBuilder, DiffApplicator, CloudClient
    marketplace/     → 6 services (Registry, Marketplace, Lifecycle, Permission, Host, Update)
    notebook/        → JupyterKernelService
    runner/          → RunnerService + RunnerClient (Isolated Process)
- android/app/src/test/  → Tests Kotlin (JUnit + Robolectric)
- SRS_Blocks/        → Spécifications détaillées (01_Architecture.md à 15_Securite.md)
- scripts/           → Scripts de build, jupyter_adapter.py

Règles impératives :
1. Pas de mock, pas de stub, pas de TODO, pas de placeholder, pas de valeur hardcodée.
2. Chaque fichier doit être livré COMPLET et compilable.
3. Gère systématiquement les erreurs (try/catch, promise.reject avec codes explicites).
4. Toutes les opérations IO/réseau dans CoroutineScope(Dispatchers.IO).
5. Libère toutes les ressources natives (JNI DeleteLocalRef, git_*_free, close()).
6. Respecte la spec SRS correspondante (référencée dans chaque prompt).
7. Si tu modifies un .h, mets à jour le .cpp correspondant, et vice-versa.
8. Si tu ajoutes une méthode native Kotlin, ajoute le JNI extern "C" correspondant.
9. Après chaque correction, mets à jour implementation_tree.md (retire le ⚠️).
```

---

## 🔴 PRIORITÉ 1 — S-13 : Bridges JSI (Coquilles vides)

### PROMPT 1.1 — Fix `PyStudioDebugBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioDebugBridgeModule.kt

Ce bridge est une coquille vide de 22 lignes. Il ne connecte aucun service réel.

EXIGENCES STRICTES :
1. Injecte ou instancie `DebugService` (com.pystudio.debug.DebugService) via AIDL ServiceConnection.
2. Implémente TOUTES les méthodes DAP requises par la spécification S-13.3 :
   - launch(config: ReadableMap, promise: Promise) → appelle DebugService.launchProgram()
   - attach(config: ReadableMap, promise: Promise) → appelle DebugService.attachToProcess()
   - setBreakpoints(params: ReadableMap, promise: Promise) → appelle DebugService.setBreakpoints(), retourne la liste des breakpoints vérifiés
   - continue(threadId: Int, promise: Promise) → appelle DebugService.continueExecution()
   - stepOver(threadId: Int, promise: Promise) → appelle DebugService.stepOver()
   - stepInto(threadId: Int, promise: Promise) → appelle DebugService.stepInto()
   - stepOut(threadId: Int, promise: Promise) → appelle DebugService.stepOut()
   - pause(threadId: Int, promise: Promise) → appelle DebugService.pauseExecution()
   - disconnect(promise: Promise) → appelle DebugService.disconnect()
   - getStackTrace(threadId: Int, promise: Promise) → appelle DebugService.getStackTrace(), parse le JSON et retourne un ReadableArray
   - getVariables(variablesReference: Int, promise: Promise) → appelle DebugService.getVariables(), parse le JSON et retourne un ReadableArray
   - getScopes(frameId: Int, promise: Promise) → appelle DebugService.getScopes(), parse le JSON et retourne un ReadableArray
   - evaluate(expression: String, frameId: Int, promise: Promise) → appelle DebugService.evaluate(), retourne un ReadableMap
3. Enregistre un IDebugCallback pour émettre les événements DAP vers JS via RCTDeviceEventEmitter :
   - "debugStopped", "debugExited", "debugOutput", "debugBreakpointHit"
4. Gère le cycle de vie du ServiceConnection (bind dans initialize, unbind dans onCatalystInstanceDestroy).
5. Toutes les opérations AIDL sont exécutées dans scope(Dispatchers.IO).
6. Gère les erreurs avec promise.reject() et des codes d'erreur explicites.

INTERDIT : promise.resolve(null), promise.resolve(Arguments.createArray()), valeurs hardcodées, commentaires "// TODO".
```

### PROMPT 1.2 — Fix `PyStudioJupyterBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioJupyterBridgeModule.kt

Ce bridge est une coquille vide de 21 lignes qui retourne {"status": "running"} hardcodé.

EXIGENCES STRICTES :
1. Instancie JupyterKernelService (com.pystudio.notebook.JupyterKernelService).
2. Implémente TOUTES les méthodes requises par S-13.6 :
   - executeCell(notebookId: String, cellId: String, code: String, promise: Promise)
     → Appelle JupyterKernelService.executeCell(), collecte le Flow<CellOutputEvent>, retourne un ReadableMap contenant {status, outputs: [{type, data, mimeType}], executionCount}
   - interruptKernel(notebookId: String, promise: Promise)
     → Appelle JupyterKernelService.interrupt()
   - restartKernel(notebookId: String, promise: Promise)
     → Détruit et recrée le kernel via JupyterKernelService
   - getKernelStatus(notebookId: String, promise: Promise)
     → Retourne {status: "idle"|"busy"|"starting"|"dead"}
   - listVariables(notebookId: String, promise: Promise)
     → Appelle JupyterKernelService.listVariables(), retourne un ReadableArray
   - inspectVariable(notebookId: String, varName: String, promise: Promise)
     → Appelle JupyterKernelService.inspect(), retourne un ReadableMap
3. Émet des événements temps réel vers JS via RCTDeviceEventEmitter pendant l'exécution :
   - "jupyterCellOutput" : stream des outputs au fur et à mesure
   - "jupyterKernelStatus" : changement de statut du kernel
4. Gère un Map<String, JupyterKernelService> pour supporter plusieurs notebooks simultanés.
5. Toutes les opérations asynchrones dans CoroutineScope(Dispatchers.IO).

INTERDIT : Retourner des valeurs hardcodées, ignorer les erreurs, promise.resolve(null).
```

### PROMPT 1.3 — Fix `PyStudioLSPBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioLSPBridgeModule.kt

Ce bridge est une coquille vide de 29 lignes qui retourne {"success": true} hardcodé.

EXIGENCES STRICTES :
1. Connecte LspService (com.pystudio.lsp.LspService) via AIDL ServiceConnection.
2. Implémente TOUTES les méthodes requises par S-13.9 :
   - initialize(options: ReadableMap, promise: Promise)
     → Extrait language, serverPath, workspacePath de options
     → Appelle LspService.startServer()
     → Envoie le message JSON-RPC "initialize" avec les capabilities
     → Retourne les serverCapabilities reçues
   - didOpen(params: ReadableMap, promise: Promise)
     → Construit et envoie la notification textDocument/didOpen
   - didChange(params: ReadableMap, promise: Promise)
     → Construit et envoie la notification textDocument/didChange
   - didClose(params: ReadableMap, promise: Promise)
     → Construit et envoie la notification textDocument/didClose
   - completion(params: ReadableMap, promise: Promise)
     → Envoie la requête textDocument/completion, attend la réponse, retourne les items
   - hover(params: ReadableMap, promise: Promise)
     → Envoie la requête textDocument/hover, retourne le contenu
   - definition(params: ReadableMap, promise: Promise)
     → Envoie la requête textDocument/definition
   - references(params: ReadableMap, promise: Promise)
     → Envoie la requête textDocument/references
   - shutdown(promise: Promise)
     → Envoie "shutdown" puis "exit", appelle LspService.stopServer()
3. Enregistre un ILspCallback pour recevoir les messages JSON-RPC du serveur :
   - Parse les notifications (diagnostics) et les émet via RCTDeviceEventEmitter : "lspDiagnostics", "lspLogMessage"
   - Route les réponses aux requêtes vers les promises correspondantes (corrélation par id JSON-RPC)
4. Gère un compteur de seq pour les requêtes JSON-RPC.
5. Gère un Map<Int, Promise> pour corréler les réponses aux requêtes en attente.

INTERDIT : Retourner des objets vides, ignorer les réponses du serveur LSP, promise.resolve(null).
```

### PROMPT 1.4 — Fix `PyStudioAIBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioAIBridgeModule.kt

Ce bridge est une coquille vide de 32 lignes. sendMessage retourne null.

EXIGENCES STRICTES :
1. Instancie AIAssistantServiceImpl avec ses dépendances réelles (ContextBuilderServiceImpl, InferenceRuntimeGateway).
2. Implémente TOUTES les méthodes requises par S-13.7 :
   - sendMessage(conversationId: String, message: String, contextParams: ReadableMap?, promise: Promise)
     → Construit un AIActionRequest à partir du message et du contexte
     → Appelle AIAssistantServiceImpl.runAction()
     → Collecte le Flow actionProgress() et émet les événements "aiProgress" via RCTDeviceEventEmitter
     → Retourne {actionId, status} dans la promise
   - applyPatch(actionId: String, decision: String, editedDiff: String?, promise: Promise)
     → Appelle AIAssistantServiceImpl.applyActionResult()
     → Retourne {applied: Boolean, filePath: String}
   - cancelRequest(actionId: String, promise: Promise)
     → Annule l'action en cours (cancel le job coroutine correspondant)
   - getConversationHistory(conversationId: String, promise: Promise)
     → Retourne l'historique sous forme de ReadableArray
   - setModel(modelConfig: ReadableMap, promise: Promise)
     → Configure le modèle (local vs cloud, chemin du modèle GGUF, endpoint API)
3. Émet des événements streaming vers JS : "aiToken", "aiProgress", "aiError".
4. Gère un Map<String, Job> pour pouvoir annuler les requêtes en cours.

INTERDIT : promise.resolve(null), UUID.randomUUID() comme seul retour, ignorer le contenu du message.
```

### PROMPT 1.5 — Fix `PyStudioGitBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioGitBridgeModule.kt

Ce bridge est une coquille vide de 56 lignes. getStatus retourne "main" hardcodé, commit retourne un UUID aléatoire.

EXIGENCES STRICTES :
1. Instancie GitRepositoryService, GitSyncService, GitMergeService (com.pystudio.core).
2. Implémente TOUTES les méthodes requises par S-13.5 :
   - clone(options: ReadableMap, promise: Promise)
     → Extrait url, destinationPath, username, token
     → Appelle GitRepositoryService.clone()
     → Retourne {repoId, success}
   - getStatus(repoId: String, promise: Promise)
     → Appelle GitRepositoryService.status(repoId)
     → Retourne {currentBranch, ahead, behind, modifiedFiles: [...], untrackedFiles: [...], stagedFiles: [...], conflictedFiles: [...]}
   - stage(repoId: String, filePath: String, promise: Promise)
     → Appelle GitRepositoryService.stageFile()
   - unstage(repoId: String, filePath: String, promise: Promise)
     → Appelle GitRepositoryService.unstageFile()
   - commit(repoId: String, message: String, options: ReadableMap?, promise: Promise)
     → Extrait authorName, authorEmail de options
     → Appelle GitRepositoryService.commit()
     → Retourne {success: Boolean}
   - createBranch(repoId: String, name: String, promise: Promise)
   - checkoutBranch(repoId: String, name: String, promise: Promise)
   - deleteBranch(repoId: String, name: String, promise: Promise)
   - listBranches(repoId: String, promise: Promise) → retourne un ReadableArray réel
   - merge(repoId: String, sourceBranch: String, promise: Promise)
     → Appelle GitMergeService.merge()
   - push(repoId: String, options: ReadableMap?, promise: Promise)
     → Extrait remoteName, username, token
     → Appelle GitSyncService.push()
   - pull(repoId: String, options: ReadableMap?, promise: Promise)
     → Appelle GitSyncService.pull()
   - diff(repoId: String, filePath: String?, promise: Promise)
     → Retourne le diff réel (à implémenter côté GitEngine si absent)
   - log(repoId: String, maxCount: Int, promise: Promise)
     → Retourne l'historique des commits (nécessite git_revwalk côté C++)
3. Émet "gitTransferProgress" via RCTDeviceEventEmitter pendant clone/push/pull.
4. Gère un Map<String, GitRepositoryService> pour supporter plusieurs repos ouverts.

INTERDIT : Retourner "main" hardcodé, UUID.randomUUID() comme commitId, promise.resolve(Arguments.createMap()) vide.
```

### PROMPT 1.6 — Fix `PyStudioBuildBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioBuildBridgeModule.kt

Ce bridge de 65 lignes retourne "success" hardcodé pour toutes les méthodes sans connecter cxxtoolchain.

EXIGENCES STRICTES :
1. Charge la bibliothèque native cxxtoolchain via System.loadLibrary("cxxtoolchain").
2. Déclare les méthodes JNI native correspondant aux fonctions de cxxtoolchain.cpp :
   - nativeConfigureBuild(projectPath: String, preset: String): Boolean
   - nativeBuild(projectPath: String, buildDir: String): String (retourne le log)
   - nativeClangFormat(filePath: String): Boolean
   - nativeClangTidy(filePath: String): String (retourne les diagnostics)
   - nativeGenerateCompileCommands(projectPath: String): Boolean
   - nativeInstallToolchain(archivePath: String, sha256: String, destPath: String): Boolean
   - nativeScaffoldProject(destPath: String, templateName: String): Boolean
3. Implémente TOUTES les méthodes React requises par S-13.2 :
   - startBuild(options: ReadableMap, promise: Promise)
     → Extrait projectPath, preset, abi de options
     → Appelle nativeConfigureBuild() puis nativeBuild() dans un thread IO
     → Émet "buildLog" via RCTDeviceEventEmitter pour chaque ligne de sortie
     → Retourne {buildId, success, outputPath, errors: [...]}
   - cancelBuild(buildId: String, promise: Promise)
     → Tue le processus cmake/ninja en cours (via Process.destroy() ou signal)
   - getBuildState(buildId: String, promise: Promise)
     → Retourne l'état réel du build en cours ou terminé
   - formatFile(filePath: String, promise: Promise)
     → Appelle nativeClangFormat()
   - lintFile(filePath: String, promise: Promise)
     → Appelle nativeClangTidy(), retourne les diagnostics
   - scaffoldProject(options: ReadableMap, promise: Promise)
     → Appelle nativeScaffoldProject()
4. Gère un Map<String, Process> pour les builds en cours et leur annulation.

INTERDIT : Retourner "success" hardcodé, ignorer les erreurs de compilation, UUID seul comme résultat.
```

### PROMPT 1.7 — Fix `PyStudioRuntimeBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioRuntimeBridgeModule.kt

stopExecution est commenté, poolStatus retourne des valeurs hardcodées, forceGcCollect est vide.

EXIGENCES STRICTES :
1. Connecte RunnerService (com.pystudio.runner.RunnerService) via AIDL ou Intent.
2. Connecte RunnerClient (com.pystudio.runner.RunnerClient) pour recevoir stdout/stderr.
3. Implémente TOUTES les méthodes requises par S-13.1 :
   - run(scriptPath: String, options: ReadableMap?, promise: Promise)
     → Extrait envId, pythonVersion, args de options
     → Crée un Intent avec extras (scriptPath, envId, etc.)
     → Démarre RunnerService
     → Enregistre un callback pour recevoir stdout/stderr
     → Émet "runtimeStdout" et "runtimeStderr" via RCTDeviceEventEmitter en temps réel
     → Retourne {sessionId, pid} dans la promise
   - stopExecution(sessionId: String, promise: Promise)
     → Envoie un signal SIGTERM/SIGKILL au process Python via RunnerService
     → Confirme l'arrêt dans la promise
   - poolStatus(promise: Promise)
     → Interroge réellement le nombre de process warm dans le pool de RunnerService
     → Retourne des valeurs réelles {warmProcesses, targetSize, memoryUsageMB}
   - forceGcCollect(envId: String, promise: Promise)
     → Envoie une commande au process Python pour exécuter gc.collect() via RunnerService
     → Retourne {collected: Int, uncollectable: Int}
   - getRunningProcesses(promise: Promise)
     → Liste les exécutions en cours avec PID, état, durée
4. Émet "runtimeExited" avec le code de sortie quand le process se termine.

INTERDIT : promise.resolve(null), valeurs hardcodées dans poolStatus, fonctions vides.
```

### PROMPT 1.8 — Supprimer `PyStudioBridgeModule.kt`

```
Fichier : android/app/src/main/java/com/pystudio/bridge/PyStudioBridgeModule.kt

Ce fichier est un vestige 100% mock (readFile retourne "Mock content", askAI retourne "AI mock response").
Il est remplacé par les bridges spécialisés (FSBridge, AIBridge, etc.).

ACTION :
1. Supprime ce fichier entièrement.
2. Supprime PyStudioBridgePackage.kt qui ne référence que ce module obsolète.
3. Vérifie que PyStudioBridgesPackage.kt est le seul ReactPackage enregistré dans MainApplication.
```

---

## 🔴 PRIORITÉ 2 — S-8 : Package Manager (Majoritairement mocké)

### PROMPT 2.1 — Fix `DependencyResolverService.kt`

```
Fichier : android/app/src/main/java/com/pystudio/core/packages/DependencyResolverService.kt

Ce fichier log "Solving dependencies via PubGrub (Mocked)" et retourne des sha256 = "mock_hash_$name".

EXIGENCES STRICTES :
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

INTERDIT : sha256 = "mock_hash_$name", version strips par regex simple, dépendances vides.
```

### PROMPT 2.2 — Fix `PackageInstallService.kt`

```
Fichier : android/app/src/main/java/com/pystudio/core/packages/PackageInstallService.kt

downloadOrBuildWheel() crée un fichier texte "mock wheel content" et les erreurs pip sont explicitement ignorées.

EXIGENCES STRICTES :
1. downloadOrBuildWheel() doit :
   a) Vérifier d'abord le cache L3 (déjà fait)
   b) Tenter de télécharger le .whl depuis PyPI : https://pypi.org/simple/{package}/
     - Parser le HTML de l'index simple pour trouver le wheel correspondant au wheelTag
     - Télécharger via HttpURLConnection avec timeout et retry (3 tentatives)
     - Calculer le SHA-256 du fichier téléchargé et le comparer à celui du lockfile
   c) Si aucun wheel n'est disponible pour l'ABI : tenter pip wheel --no-deps --platform android_21_aarch64
   d) Retourner null si le téléchargement ET le build échouent
2. installWheel() doit :
   - Extraire le contenu du .whl (qui est un ZIP) dans site-packages avec ZipInputStream
   - Créer le répertoire .dist-info avec METADATA et RECORD
   - NE PAS appeler pip install (trop lourd pour du offline), extraire directement le ZIP
   - Retourner false et un message d'erreur spécifique si l'extraction échoue
3. Gère les erreurs réseau avec des messages explicites dans InstallOutcome.Failure.
4. Émet des événements de progression pour le téléchargement (bytes downloaded / total).

INTERDIT : writeText("mock wheel content"), ignorer les codes de retour non-zero, "(mock behavior ignored)".
```

### PROMPT 2.3 — Fix `UnifiedCacheService.kt`

```
Fichier : android/app/src/main/java/com/pystudio/core/packages/UnifiedCacheService.kt

checkL5Resolution() retourne null inconditionnellement. storeL5Resolution() écrit {"mock": true}.

EXIGENCES STRICTES :
1. Utilise kotlinx.serialization ou Gson pour sérialiser/désérialiser PystudioLock vers JSON.
2. checkL5Resolution(tomlHash: String): PystudioLock?
   - Lit le fichier $tomlHash.json dans l5CacheDir
   - Désérialise en PystudioLock
   - Vérifie que le fichier n'est pas expiré (TTL configurable, défaut 24h)
   - Retourne le PystudioLock si valide, null sinon
3. storeL5Resolution(tomlHash: String, lockfile: PystudioLock)
   - Sérialise le PystudioLock en JSON complet
   - Écrit dans l5CacheDir/$tomlHash.json avec un timestamp de création
4. Ajoute les méthodes de gestion du cache :
   - clearL3Cache() / clearL5Cache() / clearAll()
   - getCacheSize(): Long (taille totale en bytes)
   - evictOldEntries(maxAgeDays: Int) — supprime les entrées plus vieilles que N jours
5. checkL3Wheel() et storeL3Wheel() sont déjà corrects, les conserver.

INTERDIT : return null sans lire le fichier, écrire {"mock": true}, ignorer les erreurs de désérialisation.
```

### PROMPT 2.4 — Fix `SecurityGateService.kt`

```
Fichier : android/app/src/main/java/com/pystudio/core/packages/SecurityGateService.kt

16 lignes. Retourne OK inconditionnellement sans aucune vérification cryptographique.

EXIGENCES STRICTES :
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

INTERDIT : return VerificationResult.OK sans vérification, vérifier uniquement le chemin du fichier.
```

### PROMPT 2.5 — Fix `PackageManagerServiceTest.kt`

```
Fichier : android/app/src/test/java/com/pystudio/core/packages/PackageManagerServiceTest.kt

100% commenté. Seule instruction : assertTrue(true).

EXIGENCES STRICTES :
1. Décommente et implémente TOUS les tests.
2. Utilise Robolectric pour simuler le contexte Android.
3. Tests requis (minimum) :
   - testResolveSimpleDependency() : résout "requests>=2.28" → vérifie version, hash non-mock
   - testResolveConflictingDependencies() : deux packages demandant des versions incompatibles → vérifie le message d'erreur
   - testInstallPurePythonPackage() : installe un wheel réel, vérifie que les fichiers sont extraits dans site-packages
   - testInstallWithCacheHit() : premier install → cache miss, deuxième install → cache hit
   - testSecurityGateRejectsCorruptedWheel() : modifie le contenu du wheel après hash → vérifie FAILED
   - testUninstallPackage() : installe puis désinstalle, vérifie que le répertoire est supprimé
   - testEnvironmentCreation() : crée un environnement, vérifie la structure de répertoires
4. Chaque test doit avoir des assertions explicites (pas de assertTrue(true)).
5. Utilise des fichiers wheel de test (créés dans @Before) pour les tests d'installation.

INTERDIT : assertTrue(true), code commenté, tests vides.
```

---

## ⚠️ PRIORITÉ 3 — S-4 : Debugger C++ (Parsing LLDB mocké)

### PROMPT 3.1 — Fix parsing LLDB dans `dbgbridge.cpp`

```
Fichier : core/modules/dbgbridge/src/dbgbridge.cpp

GetStackTrace(), GetScopes(), GetVariables(), Evaluate() retournent des valeurs hardcodées au lieu de parser la sortie LLDB.

EXIGENCES STRICTES :
1. Remplace l'IPC synchrone par un mécanisme de requête/réponse asynchrone :
   - SendCommand() envoie la commande ET bloque (avec timeout 5s) en attendant la réponse complète
   - Le read_thread parse la sortie et stocke les résultats dans une file thread-safe (std::promise/std::future ou condition_variable)
2. GetStackTrace(threadId):
   - Envoie "bt" ou "thread backtrace" et parse la sortie LLDB :
     Format: "frame #N: 0xADDR module`function at file.cpp:line:col"
   - Utilise std::regex pour extraire id, name, source, line, column de chaque frame
   - Retourne le vecteur réel parsé
3. GetVariables(variablesReference):
   - Si variablesReference == 1000 (Locals) : envoie "frame variable" et parse
     Format: "(type) name = value"
   - Utilise std::regex pour extraire name, value, type
   - Pour les types composés (struct, class), attribue un variablesReference non-zéro pour permettre l'expansion
4. GetScopes(frameId):
   - Envoie "frame select frameId" puis retourne les scopes réels (Locals avec variablesReference unique)
5. Evaluate(expression, frameId):
   - Envoie "expr expression" et parse le résultat
   - Retourne la Variable avec le vrai type et la vraie valeur

INTERDIT : Retourner {{1, "main", "main.cpp", 10, 0}}, {{"x", "42", "int", 0}}, "evaluated_result".
```

---

## ⚠️ PRIORITÉ 4 — S-6 : Git Engine (Rebase, historique, auth)

### PROMPT 4.1 — Ajouter rebase et historique à `gitengine.cpp`

```
Fichier : core/modules/gitengine/src/gitengine.cpp

Rebase (S-6.3.5) et historique de commits (S-6.3.7) ne sont pas implémentés.

EXIGENCES STRICTES :
1. Ajoute bool GitEngine::Rebase(const std::string& targetBranch) :
   - Utilise git_rebase_init() avec la branche cible
   - Itère sur les opérations avec git_rebase_next()
   - Gère les conflits (retourne false avec un message si conflit non résolu)
   - Finalise avec git_rebase_finish()
   - Libère proprement le git_rebase* avec git_rebase_free()
2. Ajoute std::vector<CommitInfo> GitEngine::GetLog(int maxCount) :
   - Struct CommitInfo { string oid, string author, string email, string message, int64_t timestamp }
   - Utilise git_revwalk_new() / git_revwalk_push_head() / git_revwalk_sorting(GIT_SORT_TIME)
   - Itère avec git_revwalk_next() jusqu'à maxCount commits
   - Pour chaque OID, appelle git_commit_lookup() pour extraire author, message, timestamp
   - Libère proprement tous les objets git_*
3. Ajoute std::string GitEngine::GetDiff(const std::string& filePath) :
   - Utilise git_diff_index_to_workdir() pour un diff de l'index vs workdir
   - Filtre par filePath si non-vide
   - Formate avec git_diff_print() ou itère les hunks
4. Mets à jour gitengine.h avec les nouvelles déclarations.
5. Mets à jour gitengine_jni.cpp avec les fonctions JNI correspondantes.
6. Mets à jour GitServices.kt (GitRepositoryService et GitMergeService) avec les nouvelles méthodes native.

INTERDIT : Fonctions vides, stubs, TODO.
```

### PROMPT 4.2 — Fix `GitAuthService` (Android Keystore)

```
Fichier : android/app/src/main/java/com/pystudio/core/GitServices.kt — class GitAuthService (L119-132)

Stocke les credentials dans un mutableMapOf en mémoire au lieu d'Android Keystore.

EXIGENCES STRICTES :
1. Utilise Android Keystore pour générer une clé AES-256-GCM dédiée ("git_credentials_key").
2. storeCredential(remoteUrl, token):
   - Chiffre le token avec AES-GCM via la clé Keystore
   - Stocke le ciphertext + IV dans SharedPreferences (encodé Base64)
   - Retourne l'alias unique
3. getCredential(alias):
   - Lit le ciphertext + IV depuis SharedPreferences
   - Déchiffre avec la clé Keystore
   - Retourne le token en clair
4. deleteCredential(alias):
   - Supprime l'entrée des SharedPreferences
5. listStoredRemotes(): List<String>
   - Liste les remotes qui ont des credentials stockés
6. Supporte aussi le stockage de clés SSH (lecture d'un fichier .pem, stockage chiffré).

INTERDIT : mutableMapOf en mémoire, credentials en clair dans SharedPreferences.
```

---

## ⚠️ PRIORITÉ 5 — S-9 : ML Runtime (Stubs de dépendances)

### PROMPT 5.1 — Rendre `mlruntime.cpp` production-ready

```
Fichiers :
- core/modules/mlruntime/src/mlruntime.cpp
- core/modules/mlruntime/src/deps_stubs.cpp
- core/modules/mlruntime/include/mlruntime.h

deps_stubs.cpp fournit des implémentations factices (TfLiteModelCreateFromFile retourne (TfLiteModel*)1).
mlruntime.cpp hardcode outFloats = 2 et utilise une API LibTorch mockée.

EXIGENCES STRICTES :
1. Stratégie de compilation conditionnelle :
   - Ajoute des guards CMake et preprocessor : #ifdef PYSTUDIO_HAS_TFLITE, #ifdef PYSTUDIO_HAS_OPENCV, #ifdef PYSTUDIO_HAS_LIBTORCH
   - Si la dépendance réelle est disponible, linke contre elle
   - Si elle n'est pas disponible, désactive la fonctionnalité proprement (retourne une erreur "TFLite not available" au lieu de simuler le succès)
   - Supprime deps_stubs.cpp du build par défaut (ne le garder QUE comme option de build pour les tests unitaires)
2. RunTFLiteInference() :
   - Récupère la taille de sortie dynamiquement via TfLiteTensorByteSize(outputTensor) / sizeof(float)
   - Ne hardcode PAS outFloats = 2
3. RunTorchInference() :
   - Utilise la vraie API LibTorch : torch::from_blob() pour créer le tensor d'entrée
   - Appelle module.forward({input_tensor}).toTensor()
   - Convertit le tensor de sortie en std::vector<float>
4. Mets à jour le CMakeLists.txt du module :
   - Option PYSTUDIO_USE_TFLITE=ON/OFF
   - Option PYSTUDIO_USE_OPENCV=ON/OFF
   - Option PYSTUDIO_USE_LIBTORCH=ON/OFF
   - find_package() conditionnel pour chaque dépendance
5. Les tests doivent aussi être conditionnels (#ifdef).

INTERDIT : (TfLiteModel*)1, Mat(100,100,0), outFloats = 2, forward(vector<float>).
```

---

## ⚠️ PRIORITÉ 6 — S-11 : AI Service (applyActionResult vide, clé API mock)

### PROMPT 6.1 — Fix `AIAssistantServiceImpl.kt` et `AICloudClient.kt`

```
Fichiers :
- android/app/src/main/java/com/pystudio/ai/AIAssistantServiceImpl.kt
- android/app/src/main/java/com/pystudio/ai/AICloudClient.kt

applyActionResult() ne fait rien (L60-68). getApiKey() retourne "mock-api-key".

EXIGENCES STRICTES :
1. AIAssistantServiceImpl.applyActionResult() :
   - Si decision == "accept" :
     → Lit le fichier original via FileSystemService.readFile(filePath)
     → Applique le diff via DiffApplicator.applyDiff(originalText, generatedDiff)
     → Écrit le résultat via FileSystemService.writeFile(filePath, patchedContent)
   - Si decision == "edit" :
     → Applique le editedDiff fourni par l'utilisateur au lieu du diff original
     → Écrit le résultat
   - Si decision == "reject" :
     → Ne fait rien, nettoie l'action de activeActions
   - Émet un événement de progression "applied" ou "rejected"
2. AICloudClient.getApiKey() :
   - Lit la clé API chiffrée depuis SharedPreferences
   - Déchiffre avec la clé AES-GCM stockée dans Android Keystore (KEY_ALIAS)
   - Retourne null si aucune clé n'est configurée (au lieu de "mock-api-key")
3. Ajoute AICloudClient.setApiKey(apiKey: String) :
   - Chiffre la clé API avec AES-GCM via la clé Keystore
   - Stocke le ciphertext + IV dans SharedPreferences (Base64)
4. Ajoute la dépendance vers FileSystemService dans le constructeur de AIAssistantServiceImpl.
5. Stocke le diff généré dans activeActions (Map<String, Pair<String, String>> → actionId to (filePath, diff)).

INTERDIT : "mock-api-key", applyActionResult vide, commentaire "This is mocked for now".
```

---

## ⚠️ PRIORITÉ 7 — S-10 : Jupyter (Méthodes stubées)

### PROMPT 7.1 — Fix les méthodes stubées de `JupyterKernelService.kt`

```
Fichier : android/app/src/main/java/com/pystudio/notebook/JupyterKernelService.kt

executeAll() retourne emptyList(), listVariables() retourne emptyList() avec "// MOCK for now", inspect() retourne un placeholder.

EXIGENCES STRICTES :
1. executeAll(cells: List<NotebookCell>): List<CellResult>
   - Itère sur chaque cellule dans l'ordre
   - Appelle executeCell() pour chaque cellule de type "code"
   - Collecte les résultats (Flow<CellOutputEvent>) et les agrège en CellResult
   - Gère les erreurs : si une cellule échoue et stopOnError=true, arrête l'exécution
   - Retourne la liste complète des résultats
2. listVariables(): List<VariableSummary>
   - Envoie au kernel Python la commande : "%who_ls" ou un script d'introspection
     (ex: "import json; json.dumps([{'name': k, 'type': type(v).__name__, 'size': sys.getsizeof(v)} for k,v in globals().items() if not k.startswith('_')])")
   - Parse la sortie JSON
   - Retourne une liste de VariableSummary(name, type, sizeBytes)
3. inspect(varName: String): VariableDetail
   - Envoie au kernel : un script d'introspection qui retourne repr(), type(), dir(), et doc()
   - Parse la sortie JSON
   - Retourne VariableDetail(name, type, repr, attributes, docstring)
4. Toutes les commandes d'introspection utilisent le même mécanisme IPC (__PYSTUDIO_JUPYTER__:) que executeCell().

INTERDIT : emptyList(), retours placeholder, commentaires "// MOCK for now".
```

---

## 🧹 PRIORITÉ 8 — NETTOYAGE FINAL

### PROMPT 8.1 — Nettoyage global des mocks résiduels

```
Exécute une recherche exhaustive dans tout le projet pour trouver et corriger :

1. grep -rn "mock" --include="*.kt" --include="*.cpp" --include="*.h" android/ core/
   → Chaque occurrence doit être remplacée par du vrai code ou supprimée
2. grep -rn "TODO" --include="*.kt" --include="*.cpp" --include="*.h" android/ core/
   → Chaque TODO doit être résolu ou documenté dans un issue tracker
3. grep -rn "promise.resolve(null)" --include="*.kt" android/
   → Chaque occurrence doit retourner une vraie valeur ou un objet significatif
4. grep -rn "hardcoded\|hardcode" --include="*.kt" --include="*.cpp" android/ core/
   → Remplacer par des valeurs dynamiques
5. grep -rn "assertTrue(true)" --include="*.kt" android/
   → Remplacer par de vrais tests

Mets à jour implementation_tree.md :
- Retire les marqueurs ⚠️ pour chaque module corrigé
- Ajoute la date de correction
```

---

## 🔧 PRIORITÉ 9 — AIDL, CMake et Infrastructure manquante

### PROMPT 9.1 — Vérification et complétion des fichiers AIDL

```
Fichiers AIDL nécessaires pour les services Android inter-process :
Dossier attendu : android/app/src/main/aidl/com/pystudio/

EXIGENCES STRICTES :
1. Vérifie que les fichiers AIDL suivants existent et sont complets :
   a) IRunnerService.aidl :
      - void executeScript(String scriptPath, String envId, in Bundle options)
      - void stopExecution(String sessionId)
      - void registerCallback(IRunnerCallback callback)
   b) IRunnerCallback.aidl :
      - void onStdout(String sessionId, String text)
      - void onStderr(String sessionId, String text)
      - void onExited(String sessionId, int exitCode)
   c) IDebugService.aidl :
      - boolean initialize(IDebugCallback callback)
      - boolean launchProgram(String programPath, in String[] args)
      - boolean attachToProcess(int pid)
      - String setBreakpoints(String file, in int[] lines)
      - boolean continueExecution()
      - boolean stepOver()
      - boolean stepInto()
      - boolean stepOut()
      - boolean pauseExecution()
      - boolean disconnect()
      - String getStackTrace(int threadId)
      - String getScopes(int frameId)
      - String getVariables(int variablesReference)
      - String evaluate(String expression, int frameId)
   d) IDebugCallback.aidl :
      - void onDapEvent(String event, String jsonPayload)
   e) ILspService.aidl :
      - boolean startServer(String language, String serverPath, String workspacePath, ILspCallback callback)
      - boolean sendMessage(String jsonRpcMessage)
      - void stopServer()
   f) ILspCallback.aidl :
      - void onMessage(String jsonRpcMessage)
      - void onError(String errorMessage)
2. Chaque fichier AIDL doit être syntaxiquement correct et correspondre exactement aux Stub implémentés dans les services Kotlin.
3. Vérifie que le build.gradle inclut le sourceSets aidl.
4. Si des fichiers manquent, crée-les. Si des signatures ne correspondent pas, corrige-les.

INTERDIT : Fichiers AIDL vides, signatures qui ne matchent pas les implémentations.
```

### PROMPT 9.2 — Vérification et complétion du build CMake natif

```
Fichier principal : core/CMakeLists.txt
Fichiers modules : core/modules/*/CMakeLists.txt

EXIGENCES STRICTES :
1. Vérifie que le CMakeLists.txt racine :
   - Définit le projet "pystudio_core" avec C++20
   - Inclut TOUS les modules : pyembed, cxxtoolchain, dbgbridge, gitengine, mlruntime
   - Configure les ABI filters : arm64-v8a, armeabi-v7a, x86_64
   - Link contre android log (-llog)
   - Active ASan en mode Debug
   - Configure FetchContent pour Google Test
   - Génère les cibles de test via enable_testing() + add_test()
   - Installe les .so dans les bons répertoires pour le packaging Android

2. Vérifie chaque CMakeLists.txt de module :
   a) pyembed/CMakeLists.txt :
      - Link libpython3.14.so (ou la version active)
      - Produit libpyembed.so ET librunner_jni.so
   b) cxxtoolchain/CMakeLists.txt :
      - Produit libcxxtoolchain.so
      - Link pystudio_core
   c) dbgbridge/CMakeLists.txt :
      - Produit libdbgbridge.so ET libdbgbridge_jni.so (pour DebugService)
      - Link pystudio_core, pthread
   d) gitengine/CMakeLists.txt :
      - Link libgit2 (find_package ou FetchContent)
      - Produit libgitengine.so (incluant gitengine_jni.cpp)
   e) mlruntime/CMakeLists.txt :
      - Options conditionnelles PYSTUDIO_USE_TFLITE, PYSTUDIO_USE_OPENCV, PYSTUDIO_USE_LIBTORCH
      - NE link PAS deps_stubs.cpp par défaut en production
      - find_package conditionnel pour chaque dépendance

3. Vérifie que le build.gradle Android configure externalNativeBuild avec le CMakeLists.txt racine.

4. Tente une compilation dry-run : cmake -B core/build -S core/ && cmake --build core/build
   Corrige chaque erreur de compilation.

INTERDIT : Modules manquants dans le CMake, libraries non linkées, erreurs de compilation ignorées.
```

### PROMPT 9.3 — Créer les JNI manquants pour les bridges

```
Les bridges React Native (S-13) appellent des services Kotlin qui à leur tour appellent du C++ natif via JNI.
Certaines couches JNI manquent.

EXIGENCES STRICTES :
1. dbgbridge_jni.cpp — Vérifie qu'il existe dans core/modules/dbgbridge/src/ :
   - Doit exposer toutes les méthodes native déclarées dans DebugService.kt :
     nativeInitialize, nativeLaunch, nativeAttach, nativeSetBreakpoints,
     nativeContinue, nativeStepOver, nativeStepInto, nativeStepOut,
     nativePause, nativeDisconnect, nativeGetStackTrace, nativeGetScopes,
     nativeGetVariables, nativeEvaluate
   - Chaque fonction JNI crée une instance DebugBridge ou utilise un singleton
   - Convertit correctement les types JNI ↔ C++ (jstring → std::string, jintArray → std::vector<int>)
   - Le callback onDapEvent doit appeler la méthode Java DebugService.onDapEvent() via JNI CallVoidMethod

2. cxxtoolchain_jni.cpp — Ce fichier N'EXISTE PAS et doit être créé :
   Chemin : core/modules/cxxtoolchain/src/cxxtoolchain_jni.cpp
   - Expose les fonctions JNI pour PyStudioBuildBridgeModule :
     nativeConfigureBuild, nativeBuild, nativeClangFormat, nativeClangTidy,
     nativeGenerateCompileCommands, nativeInstallToolchain, nativeScaffoldProject
   - Chaque fonction crée un ToolchainManager et appelle les méthodes correspondantes
   - Les résultats string (logs, diagnostics) sont convertis en jstring via env->NewStringUTF()

3. Mets à jour les CMakeLists.txt de chaque module pour inclure les nouveaux fichiers JNI.

INTERDIT : Fonctions JNI vides, conversions de type incorrectes, fuites de références JNI.
```

---

## 🧪 PRIORITÉ 10 — Tests d'intégration et validation

### PROMPT 10.1 — Tests Google Test C++ pour les modules corrigés

```
Fichiers : core/tests/

EXIGENCES STRICTES :
1. test_dbgbridge.cpp — NOUVEAU, à créer :
   - Test que Initialize() retourne true
   - Test que SetBreakpoints() retourne des breakpoints avec verified=true
   - Test que GetStackTrace() retourne des frames non-vides (au moins quand un processus est lancé en test)
   - Test que Evaluate("2+2") retourne une valeur
   - Test le cycle complet : Initialize → Launch → SetBreakpoint → Continue → Disconnect

2. test_gitengine.cpp — Compléter les tests existants :
   - Test Clone() dans un répertoire temporaire (utilise un repo file:// local)
   - Test le cycle complet : Open → StageFile → Commit → GetLog → vérifier le message
   - Test CreateBranch → CheckoutBranch → ListBranches → vérifier la branche
   - Test Merge de deux branches avec des modifications non-conflictuelles
   - Test Rebase (nouveau) si implémenté
   - Test GetDiff (nouveau) si implémenté
   - Cleanup : suppression du répertoire temporaire dans TearDown

3. test_mlruntime.cpp — Compléter :
   - Test ProcessImageOpenCV avec une vraie image de test (créer un BMP simple en mémoire)
   - Test que LoadTFLiteModel retourne false pour un fichier inexistant
   - Test conditionnel (#ifdef PYSTUDIO_HAS_TFLITE) pour les tests réels TFLite

4. Vérifie que TOUS les tests compilent : cd core/build && ctest --output-on-failure

INTERDIT : Tests qui passent sans rien vérifier, ASSERT_TRUE(true), tests commentés.
```

### PROMPT 10.2 — Tests Kotlin unitaires pour les services corrigés

```
Fichiers : android/app/src/test/java/com/pystudio/

EXIGENCES STRICTES :
1. PackageManagerServiceTest.kt — Complet (voir PROMPT 2.5)

2. AIAssistantServiceTest.kt — Compléter :
   - Test runAction() avec un ContextBuilder mocké (mock autorisé UNIQUEMENT dans les tests, pas dans le code de prod)
   - Test applyActionResult("accept") : vérifie que DiffApplicator est appelé et le fichier modifié
   - Test applyActionResult("reject") : vérifie que le fichier n'est pas modifié
   - Test le fallback cloud quand le modèle local échoue

3. JupyterKernelServiceTest.kt — Compléter :
   - Test executeAll() avec 3 cellules : vérifie que chaque cellule produit un résultat
   - Test listVariables() : exécute "x = 42" puis vérifie que "x" apparaît dans les variables
   - Test inspect("x") : vérifie type="int", repr="42"

4. WorkspaceServiceTest.kt — Vérifier qu'il est complet

5. MarketplaceServiceTest.kt — Vérifier qu'il est complet

6. Ajoute LspServiceTest.kt :
   - Test startServer() avec un serveur mock (process echo)
   - Test sendMessage() : vérifie le format JSON-RPC (Content-Length header)
   - Test readLoop() : injecte un message JSON-RPC et vérifie la callback

INTERDIT : Tests vides, tests commentés, assertTrue(true), tests qui n'appellent aucune méthode du SUT.
```

### PROMPT 10.3 — Tests d'intégration end-to-end

```
Crée un nouveau fichier de test d'intégration :
Fichier : android/app/src/test/java/com/pystudio/integration/EndToEndServerTest.kt

EXIGENCES STRICTES :
Ce test vérifie que la chaîne complète fonctionne de bout en bout :

1. Test "Python Run" :
   - Crée un fichier Python temporaire contenant "print('hello')"
   - Appelle PyStudioRuntimeBridgeModule.run() via le service
   - Vérifie que stdout contient "hello"
   - Vérifie que le process se termine avec exitCode 0

2. Test "File Lifecycle" :
   - Crée un fichier via FileSystemService.writeFile()
   - Lit le fichier via FileSystemService.readFile() → vérifie le contenu
   - Watch le répertoire, modifie le fichier, vérifie l'événement FileObserver

3. Test "Git Workflow" :
   - Initialise un repo git dans un répertoire temporaire
   - Crée un fichier, stage, commit
   - Vérifie que getStatus() retourne une liste vide de modifiedFiles
   - Vérifie que getLog() retourne 1 commit avec le bon message

4. Test "Workspace Persistence" :
   - Crée un workspace via WorkspaceService
   - Ajoute des fichiers, indexe
   - Sauve l'état de session (onglets, curseur)
   - Ferme et rouvre le workspace
   - Vérifie que l'état de session est restauré

5. Test "Package Resolution" :
   - Crée un PystudioToml avec une dépendance simple
   - Appelle DependencyResolverService.resolve()
   - Vérifie que le résultat contient un hash SHA-256 valide (64 caractères hex, pas "mock_hash_*")

Ces tests utilisent Robolectric et peuvent mocker les couches JNI si le NDK n'est pas disponible.
Mais les couches Kotlin pures doivent être testées avec du VRAI code, pas des stubs.

INTERDIT : Tests qui passent sans exécuter le code réel, assertions triviales.
```

---

## ✅ PRIORITÉ 11 — Validation finale et checklist

### PROMPT 11.1 — Audit de conformité post-correction

```
Après avoir exécuté TOUS les prompts ci-dessus, effectue la vérification finale :

CHECKLIST DE VALIDATION :

□ S-1 Core Infrastructure
  □ CMakeLists.txt compile sans erreur
  □ service_registry init/shutdown fonctionne
  □ Logger route vers logcat ET stderr

□ S-2 Pyembed
  □ RunString("print('ok')") capture "ok" sur stdout
  □ RunFile exécute un .py et capture la sortie
  □ runner_jni.cpp compile et link correctement

□ S-3 Cxxtoolchain
  □ ScaffoldProject crée un CMakeLists.txt valide
  □ ConfigureAndBuild compile un Hello World
  □ ClangFormat et ClangTidy fonctionnent
  □ cxxtoolchain_jni.cpp existe et compile

□ S-4 Debugger
  □ GetStackTrace() parse la vraie sortie LLDB
  □ GetVariables() parse les variables réelles
  □ DebugService.kt connecte C++ ET Python (debugpy)
  □ dbgbridge_jni.cpp existe et compile

□ S-5 LSP
  □ LspService démarre pylsp et clangd
  □ LspProtocol encode/décode les messages JSON-RPC
  □ LSPBridge connecte réellement LspService

□ S-6 Git
  □ gitengine.cpp compile contre libgit2
  □ Clone, Commit, Push, Pull, Merge, Rebase fonctionnent
  □ GetLog retourne l'historique réel
  □ GetDiff retourne le diff réel
  □ GitAuthService utilise Android Keystore
  □ GitBridge connecte réellement GitRepositoryService

□ S-7 Workspace/FS
  □ SQLite persistence fonctionne
  □ FileObserver émet des événements
  □ FSBridge est déjà connecté ✅

□ S-8 Packages
  □ DependencyResolver résout les dépendances réellement (pas de "mock_hash")
  □ PackageInstallService télécharge les vrais wheels
  □ UnifiedCacheService sérialise/désérialise le lockfile
  □ SecurityGate vérifie les hash SHA-256
  □ Tests non-commentés et fonctionnels

□ S-9 ML Runtime
  □ Compilation conditionnelle (#ifdef) pour TFLite/OpenCV/LibTorch
  □ deps_stubs.cpp n'est PAS linké en production
  □ RunTFLiteInference utilise la taille de sortie dynamique
  □ RunTorchInference utilise la vraie API torch::jit::IValue

□ S-10 Jupyter
  □ executeAll() itère les cellules et collecte les résultats
  □ listVariables() exécute un script d'introspection Python
  □ inspect() retourne les détails réels
  □ JupyterBridge connecte réellement JupyterKernelService

□ S-11 AI
  □ applyActionResult() applique réellement le diff via DiffApplicator
  □ getApiKey() lit le Keystore Android (pas "mock-api-key")
  □ AIBridge connecte réellement AIAssistantServiceImpl

□ S-12 Marketplace
  □ Déjà production-ready ✅
  □ MarketplaceBridge.install() connecte réellement le service (à vérifier)

□ S-13 Bridges
  □ AUCUN bridge ne contient promise.resolve(null) sans logique
  □ CHAQUE bridge connecte son service Kotlin correspondant
  □ CHAQUE bridge émet des événements temps réel via RCTDeviceEventEmitter
  - [x] PyStudioBridgeModule.kt (l'ancien mock) est SUPPRIMÉ
  - [x] PyStudioBridgePackage.kt est SUPPRIMÉ

□ Infrastructure
  □ Tous les fichiers AIDL existent et correspondent aux implémentations
  □ CMake compile tous les modules natifs sans erreur
  □ Tous les tests Google Test passent (ctest)
  □ Tous les tests Kotlin passent (./gradlew test)

□ Nettoyage
  □ grep -rn "mock" → 0 résultat dans le code de production
  □ grep -rn "TODO" → 0 résultat critique
  □ grep -rn "promise.resolve(null)" → 0 résultat injustifié
  □ grep -rn "assertTrue(true)" → 0 résultat

Si TOUTES les cases sont cochées, mets à jour implementation_tree.md :
- Retire TOUS les marqueurs ⚠️
- Passe tous les modules S-1 à S-13 en [x] sans réserve
- Ajoute la ligne : "> ✅ Audit post-correction validé le YYYY-MM-DD — Tous les modules server-side sont production-ready."
```

---

## 📋 Ordre d'exécution recommandé

```
Session 1 : Préambule + PROMPT 1.1 à 1.4 (Bridges Debug, Jupyter, LSP, AI)
Session 2 : PROMPT 1.5 à 1.8 (Bridges Git, Build, Runtime + suppression mock)
Session 3 : PROMPT 2.1 à 2.5 (Package Manager complet)
Session 4 : PROMPT 3.1 (Debugger LLDB parsing)
Session 5 : PROMPT 4.1 + 4.2 (Git rebase/historique + Keystore auth)
Session 6 : PROMPT 5.1 (ML Runtime conditionals)
Session 7 : PROMPT 6.1 (AI applyActionResult + Keystore)
Session 8 : PROMPT 7.1 (Jupyter stubs)
Session 9 : PROMPT 8.1 + 9.1 + 9.2 + 9.3 (Nettoyage + AIDL + CMake + JNI)
Session 10 : PROMPT 10.1 + 10.2 + 10.3 (Tous les tests)
Session 11 : PROMPT 11.1 (Validation finale)
```
