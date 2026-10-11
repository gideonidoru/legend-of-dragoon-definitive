#!/usr/bin/env python3
"""Regression checks for exact palette geometry and native STP preservation."""
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


repair = module('motif_tests', 'repair-envhd-painted-battle-motif.py')
duplicate = module('motif_header_fixture', 'test-envhd-painted-cutout.py').duplicate_header_png
COLOUR = [148, 180, 156, 0]


class BattleMotifTests(unittest.TestCase):
    def source(self):
        pixels = np.tile(np.array([213, 255, 222, 0], dtype=np.uint8), (4, 4, 1))
        pixels[0, 1] = pixels[1, 1] = pixels[1, 2] = COLOUR
        pixels[3] = COLOUR
        return Image.fromarray(pixels)

    def test_stp_zero_colours_remain_visible_and_motif_topology_is_exact(self):
        output, preview, receipt = repair.repair(self.source(), Image.new('RGB', (16, 16), (220, 248, 227)), COLOUR, 3)
        pixels = np.asarray(output)
        expected = np.repeat(np.repeat(np.all(np.asarray(self.source()) == COLOUR, axis=2), 4, axis=0), 4, axis=1)
        self.assertTrue(np.array_equal(pixels[:, :, :3].mean(axis=2) < 200, expected))
        self.assertTrue((pixels[:, :, 3] == 0).all())
        self.assertTrue((np.asarray(preview)[:, :, 3] == 255).all())
        self.assertEqual(receipt['sourceMotifPixels'], 3)
        self.assertFalse(receipt['runtimeSelected'])

    def test_exact_discard_visible_black_and_stp_classes(self):
        source = np.array(self.source()); source[0, 0] = [0, 0, 0, 0]; source[0, 3] = [0, 0, 0, 255]; source[2, 2, 3] = 255
        output, _, _ = repair.repair(Image.fromarray(source), Image.new('RGB', (16, 16), (220, 248, 227)), COLOUR, 3)
        expected = np.repeat(np.repeat(source, 4, axis=0), 4, axis=1); actual = np.asarray(output)
        self.assertTrue(np.array_equal(actual[:, :, 3], expected[:, :, 3]))
        self.assertTrue(np.array_equal(np.all(actual[:, :, :3] == 0, axis=2), np.all(expected[:, :, :3] == 0, axis=2)))

    def test_changed_palette_or_band_cannot_be_repaired(self):
        for colour, band in [([1, 2, 3, 0], 3), (COLOUR, 2)]:
            with self.assertRaisesRegex(ValueError, 'classification'):
                repair.repair(self.source(), Image.new('RGB', (16, 16)), colour, band)

    def test_duplicate_generated_header_rejected_before_decode(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory).resolve(); source = base/'source.png'; raw = base/'raw.png'; output = base/'output.png'
            self.source().save(source); raw.write_bytes(duplicate(first=(16, 16), colour_type=2))
            key = repair.terrain.fingerprint(self.source())
            with mock.patch.object(Image.Image, 'convert', side_effect=AssertionError('Allocation')):
                with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
                    repair.execute(source, raw, output, key, COLOUR, 3)
            self.assertFalse(output.exists())

    def test_exact_source_identity_and_reproducible_output(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory).resolve(); source = base/'source.png'; raw = base/'raw.png'; output = base/'output.png'
            self.source().save(source); Image.new('RGB', (16, 16), (219, 249, 227)).save(raw)
            with self.assertRaisesRegex(ValueError, 'identity'):
                repair.execute(source, raw, output, 'wrong', COLOUR, 3)
            record, _ = repair.execute(source, raw, output, repair.terrain.fingerprint(self.source()), COLOUR, 3)
            before = output.read_bytes()
            again, _ = repair.execute(source, raw, base/'repeat.png', repair.terrain.fingerprint(self.source()), COLOUR, 3)
            self.assertEqual(before, output.read_bytes()); self.assertEqual(record, again)


if __name__ == '__main__':
    unittest.main()
