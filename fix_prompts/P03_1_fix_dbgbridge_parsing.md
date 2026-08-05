# ⚠️ PRIORITÉ 3 — S-4 : Debugger C++ (Parsing LLDB mocké)
## PROMPT 3.1 — Fix parsing LLDB dans `dbgbridge.cpp`

**Fichier** : `core/modules/dbgbridge/src/dbgbridge.cpp`

**Problème** : `GetStackTrace()`, `GetScopes()`, `GetVariables()`, `Evaluate()` retournent des valeurs hardcodées au lieu de parser la sortie LLDB.

---

### EXIGENCES STRICTES :
1. Remplace l'IPC synchrone par un mécanisme de requête/réponse asynchrone :
   - `SendCommand()` envoie la commande ET bloque (avec timeout 5s) en attendant la réponse complète
   - Le read_thread parse la sortie et stocke les résultats dans une file thread-safe (std::promise/std::future ou condition_variable)
2. `GetStackTrace(threadId)`:
   - Envoie "bt" ou "thread backtrace" et parse la sortie LLDB :
     Format: `"frame #N: 0xADDR module\`function at file.cpp:line:col"`
   - Utilise std::regex pour extraire id, name, source, line, column de chaque frame
   - Retourne le vecteur réel parsé
3. `GetVariables(variablesReference)`:
   - Si variablesReference == 1000 (Locals) : envoie "frame variable" et parse
     Format: `"(type) name = value"`
   - Utilise std::regex pour extraire name, value, type
   - Pour les types composés (struct, class), attribue un variablesReference non-zéro pour permettre l'expansion
4. `GetScopes(frameId)`:
   - Envoie "frame select frameId" puis retourne les scopes réels (Locals avec variablesReference unique)
5. `Evaluate(expression, frameId)`:
   - Envoie "expr expression" et parse le résultat
   - Retourne la Variable avec le vrai type et la vraie valeur

### INTERDIT :
Retourner `{{1, "main", "main.cpp", 10, 0}}`, `{{"x", "42", "int", 0}}`, `"evaluated_result"`.
