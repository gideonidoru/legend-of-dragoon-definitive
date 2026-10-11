#!/usr/bin/env python3
"""Offline authored joint surface; AGPL v3, see LICENSE.

Two corresponded planar collars remain attached to their native rigid frames.
Interior cross-sections rotate without linear-blend collapse. This is an
authoring experiment, not an engine hook, collision solver or skinning format.
"""
import numbers

import numpy as np
from scipy.spatial.transform import Rotation, Slerp


def rigid_pose(value):
    matrix = np.asarray(value, dtype=float)
    if (matrix.shape != (4, 4) or not np.isfinite(matrix).all()
            or not np.array_equal(matrix[3], [0, 0, 0, 1])
            or np.max(np.abs(matrix[:3, :3].T @ matrix[:3, :3] - np.eye(3))) > 1e-8
            or abs(np.linalg.det(matrix[:3, :3]) - 1) > 1e-8):
        raise ValueError('Expected a finite proper rigid pose')
    return matrix.copy()


def placed(points, pose):
    return points @ pose[:3, :3].T + pose[:3, 3]


def ring_area(ring, axis):
    axes = [index for index in range(3) if index != axis]
    points = ring[:, axes]
    return .5 * np.sum(points[:, 0] * np.roll(points[:, 1], -1)
                       - points[:, 1] * np.roll(points[:, 0], -1))


class JointCollarSurface:
    """A bounded open strip between two authored, corresponded local collars.

    Both collars use the same positive longitudinal axis and cyclic vertex order.
    Their poses are world transforms, not hierarchical relative transforms. The
    caller must establish source fidelity and correspondence before construction.
    Endpoint positions are exact. Interior thickness preservation does not prove
    absence of folds, self-intersections or collision with other actor surfaces.
    """
    def __init__(self, first_ring, second_ring, axis, stations=9):
        first = np.asarray(first_ring, dtype=float)
        second = np.asarray(second_ring, dtype=float)
        if (isinstance(axis, (bool, np.bool_)) or not isinstance(axis, numbers.Integral)
                or axis not in (0, 1, 2)
                or isinstance(stations, (bool, np.bool_)) or not isinstance(stations, numbers.Integral)
                or not 3 <= stations <= 33
                or first.ndim != 2 or first.shape[1] != 3 or second.shape != first.shape
                or not 8 <= len(first) <= 128
                or not np.isfinite(first).all() or not np.isfinite(second).all()
                or np.max(np.abs([first, second])) > 1e6):
            raise ValueError('Expected bounded corresponded collar rings')
        centres = np.stack((first.mean(0), second.mean(0)))
        offsets = np.stack((first - centres[0], second - centres[1]))
        areas = np.array([ring_area(ring, axis) for ring in (first, second)])
        if (np.max(np.abs(offsets[:, :, axis])) > 1e-8
                or np.any(areas <= 1e-8)
                or np.any(np.linalg.norm(np.roll(offsets, -1, axis=1) - offsets, axis=2) <= 1e-8)):
            raise ValueError('Collars must be planar, positively wound and nondegenerate')
        self.axis = int(axis)
        self.stations = int(stations)
        self.ring_size = len(first)
        self._rings = np.stack((first, second)).copy()
        self._centres = centres.copy()
        self._offsets = offsets.copy()
        self._areas = areas.copy()
        self._t = np.linspace(0, 1, stations)
        faces = []
        for row in range(stations - 1):
            for column in range(self.ring_size):
                a = row * self.ring_size + column
                b = row * self.ring_size + (column + 1) % self.ring_size
                faces.extend(((a, b, a + self.ring_size),
                              (b, b + self.ring_size, a + self.ring_size)))
        self.faces = np.asarray(faces, dtype=np.int32)
        if axis == 1:
            self.faces = self.faces[:, ::-1].copy()
        for value in (self._rings, self._centres, self._offsets, self._areas, self._t, self.faces):
            value.setflags(write=False)

    def deform(self, first_pose, second_pose):
        first, second = rigid_pose(first_pose), rigid_pose(second_pose)
        rotations = Slerp([0, 1], Rotation.from_matrix(np.stack((first[:3, :3], second[:3, :3]))))(self._t).as_matrix()
        start = placed(self._centres[0], first)
        end = placed(self._centres[1], second)
        length = np.linalg.norm(end - start)
        if length < 1e-8:
            raise ValueError('Collars coincide; author a different joint span')
        # Circular-arc Hermite tangents distribute the bend over the span.
        # Chord/2 tangents bunch cross-sections together at sharp native elbows.
        cosine = np.clip(first[:3, self.axis] @ second[:3, self.axis], -1, 1)
        bend = np.arccos(cosine)
        tangent_length = length / np.cos(bend/4)**2
        tangent_first = first[:3, self.axis] * tangent_length
        tangent_second = second[:3, self.axis] * tangent_length
        t = self._t[:, None]
        centres = ((2*t**3 - 3*t**2 + 1)*start + (t**3 - 2*t**2 + t)*tangent_first
                   + (-2*t**3 + 3*t**2)*end + (t**3 - t**2)*tangent_second)
        offsets = ((1-t[:, :, None])*self._offsets[0]
                   + t[:, :, None]*self._offsets[1])
        target_areas = (1-self._t)*self._areas[0] + self._t*self._areas[1]
        actual_areas = np.array([ring_area(ring, self.axis) for ring in offsets])
        if np.any(actual_areas <= 1e-8):
            raise ValueError('Collar correspondence collapses an interior cross-section')
        offsets *= np.sqrt(target_areas / actual_areas)[:, None, None]
        vertices = np.einsum('rij,rkj->rki', rotations, offsets) + centres[:, None, :]
        # Preserve the supplied collars, including their original centres.
        vertices[0] = placed(self._rings[0], first)
        vertices[-1] = placed(self._rings[1], second)
        if not np.isfinite(vertices).all():
            raise ValueError('Joint deformation produced nonfinite vertices')
        return vertices.reshape(-1, 3)
