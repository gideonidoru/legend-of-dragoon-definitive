#!/usr/bin/env python3
"""Behavior checks for palette-live 4x world-water candidates, without a GPU."""
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('water', Path(__file__).with_name('build-envhd-water-palette-detail.py'))
water = importlib.util.module_from_spec(spec)
spec.loader.exec_module(water)


class WaterDetailTests(unittest.TestCase):
    def setUp(self):
        self.indices = np.array([[1,2],[2,1]], dtype=np.uint8)
        self.palettes = np.repeat(np.arange(16, dtype=np.uint16)[None, :], 8, axis=0)
        self.original = np.repeat(np.repeat(self.indices,4,axis=0),4,axis=1)
        self.colours = np.stack([water.fx.colours(p) for p in self.palettes])
        self.targets = self.colours[:, self.original].copy()

    def test_flat_exact_targets_keep_original_index_and_colour(self):
        detail = water.fit(self.indices,self.palettes,self.targets)
        self.assertTrue(np.array_equal(detail[...,0],self.original))
        self.assertEqual(np.count_nonzero(detail[...,2]),0)
        self.assertTrue(np.all(detail[...,3]==255))

    def test_every_palette_row_changes_the_shared_fit(self):
        results=[]
        opposite=np.where(self.original==1,2,1)
        for row in range(8):
            target=self.targets.copy()
            target[row]=(self.colours[row,self.original]+self.colours[row,opposite])*0.5
            detail=water.fit(self.indices,self.palettes,target)
            self.assertGreater(np.count_nonzero(detail[...,2]),0, row)
            self.assertLess(int(detail[...,2].max()),127)
            results.append(detail)
        # Each independently perturbed row carries equal information here;
        # dropping any palette row would make its result the all-zero baseline.
        for result in results[1:]:self.assertTrue(np.array_equal(results[0],result))

    def test_bright_target_cannot_exceed_half_local_colour_change(self):
        detail=water.fit(self.indices,self.palettes,np.full(self.targets.shape,255,dtype=np.float32))
        rgb=water.preview(self.indices,self.palettes[0],detail)
        base=np.rint(self.colours[0,self.original]).astype(np.uint8)
        # Native red entries are1or2; no local source colour is above entry2.
        self.assertTrue(np.all(rgb[:,:,0]<=np.ceil((base[:,:,0].astype(float)+self.colours[0,2,0])*0.5)))
        self.assertTrue(np.all(rgb[:,:,1:]==0))
        self.assertTrue(np.array_equal(detail[...,0],self.original))

    def test_periodic_neighbor_crosses_native_left_right_edge(self):
        indices=np.zeros((3,3),dtype=np.uint8);indices[:,-1]=15
        original=np.repeat(np.repeat(indices,4,axis=0),4,axis=1)
        targets=self.colours[:,original].copy()
        targets[:,:,:4]=self.colours[:,15,None,None,:]*0.5
        detail=water.fit(indices,self.palettes,targets)
        self.assertTrue(np.all(detail[:,:4,1]==15))
        self.assertTrue(np.all(detail[:,:4,2]>0))

    def test_row_rotation_preserves_the_same_live_palette_detail(self):
        targets=self.targets.copy();targets[0]+=1
        detail=water.fit(self.indices,self.palettes,targets)
        palette=self.palettes[0].copy();palette[1]=0x135;palette[2]=0x224
        before=water.preview(self.indices,palette,detail)
        after=water.preview(np.roll(self.indices,1,axis=0),palette,np.roll(detail,4,axis=0))
        self.assertTrue(np.array_equal(after,np.roll(before,4,axis=0)))

    def test_live_palette_change_recolors_without_reauthoring(self):
        detail=water.fit(self.indices,self.palettes,self.targets)
        blue=self.palettes[0]<<10;green=self.palettes[0]<<5
        blue_rgb=water.preview(self.indices,blue,detail)
        green_rgb=water.preview(self.indices,green,detail)
        self.assertGreater(np.count_nonzero(blue_rgb[:,:,2]),0)
        self.assertEqual(np.count_nonzero(blue_rgb[:,:,1]),0)
        self.assertGreater(np.count_nonzero(green_rgb[:,:,1]),0)
        self.assertEqual(np.count_nonzero(green_rgb[:,:,2]),0)

    def test_native_black_preview_remains_black_under_bad_neural_target(self):
        detail=water.fit(self.indices,self.palettes,np.full(self.targets.shape,255,dtype=np.float32))
        palette=self.palettes[0].copy();palette[1]=0
        rgb=water.preview(self.indices,palette,detail)
        self.assertTrue(np.all(rgb[self.original==1]==0))
        self.assertGreater(np.count_nonzero(rgb[self.original==2]),0)

    def test_stp_bits_do_not_change_rgb_preview_or_source_indices(self):
        detail=water.fit(self.indices,self.palettes,self.targets)
        self.assertTrue(np.array_equal(water.preview(self.indices,self.palettes[0],detail),water.preview(self.indices,self.palettes[0]|0x8000,detail)))
        self.assertTrue(np.array_equal(detail[::4,::4,0],self.indices))

    def test_corrupt_codes_and_nonlocal_secondary_are_rejected(self):
        detail=water.fit(self.indices,self.palettes,self.targets)
        for channel,value in [(0,0),(1,15),(2,128),(3,0)]:
            bad=detail.copy();bad[0,0,channel]=value
            with self.subTest(channel=channel),self.assertRaises(ValueError):water.validate(self.indices,bad)

    def test_missing_rows_nonfinite_targets_and_unbounded_indices_rejected(self):
        with self.assertRaises(ValueError):water.fit(self.indices,self.palettes[:-1],self.targets)
        bad=self.targets.copy();bad[0,0,0,0]=np.nan
        with self.assertRaises(ValueError):water.fit(self.indices,self.palettes,bad)
        with self.assertRaises(ValueError):water.fit(np.zeros((65,64),dtype=np.uint8),self.palettes,self.targets)
        bad=self.indices.copy();bad[0,0]=16
        with self.assertRaises(ValueError):water.fit(bad,self.palettes,self.targets)


class CandidatePublicationTests(unittest.TestCase):
    def setUp(self):
        temporary=tempfile.TemporaryDirectory();self.addCleanup(temporary.cleanup)
        self.root=Path(temporary.name).resolve()
        self.files=self.root/'source';self.files.mkdir()
        self.native_path=self.files/'native';self.native_path.write_bytes(b'captured synthetic source')
        self.indices=np.ones((64,64),dtype=np.uint8)
        self.palettes=np.repeat(np.arange(16,dtype=np.uint16)[None,:],8,axis=0)
        self.inference=self.root/'inferences';self.inference.mkdir()
        records=[]
        for row,palette in enumerate(self.palettes):
            rgb=np.rint(water.fx.colours(palette)[self.indices]).astype(np.uint8)
            stream=io.BytesIO();Image.fromarray(np.pad(rgb,((16,16),(16,16),(0,0)),mode='wrap')).save(stream,format='PNG')
            result=io.BytesIO();Image.new('RGB',(384,384),tuple(int(v) for v in rgb[0,0])).save(result,format='PNG')
            (self.inference/f'private-inference-row-{row}.png').write_bytes(result.getvalue())
            records.append(dict(row=row,inputSha256=water.png.digest(stream.getvalue()),inferenceSha256=water.png.digest(result.getvalue())))
        origin=dict(method=water.METHOD,sourceTimSha256=water.SOURCE_SHA,decodedIndexedPaletteSha256='synthetic',tools=water.fx.PINS,inferences=records,producerSha256='synthetic-producer')
        (self.inference/'candidate-receipt-v1.json').write_text(json.dumps(origin))
        snapshots={self.native_path:self.native_path.read_bytes()}
        mock=patch.object(water,'source_snapshot',return_value=(self.indices,self.palettes,'synthetic',snapshots));mock.start();self.addCleanup(mock.stop)

    def test_complete_unselected_receipt_and_existing_output_preservation(self):
        out=self.root/'output'
        result=water.replay(self.files,self.inference,out)
        self.assertEqual(len(result['previews']),8)
        self.assertFalse(result['runtimeSelected'])
        self.assertFalse(result['currentIndexedRuntimeSupportsThis4xResource'])
        self.assertEqual(result['holdsCleared'],0)
        before={p.name:p.read_bytes() for p in out.iterdir()}
        with self.assertRaisesRegex(ValueError,'fresh candidate'):water.replay(self.files,self.inference,out)
        self.assertEqual(before,{p.name:p.read_bytes() for p in out.iterdir()})

    def assert_mutation_rejected(self,path):
        out=self.root/'output';original=water.writer.write_candidate;changed=False
        def mutate(image,destination):
            nonlocal changed
            result=original(image,destination)
            if not changed:path.write_bytes(path.read_bytes()+b' changed after decoding');changed=True
            return result
        with patch.object(water.writer,'write_candidate',side_effect=mutate),self.assertRaisesRegex(ValueError,'changed before candidate receipt'):
            water.replay(self.files,self.inference,out)
        self.assertFalse((out/'candidate-receipt-v1.json').exists())
        # A failed private authoring run keeps its diagnostic candidates,
        # but cannot publish a receipt asserting intact captured inputs.
        self.assertTrue((out/'indexed-detail-4x-v1.png').exists())

    def test_source_change_after_candidate_write_aborts_receipt(self):
        self.assert_mutation_rejected(self.native_path)

    def test_inference_change_after_candidate_write_aborts_receipt(self):
        self.assert_mutation_rejected(self.inference/'private-inference-row-7.png')

    def test_origin_control_change_after_candidate_write_aborts_receipt(self):
        self.assert_mutation_rejected(self.inference/'candidate-receipt-v1.json')


if __name__=='__main__':unittest.main()
