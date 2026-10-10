#!/usr/bin/env python3
"""Bind custom development head packs to exact source and ModelsHD identities. AGPL v3.
Original inputs are read locally; only checksums and custom asset paths are emitted.
This registry is not a normal CharHD runtime selection or release approval.
"""
import argparse
import hashlib
import importlib.util
import json
import struct
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
NAMES = ('rose', 'lavitz', 'shana', 'haschel', 'albert', 'meru', 'kongol', 'miranda')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def geometry_hash(part, headers):
    digest = hashlib.sha256()
    def put(value):
        digest.update(struct.pack('>I', value & 0xffffffff))
    put(1)
    put(0)
    for vectors in (part['vertices'], part['normals']):
        put(len(vectors))
        digest.update(np.asarray(vectors, dtype='>f4').tobytes())
    put(len(part['faces']))
    for face in part['faces']:
        header = headers[face['sourceFace']]
        mode = header >> 24
        mode = (mode & ~8) | (8 if len(face['vertices']) == 4 else 0) | (16 if not mode & 1 else 0)
        put((mode << 24 | (header & 0x40000)) & 0xff040000)
        for vertex, normal in zip(face['vertices'], face['normals']):
            if not mode & 1:
                put(normal)
            put(vertex)
    return digest.hexdigest()


def build(files, root):
    spec = importlib.util.spec_from_file_location('party_study', ROOT / 'experiments/dart-modelshd-charhd/build-study.py')
    study = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(study)
    audit = json.loads((root / 'source-audit.json').read_text())
    rows = []
    for name in NAMES:
        directory = root / name / 'candidate-v1'
        fit = json.loads((directory / 'fit-receipt.json').read_text())
        actor = next(a for a in audit['characters'] if a['character'] == name)
        for row in fit['forms']:
            form = row['form']
            source = next(m for m in actor['models'] if m['form'] == form)
            data = (files / source['source']).read_bytes()
            if hashlib.sha256(data).hexdigest() != source['sourceSha256']:
                raise ValueError('Source changed: ' + name + '/' + form)
            _, _, _, identities, baseline = study.baseline(data)
            index = row['sourcePartIndex']
            table = struct.unpack_from('<I', data)[0] + 12
            offset, count = struct.unpack_from('<7I', data, table + index * 28)[4:6]
            cursor = table + offset
            headers = []
            for _ in range(count):
                header = struct.unpack_from('<I', data, cursor)[0]
                headers.append(header)
                cursor += 4 + ((header >> 8) & 255) * 4
            pack = directory / 'parts' / (identities[index] + '.json')
            uv = directory / (form + '-head-uv.json')
            texture = directory / 'head-basecolour.png'
            binding = json.loads(uv.read_text())
            geometry = geometry_hash(json.loads(pack.read_text())['parts'][0], headers)
            if binding['geometrySha256'] != geometry or binding['textureSha256'] != sha(texture):
                raise ValueError('Binding mismatch: ' + name + '/' + form)
            rows.append({
                'character': name, 'form': form,
                'sourceGeometrySha256': identities[index], 'geometrySha256': geometry,
                'baselineGeometrySha256': geometry_hash(baseline[index], headers),
                'pack': str(pack.relative_to(root)), 'packSha256': sha(pack),
                'uv': str(uv.relative_to(root)), 'uvSha256': sha(uv),
                'texture': str(texture.relative_to(root)), 'textureSha256': sha(texture),
                'width': 2048, 'height': 2048,
            })
    destination = root / 'candidate-manifest.json'
    destination.write_text(json.dumps({'format': 1, 'state': 'development-not-runtime-selected', 'parts': rows}, indent=2) + '\n')
    return len(rows)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--root', type=Path, default=ROOT / 'integrations/charhd/production/party-reconstruction')
    args = parser.parse_args()
    print('Wrote', build(args.files, args.root), 'development head bindings')
