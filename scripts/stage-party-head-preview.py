#!/usr/bin/env python3
"""Stage an explicit eight-head CharHD preview, preserving accepted Dart. AGPL v3.

No normal runtime selection or installed files are modified. Only versioned
custom resources enter this overlay; original sources are not read.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ACCEPTED = ROOT / 'integrations/charhd/runtime-assets/charhd/characters'
CANDIDATES = ROOT / 'integrations/charhd/production/party-reconstruction/head-program-v4'
NAMES = {'rose', 'lavitz', 'shana', 'haschel', 'albert', 'meru', 'kongol', 'miranda'}


def checked(root, row, kind):
    name = row[kind]
    relative = Path(name)
    if relative.is_absolute() or '..' in relative.parts:
        raise ValueError('Resource path must stay within its root')
    source = (root / relative).resolve()
    if not source.is_relative_to(root.resolve()):
        raise ValueError('Resource path escaped its root')
    data = source.read_bytes()
    if hashlib.sha256(data).hexdigest() != row[kind + 'Sha256']:
        raise ValueError('Resource checksum differs: ' + name)
    return data


def stage(candidates, output, accepted=ACCEPTED, auxiliary_hair=None):
    if output.exists() or output.resolve().is_relative_to(ROOT):
        raise ValueError('Use a new output outside the checkout')
    prior = json.loads((accepted / 'characters.json').read_text())
    manifest = json.loads((candidates / 'candidate-manifest.json').read_text())
    rows = manifest['parts']
    expected = {(name, form) for name in NAMES for form in ('field', 'combat')}
    if len(rows) != 16 or {(row['character'], row['form']) for row in rows} != expected:
        raise ValueError('Expected exactly eight field/battle head pairs')
    if manifest['format'] != 1 or prior['format'] != 1 or any(row['character'] != 'dart' for row in prior['parts']):
        raise ValueError('Unexpected accepted/candidate scope')
    groups=[(accepted, 'accepted', prior['parts']), (candidates, 'heads', rows)]
    if auxiliary_hair:
        hair=json.loads((auxiliary_hair/'candidate-manifest.json').read_text())
        expected_hair={('rose','field',5),('rose','combat',7),*{('meru','field',i) for i in (7,8,9)},*{('meru','combat',i) for i in (9,10,11)}}
        hair_rows=hair['parts']
        if hair['format']!=1 or len(hair_rows)!=8 or {(r['character'],r['form'],r['sourcePartIndex']) for r in hair_rows}!=expected_hair or any(r.get('role')!='auxiliary-hair' for r in hair_rows):
            raise ValueError('Expected eight explicit animated Rose/Meru hair parts')
        groups.append((auxiliary_hair,'hair',hair_rows))
    parts, resources, identities = [], {}, set()
    for root, prefix, entries in groups:
        for entry in entries:
            if entry['sourceGeometrySha256'] in identities:
                raise ValueError('Duplicate source geometry identity')
            identities.add(entry['sourceGeometrySha256'])
            if prefix in ('heads','hair'):
                pack = json.loads(checked(root, entry, 'pack'))
                uv = json.loads(checked(root, entry, 'uv'))
                if len(pack['parts']) != 1 or pack['sourceGeometrySha256'] != entry['sourceGeometrySha256']:
                    raise ValueError('Expected one exact source-bound head')
                if uv['geometrySha256'] != entry['geometrySha256'] or uv['sourceGeometrySha256'] != entry['sourceGeometrySha256'] or uv['textureSha256'] != entry['textureSha256']:
                    raise ValueError('Head geometry/material transaction differs')
                mode=entry.get('alphaMode','opaque')
                if mode not in ('opaque','cutout') or mode!=uv.get('alphaMode','opaque'):
                    raise ValueError('Geometry/material alpha mode differs')
            row = dict(entry)
            for kind in ('pack', 'uv', 'texture'):
                if kind not in row:
                    continue
                data = checked(root, entry, kind)
                name = prefix + '/' + entry[kind]
                if name in resources and resources[name] != data:
                    raise ValueError('Conflicting resource bytes')
                resources[name] = data
                row[kind] = name
            parts.append(row)
    output.mkdir(parents=True)
    for name, data in resources.items():
        destination = output / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    result = {'format': 1, 'state': 'explicit-head-preview-not-runtime-selected', 'parts': parts}
    (output / 'characters.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def preview_jar(base, resources, output):
    if output.exists() or output.resolve().is_relative_to(ROOT):
        raise ValueError('Use a new preview JAR outside the checkout')
    prefix = 'charhd/characters/'
    with zipfile.ZipFile(base) as original:
        names = original.namelist()
        if len(names) != len(set(names)) or prefix + 'characters.json' not in names:
            raise ValueError('Expected one ordinary CharHD JAR')
        with zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED) as target:
            for name in names:
                if not name.startswith(prefix):
                    target.writestr(name, original.read(name))
            for path in sorted(resources.rglob('*')):
                if path.is_file():
                    target.writestr(prefix + path.relative_to(resources).as_posix(), path.read_bytes())
            target.writestr('META-INF/charhd/HEAD-PREVIEW.txt', 'Eight development party heads and any explicitly staged companion hair; accepted Dart unchanged. Owner review and native/Deck validation pending. Replace CharHD only in an isolated test installation. Do not install alongside another CharHD JAR.\n')
    with zipfile.ZipFile(output) as target:
        if target.testzip() is not None:
            raise ValueError('Preview JAR integrity failed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidates', type=Path, default=CANDIDATES)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--base-jar', type=Path)
    parser.add_argument('--preview-jar', type=Path)
    parser.add_argument('--auxiliary-hair',type=Path,help='Explicit optional development Rose/Meru hair; never selected by default')
    args = parser.parse_args()
    if bool(args.base_jar) != bool(args.preview_jar):
        parser.error('Provide both base JAR and preview JAR')
    result = stage(args.candidates, args.output, auxiliary_hair=args.auxiliary_hair)
    if args.base_jar:
        preview_jar(args.base_jar, args.output, args.preview_jar)
    print(json.dumps({'parts': len(result['parts']), 'newHeads': 16, 'animatedHair':8 if args.auxiliary_hair else 0, 'dart': 'accepted resources preserved', 'runtimeSelected': False}))
