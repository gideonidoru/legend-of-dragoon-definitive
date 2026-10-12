#!/usr/bin/env python3
"""Adversarial checks for source alias binding and exact panorama recipe replay."""
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import tempfile
import unittest

from PIL import Image

spec = importlib.util.spec_from_file_location('panorama_provenance_test', Path(__file__).with_name('verify-envhd-battle-panorama.py'))
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


def synthetic_mcq(header_variant):
    data = bytearray(40 + 128 * 512 * 2)
    struct.pack_into('<II8H', data, 0, 0x151434d, 40, 128, 512, 96, 0, 0, 0, 256, 384)
    data[24] = header_variant
    for y in range(512):
        for x in range(96):
            struct.pack_into('<H', data, 40 + 2 * (y * 128 + x), 0x1111)
    for cx, rows in [(96, 256), (112, 128)]:
        for y in range(rows):
            struct.pack_into('<H', data, 40 + 2 * (y * 128 + cx + 1), 0x3d4)
    return data


class PanoramaProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve() / 'project'
        self.files = Path(self.temp.name).resolve() / 'private'
        (self.root / 'scripts').mkdir(parents=True)
        for name in verify.TOOL_HASHES:
            shutil.copyfile(verify.ROOT / 'scripts' / name, self.root / 'scripts' / name)
        entries = []
        for stage in (0, 1):
            data = synthetic_mcq(stage)
            source_name = f'SECT/DRGN0.BIN/{2497 + stage}/1'
            path = self.files / source_name
            path.parent.mkdir(parents=True)
            path.write_bytes(data)
            original = verify.decoder.decode(data)
            entries.append(dict(sourceMcqSha256=verify.digest(data), decodedRgbaSha256=verify.recipe.terrain.fingerprint(original),
                                sourceFiles=[source_name], stages=[stage], sourceSize=[256, 384], targetSize=[1024, 1536],
                                status='visual-reviewed-native-pending'))
        self.original = original
        self.key = entries[0]['sourceMcqSha256']
        for entry in entries:
            entry['generationMasterSourceSha256'] = self.key
        production = self.root / verify.PRODUCTION
        production.mkdir(parents=True)
        self.ledger = production / 'battle-skies.json'
        self.ledger.write_text(json.dumps(dict(assets=entries)))
        prefix = verify.PRODUCTION + '/battle-panorama-candidates/' + self.key
        self.raw = self.root / prefix / 'generated-v1.png'
        self.raw.parent.mkdir(parents=True)
        Image.new('RGB', (1536, 1536), (40, 60, 80)).save(self.raw)
        source_png = self.files / 'synthetic.png'
        original.save(source_png)
        self.output = self.root / prefix / 'image-v1.png'
        receipt, _ = verify.recipe.execute(source_png, self.raw, self.output, entries[0]['decodedRgbaSha256'])
        self.record = dict(sourceMcqSha256=self.key, decodedRgbaSha256=entries[0]['decodedRgbaSha256'],
                           sourceBindings=sorted([verify.binding(e) for e in entries],key=lambda e:e['sourceMcqSha256']),
                           generatedPath=str(self.raw.relative_to(self.root)), outputPath=str(self.output.relative_to(self.root)),
                           recipe=receipt, prompt='Synthetic source-bound panorama test', references=[],
                           reviewStatus='layout-reviewed-wrap-pending',runtimeSelected=False,
                           independentReview='pending',wrapAcceptance='pending',nativeAcceptance='pending',finalQualityAcceptance='pending',
                           review=dict(intent='Synthetic color bands',style='Synthetic fixture',layout='Fixed crop',wrap='Pending real scene'))
        self.manifest = dict(schema=1,method=verify.METHOD,toolHashes=verify.TOOL_HASHES,
                             sourceLedgerSha256=verify.digest(self.ledger.read_bytes()),authorReviewedCandidates=1,
                             sourceCounts=dict(sourceVariants=2,decodedSources=1,nonuniformDecodedSources=1,uniformNativeArtwork=0),
                             candidates=[self.record],reusedEarlierPaintings=[],unchangedRuntimeResources={})
        self.manifest_path = production / 'battle-panorama-artwork-v1.json'

    def audit(self):
        self.manifest_path.write_text(json.dumps(self.manifest))
        return verify.preflight(self.files,self.manifest_path,self.root)

    def test_real_mcq_alias_decode_and_replay_pass_without_runtime_selection(self):
        result = self.audit()
        self.assertEqual(result['authorReviewedCandidates'],1)
        self.assertTrue(result['authoringCoverageComplete'])
        self.assertEqual(result['runtimeSelectedNewCandidates'],0)
        self.assertFalse((self.root / 'integrations/envhd/runtime-assets').exists())

    def test_rehashing_wrong_output_cannot_forge_recipe_replay(self):
        Image.new('RGBA',(1024,1536),(1,2,3,255)).save(self.output)
        self.record['recipe']['outputSha256']=verify.digest(self.output.read_bytes())
        with self.assertRaisesRegex(ValueError,'replay differs'):
            self.audit()

    def test_private_source_alias_change_fails_before_replay(self):
        alias = self.files / 'SECT/DRGN0.BIN/2498/1'
        data = bytearray(alias.read_bytes()); data[24] = 99; alias.write_bytes(data)
        with self.assertRaisesRegex(ValueError,'source alias changed'):
            self.audit()

    def test_source_binding_omission_is_rejected(self):
        self.record['sourceBindings'].pop()
        with self.assertRaisesRegex(ValueError,'placement aliases changed'):
            self.audit()

    def test_tool_pin_and_implementation_tampering_are_rejected(self):
        self.manifest['toolHashes']=dict(verify.TOOL_HASHES)
        self.manifest['toolHashes']['bounded-envhd-battle-panorama.py']='0'*64
        with self.assertRaisesRegex(ValueError,'recipe pins'):
            self.audit()
        self.manifest['toolHashes']=verify.TOOL_HASHES
        (self.root/'scripts/bounded-envhd-battle-panorama.py').write_text('changed')
        with self.assertRaisesRegex(ValueError,'implementation changed'):
            self.audit()

    def test_native_or_independent_acceptance_cannot_be_forged(self):
        for field,value in [('runtimeSelected',True),('nativeAcceptance','accepted'),('independentReview','clear')]:
            previous=self.record[field]; self.record[field]=value
            with self.subTest(field=field),self.assertRaisesRegex(ValueError,'cannot claim'):
                self.audit()
            self.record[field]=previous

    def test_relative_escape_and_linked_candidate_are_rejected(self):
        previous=self.record['generatedPath']
        self.record['generatedPath']=verify.PRODUCTION+'/battle-panorama-candidates/'+self.key+'/../../outside.png'
        with self.assertRaisesRegex(ValueError,'escapes'):
            self.audit()
        self.record['generatedPath']=previous
        data=self.raw.read_bytes(); self.raw.unlink()
        external=self.files/'external.png'; external.write_bytes(data); self.raw.symlink_to(external)
        with self.assertRaisesRegex(ValueError,'Linked artwork'):
            self.audit()

    def test_source_identity_and_raw_receipt_tampering_are_rejected(self):
        self.record['recipe']['decodedRgbaSha256']='0'*64
        with self.assertRaisesRegex(ValueError,'replay differs'):
            self.audit()
        self.record['recipe']['decodedRgbaSha256']=self.record['decodedRgbaSha256']
        self.record['recipe']['generatedSha256']='0'*64
        with self.assertRaisesRegex(ValueError,'candidate bytes'):
            self.audit()

    def test_runtime_inventory_addition_is_rejected(self):
        path=self.root/'integrations/envhd/runtime-assets/envhd/skies/synthetic/manifest.json'
        path.parent.mkdir(parents=True);path.write_text('{}')
        with self.assertRaisesRegex(ValueError,'resource inventory changed'):
            self.audit()

    def test_duplicate_and_count_tampering_are_rejected(self):
        self.manifest['authorReviewedCandidates']=2
        with self.assertRaisesRegex(ValueError,'count differs'):
            self.audit()
        self.manifest['candidates'].append(copy.deepcopy(self.record))
        with self.assertRaisesRegex(ValueError,'Duplicate'):
            self.audit()

    def test_source_census_and_boolean_count_forgery_are_rejected(self):
        self.manifest['sourceCounts']['sourceVariants']=99
        with self.assertRaisesRegex(ValueError,'source census'):
            self.audit()
        self.manifest['sourceCounts']['sourceVariants']=2
        self.manifest['authorReviewedCandidates']=True
        with self.assertRaisesRegex(ValueError,'count differs'):
            self.audit()


if __name__=='__main__':
    unittest.main()
