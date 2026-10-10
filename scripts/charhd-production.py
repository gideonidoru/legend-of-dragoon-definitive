#!/usr/bin/env python3
"""Source-bound CharHD census and bounded palette-aware production; AGPLv3."""
import argparse
import hashlib
import importlib.util
import json
import subprocess
import sys
from pathlib import Path


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def census(files):
    audit = module('charhd_audit', 'audit-model-materials.py')
    records = []
    for texture in sorted((files / 'characters').glob('*/textures/*')):
        if not texture.is_file() or texture.name not in ('combat', 'dragoon'):
            continue
        relative = texture.relative_to(files)
        character = relative.parts[1]
        model = files / 'characters' / character / 'models' / texture.name / '32'
        entry = {'character': character, 'form': texture.name, 'texture': relative.as_posix(),
                 'model': model.relative_to(files).as_posix(), 'status': 'pending-audit'}
        try:
            raw_model, raw_tim = audit.bounded_read(model), audit.bounded_read(texture)
            report, _, _ = audit.audit(raw_model, raw_tim)
            entry.update({key: report[key] for key in ('modelSha256', 'timSha256', 'texturedFaces',
                'usedPalettes', 'conflictingColourTexels', 'hasClutAnimations', 'hasExtraSubfile')})
            entry['parts'] = len(report['parts'])
            entry['status'] = 'unsupported-animation-or-extra-data' if report['hasClutAnimations'] or report['hasExtraSubfile'] else 'eligible-pending-art-and-runtime'
        except (OSError, ValueError) as error:
            entry.update(status='unsupported-pending-investigation', reason=str(error))
        records.append(entry)
    return {'pipeline': 'charhd-source-census-1', 'scope': 'Named playable battle forms only; field/NPC/enemy census pending',
            'records': records, 'assetCount': len(records), 'artAccepted': 0, 'nativeAccepted': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--characters', nargs='+', default=['dart', 'haschel', 'meru'])
    parser.add_argument('--forms', nargs='+', choices=['combat', 'dragoon'], default=['combat'])
    parser.add_argument('--recipes', nargs='+', choices=['nearest4', 'restored2', 'restored4'], default=['nearest4', 'restored2', 'restored4'])
    parser.add_argument('--engine', type=Path)
    parser.add_argument('--models', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output.is_relative_to(root) or output.is_relative_to(args.files.resolve()):
        parser.error('Stage original-derived controls/inputs outside the repository and extraction')
    if args.execute and (not args.engine or not args.models):
        parser.error('Execution needs explicit pinned engine/models')
    data = census(args.files.resolve())
    output.mkdir(parents=True, exist_ok=False)
    (output / 'census.json').write_text(json.dumps(data, indent=2) + '\n')
    if args.execute:
        for entry in data['records']:
            if entry['character'] not in args.characters or entry['form'] not in args.forms or not entry['status'].startswith('eligible'):
                continue
            for name, scale, algorithm, model in [('nearest4', 4, 'nearest', None),
                    ('restored2', 2, 'neural', 'realesr-animevideov3'),
                    ('restored4', 4, 'neural', 'realesrgan-x4plus-anime')]:
                if name not in args.recipes: continue
                destination = output / entry['character'] / entry['form'] / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                for source, hash_key in ((entry['model'], 'modelSha256'), (entry['texture'], 'timSha256')):
                    if hashlib.sha256((args.files / source).read_bytes()).hexdigest() != entry[hash_key]:
                        raise ValueError('Source changed after census; stop production and audit again')
                command = [sys.executable, str(root / 'scripts/pack-model-materials.py'),
                    '--model', str(args.files / entry['model']), '--tim', str(args.files / entry['texture']),
                    '--output', str(destination), '--algorithm', algorithm, '--scale', str(scale), '--strength', '0.75']
                if model:
                    command += ['--engine', str(args.engine), '--models', str(args.models), '--neural-model', model]
                subprocess.run(command, check=True, timeout=360)
    print(json.dumps({'assets': data['assetCount'], 'staging': str(output), 'productionRequested': args.execute}))


if __name__ == '__main__':
    main()
