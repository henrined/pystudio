# 🧪 PRIORITÉ 10 — Tests d'intégration et validation
## PROMPT 10.3 — Tests d'intégration end-to-end

Crée un nouveau fichier de test d'intégration :
**Fichier** : `android/app/src/test/java/com/pystudio/integration/EndToEndServerTest.kt`

---

### EXIGENCES STRICTES :
Ce test vérifie que la chaîne complète fonctionne de bout en bout :

1. Test "Python Run" :
   - Crée un fichier Python temporaire contenant `print('hello')`
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

### INTERDIT :
Tests qui passent sans exécuter le code réel, assertions triviales.
