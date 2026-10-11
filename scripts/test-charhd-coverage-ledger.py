#!/usr/bin/env python3
"""Synthetic whole-scope accounting and disclosure fixtures. AGPLv3."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('ledger', Path(__file__).with_name('build-charhd-coverage-ledger.py'))
ledger = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ledger)


class CoverageTest(unittest.TestCase):
    def test_generation_does_not_imply_selection_acceptance_or_complete_denominator(self):
        row = dict(packId='pair', modelSha256='model-hash', timSha256='tim-hash', model='model',
            texture='tim', status='eligible-restoration', consumers=[dict(category='unresolved-field-actor', ownership='unresolved')])
        census = dict(records=[row], bindings=1, unbound=[dict(model='unbound')],
            sourceFailures=[dict(reason='Missing /private/source/tim')])
        candidates = dict(records=[dict(packId='pair', status='restoration-candidate', path='candidate')], originalQueueCount=1)
        with tempfile.TemporaryDirectory() as directory:
            report = ledger.build(census, candidates, Path(directory), dict(models=[{}]), Path('/private/source'))
        self.assertEqual(report['selectedRuntimePairs'], 0)
        self.assertFalse(report['publicationReady'])
        self.assertIsNone(report['fullGameDenominator'])
        self.assertEqual(report['artAccepted'], 0)
        self.assertEqual(report['unboundRoutes'], 1)
        self.assertNotIn('/private/source', json.dumps(report))
        self.assertIn('Resolve character versus environment/prop ownership', report['records'][0]['remaining'])

    def test_source_pair_selection_remains_development_and_ambiguous_pairs_fail(self):
        row = dict(packId='pair', modelSha256='model-hash', timSha256='tim-hash', model='model', texture='tim',
            status='hold-animated-or-extra-data', consumers=[dict(category='boss-story-battle', ownership='source-verified')])
        census = dict(records=[row], bindings=1, unbound=[], sourceFailures=[])
        candidates = dict(records=[], originalQueueCount=0)
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory); one=runtime/'one';one.mkdir()
            manifest = dict(modelSha256='model-hash', timSha256='tim-hash', atlasEngineSha256='atlas')
            (one/'manifest.json').write_text(json.dumps(manifest))
            report = ledger.build(census, candidates, runtime, dict(models=[]), Path('/private/source'))
            self.assertEqual(report['selectedRuntimePairs'], 1)
            self.assertFalse(report['records'][0]['artAccepted'])
            self.assertIn('Preserve and reconstruct animated palette/texture or extra-data behavior', report['records'][0]['remaining'])
            two=runtime/'two';two.mkdir();(two/'manifest.json').write_text(json.dumps(manifest))
            with self.assertRaises(ValueError): ledger.build(census, candidates, runtime, dict(models=[]), Path('/private/source'))


if __name__ == '__main__': unittest.main()
