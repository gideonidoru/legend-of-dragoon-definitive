#!/usr/bin/env python3
"""Plan source-bound panorama batches; --execute requires the approved pixel-edit workflow.

Default mode only validates inputs and writes a private job plan. It never edits
artwork, invokes inference, imports resources, or changes installed selections.
Execution keeps inference inputs/logs private and creates review candidates only.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import struct
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('sky_import', Path(__file__).with_name('import-envhd-sky.py'))
sky = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sky)

WEIGHTS = sky.BATCH_TOOL_HASHES['weightsSha256']
PARAMETERS = sky.BATCH_TOOL_HASHES['parametersSha256']
ENGINE = sky.BATCH_TOOL_HASHES['engineSha256']
REPAIRABLE = {'repeat-boundary-revision-needed', 'layout-reviewed-wrap-pending'}


def fingerprint(image):
    return sky.digest(struct.pack('<II', *image.size) + image.tobytes())


def original(files, entry):
    image = None
    for name in entry['sourceFiles']:
        path = (files / name).resolve()
        path.relative_to(files.resolve())
        data = sky.read_bytes(path, 1024 * 1024)
        if sky.digest(data) != entry['sourceMcqSha256']:
            raise ValueError('Source changed before batch planning')
        decoded = sky.decoder.decode(data)
        if fingerprint(decoded) != entry['decodedRgbaSha256'] or list(decoded.size) != entry['sourceSize']:
            raise ValueError('Decoded source differs from production ownership')
        image = decoded
    if image is None:
        raise ValueError('Missing source artwork')
    return image


def candidate(root, entry):
    path = (root / entry['output']).resolve()
    path.relative_to((root / 'integrations/envhd/production/candidates').resolve())
    version = path.stem.removeprefix('image-v')
    metadata = sky.read_json(path.with_name(f'manifest-v{version}.json'))
    data = sky.read_bytes(path)
    if metadata['sourceMcqSha256'] != entry['sourceMcqSha256'] or metadata.get('decodedRgbaSha256', entry['decodedRgbaSha256']) != entry['decodedRgbaSha256'] or sky.digest(data) != metadata['outputSha256']:
        raise ValueError('Candidate differs from its recorded version')
    if metadata['reviewStatus'] not in REPAIRABLE or metadata['reviewStatus'] != entry['status']:
        raise ValueError('Repair requires a matching reviewed-layout verdict')
    with Image.open(io.BytesIO(data)) as image:
        if image.mode not in ('RGB', 'RGBA') or list(image.size) != entry['targetSize']:
            raise ValueError('Candidate layout differs from its source')
        return image.convert('RGB'), metadata['outputSha256']


def retains_selection(root, group):
    runtime = root / 'integrations/envhd/runtime-assets'
    paths = [runtime / 'envhd/skies' / e['sourceMcqSha256'] / 'manifest.json' for e in group]
    if not any(p.exists() or e.get('runtimeReviewStatus') in sky.SELECTED for p, e in zip(paths, group)):
        return False
    outputs = set()
    for path, entry in zip(paths, group):
        metadata = sky.read_json(path)
        if metadata['reviewStatus'] not in sky.SELECTED or metadata['reviewStatus'] != entry.get('runtimeReviewStatus') or metadata.get('decodedRgbaSha256') != entry['decodedRgbaSha256']:
            raise ValueError('Selected runtime binding differs from production ownership')
        image = sky.resource_path(runtime, entry['sourceMcqSha256'], metadata)
        data = sky.read_bytes(image)
        if str(image.relative_to(root)) != entry.get('runtimeOutput') or sky.digest(data) != metadata['outputSha256']:
            raise ValueError('Selected runtime artwork changed')
        if metadata.get('scale') != 4 or len(data) < 33 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[24] != 8 or data[25] not in (2, 6):
            raise ValueError('Selected runtime layout differs from source-bound 4x')
        with Image.open(io.BytesIO(data)) as decoded:
            if list(decoded.size) != entry['targetSize'] or decoded.mode not in ('RGB', 'RGBA'):
                raise ValueError('Selected PNG dimensions differ from source-bound target')
        outputs.add((str(image), metadata['outputSha256']))
    if len(outputs) != 1:
        raise ValueError('Shared source variants have different selected artwork')
    return True


def plan(root, files, source_edge_baselines=False):
    production = root / 'integrations/envhd/production'
    assets = sky.read_json(production / 'battle-skies.json')['assets']
    tasks = sky.read_json(production / 'battle-generation-tasks.json')['tasks']
    by_hash = {entry['sourceMcqSha256']: entry for entry in assets}
    if len(by_hash) != len(assets):
        raise ValueError('Duplicate source ownership entries')
    jobs = []
    for task in tasks:
        master = by_hash[task['masterSourceSha256']]
        if master['generationMasterSourceSha256'] != master['sourceMcqSha256']:
            raise ValueError('Task does not own its restoration master')
        group = [e for e in assets if e['decodedRgbaSha256'] == master['decodedRgbaSha256']]
        if any(e['generationMasterSourceSha256'] != master['sourceMcqSha256'] for e in group):
            raise ValueError('Shared source group has conflicting restoration owners')
        master_image = None
        for entry in group:
            decoded = original(files, entry)
            if entry is master:
                master_image = decoded
        if master['status'] == 'no-generation-required':
            if master_image.getextrema() != ((0, 0), (0, 0), (0, 0), (255, 255)):
                raise ValueError('No-generation exemption requires verified uniform opaque black')
            continue
        w, h = master['sourceSize']
        if master['targetSize'] != [w * 4, h * 4] or max(w * 4, h * 4) > 4096:
            raise ValueError('Batch only supports the source-bound 4x layout')
        action = 'retain-selected' if retains_selection(root, group) else 'upscale-original'
        input_hash = master['decodedRgbaSha256']
        if action != 'retain-selected' and master['status'] in REPAIRABLE and not source_edge_baselines:
            _, input_hash = candidate(root, master)
            action = 'repair-reviewed-layout'
        job = {'masterSourceSha256': master['sourceMcqSha256'],
                     'decodedRgbaSha256': master['decodedRgbaSha256'], 'action': action,
                     'inputSha256': input_hash, 'stages': sorted(s for e in group for s in e['stages']),
                     'sourceSize': master['sourceSize'], 'targetSize': master['targetSize']}
        if action == 'repair-reviewed-layout':
            job['inputImageFile'] = Path(master['output']).name
        jobs.append(job)
    if len({j['masterSourceSha256'] for j in jobs}) != len(jobs):
        raise ValueError('Duplicate restoration tasks')
    return jobs, by_hash


def preserve_visibility(source, pixels):
    rgba = np.asarray(source.convert('RGBA'))
    scale = pixels.shape[1] // source.width
    if pixels.shape != (source.height * scale, source.width * scale, 3) or scale != 4:
        raise ValueError('Incorrect source-bound candidate dimensions')
    classification = np.repeat(np.repeat(rgba, scale, axis=0), scale, axis=1)
    protected = (classification[:, :, 3] == 0) | np.all(classification[:, :, :3] == 0, axis=2)
    result = np.dstack((pixels, classification[:, :, 3])).astype(np.uint8)
    result[protected, :3] = 0
    new_black = ~protected & np.all(result[:, :, :3] == 0, axis=2)
    result[new_black, :3] = classification[new_black, :3]
    return result, protected


def join_edges(rgba, protected, border=32):
    """Change a narrow border only; never paint across source coverage classes."""
    if not 2 <= border <= rgba.shape[1] // 8:
        raise ValueError('Repair band must be narrow and contain at least two columns')
    output = rgba.copy()
    for x in range(border):
        t = x / (border - 1)
        weight = 0.5 * (1 - t * t * (3 - 2 * t))
        other = rgba.shape[1] - x - 1
        allowed = ~protected[:, x] & ~protected[:, other]
        left = rgba[allowed, x, :3].astype(np.float64)
        right = rgba[allowed, other, :3].astype(np.float64)
        output[allowed, x, :3] = np.rint(left * (1 - weight) + right * weight)
        output[allowed, other, :3] = np.rint(right * (1 - weight) + left * weight)
    return output


def file_digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def private_output(root, files, output):
    path = output.resolve()
    if path.is_relative_to(root.resolve()) or path.is_relative_to(files.resolve()):
        raise ValueError('Private inference inputs and logs must stay outside the project and original extraction')


def execute(root, files, output, jobs, by_hash, engine, models, source_edge_baselines=False):
    private_output(root, files, output)
    if file_digest(engine) != ENGINE or file_digest(models / 'realesrgan-x4plus.bin') != WEIGHTS or file_digest(models / 'realesrgan-x4plus.param') != PARAMETERS:
        raise ValueError('Inference tool or weights differ from pinned versions')
    work = output / 'private-work'
    work.mkdir(exist_ok=False)
    results = []
    for job in jobs:
        if job['action'] == 'retain-selected':
            continue
        if source_edge_baselines and job['action'] != 'upscale-original':
            raise ValueError('Source-edge baselines must start from the original artwork')
        entry = by_hash[job['masterSourceSha256']]
        source = original(files, entry)
        key = job['masterSourceSha256']
        if job['action'] == 'repair-reviewed-layout':
            image, digest = candidate(root, entry)
            if digest != job['inputSha256']:
                raise ValueError('Candidate changed after batch preflight')
            pixels = np.asarray(image)
            method = 'python-border-repair'
        else:
            # Periodic horizontal context avoids artificial neural input edges.
            padded = np.pad(np.asarray(source.convert('RGB')), ((0, 0), (64, 64), (0, 0)), mode='wrap')
            padded = np.pad(padded, ((16, 16), (0, 0), (0, 0)), mode='edge')
            input_path, generated = work / f'{key}-input.png', work / f'{key}-inference.png'
            Image.fromarray(padded).save(input_path)
            command = [str(engine), '-i', str(input_path), '-o', str(generated), '-m', str(models),
                       '-n', 'realesrgan-x4plus', '-s', '4', '-t', '256', '-j', '1:1:1']
            run = subprocess.run(command, capture_output=True, timeout=180)
            (work / f'{key}.log').write_bytes(run.stdout + run.stderr)
            if run.returncode:
                raise RuntimeError('Inference failed; see private log')
            data = sky.read_bytes(generated)
            with Image.open(io.BytesIO(data)) as image:
                if image.size != ((source.width + 128) * 4, (source.height + 32) * 4):
                    raise ValueError('Inference changed the padded source layout')
                pixels = np.asarray(image.convert('RGB').crop((256, 64, 256 + source.width * 4, 64 + source.height * 4)))
            method = 'real-esrgan-x4plus-periodic-preserved-source-edges' if source_edge_baselines else 'real-esrgan-x4plus-periodic-python-border-repair'
        rgba, protected = preserve_visibility(source, pixels)
        border = 0 if source_edge_baselines else min(32, rgba.shape[1] // 8)
        repaired = rgba if source_edge_baselines else preserve_visibility(source, join_edges(rgba, protected, border)[:, :, :3])[0]
        Image.fromarray(repaired).save(output / f'{key}.png')
        result = dict(job, method=method, outputSha256=file_digest(output / f'{key}.png'),
                      reviewStatus='pending-source-intent-style-layout-wrap-review',
                      repairBorderPixels=border, sourceVisibility='exact-nearest-4x-discard-and-visible-black',
                      engineSha256=ENGINE if job['action'] == 'upscale-original' else None,
                      weightsSha256=WEIGHTS if job['action'] == 'upscale-original' else None,
                      parametersSha256=PARAMETERS if job['action'] == 'upscale-original' else None)
        results.append(result)
        (output / 'candidates.json').write_bytes(sky.json_bytes({'candidates': results, 'installed': False}))
        print(json.dumps({'completedCandidate': key, 'method': method}), flush=True)
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('root', 'files', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--execute', action='store_true', help='Only after the user approves the alternate pixel-edit workflow')
    parser.add_argument('--source-edge-baselines', action='store_true', help='Upscale remaining originals with periodic context, preserving structural edges without reflected border averaging')
    parser.add_argument('--engine', type=Path)
    parser.add_argument('--models', type=Path)
    args = parser.parse_args()
    if args.execute and (args.engine is None or args.models is None):
        parser.error('Execution requires explicit pinned engine and model paths')
    jobs, by_hash = plan(args.root.resolve(), args.files.resolve(), args.source_edge_baselines)
    private_output(args.root, args.files, args.output)
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / 'plan.json').write_bytes(sky.json_bytes({'jobs': jobs, 'executionRequested': args.execute, 'installed': False}))
    print(json.dumps({'jobs': len(jobs), 'actions': {a: sum(j['action'] == a for j in jobs) for a in sorted({j['action'] for j in jobs})}, 'executionRequested': args.execute}))
    if args.execute:
        execute(args.root.resolve(), args.files.resolve(), args.output, jobs, by_hash, args.engine.resolve(), args.models.resolve(), args.source_edge_baselines)
