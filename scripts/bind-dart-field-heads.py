#!/usr/bin/env python3
"""Bind the accepted Dart field head to verified native variants. AGPL v3.

Reuses custom geometry/UVs only. No original model or image is emitted.
The actual-source Java probes independently validate these exact bindings.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CANONICAL = 'b84e13a11adbd3419e1e4c5b810c8bd797f9dc68af5d41fe3c4736ce5c4f584b'
VARIANTS = {
    '82d567bfa9c5bd155ae5d09aad12bb6b0b0bcd72a0e2543213b4822efecfc05b':
        '82d567bfa9c5bd155ae5d09aad12bb6b0b0bcd72a0e2543213b4822efecfc05b',
    '7ec2a8d20854bdffb131236bfec72593e54b3a40ccd08232afb8a5f372583706':
        'ae9365fe2fd18b715a67c3c1a82a6a65d50c22a34e1b1e8a5281ba5a04c56d57',
    '640e2a8491d68dd0351de32036c1f581548ca9ed1ec869ada24c92a903939c7c':
        '640e2a8491d68dd0351de32036c1f581548ca9ed1ec869ada24c92a903939c7c',
}


def bind(write=False):
    folder = ROOT / 'integrations/charhd/runtime-assets/charhd/characters'
    manifest_path = folder / 'characters.json'
    manifest = json.loads(manifest_path.read_text())
    selected = [entry for entry in manifest['parts'] if entry['sourceGeometrySha256'] not in VARIANTS]
    head = next(entry for entry in selected if entry['sourceGeometrySha256'] == CANONICAL)
    if head['character'] != 'dart' or head['form'] != 'field' or 'uv' not in head:
        raise ValueError('Canonical accepted field head missing')
    inputs = {}
    for key in ('pack', 'uv'):
        data = (folder / head[key]).read_bytes()
        if hashlib.sha256(data).hexdigest() != head[key + 'Sha256']:
            raise ValueError('Canonical custom input hash differs')
        inputs[key] = json.loads(data)
    if hashlib.sha256((folder / head['texture']).read_bytes()).hexdigest() != head['textureSha256']:
        raise ValueError('Accepted head material hash differs')
    for source, baseline in VARIANTS.items():
        row = copy.deepcopy(head)
        row.update(sourceGeometrySha256=source, baselineGeometrySha256=baseline,
                   pack=f'dart/parts/{source}.json', uv=f'dart/field-head-{source[:12]}-uv.json')
        for key, original in inputs.items():
            variant = copy.deepcopy(original)
            variant['sourceGeometrySha256'] = source
            data = json.dumps(variant, separators=(',', ':'), allow_nan=False).encode()
            path = folder / row[key]
            if write:
                path.write_bytes(data)
            elif path.read_bytes() != data:
                raise ValueError(f'Variant changes accepted custom payload: {row[key]}')
            row[key + 'Sha256'] = hashlib.sha256(data).hexdigest()
        selected.append(row)
    if write:
        manifest['parts'] = selected
        manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    elif manifest['parts'] != selected:
        raise ValueError('Manifest bindings differ from verified variant declarations')
    print('Dart bindings verified: four exact field identities; accepted geometry, UVs and pixels preserved')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='Regenerate only the three custom variant bindings')
    bind(parser.parse_args().write)
