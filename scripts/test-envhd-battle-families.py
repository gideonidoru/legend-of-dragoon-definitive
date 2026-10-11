#!/usr/bin/env python3
"""Behavioral sequence/consumer regressions; synthetic words only, no game window."""
import importlib.util
import gzip
import json
import os
import struct
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT/relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


family = load('families', 'scripts/discover-envhd-battle-families.py')
native = load('native', 'scripts/verify-envhd-battle-native-replay.py')
fixture = load('battle_fixture', 'scripts/test-envhd-battle-materials.py')


def synthetic():
    palette = np.zeros((1, 16), dtype='<u2')
    palette[0, :4] = [0, 0x8000, 31, 0x801f]
    image = np.array([[0x3210, 0x3210], [0x0000, 0x0000],
                      [0x1111, 0x1111], [0x2222, 0x2222]], dtype='<u2')
    def block(x, y, words):
        h, w = words.shape
        return struct.pack('<I4H', 12 + words.nbytes, x, y, w, h) + words.tobytes()
    tim = struct.pack('<II', 16, 8) + block(0, 480, palette) + block(64, 0, image)
    model = fixture.model(uv=((0, 0), (3, 0), (0, 0)), animation=(64, 2, 8, 2))
    scene, _ = family.battle.scene(model, tim, 10)
    material = scene['materials'][0]
    row = dict(stage=10, modelSha256=family.digest(model), timSha256=family.digest(tim),
        material=material, paddedSourceVramBounds=[64, 0, 2, 4], paletteVramBounds=[0, 480, 16, 1],
        dependencyTags=['padded-image-context/animated-rectangle'], intersectingAnimationSlots=[{'slot': 0}])
    sequence = family.sequences(model)[0]
    slot = dict(**sequence, rect=[64, 2, 2, 2], cycle=family.cycle(sequence['values']))
    vram, written, tims = family.battle.terrain.upload_bank([(0, tim)])
    faces = family.consumer_contracts(model)[1, 480 << 6]
    return row, slot, vram, written, tims[0]['image'], faces


class BattleFamilyTest(unittest.TestCase):
    def test_worklist_change_during_shard_publication_cleans_owned_files_and_rejects_final_link(self):
        row, slot, vram, written, image, faces = synthetic()
        binding = family.binding_cycle(row, [slot], vram, written, image, faces)
        old = {'snapshot': 'old'}
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            worklist, output, details = root/'worklist.json', root/'ledger.json', root/'shards'
            worklist.write_text(json.dumps(old))
            real_publish = family.publish_metadata
            def changed(result, output, details, before_publish):
                worklist.write_text(json.dumps({'snapshot': 'changed-before-final-publication'}))
                return real_publish(result, output, details, before_publish=before_publish)
            argv = ['discover-envhd-battle-families.py', '--files', str(root/'originals'),
                    '--worklist', str(worklist), '--output', str(output),
                    '--details-dir', str(details), '--java-home', str(root/'unused-java')]
            result = dict(schema=1, summary={'holdsCleared': 0}, bindings=[binding])
            with mock.patch.object(sys, 'argv', argv), mock.patch.object(family, 'discover', return_value=(result, [])), mock.patch.object(family, 'verify_native_cases', return_value={'matched': True}), mock.patch.object(family, 'publish_metadata', side_effect=changed):
                with self.assertRaisesRegex(ValueError, 'worklist changed'):
                    family.main()
            self.assertFalse(output.exists())
            self.assertFalse(details.exists())
            self.assertEqual(list(root.iterdir()), [worklist])

    def test_changed_worklist_at_verification_seam_cannot_bind_new_bytes_to_old_semantics(self):
        for foreign_output in (False, True):
            with self.subTest(foreign_output=foreign_output), tempfile.TemporaryDirectory() as temp:
                root = Path(temp).resolve()
                worklist, output, details = root/'worklist.json', root/'ledger.json', root/'shards'
                old = {'snapshot': 'old'}
                worklist.write_text(json.dumps(old))
                if foreign_output:
                    output.write_bytes(b'foreign completed checkpoint')
                def discovered(files, snapshot):
                    self.assertEqual(snapshot, old)
                    return {'sourceWorklistSha256': family.json_digest(snapshot)}, []
                def changed(cases, java_home):
                    worklist.write_text(json.dumps({'snapshot': 'changed-at-verification'}))
                    return {'matched': True}
                argv = ['discover-envhd-battle-families.py', '--files', str(root/'originals'),
                        '--worklist', str(worklist), '--output', str(output),
                        '--details-dir', str(details), '--java-home', str(root/'unused-java')]
                with mock.patch.object(sys, 'argv', argv), mock.patch.object(family, 'discover', side_effect=discovered), mock.patch.object(family, 'verify_native_cases', side_effect=changed):
                    with self.assertRaisesRegex(ValueError, 'worklist changed'):
                        family.main()
                self.assertFalse(details.exists())
                if foreign_output:
                    self.assertEqual(output.read_bytes(), b'foreign completed checkpoint')
                else:
                    self.assertFalse(output.exists())
                self.assertEqual(list(root.glob('.envhd-family-ledger-*')), [])

    def publication_failure(self, phase):
        row, slot, vram, written, image, faces = synthetic()
        binding = family.binding_cycle(row, [slot], vram, written, image, faces)
        result = dict(schema=1, summary={'holdsCleared': 0}, bindings=[binding])
        real_fdopen = family.os.fdopen
        class InterruptedStream:
            def __init__(self, descriptor, mode):
                self.stream = real_fdopen(descriptor, mode)
            def __enter__(self):
                return self
            def write(self, data):
                if phase == 'write':
                    self.stream.write(data[:16])
                    raise OSError('injected disk-full after 16 bytes')
                return self.stream.write(data)
            def flush(self):
                return self.stream.flush()
            def fileno(self):
                return self.stream.fileno()
            def __exit__(self, kind, error, trace):
                self.stream.close()
                if phase == 'close' and error is None:
                    raise OSError('injected close failure')
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            primary, directory = root/'ledger.json', root/'shards'
            with mock.patch.object(family.os, 'fdopen', side_effect=InterruptedStream):
                with self.assertRaisesRegex(OSError, 'injected'):
                    family.publish_metadata(result, primary, directory)
            self.assertFalse(primary.exists())
            self.assertFalse(directory.exists())
            self.assertEqual(list(root.iterdir()), [])
            family.publish_metadata(result, primary, directory)
            self.assertEqual(json.loads(primary.read_text())['summary'], {'holdsCleared': 0})
            self.assertEqual(list(root.glob('.envhd-family-ledger-*')), [])

    def test_partial_ledger_write_leaves_no_advertised_checkpoint_and_same_path_retry_succeeds(self):
        self.publication_failure('write')

    def test_ledger_close_failure_leaves_no_advertised_checkpoint_and_same_path_retry_succeeds(self):
        self.publication_failure('close')

    def test_concurrent_final_ledger_is_preserved_without_owned_temporary_leak(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            path = root/'ledger.json'
            original = family.os.link
            def concurrent(*args, **kwargs):
                path.write_bytes(b'foreign completed checkpoint')
                return original(*args, **kwargs)
            with mock.patch.object(family.os, 'link', side_effect=concurrent):
                with self.assertRaises(FileExistsError):
                    family.write_new(path, {'source': 'our transaction'})
            self.assertEqual(path.read_bytes(), b'foreign completed checkpoint')
            self.assertEqual(list(root.glob('.envhd-family-ledger-*')), [])

    def test_temporary_collision_preserves_foreign_file_and_does_not_publish(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            existing = root/'.envhd-family-ledger-collision'
            existing.write_bytes(b'foreign temporary')
            with mock.patch.object(family.secrets, 'token_hex', return_value='collision'):
                with self.assertRaises(FileExistsError):
                    family.write_new(root/'ledger.json', {'new': True})
            self.assertEqual(list(root.iterdir()), [existing])
            self.assertEqual(existing.read_bytes(), b'foreign temporary')

    def test_linked_ledger_parent_cannot_redirect_or_modify_foreign_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            target = root/'target';target.mkdir()
            sentinel = target/'ledger.json';sentinel.write_bytes(b'foreign checkpoint')
            parent = root/'linked';parent.symlink_to(target, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, 'non-symlink directory'):
                family.write_new(parent/'ledger.json', {'new': True})
            self.assertEqual(list(target.iterdir()), [sentinel])
            self.assertEqual(sentinel.read_bytes(), b'foreign checkpoint')

    def test_count_cannot_hide_a_duplicate_consumer_and_missing_other_consumer(self):
        worklist = json.loads((ROOT/'integrations/envhd/production/battle-native-discovery-worklist.json').read_text())
        held = [i for i,r in enumerate(worklist['bindings']) if r['material']['status'] == 'held-native-texture-animation']
        worklist['bindings'][held[-1]] = worklist['bindings'][held[0]]
        with self.assertRaisesRegex(ValueError, 'Duplicate, missing or additional'):
            family.discover(Path('/nonexistent-originals-not-needed-for-this-check'), worklist)

    def test_low_nibble_delay_preserves_initial_wait_and_row_direction(self):
        values = [448, 128, 64, 64, 5, 2, -1]
        current = family.cycle(values)
        self.assertEqual((current['transient'], current['period']), (0, 128))
        state, commands = family.advance(values, family.initial_state(values))
        self.assertEqual(state, (1, 1, 1, 0))
        self.assertEqual(commands, [])
        state, commands = family.advance(values, state)
        self.assertEqual(state, (2, 0, 1, 63))
        self.assertEqual(commands, [(448, 129, 960, 256, 16, 63),
                                    (448, 128, 960, 319, 16, 1),
                                    (960, 256, 448, 128, 16, 64)])

    def test_sequence_transient_and_zero_duration_are_not_rounded_away(self):
        values = [448, 0, 16, 8, 2, 3, 2, 2, -1]
        current = family.cycle(values)
        self.assertGreater(current['transient'], 0)
        self.assertEqual(family.state_at(current, len(current['states'])), current['repeatState'])
        zero = [448, 0, 4, 2, 0, 16, -1]
        after, _ = family.advance(zero, family.initial_state(zero))
        self.assertEqual(after[1:3], (0, 1))

    def test_cycle_limit_and_malformed_native_sequences_fail_closed(self):
        with self.assertRaises(ValueError):
            family.cycle([448, 0, 4, 64, 2, 16, -1], limit=2)
        cases = [[448, 0, 4, 2, 2, 16], [448, 0, 4, 2, -1, 16, -1],
                 [448, 0, 3, 2, 2, 16, -1], [448, 0, 4, 2, -2, 16, -1],
                 [448, 0, 4, 2, 2, -16, -1], [448, 0, 4, 2, 2, 48, -1],
                 [960, 256, 4, 2, 2, 16, -1], [448, 0, 260, 2, 2, 16, -1]]
        for values in cases:
            with self.subTest(values=values), self.assertRaises(ValueError):
                family.validate_sequence(values)

    def test_decoder_preserves_stp_zero_and_visible_black_and_partial_word_edges(self):
        _, _, vram, _, _, _ = synthetic()
        self.assertEqual(family.decode_crop(vram, 1, 480 << 6, [0, 0, 4, 1]).tolist(),
                         [[ [0,0,0,0], [0,0,0,255], [255,0,0,0], [255,0,0,255] ]])
        self.assertEqual(family.decode_crop(vram, 1, 480 << 6, [1, 0, 2, 1]).tolist(),
                         [[ [0,0,0,255], [255,0,0,0] ]])

    def test_complete_context_cycle_cannot_be_replaced_by_constant_visible_sample(self):
        row, slot, vram, written, image, faces = synthetic()
        result = family.binding_cycle(row, [slot], vram, written, image, faces)
        self.assertTrue(result['paddingOnly'])
        self.assertEqual(result['distinctSampledFrameCount'], 1)
        self.assertEqual(result['distinctContextFrameCount'], 2)
        self.assertEqual(result['distinctFullPaletteCount'], 1)
        self.assertEqual(sum(r['ticks'] for r in result['schedule']), result['periodTicks'])
        self.assertFalse(result['fullEncounterContractClosed'])
        self.assertFalse(result['holdCleared'])
        self.assertFalse(result['runtimeSelected'])

    def test_even_unused_uninitialized_palette_words_keep_binding_held(self):
        row, slot, vram, written, image, faces = synthetic()
        written[480, 15] = False
        with self.assertRaises(ValueError):
            family.binding_cycle(row, [slot], vram, written, image, faces)

    def test_incomplete_padding_and_dependency_drift_fail_closed(self):
        row, slot, vram, written, image, faces = synthetic()
        written[1, 64] = False
        with self.assertRaises(ValueError):
            family.binding_cycle(row, [slot], vram, written, image, faces)
        written[1, 64] = True
        row['intersectingAnimationSlots'] = []
        with self.assertRaises(ValueError):
            family.binding_cycle(row, [slot], vram, written, image, faces)

    def test_same_sequence_different_source_is_not_same_art_family(self):
        row, slot, vram, written, image, faces = synthetic()
        first = family.binding_cycle(row, [slot], vram, written, image, faces)
        vram[2, 64] = 0x3333
        second = family.binding_cycle(row, [slot], vram, written, image, faces)
        self.assertNotEqual(first['familySha256'], second['familySha256'])

    def test_ordered_gpu_copy_is_forward_word_iteration_not_memmove(self):
        words = np.zeros((512, 1024), dtype=np.uint16)
        provenance = np.full(words.shape, -1, dtype=np.int32)
        words[0, :4] = [1, 2, 3, 4]
        provenance[0, :4] = [0, 1, 2, 3]
        self.assertEqual(native.copy_words(words, provenance, [0, 0, 1, 0, 3, 1]), 0)
        self.assertEqual(words[0, :4].tolist(), [1, 1, 1, 1])
        self.assertEqual(provenance[0, :4].tolist(), [0, 0, 0, 0])

    def test_unknown_copy_source_is_reported_not_filled_with_zero(self):
        words = np.zeros((512, 1024), dtype=np.uint16)
        provenance = np.full(words.shape, -1, dtype=np.int32)
        self.assertEqual(native.copy_words(words, provenance, [960, 256, 64, 0, 1, 1]), 1)
        self.assertEqual(provenance[0, 64], -1)

    def test_new_ledger_writer_does_not_overwrite_existing_checkpoint_or_symlink(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp).resolve()/'ledger.json'
            family.write_new(output, {'holdCleared': False})
            before = output.read_bytes()
            with self.assertRaises(FileExistsError):
                family.write_new(output, {'holdCleared': True})
            link = Path(temp).resolve()/'redirect.json'
            link.symlink_to(output)
            with self.assertRaises(FileExistsError):
                family.write_new(link, {'holdCleared': True})
            self.assertEqual(output.read_bytes(), before)

    def test_published_shards_reproduce_full_frame_hashes_and_tick_schedule(self):
        row, slot, vram, written, image, faces = synthetic()
        binding = family.binding_cycle(row, [slot], vram, written, image, faces)
        result = dict(schema=1, summary={'holdsCleared': 0}, bindings=[binding])
        with tempfile.TemporaryDirectory() as temp:
            primary = Path(temp).resolve()/'ledger.json'
            directory = Path(temp).resolve()/'shards'
            published = family.publish_metadata(result, primary, directory)
            ref = published['stageMetadataShards'][0]
            compressed = (primary.parent/ref['path']).read_bytes()
            expanded = gzip.decompress(compressed)
            self.assertEqual(family.digest(compressed), ref['sha256'])
            self.assertEqual(family.digest(expanded), ref['expandedJsonSha256'])
            full = json.loads(expanded)['bindings'][0]
            self.assertEqual(full['frames'], binding['frames'])
            self.assertEqual(full['schedule'], binding['schedule'])
            self.assertEqual(published['bindings'][0]['frameTableSha256'], family.json_digest(full['frames']))
            with self.assertRaises(ValueError):
                family.publish_metadata(result, primary, directory)

    @unittest.skipUnless(os.environ.get('JAVA_HOME'), 'JAVA_HOME needed for exact-source CPU differential')
    def test_exact_java_methods_match_both_directions_delay_and_shared_scratch_order(self):
        def slot(index, x, values, offset):
            height = values[3]
            return dict(slot=index, values=values,
                        sourceWords=(np.arange(height, dtype=np.uint16)[:, None] + offset))
        cases = [dict(ticks=40, sequences=[
            slot(0, 64, [64, 0, 4, 8, 5, 2, -1], 1),
            slot(1, 128, [128, 0, 4, 4, 2, 16, -1], 100)]),
            dict(ticks=100, sequences=[slot(4, 192, [192, 0, 4, 8, 2, 3, 2, 2, -1], 0x8000)]),
            dict(ticks=4, sequences=[slot(0, 64, [64, 0, 4, 2, 0, 2, -1], 31)]),
            dict(ticks=4, sequences=[slot(0, 64, [64, 0, 4, 2, 2, 16, 2, 2, -1], 0x8000)])]
        result = native.verify(cases, Path(os.environ['JAVA_HOME']))
        self.assertTrue(result['matched'])
        self.assertEqual(result['unknownSourceReads'], 0)


if __name__ == '__main__':
    unittest.main()
