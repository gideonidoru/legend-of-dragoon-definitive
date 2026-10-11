#!/usr/bin/env python3
"""Authored interpolation fixtures; no game files. AGPL v3, see LICENSE."""
import importlib.util
from pathlib import Path
import unittest

import numpy as np
from scipy.spatial.transform import Rotation


spec = importlib.util.spec_from_file_location('probe', Path(__file__).with_name('probe-native-collars.py'))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class CollarProbeTest(unittest.TestCase):
    def test_midpoint_uses_shortest_rotation_arc_and_linear_translation(self):
        first, second = np.eye(4), np.eye(4)
        second[:3, :3] = Rotation.from_euler('z', 240, degrees=True).as_matrix()
        second[:3, 3] = [10, -6, 4]
        out = probe.intermediate_pose(first, second, .5)
        np.testing.assert_allclose(out[:3, :3], Rotation.from_euler('z', -60, degrees=True).as_matrix(), atol=1e-12)
        np.testing.assert_array_equal(out[:3, 3], [5, -3, 2])

    def test_quarter_step_is_normalized_linear_blend_not_slerp(self):
        second = np.eye(4)
        second[:3, :3] = Rotation.from_euler('z', 120, degrees=True).as_matrix()
        out = probe.intermediate_pose(np.eye(4), second, .25)
        expected = 2*np.arctan2(np.sqrt(3)/8, 7/8)
        np.testing.assert_allclose(out[:3, :3], Rotation.from_rotvec([0, 0, expected]).as_matrix(), atol=1e-12)

    def test_endpoint_frames_and_external_inputs_are_preserved(self):
        first, second = np.eye(4), np.eye(4)
        first[:3, :3] = Rotation.from_rotvec([.2, .4, -.3]).as_matrix()
        second[:3, :3] = Rotation.from_rotvec([-.5, .1, .8]).as_matrix()
        saved_first, saved_second = first.copy(), second.copy()
        np.testing.assert_allclose(probe.intermediate_pose(first, second, 0), first, atol=1e-12)
        np.testing.assert_allclose(probe.intermediate_pose(first, second, 1), second, atol=1e-12)
        np.testing.assert_array_equal(first, saved_first)
        np.testing.assert_array_equal(second, saved_second)

    def test_invalid_fraction_or_nonrigid_pose_fails_closed(self):
        for fraction in (-.1, 1.1, np.nan, np.inf):
            with self.assertRaises(ValueError): probe.intermediate_pose(np.eye(4), np.eye(4), fraction)
        bad = np.eye(4); bad[0, 0] = 2
        with self.assertRaises(ValueError): probe.intermediate_pose(bad, np.eye(4), .5)
        with self.assertRaises(ValueError): probe.intermediate_pose(np.eye(4), bad, .5)


if __name__ == '__main__':
    unittest.main()
