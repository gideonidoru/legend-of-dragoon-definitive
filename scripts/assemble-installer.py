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
# Keep the command valid both as Desktop Entry Exec and as a literal shell copy.
# Nested dollar/quote escaping caused curl to consume the URL as its output filename.
def quote_exec_argument(value):
    if any(ch in value for ch in '\\"`$%'):
        raise ValueError('Bootstrap Exec must not require nested escapes or field codes')
    return '"' + value + '"'

shell = f"""set -o pipefail; umask 077; mkdir -p ~/.cache/legend-of-dragoon-definitive; (echo Legend of Dragoon - Definitive setup; mktemp -d /tmp/definitive-installer.XXXXXX | xargs -I DEF_STAGE /usr/bin/env -C DEF_STAGE /bin/bash -c 'set -euo pipefail; echo Downloading the verified installer; curl --fail --location --show-error --proto =https --proto-redir =https --retry 2 --connect-timeout 20 --max-time 180 --output install.sh https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/definitive-alpha-2026-10-09-presentation/Install-Definitive.sh; sha256sum install.sh | grep -q ^{script_hash} || {{ echo Installer checksum mismatch. No installer was launched.; exit 1; }}; /bin/bash install.sh; rm -f install.sh; pwd | xargs rmdir') 2>&1 | tee ~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log || {{ echo Setup could not start.; echo Diagnostic logs:; echo ~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log; echo ~/.cache/legend-of-dragoon-definitive/installer-bootstrap.log; echo ~/.cache/legend-of-dragoon-definitive/installer.log; echo Press Enter to close.; read -r; exit 1; }}"""
command = 'Exec=/bin/bash -c ' + quote_exec_argument(shell)
desktop = root / 'delivery/Install-Definitive.desktop'
desktop.write_text('[Desktop Entry]\nType=Application\nName=Install Legend of Dragoon: Definitive\nComment=Guided Steam Deck setup with visible startup diagnostics\nIcon=applications-games\nTerminal=true\n' + command + '\nCategories=Game;\n')
desktop.chmod(0o755)
print('Portable installer SHA256:', digest)
print('Entry script SHA256:', script_hash)
