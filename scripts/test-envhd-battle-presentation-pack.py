#!/usr/bin/env python3
"""Presentation selection preserves previous paintings, UVs and GPU allocations."""
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import unittest
from unittest import mock


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


fixture = module('presentation_pack_fixture', 'test-envhd-painted-battle-pack.py')
pack = fixture.pack


class PresentationPackTests(unittest.TestCase):
    def setUp(self):
        self.f = fixture.PaintedBattlePackTests()
        self.f.setUp()
        self.addCleanup(self.f.doCleanups)
        source = self.f.fixture.source.copy()
        source.putpixel((0, 0), (32, 64, 96, 255))
        self.key = pack.tools.terrain.fingerprint(source)
        folder = self.f.production / 'battle-presentation-candidates' / self.key
        folder.mkdir(parents=True)
        raw, output, original = folder / 'painted-input-v1.png', folder / 'image-v1.png', self.f.root / 'new-source.png'
        source.save(original)
        shutil.copy2(self.f.fixture.raw, raw)
        record, _ = pack.presentation_tools.recipe.execute(original, raw, output, self.key)
        candidate = copy.deepcopy(self.f.fixture.candidate)
        candidate.update(record, prompt='Repaint the synthetic material without changing layout.', generatedInputMode='RGB',
            generatedPath=str(raw.relative_to(self.f.root)), outputPath=str(output.relative_to(self.f.root)))
        candidate['sourceBindings'][0]['stage'] = 45
        candidate['review']['outputSha256'] = record['outputSha256']
        self.f.add_source(source, candidate, 45)
        self.f.records[-1]['reviewStatus'] = 'visual-reviewed-runtime-pending'
        self.f.records[-1]['review']['verdict'] = 'visual-reviewed-runtime-pending'
        neural = self.f.production / 'battle-material-candidates' / self.key
        (neural / 'manifest-v1.json').write_text(json.dumps(self.f.records[-1]))
        self.f.write_ledger()
        self.f.publish()
        self.path = self.f.production / 'battle-presentation.json'
        self.manifest = dict(schema=1, pipeline=pack.presentation_tools.PIPELINE,
            method=pack.presentation_tools.recipe.METHOD, **pack.presentation_tools.PINS,
            sourceArtworkLedgerSha256=pack.tools.terrain.digest((self.f.production / 'battle-material-artwork.json').read_bytes()),
            runtimeSelected=0, nativeAccepted=0, finalQualityAccepted=0, visualReviewedBespokeCandidates=1,
            candidates=[candidate])
        self.save()
        self.before = self.f.runtime_snapshot()
        self.previous = json.loads(self.f.selection.read_text())

    def save(self):
        self.path.write_text(json.dumps(self.manifest))

    def publish(self,omit_presentation=False):
        census = (self.f.scenes, self.f.masters, [])
        with mock.patch.object(pack, 'ROOT', self.f.root), \
                mock.patch.object(pack.battle_sources, 'census', return_value=census), \
                mock.patch.object(pack.painted_tools.battle, 'census', return_value=census), \
                mock.patch.object(pack.presentation_tools.battle, 'census', return_value=census):
            return pack.publish(self.f.root / 'files', presentation_manifest=None if omit_presentation else self.path)

    def test_ordinary_repack_after_presentation_selection_refuses_before_writes(self):
        self.publish()
        before = self.f.runtime_snapshot()
        receipt = self.f.selection.read_bytes()
        with self.assertRaisesRegex(ValueError, 'requires --presentation-manifest'):
            self.publish(omit_presentation=True)
        self.assertEqual(before, self.f.runtime_snapshot())
        self.assertEqual(receipt, self.f.selection.read_bytes())

    def test_ordinary_repack_before_presentation_preserves_earlier_paintings(self):
        result = self.publish(omit_presentation=True)
        self.assertEqual(result['paintedSelectedMasters'], 1)
        self.assertEqual(result['paintedReviewReceipts'], self.previous['paintedReviewReceipts'])
        for old, new in zip(self.previous['scenes'], result['scenes']):
            self.assertEqual(old['atlasSha256'], new['atlasSha256'])

    def test_malformed_previous_selection_is_not_overwritten(self):
        self.f.selection.write_bytes(b'invalid selection')
        before = self.f.runtime_snapshot()
        with self.assertRaises(ValueError):
            self.publish(omit_presentation=True)
        self.assertEqual(before, self.f.runtime_snapshot())

    def test_parseable_partial_wrong_type_and_inconsistent_receipts_fail_before_census(self):
        self.publish()
        valid=json.loads(self.f.selection.read_text())
        controls=[dict(schema=1)]
        for field,value in [('scenes',{}),('runtimeSelectedMasters',True),('runtimeSelectedBindings',0),
            ('paintedSelectedMasters',0),('paintedReviewReceipts',{}),('presentationSelectedMasters',True),
            ('presentationSourceKeys',[]),('presentationReviewReceipt','bad')]:
            malformed=copy.deepcopy(valid);malformed[field]=value;controls.append(malformed)
        for field in ('paintedReviewReceipts','presentationSourceKeys','nativeAccepted','scenes'):
            malformed=copy.deepcopy(valid);del malformed[field];controls.append(malformed)
        malformed=copy.deepcopy(valid)
        receipt=next(iter(malformed['paintedReviewReceipts'].values()))
        malformed['paintedReviewReceipts']={'0'*64:receipt};controls.append(malformed)
        malformed=copy.deepcopy(valid);del malformed['paintedReviewReceipts'];del malformed['paintedSelectedMasters'];controls.append(malformed)
        for sources in ([self.key,self.key], ['0'*64]):
            malformed=copy.deepcopy(valid);malformed['presentationSourceKeys']=sources;malformed['presentationSelectedMasters']=len(sources);controls.append(malformed)
        for selection in controls:
            with self.subTest(selection=selection):
                self.f.selection.write_text(json.dumps(selection));before=self.f.runtime_snapshot()
                with mock.patch.object(pack,'ROOT',self.f.root), mock.patch.object(pack.battle_sources,'census') as census:
                    with self.assertRaises(ValueError):pack.publish(self.f.root/'files')
                    census.assert_not_called()
                self.assertEqual(before,self.f.runtime_snapshot())

    def test_valid_repair_preserves_prior_painting_coverage_mapping_and_allocation(self):
        result = self.publish()
        self.assertEqual(result['runtimeSelectedMasters'], 2)
        self.assertEqual(result['paintedSelectedMasters'], 1)
        self.assertEqual(result['presentationSourceKeys'], [self.key])
        self.assertEqual(result['paintedReviewReceipts'], self.previous['paintedReviewReceipts'])
        for old, new in zip(self.previous['scenes'], result['scenes']):
            self.assertEqual(old['atlasSize'], new['atlasSize'])
            self.assertEqual(old['regions'], new['regions'])
            if old['stage'] == 44: self.assertEqual(old['atlasSha256'], new['atlasSha256'])
        self.assertNotEqual(self.before, self.f.runtime_snapshot())

    def test_atlas_footprint_change_aborts_before_writing_runtime(self):
        self.previous['scenes'][-1]['atlasSize'][1] += 4
        self.f.selection.write_text(json.dumps(self.previous))
        before = self.f.runtime_snapshot()
        with self.assertRaisesRegex(ValueError, 'incomplete or inconsistent'):
            self.publish()
        self.assertEqual(before, self.f.runtime_snapshot())

    def test_cumulative_selection_cannot_drop_prior_bespoke_source(self):
        self.publish()
        original=pack.presentation_tools.preflight
        def missing(*args):
            result=original(*args);result['verified']=[];return result
        before = self.f.runtime_snapshot()
        with mock.patch.object(pack.presentation_tools,'preflight',side_effect=missing):
            with self.assertRaisesRegex(ValueError,'earlier bespoke'):self.publish()
        self.assertEqual(before, self.f.runtime_snapshot())

    def test_manifest_change_during_atlas_assembly_aborts_before_writing_runtime(self):
        original = pack.tools.layout
        def mutate(*args):
            result = original(*args)
            self.path.write_bytes(self.path.read_bytes() + b' ')
            return result
        with mock.patch.object(pack.tools, 'layout', side_effect=mutate):
            with self.assertRaisesRegex(ValueError, 'changed after preflight'):
                self.publish()
        self.assertEqual(self.before, self.f.runtime_snapshot())


if __name__ == '__main__':
    unittest.main()
