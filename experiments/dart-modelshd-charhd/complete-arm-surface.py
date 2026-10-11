#!/usr/bin/env python3
"""Source-guided complete arm authoring experiment. AGPL v3; see LICENSE.

Retained rigid surfaces and an articulated elbow share welded cut vertices.
Original detached trim stays rigid. This is an offline authoring representation,
not a game format or a claim that all source attachment borders are closed.
"""
import importlib.util
import numbers
from pathlib import Path

import numpy as np

spec = importlib.util.spec_from_file_location('complete_arm_collar', Path(__file__).with_name('joint-collar-surface.py'))
collars = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collars)


def mesh_inputs(vertices, faces):
    vertices = np.asarray(vertices, dtype=float)
    faces = np.asarray(faces)
    if (vertices.ndim != 2 or vertices.shape[1] != 3 or not np.isfinite(vertices).all()
            or np.max(np.abs(vertices), initial=0) > 1e6
            or faces.ndim != 2 or faces.shape[1] != 3
            or not np.issubdtype(faces.dtype, np.integer)
            or not len(faces) or faces.min() < 0 or faces.max() >= len(vertices)):
        raise ValueError('Expected finite bounded triangulated surfaces')
    return vertices.copy(), faces.astype(np.int32, copy=True)


def clip_to_ring(vertices, faces, axis, station, keep_lower, ring):
    """Clip triangles, splitting every cut edge at the supplied collar vertices.

    Per-corner barycentric lineage is retained against the original triangles.
    A polygon-centre fan retains collinear edge insertions without zero-area
    triangles. Only the cut is welded; separate source trim is not filled in.
    """
    vertices, faces = mesh_inputs(vertices, faces)
    ring = np.asarray(ring, dtype=float)
    if (isinstance(axis, (bool, np.bool_)) or not isinstance(axis, numbers.Integral)
            or axis not in (0, 1, 2) or not isinstance(keep_lower, (bool, np.bool_))
            or not isinstance(station, numbers.Real) or not np.isfinite(station)
            or ring.ndim != 2 or ring.shape[1] != 3 or not len(ring)
            or not np.isfinite(ring).all() or np.max(np.abs(ring[:, axis]-station)) > 1e-8):
        raise ValueError('Expected a finite planar cut ring')
    output, triangles, lineage, lookup = [], [], [], {}

    def vertex(point):
        key = tuple(np.round(point, 8))
        if key not in lookup:
            lookup[key] = len(output)
            output.append(point.copy())
        return lookup[key]

    for source_index, refs in enumerate(faces):
        polygon = [(point, weight) for point, weight in zip(vertices[refs], np.eye(3))]
        clipped = []
        for first, second in zip(polygon, polygon[1:]+polygon[:1]):
            da, db = first[0][axis]-station, second[0][axis]-station
            a_in = da <= 0 if keep_lower else da >= 0
            b_in = db <= 0 if keep_lower else db >= 0
            if a_in:
                clipped.append(first)
            if a_in != b_in:
                t = da/(da-db)
                point = (1-t)*first[0]+t*second[0]; point[axis] = station
                clipped.append((point, (1-t)*first[1]+t*second[1]))
        # Eliminate repeated points caused by vertices exactly on the cut plane.
        clipped = [item for i, item in enumerate(clipped)
                   if np.linalg.norm(item[0]-clipped[i-1][0]) > 1e-9]
        if len(clipped) < 3:
            continue
        expanded = []
        for first, second in zip(clipped, clipped[1:]+clipped[:1]):
            expanded.append(first)
            if abs(first[0][axis]-station) > 1e-8 or abs(second[0][axis]-station) > 1e-8:
                continue
            edge = second[0]-first[0]
            square = edge@edge
            if square <= 1e-18:
                continue
            t = (ring-first[0])@edge/square
            errors = np.linalg.norm(ring-(first[0]+t[:, None]*edge), axis=1)
            insert = np.flatnonzero((t > 1e-8) & (t < 1-1e-8) & (errors < 1e-6))
            for index in insert[np.argsort(t[insert])]:
                expanded.append((ring[index].copy(), (1-t[index])*first[1]+t[index]*second[1]))
        if len(expanded) == 3:
            children = [expanded]
        else:
            centre = (np.mean([p for p, _ in expanded], axis=0), np.mean([w for _, w in expanded], axis=0))
            children = [[centre, first, second] for first, second in zip(expanded, expanded[1:]+expanded[:1])]
        for child in children:
            points = np.array([item[0] for item in child])
            if np.linalg.norm(np.cross(points[1]-points[0], points[2]-points[0])) <= 1e-10:
                continue
            triangles.append([vertex(point) for point in points])
            lineage.append({'sourceTriangle': source_index, 'weights': [item[1].tolist() for item in child]})
    if not output:
        raise ValueError('Cut retained no surface')
    output = np.asarray(output)
    indices = []
    for point in ring:
        distances = np.linalg.norm(output-point, axis=1)
        index = int(distances.argmin())
        if distances[index] > 1e-6:
            raise ValueError('Collar does not lie on the clipped surface boundary')
        indices.append(index)
    return output, np.asarray(triangles, dtype=np.int32), np.asarray(indices), lineage


class CompleteArmSurface:
    """Two retained rigid shells welded to one independently moving elbow."""
    def __init__(self, upper, lower, first_ring, second_ring, axis):
        first_ring, second_ring = np.asarray(first_ring, float), np.asarray(second_ring, float)
        self.collar = collars.JointCollarSurface(first_ring, second_ring, axis)
        self.upper, upper_faces, upper_ring, self.upper_lineage = clip_to_ring(
            *upper, axis, float(first_ring[0, axis]), True, first_ring)
        self.lower, lower_faces, lower_ring, self.lower_lineage = clip_to_ring(
            *lower, axis, float(second_ring[0, axis]), False, second_ring)
        self.rigid_count = len(self.upper)+len(self.lower)
        n = self.collar.ring_size
        mapping = np.concatenate((upper_ring,
                                  np.arange(self.rigid_count, self.rigid_count+(self.collar.stations-2)*n),
                                  lower_ring+len(self.upper)))
        self.faces = np.concatenate((upper_faces, lower_faces+len(self.upper), mapping[self.collar.faces]))
        self.face_regions = np.concatenate((np.zeros(len(upper_faces), int), np.ones(len(lower_faces), int),
                                            np.full(len(self.collar.faces), 2, int)))
        self.collar_map = mapping
        self.upper_ring, self.lower_ring = upper_ring, lower_ring
        # Every cut edge must have exactly two owners after joining. A resampled
        # ring without the native contour corners would leave hidden T-junctions.
        counts = {}
        for face in self.faces:
            for a, b in zip(face, np.roll(face, -1)):
                edge = tuple(sorted((int(a), int(b))))
                counts[edge] = counts.get(edge, 0)+1
        for ring in (upper_ring, lower_ring+len(self.upper)):
            if any(counts.get(tuple(sorted((int(a), int(b)))), 0) != 2 for a, b in zip(ring, np.roll(ring, -1))):
                raise ValueError('Complete arm has an unwelded or nonmanifold collar edge')
        for value in (self.upper, self.lower, self.faces, self.face_regions, self.collar_map, self.upper_ring, self.lower_ring):
            value.setflags(write=False)

    def deform(self, upper_pose, lower_pose):
        upper_pose, lower_pose = collars.rigid_pose(upper_pose), collars.rigid_pose(lower_pose)
        strip = self.collar.deform(upper_pose, lower_pose)
        n = self.collar.ring_size
        result = np.concatenate((collars.placed(self.upper, upper_pose), collars.placed(self.lower, lower_pose), strip[n:-n]))
        error = np.linalg.norm(result[self.collar_map]-strip, axis=1).max()
        if error > 1e-6:
            raise ValueError('Joined arm moved away from its collar')
        return result
