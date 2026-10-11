#!/usr/bin/env python3
"""Source palette geometry, isolation, STP and bounded rectangle input checks."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import numpy as np
from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


repair = module('rectangle_test', 'repair-envhd-painted-battle-rectangle.py')
duplicate = module('rectangle_header_test', 'test-envhd-painted-cutout.py').duplicate_header_png
PALETTE = [[180, 170, 130, 0], [125, 115, 100, 255]]


class PaintedRectangleTests(unittest.TestCase):
    def source(self):
        pixels = np.tile(np.array([110, 30, 40, 0], dtype=np.uint8), (4, 4, 1))
        pixels[1, 1] = pixels[1, 2] = PALETTE[0]
        pixels[2, 2] = PALETTE[1]
        # A matching palette class outside the reviewed rectangle must not be restored.
        pixels[0, 3] = PALETTE[0]
        pixels[3, 0] = [0, 0, 0, 0]
        pixels[3, 1] = [0, 0, 0, 255]
        return Image.fromarray(pixels)

    def test_gap_shades_and_outside_rectangle_are_preserved(self):
        output, _, record = repair.repair(self.source(), Image.new('RGB', (16, 16), (100, 20, 30)), PALETTE, [1, 1, 3, 3])
        actual = np.asarray(output)
        # A separate colour classifier recovers the three nominated synthetic cells.
        expected = np.zeros((4, 4), dtype=bool)
        expected[1, 1:3] = True
        expected[2, 2] = True
        self.assertTrue(np.array_equal(actual[:, :, 1] > 80, np.repeat(np.repeat(expected, 4, axis=0), 4, axis=1)))
        self.assertEqual(record['sourceMarkingPixels'], 3)
        self.assertEqual([c['sourcePixels'] for c in record['sourcePalettePixels']], [2, 1])
        self.assertFalse(record['runtimeSelected'])

    def test_mixed_stp_discard_and_visible_black_exact(self):
        output, preview, _ = repair.repair(self.source(), Image.new('RGB', (16, 16), (100, 20, 30)), PALETTE, [1, 1, 3, 3])
        expected = np.repeat(np.repeat(np.asarray(self.source()), 4, axis=0), 4, axis=1)
        actual = np.asarray(output)
        self.assertTrue(np.array_equal(actual[:, :, 3], expected[:, :, 3]))
        self.assertTrue(np.array_equal(np.all(actual[:, :, :3] == 0, axis=2), np.all(expected[:, :, :3] == 0, axis=2)))
        self.assertTrue((np.asarray(preview)[4:8, 4:8, 3] == 255).all())

    def test_absent_duplicate_invisible_and_partial_palettes_rejected(self):
        for palette in [[[1, 2, 3, 0]], PALETTE + [PALETTE[0]], [[0, 0, 0, 0]], [[1, 2, 3, 1]], [[True, 2, 3, 0]]]:
            with self.assertRaises(ValueError):
                repair.repair(self.source(), Image.new('RGB', (16, 16)), palette, [1, 1, 3, 3])
        with self.assertRaisesRegex(ValueError, 'background'):
            repair.repair(self.source(), Image.new('RGB', (16, 16)), [PALETTE[0]], [1, 1, 3, 2])

    def test_invalid_geometry_aspect_and_partial_painted_canvas_rejected(self):
        for rect in [[-1, 1, 3, 3], [1, 1, 5, 3], [2, 1, 1, 3], [True, 1, 3, 3], [1, 1, 3]]:
            with self.assertRaises(ValueError):
                repair.repair(self.source(), Image.new('RGB', (16, 16)), PALETTE, rect)
        with self.assertRaisesRegex(ValueError, 'aspect'):
            repair.repair(self.source(), Image.new('RGB', (16, 20)), PALETTE, [1, 1, 3, 3])
        with self.assertRaisesRegex(ValueError, 'entire source canvas'):
            repair.repair(self.source(), Image.new('RGBA', (16, 16)), PALETTE, [1, 1, 3, 3])

    def test_decoder_bounds_before_allocation_and_source_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            source, raw, output = [base / n for n in ['source.png', 'raw.png', 'output.png']]
            self.source().save(source)
            raw.write_bytes(duplicate(first=(16, 16), colour_type=2))
            key = repair.terrain.fingerprint(self.source())
            with mock.patch.object(Image.Image, 'convert', side_effect=AssertionError('Allocation')):
                with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
                    repair.execute(source, raw, output, key, PALETTE, [1, 1, 3, 3])
            self.assertFalse(output.exists())
            Image.new('RGB', (16, 16), (100, 20, 30)).save(raw)
            with self.assertRaisesRegex(ValueError, 'identity'):
                repair.execute(source, raw, output, 'wrong', PALETTE, [1, 1, 3, 3])
            first, _ = repair.execute(source, raw, output, key, PALETTE, [1, 1, 3, 3])
            before = output.read_bytes()
            second, _ = repair.execute(source, raw, output, key, PALETTE, [1, 1, 3, 3])
            self.assertEqual(first, second)
            self.assertEqual(before, output.read_bytes())


if __name__ == '__main__':
    unittest.main()
