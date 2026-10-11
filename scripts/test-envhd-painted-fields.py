#!/usr/bin/env python3
"""Painted selection rejects changed sources, pixels, reviews and protected owners."""
import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import numpy as np
from PIL import Image


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec);spec.loader.exec_module(result)
    return result


verify = module('painted_verify_test', 'verify-envhd-painted-fields.py')
selector = module('painted_selector_test', 'select-envhd-fields.py')


class PaintedFieldTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory();self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name);self.prod = self.root/'integrations/envhd/production';self.prod.mkdir(parents=True)
        self.source = Image.fromarray(np.array([[[20,40,15,255],[0,0,0,255]],[[0,0,0,0],[40,60,25,255]]],dtype=np.uint8),'RGBA')
        self.key = verify.batch.terrain.fingerprint(self.source)
        self.binding = dict(kind='foreground',slot=0,renderKey='fixture',canDeriveFromBackground=False)
        self.asset = dict(decodedRgbaSha256=self.key,sourceSize=[2,2],owners=['envhd'],bindings=[self.binding])
        self.metadata = dict(canvasRect=[0,0,2,2],foregrounds=[dict(canvasPlacement=[0,0,2,2])])
        self.data = {3:b'private synthetic source'}
        self.scene = dict(owner='envhd',renderKey='fixture',representative=dict(bank=21,directory=4,cut=10,location='Synthetic'),sourceFiles=[dict(index=3,sha256=verify.batch.terrain.digest(self.data[3]),size=len(self.data[3]))],**self.metadata)
        self.report = dict(assets=[self.asset],renders=[self.scene])
        folder=self.prod/'bespoke-field-candidates'/self.key;folder.mkdir(parents=True)
        self.raw_path=folder/'painted-input-v1.png';Image.new('RGBA',(8,8),(35,65,20,255)).save(self.raw_path)
        painted,operation=verify.repair.repair(self.source,Image.open(self.raw_path));self.output_path=folder/'image-v1.png';painted.save(self.output_path)
        output=verify.batch.terrain.digest(self.output_path.read_bytes())
        self.candidate=dict(operation,decodedRgbaSha256=self.key,sourceSize=[2,2],targetSize=[8,8],algorithmSha256=verify.REPAIR_SHA256,method=verify.METHOD,generatedBy='builtin-imagegen',generatedSha256=verify.batch.terrain.digest(self.raw_path.read_bytes()),outputSha256=output,sourceOwners=['envhd'],sourceBindings=[self.binding],representativeCut=10,representativeLocation='Synthetic',generatedPath=self.raw_path.relative_to(self.root).as_posix(),outputPath=self.output_path.relative_to(self.root).as_posix(),prompt='Source-faithful painted synthetic fixture',nativeAcceptance='pending',finalQualityAcceptance='pending',runtimeSelected=False,review=dict(intent='Preserve synthetic source identity',style='Source-faithful development painted fixture',layout='Exact original layout and transparency',verdict='visual-reviewed-bespoke-candidate-runtime-pending',outputSha256=output,nativeAcceptance='pending',finalQualityAcceptance='pending'))
        self.manifest=dict(schema=1,algorithmPath='scripts/repair-envhd-painted-cutout.py',algorithmSha256=verify.REPAIR_SHA256,runtimeSelected=0,nativeAccepted=0,finalQualityAccepted=0,visualReviewedBespokeCandidates=1,candidates=[self.candidate])
        self.manifest_path=self.prod/'painted.json';self.save()
        for name,result in [('census',self.report),('entries',self.data),('render',(None,[self.source],self.metadata))]:
            patch=mock.patch.object(verify.batch.fields,name,return_value=result);patch.start();self.addCleanup(patch.stop)

    def save(self):
        self.manifest_path.write_text(json.dumps(self.manifest))

    def preflight(self):
        return verify.preflight(self.root/'private-files',self.manifest_path,self.root)

    def test_source_bound_candidate_is_verified_without_writes(self):
        before=self.output_path.read_bytes();records,receipt=self.preflight()
        self.assertEqual(self.key,records[0]['decodedRgbaSha256']);self.assertEqual(64,len(receipt));self.assertEqual(before,self.output_path.read_bytes())

    def test_protected_source_is_rejected_before_reading_pixels(self):
        self.asset['owners']=['skurfa']
        with self.assertRaisesRegex(ValueError,'protected'):self.preflight()

    def test_changed_source_bytes_are_rejected(self):
        self.data[3]=b'changed source'
        with self.assertRaisesRegex(ValueError,'source changed'):self.preflight()

    def test_changed_png_is_rejected(self):
        Image.new('RGBA',(8,8),(90,10,10,255)).save(self.output_path)
        with self.assertRaisesRegex(ValueError,'artwork bytes'):self.preflight()

    def test_rehashed_wrong_repair_cannot_bypass_exact_pixel_validation(self):
        Image.new('RGBA',(8,8),(90,10,10,255)).save(self.output_path)
        self.candidate['outputSha256']=verify.batch.terrain.digest(self.output_path.read_bytes());self.candidate['review']['outputSha256']=self.candidate['outputSha256'];self.save()
        with self.assertRaisesRegex(ValueError,'exact recorded repair'):self.preflight()

    def test_stale_review_and_false_acceptance_are_rejected(self):
        self.candidate['review']['outputSha256']='0'*64;self.save()
        with self.assertRaisesRegex(ValueError,'review required'):self.preflight()
        self.candidate['review']['outputSha256']=self.candidate['outputSha256'];self.manifest['nativeAccepted']=1;self.save()
        with self.assertRaisesRegex(ValueError,'acceptance changed'):self.preflight()

    def test_rgb_generated_patch_requires_an_opaque_source(self):
        Image.open(self.raw_path).convert('RGB').save(self.raw_path)
        self.candidate['generatedSha256']=verify.batch.terrain.digest(self.raw_path.read_bytes());self.candidate['generatedInputMode']='RGB';self.save()
        with self.assertRaisesRegex(ValueError,'cannot replace a source cutout'):self.preflight()
        pixels=np.asarray(self.source).copy();pixels[:,:,3]=255;self.source=Image.fromarray(pixels,'RGBA')
        old_key=self.key;self.key=verify.batch.terrain.fingerprint(self.source)
        folder=self.raw_path.parent;new_folder=folder.with_name(self.key);folder.rename(new_folder)
        self.raw_path=new_folder/'painted-input-v1.png';self.output_path=new_folder/'image-v1.png'
        self.asset['decodedRgbaSha256']=self.key;self.candidate['decodedRgbaSha256']=self.key
        for path_field in ('generatedPath','outputPath'):self.candidate[path_field]=self.candidate[path_field].replace(old_key,self.key)
        painted,operation=verify.repair.repair(self.source,Image.open(self.raw_path).convert('RGBA'));painted.save(self.output_path)
        self.candidate.update(operation);self.candidate['outputSha256']=verify.batch.terrain.digest(self.output_path.read_bytes());self.candidate['review']['outputSha256']=self.candidate['outputSha256'];self.save()
        with mock.patch.object(verify.batch.fields,'render',return_value=(None,[self.source],self.metadata)):
            self.assertEqual(1,len(self.preflight()[0]))

    def test_rebound_layout_and_symlinked_artwork_are_rejected(self):
        self.candidate['sourceBindings']=[self.binding|dict(slot=1)];self.save()
        with self.assertRaisesRegex(ValueError,'rebound'):self.preflight()
        self.candidate['sourceBindings']=[self.binding];self.save();target=self.root/'redirect.png';target.write_bytes(self.raw_path.read_bytes());self.raw_path.unlink();self.raw_path.symlink_to(target)
        with self.assertRaises(ValueError):self.preflight()

    def test_explicit_selection_uses_verified_candidate_and_withdrawal_removes_it(self):
        for name,value in [('field-artwork.json',dict(assets=[])),('field-review-receipt.json',dict(reviews={})),('field-ownership.json',dict(scenes=[dict(owner='skurfa',mappings=[dict(cut=37)])]))]:
            (self.prod/name).write_text(json.dumps(value))
        with mock.patch.object(selector,'ROOT',self.root),mock.patch.object(selector.tools,'module',return_value=verify):
            selected=selector.select(self.root/'private-files',self.manifest_path)
            self.assertEqual(1,selected['runtimeSelected']);self.assertEqual(1,selected['paintedRuntimeSelected']);self.assertEqual([37],selected['protectedCuts']);self.assertEqual(0,selected['nativeAccepted']);self.assertEqual(0,selected['finalQualityAccepted'])
            runtime=self.root/'integrations/envhd/runtime-assets/envhd/fields'/self.key/'image-v1.png';self.assertEqual(self.output_path.read_bytes(),runtime.read_bytes())
            self.assertEqual(0,selector.select()['runtimeSelected']);self.assertFalse(runtime.exists());self.assertTrue(self.output_path.exists())

    def test_selector_requires_current_sources_for_painted_manifest(self):
        (self.prod/'field-artwork.json').write_text(json.dumps(dict(assets=[])));(self.prod/'field-review-receipt.json').write_text(json.dumps(dict(reviews={})))
        with mock.patch.object(selector,'ROOT',self.root),self.assertRaisesRegex(ValueError,'requires both'):
            selector.select(painted_manifest=self.manifest_path)

    def neural_fixture(self):
        folder=self.prod/'field-candidates'/self.key;folder.mkdir(parents=True)
        Image.new('RGBA',(8,8),(80,40,20,255)).save(folder/'image-v1.png')
        output=verify.batch.terrain.digest((folder/'image-v1.png').read_bytes())
        review=dict(self.candidate['review'],verdict='visual-reviewed-runtime-pending',outputSha256=output)
        asset=dict(decodedRgbaSha256=self.key,outputSha256=output,review=review,owners=['envhd'],inputCandidate=dict(sourceSize=[2,2],bindings=[self.binding]))
        (folder/'manifest-v1.json').write_text(json.dumps(asset))
        for name,value in [('field-artwork.json',dict(assets=[asset])),('field-review-receipt.json',dict(reviews={self.key:review})),('field-ownership.json',dict(scenes=[dict(owner='skurfa',mappings=[dict(cut=37)])]))]:
            (self.prod/name).write_text(json.dumps(value))
        return (folder/'image-v1.png').read_bytes()

    def test_painted_override_and_neural_restoration_keep_candidate_history(self):
        neural=self.neural_fixture();runtime=self.root/'integrations/envhd/runtime-assets/envhd/fields'/self.key/'image-v1.png'
        with mock.patch.object(selector,'ROOT',self.root),mock.patch.object(selector.tools,'module',return_value=verify):
            self.assertEqual(1,selector.select()['runtimeSelected']);self.assertEqual(neural,runtime.read_bytes())
            self.assertEqual(1,selector.select(self.root/'private-files',self.manifest_path)['runtimeSelected']);self.assertEqual(self.output_path.read_bytes(),runtime.read_bytes())
            restored=selector.select();self.assertEqual(neural,runtime.read_bytes());self.assertNotIn('paintedRuntimeSelected',restored)
            self.assertEqual(neural,(self.prod/'field-candidates'/self.key/'image-v1.png').read_bytes());self.assertTrue(self.output_path.exists())

    def test_manifest_race_cannot_change_existing_runtime_or_ledger(self):
        neural=self.neural_fixture();runtime=self.root/'integrations/envhd/runtime-assets/envhd/fields'/self.key/'image-v1.png'
        with mock.patch.object(selector,'ROOT',self.root),mock.patch.object(selector.tools,'module',return_value=verify):
            selector.select();ledger=self.prod/'field-runtime-artwork.json';before=ledger.read_bytes();original=selector.tools.publication.atomic_write
            def change_manifest(path,data):
                result=original(path,data)
                if path.name=='image-v1.png':self.manifest_path.write_text('{}')
                return result
            with mock.patch.object(selector.tools.publication,'atomic_write',side_effect=change_manifest),self.assertRaisesRegex(ValueError,'manifest changed after preflight'):
                selector.select(self.root/'private-files',self.manifest_path)
            self.assertEqual(neural,runtime.read_bytes());self.assertEqual(before,ledger.read_bytes())


if __name__=='__main__':unittest.main()
