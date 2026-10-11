#!/usr/bin/env python3
"""Perspective registration must preserve orientation and stable canvas geometry."""
import importlib.util
from pathlib import Path
import unittest

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('registration', Path(__file__).with_name('register-envhd-painted-cutout.py'))
registration = importlib.util.module_from_spec(spec);spec.loader.exec_module(registration)


class RegistrationTests(unittest.TestCase):
    def setUp(self):
        self.source=Image.new('RGBA',(2,2),(35,65,20,255))
        self.points=[[0,0],[2,0],[2,2],[0,2]]

    def test_identity_registration_retains_decoded_painted_pixels(self):
        pixels=np.arange(8*8*4,dtype=np.uint8).reshape(8,8,4);pixels[:,:,3]=255
        painted=Image.fromarray(pixels,'RGBA')
        result,operation=registration.register(self.source,painted,self.points,self.points)
        np.testing.assert_array_equal(np.asarray(result),pixels)
        self.assertFalse(operation['sourceAlphaApplied']);self.assertEqual([8,8],operation['targetSize'])

    def test_reversed_or_crossed_geometry_is_rejected(self):
        painted=Image.new('RGBA',(8,8),(35,65,20,255))
        for points in [list(reversed(self.points)),[[0,0],[2,2],[2,0],[0,2]]]:
            with self.assertRaises(ValueError):registration.register(self.source,painted,self.points,points)

    def test_canvas_escape_nonfinite_and_degenerate_geometry_are_rejected(self):
        painted=Image.new('RGBA',(8,8),(35,65,20,255))
        for points in [[[-1,0],[2,0],[2,2],[0,2]],[[float('nan'),0],[2,0],[2,2],[0,2]],[[0,0],[1,0],[2,0],[0,2]]]:
            with self.assertRaises(ValueError):registration.register(self.source,painted,self.points,points)

    def test_perspective_repair_does_not_apply_source_visibility(self):
        source=Image.fromarray(np.array([[[35,65,20,255],[0,0,0,0]],[[0,0,0,0],[35,65,20,255]]],dtype=np.uint8),'RGBA')
        result,_=registration.register(source,Image.new('RGBA',(8,8),(35,65,20,255)),self.points,self.points)
        self.assertEqual((255,255),result.getchannel('A').getextrema())


if __name__=='__main__':unittest.main()
