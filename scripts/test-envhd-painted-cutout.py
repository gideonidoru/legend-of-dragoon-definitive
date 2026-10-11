#!/usr/bin/env python3
"""Checks for categorical source visibility and generated alpha matte repair."""
import importlib.util
from pathlib import Path
import unittest

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('painted_repair', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)


class PaintedCutoutTests(unittest.TestCase):
    def source(self):
        return Image.fromarray(np.array([
            [[20, 40, 15, 255], [0, 0, 0, 255]],
            [[5, 9, 12, 0], [40, 60, 25, 128]],
        ], dtype=np.uint8), 'RGBA')

    def test_near_transparent_matte_does_not_become_visible(self):
        painted = np.full((8, 8, 4), [35, 65, 20, 255], dtype=np.uint8)
        painted[1, 1] = [0, 255, 0, 1]
        result, operation = repair.repair(self.source(), Image.fromarray(painted, 'RGBA'))
        actual = np.asarray(result)
        native = np.repeat(np.repeat(np.asarray(self.source()), 4, axis=0), 4, axis=1)
        np.testing.assert_array_equal(actual[:, :, 3], native[:, :, 3])
        np.testing.assert_array_equal(actual[1, 1], [35, 65, 20, 255])
        np.testing.assert_array_equal(actual[0, 4], [0, 0, 0, 255])
        np.testing.assert_array_equal(actual[4, 0], [5, 9, 12, 0])
        self.assertEqual(operation['paintedGapPixelsExtended'], 1)

    def test_incomplete_painting_is_rejected(self):
        painted = np.zeros((8, 8, 4), dtype=np.uint8)
        painted[0, 0] = [35, 65, 20, 255]
        with self.assertRaisesRegex(ValueError, 'coverage differs'):
            repair.repair(self.source(), Image.fromarray(painted, 'RGBA'))

    def test_changed_canvas_aspect_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'aspect ratio'):
            repair.repair(self.source(), Image.new('RGBA', (8, 16), (35, 65, 20, 255)))


if __name__ == '__main__':
    unittest.main()
