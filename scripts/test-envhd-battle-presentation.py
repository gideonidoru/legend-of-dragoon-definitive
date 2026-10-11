#!/usr/bin/env python3
"""Reject source/recipe/pixel forgery when improving selected battle artwork."""
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import unittest
from unittest import mock

from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


verify = module('presentation_verify_test', 'verify-envhd-battle-presentation.py')
audit_module = module('presentation_audit_test', 'audit-envhd-battle-presentation.py')
fixture = module('presentation_source_fixture', 'test-envhd-painted-battle.py')
pack_fixture = module('presentation_incremental_fixture', 'test-envhd-battle-presentation-pack.py')


class BattlePresentationTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixture.PaintedBattleTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        self.key = self.fixture.key
        production = self.root / 'integrations/envhd/production'
        folder = production / 'battle-presentation-candidates' / self.key
        folder.mkdir(parents=True)
        self.raw, self.output = folder / 'painted-input-v1.png', folder / 'image-v1.png'
        shutil.copy2(self.fixture.raw, self.raw)
        record, _ = verify.recipe.execute(self.root / 'source.png', self.raw, self.output, self.key)
        self.ledger = self.fixture.ledger
        self.ledger['assets'][0]['reviewStatus'] = 'visual-reviewed-runtime-pending'
        self.fixture.save_ledger()
        self.candidate = record | dict(sourceOwners=['envhd'], sourceBindings=[self.fixture.binding],
            priorCandidateSha256='prior', generatedInputMode='RGB', prompt='Restore this source material without redesign.',
            generatedPath=str(self.raw.relative_to(self.root)), outputPath=str(self.output.relative_to(self.root)),
            review=self.fixture.candidate['review'] | dict(outputSha256=record['outputSha256']))
        self.manifest = dict(schema=1, pipeline=verify.PIPELINE, method=verify.recipe.METHOD, **verify.PINS,
            sourceArtworkLedgerSha256=verify.battle.terrain.digest(self.fixture.ledger_path.read_bytes()),
            runtimeSelected=0, nativeAccepted=0, finalQualityAccepted=0,
            visualReviewedBespokeCandidates=1, candidates=[self.candidate])
        self.path = production / 'battle-presentation.json'
        self.save()

    def save(self):
        self.path.write_text(json.dumps(self.manifest))

    def preflight(self):
        with mock.patch.object(verify.battle, 'census', return_value=([], {self.key: self.fixture.item}, [])):
            return verify.preflight(self.root / 'files', self.path, self.root)

    def test_valid_presentation_preserves_source_stp_and_does_not_claim_acceptance(self):
        result = self.preflight()
        self.assertEqual(len(result['verified']), 1)
        self.assertEqual(self.manifest['nativeAccepted'], 0)
        self.assertEqual(self.manifest['finalQualityAccepted'], 0)
        self.assertFalse(self.candidate['runtimeSelected'])
        self.assertEqual(result['verified'][0]['image'].shape, (16, 16, 4))

    def test_held_or_prior_painted_source_cannot_be_replaced_as_neural(self):
        self.ledger['assets'][0]['reviewStatus'] = 'intent-revision-needed'
        self.fixture.save_ledger()
        self.manifest['sourceArtworkLedgerSha256'] = verify.battle.terrain.digest(self.fixture.ledger_path.read_bytes())
        self.save()
        with self.assertRaisesRegex(ValueError, 'held, painted, pilot'):
            self.preflight()

    def test_source_history_and_binding_and_owner_cannot_be_rebound(self):
        for change in ['history', 'binding', 'owner', 'baseline']:
            with self.subTest(change=change):
                before = json.loads(json.dumps(self.manifest))
                if change == 'history': self.manifest['sourceArtworkLedgerSha256'] = 'wrong'
                if change == 'binding': self.candidate['sourceBindings'] = []
                if change == 'owner': self.candidate['sourceOwners'] = ['charhd']
                if change == 'baseline': self.candidate['priorCandidateSha256'] = 'wrong'
                self.save()
                with self.assertRaises(ValueError): self.preflight()
                self.manifest = before
                self.candidate = self.manifest['candidates'][0]
        self.save()

    def test_duplicate_source_cannot_be_selected_twice(self):
        self.manifest['candidates'].append(self.candidate)
        self.manifest['visualReviewedBespokeCandidates'] = 2
        self.save()
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            self.preflight()

    def test_rehashing_wrong_pixels_does_not_forge_source_recipe(self):
        with Image.open(self.output) as image:
            pixels = image.copy()
        pixels.putpixel((0, 0), (255, 0, 255, 255))
        pixels.save(self.output)
        digest = verify.battle.terrain.digest(self.output.read_bytes())
        self.candidate['outputSha256'] = self.candidate['review']['outputSha256'] = digest
        self.save()
        with self.assertRaisesRegex(ValueError, 'pixels differ'):
            self.preflight()

    def test_manifest_mutation_during_recipe_replay_is_rejected(self):
        original = verify.recipe.repair
        def mutate(*args):
            result = original(*args)
            self.path.write_bytes(self.path.read_bytes() + b' ')
            return result
        with mock.patch.object(verify.recipe, 'repair', side_effect=mutate):
            with self.assertRaisesRegex(ValueError, 'changed during preflight'):
                self.preflight()

    def test_source_history_mutation_during_recipe_replay_is_rejected(self):
        original = verify.recipe.repair
        def mutate(*args):
            result = original(*args)
            self.fixture.ledger_path.write_bytes(self.fixture.ledger_path.read_bytes() + b' ')
            return result
        with mock.patch.object(verify.recipe, 'repair', side_effect=mutate):
            with self.assertRaisesRegex(ValueError, 'history changed during preflight'):
                self.preflight()

    def test_native_acceptance_and_missing_prompt_and_forged_recipe_reject(self):
        for field, value in [('nativeAcceptance', 'accepted'), ('prompt', ''), ('method', 'unrecorded')]:
            with self.subTest(field=field):
                original = self.candidate['review'][field] if field == 'nativeAcceptance' else self.candidate[field]
                target = self.candidate['review'] if field == 'nativeAcceptance' else self.candidate
                target[field] = value
                self.save()
                with self.assertRaises(ValueError): self.preflight()
                target[field] = original
        self.save()

    def test_wrong_canvas_rejects_before_pixel_repair(self):
        Image.new('RGB', (16, 32), 'green').save(self.raw)
        self.candidate['generatedSha256'] = verify.battle.terrain.digest(self.raw.read_bytes())
        self.save()
        with mock.patch.object(verify.recipe, 'repair', side_effect=AssertionError('Must reject before repair')):
            with self.assertRaisesRegex(ValueError, 'aspect ratio'):
                self.preflight()


class BattlePresentationAuditTests(unittest.TestCase):
    def setUp(self):
        self.source_case = BattlePresentationTests()
        self.source_case.setUp()
        self.addCleanup(self.source_case.doCleanups)
        self.root, self.key = self.source_case.root, self.source_case.key
        self.production = self.root / 'integrations/envhd/production'
        self.selection = dict(scenes=[], runtimeSelectedMasters=1, runtimeSelectedBindings=1,
                              paintedSelectedMasters=0)
        self.source_case.ledger.update(protectedPilotBindings=0)
        self.source_case.ledger['assets'][0].update(sourceSize=[4, 4], bindings=[self.source_case.fixture.binding],
                                                  review=dict(intent='Source material'))
        self.source_case.fixture.save_ledger()
        self.verified = dict(verified=[dict(candidate=self.source_case.candidate)], manifestSha256='a' * 64)
        self.source_case.manifest['sourceArtworkLedgerSha256'] = verify.battle.terrain.digest(self.source_case.fixture.ledger_path.read_bytes())
        (self.production / 'battle-presentation-artwork-v1.json').write_text(json.dumps(self.source_case.manifest))
        (self.production / 'battle-skies.json').write_text(json.dumps(dict(uniqueDecodedArtwork=0, total=0)))
        (self.production / 'battle-stage-family-ledger.json').write_text(json.dumps(dict(
            summary=dict(animationOrContextBindings=0, separateIncompletePaletteBindings=0))))

    def audit(self):
        (self.production / 'battle-material-runtime-artwork.json').write_text(json.dumps(self.selection))
        with mock.patch.object(audit_module, 'ROOT', self.root), \
             mock.patch.object(audit_module.verify.battle, 'census', return_value=([], {self.key: {}}, [])), \
             mock.patch.object(audit_module.verify, 'preflight', return_value=self.verified), \
             mock.patch.object(audit_module.pack, 'validate_previous_selection'):
            return audit_module.audit(self.root / 'files')

    def test_unselected_art_remains_pending(self):
        result = self.audit()
        self.assertEqual(result['staticSources'][0]['presentationStatus'], 'new-bespoke-awaiting-independent-review')
        self.assertEqual(result['counts']['newlySelectedBespokeMasters'], 0)

    def test_selected_art_is_reported_without_native_or_beauty_acceptance(self):
        self.selection.update(presentationSourceKeys=[self.key], presentationSelectedMasters=1,
                              presentationReviewReceipt='a' * 64)
        result = self.audit()
        self.assertEqual(result['staticSources'][0]['presentationStatus'],
                         'new-bespoke-development-selection-pending-native-review')
        self.assertEqual(result['counts']['newlySelectedBespokeMasters'], 1)
        self.assertEqual(result['counts']['pendingCandidateReviews'], 0)
        self.assertFalse(result['staticSources'][0]['nativeSceneAccepted'])
        self.assertFalse(result['allBattlePresentationAccepted'])

    def test_stale_or_rebound_selection_cannot_claim_coverage(self):
        valid = dict(presentationSourceKeys=[self.key], presentationSelectedMasters=1,
                     presentationReviewReceipt='a' * 64)
        for change in [dict(presentationSourceKeys=['unknown']), dict(presentationSourceKeys=[self.key, self.key]),
                       dict(presentationSelectedMasters=2), dict(presentationReviewReceipt='stale'),
                       dict(presentationSourceKeys=[{}])]:
            with self.subTest(change=change):
                self.selection.update(valid | change)
                with self.assertRaises(ValueError):
                    self.audit()



class CumulativeBattlePresentationAuditTests(unittest.TestCase):
    """Actual recipe replay and actual installed atlases, with synthetic source discovery only."""
    def setUp(self):
        self.case = pack_fixture.PresentationPackTests()
        self.case.setUp()
        self.addCleanup(self.case.doCleanups)
        self.f = self.case.f
        self.root, self.production = self.f.root, self.f.production
        # All source history exists before review: append authoring candidates, never rebind sources.
        candidates = [self.case.manifest['candidates'][0]]
        for i in range(22):
            source = self.f.fixture.source.copy()
            source.putpixel((0, 0), (100 + i, 60, 90, 255))
            key = verify.battle.terrain.fingerprint(source)
            folder = self.production / 'battle-presentation-candidates' / key
            folder.mkdir(parents=True)
            raw, output, original = folder / 'painted-input-v1.png', folder / 'image-v1.png', self.root / f'source-{i}.png'
            source.save(original)
            shutil.copy2(self.f.fixture.raw, raw)
            record, _ = verify.recipe.execute(original, raw, output, key)
            candidate = copy.deepcopy(candidates[0])
            candidate.update(record, generatedPath=str(raw.relative_to(self.root)), outputPath=str(output.relative_to(self.root)))
            candidate['sourceBindings'][0]['stage'] = 46 + i
            candidate['review']['outputSha256'] = record['outputSha256']
            self.f.add_source(source, candidate, 46 + i)
            self.f.records[-1]['reviewStatus'] = 'visual-reviewed-runtime-pending'
            self.f.records[-1]['review']['verdict'] = 'visual-reviewed-runtime-pending'
            (self.production / 'battle-material-candidates' / key / 'manifest-v1.json').write_text(json.dumps(self.f.records[-1]))
            candidates.append(candidate)
        self.f.write_ledger()
        history_path = self.production / 'battle-material-artwork.json'
        history = json.loads(history_path.read_bytes())
        history['protectedPilotBindings'] = 0
        history_path.write_text(json.dumps(history))
        self.f.publish()
        self.initial_neural = self.f.runtime_snapshot()
        self.case.path = self.production / 'battle-presentation-artwork-v1.json'
        self.case.manifest.update(sourceArtworkLedgerSha256=verify.battle.terrain.digest(history_path.read_bytes()),
            candidates=candidates[:22], visualReviewedBespokeCandidates=22)
        self.case.save()
        self.case.publish()
        selected = json.loads(self.f.selection.read_bytes())
        self.receipt = selected['presentationReviewReceipt']
        self.review = self.production / 'battle-presentation-reviews' / f'{self.receipt}.json'
        self.review.parent.mkdir()
        self.review.write_bytes(self.case.path.read_bytes())
        self.case.manifest['candidates'] = candidates
        self.case.manifest['visualReviewedBespokeCandidates'] = 23
        self.case.save()
        self.selected_keys = set(selected['presentationSourceKeys'])
        self.pending_key = candidates[-1]['decodedRgbaSha256']
        (self.production / 'battle-skies.json').write_text(json.dumps(dict(uniqueDecodedArtwork=0, total=0)))
        (self.production / 'battle-stage-family-ledger.json').write_text(json.dumps(dict(
            summary=dict(animationOrContextBindings=0, separateIncompletePaletteBindings=0))))
        self.before = self.f.runtime_snapshot()

    def audit(self):
        with mock.patch.object(audit_module, 'ROOT', self.root), \
             mock.patch.object(audit_module.verify.battle, 'census', return_value=(self.f.scenes, self.f.masters, [])):
            return audit_module.audit(self.root / 'files')

    def test_appending_unselected_candidate_preserves_all_22_installed_regions_and_receipt(self):
        result = self.audit()
        self.assertEqual(result['counts']['newBespokeCandidates'], 23)
        self.assertEqual(result['counts']['newlySelectedBespokeMasters'], 22)
        self.assertEqual(result['counts']['pendingCandidateReviews'], 1)
        selected = {r['decodedRgbaSha256'] for r in result['staticSources']
                    if r['presentationStatus'] == 'new-bespoke-development-selection-pending-native-review'}
        self.assertEqual(selected, self.selected_keys)
        pending = next(r for r in result['staticSources'] if r['decodedRgbaSha256'] == self.pending_key)
        self.assertEqual(pending['presentationStatus'], 'new-bespoke-awaiting-independent-review')
        self.assertEqual(self.f.runtime_snapshot(), self.before)
        self.assertEqual(result['counts']['nativeSceneAccepted'], 0)
        self.assertEqual(result['counts']['finalBeautyAccepted'], 0)

    def test_erasing_all_receipt_fields_or_emptying_selected_keys_rejects_installed_paintings(self):
        original = json.loads(self.f.selection.read_bytes())
        for change in ('all-removed', 'empty-keys', 'missing-receipt'):
            with self.subTest(change=change):
                selected = copy.deepcopy(original)
                if change == 'all-removed':
                    for field in ('presentationSourceKeys', 'presentationSelectedMasters', 'presentationReviewReceipt'):
                        del selected[field]
                elif change == 'empty-keys':
                    selected.update(presentationSourceKeys=[], presentationSelectedMasters=0)
                    del selected['presentationReviewReceipt']
                else: del selected['presentationReviewReceipt']
                self.f.selection.write_text(json.dumps(selected))
                before = self.f.runtime_snapshot()
                with self.assertRaises(ValueError): self.audit()
                self.assertEqual(self.f.runtime_snapshot(), before)

    def test_legitimate_initial_neural_selection_keeps_all_drafts_pending(self):
        for relative, data in self.initial_neural.items():
            (self.root / relative).write_bytes(data)
        result = self.audit()
        self.assertEqual(result['counts']['newlySelectedBespokeMasters'], 0)
        self.assertEqual(result['counts']['pendingCandidateReviews'], 23)
        self.assertEqual(self.f.runtime_snapshot(), self.initial_neural)

    def test_selected_prompt_review_and_recipe_cannot_be_silently_rebound(self):
        original = copy.deepcopy(self.case.manifest)
        for field in ('prompt', 'review', 'method'):
            with self.subTest(field=field):
                self.case.manifest = copy.deepcopy(original)
                candidate = self.case.manifest['candidates'][0]
                if field == 'prompt': candidate['prompt'] += ' Changed review intent.'
                elif field == 'review': candidate['review']['layout'] += ' Changed review.'
                else: candidate['method'] = 'unrecorded'
                self.case.save()
                with self.assertRaises(ValueError): self.audit()
                self.assertEqual(self.f.runtime_snapshot(), self.before)

    def test_rehashed_selected_pixels_cannot_replace_installed_art(self):
        candidate = self.case.manifest['candidates'][0]
        path = self.root / candidate['outputPath']
        with Image.open(path) as image: changed = image.copy()
        changed.putpixel((0, 0), (255, 0, 255, 255))
        changed.save(path)
        candidate['outputSha256'] = candidate['review']['outputSha256'] = verify.battle.terrain.digest(path.read_bytes())
        self.case.save()
        with self.assertRaises(ValueError): self.audit()
        self.assertEqual(self.f.runtime_snapshot(), self.before)

    def test_missing_or_changed_historical_receipt_and_changed_selection_keys_reject(self):
        review_bytes = self.review.read_bytes()
        selection_bytes = self.f.selection.read_bytes()
        for change in ('missing', 'receipt-bytes', 'receipt-id', 'keys'):
            with self.subTest(change=change):
                self.review.write_bytes(review_bytes)
                self.f.selection.write_bytes(selection_bytes)
                selected = json.loads(selection_bytes)
                if change == 'missing': self.review.unlink()
                elif change == 'receipt-bytes': self.review.write_bytes(review_bytes + b' ')
                elif change == 'receipt-id': selected['presentationReviewReceipt'] = '0' * 64
                else:
                    selected['presentationSourceKeys'] = selected['presentationSourceKeys'][:-1]
                    selected['presentationSelectedMasters'] -= 1
                if change in ('receipt-id', 'keys'): self.f.selection.write_text(json.dumps(selected))
                before = self.f.runtime_snapshot()
                with self.assertRaises(ValueError): self.audit()
                self.assertEqual(self.f.runtime_snapshot(), before)

    def test_rehashed_atlas_corruption_rejects_without_writes(self):
        selected = json.loads(self.f.selection.read_bytes())
        scene = next(s for s in selected['scenes'] if s['regions'][0]['decodedRgbaSha256'] in self.selected_keys)
        folder = self.f.runtime / scene['modelSha256']
        path = folder / 'atlas-v1.png'
        with Image.open(path) as image: changed = image.copy()
        x, y, _, _ = scene['regions'][0]['atlasRect']
        changed.putpixel((x, y), (255, 0, 255, 255))
        changed.save(path)
        scene['atlasSha256'] = verify.battle.terrain.digest(path.read_bytes())
        (folder / 'manifest.json').write_text(json.dumps({k: v for k, v in scene.items() if k not in ('stage', 'hdBindings')}))
        self.f.selection.write_text(json.dumps(selected))
        before = self.f.runtime_snapshot()
        with self.assertRaises(ValueError): self.audit()
        self.assertEqual(self.f.runtime_snapshot(), before)

    def test_historical_receipt_mutation_during_runtime_validation_rejects(self):
        original = audit_module.pack.validate_previous_selection
        def mutate(*args):
            original(*args)
            self.review.write_bytes(self.review.read_bytes() + b' ')
        with mock.patch.object(audit_module.pack, 'validate_previous_selection', side_effect=mutate):
            with self.assertRaisesRegex(ValueError, 'changed during audit'): self.audit()
        self.assertEqual(self.f.runtime_snapshot(), self.before)


if __name__ == '__main__':
    unittest.main()
