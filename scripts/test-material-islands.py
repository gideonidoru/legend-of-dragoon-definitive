#!/usr/bin/env python3
"""Original synthetic material isolation fixtures, AGPL v3; see LICENSE."""
import importlib.util
from pathlib import Path
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location('islands', Path(__file__).with_name('compare-material-islands.py'))
islands = importlib.util.module_from_spec(spec)
spec.loader.exec_module(islands)


class MaterialIsolationTest(unittest.TestCase):
    def test_neighboring_palette_cannot_fill_tile_border(self):
        source = Image.new('RGBA', (4, 4), (0, 0, 255, 0))
        source.putpixel((1, 1), (255, 0, 0, 0))
        mask = Image.new('1', source.size)
        mask.putpixel((1, 1), 1)
        tile, crop = islands.padded_material(source, mask, 2)
        self.assertEqual(crop, (-1, -1, 4, 4))
        self.assertEqual(set(tile.get_flattened_data()), {(255, 0, 0)})
        with self.assertRaises(ValueError):
            islands.padded_material(source, Image.new('1', source.size))

    def test_blending_is_confined_to_source_coverage_and_retains_stp(self):
        source = Image.new('RGBA', (2, 2), (10, 20, 30, 0))
        source.putpixel((0, 0), (30, 40, 50, 255))
        mask = Image.new('1', source.size)
        mask.putpixel((0, 0), 1)
        target = source.resize((4, 4), Image.Resampling.NEAREST)
        candidate = Image.new('RGBA', (4, 4), (130, 140, 150, 255))
        islands.composite_material(target, source, candidate, mask, (0, 0, 2, 2), 2, 0.5)
        self.assertEqual(target.getpixel((1, 1)), (80, 90, 100, 255))
        self.assertEqual(target.getpixel((2, 2)), (10, 20, 30, 0))


if __name__ == '__main__':
    unittest.main()
