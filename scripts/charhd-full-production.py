#!/usr/bin/env python3
"""Full CharHD source discovery and resumable, bounded restoration queue. AGPLv3."""
import argparse
from collections import Counter, defaultdict, deque
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def save(path, data):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(data, indent=2) + '\n')
    temporary.replace(path)


def discover(files, catalog):
    """Bind only evidenced model/TIM consumers. Unnamed field actors stay unresolved."""
    bindings, unbound = [], []
    for texture in sorted((files / 'characters').glob('*/textures/*')):
        if texture.is_file() and texture.name in ('combat', 'dragoon'):
            character = texture.relative_to(files).parts[1]
            bindings.append({'model': f'characters/{character}/models/{texture.name}/32',
                'texture': texture.relative_to(files).as_posix(), 'category': 'party-battle',
                'entities': [character], 'variant': texture.name, 'ownership': 'source-verified'})
    for model in catalog['models']:
        kinds = set(model['kinds'])
        if model['bucket'] == '5-scenes-props-effects':
            continue  # Environment/prop/effect routes retain their separate workstreams.
        for appearance in model['appearances']:
            seen = set()
            for mapping in appearance['mappings']:
                texture = mapping.get('texture')
                monster = mapping.get('monsterId')
                if monster is not None:
                    texture = f'monsters/{monster}/textures/combat'
                    category = 'boss-story-battle' if model['bucket'] == '3-bosses-story-combatants' else 'enemy-battle'
                elif texture:
                    category = 'party-field-world' if 'party-field' in kinds or 'world-map-party' in kinds else 'npc-field' if 'story-npc' in kinds else 'unresolved-field-actor'
                else:
                    continue
                key = (texture, category)
                if key in seen: continue
                seen.add(key)
                bindings.append({'model': appearance['source'], 'texture': texture,
                    'category': category, 'entities': [mapping['entityName']] if mapping.get('entityName') else model['entityNames'],
                    'variant': mapping.get('label', model['label']),
                    'ownership': 'unresolved' if category == 'unresolved-field-actor' else 'source-verified'})
            if not seen:
                unbound.append({'model': appearance['source'], 'entities': model['entityNames'],
                    'kinds': model['kinds'], 'status': 'texture-consumer-unbound'})
    bound_textures = {row['texture'] for row in bindings}
    for texture in sorted((files / 'monsters').glob('*/textures/combat')):
        relative = texture.relative_to(files).as_posix()
        if relative not in bound_textures:
            unbound.append({'texture': relative, 'status': 'empty-original' if not texture.stat().st_size else 'model-consumer-unbound'})
    return bindings, unbound


def census(files, catalog):
    audit = load('full_charhd_audit', 'audit-model-materials.py')
    bindings, unbound = discover(files, catalog)
    pairs, failures = {}, []
    for binding in bindings:
        try:
            model = audit.bounded_read(files / binding['model'])
            tim = audit.bounded_read(files / binding['texture'])
            mh, th = hashlib.sha256(model).hexdigest(), hashlib.sha256(tim).hexdigest()
            identity = hashlib.sha256(bytes.fromhex(mh) + bytes.fromhex(th)).hexdigest()
            if identity not in pairs:
                row = {'packId': identity, 'modelSha256': mh, 'timSha256': th,
                    'model': binding['model'], 'texture': binding['texture'], 'consumers': [], 'status': 'pending-audit'}
                try:
                    report, _, _ = audit.audit(model, tim)
                    row.update({key: report[key] for key in ('width', 'height', 'usedPalettes', 'texturedFaces', 'hasClutAnimations', 'hasExtraSubfile')})
                    row['status'] = 'hold-animated-or-extra-data' if report['hasClutAnimations'] or report['hasExtraSubfile'] else 'eligible-restoration'
                except ValueError as failure:
                    row.update(status='hold-format-or-mapping', reason=str(failure))
                pairs[identity] = row
            pairs[identity]['consumers'].append(binding)
        except (OSError, ValueError) as failure:
            failures.append(dict(binding, status='hold-source', reason=str(failure)))
    return {'pipeline': 'charhd-full-source-census-1',
        'scope': 'Party battle/field/world, bosses, enemies, NPCs and unresolved field actors; missing consumer routes explicitly held',
        'fullGameDenominator': None, 'records': list(pairs.values()), 'unbound': unbound,
        'sourceFailures': failures, 'bindings': len(bindings),
        'statusCounts': dict(Counter(row['status'] for row in pairs.values())),
        'categoryBindingCounts': dict(Counter(row['category'] for row in bindings))}


def balanced_queue(records):
    queues = defaultdict(deque)
    for row in records:
        if row['status'] != 'eligible-restoration': continue
        categories = {consumer['category'] for consumer in row['consumers']}
        # Every pair is processed once; unresolved ownership is never promoted by generation.
        category = next(c for c in ('npc-field', 'party-field-world', 'boss-story-battle', 'enemy-battle', 'party-battle', 'unresolved-field-actor') if c in categories)
        queues[category].append(row)
    result = []
    while any(queues.values()):
        for category in sorted(queues):
            if queues[category]: result.append(queues[category].popleft())
    return result


def validate_recipe(packing, report):
    scale, _, weights, parameters = packing.neural.MODELS['realesrgan-x4plus-anime']
    expected = {'algorithm': 'neural', 'scale': scale, 'strength': 0.75,
        'weightsSha256': weights, 'paramsSha256': parameters}
    if any(report.get(key) != value for key, value in expected.items()):
        raise ValueError('Prior revision does not match the full restoration batch recipe')


def execute(args, data):
    packing = load('full_charhd_packing', 'pack-model-materials.py')
    root = Path(__file__).resolve().parents[1]
    progress_file = args.output / 'progress.json'
    progress = json.loads(progress_file.read_text()) if progress_file.exists() else {'pipeline': 'charhd-full-restoration-progress-1', 'jobs': {}}
    count = 0
    for row in balanced_queue(data['records']):
        try:
            model, tim = packing.materials.bounded_read(args.files / row['model']), packing.materials.bounded_read(args.files / row['texture'])
        except (OSError, ValueError) as failure:
            progress['jobs'][row['packId']] = {'status': 'hold-production',
                'categories': sorted({c['category'] for c in row['consumers']}),
                'reason': 'Source read failed: ' + str(failure),
                'artAccepted': False, 'nativeAccepted': False}
            save(progress_file, progress)
            continue
        if hashlib.sha256(model).hexdigest() != row['modelSha256'] or hashlib.sha256(tim).hexdigest() != row['timSha256']:
            raise ValueError('Source drift after census; stop and re-audit')
        folder = args.output / 'packs' / row['packId']
        prior = progress['jobs'].get(row['packId'], {})
        if not row.get('usedPalettes'):
            progress['jobs'][row['packId']] = {'status': 'no-textured-faces',
                'categories': sorted({c['category'] for c in row['consumers']}),
                'reason': 'Source model has no textured faces; no color atlas applies',
                'artAccepted': False, 'nativeAccepted': False}
            save(progress_file, progress)
            continue
        if prior.get('status') == 'produced':
            try:
                _, _, report = packing.validate_pack(args.output / prior['folder'], model, tim)
                validate_recipe(packing, report)
                continue
            except (ValueError, OSError):
                pass  # Preserve a damaged prior revision and retry in a new folder.
        existing = root / 'integrations/charhd/production/candidates' / row['modelSha256']
        if not folder.exists() and (existing / 'manifest.json').exists():
            try:
                _, _, report = packing.validate_pack(existing, model, tim)
                validate_recipe(packing, report)
                folder.mkdir(parents=True)
                for name in ('manifest.json', 'atlas-engine-stp.png', 'surfaces.json'):
                    if (existing / name).is_file(): shutil.copy2(existing / name, folder / name)
                progress['jobs'][row['packId']] = {'status': 'produced', 'folder': folder.relative_to(args.output).as_posix(),
                    'categories': sorted({c['category'] for c in row['consumers']}), 'origin': 'existing-reviewed-format-baseline',
                    'artAccepted': False, 'nativeAccepted': False}
                save(progress_file, progress)
                continue
            except (ValueError, OSError):
                pass  # Same model can have another TIM identity; do not reuse it.
        # A crash can leave an incomplete folder. Preserve it for diagnosis and use
        # a fresh numbered attempt; never overwrite an accepted or partial revision.
        attempt = 1
        while folder.exists():
            attempt += 1
            folder = args.output / 'packs' / f"{row['packId']}-attempt-{attempt}"
        job = {'status': 'running', 'categories': sorted({c['category'] for c in row['consumers']}),
            'folder': folder.relative_to(args.output).as_posix(), 'artAccepted': False, 'nativeAccepted': False}
        progress['jobs'][row['packId']] = job
        save(progress_file, progress)
        command = [sys.executable, str(root / 'scripts/pack-model-materials.py'),
            '--model', str(args.files / row['model']), '--tim', str(args.files / row['texture']),
            '--output', str(folder), '--algorithm', 'neural', '--scale', '4', '--strength', '0.75',
            '--engine', str(args.engine), '--models', str(args.models), '--neural-model', 'realesrgan-x4plus-anime']
        folder.parent.mkdir(parents=True, exist_ok=True)
        try:
            with (args.output / f"{row['packId']}.log").open('w') as log:
                subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=360)
            packing.validate_pack(folder, model, tim)
            job['status'] = 'produced'
        except (subprocess.SubprocessError, ValueError, OSError) as failure:
            job.update(status='hold-production', reason=str(failure))
        save(progress_file, progress)
        count += 1
        print(json.dumps({'completedThisRun': count, 'packId': row['packId'], **job}), flush=True)
        if args.max_jobs and count >= args.max_jobs: break


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--engine', type=Path)
    parser.add_argument('--models', type=Path)
    parser.add_argument('--max-jobs', type=int, default=0)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.output.resolve().is_relative_to(root) or args.output.resolve().is_relative_to(args.files.resolve()):
        parser.error('Private inputs and diagnostic controls must stay outside the repository/extraction')
    if args.max_jobs < 0 or args.execute and (not args.engine or not args.models):
        parser.error('Execution requires pinned engine/models and nonnegative max-jobs')
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / 'census.json'
    if path.exists(): data = json.loads(path.read_text())
    else:
        data = census(args.files, json.loads(args.catalog.read_text()))
        save(path, data)
    print(json.dumps({key: value for key, value in data.items() if key not in ('records', 'unbound', 'sourceFailures')}), flush=True)
    if args.execute: execute(args, data)


if __name__ == '__main__':
    main()
