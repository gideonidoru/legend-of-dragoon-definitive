#!/usr/bin/env python3
"""Independent authored fixtures; no game files. AGPL v3, see LICENSE."""
import importlib.util
from pathlib import Path
import unittest

import numpy as np
from scipy.spatial.transform import Rotation


spec = importlib.util.spec_from_file_location('collars', Path(__file__).with_name('joint-collar-surface.py'))
collars = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collars)


def ring(x, radius=2):
    angle = np.linspace(0, 2*np.pi, 32, endpoint=False)
    return np.column_stack((np.full(32, x), radius*np.cos(angle), radius*np.sin(angle)))


def pose(rotation=(0, 0, 0), translation=(0, 0, 0)):
    matrix = np.eye(4)
    matrix[:3, :3] = Rotation.from_rotvec(rotation).as_matrix()
    matrix[:3, 3] = translation
    return matrix


class JointCollarTest(unittest.TestCase):
    def test_half_turn_does_not_collapse_the_middle_cross_section(self):
        surface = collars.JointCollarSurface(ring(0), ring(10), axis=0)
        out = surface.deform(np.eye(4), pose((np.pi, 0, 0))).reshape(9, 32, 3)
        radius = np.linalg.norm(out[4] - out[4].mean(0), axis=1)
        np.testing.assert_allclose(radius, 2, atol=1e-12)

    def test_cross_section_area_stays_between_authored_endpoint_areas(self):
        surface = collars.JointCollarSurface(ring(0, 2), ring(10, 3), axis=0)
        out = surface.deform(np.eye(4), pose((1.7, 0, 0))).reshape(9, 32, 3)
        areas = []
        for points in out:
            vectors = points - points.mean(0)
            areas.append(.5*np.linalg.norm(np.cross(vectors, np.roll(vectors, -1, axis=0)).sum(0)))
        np.testing.assert_allclose(areas, np.linspace(collars.ring_area(ring(0, 2), 0), collars.ring_area(ring(10, 3), 0), 9), atol=1e-12)

    def test_distinct_native_frames_preserve_exact_endpoint_positions(self):
        first, second = ring(3), ring(-4, 3)
        surface = collars.JointCollarSurface(first, second, axis=0)
        a, b = pose((.2, -.6, 1.3), (120, 80, -40)), pose((-.9, .1, -.7), (-100, 50, 200))
        out = surface.deform(a, b).reshape(9, 32, 3)
        np.testing.assert_array_equal(out[0], collars.placed(first, a))
        np.testing.assert_array_equal(out[-1], collars.placed(second, b))

    def test_common_world_motion_moves_the_complete_surface_rigidly(self):
        surface = collars.JointCollarSurface(ring(3), ring(-4), axis=0)
        a, b = pose((.1, .3, -.2), (0, 3, 2)), pose((.2, -.4, .3), (20, -6, 5))
        world = pose((-.3, .6, .8), (90, -100, 20))
        np.testing.assert_allclose(surface.deform(world @ a, world @ b), collars.placed(surface.deform(a, b), world), atol=1e-12)

    def test_strip_has_only_two_boundary_rings_and_no_nonmanifold_edges(self):
        surface = collars.JointCollarSurface(ring(0), ring(10), axis=0)
        faces = surface.faces
        edges = np.sort(np.concatenate((faces[:, [0, 1]], faces[:, [1, 2]], faces[:, [2, 0]])), axis=1)
        _, counts = np.unique(edges, axis=0, return_counts=True)
        self.assertEqual(np.sum(counts == 1), 64)
        self.assertTrue(np.all(counts <= 2))

    def test_each_longitudinal_axis_has_outward_face_winding(self):
        for axis in range(3):
            order = [axis] + [index for index in range(3) if index != axis]
            first, second = np.empty((32, 3)), np.empty((32, 3))
            first[:, order] = ring(0); second[:, order] = ring(10)
            surface = collars.JointCollarSurface(first, second, axis)
            vertices = surface.deform(np.eye(4), np.eye(4))
            triangles = vertices[surface.faces]
            normals = np.cross(triangles[:, 1]-triangles[:, 0], triangles[:, 2]-triangles[:, 0])
            outward = triangles.mean(1); outward[:, axis] = 0
            self.assertTrue(np.all(np.einsum('ij,ij->i', normals, outward) > 0))

    def test_sharp_bend_distributes_sections_without_inward_facing_triangles(self):
        # Hand-authored 120-degree elbow, not an original game pose.
        surface = collars.JointCollarSurface(ring(0), ring(0), axis=0)
        bend = pose((0, 0, 2*np.pi/3), (2.5, 5*np.sqrt(3)/2, 0))
        vertices = surface.deform(np.eye(4), bend)
        rings = vertices.reshape(9, 32, 3)
        centres = rings.mean(1)
        triangles = vertices[surface.faces]
        normals = np.cross(triangles[:, 1]-triangles[:, 0], triangles[:, 2]-triangles[:, 0])
        face_centres = centres[(surface.faces // 32)].mean(1)
        outward = triangles.mean(1) - face_centres
        self.assertTrue(np.all(np.einsum('ij,ij->i', normals, outward) > 0))

    def test_input_output_and_face_mutation_cannot_change_the_binding(self):
        first, second = ring(0), ring(10)
        surface = collars.JointCollarSurface(first, second, axis=0)
        expected = surface.deform(np.eye(4), np.eye(4))
        first[:] = 0; second[:] = 99
        surface.deform(np.eye(4), np.eye(4))[:] = -99
        np.testing.assert_array_equal(surface.deform(np.eye(4), np.eye(4)), expected)
        with self.assertRaises(ValueError): surface.faces[0] = 0

    def test_bad_geometry_correspondence_or_density_is_rejected(self):
        for first, second, axis, stations in [(ring(0), ring(10)[::-1], 0, 9),
                                             (ring(0)[:7], ring(10)[:7], 0, 9),
                                             (ring(0), ring(10), True, 9),
                                             (ring(0), ring(10), 0, 2),
                                             (ring(0), ring(10), 0, 99),
                                             (ring(0), np.full((32, 3), np.nan), 0, 9)]:
            with self.assertRaises(ValueError): collars.JointCollarSurface(first, second, axis, stations)
        bad = ring(0); bad[0, 0] = .1
        with self.assertRaises(ValueError): collars.JointCollarSurface(bad, ring(10), 0)

    def test_nonrigid_poses_and_coincident_collars_fail_closed(self):
        surface = collars.JointCollarSurface(ring(0), ring(10), axis=0)
        for index, value in [((0, 0), 2), ((0, 0), -1), ((0, 1), .1), ((0, 3), np.nan), ((3, 0), .1)]:
            bad = np.eye(4); bad[index] = value
            with self.assertRaises(ValueError): surface.deform(bad, np.eye(4))
            with self.assertRaises(ValueError): surface.deform(np.eye(4), bad)
        with self.assertRaises(ValueError): surface.deform(np.eye(4), pose(translation=(-10, 0, 0)))


if __name__ == '__main__':
    unittest.main()
