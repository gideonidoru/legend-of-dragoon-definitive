#!/usr/bin/env python3
"""Source fidelity, local scope and fail-closed tests for feature restoration."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('feature_test', Path(__file__).with_name('repair-envhd-painted-feature-detail.py'))
feature = importlib.util.module_from_spec(spec)
spec.loader.exec_module(feature)


class FeatureDetailTest(unittest.TestCase):
    def setUp(self):
        self.source = Image.new('RGBA', (24, 24), (31, 54, 76, 255))
        self.painting = Image.new('RGBA', (96, 96), (155, 111, 89, 255))
        self.polygons = [[[6, 6], [15, 6], [15, 15], [6, 15]]]

    def test_pixels_outside_feature_retain_painted_detail(self):
        result, receipt = feature.restore_features(self.source, self.painting, self.polygons)
        pixels = np.asarray(result)
        self.assertTrue(np.array_equal(pixels[0:20], np.asarray(self.painting)[0:20]))
        self.assertFalse(np.array_equal(pixels[40:44, 40:44], np.asarray(self.painting)[40:44, 40:44]))
        self.assertTrue(receipt['outsideFeaturePixelsExact'])

    def test_core_keeps_original_native_pixel_mean_colour(self):
        result, _ = feature.restore_features(self.source, self.painting, self.polygons)
        mean = np.asarray(result)[40:44, 40:44, :3].mean(axis=(0, 1))
        self.assertTrue(np.all(np.abs(mean - [31, 54, 76]) <= 0.5))

    def test_transparency_partial_alpha_and_black_are_preserved(self):
        self.source.putpixel((8, 8), (0, 0, 0, 255))
        self.source.putpixel((9, 9), (31, 54, 76, 128))
        self.source.putpixel((10, 10), (31, 54, 76, 0))
        result, _ = feature.restore_features(self.source, self.painting, self.polygons)
        original = np.asarray(self.source.resize(result.size, Image.Resampling.NEAREST))
        actual = np.asarray(result)
        self.assertTrue(np.array_equal(actual[:, :, 3], original[:, :, 3]))
        visible = original[:, :, 3] != 0
        self.assertTrue(np.array_equal(np.all(actual[:, :, :3] == 0, axis=2) & visible,
                                       np.all(original[:, :, :3] == 0, axis=2) & visible))

    def test_source_restoration_cannot_bypass_raw_missing_coverage(self):
        empty = Image.new('RGBA', self.painting.size)
        with self.assertRaises(ValueError):
            feature.restore_features(self.source, empty, self.polygons)

    def test_source_restoration_cannot_bypass_raw_aspect_mismatch(self):
        wrong = Image.new('RGBA', (96, 48), (155, 111, 89, 255))
        with self.assertRaises(ValueError):
            feature.restore_features(self.source, wrong, self.polygons)

    def test_invalid_or_broad_polygons_fail(self):
        for polygons in [[], [[[6, 6], [15, 15], [6, 15], [15, 6]]],
                         [[[0, 6], [15, 6], [15, 15]]],
                         [[[1, 1], [22, 1], [22, 22], [1, 22]]]]:
            with self.subTest(polygons=polygons), self.assertRaises(ValueError):
                feature.restore_features(self.source, self.painting, polygons)

    def test_invalid_feather_fails(self):
        for feather in [0, 4, True, 1.5]:
            with self.subTest(feather=feather), self.assertRaises(ValueError):
                feature.restore_features(self.source, self.painting, self.polygons, feather)

    def test_changed_art_identity_publishes_no_output(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory)
            self.source.save(p / 'source.png')
            self.painting.save(p / 'painted.png')
            with self.assertRaisesRegex(ValueError, 'artwork identity'):
                feature.execute(p / 'source.png', p / 'painted.png', p / 'output.png',
                                '0' * 64, '0' * 64, self.polygons)
            self.assertFalse((p / 'output.png').exists())


if __name__ == '__main__':
    unittest.main()
