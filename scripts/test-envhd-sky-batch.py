#!/usr/bin/env python3
"""Behavior fixtures for the batch workflow; synthetic art only, no game assets."""
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
from PIL import Image


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


batch = load('sky_batch', 'batch-envhd-skies.py')
fixtures = load('sky_import_fixtures', 'test-envhd-sky-import.py')


class BatchTest(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.SkyImportTest('runTest')
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root, self.files = self.fixture.root, self.fixture.files
        ledger = json.loads((self.fixture.production / 'battle-skies.json').read_text())
        for entry in ledger['assets']:
            entry['targetSize'] = [64, 128]
        self.fixture.control('battle-skies.json', ledger)
        Image.new('RGB', (64, 128), (40, 100, 70)).save(self.fixture.candidate)

    def test_plan_groups_alias_headers_and_retains_selected_without_touching_project(self):
        self.fixture.record()
        before = self.fixture.snapshot()
        jobs, _ = batch.plan(self.root, self.files)
        self.assertEqual([(jobs[0]['action'], jobs[0]['stages'])], [('retain-selected', [0, 1])])
        self.assertEqual(before, self.fixture.snapshot())

    def test_header_variant_drift_stops_entire_preflight(self):
        path = self.files / 'stage-1.mcq'
        path.write_bytes(path.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'Source changed'):
            batch.plan(self.root, self.files)

    def test_uniform_black_exemption_is_verified_and_excluded(self):
        data = bytearray(fixtures.source(0))
        struct.pack_into('<H', data, 40 + (41 * 16 + 2) * 2, 0x8000)
        path = self.files / 'uniform.mcq'
        path.write_bytes(data)
        key = batch.sky.digest(data)
        decoded = batch.sky.decoder.decode(data)
        entry = dict(sourceMcqSha256=key, decodedRgbaSha256=batch.fingerprint(decoded),
                     generationMasterSourceSha256=key, sourceFiles=['uniform.mcq'],
                     sourceSize=[16, 32], targetSize=[64, 128], stages=[3], status='no-generation-required')
        self.fixture.control('battle-skies.json', {'assets': [entry]})
        self.fixture.control('battle-generation-tasks.json', {'tasks': [{'masterSourceSha256': key}]})
        self.assertEqual([], batch.plan(self.root, self.files)[0])
        entry['decodedRgbaSha256'] = self.fixture.group
        path.write_bytes(fixtures.source(0))
        entry['sourceMcqSha256'] = self.fixture.master
        entry['generationMasterSourceSha256'] = self.fixture.master
        self.fixture.control('battle-skies.json', {'assets': [entry]})
        self.fixture.control('battle-generation-tasks.json', {'tasks': [{'masterSourceSha256': self.fixture.master}]})
        with self.assertRaisesRegex(ValueError, 'uniform opaque black'):
            batch.plan(self.root, self.files)

    def test_repair_requires_recorded_hash_and_dimensions(self):
        self.fixture.record(verdict='repeat-boundary-revision-needed')
        jobs, _ = batch.plan(self.root, self.files)
        self.assertEqual('repair-reviewed-layout', jobs[0]['action'])
        image = self.fixture.production / 'candidates' / self.fixture.master / 'image-v1.png'
        image.write_bytes(image.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'Candidate differs'):
            batch.plan(self.root, self.files)

    def test_legacy_source_bound_candidate_can_derive_pixel_identity_from_verified_mcq(self):
        self.fixture.record(verdict='repeat-boundary-revision-needed')
        path = self.fixture.production / 'candidates' / self.fixture.master / 'manifest-v1.json'
        metadata = json.loads(path.read_text())
        metadata.pop('decodedRgbaSha256')
        path.write_text(json.dumps(metadata))
        self.assertEqual('repair-reviewed-layout', batch.plan(self.root, self.files)[0][0]['action'])

    def test_stale_layout_review_and_corrupt_selected_resources_stop_planning(self):
        self.fixture.record(verdict='repeat-boundary-revision-needed')
        path = self.fixture.production / 'candidates' / self.fixture.master / 'manifest-v1.json'
        metadata = json.loads(path.read_text())
        metadata['reviewStatus'] = 'intent-revision-needed'
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, 'reviewed-layout verdict'):
            batch.plan(self.root, self.files)
        self.fixture.record()
        image = self.fixture.image(1)
        image.write_bytes(image.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'runtime artwork changed'):
            batch.plan(self.root, self.files)

    def test_missing_shared_selection_manifest_stops_planning(self):
        self.fixture.record()
        self.fixture.manifest(self.fixture.alias).unlink()
        with self.assertRaises(FileNotFoundError):
            batch.plan(self.root, self.files)

    def test_selected_scale_drift_stops_planning(self):
        self.fixture.record()
        path = self.fixture.manifest(self.fixture.master)
        metadata = json.loads(path.read_text())
        metadata['scale'] = 2
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, 'source-bound 4x'):
            batch.plan(self.root, self.files)

    def test_layout_failed_art_uses_original_instead_of_repairing_wrong_shapes(self):
        self.fixture.record(verdict='intent-revision-needed')
        jobs, _ = batch.plan(self.root, self.files)
        self.assertEqual('upscale-original', jobs[0]['action'])

    def test_visibility_and_visible_black_are_exact_and_central_art_is_unchanged(self):
        source = np.full((8, 16, 4), (40, 60, 80, 255), dtype=np.uint8)
        source[1, 0] = (0, 0, 0, 255)  # Visible opaque black.
        source[2, 15] = (100, 80, 70, 0)  # Hidden source color.
        pixels = np.full((32, 64, 3), (90, 100, 110), dtype=np.uint8)
        pixels[:, :8] = (20, 30, 40)
        pixels[:, -8:] = (150, 160, 170)
        rgba, protected = batch.preserve_visibility(Image.fromarray(source), pixels)
        repaired = batch.join_edges(rgba, protected, 8)
        self.assertTrue(np.array_equal(repaired[:, 8:-8], rgba[:, 8:-8]))
        self.assertTrue(np.array_equal(repaired[:, :, 3], np.repeat(np.repeat(source[:, :, 3], 4, axis=0), 4, axis=1)))
        self.assertTrue(np.array_equal(repaired[protected], rgba[protected]))
        self.assertEqual((0, 0, 0, 255), tuple(repaired[4, 0]))
        self.assertEqual((0, 0, 0, 0), tuple(repaired[8, -1]))
        mutable = ~protected[:, 0] & ~protected[:, -1]
        self.assertTrue(np.array_equal(repaired[mutable, 0], repaired[mutable, -1]))

    def test_ordinary_source_colors_cannot_become_new_opaque_black(self):
        source = Image.new('RGBA', (16, 8), (8, 16, 24, 255))
        zeros = np.zeros((32, 64, 3), dtype=np.uint8)
        restored, protected = batch.preserve_visibility(source, zeros)
        self.assertFalse(np.any(np.all(restored[:, :, :3] == 0, axis=2)))
        restored[:, 0, :3] = (1, 0, 0)
        restored[:, -1, :3] = (0, 1, 0)
        joined = batch.join_edges(restored, protected, 8)
        final, _ = batch.preserve_visibility(source, joined[:, :, :3])
        self.assertFalse(np.any(np.all(final[:, :, :3] == 0, axis=2)))

    def test_tool_drift_stops_before_output_work_or_inference(self):
        jobs, entries = batch.plan(self.root, self.files)
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            with patch.object(batch, 'file_digest', return_value='wrong'), patch.object(batch.subprocess, 'run') as run:
                with self.assertRaisesRegex(ValueError, 'pinned versions'):
                    batch.execute(self.root, self.files, output, jobs, entries, output / 'engine', output / 'models')
                run.assert_not_called()
            self.assertEqual([], list(output.iterdir()))

    def test_private_work_is_refused_inside_project_or_originals(self):
        for path in (self.root / 'output', self.files / 'output'):
            with self.assertRaisesRegex(ValueError, 'outside the project'):
                batch.private_output(self.root, self.files, path)
            self.assertFalse(path.exists())

    def test_execution_creates_review_candidate_without_changing_selection_or_sources(self):
        jobs, entries = batch.plan(self.root, self.files)
        before = self.fixture.snapshot()
        private_before = {p.name: p.read_bytes() for p in self.files.iterdir()}

        def synthetic_inference(command, **kwargs):
            # Synthetic fixture backend only; no neural runtime or retail artwork.
            input_path = command[command.index('-i') + 1]
            output_path = command[command.index('-o') + 1]
            with Image.open(input_path) as im:
                im.resize((im.width * 4, im.height * 4), Image.Resampling.NEAREST).save(output_path)
            return type('Run', (), {'returncode': 0, 'stdout': b'', 'stderr': b''})()

        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            with patch.object(batch, 'file_digest', side_effect=[batch.ENGINE, batch.WEIGHTS, batch.PARAMETERS, 'output-hash']), patch.object(batch.subprocess, 'run', side_effect=synthetic_inference):
                results = batch.execute(self.root, self.files, output, jobs, entries, output / 'engine', output / 'models')
            self.assertEqual(1, len(results))
            self.assertTrue(results[0]['reviewStatus'].startswith('pending-'))
            with Image.open(output / (self.fixture.master + '.png')) as im:
                self.assertEqual((64, 128), im.size)
            self.assertFalse(json.loads((output / 'candidates.json').read_text())['installed'])
        self.assertEqual(before, self.fixture.snapshot())
        self.assertEqual(private_before, {p.name: p.read_bytes() for p in self.files.iterdir()})

    def test_neural_import_retains_actual_method_and_rejects_tool_drift(self):
        ledger = json.loads((self.fixture.production / 'battle-skies.json').read_text())
        entry = ledger['assets'][0]
        record = dict(masterSourceSha256=self.fixture.master, decodedRgbaSha256=self.fixture.group,
                      outputSha256=batch.sky.digest(self.fixture.candidate.read_bytes()),
                      sourceSize=[16, 32], targetSize=[64, 128], repairBorderPixels=8,
                      sourceVisibility='exact-nearest-4x-discard-and-visible-black',
                      reviewStatus='pending-source-intent-style-layout-wrap-review',
                      method='real-esrgan-x4plus-periodic-python-border-repair', action='upscale-original',
                      inputSha256=self.fixture.group, **batch.sky.BATCH_TOOL_HASHES)
        before = self.fixture.snapshot()
        record['engineSha256'] = 'wrong'
        with self.assertRaisesRegex(ValueError, 'pinned tool identity'):
            batch.sky.record(self.root, self.files, self.fixture.candidate, self.fixture.master, 1,
                             self.fixture.prompt, 'visual-reviewed-native-pending', self.fixture.notes, record)
        self.assertEqual(before, self.fixture.snapshot())
        record.update(batch.sky.BATCH_TOOL_HASHES)
        batch.sky.record(self.root, self.files, self.fixture.candidate, self.fixture.master, 1,
                         self.fixture.prompt, 'visual-reviewed-native-pending', self.fixture.notes, record)
        metadata = json.loads(self.fixture.manifest(self.fixture.master).read_text())
        self.assertEqual(record['method'], metadata['method'])
        self.assertEqual(batch.ENGINE, metadata['productionRecord']['engineSha256'])
        self.assertEqual('visual-review-only', metadata['styleReferenceUse'])

    def test_source_edge_baseline_preserves_asymmetric_structure_and_records_no_border_mix(self):
        self.fixture.record(verdict='repeat-boundary-revision-needed')
        jobs, entries = batch.plan(self.root, self.files, source_edge_baselines=True)
        self.assertEqual('upscale-original', jobs[0]['action'])
        self.assertEqual(self.fixture.group, jobs[0]['inputSha256'])
        expected = None

        def synthetic_inference(command, **kwargs):
            nonlocal expected
            with Image.open(command[command.index('-i') + 1]) as im:
                pixels = np.zeros((im.height * 4, im.width * 4, 3), dtype=np.uint8)
            pixels[:, :, 0] = np.arange(pixels.shape[1]) % 251 + 1
            pixels[:, :, 1:] = 60
            Image.fromarray(pixels).save(command[command.index('-o') + 1])
            original = batch.original(self.files, entries[self.fixture.master])
            expected, _ = batch.preserve_visibility(original, pixels[64:192, 256:320])
            return type('Run', (), {'returncode': 0, 'stdout': b'', 'stderr': b''})()

        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            pins = {'engine': batch.ENGINE, 'realesrgan-x4plus.bin': batch.WEIGHTS,
                    'realesrgan-x4plus.param': batch.PARAMETERS}
            def digest(path):
                return pins[path.name] if path.name in pins else batch.sky.digest(path.read_bytes())
            with patch.object(batch, 'file_digest', side_effect=digest), patch.object(batch.subprocess, 'run', side_effect=synthetic_inference):
                records = batch.execute(self.root, self.files, output, jobs, entries, output / 'engine', output / 'models', source_edge_baselines=True)
            candidate = output / (self.fixture.master + '.png')
            with Image.open(candidate) as image:
                np.testing.assert_array_equal(expected, np.asarray(image))
            self.assertFalse(np.array_equal(expected[:, 0], expected[:, -1]))
            record = records[0]
            self.assertEqual(0, record['repairBorderPixels'])
            self.assertEqual('real-esrgan-x4plus-periodic-preserved-source-edges', record['method'])
            before = self.fixture.snapshot()
            with self.assertRaisesRegex(ValueError, 'provenance differs'):
                batch.sky.record(self.root, self.files, candidate, self.fixture.master, 2,
                                 self.fixture.prompt, 'visual-reviewed-native-pending', self.fixture.notes,
                                 dict(record, repairBorderPixels=8))
            self.assertEqual(before, self.fixture.snapshot())
            batch.sky.record(self.root, self.files, candidate, self.fixture.master, 2,
                             self.fixture.prompt, 'visual-reviewed-native-pending', self.fixture.notes, record)
            metadata = json.loads(self.fixture.manifest(self.fixture.master).read_text())
            self.assertEqual(record['method'], metadata['method'])
            self.assertEqual(0, metadata['productionRecord']['repairBorderPixels'])

    def test_repair_import_requires_recorded_reviewed_predecessor(self):
        self.fixture.record(verdict='repeat-boundary-revision-needed')
        predecessor = self.fixture.production / 'candidates' / self.fixture.master / 'image-v1.png'
        record = dict(masterSourceSha256=self.fixture.master, decodedRgbaSha256=self.fixture.group,
                      outputSha256=batch.sky.digest(self.fixture.candidate.read_bytes()),
                      sourceSize=[16, 32], targetSize=[64, 128], repairBorderPixels=8,
                      sourceVisibility='exact-nearest-4x-discard-and-visible-black',
                      reviewStatus='pending-source-intent-style-layout-wrap-review',
                      method='python-border-repair', action='repair-reviewed-layout',
                      inputSha256=batch.sky.digest(predecessor.read_bytes()), inputImageFile='image-v1.png',
                      engineSha256=None, weightsSha256=None, parametersSha256=None)
        record['inputSha256'] = 'wrong'
        before = self.fixture.snapshot()
        with self.assertRaisesRegex(ValueError, 'predecessor differs'):
            batch.sky.record(self.root, self.files, self.fixture.candidate, self.fixture.master, 2,
                             self.fixture.prompt, 'visual-reviewed-native-pending', self.fixture.notes, record)
        self.assertEqual(before, self.fixture.snapshot())
        record['inputSha256'] = batch.sky.digest(predecessor.read_bytes())
        batch.sky.record(self.root, self.files, self.fixture.candidate, self.fixture.master, 2,
                         self.fixture.prompt, 'visual-reviewed-native-pending', self.fixture.notes, record)
        self.assertEqual(record['method'], json.loads(self.fixture.manifest(self.fixture.master).read_text())['method'])


if __name__ == '__main__':
    unittest.main()
