# Contexte du projet : PyStudio Mobile

Tu travailles sur le projet **PyStudio Mobile**, un IDE complet pour Android inspiré de VS Code, supportant Python et C/C++ nativement.

## Règles de session et Continuité

Pour assurer la continuité entre les sessions, respecte rigoureusement ces règles :

1. **Source de vérité de l'implémentation :** Le document maître de l'avancement est le fichier `implementation_tree.md` situé dans le dossier des artéfacts ou à la racine logique du projet. **Dès le début d'une session, si on te demande de continuer, lis ce fichier pour savoir où on en est.**
2. **Ordre strict :** Ne saute pas les modules. La partie Server-Side (C++, Kotlin) doit être terminée et validée avant de toucher au Client-Side (React Native).
3. **Documentation :** L'architecture et les spécifications détaillées se trouvent dans le dossier `SRS_Blocks/`. Référe-toi toujours à `01_Architecture.md` en cas de doute sur la stack ou l'intégration.
4. **Validation :** Chaque sous-module C++ terminé doit être accompagné de ses tests Google Test et la compilation doit réussir avant de cocher la case dans `implementation_tree.md`.
5. **Mise à jour :** Si tu termines un module, c'est **à toi** de mettre à jour le fichier `implementation_tree.md` en remplaçant le `[ ]` par `[x]` pour la tâche correspondante, afin que la prochaine session reprenne correctement.
