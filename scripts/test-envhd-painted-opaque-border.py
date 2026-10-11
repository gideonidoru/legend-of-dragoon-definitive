#!/usr/bin/env python3
"""Prove exact flat borders, feature preservation and source-bound preflight."""
import importlib.util
from pathlib import Path
import unittest

import numpy as np
from PIL import Image

spec=importlib.util.spec_from_file_location('border_preflight_fixtures',Path(__file__).with_name('test-envhd-painted-fields.py'))
fixtures=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixtures)
verify=fixtures.verify
border=verify.opaque_border


class OpaqueBorderTests(unittest.TestCase):
    def source(self):
        source=Image.new('RGBA',(8,8),(224,224,224,255))
        source.putpixel((1,3),(20,50,25,255))
        return source

    def test_border_matches_source_and_does_not_replace_near_edge_feature(self):
        source=self.source();painted=Image.new('RGBA',(32,32),(217,219,221,255))
        output,operation=border.repair_border(source,painted);a=np.asarray(output)
        self.assertTrue(np.all(a[:4,:,:3]==224));self.assertTrue(np.all(a[-4:,:,:3]==224))
        self.assertTrue(np.all(a[:,:4,:3]==224));self.assertTrue(np.all(a[:,-4:,:3]==224))
        self.assertTrue(np.all(a[12:16,4:8]==[217,219,221,255]))
        self.assertTrue(np.all(a[8:24,8:24]==[217,219,221,255]))
        self.assertTrue(np.all(a[:,:,3]==255));self.assertEqual([224]*3,operation['sourceBorderRgb'])
        self.assertGreater(operation['modifiedTargetPixels'],0)

    def test_nonuniform_or_transparent_source_and_transparent_paint_fail(self):
        painted=Image.new('RGBA',(32,32),(217,219,221,255))
        source=self.source();source.putpixel((0,0),(225,224,224,255))
        with self.assertRaisesRegex(ValueError,'uniform'):border.repair_border(source,painted)
        source=self.source();source.putpixel((3,3),(224,224,224,0))
        with self.assertRaisesRegex(ValueError,'transparent source'):border.repair_border(source,painted)
        painted.putpixel((0,0),(217,219,221,0))
        with self.assertRaisesRegex(ValueError,'opaque painting'):border.repair_border(self.source(),painted)

    def test_dimensions_checked_before_source_array_or_resize(self):
        source=Image.new('RGBA',(8,8));painted=Image.new('RGBA',(16,32))
        with fixtures.mock.patch.object(border.np,'asarray',side_effect=AssertionError('Pixel allocation')):
            with self.assertRaisesRegex(ValueError,'aspect ratio'):border.repair_border(source,painted)

    def fixture(self):
        case=fixtures.PaintedFieldTests('runTest');case.setUp();self.addCleanup(case.doCleanups)
        case.source.paste((224,224,224,255),(0,0,2,2));key=verify.batch.terrain.fingerprint(case.source)
        case.asset['decodedRgbaSha256']=key;folder=case.prod/'bespoke-field-candidates'/key;folder.mkdir()
        raw=Image.open(case.raw_path).copy();case.raw_path=folder/'painted-input-v1.png';raw.save(case.raw_path)
        prepared,op=border.repair_border(case.source,raw);output,repair_op=verify.repair.repair(case.source,prepared)
        case.output_path=folder/'image-v1.png';output.save(case.output_path);output_sha=verify.batch.terrain.digest(case.output_path.read_bytes())
        case.candidate.update(repair_op,decodedRgbaSha256=key,generatedPath=case.raw_path.relative_to(case.root).as_posix(),outputPath=case.output_path.relative_to(case.root).as_posix(),outputSha256=output_sha,opaqueBorderRepair=dict(op,algorithmPath='scripts/repair-envhd-painted-opaque-border.py',algorithmSha256=verify.OPAQUE_BORDER_SHA256))
        case.candidate['review']['outputSha256']=output_sha;case.save();return case

    def test_preflight_reproduces_boundary_step_and_rejects_tampered_source_colour(self):
        case=self.fixture();records,_=case.preflight();self.assertEqual(1,len(records))
        case.candidate['opaqueBorderRepair']['sourceBorderRgb']=[225]*3;case.save()
        with self.assertRaisesRegex(ValueError,'recorded source colour'):case.preflight()

    def test_preflight_rejects_changed_algorithm_and_ambiguous_recipe_order(self):
        case=self.fixture();case.candidate['opaqueBorderRepair']['algorithmSha256']='0'*64;case.save()
        with self.assertRaisesRegex(ValueError,'identity changed'):case.preflight()
        case.candidate['opaqueBorderRepair']['algorithmSha256']=verify.OPAQUE_BORDER_SHA256
        # Even a valid registration cannot be combined in an unrecorded order.
        case.registration_fixture()
        with self.assertRaisesRegex(ValueError,'border repair identity'):case.preflight()


if __name__=='__main__':unittest.main()
