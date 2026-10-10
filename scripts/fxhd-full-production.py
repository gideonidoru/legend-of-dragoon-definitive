#!/usr/bin/env python3
"""Complete retail FX source census and palette-live neural detail production. AGPLv3."""
import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import shutil
import tempfile
import time

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PINS = {'engine': 'c1c35d92079085de96b9d547fd7e4464bc8a2e9ccf28d7b8c712d72ade91b7cc',
        'weights': '713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf',
        'parameters': '35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86'}
RECIPE = 'fxhd-neural-live-palette-2x-v1'
FIELD = {'SUBMAP/' + n + '.tim' for n in ('dust', 'smoke_1', 'smoke_2', 'left_foot', 'right_foot', 'savepoint', 'savepoint_big_circle', '800d85e0', 'darker_shadow')} | {'shadow.tim'}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(data, indent=2) + '\n')
    temporary.replace(path)

def decode(data):
    if len(data) < 52 or struct.unpack_from('<II', data) != (16, 8):
        raise ValueError('FX census requires a complete 4-bit indexed TIM')
    size, cx, cy, cw, ch = struct.unpack_from('<I4H', data, 8)
    if size < 12 + cw * ch * 2 or cw < 16 or cw * ch % 16:
        raise ValueError('Invalid palette block')
    image_offset = 8 + size
    block, x, y, words, height = struct.unpack_from('<I4H', data, image_offset)
    width = words * 4
    if block < 12 + words * height * 2 or image_offset + block > len(data) or x + words > 1024 or y + height > 512:
        raise ValueError('Invalid image block')
    if not 0 < width <= 1024 or not 0 < height <= 512:
        raise ValueError('FX dimensions exceed bounded runtime')
    packed = np.frombuffer(data[image_offset + 12:image_offset + 12 + words * height * 2], dtype=np.uint8).reshape(height, words * 2)
    indices = np.empty((height, width), dtype=np.uint8)
    indices[:, 0::2] = packed & 15
    indices[:, 1::2] = packed >> 4
    palettes = np.frombuffer(data[20:20 + cw * ch * 2], dtype='<u2').reshape(-1, 16)
    identity = sha(struct.pack('<II', width, height) + indices.tobytes() + palettes.tobytes())
    return indices, palettes, [x, y, words, height], identity

def census(files, inventory):
    records, non_textures = [], Counter()
    required = [files / p for p in FIELD]
    required += [files / f'SECT/DRGN0.BIN/{n}' for n in [4114, 5695, 5718]]
    missing = [str(p.relative_to(files)) for p in required if not p.exists()]
    if missing:
        raise ValueError('Incomplete source extraction: ' + ', '.join(missing))
    with inventory.open() as stream:
        prior_hashes = {r['path']: r['sha256'] for r in csv.DictReader(stream)}
    candidates = {files / p for p in FIELD}
    for directory in (files / 'SECT/DRGN0.BIN').iterdir():
        if not directory.is_dir() or not directory.name.isdigit():
            continue
        n = int(directory.name)
        if n == 4114:
            candidates.update((directory / '3').rglob('*'))
        elif n == 5695:
            candidates.update(directory.rglob('*'))
        elif n == 5718:
            candidates.update(directory / str(i) for i in range(7, 11))
        elif 4115 <= n <= 4138 or 5505 <= n <= 5510 or 4139 <= n <= 5651 and (directory.parent / str(n + 1) / '0').is_dir():
            candidates.update(directory.rglob('*'))
    for path in sorted(p for p in candidates if p.is_file()):
        relative = path.relative_to(files).as_posix()
        parts = relative.split('/')
        category = None
        if relative in FIELD:
            category = 'field'
        elif parts[:2] == ['SECT', 'DRGN0.BIN'] and len(parts) >= 4 and parts[2].isdigit():
            number = int(parts[2])
            if number == 4114:
                category = 'common-battle'
            elif number == 5695:
                category = 'world-atmosphere-smoke'
            elif number == 5718:
                category = 'title-fire'
            elif 4115 <= number <= 4138:
                category = 'dragoon-extra'
            elif 5505 <= number <= 5510:
                category = 'cutscene-extra'
            elif 4139 <= number <= 5651 and (files / f'SECT/DRGN0.BIN/{number + 1}/0').is_dir():
                category = 'dragoon-spell' if number < 4307 else 'item-spell' if number < 4433 else 'enemy-boss' if number < 5505 else 'battle-cutscene'
        if category is None:
            continue
        source = path.read_bytes()
        if source[:4] != struct.pack('<I', 16):
            non_textures['empty-or-directory-metadata' if not source or path.name == 'mrg' else 'non-TIM-support-data'] += 1
            continue
        source_hash = sha(source)
        if relative in prior_hashes and source_hash != prior_hashes[relative]:
            raise ValueError('Source drift: ' + relative)
        indices, palettes, rect, identity = decode(source)
        records.append({'source': relative, 'sourceSha256': source_hash, 'job': identity,
            'category': category, 'imageRect': rect, 'sourceSize': [indices.shape[1], indices.shape[0]],
            'palettes': len(palettes), 'binding': 'TIM upload; live palette and VRAM-copy propagation'})
    if not records:
        raise ValueError('Empty effect census')
    return {'pipeline': RECIPE, 'sources': records, 'sourceCount': len(records),
        'uniqueJobs': len({r['job'] for r in records}), 'categories': dict(Counter(r['category'] for r in records)),
        'nonTextureSupportFiles': dict(non_textures),
        'absentGuardedExtraRoots': [n for n in [*range(4115, 4139), *range(5505, 5511)] if not (files / f'SECT/DRGN0.BIN/{n}').is_dir()],
        'discovery': 'Retail Battle loaders: common4114, extra4115..4138/5505..5510, paired DEFF TIM/definition roots4139..5651; world atmosphere/smoke5695; title flame5718/7..10; named field effects, save-point sparks and shadows',
        'scope': 'All retail texture-backed effect loads; procedural effects use the current renderer; UI and body artwork retain their owners',
        'nativeGameplayAccepted': False, 'steamDeckAccepted': False}

def colours(palette):
    p = palette.astype(np.int32)
    return np.stack([p & 31, p >> 5 & 31, p >> 10 & 31], axis=-1).astype(np.float32) * (255.0 / 31)

def reference(indices, palettes):
    # Use the most informative palette; indices stay live under every other palette.
    scores = [float(colours(p)[indices].var(axis=(0, 1)).sum()) for p in palettes]
    selected = int(np.argmax(scores))
    return selected, colours(palettes[selected])

def weights(indices, palette, target):
    """One original index + local second index, <=50% mixture; native masks stay authoritative."""
    original = np.repeat(np.repeat(indices, 2, axis=0), 2, axis=1)
    a = palette[original]
    best_error = ((a - target) ** 2).sum(axis=-1)
    second = original.copy()
    amount = np.zeros(original.shape, dtype=np.uint8)
    padded = np.pad(indices, 1, mode='edge')
    for dy in range(3):
        for dx in range(3):
            candidate = np.repeat(np.repeat(padded[dy:dy + indices.shape[0], dx:dx + indices.shape[1]], 2, axis=0), 2, axis=1)
            delta = palette[candidate] - a
            norm = (delta * delta).sum(axis=-1)
            fraction = np.clip(((target - a) * delta).sum(axis=-1) / np.maximum(norm, 1), 0, 0.5)
            quantized = np.rint(fraction * 254).astype(np.uint8)
            reconstructed = a + delta * (quantized.astype(np.float32) / 254)[..., None]
            error = ((target - reconstructed) ** 2).sum(axis=-1)
            better = error + 0.0001 < best_error
            second[better] = candidate[better]
            amount[better] = quantized[better]
            best_error[better] = error[better]
    result = np.stack([original, second, amount, np.full(original.shape, 255, dtype=np.uint8)], axis=-1)
    return result

def validate(indices, rgba):
    expected = np.repeat(np.repeat(indices, 2, axis=0), 2, axis=1)
    if rgba.shape != (*expected.shape, 4) or not np.array_equal(rgba[..., 0], expected) or np.any(rgba[..., 1] > 15) or np.any(rgba[..., 2] > 127) or np.any(rgba[..., 3] != 255):
        raise ValueError('Indexed FX detail controls differ')

def produce(args, ledger):
    for name, path in [('engine', args.engine), ('weights', args.models / 'realesrgan-x4plus.bin'), ('parameters', args.models / 'realesrgan-x4plus.param')]:
        if sha(path.read_bytes()) != PINS[name]:
            raise ValueError('Pinned tool drift: ' + name)
    progress_path = args.work / 'progress.json'
    progress = json.loads(progress_path.read_text()) if progress_path.exists() else {'pipeline': RECIPE, 'tools': PINS, 'jobs': {}}
    if progress['pipeline'] != RECIPE or progress['tools'] != PINS:
        raise ValueError('Incompatible production checkpoint')
    unique = {}
    for row in ledger['sources']:
        unique.setdefault(row['job'], row)
    started = time.monotonic()
    for number, (identity, row) in enumerate(unique.items(), 1):
        folder = args.work / 'jobs' / identity
        output = args.output / 'detail' / (identity + '.png')
        prior = progress['jobs'].get(identity)
        data = (args.files / row['source']).read_bytes()
        if sha(data) != row['sourceSha256']:
            raise ValueError('Source drift during production')
        indices, palettes, _, decoded_id = decode(data)
        if decoded_id != identity:
            raise ValueError('Decoded identity differs')
        if prior:
            if sha(output.read_bytes()) != prior['outputSha256']:
                raise ValueError('Production checkpoint altered')
            with Image.open(output) as saved:
                validate(indices, np.asarray(saved))
            continue
        folder.mkdir(parents=True, exist_ok=True)
        output.parent.mkdir(parents=True, exist_ok=True)
        selected, palette = reference(indices, palettes)
        original = np.rint(palette[indices]).astype(np.uint8)
        padded = np.pad(original, ((16, 16), (16, 16), (0, 0)), mode='edge')
        input_path, inferred = folder / 'input.png', folder / 'inference.png'
        Image.fromarray(padded).save(input_path)
        run = subprocess.run([str(args.engine), '-i', str(input_path), '-o', str(inferred), '-m', str(args.models), '-n', 'realesrgan-x4plus', '-s', '4', '-t', '128', '-j', '1:1:1'], capture_output=True, timeout=240)
        (folder / 'inference.log').write_bytes(run.stdout + run.stderr)
        if run.returncode:
            raise RuntimeError('Inference failed: ' + identity)
        with Image.open(inferred) as image:
            if image.size != (padded.shape[1] * 4, padded.shape[0] * 4):
                raise ValueError('Inference dimensions changed')
            target = image.convert('RGB').crop((64, 64, 64 + indices.shape[1] * 4, 64 + indices.shape[0] * 4)).resize((indices.shape[1] * 2, indices.shape[0] * 2), Image.Resampling.BOX)
        target = 0.65 * np.asarray(target, dtype=np.float32) + 0.35 * np.repeat(np.repeat(original, 2, axis=0), 2, axis=1)
        detail = weights(indices, palette, target)
        validate(indices, detail)
        Image.fromarray(detail).save(output)
        # Private color review, never publish original controls or inference inputs.
        enhanced = palette[detail[..., 0]] + (palette[detail[..., 1]] - palette[detail[..., 0]]) * (detail[..., 2:3].astype(np.float32) / 254)
        Image.fromarray(np.rint(enhanced).astype(np.uint8)).save(folder / 'enhanced-review.png')
        progress['jobs'][identity] = {'outputSha256': sha(output.read_bytes()), 'referencePalette': selected,
            'sourceSize': row['sourceSize'], 'changedSubtexels': int(np.count_nonzero(detail[..., 2])),
            'inputSha256': sha(input_path.read_bytes()), 'inferenceSha256': sha(inferred.read_bytes())}
        save(progress_path, progress)
        if number % 25 == 0 or number == len(unique):
            print(json.dumps({'completed': len(progress['jobs']), 'total': len(unique), 'seconds': round(time.monotonic() - started)}), flush=True)
    if len(progress['jobs']) != len(unique):
        raise ValueError('Incomplete full production')
    bindings = []
    for row in ledger['sources']:
        bindings.append(row['sourceSha256'] + '\t' + row['job'] + '\t' + progress['jobs'][row['job']]['outputSha256'] + '\t' + str(row['sourceSize'][0]) + '\t' + str(row['sourceSize'][1]))
    (args.output / 'bindings.tsv').write_text('\n'.join(sorted(set(bindings))) + '\n')
    public = dict(ledger, state='all-census-sources-generated-and-bound', tools=PINS,
        outputs={identity: {k: v for k, v in info.items() if k not in ('inputSha256', 'inferenceSha256')} for identity, info in progress['jobs'].items()})
    save(args.output / 'full-coverage.json', public)

def validate_bundle(output):
    public = json.loads((output / 'full-coverage.json').read_text())
    if public['pipeline'] != RECIPE or public['tools'] != PINS or public['state'] != 'all-census-sources-generated-and-bound':
        raise ValueError('Incompatible or incomplete full FX payload')
    sources, outputs = public['sources'], public['outputs']
    if len(sources) != public['sourceCount'] or len(outputs) != public['uniqueJobs'] or {r['job'] for r in sources} != set(outputs):
        raise ValueError('Incomplete full FX denominator')
    expected = {job + '.png' for job in outputs}
    if {p.name for p in (output / 'detail').iterdir()} != expected:
        raise ValueError('Incomplete or extra generated detail')
    for job, info in outputs.items():
        path = output / 'detail' / (job + '.png')
        if sha(path.read_bytes()) != info['outputSha256']:
            raise ValueError('Altered generated detail: ' + job)
        with Image.open(path) as image:
            width, height = info['sourceSize']
            if image.mode != 'RGBA' or image.size != (width * 2, height * 2):
                raise ValueError('Generated detail dimensions changed')
            rgba = np.asarray(image)
            if np.any(rgba[..., :2] > 15) or np.any(rgba[..., 2] > 127) or np.any(rgba[..., 3] != 255):
                raise ValueError('Generated detail controls changed')
    expected_bindings = set()
    for row in sources:
        info = outputs[row['job']]
        if row['sourceSize'] != info['sourceSize']:
            raise ValueError('Binding dimensions differ')
        expected_bindings.add('\t'.join([row['sourceSha256'], row['job'], info['outputSha256'], *map(str, row['sourceSize'])]))
    if set((output / 'bindings.tsv').read_text().splitlines()) != expected_bindings:
        raise ValueError('Incomplete runtime source bindings')
    return public


def publish(output, root=ROOT):
    """Validate everything before importing custom detail and its public ledger, with rollback."""
    public = validate_bundle(output)
    module = root / 'integrations/fxhd'
    runtime = module / 'runtime-assets/fxhd/full'
    ledger = module / 'production/full-coverage.json'
    runtime.parent.mkdir(parents=True, exist_ok=True)
    ledger.parent.mkdir(parents=True, exist_ok=True)
    previous = ledger.read_bytes() if ledger.exists() else None
    scratch = Path(tempfile.mkdtemp(prefix='.fxhd-import-', dir=module))
    staging, backup = scratch / 'selected', scratch / 'previous'
    preserve_recovery = False
    try:
        shutil.copytree(output / 'detail', staging / 'detail')
        shutil.copy2(output / 'bindings.tsv', staging / 'bindings.tsv')
        moved = False
        published = False
        try:
            if runtime.exists():
                runtime.replace(backup); moved = True
            staging.replace(runtime); published = True
            save(ledger, public)
        except BaseException as failure:
            try:
                if published and runtime.exists(): shutil.rmtree(runtime)
                if moved: backup.replace(runtime)
                if previous is None: ledger.unlink(missing_ok=True)
                else: ledger.write_bytes(previous)
            except BaseException as recovery_failure:
                preserve_recovery = True
                if previous is not None: (scratch / 'previous-coverage.json').write_bytes(previous)
                raise RuntimeError('FX import rollback failed; recovery retained at ' + str(scratch)) from recovery_failure
            raise failure
    finally:
        if not preserve_recovery: shutil.rmtree(scratch)
    print(json.dumps({'selectedSources': public['sourceCount'], 'selectedUniqueOutputs': public['uniqueJobs'], 'uncoveredTextureSources': 0}), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('files', 'inventory', 'work', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--engine', type=Path)
    parser.add_argument('--models', type=Path)
    parser.add_argument('--census-only', action='store_true')
    parser.add_argument('--import-complete', action='store_true')
    args = parser.parse_args()
    for name in ('files', 'inventory', 'work', 'output'):
        setattr(args, name, getattr(args, name).resolve())
    if args.work.is_relative_to(ROOT) or args.work.is_relative_to(args.files) or args.output.is_relative_to(args.files):
        parser.error('Original controls must remain outside checkout/extraction')
    data = census(args.files, args.inventory)
    save(args.work / 'census.json', data)
    print(json.dumps({k: data[k] for k in ('sourceCount', 'uniqueJobs', 'categories')}), flush=True)
    if not args.census_only:
        if not args.engine or not args.models:
            parser.error('Pinned neural engine and models are required')
        produce(args, data)

    if args.import_complete:
        if args.census_only: parser.error('Cannot publish an ungenerated census')
        publish(args.output)
