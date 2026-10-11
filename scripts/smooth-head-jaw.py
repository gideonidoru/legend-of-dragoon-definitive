#!/usr/bin/env python3
"""Bounded custom mandible relaxation; authoring experiment, AGPL v3.

This preserves topology/UVs and an explicitly authored central mouth/chin
volume. It does not establish anatomical or gameplay acceptance.
"""
import argparse, hashlib, json
from pathlib import Path
import numpy as np
import trimesh
from scipy.sparse import coo_matrix

BASIS = np.array([[0, 1, 0], [0, 0, -1], [-1, 0, 0]])


def smooth(value):
    value = np.clip(value, 0, 1)
    return value * value * (3 - 2 * value)


def sculpt(mesh_path, controls_path, output):
    if output.exists():
        raise ValueError('Use a new output')
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    control = json.loads(controls_path.read_text())
    if control['meshSha256'] != sha(mesh_path):
        raise ValueError('Changed custom mesh')
    if not 1 <= control['iterations'] <= 100 or not 0 < control['strength'] < 1:
        raise ValueError('Invalid relaxation budget')
    mesh = next(iter(trimesh.load(mesh_path, force='scene', process=False).geometry.values()))
    positions = np.asarray(mesh.vertices) @ BASIS.T
    _, first, inverse = np.unique(np.round(positions, 6), axis=0, return_index=True, return_inverse=True)
    vertices = positions[first].copy()
    faces = inverse[np.asarray(mesh.faces)]
    edges = np.vstack([faces[:, [0, 1]], faces[:, [1, 2]], faces[:, [2, 0]]])
    edges = np.unique(np.vstack([edges, edges[:, ::-1]]), axis=0)
    adjacency = coo_matrix((np.ones(len(edges)), (edges[:, 0], edges[:, 1])),
                           shape=(len(vertices), len(vertices))).tocsr()
    degree = np.asarray(adjacency.sum(1)).ravel()
    if np.any(degree == 0):
        raise ValueError('Isolated custom vertex')
    region, protected = control['region'], control['protectedCentralFace']
    weight = (smooth((vertices[:, 1] - region['top']) / region['topFeather']) *
              smooth((region['bottom'] - vertices[:, 1]) / region['bottomFeather']) *
              smooth((region['back'] - vertices[:, 2]) / region['backFeather']) *
              smooth((region['halfWidth'] - np.abs(vertices[:, 0])) / region['sideFeather']))
    pinned = (smooth((protected['halfWidth'] - np.abs(vertices[:, 0])) / protected['sideFeather']) *
              smooth((protected['back'] - vertices[:, 2]) / protected['backFeather']) *
              smooth((protected['bottom'] - vertices[:, 1]) / protected['bottomFeather']))
    weight *= 1 - pinned
    before = vertices.copy()
    for _ in range(control['iterations']):
        average = (adjacency @ vertices) / degree[:, None]
        vertices += (average - vertices) * weight[:, None] * control['strength']
    raw = vertices[inverse] @ BASIS
    triangles = raw[np.asarray(mesh.faces)]
    cross = np.cross(triangles[:, 1] - triangles[:, 0], triangles[:, 2] - triangles[:, 0])
    if not np.isfinite(raw).all() or np.any(np.linalg.norm(cross, axis=1) < 1e-10):
        raise ValueError('Nonfinite or collapsed custom triangle')
    sums = np.zeros((len(vertices), 3))
    for index in range(3):
        np.add.at(sums, faces[:, index], cross)
    normals = sums[inverse]
    length = np.linalg.norm(normals, axis=1)
    normals[length < 1e-10] = mesh.vertex_normals[length < 1e-10]
    normals /= np.maximum(np.linalg.norm(normals, axis=1)[:, None], 1e-10)
    mesh.vertices = raw
    mesh.vertex_normals = normals
    output.mkdir(parents=True)
    mesh.export(output / 'head-jaw-smoothed.glb')
    receipt = {'state': 'experimental-not-accepted', 'inputSha256': sha(mesh_path),
               'controlsSha256': sha(controls_path), 'sculptorSha256': sha(Path(__file__)),
               'triangles': len(faces), 'selectedVertices': int((weight > .01).sum()),
               'maximumDisplacement': float(np.linalg.norm(vertices - before, axis=1).max()),
               'topologyChanged': False, 'uvChanged': False, 'accepted': False}
    (output / 'sculpt-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('mesh', 'controls', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(sculpt(args.mesh, args.controls, args.output)))
