#!/usr/bin/env python3
"""Check custom native-size party head resources, never original game data. AGPL v3."""
import hashlib
import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import numpy as np
from PIL import Image
import trimesh

ROOT = Path(__file__).resolve().parents[1]
PARTY = ROOT / 'integrations/charhd/production/party-reconstruction'
CANDIDATES = PARTY / 'head-program-v4'


def main(candidates=CANDIDATES):
    manifest = json.loads((candidates / 'candidate-manifest.json').read_text())
    spec = importlib.util.spec_from_file_location('head_stage', ROOT / 'scripts/stage-party-head-preview.py')
    staging = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(staging)
    expected = {(name, form) for name in staging.NAMES for form in ('field', 'combat')}
    assert len(manifest['parts']) == 16 and {(row['character'], row['form']) for row in manifest['parts']} == expected
    assert len({row['sourceGeometrySha256'] for row in manifest['parts']}) == 16
    for row in manifest['parts']:
        directory = candidates / row['character']
        fit = json.loads((directory / 'fit-receipt.json').read_text())
        record = next(r for r in fit['forms'] if r['form'] == row['form'])
        landmark_path = directory / 'authoring/native-landmarks.json'
        if not landmark_path.exists():
            landmark_path = PARTY / 'native-size-head-landmarks-v2.json'
        controls = json.loads(landmark_path.read_text())['characters'][row['character']]
        target = controls['forms'][row['form']]
        assert 'targetPosedBodyToSkullHeightRatio' not in target
        assert fit['meshInputSha256'] == controls['meshInputSha256']
        assert fit['landmarksSha256'] == hashlib.sha256(landmark_path.read_bytes()).hexdigest()
        frame = np.asarray(record.get('posedAnatomicalFrame') or np.eye(3))
        assert np.allclose(frame.T @ frame, np.eye(3), atol=1e-8)
        assert np.isclose(np.linalg.det(frame), 1, atol=1e-8)
        crown = np.asarray(controls.get('rawCrownPoint', [0, controls['rawCrownY'], 0])) @ frame.T
        chin = np.asarray(controls.get('rawChinPoint', [0, controls['rawChinY'], 0])) @ frame.T
        expected_scale = (target['sourceChinY'] - target['sourceCrownY']) / (chin[1] - crown[1])
        assert np.isclose(record['scale'], expected_scale, atol=1e-9, rtol=0)
        assert np.isclose(record['scale'] * crown[1] + record['offset'][1], target['sourceCrownY'], atol=1e-8)
        assert np.isclose(record['scale'] * chin[1] + record['offset'][1], target['sourceChinY'], atol=1e-8)
        if 'sourceAnatomicalBasis' in target:
            assert record['sourceAnatomicalBasis'] == target['sourceAnatomicalBasis']
        pack = json.loads(staging.checked(candidates, row, 'pack'))
        uv = json.loads(staging.checked(candidates, row, 'uv'))
        staging.checked(candidates, row, 'texture')
        part = pack['parts'][0]
        vertices, normals, coordinates = np.asarray(part['vertices']), np.asarray(part['normals']), np.asarray(uv['uvs'])
        assert len(pack['parts']) == 1 and vertices.shape == normals.shape and coordinates.shape == (len(vertices), 2)
        assert np.isfinite(vertices).all() and np.isfinite(normals).all() and np.isfinite(coordinates).all()
        assert coordinates.min() >= 0 and coordinates.max() <= 1
        assert np.allclose(np.linalg.norm(normals, axis=1), 1, atol=1e-5)
        assert len(part['faces']) == record['triangles'] == 6000
        assert uv['geometrySha256'] == row['geometrySha256'] and pack['sourceGeometrySha256'] == uv['sourceGeometrySha256'] == row['sourceGeometrySha256']
        scene = trimesh.load(directory / (row['form'] + '-fitted-head.glb'), force='scene', process=False)
        assert len(scene.geometry) == 1
        mesh = next(iter(scene.geometry.values()))
        assert len(mesh.faces) == 6000 and np.isfinite(mesh.vertices).all()
        with Image.open(directory / 'head-basecolour.png') as texture:
            assert texture.size == (2048, 2048)
            assert np.array_equal(np.asarray(texture.convert('RGB')), np.asarray(mesh.visual.material.baseColorTexture.convert('RGB')))
    with tempfile.TemporaryDirectory(prefix='party-head-integrity-') as tmp:
        overlay = Path(tmp) / 'overlay'
        result = staging.stage(candidates, overlay)
        accepted = json.loads((staging.ACCEPTED / 'characters.json').read_text())
        dart = [row for row in result['parts'] if row['character'] == 'dart']
        assert len(dart) == len(accepted['parts']) == 12
        for row, original in zip(dart, accepted['parts']):
            for key in ('sourceGeometrySha256', 'geometrySha256', 'baselineGeometrySha256', 'packSha256', 'uvSha256', 'textureSha256'):
                assert row.get(key) == original.get(key)
            for kind in ('pack', 'uv', 'texture'):
                if kind in row:
                    assert staging.checked(overlay, row, kind) == staging.checked(staging.ACCEPTED, original, kind)
        for name, patch in [('checksum', {'packSha256': '0' * 64}), ('escape', {'pack': '../characters.json'}), ('absolute', {'pack': '/private/data.json'})]:
            row = {**manifest['parts'][0], **patch}
            try:
                staging.checked(candidates, row, 'pack')
            except ValueError:
                pass
            else:
                raise AssertionError('Invalid ' + name + ' accepted')
    baseline = json.loads((PARTY / 'original-design-v3.json').read_text())['runtimeBaseline']
    runtime = ROOT / 'integrations/charhd/runtime-assets'
    actual = {str(p.relative_to(runtime)): hashlib.sha256(p.read_bytes()).hexdigest() for p in runtime.rglob('*') if p.is_file()}
    assert actual == baseline
    print(json.dumps({'headsVerified': 16, 'characters': 8, 'trianglesPerHead': 6000, 'nativeCrownJawScale': 'verified against authored source controls', 'acceptedDartPartsUnchanged': 12, 'runtimeFilesUnchanged': len(actual), 'negativeChecks': 3, 'nativeGameplay': False, 'steamDeck': False}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidates', type=Path, default=CANDIDATES)
    main(parser.parse_args().candidates)
