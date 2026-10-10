#!/usr/bin/env python3
"""Synthetic full-scope routing, deduplication and queue fixtures. AGPLv3."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import json
import tempfile
import unittest


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


full = load('full', 'charhd-full-production.py')
fixtures = load('fixtures', 'test-model-material-audit.py')
packing_fixtures = load('packing_fixtures', 'test-material-packing.py')
publisher = load('publisher', 'publish-charhd-candidates.py')


def model(bucket, kinds, source, mappings):
    return {'bucket': bucket, 'kinds': kinds, 'label': 'fixture', 'entityNames': [],
        'appearances': [{'source': source, 'mappings': mappings}]}


class FullProductionTest(unittest.TestCase):
    def test_publication_validates_source_and_copies_only_custom_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); files = root/'files'; files.mkdir()
            staging = root/'private'; staging.mkdir(); output = root/'public'
            raw_model, raw_tim = fixtures.model(), fixtures.tim()
            (files/'model').write_bytes(raw_model); (files/'tim').write_bytes(raw_tim)
            census = full.census(files, {'models': [model('4-enemies', ['enemy'], 'model', [{'texture': 'tim'}])]})
            row = census['records'][0]
            folder = staging/'packs'/row['packId']; folder.mkdir(parents=True)
            manifest = packing_fixtures.write_control(folder, raw_model, raw_tim)
            _, _, weights, parameters = packing_fixtures.packing.neural.MODELS['realesr-animevideov3']
            manifest.update(algorithm='neural', strength=0.75, weightsSha256=weights, paramsSha256=parameters,
                command=['/private/runtime'], steps=[{'input': '/private/source.png'}])
            (folder/'manifest.json').write_text(json.dumps(manifest))
            (folder/'palette-0-input.png').write_text('private input')
            (staging/'census.json').write_text(json.dumps(census))
            (staging/'progress.json').write_text(json.dumps({'jobs': {row['packId']: {
                'status': 'produced', 'folder': folder.relative_to(staging).as_posix()}}}))
            result = publisher.publish(files, staging, output)
            self.assertEqual(result['statusCounts'], {'restoration-candidate': 1})
            public = output/'full-candidates'/row['packId']
            self.assertEqual({p.name for p in public.iterdir()}, {'manifest.json', 'atlas-engine-stp.png'})
            self.assertNotIn('/private', (public/'manifest.json').read_text())
            row['timSha256'] = '0'*64
            (staging/'census.json').write_text(json.dumps(census))
            with self.assertRaisesRegex(ValueError, 'source identity'): publisher.publish(files, staging, output)
            row['timSha256'] = manifest['timSha256']
            (staging/'census.json').write_text(json.dumps(census))
            (public/'atlas-engine-stp.png').write_bytes(b'conflicting revision')
            with self.assertRaisesRegex(ValueError, 'conflicts'): publisher.publish(files, staging, output)

    def test_reuse_requires_the_exact_batch_recipe(self):
        packing = full.load('recipe_packing', 'pack-model-materials.py')
        scale, _, weights, parameters = packing.neural.MODELS['realesrgan-x4plus-anime']
        report = dict(algorithm='neural', scale=scale, strength=0.75,
            weightsSha256=weights, paramsSha256=parameters)
        full.validate_recipe(packing, report)
        for key, wrong in (('algorithm', 'nearest'), ('scale', 2), ('strength', 0.5), ('weightsSha256', 'bad'), ('paramsSha256', 'bad')):
            with self.assertRaises(ValueError): full.validate_recipe(packing, dict(report, **{key: wrong}))

    def test_missing_source_is_held_without_stopping_other_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); output = root/'output'; output.mkdir()
            files = root/'files'; files.mkdir()
            (files/'model').write_bytes(fixtures.model()); (files/'tim').write_bytes(fixtures.tim())
            report = full.census(files, {'models': [model('4-enemies', ['enemy'], 'model', [{'texture': 'tim'}])]})
            valid = report['records'][0]; valid['usedPalettes'] = []
            missing = dict(valid, packId='f'*64, model='missing-model')
            report['records'].insert(0, missing)
            args = SimpleNamespace(output=output, files=files, engine=root/'engine', models=root/'models', max_jobs=0)
            full.execute(args, report)
            jobs = json.loads((output/'progress.json').read_text())['jobs']
            self.assertEqual(jobs[missing['packId']]['status'], 'hold-production')
            self.assertEqual(jobs[valid['packId']]['status'], 'no-textured-faces')

    def test_shared_geometry_uses_each_consumer_entity_name(self):
        entry = model('3-bosses-story-combatants', ['boss'], 'shared',
            [{'monsterId': 1, 'entityName': 'First boss'}, {'monsterId': 2, 'entityName': 'Second boss'}])
        entry['entityNames'] = ['First boss', 'Second boss']
        with tempfile.TemporaryDirectory() as directory:
            bindings, _ = full.discover(Path(directory), {'models': [entry]})
        self.assertEqual([b['entities'] for b in bindings], [['First boss'], ['Second boss']])

    def test_publication_removes_private_paths_commands_and_controls(self):
        report = {'algorithm': 'neural', 'modelSha256': 'a'*64, 'timSha256': 'b'*64,
            'command': ['private-command'], 'environment': 'private-machine',
            'atlasPreviewSha256': 'control-hash', 'steps': [{'input': '/private/source'}]}
        sanitized = publisher.public_manifest(report)
        self.assertFalse({'command', 'environment', 'atlasPreviewSha256', 'steps'} & sanitized.keys())
        self.assertEqual(sanitized['modelSha256'], 'a'*64)
        with self.assertRaises(ValueError): publisher.public_manifest({'algorithm': 'nearest'})

    def test_publication_cannot_follow_a_folder_outside_staging(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'staging'; root.mkdir()
            (root / 'escape').symlink_to(root.parent, target_is_directory=True)
            for relative in ('../other', 'escape'):
                with self.assertRaises(ValueError): publisher.safe_folder(root, relative)

    def test_publication_rejects_symlinked_ancestors_and_manifest_before_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); output = root/'public'; output.mkdir()
            private = root/'private'; private.mkdir()
            protected = private/'protected'; protected.write_text('unchanged')
            (output/'full-candidates').symlink_to(private, target_is_directory=True)
            with self.assertRaises(ValueError): publisher.safe_folder(output, 'full-candidates/pack')
            (output/'full-candidates').unlink(); (output/'full-candidates').mkdir()
            pack = output/'full-candidates/pack'; pack.mkdir()
            for name in ('manifest.json', 'atlas-engine-stp.png'):
                (pack/name).symlink_to(protected)
                with self.assertRaises(ValueError): publisher.safe_folder(output, 'full-candidates/pack/'+name)
            (output/'full-candidate-coverage.json').symlink_to(protected)
            with self.assertRaises(ValueError): publisher.safe_folder(output, 'full-candidate-coverage.json')
            self.assertEqual(protected.read_text(), 'unchanged')

    def test_untextured_models_are_recorded_without_inference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); output = root / 'output'; output.mkdir()
            files = root / 'files'; files.mkdir()
            (files / 'model').write_bytes(fixtures.model()); (files / 'tim').write_bytes(fixtures.tim())
            report = full.census(files, {'models': [model('4-enemies', ['enemy'], 'model', [{'texture': 'tim'}])]})
            report['records'][0]['usedPalettes'] = []
            args = SimpleNamespace(output=output, files=files, engine=root/'engine', models=root/'models', max_jobs=0)
            with patch.object(full.subprocess, 'run') as inference:
                full.execute(args, report)
            inference.assert_not_called()
            jobs = json.loads((output / 'progress.json').read_text())['jobs']
            self.assertEqual(next(iter(jobs.values()))['status'], 'no-textured-faces')

    def test_routes_enemy_npc_unresolved_and_excludes_environment(self):
        catalog = {'models': [model('4-enemies', ['enemy'], 'enemy', [{'monsterId': 5}]),
            model('2-story-npcs', ['story-npc'], 'npc', [{'texture': 'npc-tim'}]),
            model('6-unresolved-actor', ['field-unresolved'], 'unknown', [{'texture': 'unknown-tim'}]),
            model('5-scenes-props-effects', ['prop'], 'prop', [{'texture': 'prop-tim'}]),
            model('3-bosses-story-combatants', ['boss'], 'boss', [{}])]}
        with tempfile.TemporaryDirectory() as directory:
            bindings, unbound = full.discover(Path(directory), catalog)
        self.assertEqual({b['category'] for b in bindings}, {'enemy-battle', 'npc-field', 'unresolved-field-actor'})
        self.assertEqual(next(b for b in bindings if b['model'] == 'enemy')['texture'], 'monsters/5/textures/combat')
        self.assertEqual(next(b for b in bindings if b['model'] == 'unknown')['ownership'], 'unresolved')
        self.assertEqual(unbound[0]['status'], 'texture-consumer-unbound')

    def test_deduplicates_bytes_without_losing_consumer_ownership(self):
        with tempfile.TemporaryDirectory() as directory:
            files = Path(directory)
            for name in ('one', 'two'): (files / name).write_bytes(fixtures.model())
            (files / 'tim').write_bytes(fixtures.tim())
            catalog = {'models': [model('2-story-npcs', ['story-npc'], 'one', [{'texture': 'tim'}]),
                model('6-unresolved-actor', ['field-unresolved'], 'two', [{'texture': 'tim'}])]}
            report = full.census(files, catalog)
            self.assertEqual(len(report['records']), 1)
            self.assertEqual(len(report['records'][0]['consumers']), 2)
            self.assertIsNone(report['fullGameDenominator'])

    def test_balances_categories_and_keeps_format_holds_out_of_generation(self):
        records = [{'status': 'eligible-restoration', 'consumers': [{'category': c}], 'id': i}
            for c in ('enemy-battle', 'npc-field', 'party-field-world') for i in range(3)]
        records.append({'status': 'hold-format-or-mapping', 'consumers': [{'category': 'boss-story-battle'}]})
        queue = full.balanced_queue(records)
        self.assertEqual(len(queue), 9)
        self.assertEqual(len({r['consumers'][0]['category'] for r in queue[:3]}), 3)


if __name__ == '__main__': unittest.main()
