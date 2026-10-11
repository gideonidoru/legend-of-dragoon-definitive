#!/usr/bin/env python3
"""Separate source-canvas fitting rejects lost/moved/opaque source silhouettes."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('canvas_fixtures', Path(__file__).with_name('normalize-envhd-painted-canvas.py'))
canvas = importlib.util.module_from_spec(spec)
spec.loader.exec_module(canvas)


class CanvasTests(unittest.TestCase):
    def setUp(self):
        pixels = np.zeros((24, 20, 4), dtype=np.uint8)
        pixels[2:22, 2:18] = [35, 65, 80, 255]
        pixels[10:14, 8:12] = [0, 0, 0, 128]
        self.source = Image.fromarray(pixels, 'RGBA')
        self.painted = self.source.resize((160, 192), Image.Resampling.NEAREST)

    def test_explicit_fit_restores_canvas_and_records_alignment_without_applying_alpha(self):
        stretched = self.painted.resize((184, 192), Image.Resampling.NEAREST)
        image, receipt = canvas.normalize(self.source, stretched)
        self.assertEqual((80, 96), image.size)
        self.assertGreater(receipt['silhouetteIou'], 0.97)
        self.assertGreater(receipt['aspectDeviation'], 0.01)
        self.assertFalse(receipt['sourceAlphaApplied'])
        self.assertEqual(64, len(receipt['boundsAlgorithmSha256']))

    def test_unchanged_canvas_retains_generated_detail(self):
        pixels = np.asarray(self.painted).copy()
        pixels[16:170:4, 16:140:4, :3] = [70, 90, 110]
        result, _ = canvas.normalize(self.source, Image.fromarray(pixels, 'RGBA'))
        self.assertGreater(float(np.asarray(result)[16:80, 16:64, 0].std()), 0)

    def test_large_aspect_change_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'aspect-fit bound'):
            canvas.normalize(self.source, self.painted.resize((256, 192)))

    def test_opaque_painting_cannot_impersonate_source_silhouette(self):
        with self.assertRaisesRegex(ValueError, 'silhouette'):
            canvas.normalize(self.source, Image.new('RGBA', (160, 192), (35, 65, 80, 255)))

    def test_changed_cutout_hole_is_rejected(self):
        a = np.asarray(self.painted).copy()
        a[48:144, 48:112, 3] = 0
        with self.assertRaisesRegex(ValueError, 'silhouette'):
            canvas.normalize(self.source, Image.fromarray(a, 'RGBA'))

    def test_translated_subject_is_rejected(self):
        shifted = Image.new('RGBA', self.painted.size)
        shifted.alpha_composite(self.painted, (24, 0))
        with self.assertRaises(ValueError):
            canvas.normalize(self.source, shifted)

    def test_small_boundary_spike_is_rejected_even_when_overall_overlap_passes(self):
        pixels = np.zeros((100, 100, 4), dtype=np.uint8)
        pixels[10:90, 10:90] = [35, 65, 80, 255]
        source = Image.fromarray(pixels, 'RGBA')
        painted = np.repeat(np.repeat(pixels, 4, axis=0), 4, axis=1)
        painted[200:204, 0:40] = [35, 65, 80, 255]
        with self.assertRaisesRegex(ValueError, 'row boundary'):
            canvas.normalize(source, Image.fromarray(painted, 'RGBA'))

    def test_single_new_occupied_row_is_rejected_even_when_overall_overlap_passes(self):
        pixels = np.zeros((100, 100, 4), dtype=np.uint8)
        pixels[10:90, 10:90] = [35, 65, 80, 255]
        source = Image.fromarray(pixels, 'RGBA')
        painted = np.repeat(np.repeat(pixels, 4, axis=0), 4, axis=1)
        painted[20, 100:300] = [35, 65, 80, 255]
        with self.assertRaisesRegex(ValueError, 'occupied source rows'):
            canvas.normalize(source, Image.fromarray(painted, 'RGBA'))

    def test_uniform_opaque_and_empty_source_cannot_prove_alignment(self):
        for alpha in [0, 255]:
            with self.subTest(alpha=alpha), self.assertRaisesRegex(ValueError, 'non-opaque'):
                canvas.normalize(Image.new('RGBA', (20, 24), (35, 65, 80, alpha)), self.painted)

    def test_rgb_source_and_generated_input_are_rejected(self):
        for source, painted in [(self.source.convert('RGB'), self.painted), (self.source, self.painted.convert('RGB'))]:
            with self.assertRaisesRegex(ValueError, 'RGBA'):
                canvas.normalize(source, painted)

    def test_budget_is_checked_before_pixel_allocation(self):
        huge = SimpleNamespace(mode='RGBA', size=(4096, 4096), width=4096, height=4096)
        with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
            canvas.normalize(self.source, huge)
        source = SimpleNamespace(mode='RGBA', size=(251, 250), width=251, height=250)
        with self.assertRaisesRegex(ValueError, 'bounded repair canvas'):
            canvas.normalize(source, self.painted)

    def test_following_cutout_recipe_still_restores_native_alpha_and_black(self):
        normalized, _ = canvas.normalize(self.source, self.painted.resize((184, 192)))
        repaired, _ = canvas.repair.repair(self.source, normalized)
        expected = np.repeat(np.repeat(np.asarray(self.source), 4, axis=0), 4, axis=1)
        actual = np.asarray(repaired)
        np.testing.assert_array_equal(actual[:, :, 3], expected[:, :, 3])
        mask = np.all(expected[:, :, :3] == 0, axis=2)
        np.testing.assert_array_equal(actual[mask], expected[mask])

    def test_original_cutout_gate_still_rejects_the_same_stretched_input(self):
        with self.assertRaisesRegex(ValueError, 'aspect ratio'):
            canvas.repair.repair(self.source, self.painted.resize((184, 192)))

    def test_repeat_fit_is_deterministic(self):
        a, ar = canvas.normalize(self.source, self.painted)
        b, br = canvas.normalize(self.source, self.painted)
        self.assertEqual(a.tobytes(), b.tobytes())
        self.assertEqual(ar, br)


if __name__ == '__main__':
    unittest.main()
