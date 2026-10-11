#!/usr/bin/env python3
"""Generic painted battle repair preserves native classes without claiming geometry."""
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


repair = module('material_test', 'repair-envhd-painted-battle-material.py')
duplicate = module('material_header_fixture', 'test-envhd-painted-cutout.py').duplicate_header_png


class PaintedMaterialTests(unittest.TestCase):
    def source(self):
        return Image.fromarray(np.array([[[70,90,70,0],[0,0,0,0]],[[0,0,0,255],[170,185,170,255]]],dtype=np.uint8))

    def test_stp_zero_colour_discard_and_visible_black_are_exact(self):
        source=self.source();output,preview,record=repair.repair(source,Image.new('RGB',(8,8),(190,180,170)))
        original=np.repeat(np.repeat(np.asarray(source),4,axis=0),4,axis=1);actual=np.asarray(output)
        self.assertTrue(np.array_equal(actual[:,:,3],original[:,:,3]));self.assertTrue(np.array_equal(np.all(actual[:,:,:3]==0,axis=2),np.all(original[:,:,:3]==0,axis=2)))
        self.assertTrue((np.asarray(preview)[:4,:4,3]==255).all())
        self.assertEqual(record['internalFeatureAlignment'],'individual-source-comparison-required');self.assertFalse(record['runtimeSelected'])

    def test_partial_material_and_wrong_aspect_rejected(self):
        with self.assertRaisesRegex(ValueError,'entire source canvas'):repair.repair(self.source(),Image.new('RGBA',(8,8),(120,120,120,0)))
        with self.assertRaisesRegex(ValueError,'aspect'):repair.repair(self.source(),Image.new('RGB',(8,12)))

    def test_duplicate_headers_reject_before_pixels_and_source_identity_is_required(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory);source=p/'source.png';raw=p/'raw.png';output=p/'output.png';self.source().save(source);raw.write_bytes(duplicate(first=(8,8),colour_type=2))
            with mock.patch.object(Image.Image,'convert',side_effect=AssertionError('Allocation')):
                with self.assertRaisesRegex(ValueError,'bounded painted canvas'):repair.execute(source,raw,output,repair.terrain.fingerprint(self.source()))
            Image.new('RGB',(8,8),(190,180,170)).save(raw)
            with self.assertRaisesRegex(ValueError,'identity'):repair.execute(source,raw,output,'wrong')
            record,_=repair.execute(source,raw,output,repair.terrain.fingerprint(self.source()));before=output.read_bytes();again,_=repair.execute(source,raw,output,repair.terrain.fingerprint(self.source()));self.assertEqual(record,again);self.assertEqual(before,output.read_bytes())


if __name__ == '__main__':unittest.main()
