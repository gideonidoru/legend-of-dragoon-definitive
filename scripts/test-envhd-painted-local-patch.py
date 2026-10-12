#!/usr/bin/env python3
"""Local patch gates preserve the base and reject incompatible inputs before decode."""
import importlib.util
import io
from pathlib import Path
import unittest
from unittest import mock

import numpy as np
from PIL import Image


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result


patch = module('local_patch_tests', 'repair-envhd-painted-local-patch.py')
fixtures = module('local_patch_png_fixtures', 'test-envhd-painted-cutout.py')


def png(image):
    stream = io.BytesIO();image.save(stream, format='PNG');return stream.getvalue()


class LocalPatchTests(unittest.TestCase):
    def setUp(self):
        self.source = Image.new('RGBA', (4,4), (20,40,15,255))
        self.base = png(Image.new('RGBA', (16,16), (35,55,20,255)))
        self.insert = png(Image.new('RGBA', (8,8), (80,75,40,255)))
        self.box = [1,1,3,3]

    def replay(self):
        return patch.replay(self.source, self.base, self.insert, self.box)

    def test_only_rectangle_changes_with_exact_alpha(self):
        output,receipt=self.replay();before,_=patch.bounds.repair(self.source,Image.open(io.BytesIO(self.base)))
        mask=np.zeros((16,16),bool);mask[4:12,4:12]=True
        np.testing.assert_array_equal(np.asarray(before)[~mask],np.asarray(output)[~mask])
        np.testing.assert_array_equal(np.asarray(before)[:,:,3],np.asarray(output)[:,:,3])
        self.assertEqual(64,receipt['changedPixels']);self.assertFalse(receipt['sourceRgbPixelsCopied'])

    def test_original_holes_and_visible_black_survive_patch(self):
        self.source.putpixel((1,1),(0,0,0,0));self.source.putpixel((2,2),(0,0,0,255))
        output,receipt=self.replay();array=np.asarray(output)
        self.assertTrue(np.all(array[4:8,4:8]==0));self.assertTrue(np.all(array[8:12,8:12,:3]==0));self.assertTrue(np.all(array[8:12,8:12,3]==255))
        self.assertTrue(receipt['patchCutoutRepair']['visibleBlackExact'])

    def test_rectangle_types_bounds_and_area_are_enforced(self):
        for box in [[-1,1,2,2],[1,1,5,2],[1,1,1,2],[True,1,2,2],[1.0,1,2,2],[0,0,4,4]]:
            with self.subTest(box=box):
                self.box=box
                with self.assertRaises(ValueError):self.replay()

    def test_source_mode_and_dimensions_are_bounded(self):
        self.source=self.source.convert('RGB')
        with self.assertRaisesRegex(ValueError,'RGBA'):self.replay()
        self.source=Image.new('RGBA',(1000,1000))
        with self.assertRaisesRegex(ValueError,'source image|Source image'):self.replay()

    def test_patch_aspect_cannot_be_hidden_by_the_full_canvas(self):
        self.insert=png(Image.new('RGBA',(8,12),(80,75,40,255)))
        with self.assertRaisesRegex(ValueError,'aspect'):self.replay()

    def test_base_aspect_cannot_be_hidden_by_the_patch(self):
        self.base=png(Image.new('RGBA',(16,20),(35,55,20,255)))
        with self.assertRaisesRegex(ValueError,'aspect'):self.replay()

    def test_patch_coverage_does_not_borrow_base_pixels(self):
        self.insert=png(Image.new('RGBA',(8,8),(80,75,40,0)))
        with self.assertRaisesRegex(ValueError,'coverage|uncovered|anchors'):self.replay()

    def test_bad_extra_headers_precede_base_pixel_decode(self):
        for data in [fixtures.compressed_png(4096,4096),fixtures.duplicate_header_png()]:
            with self.subTest(data_size=len(data)):
                self.insert=data
                with mock.patch.object(Image.Image,'load',side_effect=AssertionError('pixel allocation before header rejection')):
                    with self.assertRaises(ValueError):self.replay()

    def test_exact_inputs_and_operations_are_deterministic(self):
        first,one=self.replay();second,two=self.replay()
        self.assertEqual(png(first),png(second));self.assertEqual(one,two)
        self.assertEqual(patch.bounds.digest(self.base),one['baseInputSha256']);self.assertEqual(patch.bounds.digest(self.insert),one['patchInputSha256'])


if __name__ == '__main__':
    unittest.main()
