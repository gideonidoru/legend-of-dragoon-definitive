#!/usr/bin/env python3
"""Painted battle selection rejects forged pixels, ownership and review context."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


verify = module('battle_verified_tests', 'verify-envhd-painted-battle.py')
source_fixture = module('battle_source_fixture', 'test-envhd-painted-battle-motif.py')
duplicate = module('battle_duplicate_fixture', 'test-envhd-painted-cutout.py').duplicate_header_png
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()


class PaintedBattleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.source = source_fixture.BattleMotifTests().source()
        self.key = verify.battle.terrain.fingerprint(self.source)
        self.binding = dict(stage=44, page=7, clut=15389, parts=[0], sourceCrop=[0,0,4,4], sourceUvOrigin=[0,0])
        self.item = dict(decodedRgbaSha256=self.key, sourceSize=[4,4], image=self.source, bindings=[self.binding])
        prod = self.root/'integrations/envhd/production'; self.folder = prod/'bespoke-battle-candidates'/self.key; self.folder.mkdir(parents=True)
        self.raw = self.folder/'painted-input-v1.png'; self.output = self.folder/'image-v1.png'
        source_file = self.root/'source.png'; self.source.save(source_file); Image.new('RGB',(16,16),(220,248,227)).save(self.raw)
        record, _ = verify.repair.execute(source_file, self.raw, self.output, self.key, [148,180,156,0], 3)
        self.candidate = record | dict(sourceOwners=['envhd'], sourceBindings=[self.binding], priorCandidateSha256='prior', generatedInputMode='RGB', generatedPath=str(self.raw.relative_to(self.root)), outputPath=str(self.output.relative_to(self.root)), review=dict(intent='Tower stone marking',style='Painted source-locked material',layout='Exact palette motif',verdict='visual-reviewed-runtime-pending',outputSha256=record['outputSha256'],nativeAcceptance='pending',finalQualityAcceptance='pending'))
        self.manifest = dict(schema=1,method=verify.repair.METHOD,**verify.PINS,runtimeSelected=0,nativeAccepted=0,finalQualityAccepted=0,visualReviewedBespokeCandidates=1,candidates=[self.candidate])
        self.path = prod/'battle-bespoke-motifs.json'; self.save()
        self.ledger_path = prod/'battle-material-artwork.json'
        self.ledger = dict(scenes=[],emptyModelSlots=[],assets=[dict(decodedRgbaSha256=self.key,reviewStatus='intent-revision-needed',outputSha256='prior')]); self.save_ledger()

    def save(self): self.path.write_text(json.dumps(self.manifest))
    def save_ledger(self): self.ledger_path.write_text(json.dumps(self.ledger))
    def preflight(self):
        with mock.patch.object(verify.battle,'census',return_value=([],{self.key:self.item},[])):
            return verify.preflight(self.root/'files',self.path,self.root)

    def test_source_bound_recipe_passes_without_establishing_runtime_acceptance(self):
        result = self.preflight(); self.assertEqual(len(result['verified']),1)
        self.assertFalse(result['verified'][0]['candidate']['runtimeSelected'])

    def test_rehashed_wrong_pixels_cannot_forge_a_valid_repair(self):
        Image.new('RGBA',(16,16),(200,230,220,0)).save(self.output)
        self.candidate['outputSha256'] = self.candidate['review']['outputSha256'] = sha(self.output); self.save()
        with self.assertRaisesRegex(ValueError,'source-locked recipe'): self.preflight()

    def test_source_owner_binding_or_history_cannot_be_rebound(self):
        for key, bad in [('sourceOwners',['skurfa']),('sourceBindings',[]),('priorCandidateSha256','new')]:
            previous = self.candidate[key]; self.candidate[key] = bad; self.save()
            with self.assertRaisesRegex(ValueError,'protected, rebound'): self.preflight()
            self.candidate[key] = previous

    def test_decoder_header_mismatch_rejects_generated_rgb_and_output_before_allocation(self):
        self.raw.write_bytes(duplicate(first=(16,16),colour_type=2)); self.candidate['generatedSha256'] = sha(self.raw); self.save()
        with mock.patch.object(Image.Image,'convert',side_effect=AssertionError('Allocation')):
            with self.assertRaisesRegex(ValueError,'bounded painted canvas'): self.preflight()
        Image.new('RGB',(16,16),(220,248,227)).save(self.raw); self.candidate['generatedSha256'] = sha(self.raw)
        self.output.write_bytes(duplicate(first=(16,16))); self.candidate['outputSha256'] = self.candidate['review']['outputSha256'] = sha(self.output); self.save()
        with self.assertRaisesRegex(ValueError,'output dimensions differ'): self.preflight()

    def test_native_acceptance_review_and_recipe_receipt_cannot_be_forged(self):
        self.candidate['review']['nativeAcceptance'] = 'accepted'; self.save()
        with self.assertRaisesRegex(ValueError,'review changed'): self.preflight()
        self.candidate['review']['nativeAcceptance'] = 'pending'; self.candidate['sourceMotifPixels'] += 1; self.save()
        with self.assertRaisesRegex(ValueError,'receipt changed'): self.preflight()

    def circle_recipe(self):
        source_path=self.root/'source.png'
        record,_=verify.circle.execute(source_path,self.raw,self.output,self.key,[148,180,156,0],[1.5,1.5,1],1.4)
        self.candidate.update(record);self.candidate['review']['outputSha256']=record['outputSha256']
        self.manifest.update(method=verify.circle.METHOD,**verify.CIRCLE_PINS);self.save()

    def test_circle_recipe_has_its_own_immutable_pin_and_geometry_receipt(self):
        self.circle_recipe();result=self.preflight();self.assertEqual(len(result['verified']),1)
        self.candidate['sourceMaskCircle']=[1.5,1.5,0.5];self.save()
        with self.assertRaisesRegex(ValueError,'lattice/background'):self.preflight()

    def test_circle_recipe_rejects_rehashed_wrong_pixels(self):
        self.circle_recipe();Image.new('RGBA',(16,16),(200,230,220,0)).save(self.output)
        self.candidate['outputSha256']=self.candidate['review']['outputSha256']=sha(self.output);self.save()
        with self.assertRaisesRegex(ValueError,'source-locked recipe'):self.preflight()


if __name__ == '__main__': unittest.main()
