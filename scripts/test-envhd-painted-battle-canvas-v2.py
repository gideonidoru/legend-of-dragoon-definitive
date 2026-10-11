#!/usr/bin/env python3
"""Check preregistered crop fidelity and full-page allocation limits."""
import importlib.util
from pathlib import Path
import struct
import unittest

import numpy as np
from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


recipe = module('canvas_v2_test', 'repair-envhd-painted-battle-canvas-v2.py')
legacy = module('canvas_v1_test', 'repair-envhd-painted-battle-material.py')


class BattleCanvasTests(unittest.TestCase):
    def test_full_page_preserves_exact_stp_and_black_classes(self):
        source = Image.new('RGBA', (256, 248), (20, 60, 90, 255))
        for x, pixel in enumerate([(0, 0, 0, 0), (0, 0, 0, 255), (40, 80, 20, 0), (20, 60, 90, 255)]):
            source.putpixel((x, 0), pixel)
        output, _, receipt = recipe.repair(source, Image.new('RGB', (1024, 1024), (30, 80, 40)))
        original = np.repeat(np.repeat(np.asarray(source), 4, axis=0), 4, axis=1)
        pixels = np.asarray(output)
        self.assertEqual(output.size, (1024, 992))
        np.testing.assert_array_equal(pixels[:, :, 3], original[:, :, 3])
        np.testing.assert_array_equal(np.all(pixels[:, :, :3] == 0, axis=2), np.all(original[:, :, :3] == 0, axis=2))
        self.assertEqual(receipt['sourceCanvasRect'], [0, 4, 256, 248])
        self.assertEqual(receipt['paintedCropPixels'], [0, 16, 1024, 1008])
        self.assertFalse(receipt['runtimeSelected'])

    def test_wide_crop_is_fixed_before_authoring_and_ignores_padding(self):
        source = Image.new('RGBA', (256, 48), (40, 60, 90, 255))
        painted = Image.new('RGB', (1024, 1024), (255, 0, 255))
        painted.paste((40, 80, 120), (0, 416, 1024, 608))
        output, _, receipt = recipe.repair(source, painted)
        self.assertEqual(receipt['sourceCanvasRect'], [0, 104, 256, 48])
        self.assertEqual(receipt['paintedCropPixels'], [0, 416, 1024, 608])
        self.assertEqual(output.size, (1024, 192))
        self.assertEqual(output.getpixel((500, 90)), (40, 80, 120, 255))

    def test_wrong_canvas_fails_before_crop(self):
        with self.assertRaisesRegex(ValueError, 'exactly square'):
            recipe.repair(Image.new('RGBA', (24, 80)), Image.new('RGB', (1024, 1000)))

    def test_native_page_extents_are_bounded_even_when_pixel_count_is_small(self):
        for size in [(257, 1), (1, 257), (257, 256), (256, 257)]:
            with self.subTest(size=size), self.assertRaisesRegex(ValueError, 'native battle page'):
                recipe.bounds.validate_dimensions(size, 'source')

    def test_oversized_lazy_generated_header_is_rejected(self):
        data = b'\x89PNG\r\n\x1a\n' + struct.pack('>I', 13) + b'IHDR' + struct.pack('>II', 2200, 2200) + bytes([8, 2, 0, 0, 0]) + b'0000'
        with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
            recipe.bounds.validate_png_header(data, 'painted', allow_rgb=True)

    def test_output_requires_exact_fourfold_source_dimensions(self):
        with self.assertRaisesRegex(ValueError, 'exact source canvas'):
            recipe.bounds.validate_dimensions((1024, 1024), 'output', (256, 248))

    def test_transparent_authoring_canvas_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'entire authoring canvas'):
            recipe.repair(Image.new('RGBA', (64, 64)), Image.new('RGBA', (1024, 1024), (30, 60, 80, 254)))

    def test_subpixel_crop_cannot_silently_change_aspect(self):
        with self.assertRaisesRegex(ValueError, 'source aspect ratio'):
            recipe.repair(Image.new('RGBA', (1, 2)), Image.new('RGB', (100, 100)))

    def test_legacy_recipe_retains_original_full_page_rejection(self):
        self.assertEqual(legacy.bounds.MAX_TARGET_PIXELS, 1_000_000)
        with self.assertRaisesRegex(ValueError, 'bounded repair canvas'):
            legacy.bounds.validate_dimensions((256, 248), 'source')


if __name__ == '__main__':
    unittest.main()
