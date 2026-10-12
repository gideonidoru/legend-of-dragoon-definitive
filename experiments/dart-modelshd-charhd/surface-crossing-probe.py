#!/usr/bin/env python3
"""Bounded proper noncoplanar crossing diagnostic. AGPL v3; see LICENSE.

Excludes triangles sharing a mesh vertex, coplanar overlap and endpoint/edge
contacts. A zero result is not comprehensive collision or self-intersection
validation. Intended for small offline arm studies, never runtime collision.
"""
import numpy as np


def proper_crossing_pairs(vertices, first_faces, second_faces):
    vertices = np.asarray(vertices, dtype=float)
    first_faces, second_faces = np.asarray(first_faces), np.asarray(second_faces)
    if (vertices.ndim != 2 or vertices.shape[1] != 3 or not np.isfinite(vertices).all()
            or np.max(np.abs(vertices), initial=0) > 1e6):
        raise ValueError('Expected bounded finite vertices')
    for faces in (first_faces, second_faces):
        if (faces.ndim != 2 or faces.shape[1] != 3 or not np.issubdtype(faces.dtype, np.integer)
                or (len(faces) and (faces.min() < 0 or faces.max() >= len(vertices)))):
            raise ValueError('Expected valid triangle references')
    if len(first_faces)*len(second_faces) > 1_000_000:
        raise ValueError('Crossing probe exceeds the offline pair budget')
    if not len(first_faces) or not len(second_faces):
        return []
    first, second = vertices[first_faces], vertices[second_faces]
    low = np.maximum(first.min(1)[:, None], second.min(1)[None])
    high = np.minimum(first.max(1)[:, None], second.max(1)[None])
    ii, jj = np.nonzero((high-low >= -1e-7).all(2))
    shared = (first_faces[ii, :, None] == second_faces[jj, None, :]).any((1, 2))
    ii, jj = ii[~shared], jj[~shared]
    first, second = first[ii], second[jj]
    found = set()
    for source, target in ((first, second), (second, first)):
        for edge in range(3):
            start = source[:, edge]
            direction = source[:, (edge+1)%3]-start
            e1, e2 = target[:, 1]-target[:, 0], target[:, 2]-target[:, 0]
            h = np.cross(direction, e2)
            determinant = np.einsum('ij,ij->i', e1, h)
            # The determinant is scale-dependent. An absolute threshold counts
            # nearly parallel shared-edge contacts as crossings at battle scale.
            scale = np.linalg.norm(direction, axis=1)*np.linalg.norm(e1, axis=1)*np.linalg.norm(e2, axis=1)
            valid = np.abs(determinant) > 1e-8*scale
            inverse = np.divide(1., determinant, out=np.zeros_like(determinant), where=valid)
            offset = start-target[:, 0]
            u = inverse*np.einsum('ij,ij->i', offset, h)
            q = np.cross(offset, e1)
            v = inverse*np.einsum('ij,ij->i', direction, q)
            t = inverse*np.einsum('ij,ij->i', e2, q)
            proper = valid & (u > 1e-6) & (v > 1e-6) & (u+v < 1-1e-6) & (t > 1e-6) & (t < 1-1e-6)
            found.update(zip(ii[proper].tolist(), jj[proper].tolist()))
    return sorted(found)
