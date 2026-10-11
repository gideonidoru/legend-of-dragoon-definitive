#!/usr/bin/env python3
"""Bounded padded crops preserve geometry and reject lost/clipped painted art."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('crop_test', Path(__file__).with_name('crop-envhd-painted-authoring-canvas.py'))
crop = importlib.util.module_from_spec(spec)
spec.loader.exec_module(crop)


class AuthoringCropTests(unittest.TestCase):
    def setUp(self):
        pixels = np.zeros((4,16,4), dtype=np.uint8)
        pixels[1:4,2:14] = (30,50,70,255)
        pixels[2:4,1:2] = (25,40,55,255)
        self.source = Image.fromarray(pixels,'RGBA')
        self.canvas = [32,16];self.origin = [8,6]
        self.painted = Image.new('RGBA',(128,64))
        detail = self.source.resize((64,16),Image.Resampling.NEAREST)
        array = np.asarray(detail).copy();array[4,8] = (77,88,99,255)
        self.painted.paste(Image.fromarray(array,'RGBA'),(32,24))

    def test_exact_explicit_crop_within_a_supported_canvas(self):
        output,receipt = crop.crop(self.source,self.painted,self.canvas,self.origin)
        self.assertEqual(output.size,(64,16));self.assertEqual(receipt['sourceSilhouetteIoU'],1)
        self.assertEqual(receipt['cropRectAtTargetScale'],[32,24,96,40])
        self.assertEqual(output.tobytes(),self.painted.crop((32,24,96,40)).tobytes())

    def test_painted_detail_is_retained_without_copying_source_rgb_or_alpha(self):
        output,receipt = crop.crop(self.source,self.painted,self.canvas,self.origin)
        self.assertEqual(output.getpixel((8,4)),(77,88,99,255))
        self.assertFalse(receipt['sourceRgbApplied']);self.assertFalse(receipt['sourceAlphaApplied'])
        self.assertEqual(receipt['generatedVisibleOutsideSourceCrop'],0)

    def test_inputs_unchanged_and_receipt_deterministic(self):
        before = self.source.tobytes(),self.painted.tobytes()
        first,one = crop.crop(self.source,self.painted,self.canvas,self.origin)
        second,two = crop.crop(self.source,self.painted,self.canvas,self.origin)
        self.assertEqual(one,two);self.assertEqual(first.tobytes(),second.tobytes())
        self.assertEqual(before,(self.source.tobytes(),self.painted.tobytes()))

    def test_integer_geometry_rejects_float_bool_nested_and_missing_values(self):
        for canvas,origin in [([32.0,16],[8,6]),([True,16],[8,6]),([32,16],[8.0,6]),([32,16],[8,False]),(None,[8,6]),([32,16],None),([[32],16],[8,6])]:
            with self.subTest(canvas=canvas,origin=origin),self.assertRaises(ValueError):
                crop.crop(self.source,self.painted,canvas,origin)

    def test_every_side_must_have_explicit_padding(self):
        for origin in [[0,6],[8,0],[-1,6],[16,6],[8,12]]:
            with self.subTest(origin=origin),self.assertRaisesRegex(ValueError,'padding'):
                crop.crop(self.source,self.painted,self.canvas,origin)

    def test_canvas_may_not_hide_the_source_in_arbitrary_empty_space(self):
        with self.assertRaisesRegex(ValueError,'fraction'):
            crop.validate_layout(self.source.size,[256,128],[100,60])

    def test_canvas_source_and_painted_dimensions_checked_before_resize(self):
        with mock.patch.object(Image.Image,'resize',side_effect=AssertionError('allocated')):
            with self.assertRaisesRegex(ValueError,'bounded repair canvas'):
                crop.crop(self.source,self.painted,[4096,4096],[8,6])
            with self.assertRaisesRegex(ValueError,'aspect ratio'):
                crop.crop(self.source,Image.new('RGBA',(128,100)),self.canvas,self.origin)
            with self.assertRaisesRegex(ValueError,'bounded painted canvas'):
                crop.crop(self.source,Image.new('RGBA',(4097,1)),self.canvas,self.origin)

    def test_rgb_inputs_cannot_fake_transparent_authoring_padding(self):
        for source,painted in [(self.source.convert('RGB'),self.painted),(self.source,self.painted.convert('RGB'))]:
            with self.subTest(source=source.mode,painted=painted.mode),self.assertRaisesRegex(ValueError,'RGBA'):
                crop.crop(source,painted,self.canvas,self.origin)

    def test_opaque_or_empty_original_has_no_eligible_silhouette(self):
        for alpha in [0,255]:
            source=Image.new('RGBA',self.source.size,(30,50,70,alpha))
            with self.subTest(alpha=alpha),self.assertRaisesRegex(ValueError,'silhouette'):
                crop.crop(source,self.painted,self.canvas,self.origin)

    def test_shifted_original_outline_is_rejected_instead_of_recropped(self):
        shifted=Image.new('RGBA',self.painted.size);shifted.paste(self.painted,(4,0))
        with self.assertRaisesRegex(ValueError,'silhouette'):
            crop.crop(self.source,shifted,self.canvas,self.origin)

    def test_unexpected_visible_padding_cannot_be_clipped_away(self):
        painted=self.painted.copy()
        # Less than3% total silhouette change passes IoU, but >1% would be lost.
        count=int(np.count_nonzero(np.asarray(painted)[:,:,3]>=128)*0.02)
        for x in range(count):painted.putpixel((x,0),(50,60,70,255))
        with self.assertRaisesRegex(ValueError,'clipped'):
            crop.crop(self.source,painted,self.canvas,self.origin)

    def test_large_deleted_coverage_and_completed_subject_are_rejected(self):
        for painted in [Image.new('RGBA',self.painted.size),Image.new('RGBA',self.painted.size,(50,60,70,255))]:
            with self.assertRaisesRegex(ValueError,'silhouette'):
                crop.crop(self.source,painted,self.canvas,self.origin)

    def test_original_bounds_pin_cannot_be_changed(self):
        with mock.patch.object(crop.bounds,'digest',return_value='0'*64):
            with self.assertRaisesRegex(ValueError,'bounds identity'):
                crop.crop(self.source,self.painted,self.canvas,self.origin)

    def test_old_cutout_visibility_step_remains_separate(self):
        cropped,receipt=crop.crop(self.source,self.painted,self.canvas,self.origin)
        output,operation=crop.bounds.repair(self.source,cropped)
        self.assertTrue(operation['sourceAlphaExact']);self.assertTrue(operation['visibleBlackExact'])
        self.assertEqual(output.getpixel((8,4)),(77,88,99,255))
        self.assertEqual(receipt['originalBoundsAlgorithmSha256'],crop.REPAIR_SHA256)


if __name__=='__main__':unittest.main()
