#!/usr/bin/env python3
"""Prepare the owner's private head comparisons; original controls stay outside Git. AGPL v3."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import shutil

NAMES = ('rose', 'lavitz', 'shana', 'haschel', 'albert', 'meru', 'kongol', 'miranda')
ROOT = Path(__file__).resolve().parents[1]


def build(comparisons, output, sources=None):
    if output.exists() or output.resolve().is_relative_to(ROOT):
        raise ValueError('Use a new private gallery outside the checkout')
    output.mkdir(parents=True)
    entries = []
    directories = json.loads(sources.read_text()) if sources else [
        {'character': name, 'focus': focus, 'directory': str(comparisons / (name + '-' + suffix))}
        for name in NAMES for focus, suffix in (('actor', 'original-size-review-v4'), ('head', 'original-size-close-review-v4'))]
    expected = {(name, focus) for name in (('dart',) + NAMES if sources else NAMES) for focus in ('actor', 'head')}
    if len(directories) != len(expected) or {(row['character'], row['focus']) for row in directories} != expected:
        raise ValueError('Expected one head/body source pair per character')
    for row in directories:
        name, focus, directory = row['character'], row['focus'], Path(row['directory'])
        manifest = json.loads((directory / 'manifest.json').read_text())
        if manifest['character'] != name:
            raise ValueError('Comparison identity differs')
        for item in manifest['images']:
            source = (directory / item['path']).resolve()
            match = re.fullmatch(r'(field|combat)-(\d+)-(-?\d+(?:\.\d+)?)-comparison\.(?:jpg|png)', source.name)
            if not source.is_relative_to(directory.resolve()) or match is None:
                raise ValueError('Invalid comparison path')
            if hashlib.sha256(source.read_bytes()).hexdigest() != item['sha256']:
                raise ValueError('Comparison checksum differs')
            form, frame, angle = match.groups()
            target = output / 'comparisons' / name / focus / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            entries.append({'character': name, 'focus': focus, 'form': form, 'frame': int(frame), 'angle': float(angle), 'path': str(target.relative_to(output)), 'sha256': item['sha256']})
    index = {'scope': 'Private original-body comparisons; not public asset payload or gameplay validation', 'images': entries}
    (output / 'comparison-index.json').write_text(json.dumps(index, indent=2) + '\n')
    template = (ROOT / 'experiments/dart-modelshd-charhd/party-head-gallery.html').read_text()
    # Embed the index so a local file can open without fetch/server permissions.
    embedded = json.dumps(index).replace('<', '\\u003c')
    template = template.replace('<script type="module">', '<script type="application/json" id="comparison-data">' + embedded + '</script>\n<script type="module">')
    (output / 'index.html').write_text(template)
    return len(entries)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--comparisons', type=Path)
    parser.add_argument('--sources', type=Path, help='Explicit private JSON list of all nine head/body comparison directories')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not args.comparisons and not args.sources:
        parser.error('Provide --comparisons or --sources')
    print(json.dumps({'comparisonImages': build(args.comparisons, args.output, args.sources), 'public': False}))
