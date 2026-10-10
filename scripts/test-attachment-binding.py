#!/usr/bin/env python3
"""Original attachment fixtures; no retail models, weights or renderer."""
import importlib.util
from pathlib import Path
import unittest
import numpy as np

spec = importlib.util.spec_from_file_location('binding', Path(__file__).with_name('bind-rigid-attachments.py'))
binding = importlib.util.module_from_spec(spec); spec.loader.exec_module(binding)


def matrix(angle, translation=(0, 0, 0)):
    c, s = np.cos(angle), np.sin(angle)
    value = np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=float)
    value[:3, 3] = translation
    return value


def fixture():
    return np.array([[9., 2, 3], [11., 2, 3], [9., 4, 3], [11., 4, 3], [9., 8, 3], [11., 8, 3]])


class AttachmentBindingTest(unittest.TestCase):
    def test_bind_pose_stays_exact_with_nonzero_pivots(self):
        v = fixture(); a = matrix(.3, [20, 40, -10]); s = matrix(-.6, [5, 10, 15])
        part = binding.RigidAttachment(v, [1, 1, .5, .5, 0, 0], [0, 1], a, s)
        np.testing.assert_allclose(part.deform(a, s), binding.placed(v, s), atol=1e-12)

    def test_roots_follow_anchor_and_tail_keeps_its_relative_rotation(self):
        v = fixture(); part = binding.RigidAttachment(v, [1, 1, .5, .5, 0, 0], [0, 1], np.eye(4), np.eye(4))
        a = matrix(np.pi/2, [100, 20, 30]); s = matrix(-.4, [-1000, 500, 80])
        out = part.deform(a, s)
        np.testing.assert_allclose(out[:2], binding.placed(v[:2], a), atol=1e-12)
        np.testing.assert_allclose(out[5]-out[4], (v[5]-v[4]) @ s[:3, :3].T, atol=1e-12)
        self.assertAlmostEqual(np.linalg.norm(out[5]-out[4]), 2)

    def test_swing_translation_is_replaced_by_the_anchor_centroid(self):
        v = fixture(); part = binding.RigidAttachment(v, [1, 1, 0, 0, 0, 0], [0, 1], np.eye(4), np.eye(4))
        a = matrix(.2, [100, -50, 30]); first = matrix(.5, [1, 2, 3]); second = matrix(.5, [-999, 888, 777])
        np.testing.assert_array_equal(part.deform(a, first), part.deform(a, second))
        np.testing.assert_allclose(part.root_targets(a), binding.placed(v[:2], a), atol=1e-12)

    def test_fixed_random_poses_preserve_boundary_and_tail_distances(self):
        v = fixture(); part = binding.RigidAttachment(v, [1, 1, .3, .3, 0, 0], [0, 1], np.eye(4), np.eye(4))
        random = np.random.default_rng(12)
        for _ in range(80):
            a = matrix(random.uniform(-np.pi, np.pi), random.uniform(-1e4, 1e4, 3))
            s = matrix(random.uniform(-np.pi, np.pi), random.uniform(-1e4, 1e4, 3))
            out = part.deform(a, s)
            np.testing.assert_allclose(out[:2], part.root_targets(a), atol=1e-11)
            self.assertAlmostEqual(np.linalg.norm(out[5]-out[4]), 2, places=10)

    def test_external_input_and_output_mutation_cannot_change_binding(self):
        v = fixture(); weights = np.array([1, 1, .5, .5, 0, 0]); a = np.eye(4); s = np.eye(4)
        part = binding.RigidAttachment(v, weights, [0, 1], a, s); expected = part.deform(np.eye(4), np.eye(4))
        v[:] = 999; weights[:] = 0; a[:] = 0; s[:] = 0
        out = part.deform(np.eye(4), np.eye(4)); out[:] = -999
        np.testing.assert_array_equal(part.deform(np.eye(4), np.eye(4)), expected)

    def test_bad_geometry_weights_and_root_indices_are_rejected(self):
        for vertices, weights, roots in [(fixture(), [1]*5, [0]), (fixture(), [1,1,2,0,0,0], [0]),
                                          (fixture(), [0]*6, [0]), (fixture(), [1]*6, [0,0]),
                                          (fixture(), [1]*6, [True]), (fixture(), [1]*6, [6]),
                                          (fixture(), [1]*6, []), (np.zeros((4097,3)), np.ones(4097), [0]),
                                          (np.full((6,3), np.nan), [1]*6, [0])]:
            with self.assertRaises(ValueError): binding.RigidAttachment(vertices, weights, roots, np.eye(4), np.eye(4))

    def test_scaled_reflected_sheared_nonfinite_and_projective_poses_are_rejected(self):
        part = binding.RigidAttachment(fixture(), [1]*6, [0], np.eye(4), np.eye(4))
        for position, value in [((0,0), 2), ((0,0), -1), ((0,1), .1), ((0,3), np.nan), ((3,0), .1)]:
            bad = np.eye(4); bad[position] = value
            with self.assertRaises(ValueError): part.deform(bad, np.eye(4))
            with self.assertRaises(ValueError): part.deform(np.eye(4), bad)


class TwoBoneSurfaceTest(unittest.TestCase):
    def test_known_endpoint_and_blended_positions_with_distinct_bind_frames(self):
        vertices = np.tile([12., 3, 0], (3, 1))
        surface = binding.TwoBoneSurface(vertices, [[1, 0], [0, 1], [.25, .75]],
                                        matrix(0, [10, 0, 0]), matrix(np.pi/2, [0, 20, 0]))
        np.testing.assert_allclose(surface.deform(matrix(0, [20, 0, 0]), matrix(np.pi/2, [0, 25, 0])),
                                   [[22, 3, 0], [12, 8, 0], [14.5, 6.75, 0]], atol=1e-12)
        np.testing.assert_allclose(surface.deform(matrix(np.pi/2, [20, 0, 0]), matrix(0, [0, 25, 0])),
                                   [[17, 2, 0], [-17, 13, 0], [-8.5, 10.25, 0]], atol=1e-12)

    def test_float32_bind_roundoff_does_not_move_the_rest_surface(self):
        vertices = fixture(); first = matrix(.31, [20, 40, -10]).astype(np.float32)
        second = matrix(-.61, [5, 10, 15]).astype(np.float32)
        surface = binding.TwoBoneSurface(vertices, np.tile([.37, .63], (len(vertices), 1)), first, second)
        np.testing.assert_allclose(surface.deform(first, second), vertices, atol=1e-12)

    def test_external_mutation_cannot_change_surface(self):
        vertices = fixture(); weights = np.tile([.25, .75], (len(vertices), 1)); first = matrix(.4); second = matrix(-.3)
        surface = binding.TwoBoneSurface(vertices, weights, first, second)
        before = surface.deform(np.eye(4), np.eye(4))
        vertices[:] = 0; weights[:] = 0; first[:] = 0; second[:] = 0
        surface.deform(np.eye(4), np.eye(4))[:] = 999
        np.testing.assert_array_equal(surface.deform(np.eye(4), np.eye(4)), before)

    def test_invalid_geometry_and_nonconvex_or_unnormalized_weights_fail(self):
        for vertices, weights in [(fixture(), np.ones((6, 2))), (fixture(), np.zeros((6, 2))),
                                  (fixture(), [[1, 0]] * 5), (fixture(), [[1.1, -.1]] * 6),
                                  (fixture(), [[np.nan, 0]] * 6), (np.zeros((4097, 3)), [[1, 0]] * 4097),
                                  (np.zeros((0, 3)), np.zeros((0, 2))), (np.full((6, 3), np.inf), [[1, 0]] * 6)]:
            with self.assertRaises(ValueError): binding.TwoBoneSurface(vertices, weights, np.eye(4), np.eye(4))

    def test_bad_bind_and_pose_matrices_fail_before_deformation(self):
        vertices = fixture(); weights = np.tile([.5, .5], (6, 1))
        surface = binding.TwoBoneSurface(vertices, weights, np.eye(4), np.eye(4))
        for position, value in [((0, 0), 2), ((0, 0), -1), ((0, 1), .1), ((0, 3), np.nan), ((3, 0), .1)]:
            bad = np.eye(4); bad[position] = value
            with self.assertRaises(ValueError): binding.TwoBoneSurface(vertices, weights, bad, np.eye(4))
            with self.assertRaises(ValueError): binding.TwoBoneSurface(vertices, weights, np.eye(4), bad)
            with self.assertRaises(ValueError): surface.deform(bad, np.eye(4))
            with self.assertRaises(ValueError): surface.deform(np.eye(4), bad)


if __name__ == '__main__': unittest.main()
