#!/usr/bin/env python3
"""Original synthetic ModelsHD roster fixtures, AGPL v3; see LICENSE."""
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


roster = load('roster', 'build-modelshd-roster.py')
fixtures = load('fixtures', 'test-model-material-audit.py')


class RosterTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.files = self.root / 'files'
        self.output = self.root / 'new-pack'
        for index, (name, form) in enumerate(roster.ROSTER):
            folder = self.files / 'characters' / name / 'models' / form
            folder.mkdir(parents=True)
            data = bytearray(fixtures.model())
            for i, vertex in enumerate(((0, 0, 0), (10+index, 0, 0), (0, 10, 0))):
                struct.pack_into('<3h', data, 52+i*8, *vertex)
            struct.pack_into('<3h', data, 76, 0, 0, 4096)
            (folder / '32').write_bytes(data)
            (folder / '0').write_bytes(struct.pack('<4I6h', 12, 0, 0, 1 | 2 << 16, 0, 0, 0, 0, 0, 0))

    def test_complete_roster_separates_controls_and_installable_packs(self):
        report = roster.build(self.files, self.output)
        self.assertEqual(len(report['models']), 19)
        self.assertEqual(len(list((self.output / 'model-packs/modelshd/battle').glob('*.json'))), 19)
        self.assertEqual(len(list((self.output / 'verification/controls').glob('*.json'))), 19)
        for row in report['models']:
            self.assertEqual(row['candidateTriangles'], 4)
            self.assertEqual(row['checks']['openBorderVerticesUnchanged'], 3)
            self.assertEqual(row['animations']['sampled'][0]['keyframes'], 1)
            pack = json.loads((self.output / 'model-packs/modelshd/battle' / (row['sourceGeometrySha256'] + '.json')).read_text())
            self.assertEqual(pack['parts'][0]['faces'][0]['sourceWeights'], [[1,0,0],[0.5,0.5,0],[0.5,0,0.5]])

    def test_missing_last_source_never_publishes_partial_roster(self):
        (self.files / 'characters/divine/models/dragoon/32').unlink()
        with self.assertRaises(FileNotFoundError):
            roster.build(self.files, self.output)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.root.glob('.modelshd-build-*')), [])

    def test_unsupported_last_container_cleans_staged_packs(self):
        path = self.files / 'characters/divine/models/dragoon/32'
        data = bytearray(path.read_bytes()); struct.pack_into('<I', data, 4, 12); path.write_bytes(data)
        with self.assertRaisesRegex(ValueError, 'auxiliary'):
            roster.build(self.files, self.output)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.root.glob('.modelshd-build-*')), [])

    def test_existing_output_and_source_files_remain_untouched(self):
        source = self.files / 'characters/dart/models/combat/32'
        original = source.read_bytes()
        self.output.mkdir(); (self.output / 'owner.txt').write_text('keep')
        with self.assertRaises(FileExistsError):
            roster.build(self.files, self.output)
        self.assertEqual((self.output / 'owner.txt').read_text(), 'keep')
        self.assertEqual(source.read_bytes(), original)

    def test_geometry_check_rejects_moved_joint_border(self):
        data = (self.files / 'characters/dart/models/combat/32').read_bytes()
        pack, reports = roster.exporter.export(data)
        parts, _, _ = roster.exporter.native_parts(data)
        pack['parts'][0]['vertices'][0][0] += 1
        with self.assertRaisesRegex(ValueError, 'joint border'):
            roster.geometry_checks(parts, pack, reports)


if __name__ == '__main__':
    unittest.main()
