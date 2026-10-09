#!/usr/bin/env python3
"""Original synthetic pose/UV inspection fixtures, AGPL v3; see LICENSE."""
import importlib.util
from pathlib import Path
import struct
import unittest
import numpy as np
from PIL import Image


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


poses = load('poses', 'inspect-model-poses.py')
fixtures = load('fixtures', 'test-model-material-audit.py')


class ModelPoseTest(unittest.TestCase):
    def test_animation_excursion_is_included_in_common_framing(self):
        ordinary = [np.array([[0, 0, 0], [10, 20, 0]])]
        excursion = [np.array([[-100, -200, 0], [150, 300, 0]])]
        self.assertEqual(poses.framing_bounds([ordinary, excursion]), (-100, 150, -200, 300))

    def test_keyframes_match_parts_and_apply_rotation_then_translation(self):
        animation = struct.pack('<4I6h', 12, 0, 0, 1 | 2 << 16, 0, 0, 1024, 10, 20, 30)
        frames = poses.read_keyframes(animation, 1)
        parts = [(np.array([[1, 0, 0]]), [])]
        result = poses.posed_vertices(parts, frames[0], 0)
        np.testing.assert_allclose(result[0], [[10, 21, 30]], atol=1e-10)
        with self.assertRaises(ValueError): poses.read_keyframes(animation, 2)
        with self.assertRaises(ValueError): poses.read_keyframes(animation[:-1], 1)

    def test_geometry_rejects_invalid_references(self):
        self.assertEqual(len(poses.read_geometry(fixtures.model())), 1)
        invalid = bytearray(fixtures.model())
        struct.pack_into('<H', invalid, len(invalid)-2, 3)
        with self.assertRaises(ValueError): poses.read_geometry(bytes(invalid))

    def test_high_palette_indices_and_identical_coverage(self):
        model = bytearray(fixtures.model())
        for i, vertex in enumerate([(-10, -10, 0), (10, -10, 0), (-10, 10, 0)]):
            struct.pack_into('<3h', model, 52+i*8, *vertex)
        struct.pack_into('<H', model, 90, (15 << 6) | 1)
        colors = [31] * 512
        tim = struct.pack('<3I4H', 0x10, 8, 1036, 0, 0, 32, 16) + struct.pack('<512H', *colors) + struct.pack('<I4H', 20, 0, 0, 1, 4) + bytes([0x11])*8
        parts = poses.read_geometry(bytes(model))
        self.assertEqual(parts[0][1][0][2], (15 << 6) | 1)
        positions = [parts[0][0]]
        original = poses.render(parts, positions, tim, None, (-10, 10, -10, 10), (96, 96))
        candidate = Image.new('RGBA', (8, 8), (255, 0, 0, 0))
        enhanced = poses.render(parts, positions, tim, candidate, (-10, 10, -10, 10), (96, 96))
        self.assertEqual(poses.coverage_delta(original, enhanced), {'missingPixels': 0, 'extraPixels': 0})
        self.assertGreater(np.count_nonzero(np.array(original.getchannel('A'))), 0)


if __name__ == '__main__':
    unittest.main()
