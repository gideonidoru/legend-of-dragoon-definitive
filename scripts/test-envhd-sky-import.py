#!/usr/bin/env python3
"""Synthetic fixtures for shared sky selection, rejected revisions and import rollback."""
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image

spec = importlib.util.spec_from_file_location('sky_import', Path(__file__).with_name('import-envhd-sky.py'))
sky = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sky)


def source(clear_colour):
    data = bytearray(40 + 16 * 64 * 2)
    struct.pack_into('<II8H', data, 0, 0x151434d, 40, 16, 64, 0, 40, 0, 0, 16, 32)
    data[24] = clear_colour
    for y in range(32):
        for x in range(4):
            struct.pack_into('<H', data, 40 + (y * 16 + x) * 2, 0x1111 if y < 16 else 0x2222)
    struct.pack_into('<H', data, 40 + (40 * 16 + 1) * 2, 0x8000)
    struct.pack_into('<H', data, 40 + (41 * 16 + 2) * 2, 31)
    return data


class SkyImportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'project'
        self.files = Path(self.temp.name) / 'private'
        self.files.mkdir()
        self.production = self.root / 'integrations/envhd/production'
        self.production.mkdir(parents=True)
        self.runtime = self.root / 'integrations/envhd/runtime-assets'
        self.prompt = self.production / 'prompts/test.txt'
        self.prompt.parent.mkdir()
        self.prompt.write_text('Synthetic test fixture only; no retail artwork.\n')
        assets = []
        for stage, data in enumerate((source(0), source(77))):
            name = f'stage-{stage}.mcq'
            (self.files / name).write_bytes(data)
            decoded = sky.decoder.decode(data)
            pixels = sky.digest(struct.pack('<II', *decoded.size) + decoded.tobytes())
            assets.append(dict(sourceMcqSha256=sky.digest(data), decodedRgbaSha256=pixels,
                               sourceFiles=[name], stages=[stage], sourceSize=[16, 32],
                               targetSize=[32, 64], status='source-decoded'))
        self.master = assets[0]['sourceMcqSha256']
        self.alias = assets[1]['sourceMcqSha256']
        self.group = assets[0]['decodedRgbaSha256']
        for asset in assets:
            asset['generationMasterSourceSha256'] = self.master
        self.control('battle-skies.json', dict(assets=assets))
        self.control('battle-generation-tasks.json', dict(tasks=[dict(masterSourceSha256=self.master)]))
        self.control('coverage.json', {})
        self.candidate = Path(self.temp.name) / 'candidate.png'
        Image.new('RGB', (32, 64), (50, 75, 100)).save(self.candidate)
        self.notes = dict(intent='Source geometry retained', style='Reference style reviewed',
                          layout='Exact source-bound size', wrap='Repeat reviewed')

    def control(self, name, value):
        (self.production / name).write_text(json.dumps(value))

    def record(self, version=1, verdict='visual-reviewed-native-pending', source_hash=None):
        return sky.record(self.root, self.files, self.candidate, source_hash or self.master,
                          version, self.prompt, verdict, self.notes)

    def snapshot(self):
        return {str(p.relative_to(self.root)): p.read_bytes() for p in self.root.rglob('*') if p.is_file()}

    def manifest(self, key):
        return self.runtime / 'envhd/skies' / key / 'manifest.json'

    def image(self, version):
        return self.runtime / 'envhd/sky-images' / self.group / f'image-v{version}.png'

    def test_header_variants_share_one_png_without_copying_originals(self):
        result = self.record()
        self.assertEqual(2, result['sharedStageVariants'])
        for key in (self.master, self.alias):
            data = json.loads(self.manifest(key).read_text())
            self.assertEqual(key, data['sourceMcqSha256'])
            self.assertEqual(self.group, data['decodedRgbaSha256'])
        self.assertEqual(self.candidate.read_bytes(), self.image(1).read_bytes())
        self.assertEqual(1, len(list(self.runtime.rglob('*.png'))))
        self.assertFalse(list(self.root.rglob('*.mcq')))
        with self.assertRaises(ValueError):
            self.record(source_hash=self.alias)

    def test_rejected_new_revision_preserves_previous_selection(self):
        self.record()
        selected = self.manifest(self.master).read_bytes()
        Image.new('RGB', (32, 64), (99, 88, 77)).save(self.candidate)
        self.record(2, 'intent-revision-needed')
        self.assertEqual(selected, self.manifest(self.master).read_bytes())
        self.assertTrue(self.image(1).is_file())
        self.assertFalse(self.image(2).exists())
        self.assertEqual(1, json.loads((self.production / 'coverage.json').read_text())['integrated'])

    def test_withdraw_exact_selected_image_keeps_public_candidate(self):
        self.record()
        self.record(verdict='repeat-boundary-revision-needed')
        self.assertFalse(self.manifest(self.master).exists())
        self.assertFalse(self.manifest(self.alias).exists())
        self.assertFalse(self.image(1).exists())
        self.assertTrue((self.production / 'candidates' / self.master / 'image-v1.png').exists())
        self.assertEqual(0, json.loads((self.production / 'coverage.json').read_text())['integrated'])

    def test_selected_revision_cleans_only_unreferenced_runtime_version(self):
        self.record()
        Image.new('RGB', (32, 64), (99, 88, 77)).save(self.candidate)
        self.record(2)
        self.assertFalse(self.image(1).exists())
        self.assertTrue(self.image(2).is_file())
        self.assertTrue((self.production / 'candidates' / self.master / 'image-v1.png').is_file())

    def test_source_drift_and_wrong_group_leave_project_unchanged(self):
        self.record()
        path = self.files / 'stage-1.mcq'
        data = path.read_bytes()
        path.write_bytes(data + b'drift')
        before = self.snapshot()
        with self.assertRaises(ValueError):
            self.record(2)
        self.assertEqual(before, self.snapshot())
        path.write_bytes(data)
        ledger = json.loads((self.production / 'battle-skies.json').read_text())
        for asset in ledger['assets']:
            asset['decodedRgbaSha256'] = '0' * 64
        self.control('battle-skies.json', ledger)
        before = self.snapshot()
        with self.assertRaises(ValueError):
            self.record(2)
        self.assertEqual(before, self.snapshot())

    def test_drifted_runtime_malformed_control_and_version_conflicts_preflight(self):
        self.record()
        original = self.image(1).read_bytes()
        self.image(1).write_bytes(b'corrupt')
        before = self.snapshot()
        with self.assertRaises(ValueError):
            self.record(2)
        self.assertEqual(before, self.snapshot())
        self.image(1).write_bytes(original)
        for name in ('battle-generation-tasks.json', 'coverage.json'):
            path = self.production / name
            valid = path.read_bytes()
            path.write_text('[')
            before = self.snapshot()
            with self.assertRaises(ValueError):
                self.record(2)
            self.assertEqual(before, self.snapshot())
            path.write_bytes(valid)
        Image.new('RGB', (32, 64), (99, 88, 77)).save(self.candidate)
        before = self.snapshot()
        with self.assertRaises(FileExistsError):
            self.record()
        self.assertEqual(before, self.snapshot())

    def test_oversized_candidate_is_bounded_before_decode(self):
        with self.candidate.open('wb') as stream:
            stream.truncate(32 * 1024 * 1024 + 1)
        before = self.snapshot()
        with patch.object(sky.Image, 'open', side_effect=AssertionError('Must reject before decode')):
            with self.assertRaises(ValueError):
                self.record()
        self.assertEqual(before, self.snapshot())

    def test_write_failure_rolls_back_complete_import(self):
        self.record()
        Image.new('RGB', (32, 64), (99, 88, 77)).save(self.candidate)
        before = self.snapshot()
        write = sky.atomic_write
        calls = 0
        def fail_once(path, data):
            nonlocal calls
            calls += 1
            if calls == 5:
                raise OSError('Synthetic publication failure')
            write(path, data)
        with patch.object(sky, 'atomic_write', side_effect=fail_once):
            with self.assertRaises(OSError):
                self.record(2)
        self.assertEqual(before, self.snapshot())


if __name__ == '__main__':
    unittest.main()
