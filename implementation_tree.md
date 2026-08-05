# PyStudio Mobile — Arbre d'Implémentation

> **Principe :** On finalise le **Server-Side** (backend embarqué) en totalité et on valide chaque module avant de toucher au **Client-Side** (frontend React Native).
> Chaque nœud est indépendant de son voisin mais dépend de son parent.
> Statuts : `[ ]` TODO · `[~]` En cours · `[x]` Terminé · `[!]` Bloquant
> **Correction globale (P08_1) validée le 2026-08-04**
> ✅ Audit post-correction validé le 2026-08-04 — Tous les modules server-side sont production-ready.

---

## 🖥️ PARTIE 1 — SERVER-SIDE (Backend embarqué Android)

> Stack : **C++17/20, Kotlin, JNI, NDK, AIDL, Coroutines**
> Réf. SRS : `01_Architecture.md`, `02` → `11`, `13_Interfaces_API_Internes.md`, `14_Performances.md`, `15_Securite.md`

---

### MODULE S-1 : Socle & Infrastructure C++ (`pystudio_core`)
> Réf. [REQ-ARCH-0023] — Dépendances : aucune (point de départ)

- [x] **S-1.1** Initialisation du projet Android NDK (CMakeLists.txt racine, ABI filters : `arm64-v8a`, `armeabi-v7a`, `x86_64`)
- [x] **S-1.2** Orchestrateur C++ `pystudio_core` — registre de services natifs, lifecycle
- [x] **S-1.3** Couche JNI de base — conventions de nommage, gestion `DeleteLocalRef`, gestion des exceptions JNI
- [x] **S-1.4** Tests unitaires C++ (Google Test) — pipeline CI via `ctest`

---

### MODULE S-2 : Runtime CPython embarqué (`pyembed`)
> Réf. [REQ-ARCH-0014 → 0019] — Dépend de : **S-1**

- [x] **S-2.1** Cross-compilation CPython 3.11 + 3.12 pour chaque ABI (NDK toolchain)
  - [x] **S-2.1.1** Build `libpython3.x.so`
  - [x] **S-2.1.2** Build des modules d'extension standards (`.so`)
  - [x] **S-2.1.3** Packaging de la stdlib en `python3xx.zip` (zipimport)
- [x] **S-2.2** Module C++ `pyembed` — wrapping de l'API d'embedding (Py_Initialize, Py_RunFile, Py_Finalize)
- [x] **S-2.3** Isolation d'exécution — Android Isolated Process (`:runner`, `android:isolatedProcess=true`)
  - [x] **S-2.3.1** Interface AIDL `IRunnerService`
  - [x] **S-2.3.2** Communication Binder/AIDL entre UI-process et runner-process
- [x] **S-2.4** Gestion multi-version & venv émulés (`PYTHONHOME` / `PYTHONPATH` redirects)
- [x] **S-2.5** Streaming stdout/stderr vers l'UI en temps réel (EventEmitter)
- [x] **S-2.6** Tests : exécution d'un script Python simple, isolation du crash, streaming des logs

---

### MODULE S-3 : Toolchain C/C++ embarquée (`cxxtoolchain`)
> Réf. [REQ-ARCH-0025 → 0028] — Dépend de : **S-1**

- [x] **S-3.1** Intégration Clang/LLVM (binaires natifs ARM64 sur device) — `clang`, `clang++`, `lld`, `llvm-ar`, `llvm-strip`
- [x] **S-3.2** Sysroot NDK embarqué ou téléchargeable (Play Feature Delivery) avec vérification SHA-256
- [x] **S-3.3** Intégration CMake natif (binary ARM64) + Ninja
- [x] **S-3.4** Module C++ `cxxtoolchain` — wrapper Clang/libTooling, pilotage CMake/Ninja
- [x] **S-3.5** Génération automatique de `CMakeLists.txt` et `CMakePresets.json` selon template projet
- [x] **S-3.6** Build multi-ABI en parallèle (thread pool borné)
- [x] **S-3.7** `clang-format` et `clang-tidy` — analyse statique inline
- [x] **S-3.8** Génération de `compile_commands.json` pour `clangd`
- [x] **S-3.9** Tests : compilation d'un `Hello World` C++, vérification des `.so` générés par ABI

---

### MODULE S-4 : Débogueur LLDB (`dbgbridge` + DAP)
> Réf. [REQ-ARCH-0029] — Dépend de : **S-3** (pour C++), **S-2** (pour Python)

- [x] **S-4.1** Intégration `lldb-server` (process isolé, socket Unix local, vérification UID)
- [x] **S-4.2** Module C++ `dbgbridge` — pont RPC vers LLDB
- [x] **S-4.3** Hôte DAP Kotlin (`DebugService`) — implémentation du Debug Adapter Protocol
  - [x] **S-4.3.1** `launch` / `attach`
  - [x] **S-4.3.2** `setBreakpoints`
  - [x] **S-4.3.3** `continue` / `stepOver` / `stepInto` / `stepOut`
  - [x] **S-4.3.4** `variables` / `stackTrace` / `scopes`
  - [x] **S-4.3.5** `evaluate` (watch expressions)
- [x] **S-4.4** Bridge Python debug — `debugpy` intégré, pont vers DAP unifié
- [x] **S-4.5** Tests : session de debug Python (breakpoint + inspection variable), session LLDB sur binaire C++

---

### MODULE S-5 : Serveurs LSP embarqués
> Réf. [REQ-ARCH-0010] — Dépend de : **S-2**, **S-3**

- [x] **S-5.1** `pylsp` / `pyright` — packaging & démarrage en process isolé
- [x] **S-5.2** `clangd` — intégration avec `compile_commands.json` généré par CMake (S-3.8)
- [x] **S-5.3** Proxy LSP (JSON-RPC over stdin/stdout → AIDL/Binder → WebView)
- [x] **S-5.4** Tests : autocomplétion Python, diagnostics C++ en temps réel

---

### MODULE S-6 : Moteur Git (`gitengine`)
> Réf. [REQ-ARCH-0005/S5], `06_Fonctionnelles_Integration_Git.md` — Dépend de : **S-1**

- [x] **S-6.1** Cross-compilation `libgit2` pour chaque ABI
- [x] **S-6.2** Module C++ `gitengine` — wrapper libgit2
- [x] **S-6.3** `GitService` Kotlin — API de haut niveau
  - [x] **S-6.3.1** `clone` (HTTPS + SSH)
  - [x] **S-6.3.2** `status` / `diff`
  - [x] **S-6.3.3** `stage` / `unstage` / `commit`
  - [x] **S-6.3.4** `branch` (create, checkout, delete)
  - [x] **S-6.3.5** `merge` / `rebase`
  - [x] **S-6.3.6** `push` / `fetch` / `pull`
  - [x] **S-6.3.7** Historique de commits
- [x] **S-6.4** Gestion des credentials (Android Keystore, SSH keys chiffrées)
- [x] **S-6.5** Tests : clone → commit → push sur dépôt de test

---

### MODULE S-7 : Système de fichiers & Workspace (`WorkspaceService` + `FileSystemService`)
> Réf. [REQ-ARCH-0013] — Dépend de : **S-1**

- [x] **S-7.1** `FileSystemService` — accès sandboxé (Scoped Storage), `FileObserver` pour le watch
- [x] **S-7.2** `WorkspaceService` — cycle de vie des projets, persistance SQLite
  - [x] **S-7.2.1** Création, ouverture, fermeture de workspace
  - [x] **S-7.2.2** Indexation des fichiers (incrémentale)
  - [x] **S-7.2.3** Gestion de l'état de session (onglets ouverts, curseurs)
- [x] **S-7.3** Tests : création de projet, survie de l'état au redémarrage de l'app

---

### MODULE S-8 : Gestion des packages (`PackageManagerService`)
> Réf. `03_Fonctionnelles_Gestionnaire_Python.md`, `04_Fonctionnelles_Registre_Packages.md` — Dépend de : **S-2**, **S-7**

- [x] **S-8.1** Résolveur de dépendances pip (mode offline avec cache de wheels pré-compilées)
- [x] **S-8.2** Support online pip (pip install classique sur les dépôts autorisant les packages pur Python)
- [x] **S-8.3** Cache local de wheels cross-compilées pour packages natifs (NumPy, SciPy, OpenCV, etc.)
- [x] **S-8.4** Tests : installation d'un package Python pur, import dans le runner

---

### MODULE S-9 : Bibliothèques scientifiques & ML (`mlruntime`)
> Réf. `10_Fonctionnelles_Scientific_Computing.md` — Dépend de : **S-2**, **S-8**

- [x] **S-9.1** Cross-compilation NumPy + OpenBLAS (NDK)
- [x] **S-9.2** Cross-compilation SciPy (sur base NumPy)
- [x] **S-9.3** PyTorch Mobile / LibTorch Lite — wheels précompilées
- [x] **S-9.4** TensorFlow Lite — interpréteur + délégués NNAPI/GPU
- [x] **S-9.5** OpenCV — cross-compilation NDK ou SDK Android exposé via JNI
- [x] **S-9.6** Module C++ `mlruntime` — dispatch TFLite / LibTorch / OpenCV
- [x] **S-9.7** Tests : script Python exécutant un calcul NumPy, inférence TFLite basique

---

### MODULE S-10 : Kernel Jupyter (`JupyterKernelService`)
> Réf. `05_Fonctionnelles_Systeme_Notebook.md` — Dépend de : **S-2**, **S-7**

- [x] **S-10.1** Packaging `ipykernel` dans le runtime Python embarqué
- [x] **S-10.2** `JupyterKernelService` Kotlin — hébergement du kernel in-process
- [x] **S-10.3** Protocole Jupyter simplifié en mémoire (ZMQ émulé via IPC local)
- [x] **S-10.4** Exécution de cellules, capture des sorties riches (texte, images, DataFrames)
- [x] **S-10.5** Tests : exécution d'un notebook `.ipynb` avec cellule matplotlib

---

### MODULE S-11 : Assistant IA (`AIService`)
> Réf. `08_Fonctionnelles_Systeme_IA_Integre.md`, `09_Fonctionnelles_Runtime_IA.md` — Dépend de : **S-2**, **S-7**

- [x] **S-11.1** Modèle IA on-device (TFLite / GGUF/llama.cpp) — packaging et chargement
- [x] **S-11.2** `AIService` Kotlin — `ContextBuilder` (extraction du fichier actif, sélection, erreurs)
- [x] **S-11.3** Client API IA distante (opt-in, HTTPS, clé stockée Keystore)
- [x] **S-11.4** Application de patchs suggérés par l'IA (diff → apply)
- [x] **S-11.5** Tests : question sur un script Python → réponse + patch applicable

---

### MODULE S-12 : Marketplace (`MarketplaceService`)
> Réf. `11_Fonctionnelles_Marketplace_Extensions.md` — Dépend de : **S-7**, **S-8**

- [x] **S-12.1** Registre local + remote (JSON signé, vérification SHA-256 + signature)
- [x] **S-12.2** `MarketplaceService` Kotlin — recherche, téléchargement, quarantaine, installation
- [x] **S-12.3** Sandbox JS pour extensions tierces (limites d'API)
- [x] **S-12.4** Tests : installation d'un plugin de test, vérification de la signature

---

### MODULE S-13 : Bridges JSI / TurboModules
> Réf. [REQ-ARCH-0012], `13_Interfaces_API_Internes.md` — Dépend de : **S-2 à S-12** (un bridge par service)

- [x] **S-13.1** `RuntimeBridge` — `runScript`, `stopExecution`, flux stdout/stderr
- [x] **S-13.2** `BuildBridge` — `startBuild`, `cancelBuild`, flux logs build
- [x] **S-13.3** `DebugBridge` — toutes les commandes DAP (setBreakpoints, continue, step, etc.)
- [x] **S-13.4** `FSBridge` — `readFile`, `writeFile`, `listDir`, `watchDir`
- [x] **S-13.5** `GitBridge` — toutes les opérations Git
- [x] **S-13.6** `JupyterBridge` — `executeCell`, `interruptKernel`, `restartKernel`
- [x] **S-13.7** `AIBridge` — `sendMessage`, `applyPatch`, `cancelRequest`
- [x] **S-13.8** `MarketplaceBridge` — `search`, `install`, `uninstall`, `listInstalled`
- [x] **S-13.9** `LSPBridge` — `initialize`, `didOpen`, `didChange`, `completion`, `hover`, `diagnostics`

---

## 📱 PARTIE 2 — CLIENT-SIDE (Frontend React Native)

> Stack : **React Native (New Architecture), TypeScript, Hermes, Monaco via WebView, Zustand**

---

### MODULE C-1 : Fondations & Design System
> Dépend de : **S-13** (tous les bridges stables)

- [x] **C-1.1** Init projet React Native (New Architecture — Fabric + TurboModules)
- [x] **C-1.2** Système de thèmes VS Code (Dark+, tokens de couleur, typographie `JetBrains Mono` / `Fira Code`)
- [x] **C-1.3** Composants atomiques réutilisables (Icon, Button, Tooltip, ContextMenu, Badge)
- [x] **C-1.4** Layout principal : Activity Bar 48dp fixe + zone de contenu principale
- [x] **C-1.5** Barre de titre (`PyStudio Mobile`, `[🔍]`, `[⋮]`)
- [x] **C-1.6** Barre d'état (Status Bar bleue — branche Git, erreurs, langue, Ln/Col)
- [x] **C-1.7** Palette de commandes (`Ctrl+Shift+P` / barre de recherche globale)
- [x] **C-1.8** Navigation entre screens (React Navigation, un screen actif à la fois)

---

### MODULE C-2 : Écran Accueil
> Dépend de : **C-1**, **S-13.4** (FSBridge)

- [ ] **C-2.1** Boutons "New Project", "Open Folder", "Clone Git Repository"
- [ ] **C-2.2** Liste des projets récents (depuis WorkspaceService via FSBridge)
- [ ] **C-2.3** Galerie de templates (Python vide, C++ CMake, ML Notebook)

---

### MODULE C-3 : Écran Explorateur
> Dépend de : **C-1**, **S-13.4** (FSBridge)

- [ ] **C-3.1** Arbre de fichiers (virtualisé — FlatList récursif)
- [ ] **C-3.2** Menu contextuel tactile (appui long) : Ouvrir, Renommer, Supprimer, Copier le chemin
- [ ] **C-3.3** Watch temps réel (FileObserver → FSBridge → mise à jour de l'arbre)

---

### MODULE C-4 : Écran Éditeur (Monaco)
> Dépend de : **C-1**, **S-13.4**, **S-13.9** (LSPBridge)

- [ ] **C-4.1** Intégration Monaco Editor dans WebView (bundle HTML auto-contenu, offline)
- [ ] **C-4.2** Gestion clavier (REQ-ARCH-0010-B)
  - [ ] **C-4.2.1** `android:windowSoftInputMode="adjustResize"` dans AndroidManifest
  - [ ] **C-4.2.2** Listener `window.visualViewport.onresize` injecté dans la WebView
  - [ ] **C-4.2.3** Callback `editor.layout()` + `editor.revealPositionInCenterIfOutsideViewport()`
- [ ] **C-4.3** Coloration syntaxique Python et C/C++ (grammaire TextMate)
- [ ] **C-4.4** Canal LSP (postMessage WebView ↔ LSPBridge)
  - [ ] **C-4.4.1** Autocomplétion (IntelliSense)
  - [ ] **C-4.4.2** Diagnostics inline (soulignements d'erreurs)
  - [ ] **C-4.4.3** Hover (documentation au survol)
- [ ] **C-4.5** Gestion multi-onglets (tabs)
- [ ] **C-4.6** Barre d'outils flottante sur sélection tactile (Couper, Copier, Coller, Commenter, IA)
- [ ] **C-4.7** Indicateurs de breakpoints inline (cliquables)
- [ ] **C-4.8** Bouton Run [▶] — appel `RuntimeBridge.runScript`

---

### MODULE C-5 : Écran Recherche
> Dépend de : **C-1**, **S-13.4** (FSBridge)

- [ ] **C-5.1** Champ de recherche avec toggles (Casse, Mot entier, Regex)
- [ ] **C-5.2** Liste de résultats groupés par fichier avec snippets
- [ ] **C-5.3** Tap sur un résultat → navigation vers l'Éditeur à la ligne exacte
- [ ] **C-5.4** Remplacement global

---

### MODULE C-6 : Écran Git
> Dépend de : **C-1**, **S-13.5** (GitBridge)

- [ ] **C-6.1** Zone de message de commit + bouton Commit
- [ ] **C-6.2** Listes "Staged Changes" et "Changes" avec actions stage/unstage/discard
- [ ] **C-6.3** Vue diff inline (Monaco en mode read-only diff)
- [ ] **C-6.4** Gestion des branches (create, checkout, delete)
- [ ] **C-6.5** Historique de commits
- [ ] **C-6.6** Push / Pull / Fetch

---

### MODULE C-7 : Écran Débogage
> Dépend de : **C-1**, **S-13.3** (DebugBridge), **C-4** (breakpoints inline)

- [ ] **C-7.1** Barre de contrôle debug (▶ Continue, ⏸ Pause, ⏭ Step Over, ⏮ Step Into, ■ Stop)
- [ ] **C-7.2** Panneau Variables (arbre collapsible)
- [ ] **C-7.3** Panneau Call Stack
- [ ] **C-7.4** Panneau Breakpoints (liste, activer/désactiver)
- [ ] **C-7.5** Console de débogage (REPL, évaluation d'expressions)

---

### MODULE C-8 : Écran Extensions
> Dépend de : **C-1**, **S-13.8** (MarketplaceBridge)

- [ ] **C-8.1** Barre de recherche dans le marketplace
- [ ] **C-8.2** Listes "Installed" et "Recommended"
- [ ] **C-8.3** Boutons Install / Uninstall avec indicateur de progression
- [ ] **C-8.4** Fiche détaillée d'une extension

---

### MODULE C-9 : Écran IA
> Dépend de : **C-1**, **S-13.7** (AIBridge), **C-4** (contexte du fichier actif)

- [ ] **C-9.1** Interface de chat (bulles utilisateur / IA)
- [ ] **C-9.2** Affichage des blocs de code dans les réponses (syntax highlight)
- [ ] **C-9.3** Bouton "Apply Fix" → `AIBridge.applyPatch` → mise à jour de l'éditeur
- [ ] **C-9.4** Bandeau de contexte (fichier actif + sélection courante)
- [ ] **C-9.5** Champ de saisie + bouton envoi + microphone (opt-in)

---

### MODULE C-10 : Écran Paramètres
> Dépend de : **C-1**, **S-13.4** (FSBridge — lecture/écriture config)

- [ ] **C-10.1** Catégories : Text Editor, Workbench, Python Toolchain, Appearance
- [ ] **C-10.2** Contrôles : Toggle switches, dropdowns, sliders, champs texte
- [ ] **C-10.3** Thèmes (Dark+, Light, Dracula…)
- [ ] **C-10.4** Gestion des raccourcis clavier
- [ ] **C-10.5** Gestion des toolchains (version Python active, chemin SDK)

---

### MODULE C-11 : Panneaux & Overlays
> Dépend de : **C-4**, **S-13.1** (RuntimeBridge)

- [ ] **C-11.1** Terminal intégré (émulateur PTY → shell embarqué, output streaming)
- [ ] **C-11.2** Panneau Problèmes (erreurs/warnings du LSP, cliquable → éditeur)
- [ ] **C-11.3** Panneau Sortie (logs build, logs d'exécution)
- [ ] **C-11.4** Viewer Jupyter (cellules code + markdown + sorties riches)
- [ ] **C-11.5** Viewer de visualisation scientifique (graphiques matplotlib/Plotly, zoom/pan)

---

## 📋 Récapitulatif — Ordre d'implémentation Backend

```
S-1 → S-2 → S-5.1 (pylsp)
     → S-3 → S-5.2 (clangd)
           → S-4 (LLDB + DAP)
     → S-6 (Git)
     → S-7 (Workspace + FS)
           → S-8 (Packages)
                 → S-9 (ML libs)
           → S-10 (Jupyter)
           → S-11 (IA)
           → S-12 (Marketplace)
→ S-13 (TOUS les bridges — validation finale avant frontend)
→ [x] [VALIDATION] Tests d'intégration end-to-end : run, debug, build, git
→ C-1 → ... → C-11 (Frontend)
```
