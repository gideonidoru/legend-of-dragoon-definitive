#!/usr/bin/env python3
"""Verify the public complete Dart review payload; no original data. AGPL v3."""
from pathlib import Path
import hashlib
import json
import numpy as np
import trimesh
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT/'integrations/charhd/production/party-reconstruction/dart/complete-model-v1'
receipt = json.loads((ASSET/'build-receipt.json').read_text())
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert receipt['builderSha256'] == digest(Path(__file__).with_name('build-complete-dart.py'))
assert receipt['skinningSha256'] == digest(Path(__file__).with_name('continuous-rig.py'))
assert receipt['inputSha256'] == digest(ASSET/'body-rest-source.glb')
assert receipt['earlierSculptSha256'] == digest(ASSET.parent/'original-design-v3/body-sculpt-v1.glb')
assert not receipt['nativeAnimation']
assert receipt['status'] == 'complete-actor-review-candidate-not-runtime-selected'
skin = np.load(ASSET/'authoring-skin.npz', allow_pickle=False)
atlas = np.asarray(Image.open(ASSET/'body-basecolour.png').convert('RGB'))
assert skin['weights'].shape == (len(skin['vertices']), 15)
assert np.isfinite(skin['weights']).all() and np.min(skin['weights']) >= 0
assert np.allclose(skin['weights'].sum(1), 1, atol=1e-7)
assert len(skin['faces']) == receipt['bodyTriangles']
unique, inverse = np.unique(skin['vertices'], axis=0, return_inverse=True)
for index in range(len(unique)):
    group = skin['weights'][inverse == index]
    assert np.array_equal(group, np.broadcast_to(group[0], group.shape)), 'UV seam skin mismatch'
head_controls = None
body_topology = None
for row in receipt['poses']:
    path = ASSET/'viewer'/row['file']
    assert digest(path) == row['sha256'], row['file']
    actor = trimesh.load(path, force='scene', process=False)
    assert sum(len(mesh.faces) for mesh in actor.geometry.values()) == row['triangles']
    topology = {}
    for name, mesh in actor.geometry.items():
        assert np.isfinite(mesh.vertices).all() and np.isfinite(mesh.vertex_normals).all()
        assert np.min(mesh.area_faces) > 1e-12, (row['pose'], name, 'zero-area triangle')
        assert mesh.visual.kind == 'texture', name
        assert np.isfinite(mesh.visual.uv).all() and np.min(mesh.visual.uv) >= 0 and np.max(mesh.visual.uv) <= 1
        if name in ('red-armor', 'cloth-leather-skin'):
            topology[name] = (mesh.faces, mesh.visual.uv)
            assert mesh.visual.material.baseColorTexture.size == (2048, 2048)
            assert np.array_equal(np.asarray(mesh.visual.material.baseColorTexture.convert('RGB')), atlas)
    if body_topology is None:
        body_topology = topology
    else:
        for name, (faces, uv) in topology.items():
            assert np.array_equal(faces, body_topology[name][0]) and np.array_equal(uv, body_topology[name][1])
    head = actor.geometry['accepted-charhd-head']
    if head_controls is None:
        head_controls = (head.vertices, head.faces)
    else:
        assert np.array_equal(head.vertices, head_controls[0]) and np.array_equal(head.faces, head_controls[1])
    assert len(head.faces) == receipt['headTriangles'] == 5748
    dots = np.einsum('ij,ij->i', head.face_normals, head.vertex_normals[head.faces].mean(1))
    assert np.min(dots) > 0, 'Head winding differs from authored normals'
print(json.dumps({'posesVerified': len(receipt['poses']), 'maximumTriangles': max(r['triangles'] for r in receipt['poses']),
                  'continuousBodyTriangles': len(skin['faces']), 'headTriangles': len(head_controls[1]),
                  'weldedSkinGroups': len(unique), 'nativeGameplay': False, 'steamDeckValidated': False}))
