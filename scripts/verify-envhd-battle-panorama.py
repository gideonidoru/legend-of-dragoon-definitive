#!/usr/bin/env python3
"""Replay unselected panorama repairs against every private MCQ source alias.

This audit never selects runtime artwork. Pixel replay establishes crop/coverage
provenance, not internal-feature fidelity, repeated-edge or native scene acceptance.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import tempfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION = 'integrations/envhd/production'
METHOD = 'python-reviewed-painted-battle-panorama-source-canvas-1'
TOOL_HASHES = {
    'repair-envhd-painted-battle-panorama.py': 'a717b9cc9f7841a671496a2d81de130aef324a6278b1cf1765eea43edc73902b',
    'bounded-envhd-battle-panorama.py': 'e5e40735b3c41e87ef763595b6a941697cd85bbc042162ae326b1476154c55d0',
    'batch-envhd-terrain.py': '2e182287e873db6aa46cbede3bf18339c894608dc7a8610abf78fe8c8ee64751',
    'write-envhd-painted-candidate.py': 'fb11cff951cba94590dd6071934fa63f1ce9da475fd70f4921298efa33d2db57',
}
BINDING_FIELDS = ('sourceMcqSha256', 'decodedRgbaSha256', 'generationMasterSourceSha256',
                  'sourceFiles', 'stages', 'sourceSize', 'targetSize')


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


recipe = module('panorama_replay_recipe', 'repair-envhd-painted-battle-panorama.py')
decoder = module('panorama_replay_mcq', 'export-envhd-skies.py')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def resource(root, name, prefix):
    if not isinstance(name, str) or '\\' in name:
        raise ValueError('Invalid relative artwork path')
    parts = PurePosixPath(name)
    if parts.is_absolute() or any(p in ('.', '..') for p in parts.parts) or not name.startswith(prefix + '/'):
        raise ValueError('Artwork path escapes its declared category')
    result = root / name
    cursor = root
    for part in parts.parts:
        cursor = cursor / part
        if cursor.is_symlink():
            raise ValueError('Linked artwork path is not allowed')
    result.resolve().relative_to(root.resolve())
    return result


def read(path, limit=32 * 1024 * 1024):
    with path.open('rb') as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ValueError('Panorama audit payload exceeds its bound')
    return data


def binding(entry):
    return {k: entry[k] for k in BINDING_FIELDS}


def original_for_group(files, entries):
    original = None
    for entry in entries:
        for name in entry['sourceFiles']:
            path = resource(files, name, 'SECT/DRGN0.BIN')
            data = read(path, 1024 * 1024)
            if digest(data) != entry['sourceMcqSha256']:
                raise ValueError('Private MCQ source alias changed')
            image = decoder.decode(data)
            if recipe.terrain.fingerprint(image) != entry['decodedRgbaSha256'] or list(image.size) != entry['sourceSize']:
                raise ValueError('Decoded MCQ source alias changed')
            if entry['sourceMcqSha256'] == entry['generationMasterSourceSha256']:
                original = image
    if original is None:
        raise ValueError('Missing canonical panorama source')
    return original


def validate_review(record, existing=False):
    if (record.get('reviewStatus') != 'layout-reviewed-wrap-pending'
            or record.get('runtimeSelected') is not existing
            or any(record.get(k) != 'pending' for k in ('independentReview', 'wrapAcceptance', 'nativeAcceptance', 'finalQualityAcceptance'))):
        raise ValueError('Authoring cannot claim runtime or independent/native acceptance')
    notes = record.get('review')
    if not isinstance(notes, dict) or set(notes) != {'intent', 'style', 'layout', 'wrap'} or any(not isinstance(v, str) or not v.strip() for v in notes.values()):
        raise ValueError('Requires individual intent/style/layout/wrap review notes')


def replay_record(record, original, root):
    validate_review(record)
    if not isinstance(record.get('prompt'), str) or not record['prompt'].strip() or record.get('references') != []:
        raise ValueError('Requires exact public prompt without private reference paths')
    prefix = PRODUCTION + '/battle-panorama-candidates/' + record['sourceMcqSha256']
    raw_path = resource(root, record['generatedPath'], prefix)
    output_path = resource(root, record['outputPath'], prefix)
    raw_data, output_data = read(raw_path), read(output_path)
    receipt = record['recipe']
    if receipt.get('method') != METHOD or digest(raw_data) != receipt.get('generatedSha256') or digest(output_data) != receipt.get('outputSha256'):
        raise ValueError('Panorama candidate bytes or recipe identity changed')
    header = recipe.bounds.validate_png_header(output_data, 'output', original.size)
    with Image.open(output_path) as output:
        recipe.bounds.validate_opened_png(output, header, 'output', original.size)
    with tempfile.TemporaryDirectory() as directory:
        private = Path(directory).resolve()
        source_path = private / 'source.png'
        original.save(source_path)
        replay, _ = recipe.execute(source_path, raw_path, private / 'replayed.png', record['decodedRgbaSha256'])
        if replay != receipt or read(private / 'replayed.png') != output_data:
            raise ValueError('Panorama recipe replay differs from candidate pixels or receipt')
    for rejected in record.get('rejectedVersions', []):
        path = resource(root, rejected['generatedPath'], prefix)
        if digest(read(path)) != rejected['generatedSha256'] or not rejected.get('reason') or not rejected.get('prompt'):
            raise ValueError('Rejected panorama history changed')
    return {'sourceMcqSha256': record['sourceMcqSha256'], 'decodedRgbaSha256': record['decodedRgbaSha256'],
            'outputSha256': receipt['outputSha256'], 'sourceSize': list(original.size),
            'runtimeSelected': False, 'nativeAcceptance': 'pending'}


def preflight(files, manifest_path, root=ROOT):
    root, files = root.resolve(), files.resolve()
    manifest_data = read(manifest_path)
    manifest = json.loads(manifest_data)
    if type(manifest.get('schema')) is not int or manifest.get('schema') != 1 or manifest.get('method') != METHOD or manifest.get('toolHashes') != TOOL_HASHES:
        raise ValueError('Unknown or changed panorama recipe pins')
    for name, sha in TOOL_HASHES.items():
        if digest(read(root / 'scripts' / name)) != sha:
            raise ValueError('Panorama recipe implementation changed')
    ledger_path = root / PRODUCTION / 'battle-skies.json'
    ledger_data = read(ledger_path)
    if digest(ledger_data) != manifest.get('sourceLedgerSha256'):
        raise ValueError('Panorama source/selection ledger changed')
    ledger = json.loads(ledger_data)
    groups = {}
    for entry in ledger['assets']:
        if entry['status'] == 'no-generation-required':
            continue
        groups.setdefault(entry['decodedRgbaSha256'], []).append(entry)
    expected_counts = dict(sourceVariants=len(ledger['assets']),
        decodedSources=len({e['decodedRgbaSha256'] for e in ledger['assets']}),
        nonuniformDecodedSources=len(groups),
        uniformNativeArtwork=len({e['decodedRgbaSha256'] for e in ledger['assets'] if e['status'] == 'no-generation-required'}))
    if manifest.get('sourceCounts') != expected_counts or any(type(v) is not int for v in manifest['sourceCounts'].values()):
        raise ValueError('Panorama source census changed')
    candidates, reused = manifest.get('candidates'), manifest.get('reusedEarlierPaintings')
    if (not isinstance(candidates, list) or not isinstance(reused, list)
            or type(manifest.get('authorReviewedCandidates')) is not int
            or manifest.get('authorReviewedCandidates') != len(candidates)):
        raise ValueError('Panorama authoring count differs from its records')
    verified, seen = [], set()
    snapshots = {ledger_path: ledger_data, manifest_path: manifest_data}
    for record in candidates + reused:
        key = record['decodedRgbaSha256']
        if key in seen or key not in groups:
            raise ValueError('Duplicate or unknown panorama source')
        seen.add(key)
        group = groups[key]
        expected = sorted([binding(e) for e in group], key=lambda b: b['sourceMcqSha256'])
        if record.get('sourceBindings') != expected or record['sourceMcqSha256'] != group[0]['generationMasterSourceSha256']:
            raise ValueError('Panorama source header/placement aliases changed')
        original = original_for_group(files, group)
        recipe.bounds.validate_dimensions(original.size, 'source')
        if record in candidates:
            verified.append(replay_record(record, original, root))
        else:
            validate_review(record, existing=True)
            path = resource(root, record['existingOutputPath'], 'integrations/envhd/runtime-assets/envhd/sky-images')
            if digest(read(path)) != record['existingOutputSha256'] or record['existingOutputPath'] != group[0]['runtimeOutput']:
                raise ValueError('Earlier panorama painting changed')
    # The runtime snapshot protects all selected images and every per-header manifest.
    current_paths = sorted(str(p.relative_to(root)) for category in ('skies', 'sky-images')
                           for p in (root / 'integrations/envhd/runtime-assets/envhd' / category).rglob('*') if p.is_file())
    runtime = manifest.get('unchangedRuntimeResources')
    if not isinstance(runtime, dict) or sorted(runtime) != current_paths:
        raise ValueError('Selected panorama resource inventory changed')
    for name, sha in runtime.items():
        path = resource(root, name, 'integrations/envhd/runtime-assets/envhd')
        data = read(path)
        if digest(data) != sha:
            raise ValueError('Selected panorama bytes changed')
        snapshots[path] = data
    for path, data in snapshots.items():
        if read(path) != data:
            raise ValueError('Panorama controls changed during audit')
    return {'schema': 1, 'manifestSha256': digest(manifest_data), 'sourceLedgerSha256': digest(ledger_data),
            'authorReviewedCandidates': len(verified), 'reusedEarlierPaintings': len(reused),
            'nonuniformDecodedSources': len(groups), 'authoringCoverageComplete': seen == set(groups),
            'runtimeSelectedNewCandidates': 0, 'independentReviewedNewCandidates': 0,
            'nativeSceneAccepted': 0, 'finalBeautyAccepted': 0, 'verified': verified}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    print(json.dumps(preflight(args.files, args.manifest, args.root), indent=2))
