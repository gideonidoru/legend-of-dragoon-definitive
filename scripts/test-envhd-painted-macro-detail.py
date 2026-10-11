#!/usr/bin/env python3
"""Behavior checks for source-anchored, unselected static painted candidates."""
import hashlib
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('macro', Path(__file__).with_name('repair-envhd-painted-macro-detail.py'))
macro = importlib.util.module_from_spec(spec)
spec.loader.exec_module(macro)


class CandidateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.source = Image.new('RGBA', (8, 8), (40, 80, 120, 255))
        self.paint = Image.new('RGBA', (32, 32), (240, 160, 30, 255))

    def execute(self, source=None, painted=None, **kwargs):
        source = source if source is not None else self.source
        painted = painted if painted is not None else self.paint
        source_path, painted_path = self.root/'source.png', self.root/'painted.png'
        source.save(source_path)
        painted.save(painted_path)
        args = dict(source_path=source_path, painted_path=painted_path,
                    output_path=self.root/'out.png',
                    source_key=hashlib.sha256(struct.pack('<II', *source.size)+source.tobytes()).hexdigest(),
                    painted_sha256=hashlib.sha256(painted_path.read_bytes()).hexdigest())
        args.update(kwargs)
        return macro.execute(**args)

    def test_wrong_broad_palette_cannot_replace_source_colour(self):
        result, receipt = macro.refine(self.source, self.paint)
        self.assertEqual(result.size, (32, 32))
        self.assertTrue(np.array_equal(np.asarray(result), np.asarray(self.source.resize((32,32),Image.Resampling.NEAREST))))
        self.assertFalse(receipt['runtimeSelected'])

    def test_invented_relief_keeps_every_native_pixel_mean(self):
        pixels = np.asarray(self.paint).copy()
        pixels[:16,:16,:3] = (15,240,70)
        pixels[::2,::2,:3] = (255,1,200)
        source = np.asarray(self.source).copy()
        source[4:,:,:3] = (110,40,160)
        result, receipt = macro.refine(Image.fromarray(source), Image.fromarray(pixels))
        means = np.asarray(result)[:,:,:3].reshape(8,4,8,4,3).mean(axis=(1,3))
        self.assertLessEqual(np.abs(means-source[:,:,:3]).max(), 0.5)
        # The source already varies between rows; whole-image variance cannot
        # prove the recipe kept any generated detail. Inspect a uniform source
        # cell, then compare against the same source with a flat painting.
        cell = np.asarray(result)[8:12,8:12,:3]
        self.assertGreater(np.ptp(cell,axis=(0,1)).max(), 0)
        flat_result,_ = macro.refine(Image.fromarray(source), self.paint)
        self.assertFalse(np.array_equal(np.asarray(result),np.asarray(flat_result)))
        self.assertLessEqual(receipt['maximumNativeCellMeanChannelDrift'], 0.5)

    def test_alpha_black_and_hidden_rgb_are_exact(self):
        source = np.asarray(self.source).copy()
        source[0,0] = (0,0,0,255)
        source[0,1] = (11,17,33,0)
        source[0,2,3] = 128
        source[0,3] = (0,255,1,128)
        result,_ = macro.refine(Image.fromarray(source), self.paint)
        expected = np.repeat(np.repeat(source,4,axis=0),4,axis=1)
        actual = np.asarray(result)
        self.assertTrue(np.array_equal(actual[:,:,3], expected[:,:,3]))
        self.assertTrue(np.array_equal(np.all(actual[:,:,:3]==0,axis=2), np.all(expected[:,:,:3]==0,axis=2)))
        self.assertTrue(np.array_equal(actual[:4,:8], expected[:4,:8]))
        self.assertTrue(np.all(actual[:4,12:16,0]==0))
        self.assertTrue(np.all(actual[:4,12:16,1]==255))
        self.assertTrue(np.all(actual[:4,12:16,2]>=1))

    def test_original_aspect_and_coverage_gates_stay_in_force(self):
        with self.assertRaisesRegex(ValueError, 'aspect'):
            macro.refine(self.source, Image.new('RGBA',(32,31),(50,60,70,255)))
        with self.assertRaisesRegex(ValueError, 'coverage'):
            macro.refine(self.source, Image.new('RGBA',(32,32),(50,60,70,0)))

    def test_rgb_only_allowed_for_fully_opaque_source(self):
        self.execute(painted=self.paint.convert('RGB'))
        (self.root/'out.png').unlink()
        translucent = self.source.copy()
        translucent.putpixel((0,0),(40,80,120,128))
        with self.assertRaisesRegex(ValueError, 'nonopaque'):
            self.execute(source=translucent, painted=self.paint.convert('RGB'))
        self.assertFalse((self.root/'out.png').exists())

    def test_input_identity_mismatch_creates_nothing(self):
        for argument in ('source_key','painted_sha256'):
            with self.subTest(argument=argument), self.assertRaisesRegex(ValueError, 'identity'):
                self.execute(**{argument:'0'*64})
            self.assertFalse((self.root/'out.png').exists())

    def test_source_mutation_during_repair_aborts_publication(self):
        original = macro.refine
        def mutate(source, painted):
            result = original(source, painted)
            self.source.save(self.root/'changed.png')
            (self.root/'source.png').write_bytes(b'changed')
            return result
        with patch.object(macro, 'refine', side_effect=mutate), self.assertRaisesRegex(ValueError,'changed before'):
            self.execute()
        self.assertFalse((self.root/'out.png').exists())

    def test_existing_output_and_symlink_are_preserved(self):
        output = self.root/'out.png'
        output.write_bytes(b'foreign history')
        with self.assertRaises((ValueError, FileExistsError)):
            self.execute()
        self.assertEqual(output.read_bytes(), b'foreign history')
        output.unlink()
        target = self.root/'protected'
        target.write_bytes(b'protected')
        output.symlink_to(target)
        with self.assertRaises((ValueError, FileExistsError)):
            self.execute()
        self.assertTrue(output.is_symlink())
        self.assertEqual(target.read_bytes(), b'protected')

    def test_deterministic_output_and_bound_receipt(self):
        a = self.execute()
        before = (self.root/'out.png').read_bytes()
        (self.root/'out.png').unlink()
        b = self.execute()
        self.assertEqual(a,b)
        self.assertEqual(before, (self.root/'out.png').read_bytes())
        self.assertEqual(a['outputSha256'], hashlib.sha256(before).hexdigest())
        self.assertEqual(a['method'], macro.METHOD)
        self.assertFalse(a['runtimeSelected'])
        self.assertEqual(len(a['dependencySha256']), 2)


if __name__ == '__main__':
    unittest.main()
