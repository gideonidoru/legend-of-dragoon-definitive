#!/usr/bin/env python3
"""Original RGB patch provenance and allocation bounds cannot be bypassed."""
import importlib.util
import io
from pathlib import Path
import unittest
from unittest import mock
import numpy as np
from PIL import Image


def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result


patch=module('rgb_local_test','repair-envhd-painted-rgb-local-patch.py')
fixtures=module('rgb_local_png_fixtures','test-envhd-painted-cutout.py')

def png(image):
    stream=io.BytesIO();image.save(stream,format='PNG');return stream.getvalue()


class RgbLocalPatchTests(unittest.TestCase):
    def setUp(self):
        self.source=Image.new('RGBA',(4,4),(20,40,15,255))
        self.base=png(Image.new('RGBA',(16,16),(35,55,20,255)))
        self.insert=png(Image.new('RGB',(8,8),(80,75,40)))
        self.box=[1,1,3,3]

    def replay(self):return patch.replay(self.source,self.base,self.insert,self.box)

    def test_rgb_pixels_preserved_and_outside_alpha_exact(self):
        output,receipt=self.replay();converted,container=patch.rgb.repack(self.insert,(2,2))
        expected,old=patch.local.replay(self.source,self.base,converted,self.box)
        np.testing.assert_array_equal(np.asarray(expected),np.asarray(output))
        self.assertEqual(patch.bounds.digest(self.insert),receipt['patchInputSha256'])
        self.assertEqual(container['convertedInputSha256'],receipt['rgbPatchRepack']['convertedInputSha256'])
        self.assertTrue(receipt['rgbPatchRepack']['rgbPixelsExact']);self.assertTrue(receipt['outsideSourceBoxExact'])
        self.assertFalse(receipt['sourceRgbPixelsCopied']);self.assertEqual(patch.METHOD,receipt['method'])

    def test_rgba_input_cannot_claim_original_rgb_protocol(self):
        self.insert=png(Image.new('RGBA',(8,8),(80,75,40,255)))
        with self.assertRaisesRegex(ValueError,'original opaque RGB'):self.replay()

    def test_invalid_headers_precede_any_pixel_decode(self):
        for role in ['base','insert']:
            for bad in [fixtures.compressed_png(4096,4096),fixtures.duplicate_header_png()]:
                with self.subTest(role=role):
                    old=getattr(self,role);setattr(self,role,bad)
                    with mock.patch.object(Image.Image,'load',side_effect=AssertionError('early pixel allocation')):
                        with self.assertRaises(ValueError):self.replay()
                    setattr(self,role,old)

    def test_bounds_and_patch_aspect_remain_enforced(self):
        for box in [[0,0,4,4],[True,0,2,2],[-1,1,2,2]]:
            self.box=box
            with self.assertRaises(ValueError):self.replay()
        self.box=[1,1,3,3];self.insert=png(Image.new('RGB',(8,12),(80,75,40)))
        with self.assertRaisesRegex(ValueError,'aspect'):self.replay()

    def test_changed_dependency_fails_before_repack(self):
        with mock.patch.object(patch,'LOCAL_SHA256','0'*64),mock.patch.object(patch.rgb,'repack',side_effect=AssertionError('decode after dependency drift')):
            with self.assertRaisesRegex(ValueError,'dependency'):self.replay()

    def test_source_holes_black_and_deterministic_replay(self):
        self.source.putpixel((1,1),(0,0,0,0));self.source.putpixel((2,2),(0,0,0,255));one,a=self.replay();two,b=self.replay()
        self.assertEqual(png(one),png(two));self.assertEqual(a,b);array=np.asarray(one)
        self.assertTrue(np.all(array[4:8,4:8]==0));self.assertTrue(np.all(array[8:12,8:12,:3]==0))


if __name__=='__main__':unittest.main()
