#!/usr/bin/env python3
"""Build private ModelsHD 0.2 geometry packs for the 19 party battle variants.

AGPL v3; see LICENSE. Reads the owner's extracted game files, never edits them.
One conservative surface pass is not bespoke character reconstruction or Deck proof.
"""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import struct
import tempfile

import numpy as np

spec = importlib.util.spec_from_file_location('modelshd_export', Path(__file__).with_name('export-modelshd-pilot.py'))
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)

CHARACTERS = ('dart', 'lavitz', 'shana', 'rose', 'haschel', 'albert', 'meru', 'kongol', 'miranda')
ROSTER = tuple((name, form) for name in CHARACTERS for form in ('combat', 'dragoon')) + (('divine', 'dragoon'),)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encode(pack):
    data = json.dumps(pack, separators=(',', ':'), allow_nan=False).encode()
    if len(data) > 16 * 1024 * 1024:
        raise ValueError('Pack exceeds the native adapter limit')
    parts = pack['parts']
    if not 0 < len(parts) <= 64 or sum(len(p['vertices']) for p in parts) > 200000 or sum(len(p['faces']) for p in parts) > 50000:
        raise ValueError('Pack exceeds native geometry budgets')
    return data


def geometry_checks(source, candidate, reports):
    """Check fixed joint borders and collapsed new triangles in original part frames."""
    preserved = 0
    for (points, polygons), part, report in zip(source, candidate['parts'], reports):
        target = np.array(part['vertices'])
        if not report['changed']:
            if not np.array_equal(points, target):
                raise ValueError('Held part changed')
            continue
        triangles = exporter.surface.triangles(polygons)
        owners = Counter(tuple(sorted(edge)) for refs, *_ in triangles for edge in zip(refs, refs[1:] + refs[:1]))
        boundary = sorted({vertex for edge, count in owners.items() if count == 1 for vertex in edge})
        if boundary and not np.array_equal(points[boundary], target[boundary]):
            raise ValueError('Open joint border moved')
        preserved += len(boundary)
        for face in part['faces']:
            a, b, c = target[face['vertices']]
            if np.linalg.norm(np.cross(b-a, c-a)) <= 1e-10:
                raise ValueError('Refinement collapsed a triangle')
    return {'openBorderVerticesUnchanged': preserved, 'newTrianglesNoncollapsed': True}


def animation_inventory(folder, source, candidate):
    """Bounded original keyframe samples; no interpolation, native timing or collision claim."""
    supported, held = [], []
    parts = [(np.array(p['vertices']), []) for p in candidate['parts']]
    for path in sorted(folder.iterdir(), key=lambda p: p.name):
        if not path.name.isdecimal() or path.name == '32' or not path.is_file():
            continue
        data = exporter.surface.poses.materials.bounded_read(path)
        try:
            keyframes = exporter.surface.poses.read_keyframes(data, len(source))
        except ValueError as error:
            held.append({'file': path.name, 'sha256': digest(data), 'reason': str(error)})
            continue
        for keyframe in keyframes:
            for points in exporter.surface.poses.posed_vertices(parts, keyframe, 0):
                if not np.isfinite(points).all():
                    raise ValueError('Nonfinite posed geometry')
        supported.append({'file': path.name, 'sha256': digest(data), 'keyframes': len(keyframes)})
    if not supported:
        raise ValueError('No matching original keyframe sequence')
    return {'sampled': supported, 'unsupported': held, 'scope': 'Original rigid transforms at stored keyframes only; not native playback, joint collision or gameplay.'}


def build(files, output):
    files, output = files.resolve(), output.resolve()
    repo = Path(__file__).resolve().parents[1]
    if output == repo or repo in output.parents:
        raise ValueError('Source-derived packs must stay outside the source checkout')
    if output.exists():
        raise FileExistsError('Choose a new output folder; existing packs are never overwritten')
    sources = [(name, form, files / 'characters' / name / 'models' / form / '32') for name, form in ROSTER]
    for _, _, path in sources:
        if not path.is_file():
            raise FileNotFoundError(f'Missing required model: {path}')
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='.modelshd-build-', dir=output.parent))
    try:
        packs = stage / 'model-packs/modelshd/battle'
        controls = stage / 'verification/controls'
        packs.mkdir(parents=True)
        controls.mkdir(parents=True)
        rows, identities = [], set()
        for name, form, path in sources:
            data = exporter.surface.poses.materials.bounded_read(path)
            if struct.unpack_from('<2I', data, 4) != (0, 0):
                raise ValueError(f'{name}/{form}: auxiliary container data is not supported')
            control, _ = exporter.export(data, True)
            candidate, reports = exporter.export(data)
            identity = candidate['sourceGeometrySha256']
            if identity in identities:
                raise ValueError('Unexpected duplicate roster source identity')
            identities.add(identity)
            source, _, _ = exporter.native_parts(data)
            checks = geometry_checks(source, candidate, reports)
            animations = animation_inventory(path.parent, source, candidate)
            payload, baseline = encode(candidate), encode(control)
            (packs / f'{identity}.json').write_bytes(payload)
            (controls / f'{identity}.json').write_bytes(baseline)
            rows.append({'character': name, 'form': form, 'sourceRelativePath': str(path.relative_to(files)), 'sourceSha256': digest(data), 'sourceGeometrySha256': identity,
                         'packSha256': digest(payload), 'packBytes': len(payload), 'animationParts': len(reports), 'refinedParts': sum(p['changed'] for p in reports),
                         'sourceVertices': sum(len(v) for v, _ in source), 'candidateVertices': sum(len(p['vertices']) for p in candidate['parts']),
                         'sourceTriangles': sum(len(exporter.surface.triangles(p)) for _, p in source), 'candidateTriangles': sum(2 if len(f['vertices']) == 4 else 1 for p in candidate['parts'] for f in p['faces']),
                         'checks': checks, 'animations': animations, 'parts': reports})
            print(f'{name}/{form}: {rows[-1]["refinedParts"]}/{len(reports)} parts refined', flush=True)
        manifest = {'version': '0.2.0', 'recipe': {'iterations': 1, 'strength': 0.7, 'creaseDegrees': 110, 'jointBorders': 'fixed', 'sourceMaterials': 'inherited from active source'},
                    'scope': '19 source-derived battle geometry candidates. No authored new faces/costumes, GPU/gameplay, real texture-mod integration or physical Deck acceptance.',
                    'toolHashes': {name: digest(Path(__file__).with_name(name).read_bytes()) for name in ('build-modelshd-roster.py', 'export-modelshd-pilot.py', 'refine-model-surfaces.py', 'inspect-model-poses.py', 'audit-model-materials.py')}, 'numpy': np.__version__, 'models': rows}
        (stage / 'roster-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        if output.exists():
            raise FileExistsError('Output appeared during build; retained unchanged')
        os.rename(stage, output)
        return manifest
    except BaseException:
        shutil.rmtree(stage)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True, help='Existing extracted game files directory')
    parser.add_argument('--output', type=Path, required=True, help='New private output folder outside Git')
    args = parser.parse_args()
    manifest = build(args.files, args.output)
    print(f'Built {len(manifest["models"])} model packs in {args.output}')


if __name__ == '__main__':
    main()
