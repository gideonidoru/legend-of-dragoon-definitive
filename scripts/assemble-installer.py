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
template_text = template.read_text()
# Preserve the actual reviewed entrypoint, including interrupted-stage recovery;
# generate only the requested archive digest/tag and current Desktop shortcut.
if template_text.count('# definitive-owned-stage-v1:') != 1 or 'recover_owned_stages "$CACHE" ".portable." "portable"' not in template_text:
    raise ValueError('Portable template is missing owned-stage startup recovery')
text, count = re.subn(r'^EXPECTED=.*$', 'EXPECTED=' + digest, template_text, flags=re.M)
if count != 1:
    raise ValueError('Portable template needs exactly one installer digest')
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
