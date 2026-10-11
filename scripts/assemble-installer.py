#!/usr/bin/env python3
"""Stamp the tiny Steam Deck entry point with hashes of the reviewed portable UI package."""
from pathlib import Path
import hashlib
import re
import argparse
from desktop_bootstrap import desktop_entry

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
desktop = args.output_dir / 'Install-Definitive.desktop'
desktop.write_text(desktop_entry())
desktop.chmod(0o755)
print('Portable installer SHA256:', digest)
print('Entry script SHA256:', script_hash)
