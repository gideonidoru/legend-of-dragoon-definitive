#!/usr/bin/env python3
"""Regression checks: shared source scenes stay owned by Skurfa across cut aliases."""
import importlib.util
import unittest
import tempfile
from pathlib import Path

spec = importlib.util.spec_from_file_location('ownership', Path(__file__).with_name('audit-envhd-ownership.py'))
ownership = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ownership)


class OwnershipTest(unittest.TestCase):
    def setUp(self):
        self.census = {'rows': [dict(cut=cut, period=period, bank=bank, file=file, signature=key)
                               for cut, period, bank, file, key in [(5, 'early', 21, 19, 'shared'),
                                                                  (624, 'early', 21, 1876, 'shared'),
                                                                  (5, 'late', 22, 19, 'shared'),
                                                                  (7, 'early', 21, 25, 'gap')]]}
        self.manifests = {5: dict(drgnBin=21, environmentDir=19, backgroundSha256='hd-hash', foregroundCount=3)}

    def test_aliases_and_late_period_do_not_generate_again(self):
        report = ownership.plan(self.census, self.manifests, [{'disk': 1, 'cut': 5, 'resourceCut': 5}])
        self.assertEqual(3, report['protectedCutPeriodMappings'])
        self.assertEqual(1, report['fieldGapsBeforeVisualDedup'])
        protected = next(s for s in report['scenes'] if s['sourceSignature'] == 'shared')
        self.assertEqual('reuse-existing', protected['action'])
        self.assertEqual(5, protected['resourceCut'])
        self.assertEqual('skurfa', protected['owner'])

    def test_unknown_runtime_resource_is_not_treated_as_a_gap(self):
        with self.assertRaises(ValueError):
            ownership.plan(self.census, self.manifests, [{'disk': 1, 'cut': 99, 'resourceCut': 99}])

    def test_conflicting_existing_owners_require_resolution(self):
        self.manifests[624] = dict(drgnBin=21, environmentDir=1876, backgroundSha256='other-hd', foregroundCount=3)
        with self.assertRaises(ValueError):
            ownership.plan(self.census, self.manifests, [])

    def test_every_alias_and_gap_is_rehashed_and_missing_pages_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            files = Path(tmp)
            census = {'rows': []}
            for number in (19, 1876, 25):
                folder = files / 'SECT/DRGN21.BIN' / str(number)
                folder.mkdir(parents=True)
                (folder / 'mrg').write_text('0=0;4\n3=3;4\n4=;0\n')
                (folder / '0').write_bytes(b'env!')
                (folder / '3').write_bytes(b'page')
                census['rows'].append(dict(bank=21, file=number, signature=ownership.signature(folder)))
            self.assertEqual(3, ownership.verify_sources(census, files))
            for number in (1876, 25):
                folder = files / 'SECT/DRGN21.BIN' / str(number)
                (folder / '3').write_bytes(b'drift')
                with self.assertRaises(ValueError):
                    ownership.verify_sources(census, files)
                (folder / '3').write_bytes(b'page')
            (folder / '3').unlink()
            with self.assertRaises(ValueError):
                ownership.signature(folder)

    def test_malformed_or_unresolved_entries_stop_classification(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            (folder / '0').write_bytes(b'env!')
            (folder / '99').write_bytes(b'page')
            for text in ('0=0;4\n3=;4\n', '0=0;4\n3=4;4\n4=;0\n', '0=0;4\n3=bad;4\n', '0=0;4\n3=99;4\n'):
                (folder / 'mrg').write_text(text)
                with self.assertRaises(ValueError):
                    ownership.signature(folder)


if __name__ == '__main__':
    unittest.main()
