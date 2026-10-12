"""Authored geometry fixtures, no game data. AGPL v3."""
import importlib.util
from pathlib import Path
import unittest
import numpy as np
from scipy.spatial.transform import Rotation
spec = importlib.util.spec_from_file_location('rig', Path(__file__).with_name('continuous-rig.py'))
rig = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rig)


class ContinuousSkinTests(unittest.TestCase):
    def setUp(self):
        self.points = np.array([[1., 0, 0], [0, 1, 0], [0, 0, 1], [0, 0, 0]])
        self.transforms = np.repeat(np.eye(4)[None], 2, axis=0)
        self.weights = np.full((4, 2), .5)

    def test_identity_and_input_immutability(self):
        saved = [x.copy() for x in (self.points, self.weights, self.transforms)]
        np.testing.assert_allclose(rig.deform(self.points, self.weights, self.transforms), self.points, atol=1e-12)
        for original, copy in zip((self.points, self.weights, self.transforms), saved):
            np.testing.assert_array_equal(original, copy)

    def test_single_rigid_matches_matrix(self):
        self.transforms[1, :3, :3] = Rotation.from_euler('xyz', [35, 91, -47], degrees=True).as_matrix()
        self.transforms[1, :3, 3] = [3, -2, 4]
        self.weights[:] = [0, 1]
        expected = self.points@self.transforms[1, :3, :3].T+self.transforms[1, :3, 3]
        np.testing.assert_allclose(rig.deform(self.points, self.weights, self.transforms), expected, atol=1e-12)

    def test_half_turn_preserves_radius(self):
        self.transforms[1, :3, :3] = Rotation.from_rotvec([0, np.pi, 0]).as_matrix()
        result = rig.deform(self.points, self.weights, self.transforms)
        np.testing.assert_allclose(np.linalg.norm(result, axis=1), np.linalg.norm(self.points, axis=1), atol=1e-12)

    def test_antipodal_short_arc(self):
        self.transforms[:, :3, :3] = Rotation.from_euler('z', [[179], [-179]], degrees=True).as_matrix()
        result = rig.deform(self.points, self.weights, self.transforms)
        np.testing.assert_allclose(result[0], [-1, 0, 0], atol=1e-12)

    def test_translation_blend(self):
        self.transforms[1, :3, 3] = [2, 4, 6]
        np.testing.assert_allclose(rig.deform(self.points, self.weights, self.transforms), self.points+[1, 2, 3], atol=1e-12)

    def test_invalid_weights(self):
        for bad in [np.full((4, 2), -1), np.zeros((4, 2)), np.full((4, 2), np.nan)]:
            with self.assertRaises(ValueError):
                rig.deform(self.points, bad, self.transforms)

    def test_reject_scale_reflection_and_affine(self):
        for index, value in [((0, 0, 0), 2), ((0, 0, 0), -1), ((0, 3, 0), .1)]:
            bad = self.transforms.copy()
            bad[index] = value
            with self.assertRaises(ValueError):
                rig.deform(self.points, self.weights, bad)

    def test_segment_rotation_endpoints(self):
        start, end = np.array([1., 2, 3]), np.array([1., 3, 3])
        target_start, target_end = np.array([-1., 2, -3]), np.array([-2., 2, -3])
        transform = rig.align_segment(start, end, target_start, target_end)
        np.testing.assert_allclose(np.stack([start, end])@transform[:3, :3].T+transform[:3, 3], [target_start, target_end], atol=1e-12)

    def test_reverse_segment(self):
        transform = rig.align_segment(np.zeros(3), np.array([0., 1, 0]), np.zeros(3), np.array([0., -1, 0]))
        np.testing.assert_allclose(transform[:3, :3]@[0, 1, 0], [0, -1, 0], atol=1e-12)

    def test_ik_keeps_lengths(self):
        shoulder, wrist = np.zeros(3), np.array([.6, .4, .2])
        elbow = rig.solve_elbow(shoulder, wrist, .5, .4, [0, 0, 1])
        self.assertAlmostEqual(np.linalg.norm(elbow-shoulder), .5)
        self.assertAlmostEqual(np.linalg.norm(wrist-elbow), .4)
        with self.assertRaises(ValueError):
            rig.solve_elbow(shoulder, [2, 0, 0], .5, .4, [0, 0, 1])

    def test_uv_duplicates_share_weights(self):
        bones = [('hips', np.array([0., .8, 0]), np.array([0., 1.1, 0])),
                 ('chest', np.array([0., 1.1, 0]), np.array([0., 1.5, 0]))]
        points = np.array([[.1, 1.2, 0], [.1, 1.2, 0], [0, .9, 0]])
        weights = rig.skin_weights(points, bones)
        np.testing.assert_array_equal(weights[0], weights[1])
        np.testing.assert_allclose(weights.sum(1), 1)


class HeadCutTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('builder', Path(__file__).with_name('build-complete-dart.py'))
        self.builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.builder)

    def test_head_cut_preserves_high_shoulders(self):
        import trimesh
        vertices = [[.19, 1.55, 0], [.26, 1.58, 0], [.24, 1.48, 0],
                    [-.03, 1.65, 0], [.03, 1.65, 0], [0, 1.70, 0]]
        mesh = trimesh.Trimesh(vertices, [[0, 1, 2], [3, 4, 5]], process=False)
        mesh.visual = trimesh.visual.TextureVisuals(uv=np.zeros((6, 2)))
        kept = self.builder.clip_body(mesh, 1.505)
        self.assertEqual(len(kept.faces), 1)
        np.testing.assert_allclose(kept.vertices, vertices[:3])

    def test_low_body_is_unchanged(self):
        import trimesh
        vertices = [[-.1, 1.4, 0], [.1, 1.4, 0], [0, 1.3, 0]]
        uv = [[0, 0], [1, 0], [.5, 1]]
        mesh = trimesh.Trimesh(vertices, [[0, 1, 2]], process=False)
        mesh.visual = trimesh.visual.TextureVisuals(uv=uv)
        kept = self.builder.clip_body(mesh, 1.505)
        np.testing.assert_array_equal(kept.vertices, vertices)
        np.testing.assert_array_equal(kept.faces, [[0, 1, 2]])
        np.testing.assert_array_equal(kept.visual.uv, uv)


if __name__ == '__main__':
    unittest.main()
