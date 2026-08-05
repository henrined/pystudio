# 📌 PRÉAMBULE — Contexte système à injecter AVANT chaque prompt

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
