#!/usr/bin/env python3
"""Verify bespoke improvements to already-selected static neural battle surfaces.

This does not clear animation holds or replace pilot/Skurfa/previous painted art.
"""
import argparse
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image

import importlib.util


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


painted = module('presentation_painted', 'verify-envhd-painted-battle.py')
battle = painted.battle
recipe = painted.material
PINS = painted.MATERIAL_PINS
ROOT = Path(__file__).resolve().parents[1]
PIPELINE = 'envhd-bespoke-battle-presentation-1'


def preflight(files, manifest_path, root=ROOT):
    data = battle.terrain.bounded_read(manifest_path, 32 * 1024 * 1024)
    manifest = json.loads(data)
    if (manifest.get('schema') != 1 or manifest.get('pipeline') != PIPELINE
            or manifest.get('method') != recipe.METHOD
            or any(manifest.get(k) != 0 for k in ('runtimeSelected', 'nativeAccepted', 'finalQualityAccepted'))):
        raise ValueError('Battle presentation contract or acceptance changed')
    for name, script in [('algorithmSha256', recipe.__file__), ('boundsAlgorithmSha256', recipe.bounds.__file__),
                         ('stpAlgorithmSha256', recipe.terrain.__file__), ('outputWriterSha256', recipe.writer.__file__)]:
        if manifest.get(name) != PINS[name] or battle.terrain.digest(Path(script).read_bytes()) != PINS[name]:
            raise ValueError('Battle presentation recipe identity changed')
    production = root / 'integrations/envhd/production'
    history_data = battle.terrain.bounded_read(production / 'battle-material-artwork.json', 32 * 1024 * 1024)
    if manifest.get('sourceArtworkLedgerSha256') != battle.terrain.digest(history_data):
        raise ValueError('Battle presentation source history changed')
    ledger = json.loads(history_data)
    scenes, masters, empty = battle.census(files)
    if ledger['scenes'] != scenes or ledger['emptyModelSlots'] != empty:
        raise ValueError('Fresh battle source differs from reviewed usage')
    history = {a['decodedRgbaSha256']: a for a in ledger['assets']}
    candidates = manifest.get('candidates')
    if (not isinstance(candidates, list) or not candidates
            or manifest.get('visualReviewedBespokeCandidates') != len(candidates)
            or any(not isinstance(c, dict) for c in candidates)):
        raise ValueError('Complete battle presentation reviews required')
    keys = [c.get('decodedRgbaSha256') for c in candidates]
    if any(not isinstance(k, str) for k in keys) or len(keys) != len(set(keys)):
        raise ValueError('Duplicate battle presentation source')
    verified = []
    for candidate in candidates:
        key = candidate['decodedRgbaSha256']
        item, previous = masters.get(key), history.get(key)
        if (item is None or previous is None or previous['reviewStatus'] != 'visual-reviewed-runtime-pending'
                or candidate.get('sourceOwners') != ['envhd'] or candidate.get('sourceBindings') != item['bindings']
                or candidate.get('sourceSize') != item['sourceSize']
                or candidate.get('priorCandidateSha256') != previous['outputSha256']):
            raise ValueError('Battle presentation cannot replace held, painted, pilot or rebound artwork')
        source = item['image']
        if battle.terrain.fingerprint(source) != key:
            raise ValueError('Battle presentation source identity changed')
        prefix = f'integrations/envhd/production/battle-presentation-candidates/{key}/'
        if (candidate.get('generatedPath') != prefix + 'painted-input-v1.png'
                or candidate.get('outputPath') != prefix + 'image-v1.png'):
            raise ValueError('Battle presentation candidate path changed')
        note = candidate.get('review')
        if (not isinstance(note, dict) or set(note) != painted.REVIEW_KEYS
                or any(not isinstance(v, str) or not v.strip() for v in note.values())
                or note['verdict'] != 'visual-reviewed-runtime-pending'
                or note['nativeAcceptance'] != 'pending' or note['finalQualityAcceptance'] != 'pending'
                or note['outputSha256'] != candidate.get('outputSha256')
                or candidate.get('method') != recipe.METHOD or candidate.get('runtimeSelected') is not False
                or not isinstance(candidate.get('prompt'), str) or not candidate['prompt'].strip()):
            raise ValueError('Battle presentation review or acceptance changed')
        raw_data = recipe.bounds.bounded_read(battle.staging_path(root, candidate['generatedPath']))
        output_data = recipe.bounds.bounded_read(battle.staging_path(root, candidate['outputPath']))
        if (battle.terrain.digest(raw_data) != candidate.get('generatedSha256')
                or battle.terrain.digest(output_data) != candidate.get('outputSha256')):
            raise ValueError('Battle presentation artwork bytes changed')
        raw_size = recipe.bounds.validate_png_header(raw_data, 'painted', source.size, allow_rgb=True)
        output_size = recipe.bounds.validate_png_header(output_data, 'output', source.size)
        with Image.open(io.BytesIO(raw_data)) as raw:
            mode = 'RGB' if raw_data[25] == 2 else 'RGBA'
            recipe.bounds.validate_opened_png(raw, raw_size, 'painted', source.size, mode)
            if candidate.get('generatedInputMode') != raw.mode:
                raise ValueError('Battle presentation input mode changed')
            expected, _, metrics = recipe.repair(source, raw)
        with Image.open(io.BytesIO(output_data)) as output:
            recipe.bounds.validate_opened_png(output, output_size, 'output', source.size)
            if not np.array_equal(np.asarray(output), np.asarray(expected)):
                raise ValueError('Battle presentation pixels differ from pinned source recipe')
        if any(candidate.get(k) != v for k, v in (metrics | PINS).items()):
            raise ValueError('Battle presentation operation receipt changed')
        verified.append(dict(candidate=candidate, image=np.asarray(expected).copy()))
    identity = battle.terrain.digest(data)
    if battle.terrain.digest(battle.terrain.bounded_read(production / 'battle-material-artwork.json', 32 * 1024 * 1024)) != manifest['sourceArtworkLedgerSha256']:
        raise ValueError('Battle presentation source history changed during preflight')
    if battle.terrain.digest(battle.terrain.bounded_read(manifest_path, 32 * 1024 * 1024)) != identity:
        raise ValueError('Battle presentation manifest changed during preflight')
    return dict(manifestSha256=identity, sourceArtworkLedgerSha256=manifest['sourceArtworkLedgerSha256'], verified=verified)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    args = parser.parse_args()
    print('Source-verified battle presentation repairs:', len(preflight(args.files, args.manifest)['verified']))
