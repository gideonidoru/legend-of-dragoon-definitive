#!/usr/bin/env python3
"""Reject source/recipe/pixel forgery when improving selected battle artwork."""
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
fixture = module('presentation_source_fixture', 'test-envhd-painted-battle.py')


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


if __name__ == '__main__':
    unittest.main()
