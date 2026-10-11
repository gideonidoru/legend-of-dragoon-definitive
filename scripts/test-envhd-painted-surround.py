#!/usr/bin/env python3
"""Prove source borders, generated detail, geometry bounds and original gates."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest import mock

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('surround_fixtures', Path(__file__).with_name('repair-envhd-painted-surround.py'))
surround = importlib.util.module_from_spec(spec)
spec.loader.exec_module(surround)


class SurroundTests(unittest.TestCase):
    def setUp(self):
        y, x = np.indices((24, 24))
        self.source = Image.fromarray(np.dstack((40 + x, 50 + y, 60 + x + y,
                                               np.full((24, 24), 255))).astype(np.uint8), 'RGBA')
        self.paint = Image.new('RGBA', (96, 96), (110, 120, 130, 255))
        self.polygons = [[[5, 5], [18, 5], [18, 18], [5, 18]]]

    def restore(self, **kwargs):
        return surround.restore_surround(self.source, self.paint, self.polygons, **kwargs)

    def test_nonuniform_outer_native_band_remains_exact_and_recoverable(self):
        result, receipt = self.restore()
        native = np.asarray(self.source.resize((96, 96), Image.Resampling.NEAREST))
        pixels = np.asarray(result)
        for a, b in [(pixels[:4], native[:4]), (pixels[-4:], native[-4:]),
                     (pixels[:, :4], native[:, :4]), (pixels[:, -4:], native[:, -4:])]:
            np.testing.assert_array_equal(a, b)
        self.assertTrue(receipt['originalBorderRgbRecoverable'])
        self.assertFalse(receipt['sourceAlphaApplied'])

    def test_surround_uses_source_interpolation_and_feature_interior_keeps_painted_colour(self):
        output, receipt = self.restore()
        base = np.asarray(self.source.resize((96, 96), Image.Resampling.LANCZOS))
        np.testing.assert_array_equal(np.asarray(output)[8:16, 8:80], base[8:16, 8:80])
        self.assertTrue(np.all(np.asarray(output)[36:56, 36:56] == [110, 120, 130, 255]))
        self.assertGreater(receipt['pureGeneratedTargetPixels'], 0)

    def test_generated_subtexel_detail_is_not_discarded(self):
        pixels = np.asarray(self.paint).copy()
        pixels[32:64:2, 32:64:2, :3] = [150, 170, 190]
        self.paint = Image.fromarray(pixels, 'RGBA')
        output, _ = self.restore()
        self.assertGreater(np.asarray(output)[40:44, 40:44, 0].std(), 0)
        np.testing.assert_array_equal(np.asarray(output)[40:44, 40:44], pixels[40:44, 40:44])

    def test_feather_changes_only_inside_reviewed_feature(self):
        output, _ = self.restore(feather_native_pixels=2)
        pixels = np.asarray(output)
        self.assertTrue(np.all(pixels[20:28, 40, 0] > 50))
        self.assertTrue(np.all(pixels[20:28, 40, 0] < 111))
        self.assertEqual(110, pixels[28, 40, 0])

    def test_source_and_generated_inputs_are_unchanged(self):
        before = (self.source.tobytes(), self.paint.tobytes())
        self.restore()
        self.assertEqual(before, (self.source.tobytes(), self.paint.tobytes()))

    def test_nonopaque_source_or_paint_cannot_bypass_coverage(self):
        self.source.putpixel((0, 0), (40, 50, 60, 128))
        with self.assertRaisesRegex(ValueError, 'opaque source'):
            self.restore()
        self.source.putpixel((0, 0), (40, 50, 60, 255))
        self.paint.putpixel((0, 0), (110, 120, 130, 0))
        with self.assertRaisesRegex(ValueError, 'opaque generated'):
            self.restore()

    def test_original_aspect_and_pixel_bounds_remain_before_allocation(self):
        huge = SimpleNamespace(mode='RGBA', size=(4096, 4096))
        with mock.patch.object(surround.np, 'asarray', side_effect=AssertionError('Pixel allocation')):
            with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
                surround.restore_surround(self.source, huge, self.polygons)
            with self.assertRaisesRegex(ValueError, 'aspect ratio'):
                surround.restore_surround(self.source, Image.new('RGBA', (100, 96)), self.polygons)
            with self.assertRaisesRegex(ValueError, 'bounded repair canvas'):
                surround.restore_surround(SimpleNamespace(size=(251, 250)), self.paint, self.polygons)

    def test_rgb_inputs_require_explicit_preflight_conversion(self):
        with self.assertRaisesRegex(ValueError, 'RGBA'):
            surround.restore_surround(self.source, self.paint.convert('RGB'), self.polygons)

    def test_invalid_geometry_is_rejected_before_image_arrays(self):
        invalid = [None, [], [[['x', 2], [8, 2], [8, 8]]], [[[True, 2], [8, 2], [8, 8]]],
                   [[[0, 2], [8, 2], [8, 8]]], [[[2, 2], [2, 2], [8, 8]]],
                   [[[2, 2], [4, 4], [6, 6]]], [[[2, 2], [8, 8], [2, 8], [8, 2]]],
                   [[[2, 2], [10, 10], [2, 10], [9, 2], [10, 2]]]]
        with mock.patch.object(surround.np, 'asarray', side_effect=AssertionError('Pixel allocation')):
            for geometry in invalid:
                with self.subTest(geometry=geometry), self.assertRaises(ValueError):
                    surround.restore_surround(self.source, self.paint, geometry)

    def test_tiny_or_excessive_feature_coverage_is_rejected(self):
        for polygons in [[[[2, 2], [3, 2], [3, 3]]], [[[1, 1], [22, 1], [22, 22], [1, 22]]]]:
            # Use a larger source for the excessive case, so its inner border
            # occupies less than10% of the canvas.
            if polygons[0][1][0] == 22:
                source = self.source.resize((100, 100));paint = self.paint.resize((400, 400))
                polygons = [[[1, 1], [98, 1], [98, 98], [1, 98]]]
            else:
                source, paint = self.source, self.paint
            with self.assertRaisesRegex(ValueError, 'coverage'):
                surround.restore_surround(source, paint, polygons)

    def test_invalid_feather_is_rejected(self):
        for value in [0, 4, 1.5, True]:
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, 'feather'):
                self.restore(feather_native_pixels=value)

    def test_original_cutout_recipe_restores_source_black_after_surround(self):
        self.source.putpixel((12, 12), (0, 0, 0, 255))
        prepared, _ = self.restore()
        output, operation = surround.bounds.repair(self.source, prepared)
        self.assertTrue(np.all(np.asarray(output)[48:52, 48:52] == [0, 0, 0, 255]))
        self.assertTrue(operation['visibleBlackExact'])

    def test_replay_is_deterministic(self):
        a, ar = self.restore();b, br = self.restore()
        self.assertEqual(a.tobytes(), b.tobytes());self.assertEqual(ar, br)


if __name__ == '__main__':
    unittest.main()
