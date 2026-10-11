#!/usr/bin/env python3
"""Circular lattice repair keeps original topology and rejects malformed input."""
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


repair = module('circle_tests', 'repair-envhd-painted-battle-circle.py')
duplicate = module('circle_header_fixture', 'test-envhd-painted-cutout.py').duplicate_header_png
COLOUR = [148, 172, 148, 0]


class CircleRepairTests(unittest.TestCase):
    def source(self):
        pixels = np.tile(np.array([213,255,222,0], dtype=np.uint8), (16,16,1))
        pixels[6:9,7] = COLOUR; pixels[8,8:10] = COLOUR
        return Image.fromarray(pixels)

    def test_exact_source_topology_preserves_stp_zero_visible_material(self):
        source = self.source()
        output, preview, receipt = repair.repair(source, Image.new('RGB',(64,64),(180,209,190)), COLOUR, [7.5,7.5,3],4)
        actual = np.asarray(output)
        expected = np.repeat(np.repeat(np.all(np.asarray(source)==COLOUR,axis=2),4,axis=0),4,axis=1)
        yy,xx=np.mgrid[:16,:16];zone=np.repeat(np.repeat((xx-7.5)**2+(yy-7.5)**2<=9,4,axis=0),4,axis=1)
        self.assertTrue(np.array_equal(zone & (actual[:,:,:3].mean(axis=2)<185), expected))
        self.assertTrue((actual[:,:,3]==0).all());self.assertTrue((np.asarray(preview)[:,:,3]==255).all())
        self.assertEqual(receipt['sourceMotifPixels'],5);self.assertFalse(receipt['runtimeSelected'])

    def test_invalid_circle_palette_and_partial_material_are_rejected(self):
        for circle,lighting in [([7.5,7.5,float('nan')],4),([7.5,7.5,3],3),([0,0,3],4)]:
            with self.assertRaises(ValueError):repair.repair(self.source(),Image.new('RGB',(64,64)),COLOUR,circle,lighting)
        with self.assertRaisesRegex(ValueError,'lattice/background'):
            repair.repair(self.source(),Image.new('RGB',(64,64)),[1,2,3,0],[7.5,7.5,3],4)
        with self.assertRaisesRegex(ValueError,'entire source canvas'):
            repair.repair(self.source(),Image.new('RGBA',(64,64),(213,255,222,0)),COLOUR,[7.5,7.5,3],4)

    def test_exact_discard_visible_black_and_stp(self):
        pixels = np.array(self.source());pixels[0,0]=[0,0,0,0];pixels[0,1]=[0,0,0,255];pixels[10,10,3]=255
        output,_,_=repair.repair(Image.fromarray(pixels),Image.new('RGB',(64,64),(220,245,230)),COLOUR,[7.5,7.5,3],4)
        expected=np.repeat(np.repeat(pixels,4,axis=0),4,axis=1);actual=np.asarray(output)
        self.assertTrue(np.array_equal(actual[:,:,3],expected[:,:,3]))
        self.assertTrue(np.array_equal(np.all(actual[:,:,:3]==0,axis=2),np.all(expected[:,:,:3]==0,axis=2)))

    def test_two_source_shades_preserve_both_classes_and_reject_duplicate_or_absent_class(self):
        pixels=np.array(self.source());pixels[6,8]=[131,156,139,0];source=Image.fromarray(pixels)
        output,_,receipt=repair.repair(source,Image.new('RGB',(64,64),(220,245,230)),[[131,156,139,0],COLOUR],[7.5,7.5,3],4)
        self.assertEqual(receipt['sourceMotifPalettePixels'],[1,5]);self.assertEqual(receipt['sourceMotifPixels'],6)
        self.assertLess(np.asarray(output)[24:28,32:36,:3].mean(),np.asarray(output)[24:28,28:32,:3].mean())
        for palette in [[COLOUR,COLOUR],[COLOUR,[1,2,3,0]]]:
            with self.assertRaises(ValueError):repair.repair(source,Image.new('RGB',(64,64)),palette,[7.5,7.5,3],4)

    def test_duplicate_source_and_painted_headers_fail_before_allocation(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory).resolve();source=p/'source.png';raw=p/'raw.png';output=p/'output.png'
            raw.write_bytes(duplicate(first=(64,64),colour_type=2));self.source().save(source)
            with mock.patch.object(Image.Image,'convert',side_effect=AssertionError('Allocation')):
                with self.assertRaisesRegex(ValueError,'bounded painted canvas'):
                    repair.execute(source,raw,output,repair.terrain.fingerprint(self.source()),COLOUR,[7.5,7.5,3],4)
            source.write_bytes(duplicate(first=(16,16)));Image.new('RGB',(64,64)).save(raw)
            with mock.patch.object(Image.Image,'load',side_effect=AssertionError('Allocation')):
                with self.assertRaisesRegex(ValueError,'bounded repair canvas'):
                    repair.execute(source,raw,output,'unused',COLOUR,[7.5,7.5,3],4)
            self.assertFalse(output.exists())

    def test_cli_recipe_reproduces_exact_bytes_and_rejects_source_change(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory).resolve();source=p/'source.png';raw=p/'raw.png';output=p/'output.png'
            self.source().save(source);Image.new('RGB',(64,64),(220,245,230)).save(raw);key=repair.terrain.fingerprint(self.source())
            with self.assertRaisesRegex(ValueError,'identity'):
                repair.execute(source,raw,output,'wrong',COLOUR,[7.5,7.5,3],4)
            record,_=repair.execute(source,raw,output,key,COLOUR,[7.5,7.5,3],4);before=output.read_bytes()
            again,_=repair.execute(source,raw,p/'repeat.png',key,COLOUR,[7.5,7.5,3],4)
            self.assertEqual(before,output.read_bytes());self.assertEqual(record,again)


if __name__ == '__main__':unittest.main()
