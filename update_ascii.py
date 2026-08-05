import re

file_path = '/data/data/com.termux/files/home/pystudio/SRS_Blocks/12_Interfaces_UX_UI.md'
with open(file_path, 'r') as f:
    content = f.read()

replacements = {
    "4.1 Accueil": r"""```text
+--+-----------------------------------+
|  | PyStudio Mobile            [🔍][⋮]|
|🏠| Welcome                           |
|  | PyStudio Mobile                   |
|📁|                                   |
|  | [ + New Project ]                 |
|🔎| [ 📂 Open Folder ]                |
|  | [ ⎇ Clone Git Repository ]        |
|⎇|                                   |
|  | Recent                            |
|▶| 📄 main.py               2h ago   |
|  | 📄 data_analysis        Yesterday |
|🧩|                                   |
+--+-----------------------------------+
```""",
    "4.2 Explorateur": r"""```text
+--+-----------------------------------+
|  | PyStudio Mobile            [🔍][⋮]|
|🏠| EXPLORER                     [...] |
|  | v my_project                      |
|📁|   v src                           |
|  |     📄 main.py                    |
|🔎|   v native                        |
|  |     ⚙ CMakeLists.txt              |
|⎇|   > tests                         |
|  |                                   |
|▶|                                   |
|  |                                   |
|🧩|                                   |
+--+-----------------------------------+
```""",
    "4.3 Éditeur": r"""```text
+--+-----------------------------------+
|  | PyStudio Mobile            [🔍][⋮]|
|🏠| 📄 main.py x                [▶][⋮]|
|  |  1 import requests                |
|📁|  2 from flask import Flask        |
|  |  3                                |
|🔎|  4 app = Flask(__name__)          |
|  |  5                                |
|⎇|  6 def fetch_user_data(id):       |
|  |  7     url = f"https..."          |
|▶|                                   |
|  |                                   |
|🧩|                                   |
+--+-----------------------------------+
| ⎇ main*  [X]0 [!]0   Ln 15, Col 21   |
+--------------------------------------+
```""",
    "4.4 Recherche": r"""```text
+--+-----------------------------------+
|  | Search                        [⋮] |
|🏠| [ compute                   ][🔍] |
|  | compute               [Aa][\b][.*]|
|📁|                                   |
|  | 📄 src/analytics.js               |
|🔎| Line 143: function computeScore() |
|  | Line 210: value = computeData()   |
|⎇|                                   |
|  | 📄 README.md                      |
|▶| Line 55: The algorithm to compute |
|  |                                   |
|🧩|                                   |
+--+-----------------------------------+
| ⎇ main*  [X]1 [!]1                 🔔|
+--------------------------------------+
```""",
    "4.5 Git": r"""```text
+--+-----------------------------------+
|  | Source Control             [↻][⋮] |
|🏠|                                   |
|  | [ feat: add user profile page   ] |
|📁| [ navigation                  ] |
|  |                [ Commit > ]       |
|🔎|                                   |
|  | Staged Changes (2)                |
|⎇|  [M] 📄 components/UserProfile.js |
|  |  [A] 📄 services/api.js           |
|▶|                                   |
|  | Changes (3)                       |
|🧩|  [M] 📄 routes/AppRoutes.js       |
+--+-----------------------------------+
```""",
    "4.6 Débogage": r"""```text
+--+-----------------------------------+
|  | Run and Debug                     |
|🏠| [▶][⏸][⏭][⏮][↻][■]            |
|  | v VARIABLES                   [+] |
|📁|   v Locals                        |
|  |     v self (dict) {x:4, y:10}     |
|🔎|       x = 4                       |
|  |                                   |
|⎇| v CALL STACK                    [+]|
|  |   v main.py      stopped at line24|
|▶|      start_process            L20  |
|  |      initialize_app           L12  |
|🧩| v BREAKPOINTS                     |
+--+-----------------------------------+
| ⎇ main*  [X]0 [!]0      Ln 24, Col 5 |
+--------------------------------------+
```""",
    "4.7 Extensions": r"""```text
+--+-----------------------------------+
|  | Extensions                 [🔍][⋮]|
|🏠| [ 🔍 Search Extensions in Mark...] |
|  |                                   |
|📁| v INSTALLED                    2  |
|  |  📦 Python v2024.18.0             |
|🔎|     Microsoft          [Uninstall]|
|  |  📦 C/C++ v1.22.0                 |
|⎇|     Microsoft          [Uninstall]|
|  |                                   |
|▶| v RECOMMENDED                  3  |
|  |  📦 GitHub Copilot v1.23.0        |
|🧩|     Microsoft          [ Install ]|
+--+-----------------------------------+
```""",
    "4.8 IA": r"""```text
+--+-----------------------------------+
|  | AI Assistant                  [+] |
|🏠|                                   |
|  | 👤 User                           |
|📁| how do I center a div in CSS?     |
|  |                                   |
|🔎| 🤖 AI Assistant                   |
|  | You can easily center a div...    |
|⎇|                                   |
|  | .container {                      |
|▶|    display: flex;                  |
|  |    justify-content: center;       |
|🤖| }                                 |
|  |          [ Apply Fix -> ]         |
+--+-----------------------------------+
| [ Ask AI...                   ][>][m]|
+--------------------------------------+
```""",
    "4.9 Paramètres": r"""```text
+--+-----------------------------------+
|  | Settings                   [•••] S|
|🏠| [ 🔍 Search settings...          ] |
|  | TEXT EDITOR                       |
|📁| Cursor                            |
|  | Files                             |
|🔎| Auto Save           [afterDelay v]|
|  | Render Whitespace     [Enabled (O)]|
|⎇| WORKBENCH                         |
|  | Appearance                        |
|▶| Side Bar Position          [Right (O)]|
|  | PYTHON TOOLCHAIN                  |
|⚙| Python Path    Set python path... >|
+--+-----------------------------------+
```"""
}

# The regex replaces the first ``` block following the section header
for section, new_block in replacements.items():
    pattern = r"(###### \[REQ-INTF-\d+\] " + re.escape(section) + r".*?\n\n.*?\n\n)```.*?```"
    # Fallback if no text between header and code block
    pattern2 = r"(###### \[REQ-INTF-\d+\] " + re.escape(section) + r".*?\n\n)```.*?```"
    
    if re.search(pattern, content, flags=re.DOTALL):
        content = re.sub(pattern, r"\1" + new_block, content, count=1, flags=re.DOTALL)
    elif re.search(pattern2, content, flags=re.DOTALL):
        content = re.sub(pattern2, r"\1" + new_block, content, count=1, flags=re.DOTALL)
    else:
        print(f"Failed to find section {section}")

with open(file_path, 'w') as f:
    f.write(content)

print("Done updating ASCII mockups.")
