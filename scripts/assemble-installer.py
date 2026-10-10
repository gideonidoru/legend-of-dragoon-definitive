#!/usr/bin/env python3
"""Stamp the tiny Steam Deck entry point with hashes of the reviewed portable UI package."""
from pathlib import Path
import hashlib
import re
import argparse

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output-dir', type=Path, default=root / 'delivery')
parser.add_argument('--tag')
args = parser.parse_args()
args.output_dir.mkdir(parents=True, exist_ok=True)
archive = root / 'build/distributions/Definitive-Installer.zip'
template = root / 'delivery/Install-Definitive.sh'
script = args.output_dir / 'Install-Definitive.sh'
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
text = re.sub(r'^EXPECTED=.*$', 'EXPECTED=' + digest, template.read_text(), flags=re.M)
if args.tag:
    if not re.fullmatch(r'[A-Za-z0-9._-]+', args.tag):raise ValueError('Invalid release tag')
    text = re.sub(r'^TAG=.*$', 'TAG=' + args.tag, text, flags=re.M)
tag_match = re.search(r'^TAG=([A-Za-z0-9._-]+)$', text, flags=re.M)
if tag_match is None:
    raise ValueError('Portable entry point needs a valid release tag')
tag = tag_match[1]
script.write_text(text)
script.chmod(0o755)
script_hash = hashlib.sha256(script.read_bytes()).hexdigest()
# Keep the command valid both as Desktop Entry Exec and as a literal shell copy.
# Nested dollar/quote escaping caused curl to consume the URL as its output filename.
def quote_exec_argument(value):
    if any(ch in value for ch in '\\"`$%'):
        raise ValueError('Bootstrap Exec must not require nested escapes or field codes')
    return '"' + value + '"'

shell = f"""set -o pipefail; umask 077; mkdir -p ~/.cache/legend-of-dragoon-definitive; (echo Legend of Dragoon - Definitive setup; mktemp -d /tmp/definitive-installer.XXXXXX | xargs -I DEF_STAGE /usr/bin/env -C DEF_STAGE /bin/bash -c 'set -euo pipefail; cleanup() {{ mv -fT desktop-bootstrap.log ~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log; rm -f install.sh; pwd | xargs rmdir; }}; trap cleanup EXIT; (echo Downloading the verified installer; curl --fail --location --show-error --proto =https --proto-redir =https --retry 2 --connect-timeout 20 --max-time 180 --output install.sh https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/{tag}/Install-Definitive.sh; sha256sum install.sh | grep -q ^{script_hash} || {{ echo Installer checksum mismatch. No installer was launched.; exit 1; }}; /bin/bash install.sh) 2>&1 | tee desktop-bootstrap.log') 2>&1 || {{ echo Setup could not start.; echo Diagnostic logs:; echo ~/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log; echo ~/.cache/legend-of-dragoon-definitive/installer-bootstrap.log; echo ~/.cache/legend-of-dragoon-definitive/installer.log; echo Press Enter to close.; read -r; exit 1; }}"""
command = 'Exec=/bin/bash -c ' + quote_exec_argument(shell)
desktop = args.output_dir / 'Install-Definitive.desktop'
desktop.write_text('[Desktop Entry]\nType=Application\nName=Install Legend of Dragoon: Definitive\nComment=Guided Steam Deck setup with visible startup diagnostics\nIcon=applications-games\nTerminal=true\n' + command + '\nCategories=Game;\n')
desktop.chmod(0o755)
print('Portable installer SHA256:', digest)
print('Entry script SHA256:', script_hash)
