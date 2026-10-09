#!/usr/bin/env python3
"""Stamp the tiny Steam Deck entry point with hashes of the reviewed portable UI package."""
from pathlib import Path
import hashlib
import re

root = Path(__file__).resolve().parents[1]
archive = root / 'build/distributions/Definitive-Installer.zip'
script = root / 'delivery/Install-Definitive.sh'
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
text = re.sub(r'^EXPECTED=.*$', 'EXPECTED=' + digest, script.read_text(), flags=re.M)
script.write_text(text)
script.chmod(0o755)
script_hash = hashlib.sha256(script.read_bytes()).hexdigest()
# Desktop Entry quoting: escaped quotes reach bash; %k supplies this launcher file's path.
command = f'''Exec=/bin/bash -c "set -e; d=$(mktemp -d); trap 'rm -rf \\\"$d\\\"' EXIT; curl --fail --location --proto '=https' --proto-redir '=https' --output \\\"$d/install.sh\\\" 'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-alpha-2026-10-09/Install-Definitive.sh'; echo '{script_hash}  ' \\\"$d/install.sh\\\" | sha256sum --check --status; /bin/bash \\\"$d/install.sh\\\""'''
desktop = root / 'delivery/Install-Definitive.desktop'
desktop.write_text('[Desktop Entry]\nType=Application\nName=Install Legend of Dragoon: Definitive\nComment=Guided Steam Deck setup\nIcon=applications-games\nTerminal=false\n' + command + '\nCategories=Game;\n')
desktop.chmod(0o755)
print('Portable installer SHA256:', digest)
print('Entry script SHA256:', script_hash)
