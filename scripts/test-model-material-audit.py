#!/usr/bin/env python3
"""Original AGPL synthetic fixtures: bounds, palette conflicts, and SC zero-padding variant."""
import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location('materials', Path(__file__).with_name('audit-model-materials.py'))
materials = importlib.util.module_from_spec(spec); spec.loader.exec_module(materials)

def model(conflict=False):
    table = struct.pack('<7I', 28, 3, 52, 1, 60, 2 if conflict else 1, 0)
    vertices = bytes(24); normals = bytes(8)
    def packet(clut):
        return bytes([7,5,0,0x24]) + struct.pack('<2BH2BH2BH4H',0,0,clut,3,0,0,0,3,0,0,0,1,2)
    return struct.pack('<6I',12,0,0,0x41,0,1) + table + vertices + normals + packet(0) + (packet(1) if conflict else b'')

def tim(padded=False):
    colours = [0]*32; colours[1] = 31; colours[17] = 0x7c00
    words = 32 if padded else 1; height = 4
    pixels = bytes([0x11]) * (words * height * 2)
    if padded: pixels += bytes(64)
    size = len(pixels) if padded else len(pixels)+12
    return struct.pack('<3I4H',0x10,8,76,0,0,32,1) + struct.pack('<32H',*colours) + struct.pack('<I4H',size,0,0,words,height) + pixels

class MaterialAuditTest(unittest.TestCase):
    def test_colours_follow_materials_and_conflicts_fail_flattening(self):
        report, preview, engine = materials.audit(model(), tim())
        self.assertEqual(report['conflictingColourTexels'],0)
        self.assertEqual(preview.getpixel((0,0)),(255,0,0,255))
        self.assertEqual(engine.getpixel((0,0)),(255,0,0,0))
        report, _, _ = materials.audit(model(True),tim())
        self.assertGreater(report['conflictingColourTexels'],0)
    def test_strict_ranges_and_known_zero_padding(self):
        with self.assertRaises(ValueError): materials.audit(model()[:-5],tim())
        with self.assertRaises(ValueError): materials.audit(model(),tim()[:-1])
        materials.audit(model(),tim(True))
        corrupt=bytearray(tim(True));corrupt[-1]=1
        with self.assertRaises(ValueError): materials.audit(model(),bytes(corrupt))

if __name__ == '__main__': unittest.main()
