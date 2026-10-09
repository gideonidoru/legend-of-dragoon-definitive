#!/usr/bin/env python3
"""Original synthetic sampling-footprint fixtures, AGPL v3; see LICENSE."""
import importlib.util
from pathlib import Path
import unittest
import numpy as np

spec = importlib.util.spec_from_file_location('footprints', Path(__file__).with_name('audit-uv-footprints.py'))
footprints = importlib.util.module_from_spec(spec)
spec.loader.exec_module(footprints)


class SamplingFootprintTest(unittest.TestCase):
    def test_fractional_triangle_coverage_and_bounds(self):
        mask = footprints.triangle_cells(4, 4, [(0, 0), (3, 1), (0, 3)])
        self.assertTrue(mask[0, 2])  # Fractional points enter this cell despite no integer vertex there.
        self.assertFalse(mask[3, 3])
        self.assertEqual(mask.shape, (4, 4))
        self.assertTrue(footprints.triangle_cells(4, 4, [(3, 3)]*3)[3, 3])

    def test_winding_does_not_change_sampling_coverage(self):
        triangle = [(0, 0), (3, 1), (0, 3)]
        np.testing.assert_array_equal(footprints.triangle_cells(4, 4, triangle), footprints.triangle_cells(4, 4, triangle[::-1]))


if __name__ == '__main__':
    unittest.main()
