#!/usr/bin/env python3
"""Packing/selection regression checks using tiny synthetic game sources."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image


def module(name, path):
    spec = importlib.util.spec_from_file_location(name,path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


pack = module('terrain_pack_test',Path(__file__).with_name('pack-envhd-terrain.py'))
fixtures = module('terrain_source_test_fixtures',Path(__file__).with_name('test-envhd-terrain.py'))


class TerrainPackingTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        base = Path(temporary.name)
        self.files,self.repo,self.output = base/'files',base/'repo',base/'atlases'
        self.addCleanup(setattr,pack,'ROOT',pack.ROOT)
        pack.ROOT = self.repo
        drgn = self.files/'SECT/DRGN0.BIN'
        drgn.mkdir(parents=True)
        for i in range(8):
            bank = drgn/str(5697+i)
            bank.mkdir()
            (bank/'0').write_bytes(fixtures.tim())
            (drgn/str(5705+i)).write_bytes(fixtures.model())
        scenes,masters = pack.terrain.census(self.files)
        self.production = self.repo/'integrations/envhd/production'
        self.records = []
        for key,source in masters.items():
            folder = self.production/'terrain-candidates'/key
            folder.mkdir(parents=True)
            path = folder/'image-v1.png'
            source['image'].resize((source['image'].width*4,source['image'].height*4),Image.Resampling.NEAREST).save(path)
            record = pack.terrain.candidate_record(source,pack.terrain.digest(path.read_bytes()))
            record.update(reviewStatus='visual-reviewed-runtime-pending',batchPipelineSha256=pack.candidate_import.REVIEWED_BATCH_PIPELINE_SHA256,
                          review=dict(intent='Synthetic shader colors',style='Development baseline',layout='Exact source placement',verdict='visual-reviewed-runtime-pending',
                                      outputSha256=record['outputSha256'],nativeAcceptance='pending',finalQualityAcceptance='pending'))
            self.records.append(record)
            (folder/'manifest-v1.json').write_text(json.dumps(record))
        self.ledger = dict(scenes=scenes,assets=self.records)
        self.write_ledger()

    def write_ledger(self):
        (self.production/'terrain-artwork.json').write_text(json.dumps(self.ledger))

    def update_record(self,key,value):
        record = self.records[0]
        record[key] = value
        self.write_ledger()
        folder = self.production/'terrain-candidates'/record['decodedRgbaSha256']
        (folder/'manifest-v1.json').write_text(json.dumps(record))

    def test_deterministic_shelves_dedup_and_bounds(self):
        size,positions = pack.layout({'z':(32,32),'a':(32,32),'b':(16,64)})
        self.assertEqual(positions['b'],[32,32,64,256])
        self.assertLess(positions['a'][0],positions['z'][0])
        self.assertLessEqual(size[1],4096)
        self.assertEqual(pack.layout({}),([4096,4],{}))
        with self.assertRaises(ValueError):
            pack.layout({'large':(1024,1)})

    def test_repeatable_pack_and_runtime_selection_contains_only_custom_assets(self):
        inventory = pack.pack(self.files,self.output)
        snapshots = {p.relative_to(self.output):p.read_bytes() for p in self.output.rglob('*') if p.is_file()}
        pack.pack(self.files,self.output)
        self.assertEqual(snapshots,{p.relative_to(self.output):p.read_bytes() for p in self.output.rglob('*') if p.is_file()})
        self.assertEqual(len(inventory),8)
        result = pack.publish(self.files,self.output)
        self.assertEqual(result['runtimeSelectedMasters'],1)
        self.assertEqual(result['runtimeSelectedBindings'],8)
        runtime = self.repo/'integrations/envhd/runtime-assets'
        # Identical model/bank sources across fixtures share one resource directory.
        self.assertEqual(sorted(p.name for p in runtime.rglob('*') if p.is_file()),['atlas-v1.png','manifest.json'])
        self.assertFalse(any('source' in p.name or 'private' in p.name for p in runtime.rglob('*')))

    def test_hash_review_provenance_and_metadata_fail_before_any_runtime_write(self):
        mutations = [dict(outputSha256='wrong'),dict(batchPipelineSha256='wrong'),dict(bindings=[]),dict(method='nearest'),
                     dict(reviewStatus='intent-revision-needed')]
        original = dict(self.records[0])
        for mutation in mutations:
            self.records[0].clear();self.records[0].update(original)
            self.records[0].update(mutation);self.write_ledger()
            with self.assertRaises(ValueError):
                pack.pack(self.files,self.output)
            self.assertFalse(self.output.exists())

    def test_review_missing_intent_or_claiming_acceptance_rejected(self):
        for key,value in [('intent',''),('nativeAcceptance','accepted'),('finalQualityAcceptance','accepted')]:
            original = self.records[0]['review']
            changed = dict(original);changed[key] = value
            self.update_record('review',changed)
            with self.assertRaises(ValueError):
                pack.pack(self.files,self.output)
            self.update_record('review',original)

    def test_uniform_exemption_requires_constant_source(self):
        review = dict(self.records[0]['review'],verdict='retain-native-uniform')
        self.update_record('review',review);self.update_record('reviewStatus','retain-native-uniform')
        with self.assertRaises(ValueError):
            pack.pack(self.files,self.output)

    def test_candidate_ledger_must_match_immutable_version(self):
        self.records[0]['review']['intent'] = 'Changed ledger only'
        self.write_ledger()
        with self.assertRaises(ValueError):
            pack.pack(self.files,self.output)

    def test_stale_sources_fail_before_selection(self):
        pack.pack(self.files,self.output)
        (self.files/'SECT/DRGN0.BIN/5697/0').write_bytes(fixtures.tim(colour=31<<5))
        with self.assertRaises(ValueError):
            pack.publish(self.files,self.output)
        self.assertFalse((self.repo/'integrations/envhd/runtime-assets').exists())

    def test_mutated_staged_atlas_fails_before_selection(self):
        pack.pack(self.files,self.output)
        png = next(self.output.rglob('atlas-v1.png'))
        png.write_bytes(png.read_bytes()+b'changed')
        with self.assertRaises(ValueError):
            pack.publish(self.files,self.output)
        self.assertFalse((self.repo/'integrations/envhd/runtime-assets').exists())

    def test_existing_runtime_version_is_immutable(self):
        pack.pack(self.files,self.output);pack.publish(self.files,self.output)
        png = next((self.repo/'integrations/envhd/runtime-assets').rglob('atlas-v1.png'))
        png.write_bytes(b'conflicting version')
        with self.assertRaises(FileExistsError):
            pack.publish(self.files,self.output)
        self.assertEqual(png.read_bytes(),b'conflicting version')

    def test_private_staging_guard(self):
        for path in (self.repo/'staging',self.files/'staging'):
            with self.assertRaises(ValueError):
                pack.pack(self.files,path)


if __name__ == '__main__':
    unittest.main()
