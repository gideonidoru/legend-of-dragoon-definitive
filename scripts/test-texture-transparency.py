#!/usr/bin/env python3
"""Original synthetic pixel fixtures for PSX mask preservation, AGPL v3."""
import importlib.util
from pathlib import Path
import unittest
from PIL import Image
spec=importlib.util.spec_from_file_location('transparency',Path(__file__).with_name('preserve-texture-transparency.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class TransparencyTest(unittest.TestCase):
    def test_discard_stp_black_and_coloured_alpha_zero(self):
        source=Image.new('RGBA',(3,1));source.putdata([(0,0,0,0),(0,0,0,255),(90,20,10,0)])
        candidate=Image.new('RGBA',(6,2),(100,80,30,255))
        engine,preview=module.preserve(source,candidate)
        self.assertEqual(engine.getpixel((0,0)),(0,0,0,0));self.assertEqual(preview.getpixel((0,0))[3],0)
        self.assertEqual(engine.getpixel((2,0)),(0,0,0,255));self.assertEqual(preview.getpixel((2,0))[3],255)
        self.assertEqual(engine.getpixel((4,0)),(100,80,30,0));self.assertEqual(preview.getpixel((4,0))[3],255)
        black=Image.new('RGBA',(6,2),(0,0,0,255));engine,_=module.preserve(source,black)
        self.assertEqual(engine.getpixel((4,0)),(90,20,10,0))
    def test_invalid_scale_and_nonbinary_source_alpha(self):
        with self.assertRaises(ValueError):module.preserve(Image.new('RGBA',(2,2)),Image.new('RGBA',(3,3)))
        with self.assertRaises(ValueError):module.preserve(Image.new('RGBA',(1,1),(1,2,3,128)),Image.new('RGBA',(2,2)))
if __name__=='__main__':unittest.main()
