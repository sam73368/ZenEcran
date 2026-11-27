"""Touche l'élément dont le texte (ou la description) correspond, via uiautomator. Usage : tap.py "Libellé" """
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

label = sys.argv[1]
for _ in range(3):
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    raw = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        continue
    for n in root.iter("node"):
        if n.get("text") == label or n.get("content-desc") == label:
            b = [int(x) for x in re.findall(r"\d+", n.get("bounds", ""))]
            if len(b) == 4:
                x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
                subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)])
                print("touché", label, x, y)
                sys.exit(0)
print("introuvable :", label)
sys.exit(1)
