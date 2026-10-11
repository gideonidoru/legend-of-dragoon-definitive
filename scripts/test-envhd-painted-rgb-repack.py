#!/usr/bin/env python3
"""RGB container conversion retains paintings and cannot weaken source bounds."""
import importlib.util
import io
from pathlib import Path
import unittest
from unittest import mock

import numpy as np
from PIL import Image


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


repack = module('rgb_repack_test', 'repack-envhd-painted-rgb.py')
fixtures = module('rgb_repack_png_fixtures', 'test-envhd-painted-cutout.py')


def png(image, **options):
    stream = io.BytesIO()
    image.save(stream, format='PNG', **options)
    return stream.getvalue()


class RgbRepackTests(unittest.TestCase):
    def setUp(self):
        self.pixels = np.arange(8 * 8 * 3, dtype=np.uint8).reshape(8,8,3)
        self.data = png(Image.fromarray(self.pixels, 'RGB'))

    def test_every_rgb_pixel_and_canvas_are_preserved_with_constant_alpha(self):
        data, receipt = repack.repack(self.data, [2,2])
        with Image.open(io.BytesIO(data)) as image:
            self.assertEqual(image.mode, 'RGBA')
            self.assertEqual(image.size, (8,8))
            np.testing.assert_array_equal(np.asarray(image)[:,:,:3], self.pixels)
            self.assertEqual(image.getchannel('A').getextrema(), (255,255))
        self.assertFalse(receipt['sourceRgbApplied'])
        self.assertFalse(receipt['sourceAlphaApplied'])
        self.assertFalse(receipt['geometryChanged'])

    def test_deterministic_receipt_and_original_file_bytes_retained(self):
        before = bytes(self.data)
        self.assertEqual(repack.repack(self.data, [2,2]), repack.repack(self.data, [2,2]))
        self.assertEqual(before, self.data)

    def test_original_source_alpha_black_and_hidden_pixels_remain_exact(self):
        source = Image.fromarray(np.array([[[20,40,15,255],[0,0,0,255]],[[1,2,3,0],[40,60,25,128]]], dtype=np.uint8), 'RGBA')
        data, _ = repack.repack(self.data, list(source.size))
        with Image.open(io.BytesIO(data)) as image:
            result, receipt = repack.bounds.repair(source, image)
        native = np.repeat(np.repeat(np.asarray(source), 4, axis=0), 4, axis=1)
        pixels = np.asarray(result)
        np.testing.assert_array_equal(pixels[:,:,3], native[:,:,3])
        masked = (native[:,:,3] == 0) | np.all(native[:,:,:3] == 0, axis=2)
        np.testing.assert_array_equal(pixels[masked], native[masked])
        self.assertTrue(receipt['sourceAlphaExact'])
        self.assertTrue(receipt['visibleBlackExact'])

    def test_rgba_and_indexed_inputs_cannot_use_the_rgb_container_protocol(self):
        for mode in ('RGBA', 'P'):
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                repack.repack(png(Image.new(mode, (8,8))), [2,2])

    def test_rgb_transparency_key_is_rejected_instead_of_discarded(self):
        data = png(Image.fromarray(self.pixels, 'RGB'), transparency=(0,1,2))
        with self.assertRaisesRegex(ValueError, 'one opaque RGB'):
            repack.repack(data, [2,2])

    def test_animated_rgb_is_rejected(self):
        data = png(Image.new('RGB', (8,8), 'red'), save_all=True,
                   append_images=[Image.new('RGB', (8,8), 'blue')])
        with self.assertRaisesRegex(ValueError, 'one opaque RGB'):
            repack.repack(data, [2,2])

    def test_original_bounds_and_aspect_fail_before_pixel_allocation(self):
        cases = [(fixtures.compressed_png(colour_type=2), [2,2]),
                 (png(Image.new('RGB', (8,16))), [2,2]),
                 (self.data, [4096,4096]),
                 (b'x' * (repack.bounds.MAX_IMAGE_BYTES + 1), [2,2])]
        with mock.patch.object(repack.Image, 'open', side_effect=AssertionError('Pixel allocation')):
            for data, source in cases:
                with self.subTest(size=len(data), source=source), self.assertRaises(ValueError):
                    repack.repack(data, source)

    def test_later_oversized_png_header_is_rejected_before_loading_pixels(self):
        data = fixtures.duplicate_header_png(first=(8,8), colour_type=2)
        with mock.patch.object(Image.Image, 'load', side_effect=AssertionError('Pixel allocation')):
            with self.assertRaises(ValueError):
                repack.repack(data, [2,2])


if __name__ == '__main__':
    unittest.main()
