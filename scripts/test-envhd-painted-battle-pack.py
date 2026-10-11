#!/usr/bin/env python3
"""Manifest drift aborts battle selection before any runtime publication."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
from unittest import mock

from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


fixture = module('battle_pack_fixture', 'test-envhd-painted-battle.py')
pack = module('battle_pack_test', 'pack-envhd-battle-materials.py')


class PaintedBattlePackTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixture.PaintedBattleTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        self.production = self.root/'integrations/envhd/production'
        self.scenes, self.masters, self.records = [], {}, []
        self.paths = [self.fixture.path]
        self.add_source(self.fixture.source, self.fixture.candidate, 44)
        self.fixture.manifest['candidates'] = [self.fixture.candidate]
        self.fixture.save()
        self.write_ledger()
        self.runtime = self.root/'integrations/envhd/runtime-assets/envhd/battle-materials'
        (self.runtime/'previous').mkdir(parents=True)
        (self.runtime/'previous/atlas-v1.png').write_bytes(b'previous atlas')
        (self.runtime/'previous/manifest.json').write_bytes(b'previous manifest')
        self.selection = self.production/'battle-material-runtime-artwork.json'
        self.selection.write_text(json.dumps(dict(schema=1, runtimeSelectedMasters=0,
            runtimeSelectedBindings=0, scenes=[], scope='Previous synthetic runtime selection')))
        self.before = self.runtime_snapshot()

    def runtime_snapshot(self):
        return {str(p.relative_to(self.root)): p.read_bytes()
                for p in [*self.runtime.rglob('*'), self.selection] if p.is_file()}

    def add_source(self, source, candidate, stage):
        key = pack.tools.terrain.fingerprint(source)
        item = dict(decodedRgbaSha256=key, sourceSize=list(source.size), image=source,
                    bindings=candidate['sourceBindings'])
        self.masters[key] = item
        folder = self.production/'battle-material-candidates'/key
        folder.mkdir(parents=True)
        source.resize((source.width*4, source.height*4), Image.Resampling.NEAREST).save(folder/'image-v1.png')
        digest = pack.tools.terrain.digest((folder/'image-v1.png').read_bytes())
        record = pack.tools.terrain.candidate_record(item, digest)
        record['reviewStatus'] = 'intent-revision-needed'
        record['review'] = dict(intent='Synthetic held source', style='Synthetic neural history',
                               layout='Source aligned', verdict='intent-revision-needed',
                               outputSha256=digest, nativeAcceptance='pending', finalQualityAcceptance='pending')
        (folder/'manifest-v1.json').write_text(json.dumps(record))
        self.records.append(record)
        candidate['priorCandidateSha256'] = digest
        material = dict(candidate['sourceBindings'][0], status='candidate-pending',
                        decodedRgbaSha256=key, sourceSize=list(source.size))
        self.scenes.append(dict(stage=stage, modelSha256=f'model-{stage}', timSha256=f'tim-{stage}',
                                materials=[material]))

    def add_second_manifest(self):
        source = self.fixture.source.copy()
        source.putpixel((0, 0), (214, 255, 222, 0))
        key = pack.tools.terrain.fingerprint(source)
        folder = self.production/'bespoke-battle-candidates'/key
        folder.mkdir(parents=True)
        raw, output, original = folder/'painted-input-v1.png', folder/'image-v1.png', self.root/'second-source.png'
        source.save(original)
        raw.write_bytes(self.fixture.raw.read_bytes())
        record, _ = pack.painted_tools.repair.execute(original, raw, output, key, [148, 180, 156, 0], 3)
        candidate = copy.deepcopy(self.fixture.candidate)
        candidate.update(record, generatedPath=str(raw.relative_to(self.root)),
                         outputPath=str(output.relative_to(self.root)))
        candidate['sourceBindings'][0]['stage'] = 45
        candidate['review']['outputSha256'] = record['outputSha256']
        self.add_source(source, candidate, 45)
        manifest = copy.deepcopy(self.fixture.manifest)
        manifest['candidates'] = [candidate]
        path = self.production/'second-painted.json'
        path.write_text(json.dumps(manifest))
        self.paths.append(path)
        self.write_ledger()

    def write_ledger(self):
        (self.production/'battle-material-artwork.json').write_text(json.dumps(
            dict(scenes=self.scenes, emptyModelSlots=[], assets=self.records)))

    def publish(self):
        census = (self.scenes, self.masters, [])
        with mock.patch.object(pack, 'ROOT', self.root), \
                mock.patch.object(pack.battle_sources, 'census', return_value=census), \
                mock.patch.object(pack.painted_tools.battle, 'census', return_value=census):
            return pack.publish(self.root/'files', self.paths)

    def test_two_valid_manifests_publish_matching_current_receipts(self):
        self.add_second_manifest()
        result = self.publish()
        self.assertEqual(result['runtimeSelectedMasters'], 2)
        self.assertEqual(result['paintedSelectedMasters'], 2)
        self.assertEqual(result['paintedReviewReceipts'], {
            hashlib.sha256(p.read_bytes()).hexdigest(): str(p.relative_to(self.root)) for p in self.paths})
        self.assertFalse((self.runtime/'previous/atlas-v1.png').exists())

    def test_mutation_during_preflight_preserves_all_previous_runtime_bytes(self):
        original = pack.painted_tools.repair.repair
        def mutate(*args, **kwargs):
            result = original(*args, **kwargs)
            self.paths[0].write_bytes(self.paths[0].read_bytes() + b' ')
            return result
        with mock.patch.object(pack.painted_tools.repair, 'repair', side_effect=mutate):
            with self.assertRaisesRegex(ValueError, 'changed during preflight'):
                self.publish()
        self.assertEqual(self.runtime_snapshot(), self.before)

    def test_earlier_manifest_mutation_while_later_one_verifies_aborts(self):
        self.add_second_manifest()
        original = pack.painted_tools.preflight
        def mutate(files, path, root):
            result = original(files, path, root)
            if path == self.paths[1]:
                self.paths[0].write_bytes(self.paths[0].read_bytes() + b' ')
            return result
        with mock.patch.object(pack.painted_tools, 'preflight', side_effect=mutate):
            with self.assertRaisesRegex(ValueError, 'changed after preflight'):
                self.publish()
        self.assertEqual(self.runtime_snapshot(), self.before)

    def test_mutation_during_atlas_assembly_preserves_previous_runtime_bytes(self):
        self.add_second_manifest()
        original = pack.tools.layout
        def mutate(*args):
            result = original(*args)
            self.paths[1].write_bytes(self.paths[1].read_bytes() + b' ')
            return result
        with mock.patch.object(pack.tools, 'layout', side_effect=mutate):
            with self.assertRaisesRegex(ValueError, 'changed after preflight'):
                self.publish()
        self.assertEqual(self.runtime_snapshot(), self.before)

    def test_duplicate_manifest_is_rejected_without_runtime_writes(self):
        self.paths.append(self.paths[0])
        with self.assertRaisesRegex(ValueError, 'Duplicate painted battle manifest'):
            self.publish()
        self.assertEqual(self.runtime_snapshot(), self.before)

    def test_same_source_in_distinct_manifests_cannot_be_selected_twice(self):
        other = self.production/'duplicate-source.json'
        other.write_bytes(self.paths[0].read_bytes() + b' ')
        self.paths.append(other)
        with self.assertRaisesRegex(ValueError, 'already selected/protected source'):
            self.publish()
        self.assertEqual(self.runtime_snapshot(), self.before)


if __name__ == '__main__':
    unittest.main()
