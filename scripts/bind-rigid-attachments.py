#!/usr/bin/env python3
"""Bounded offline two-transform attachment study, AGPL v3; see LICENSE.
Positions only: not a native mesh loader, normal generator or collision solver.
"""
import numbers
import numpy as np


def rigid_matrix(values):
    try:
        matrix = np.asarray(values, dtype=float)
    except (TypeError, ValueError) as error:
        raise ValueError('Expected a finite rigid 4x4 transform') from error
    if (matrix.shape != (4, 4) or not np.isfinite(matrix).all()
            or not np.array_equal(matrix[3], [0, 0, 0, 1])
            or np.max(np.abs(matrix[:3, :3].T @ matrix[:3, :3] - np.eye(3))) > 1e-5
            or abs(np.linalg.det(matrix[:3, :3]) - 1) > 1e-5):
        raise ValueError('Expected a proper rigid transform without scale, reflection or shear')
    return matrix.copy()


def placed(vertices, matrix):
    return vertices @ matrix[:3, :3].T + matrix[:3, 3]


class RigidAttachment:
    """An immutable rest binding; roots follow the anchor, tails retain swing rotation.

    Both input poses share one coordinate basis. Anchor/swing bind poses describe
    the rest mesh. All root vertices require weight 1. A weight-0 tail undergoes
    its original swing rotation translated to the moving anchor-centroid.
    Geometry, UVs, normals and all native/GPU ownership remain caller concerns.
    """
    def __init__(self, vertices, weights, roots, anchor_bind, swing_bind):
        vertices = np.asarray(vertices, dtype=float)
        weights = np.asarray(weights, dtype=float)
        if (vertices.ndim != 2 or vertices.shape[1] != 3 or not 1 <= len(vertices) <= 4096
                or not np.isfinite(vertices).all() or np.max(np.abs(vertices)) > 1e6
                or weights.shape != (len(vertices),) or not np.isfinite(weights).all()
                or np.any(weights < 0) or np.any(weights > 1)):
            raise ValueError('Expected bounded finite vertices and convex attachment weights')
        roots = tuple(roots)
        if (not roots or len(roots) > len(vertices)
                or any(isinstance(i, (bool, np.bool_)) or not isinstance(i, numbers.Integral) or i < 0 or i >= len(vertices) for i in roots)
                or len(set(roots)) != len(roots) or np.any(weights[list(roots)] != 1)):
            raise ValueError('Root indices must be distinct and fully attached')
        anchor_bind, swing_bind = rigid_matrix(anchor_bind), rigid_matrix(swing_bind)
        self._vertices = vertices.copy()
        self._weights = weights[:, None].copy()
        self._roots = np.asarray(roots, dtype=int)
        self._root = vertices[list(roots)].mean(axis=0)
        # Invert the actual bind matrix, including float32 rotation roundoff.
        inverse = np.linalg.inv(anchor_bind)
        self._anchor_vertices = placed(placed(vertices, swing_bind), inverse)
        self._anchor_root = self._anchor_vertices[list(roots)].mean(axis=0)
        for value in (self._vertices, self._weights, self._roots, self._root,
                      self._anchor_vertices, self._anchor_root):
            value.setflags(write=False)

    def deform(self, anchor_pose, swing_pose):
        anchor_pose, swing_pose = rigid_matrix(anchor_pose), rigid_matrix(swing_pose)
        anchored = placed(self._anchor_vertices, anchor_pose)
        centre = placed(self._anchor_root, anchor_pose)
        translation = centre - self._root @ swing_pose[:3, :3].T
        swung = self._vertices @ swing_pose[:3, :3].T + translation
        result = self._weights * anchored + (1 - self._weights) * swung
        if not np.isfinite(result).all():
            raise ValueError('Attachment deformation produced nonfinite positions')
        return result

    def root_targets(self, anchor_pose):
        return placed(self._anchor_vertices[self._roots], rigid_matrix(anchor_pose))


class TwoBoneSurface:
    """An offline surface in a shared bind space with two convex bone weights.

    Fully weighted endpoint rings follow their original bones exactly. Interior
    vertices blend the two actual bind-to-pose transforms, including translation.
    This does not preserve volume at bends or establish collision freedom.
    Normals, joint topology, native ownership and GPU upload remain caller work.
    """
    def __init__(self, bind_vertices, weights, first_bind, second_bind):
        vertices = np.asarray(bind_vertices, dtype=float)
        weights = np.asarray(weights, dtype=float)
        if (vertices.ndim != 2 or vertices.shape[1] != 3 or not 1 <= len(vertices) <= 4096
                or not np.isfinite(vertices).all() or np.max(np.abs(vertices)) > 1e6
                or weights.shape != (len(vertices), 2) or not np.isfinite(weights).all()
                or np.any(weights < 0) or np.any(weights > 1)
                or np.max(np.abs(weights.sum(axis=1) - 1)) > 1e-12):
            raise ValueError('Expected bounded bind vertices and normalized convex two-bone weights')
        first_bind, second_bind = rigid_matrix(first_bind), rigid_matrix(second_bind)
        self._first = placed(vertices, np.linalg.inv(first_bind))
        self._second = placed(vertices, np.linalg.inv(second_bind))
        self._weights = weights.copy() / weights.sum(axis=1)[:, None]
        for value in (self._first, self._second, self._weights):
            value.setflags(write=False)

    def deform(self, first_pose, second_pose):
        first = placed(self._first, rigid_matrix(first_pose))
        second = placed(self._second, rigid_matrix(second_pose))
        result = self._weights[:, :1] * first + self._weights[:, 1:] * second
        if not np.isfinite(result).all():
            raise ValueError('Two-bone deformation produced nonfinite positions')
        return result
