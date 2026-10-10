#!/usr/bin/env python3
"""Publish validated custom CharHD candidates, never original controls or diagnostics. AGPLv3."""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil


def packing_module():
    spec = importlib.util.spec_from_file_location('candidate_packing', Path(__file__).with_name('pack-model-materials.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


MANIFEST_FIELDS = ('pipeline', 'modelSha256', 'timSha256', 'sourceSize', 'scale',
    'paddingSourceTexels', 'atlasSize', 'materials', 'algorithm', 'strength',
    'preservedPalettes', 'weightsSha256', 'paramsSha256', 'engineSha256',
    'atlasEngineSha256', 'scriptSha256', 'dependencyScriptHashes')


def public_manifest(report):
    if report['algorithm'] != 'neural':
        raise ValueError('Full batch publisher requires neural reconstruction, never unmodified controls')
    result = {key: report[key] for key in MANIFEST_FIELDS if key in report}
    result['scope'] = 'Custom restoration candidate; AA art, native rendering and Steam Deck acceptance pending'
    return result


def safe_folder(root, relative):
    candidate = root / relative
    if not candidate.resolve().is_relative_to(root.resolve()):
        raise ValueError('Candidate folder escapes private staging')
    for path in (candidate, *candidate.parents):
        if path == root.parent: break
        if path.is_symlink(): raise ValueError('Candidate paths cannot contain symlinks')
    return candidate


def publish(files, staging, output):
    packing = packing_module()
    census = json.loads((staging / 'census.json').read_text())
    progress = json.loads((staging / 'progress.json').read_text())
    eligible = [row for row in census['records'] if row['status'] == 'eligible-restoration']
    records = []
    if output.is_symlink(): raise ValueError('Publication root cannot be a symlink')
    output.mkdir(parents=True, exist_ok=True)
    coverage = safe_folder(output, 'full-candidate-coverage.json')
    safe_folder(output, 'full-candidates')
    for row in eligible:
        job = progress['jobs'].get(row['packId'], {})
        record = {key: row[key] for key in ('packId', 'modelSha256', 'timSha256', 'model', 'texture')}
        record.update(categories=sorted({c['category'] for c in row['consumers']}),
            ownership=sorted({c['ownership'] for c in row['consumers']}),
            consumerCount=len(row['consumers']), artAccepted=False, nativeAccepted=False)
        if not row.get('usedPalettes'):
            record.update(status='no-textured-faces', reason='Source model does not use a color texture atlas')
        elif job.get('status') != 'produced':
            record['status'] = 'production-hold' if job.get('status') == 'hold-production' else 'pending-production'
        else:
            model = packing.materials.bounded_read(files / row['model'])
            tim = packing.materials.bounded_read(files / row['texture'])
            model_hash, tim_hash = hashlib.sha256(model).hexdigest(), hashlib.sha256(tim).hexdigest()
            identity = hashlib.sha256(bytes.fromhex(model_hash) + bytes.fromhex(tim_hash)).hexdigest()
            if (model_hash, tim_hash, identity) != (row['modelSha256'], row['timSha256'], row['packId']):
                raise ValueError('Publication source identity differs from the census')
            source = safe_folder(staging, job['folder'])
            _, _, report = packing.validate_pack(source, model, tim)
            manifest = public_manifest(report)
            destination = safe_folder(output, 'full-candidates/' + row['packId'])
            destination.mkdir(parents=True, exist_ok=True)
            # An existing immutable revision must agree. Never overwrite different art.
            png = safe_folder(output, destination.relative_to(output) / 'atlas-engine-stp.png')
            manifest_path = safe_folder(output, destination.relative_to(output) / 'manifest.json')
            if png.exists() and (png.is_symlink() or hashlib.sha256(png.read_bytes()).hexdigest() != report['atlasEngineSha256']):
                raise ValueError('Public candidate revision conflicts with validated production')
            if not png.exists(): shutil.copyfile(source / png.name, png)
            if hashlib.sha256(png.read_bytes()).hexdigest() != report['atlasEngineSha256']:
                raise ValueError('Published atlas differs from the validated reconstruction')
            manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
            # Source preflight already validated every layout/mask field retained
            # by the whitelist. Byte identity proves the copied atlas is identical.
            record.update(status='restoration-candidate', atlasEngineSha256=report['atlasEngineSha256'],
                atlasSize=report['atlasSize'], path=destination.relative_to(output).as_posix())
        records.append(record)
    result = {'pipeline': 'charhd-full-candidate-coverage-1', 'originalQueueCount': len(eligible),
        'texturedCandidateTargetCount': sum(bool(row.get('usedPalettes')) for row in eligible),
        'fullGameDenominator': None,
        'sourceCensusSha256': hashlib.sha256((staging/'census.json').read_bytes()).hexdigest(),
        'statusCounts': dict(Counter(row['status'] for row in records)),
        'artAccepted': 0, 'nativeAccepted': 0, 'deckAccepted': 0,
        'scope': 'Baseline reconstruction queue only; publication does not select runtime assets or establish final art quality',
        'records': records}
    coverage.write_text(json.dumps(result, indent=2) + '\n')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--staging', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.files.resolve()) or args.output.resolve().is_relative_to(args.staging.resolve()):
        parser.error('Custom publication must be separate from originals and private staging')
    report = publish(args.files, args.staging, args.output)
    print(json.dumps({k: v for k, v in report.items() if k != 'records'}))


if __name__ == '__main__': main()
