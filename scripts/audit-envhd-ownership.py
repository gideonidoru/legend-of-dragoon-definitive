#!/usr/bin/env python3
"""Exclude Skurfa artwork and all source-identical scene aliases from EnvHD production.

This reads the private source census, verifies its covered source files against the
current extraction, and records only hashes/mappings, never retail artwork.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def signature(folder):
    entries = {}
    for line in (folder / 'mrg').read_text().splitlines():
        if not line.strip():
            continue
        match = re.fullmatch(r'(\d+)=(\d*);(\d+)', line)
        if not match or int(match[1]) in entries:
            raise ValueError('Malformed or duplicate source entry')
        index, real, size = match.groups()
        entries[int(index)] = (int(real) if real else None, int(size))

    def resolve(index):
        real, size = entries[index]
        if real is None:
            if size:
                raise ValueError('Missing required source reference')
            return None
        seen = set()
        while True:
            if real not in entries:
                raise ValueError('Alias target has no source entry')
            if entries[real][0] == real:
                break
            if real in seen:
                raise ValueError('Source alias cycle')
            seen.add(real)
            real = entries[real][0]
            if real is None:
                raise ValueError('Unresolved required source alias')
        path = folder / str(real)
        if not path.is_file() or not path.stat().st_size:
            raise ValueError('Missing required source file')
        return path

    pages = []
    for index in sorted(entries):
        if index < 3:
            continue
        path = resolve(index)
        if path is not None:
            pages.append(bytes.fromhex(digest(path.read_bytes())))
    metadata = resolve(0)
    if metadata is None:
        raise ValueError('Missing environment descriptor')
    return digest(metadata.read_bytes() + b''.join(pages))


def verify_sources(census, files):
    expected = {}
    for row in census['rows']:
        key = (row['bank'], row['file'])
        if key in expected and expected[key] != row['signature']:
            raise ValueError('Conflicting signatures for one source folder')
        expected[key] = row['signature']
    for (bank, number), value in sorted(expected.items()):
        folder = files / 'SECT' / f'DRGN{bank}.BIN' / str(number)
        if value != signature(folder):
            raise ValueError(f'Source census changed: DRGN{bank}/{number}; refresh before assigning ownership')
    return len(expected)


def plan(census, manifests, bindings):
    rows = census['rows']
    by_folder = {(row['bank'], row['file']): row for row in rows}
    protected = {}
    for resource, manifest in sorted(manifests.items()):
        source = by_folder[(manifest['drgnBin'], manifest['environmentDir'])]
        key = source['signature']
        if key in protected and protected[key]['resourceCut'] != resource:
            raise ValueError('Two Skurfa packs claim the same source; resolve before generating')
        protected[key] = {'resourceCut': resource, 'backgroundSha256': manifest['backgroundSha256'],
                          'sourceBank': source['bank'], 'sourceDirectory': source['file'],
                          'foregroundCount': manifest['foregroundCount']}
    for binding in bindings:
        if binding['resourceCut'] not in manifests:
            raise ValueError('Skurfa runtime binding has no resource manifest')
    groups = {}
    for row in rows:
        key = row['signature']
        group = groups.setdefault(key, {'sourceSignature': key, 'owner': 'skurfa' if key in protected else 'envhd',
                                        'action': 'reuse-existing' if key in protected else 'gap-needs-visual-dedup',
                                        'mappings': []})
        if key in protected:
            group.update(protected[key])
        group['mappings'].append({k: row[k] for k in ('cut', 'period', 'bank', 'file')})
    return {'schema': 1, 'policy': 'Skurfa resources are protected. EnvHD generates only gaps. Shared source signatures reuse one owner regardless of cut or period.',
            'skurfaPacks': len(manifests), 'skurfaRuntimeBindings': bindings,
            'protectedSourceScenes': len(protected),
            'protectedCutPeriodMappings': sum(len(g['mappings']) for g in groups.values() if g['owner'] == 'skurfa'),
            'fieldGapsBeforeVisualDedup': sum(g['owner'] == 'envhd' for g in groups.values()),
            'limitations': 'Source signature equality is conservative; visually identical scenes with different descriptors still need deduplication. Props and overlays remain unclassified.',
            'scenes': sorted(groups.values(), key=lambda g: g['sourceSignature'])}


def audit(root, files, census):
    verified = verify_sources(census, files)
    assets = root / 'integrations/skurfa/runtime-assets/scbackgroundhd'
    manifests = {}
    for path in assets.glob('cut*/manifest.json'):
        manifest = json.loads(path.read_text())
        cut = int(path.parent.name[3:])
        if digest((path.parent / manifest['backgroundFile']).read_bytes()) != manifest['backgroundSha256']:
            raise ValueError(f'Skurfa background changed: {path.parent.name}')
        for foreground in manifest['foregrounds']:
            if digest((path.parent / foreground['resourceFile']).read_bytes()) != foreground['sha256']['layer']:
                raise ValueError(f'Skurfa foreground changed: {path.parent.name}')
        manifests[cut] = manifest
    code = (root / 'integrations/skurfa/src/main/java/scbackgroundhd/BackgroundHdMod.java').read_text()
    matches = re.findall(r'event.disk == (\d+) && event.submapCut == (\d+)\) \{\s*replace\(event, ([\d, ]+)\);', code)
    bindings = []
    for disk, cut, arguments in matches:
        args = list(map(int, arguments.split(',')))
        if len(args) not in (4, 5) or args[0] != int(cut):
            raise ValueError('Unknown Skurfa binding syntax')
        bindings.append({'disk': int(disk), 'cut': int(cut), 'resourceCut': args[1] if len(args) == 5 else args[0]})
    if len(matches) != code.count('replace(event, ') - 1:
        raise ValueError('Unparsed Skurfa bindings; stop generation until the audit is updated')
    report = plan(census, manifests, bindings)
    report['verifiedSourceFolders'] = verified
    report['skurfaRevision'] = subprocess.check_output(['git', '-C', str(root / 'integrations/skurfa'), 'rev-parse', 'HEAD'], text=True).strip()
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--census', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = audit(args.root, args.files, json.loads(args.census.read_text()))
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(f"Protected {report['protectedSourceScenes']} Skurfa scenes across {report['protectedCutPeriodMappings']} mappings; {report['fieldGapsBeforeVisualDedup']} field gaps before visual deduplication")
