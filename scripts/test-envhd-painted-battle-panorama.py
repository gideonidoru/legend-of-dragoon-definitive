#!/usr/bin/env python3
"""Check fixed panorama crops, native coverage, and fail-closed input bounds."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

import numpy as np
from PIL import Image


spec = importlib.util.spec_from_file_location('panorama_test', Path(__file__).with_name('repair-envhd-painted-battle-panorama.py'))
recipe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recipe)


class PanoramaTests(unittest.TestCase):
    def test_portrait_and_landscape_keep_fixed_native_rectangle(self):
        for source_size, rect, crop in [((256, 384), [64, 0, 256, 384], [256, 0, 1280, 1536]),
                                        ((384, 256), [0, 64, 384, 256], [0, 256, 1536, 1280])]:
            with self.subTest(size=source_size):
                source = Image.new('RGBA', source_size, (30, 50, 70, 255))
                painted = Image.new('RGB', (1536, 1536), (255, 0, 255))
                painted.paste((40, 80, 120), tuple(crop))
                output, _, record = recipe.repair(source, painted)
                self.assertEqual(record['sourceCanvasRect'], rect)
                self.assertEqual(record['paintedCropPixels'], crop)
                self.assertEqual(output.size, tuple(n * 4 for n in source_size))
                self.assertEqual(output.getpixel((500, 500)), (40, 80, 120, 255))
                self.assertFalse(record['runtimeSelected'])
                self.assertFalse(record['edgeAveraging'])
                self.assertEqual(record['wrapAcceptance'], 'pending')

    def test_discard_visible_black_and_native_coverage_survive(self):
        source = Image.new('RGBA', (256, 384), (20, 60, 90, 255))
        for x, pixel in enumerate([(0, 0, 0, 0), (0, 0, 0, 255), (40, 80, 20, 0)]):
            source.putpixel((x, 0), pixel)
        output, _, _ = recipe.repair(source, Image.new('RGB', (1536, 1536), (30, 80, 40)))
        native = np.repeat(np.repeat(np.asarray(source), 4, axis=0), 4, axis=1)
        actual = np.asarray(output)
        np.testing.assert_array_equal(actual[:, :, 3], native[:, :, 3])
        np.testing.assert_array_equal(np.all(actual[:, :, :3] == 0, axis=2), np.all(native[:, :, :3] == 0, axis=2))

    def test_non_panorama_native_layouts_are_rejected(self):
        for size in [(16, 16), (256, 256), (384, 384), (384, 257), (1, 65536)]:
            with self.subTest(size=size), self.assertRaisesRegex(ValueError, 'native panorama layout'):
                recipe.bounds.validate_dimensions(size, 'source')

    def test_bad_authoring_aspect_and_transparency_are_rejected(self):
        source = Image.new('RGBA', (256, 384))
        with self.assertRaisesRegex(ValueError, 'exactly square'):
            recipe.repair(source, Image.new('RGB', (1024, 1536)))
        with self.assertRaisesRegex(ValueError, 'entire authoring canvas'):
            recipe.repair(source, Image.new('RGBA', (1536, 1536), (1, 2, 3, 254)))

    def test_oversized_header_is_rejected_before_decode(self):
        data = b'\x89PNG\r\n\x1a\n' + struct.pack('>I', 13) + b'IHDR' + struct.pack('>II', 2200, 2200) + bytes([8, 2, 0, 0, 0]) + b'0000'
        with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
            recipe.bounds.validate_png_header(data, 'painted', allow_rgb=True)

    def test_output_size_must_be_exactly_fourfold(self):
        with self.assertRaisesRegex(ValueError, 'exact source canvas'):
            recipe.bounds.validate_dimensions((1024, 1024), 'output', (256, 384))

    def test_execute_binds_source_identity_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            source = Image.new('RGBA', (256, 384), (20, 30, 40, 255))
            source.save(root / 'source.png')
            Image.new('RGB', (1536, 1536), (40, 60, 80)).save(root / 'raw.png')
            with self.assertRaisesRegex(ValueError, 'source identity'):
                recipe.execute(root / 'source.png', root / 'raw.png', root / 'out.png', '0' * 64)
            self.assertFalse((root / 'out.png').exists())
            record, _ = recipe.execute(root / 'source.png', root / 'raw.png', root / 'out.png', recipe.terrain.fingerprint(source))
            self.assertEqual(record['method'], recipe.METHOD)
            with self.assertRaises((FileExistsError, ValueError)):
                recipe.execute(root / 'source.png', root / 'raw.png', root / 'out.png', recipe.terrain.fingerprint(source))

    def test_animated_png_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'animated.png'
            image = Image.new('RGB', (1536, 1536), (20, 40, 60))
            image.save(path, save_all=True, append_images=[Image.new('RGB', image.size, (40, 60, 80))], duration=100, loop=0)
            data = recipe.bounds.bounded_read(path)
            size = recipe.bounds.validate_png_header(data, 'painted', allow_rgb=True)
            with Image.open(path) as opened, self.assertRaisesRegex(ValueError, 'validated header'):
                recipe.bounds.validate_opened_png(opened, size, 'painted', expected_mode='RGB')


if __name__ == '__main__':
    unittest.main()
