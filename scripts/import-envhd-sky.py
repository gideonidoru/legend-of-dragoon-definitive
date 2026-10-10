#!/usr/bin/env python3
"""Record reviewed custom sky candidates and select shared runtime images.

Copies the generated PNG without modifying pixels. Private MCQ sources are used
only for hash/layout checks and are never copied into the project.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import re
import struct
import tempfile
from pathlib import Path
from PIL import Image
import numpy as np

spec = importlib.util.spec_from_file_location('sky_export', Path(__file__).with_name('export-envhd-skies.py'))
decoder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(decoder)

VERDICTS = {'intent-revision-needed', 'layout-reviewed-wrap-pending', 'repeat-boundary-revision-needed', 'visual-reviewed-native-pending', 'native-accepted'}
SELECTED = {'visual-reviewed-native-pending', 'native-accepted'}
BATCH_TOOL_HASHES = {
    'weightsSha256': '713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf',
    'parametersSha256': '35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86',
    'engineSha256': 'c1c35d92079085de96b9d547fd7e4464bc8a2e9ccf28d7b8c712d72ade91b7cc',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_bytes(path, limit=32 * 1024 * 1024):
    with path.open('rb') as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        raise ValueError(f'Input exceeds bounds: {path}')
    return data


def read_json(path, limit=1024 * 1024):
    return json.loads(read_bytes(path, limit))


def check_version(source, target):
    if target.exists() and read_bytes(target) != source:
        raise FileExistsError(f'Existing version differs; create a new revision: {target}')


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix='.sky-import-', delete=False) as stream:
        temporary = Path(stream.name)
        try:
            stream.write(data)
            stream.close()
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)


def publish(writes, removals):
    """Roll back a failed local import; versioned candidate bytes stay unchanged."""
    previous = {path: read_bytes(path) if path.exists() else None for path in writes.keys() | removals}
    changed = []
    try:
        for path, data in writes.items():
            atomic_write(path, data)
            changed.append(path)
        for path in removals:
            path.unlink()
            changed.append(path)
    except Exception:
        for path in reversed(changed):
            if previous[path] is None:
                path.unlink(missing_ok=True)
            else:
                atomic_write(path, previous[path])
        raise


def json_bytes(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def resource_path(runtime, key, metadata):
    filename = metadata.get('imageFile', 'image-v1.png')
    decoded = metadata.get('decodedRgbaSha256')
    if not re.fullmatch('[0-9a-f]{64}', key) or metadata['sourceMcqSha256'] != key:
        raise ValueError('Runtime manifest differs from its source directory')
    if not re.fullmatch(r'image-v[1-9][0-9]{0,3}\.png', filename) or decoded is not None and not re.fullmatch('[0-9a-f]{64}', decoded):
        raise ValueError('Unsafe runtime image reference')
    return runtime / 'envhd' / ('sky-images' if decoded is not None else 'skies') / (decoded or key) / filename


def batch_provenance(production, entry, png, version, value):
    methods = {'python-border-repair': 'repair-reviewed-layout',
               'real-esrgan-x4plus-periodic-python-border-repair': 'upscale-original'}
    method = value.get('method')
    if method not in methods or value.get('action') != methods[method]:
        raise ValueError('Unsupported batch production method')
    expected = {'masterSourceSha256': entry['sourceMcqSha256'], 'decodedRgbaSha256': entry['decodedRgbaSha256'],
                'outputSha256': digest(png), 'sourceSize': entry['sourceSize'], 'targetSize': entry['targetSize'],
                'repairBorderPixels': min(32, entry['targetSize'][0] // 8),
                'sourceVisibility': 'exact-nearest-4x-discard-and-visible-black',
                'reviewStatus': 'pending-source-intent-style-layout-wrap-review'}
    if any(value.get(key) != item for key, item in expected.items()) or entry['targetSize'] != [size * 4 for size in entry['sourceSize']]:
        raise ValueError('Batch provenance differs from the reviewed source/output')
    if methods[method] == 'upscale-original':
        if value.get('inputSha256') != entry['decodedRgbaSha256'] or any(value.get(key) != item for key, item in BATCH_TOOL_HASHES.items()):
            raise ValueError('Batch source or pinned tool identity differs')
    else:
        name = value.get('inputImageFile', '')
        match = re.fullmatch(r'image-v([1-9][0-9]{0,3})\.png', name)
        if match is None or int(match[1]) >= version or any(value.get(key) is not None for key in BATCH_TOOL_HASHES):
            raise ValueError('Invalid border-repair predecessor or tool provenance')
        path = production / 'candidates' / entry['sourceMcqSha256'] / name
        metadata = read_json(path.with_name(f'manifest-v{int(match[1])}.json'))
        if digest(read_bytes(path)) != value.get('inputSha256') or metadata['outputSha256'] != value.get('inputSha256') or metadata['sourceMcqSha256'] != entry['sourceMcqSha256'] or metadata.get('decodedRgbaSha256', entry['decodedRgbaSha256']) != entry['decodedRgbaSha256'] or metadata['reviewStatus'] not in {'repeat-boundary-revision-needed', 'layout-reviewed-wrap-pending'}:
            raise ValueError('Border-repair predecessor differs from reviewed artwork')
    keys = set(expected) | {'method', 'action', 'inputSha256', 'inputImageFile'} | set(BATCH_TOOL_HASHES)
    return {key: value[key] for key in keys if key in value}


def record(root, files, candidate, source_hash, version, prompt, verdict, notes, production_record=None):
    if verdict not in VERDICTS or not 1 <= version <= 9999 or not re.fullmatch('[0-9a-f]{64}', source_hash):
        raise ValueError('Invalid review verdict, version or source hash')
    if set(notes) != {'intent', 'style', 'layout', 'wrap'} or any(not value.strip() for value in notes.values()):
        raise ValueError('Requires explicit intent, style, layout and repeat-boundary reviews')
    production = root / 'integrations/envhd/production'
    prompt_name = str(prompt.resolve().relative_to(production.resolve()))
    if not prompt.is_file() or not prompt_name.startswith('prompts/'):
        raise ValueError('Prompt must be saved in the production prompt set')
    ledger_path = production / 'battle-skies.json'
    ledger = read_json(ledger_path)
    tasks_path = production / 'battle-generation-tasks.json'
    tasks = read_json(tasks_path)
    coverage_path = production / 'coverage.json'
    coverage = read_json(coverage_path)
    if not all(isinstance(value, dict) for value in (ledger, tasks, coverage)):
        raise ValueError('Production control files must be JSON objects')
    by_source = {a['sourceMcqSha256']: a for a in ledger['assets']}
    for task in tasks['tasks']:
        by_source[task['masterSourceSha256']]
    source_entry = next(a for a in ledger['assets'] if a['sourceMcqSha256'] == source_hash)
    if source_entry['generationMasterSourceSha256'] != source_hash or source_entry['status'] == 'no-generation-required':
        raise ValueError('Generate only the canonical non-uniform artwork master')
    group = [a for a in ledger['assets'] if a['decodedRgbaSha256'] == source_entry['decodedRgbaSha256']]
    original = None
    for entry in group:
        for name in entry['sourceFiles']:
            path = (files / name).resolve()
            path.relative_to(files.resolve())
            data = read_bytes(path, 1024 * 1024)
            if digest(data) != entry['sourceMcqSha256']:
                raise ValueError('Private source file changed; stop before assigning artwork')
            decoded = decoder.decode(data)
            key = digest(struct.pack('<II', *decoded.size) + decoded.tobytes())
            if key != entry['decodedRgbaSha256']:
                raise ValueError('Shared artwork group differs from the decoded source')
            if entry is source_entry:
                original = decoded
    if original is None or list(original.size) != source_entry['sourceSize']:
        raise ValueError('Missing canonical source or incorrect source dimensions')
    width, height = source_entry['targetSize']
    scale = width // original.width
    if not 1 <= scale <= 8 or width > 4096 or height > 4096 or (width, height) != (original.width * scale, original.height * scale):
        raise ValueError('Unsupported source-bound upscale layout')
    png = read_bytes(candidate)
    if len(png) < 33 or len(png) > 32 * 1024 * 1024 or png[:8] != b'\x89PNG\r\n\x1a\n' or png[24] != 8 or png[25] not in (2, 6):
        raise ValueError('Requires a bounded 8-bit RGB/RGBA PNG')
    with Image.open(io.BytesIO(png)) as image:
        if list(image.size) != source_entry['targetSize']:
            raise ValueError('Candidate differs from the source-bound target dimensions')
        pixels = np.asarray(image.convert('RGB'), dtype=np.float32)
    source_pixels = np.asarray(original.convert('RGB'), dtype=np.float32)
    review = dict(notes, dimensions=source_entry['targetSize'],
                  sourceEdgeMeanDifference=float(np.mean(abs(source_pixels[:, 0] - source_pixels[:, -1]))),
                  outputEdgeMeanDifference=float(np.mean(abs(pixels[:, 0] - pixels[:, -1]))))
    image_file = f'image-v{version}.png'
    candidate_path = production / 'candidates' / source_hash / image_file
    check_version(png, candidate_path)
    common = {'decodedRgbaSha256': source_entry['decodedRgbaSha256'], 'outputSha256': digest(png),
              'scale': source_entry['targetSize'][0] // source_entry['sourceSize'][0], 'generationVersion': version,
              'reviewStatus': verdict, 'method': 'built-in-imagegen', 'prompt': prompt_name,
              'imageFile': image_file, 'review': review,
              'styleReferences': ['scbackgroundhd/cut5/background.png', 'scbackgroundhd/cut37/background.png']}
    if production_record is not None:
        common['productionRecord'] = batch_provenance(production, source_entry, png, version, production_record)
        common['method'] = common['productionRecord']['method']
        common['styleReferenceUse'] = 'visual-review-only'
    runtime = root / 'integrations/envhd/runtime-assets'
    previous = {p.parent.name: read_json(p, 65536) for p in (runtime / 'envhd/skies').glob('*/manifest.json')}
    previous_paths = {resource_path(runtime, key, data): data['outputSha256'] for key, data in previous.items()}
    for key, data in previous.items():
        if digest(read_bytes(resource_path(runtime, key, data))) != data['outputSha256']:
            raise ValueError('Existing runtime resource changed; stop before altering selection')
    selected_path = runtime / 'envhd/sky-images' / source_entry['decodedRgbaSha256'] / image_file
    if verdict in SELECTED:
        check_version(png, selected_path)
    writes = {candidate_path: png, candidate_path.with_name(f'manifest-v{version}.json'): json_bytes(dict(common, sourceMcqSha256=source_hash))}
    removals = set()
    current = dict(previous)
    if verdict in SELECTED:
        writes[selected_path] = png
    for entry in group:
        manifest_path = runtime / 'envhd/skies' / entry['sourceMcqSha256'] / 'manifest.json'
        if verdict in SELECTED:
            current[entry['sourceMcqSha256']] = dict(common, sourceMcqSha256=entry['sourceMcqSha256'])
            writes[manifest_path] = json_bytes(current[entry['sourceMcqSha256']])
            entry['runtimeOutput'] = str(selected_path.relative_to(root))
            entry['runtimeReviewStatus'] = verdict
            entry['status'] = verdict
        else:
            if entry is source_entry:
                entry['status'] = verdict
            if entry['sourceMcqSha256'] in previous:
                old = previous[entry['sourceMcqSha256']]
                if old['outputSha256'] == common['outputSha256']:
                    # Reclassifying this exact selected image withdraws its selection.
                    removals.add(manifest_path)
                    current.pop(entry['sourceMcqSha256'])
                    entry.pop('runtimeOutput', None)
                    entry.pop('runtimeReviewStatus', None)
                    entry['status'] = verdict
        entry['output'] = str(candidate_path.relative_to(root))
        entry['review'] = dict(review, generationMasterSourceSha256=source_hash)
    references = {resource_path(runtime, key, data) for key, data in current.items()}
    for old_path, old_hash in previous_paths.items():
        if old_path not in references and old_path.is_file():
            removals.add(old_path)  # Its public, versioned candidate remains intact.
    writes[ledger_path] = json_bytes(ledger)
    for task in tasks['tasks']:
        task['status'] = by_source[task['masterSourceSha256']]['status']
    writes[tasks_path] = json_bytes(tasks)
    coverage['generatedSources'] = sum(bool(by_source[t['masterSourceSha256']].get('output')) for t in tasks['tasks'])
    coverage['generatedCandidates'] = len(set((production / 'candidates').glob('*/image-v*.png')) | {candidate_path})
    selected_groups, accepted_groups = set(), set()
    for entry in ledger['assets']:
        if entry['sourceMcqSha256'] in current:
            state = current[entry['sourceMcqSha256']]['reviewStatus']
            if state in SELECTED:
                selected_groups.add(entry['decodedRgbaSha256'])
            if state == 'native-accepted':
                accepted_groups.add(entry['decodedRgbaSha256'])
    coverage['integrated'] = len(selected_groups)
    coverage['visualReviewedRuntimeCandidates'] = coverage['integrated']
    coverage['nativeAccepted'] = len(accepted_groups)
    coverage['accepted'] = coverage['nativeAccepted']
    coverage['repeatBoundaryRevisionSources'] = sum(by_source[t['masterSourceSha256']]['status'] == 'repeat-boundary-revision-needed' for t in tasks['tasks'])
    coverage['repeatBoundaryPendingSources'] = sum(by_source[t['masterSourceSha256']]['status'] in {'repeat-boundary-revision-needed', 'layout-reviewed-wrap-pending'} for t in tasks['tasks'])
    coverage['goalComplete'] = False
    writes[coverage_path] = json_bytes(coverage)
    publish(writes, removals)
    return {'source': source_hash, 'version': version, 'verdict': verdict,
            'sharedStageVariants': sum(len(a['stages']) for a in group), 'selected': verdict in SELECTED,
            'output': str(selected_path if verdict in SELECTED else candidate_path)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--source-hash', required=True)
    parser.add_argument('--version', type=int, required=True)
    parser.add_argument('--prompt', type=Path, required=True)
    parser.add_argument('--production-record', type=Path, help='Batch candidates.json; actual method and source/input/output/tool hashes are verified')
    parser.add_argument('--verdict', choices=sorted(VERDICTS), required=True)
    for name in ('intent', 'style', 'layout', 'wrap'):
        parser.add_argument(f'--{name}-review', required=True)
    args = parser.parse_args()
    production_record = None
    if args.production_record is not None:
        matches = [value for value in read_json(args.production_record)['candidates'] if value['masterSourceSha256'] == args.source_hash]
        if len(matches) != 1:
            parser.error('Batch record must have exactly one matching source candidate')
        production_record = matches[0]
    result = record(args.root, args.files, args.candidate, args.source_hash, args.version, args.prompt,
                    args.verdict, {name: getattr(args, name + '_review') for name in ('intent', 'style', 'layout', 'wrap')}, production_record)
    print(json.dumps(result))
