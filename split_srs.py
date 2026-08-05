import os

input_file = '/data/data/com.termux/files/home/pystudio/PyStudio_Mobile_SRS.md'
output_dir = '/data/data/com.termux/files/home/pystudio/SRS_Blocks'

os.makedirs(output_dir, exist_ok=True)

blocks = [
    (1, 51, '00_Introduction_et_Generale.md'),
    (52, 922, '01_Architecture.md'),
    (923, 1580, '02_Fonctionnelles_Runtime_Python.md'),
    (1581, 2254, '03_Fonctionnelles_Gestionnaire_Python.md'),
    (2255, 2948, '04_Fonctionnelles_Registre_Packages.md'),
    (2949, 3666, '05_Fonctionnelles_Systeme_Notebook.md'),
    (3667, 4397, '06_Fonctionnelles_Integration_Git.md'),
    (4398, 5096, '07_Fonctionnelles_Build_System.md'),
    (5097, 5936, '08_Fonctionnelles_Systeme_IA_Integre.md'),
    (5937, 6522, '09_Fonctionnelles_Runtime_IA.md'),
    (6523, 6575, '10_Fonctionnelles_Scientific_Computing.md'),
    (6576, 8264, '11_Fonctionnelles_Marketplace_Extensions.md'),
    (8265, 8860, '12_Interfaces_UX_UI.md'),
    (8861, 10569, '13_Interfaces_API_Internes.md'),
    (10570, 11682, '14_Performances.md'),
    (11683, 12963, '15_Securite.md'),
    (12964, None, '16_Annexes.md')
]

with open(input_file, 'r', encoding='utf-8') as f:
    lines = f.readlines()

for start, end, filename in blocks:
    filepath = os.path.join(output_dir, filename)
    with open(filepath, 'w', encoding='utf-8') as out_f:
        if end is None:
            out_f.writelines(lines[start-1:])
        else:
            out_f.writelines(lines[start-1:end])

print("Split complete! Files created in", output_dir)
