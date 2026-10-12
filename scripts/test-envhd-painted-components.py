#!/usr/bin/env python3
"""Component transfers preserve geometry, reject unsafe crops and bind exact replay."""
import copy
import importlib.util
import io
from pathlib import Path
import unittest
from unittest import mock

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('component_tests', Path(__file__).with_name('repair-envhd-painted-components.py'))
components = importlib.util.module_from_spec(spec)
spec.loader.exec_module(components)
fixture_spec = importlib.util.spec_from_file_location('component_png_fixtures', Path(__file__).with_name('test-envhd-painted-cutout.py'))
fixtures = importlib.util.module_from_spec(fixture_spec)
fixture_spec.loader.exec_module(fixtures)


def png(image):
    stream = io.BytesIO();image.save(stream, format='PNG');return stream.getvalue()


class ComponentTests(unittest.TestCase):
    def setUp(self):
        self.source = Image.new('RGBA', (3,3), (40,70,30,255))
        self.base = Image.new('RGBA', (12,12), (35,55,20,255))
        self.insert = Image.new('RGBA', (12,12), (100,90,70,255))
        self.regions = [dict(name='detail',nativeBox=[1,1,2,2],insertBox=[2,2,10,10],maskBox=[1,1,2,2],channelRatios=[[1,0,1]])]
        self.allowed = [1,1,2,2]

    def transfer(self):
        return components.transfer(self.source,self.base,self.insert,self.regions,self.allowed)

    def test_only_explicit_component_changes_and_alpha_is_exact(self):
        output,receipt=self.transfer();before=np.asarray(self.base);after=np.asarray(output)
        mask=np.zeros((12,12),bool);mask[4:8,4:8]=True
        np.testing.assert_array_equal(before[~mask],after[~mask]);np.testing.assert_array_equal(before[:,:,3],after[:,:,3])
        self.assertTrue(np.any(before[mask]!=after[mask]));self.assertEqual(16,receipt['changedPixels']);self.assertFalse(receipt['sourceRgbPixelsCopied'])

    def test_source_class_controls_exclude_pixels_outside_mask(self):
        pixels=np.asarray(self.source).copy();pixels[1,1,:3]=[90,20,10];self.source=Image.fromarray(pixels)
        with self.assertRaisesRegex(ValueError,'mask does not cover'):self.transfer()

    def test_rectangle_bounds_types_and_allowed_region_are_enforced(self):
        for value in [[-1,1,2,2],[1,1,4,2],[1,1,1,2],[1.0,1,2,2],[True,1,2,2],[0,0,2,2]]:
            with self.subTest(value=value):
                self.regions[0]['nativeBox']=value
                with self.assertRaises(ValueError):self.transfer()

    def test_insert_out_of_bounds_is_rejected(self):
        self.regions[0]['insertBox']=[2,2,13,10]
        with self.assertRaisesRegex(ValueError,'rectangle'):self.transfer()

    def test_ratio_types_nonfinite_and_range_are_rejected(self):
        for ratio in [[1,0,float('nan')],[1,0,float('inf')],[1,0,5],[1,0,True],[1,1,1],[3,0,1]]:
            with self.subTest(ratio=ratio):
                self.regions[0]['channelRatios']=[ratio]
                with self.assertRaisesRegex(ValueError,'channel ratios'):self.transfer()

    def test_region_count_names_and_schema_are_bounded(self):
        for regions in [[],self.regions*5,[self.regions[0],self.regions[0]],[dict(self.regions[0],unknown=True)]]:
            with self.subTest(regions=regions):
                with self.assertRaises(ValueError):components.transfer(self.source,self.base,self.insert,regions,self.allowed)

    def test_changed_base_alpha_and_dimensions_are_rejected(self):
        self.base=Image.new('RGBA',(12,12),(35,55,20,254))
        with self.assertRaisesRegex(ValueError,'exact source alpha'):self.transfer()
        self.base=Image.new('RGBA',(16,12),(35,55,20,255))
        with self.assertRaisesRegex(ValueError,'output dimensions'):self.transfer()

    def test_transparent_or_black_component_cannot_satisfy_coverage(self):
        for colour in [(100,90,70,0),(0,0,0,255)]:
            with self.subTest(colour=colour):
                self.insert=Image.new('RGBA',(12,12),colour)
                with self.assertRaisesRegex(ValueError,'coverage'):self.transfer()

    def test_zero_channel_and_excessive_exposure_gains_are_rejected(self):
        for colour in [(100,90,0,255),(1,1,1,255)]:
            with self.subTest(colour=colour):
                self.insert=Image.new('RGBA',(12,12),colour)
                with self.assertRaisesRegex(ValueError,'zero channel|gain'):self.transfer()

    def test_replay_uses_original_base_cutout_before_component_transfer(self):
        output,receipt=components.replay(self.source,png(self.base),png(self.insert),self.regions,self.allowed)
        expected,_=self.transfer();np.testing.assert_array_equal(np.asarray(output),np.asarray(expected))
        self.assertEqual(components.bounds.digest(png(self.base)),receipt['baseInputSha256']);self.assertTrue(receipt['baseCutoutRepair']['sourceAlphaExact'])

    def test_oversized_and_duplicate_headers_rejected_before_pixel_allocation(self):
        base_data=png(self.base)
        for data in [fixtures.compressed_png(),fixtures.duplicate_header_png(first=(12,12))]:
            with self.subTest(size=len(data)):
                with mock.patch.object(Image.Image,'load',side_effect=AssertionError('Pixel allocation')):
                    with self.assertRaisesRegex(ValueError,'bounded painted canvas'):
                        components.replay(self.source,base_data,data,self.regions,self.allowed)


if __name__=='__main__':unittest.main()
