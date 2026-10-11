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
compressed_png = module('painted_png_bounds_fixture', 'test-envhd-painted-cutout.py').compressed_png
duplicate_header_png = module('painted_duplicate_header_fixture', 'test-envhd-painted-cutout.py').duplicate_header_png


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

    def test_oversized_compressed_rgb_and_rgba_inputs_fail_before_open(self):
        for mode, colour_type in [('RGB',2),('RGBA',6)]:
            with self.subTest(mode=mode):
                data=compressed_png(colour_type=colour_type);self.assertLess(len(data),100000)
                self.raw_path.write_bytes(data);self.candidate['generatedSha256']=verify.batch.terrain.digest(data);self.candidate['generatedInputMode']=mode;self.save()
                with mock.patch.object(verify.Image,'open',side_effect=AssertionError('Pixel allocation')):
                    with self.assertRaisesRegex(ValueError,'bounded painted canvas'):self.preflight()

    def test_wrong_output_dimensions_fail_before_any_input_decode(self):
        for width,height in [(4096,4096),(12,12)]:
            with self.subTest(size=(width,height)):
                data=compressed_png(width,height);self.output_path.write_bytes(data);self.candidate['outputSha256']=verify.batch.terrain.digest(data);self.candidate['review']['outputSha256']=self.candidate['outputSha256'];self.save()
                with mock.patch.object(verify.Image,'open',side_effect=AssertionError('Pixel allocation')):
                    with self.assertRaisesRegex(ValueError,'output dimensions differ'):self.preflight()

    def test_oversized_lazy_source_is_rejected_before_source_pixels(self):
        with Image.open(io.BytesIO(compressed_png())) as source:
            self.asset['sourceSize']=list(source.size);self.candidate['sourceSize']=list(source.size);self.save()
            with mock.patch.object(verify.batch.fields,'render',return_value=(None,[source],self.metadata)),mock.patch.object(source,'load',side_effect=AssertionError('Pixel allocation')):
                with self.assertRaisesRegex(ValueError,'bounded repair canvas'):self.preflight()

    def test_duplicate_generated_headers_fail_before_conversion_or_resize(self):
        for mode,colour_type in [('RGB',2),('RGBA',6)]:
            with self.subTest(mode=mode):
                data=duplicate_header_png(first=(8,8),colour_type=colour_type);self.raw_path.write_bytes(data);self.candidate['generatedSha256']=verify.batch.terrain.digest(data);self.candidate['generatedInputMode']=mode;self.save()
                with mock.patch.object(Image.Image,'load',side_effect=AssertionError('Pixel allocation')),mock.patch.object(Image.Image,'convert',side_effect=AssertionError('Pixel conversion')),mock.patch.object(Image.Image,'resize',side_effect=AssertionError('Pixel resize')):
                    # Source fingerprint uses load even for the already materialized
                    # synthetic source; isolate that legitimate private-source read.
                    with mock.patch.object(self.source,'load',return_value=self.source.im):
                        with self.assertRaisesRegex(ValueError,'bounded painted canvas'):self.preflight()

    def test_duplicate_output_header_fails_before_output_pixels(self):
        data=duplicate_header_png(first=(8,8));self.output_path.write_bytes(data);self.candidate['outputSha256']=verify.batch.terrain.digest(data);self.candidate['review']['outputSha256']=self.candidate['outputSha256'];self.save();original_open=Image.open
        def guarded_open(stream):
            image=original_open(stream)
            if image.size==(4096,4096):
                image.load=mock.Mock(side_effect=AssertionError('Pixel allocation'))
                image.copy=mock.Mock(side_effect=AssertionError('Pixel copy'))
                image.convert=mock.Mock(side_effect=AssertionError('Pixel conversion'))
                image.resize=mock.Mock(side_effect=AssertionError('Pixel resize'))
            return image
        with mock.patch.object(verify.Image,'open',side_effect=guarded_open):
            with self.assertRaisesRegex(ValueError,'output dimensions differ'):self.preflight()

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

    def registration_fixture(self):
        points=[[0,0],[2,0],[2,2],[0,2]]
        painted,operation=verify.registration.register(self.source,Image.open(self.raw_path),points,points)
        stream=io.BytesIO();painted.save(stream,format='PNG')
        self.candidate['registration']=dict(operation,algorithmPath='scripts/register-envhd-painted-cutout.py',registeredInputSha256=verify.batch.terrain.digest(stream.getvalue()));self.save()

    def test_recorded_registration_is_reproduced_before_visibility_repair(self):
        self.registration_fixture();self.assertEqual(1,len(self.preflight()[0]))

    def test_registration_identity_and_parameters_cannot_change_silently(self):
        self.registration_fixture();self.candidate['registration']['algorithmSha256']='0'*64;self.save()
        with self.assertRaisesRegex(ValueError,'registration identity'):self.preflight()
        self.candidate['registration']['algorithmSha256']=verify.REGISTRATION_SHA256
        self.candidate['registration']['registeredInputSha256']='0'*64;self.save()
        with self.assertRaisesRegex(ValueError,'recorded source geometry'):self.preflight()

    def normalization_fixture(self):
        raw=self.source.resize((80,80),Image.Resampling.NEAREST).resize((88,80),Image.Resampling.NEAREST)
        raw.save(self.raw_path)
        normalized,operation=verify.canvas_fit.normalize(self.source,raw)
        stream=io.BytesIO();normalized.save(stream,format='PNG')
        self.candidate['sourceCanvasNormalization']=dict(operation,algorithmPath='scripts/normalize-envhd-painted-canvas.py',algorithmSha256=verify.CANVAS_FIT_SHA256,normalizedInputSha256=verify.batch.terrain.digest(stream.getvalue()))
        painted,repair_operation=verify.repair.repair(self.source,normalized);painted.save(self.output_path)
        self.candidate.update(repair_operation,generatedSha256=verify.batch.terrain.digest(self.raw_path.read_bytes()),outputSha256=verify.batch.terrain.digest(self.output_path.read_bytes()))
        self.candidate['review']['outputSha256']=self.candidate['outputSha256'];self.save()

    def test_explicit_canvas_fit_replays_original_generated_png_then_original_cutout_recipe(self):
        self.normalization_fixture();before=self.raw_path.read_bytes()
        self.assertEqual(1,len(self.preflight()[0]));self.assertEqual(before,self.raw_path.read_bytes())

    def test_unrecorded_stretched_input_still_fails_legacy_aspect_gate(self):
        self.normalization_fixture();del self.candidate['sourceCanvasNormalization'];self.save()
        with self.assertRaisesRegex(ValueError,'aspect ratio'):self.preflight()

    def test_canvas_fit_identity_receipt_and_intermediate_bytes_cannot_change_silently(self):
        self.normalization_fixture();recorded=self.candidate['sourceCanvasNormalization'];original=copy.deepcopy(recorded)
        for key,value,message in [('algorithmSha256','0'*64,'normalization identity'),('silhouetteIou',0.5,'recorded source silhouette'),('normalizedInputSha256','0'*64,'recorded source silhouette'),('sourceAlphaApplied',True,'recorded source silhouette')]:
            with self.subTest(key=key):
                recorded.clear();recorded.update(original);recorded[key]=value;self.save()
                with self.assertRaisesRegex(ValueError,message):self.preflight()

    def test_canvas_fit_cannot_be_combined_with_other_geometric_or_border_operations(self):
        self.normalization_fixture()
        for key in ['registration','opaqueBorderRepair']:
            with self.subTest(key=key):
                self.candidate[key]={};self.save()
                with self.assertRaisesRegex(ValueError,'exclusive operation'):self.preflight()
                del self.candidate[key]

    def test_explicit_canvas_fit_still_bounds_compressed_input_before_decode(self):
        self.normalization_fixture();data=compressed_png();self.raw_path.write_bytes(data)
        self.candidate['generatedSha256']=verify.batch.terrain.digest(data);self.save()
        with mock.patch.object(verify.Image,'open',side_effect=AssertionError('Pixel allocation')):
            with self.assertRaisesRegex(ValueError,'bounded painted canvas'):self.preflight()

    def test_explicit_canvas_fit_still_validates_decoder_header_agreement_before_pixels(self):
        self.normalization_fixture();data=duplicate_header_png(first=(88,80),second=(80,80))
        self.raw_path.write_bytes(data);self.candidate['generatedSha256']=verify.batch.terrain.digest(data);self.save()
        original_open=Image.open
        def guarded_open(stream):
            image=original_open(stream);image.load=mock.Mock(side_effect=AssertionError('Pixel allocation'));return image
        with mock.patch.object(verify.Image,'open',side_effect=guarded_open):
            with self.assertRaisesRegex(ValueError,'decoder differs'):self.preflight()

    def test_explicit_canvas_fit_does_not_accept_rgb_input(self):
        self.normalization_fixture();Image.open(self.raw_path).convert('RGB').save(self.raw_path)
        self.candidate['generatedSha256']=verify.batch.terrain.digest(self.raw_path.read_bytes());self.candidate['generatedInputMode']='RGB';self.save()
        with self.assertRaisesRegex(ValueError,'RGBA output'):self.preflight()

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
