#!/usr/bin/env python3
"""Verify painted static battle repairs against fresh source, recipe and review."""
import argparse
import importlib.util
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


ROOT = Path(__file__).resolve().parents[1]
battle = module('painted_battle_sources', 'batch-envhd-battle-materials.py')
repair = module('painted_battle_recipe', 'repair-envhd-painted-battle-motif.py')
PINS = {
    'algorithmSha256': 'e82423aa061d329dd34c19268510c08f7a4b1a0e000bd581cca0cc871166e055',
    'boundsAlgorithmSha256': 'd94733e201ae2450dabbe0a0aa3915f9c60833672ad3d06b14621c8e70adcd2a',
    'stpAlgorithmSha256': '2e182287e873db6aa46cbede3bf18339c894608dc7a8610abf78fe8c8ee64751',
}
circle = module('painted_battle_circle', 'repair-envhd-painted-battle-circle.py')
CIRCLE_PINS = dict(PINS, algorithmSha256='bc65c6067baf17e996cd908b36a30b7544c8c1160e2a2865878a14d4f6305484')
material = module('painted_battle_material', 'repair-envhd-painted-battle-material.py')
MATERIAL_PINS = dict(PINS, algorithmSha256='10d96f21c187ac579f19985e8463b6eebe56ccb09ca1c00b0893fc9535db9837')
RECIPES = {repair.METHOD: (repair, PINS), circle.METHOD: (circle, CIRCLE_PINS), material.METHOD: (material, MATERIAL_PINS)}
REVIEW_KEYS = {'intent', 'style', 'layout', 'verdict', 'outputSha256', 'nativeAcceptance', 'finalQualityAcceptance'}


def preflight(files, manifest_path, root=ROOT):
    data = battle.terrain.bounded_read(manifest_path, 32 * 1024 * 1024)
    manifest = json.loads(data)
    recipe_pair = RECIPES.get(manifest.get('method'))
    if (manifest.get('schema') != 1 or recipe_pair is None
            or any(manifest.get(k) != 0 for k in ('runtimeSelected', 'nativeAccepted', 'finalQualityAccepted'))):
        raise ValueError('Painted battle acceptance/recipe changed')
    recipe, pins = recipe_pair
    for name, script in [('algorithmSha256', recipe.__file__), ('boundsAlgorithmSha256', recipe.bounds.__file__), ('stpAlgorithmSha256', recipe.terrain.__file__)]:
        if manifest.get(name) != pins[name] or battle.terrain.digest(Path(script).read_bytes()) != pins[name]:
            raise ValueError('Painted battle algorithm identity changed')
    candidates = manifest.get('candidates')
    if (not isinstance(candidates, list) or not candidates or not all(isinstance(c, dict) for c in candidates)
            or manifest.get('visualReviewedBespokeCandidates') != len(candidates)):
        raise ValueError('Explicit complete painted battle reviews required')
    keys = [c.get('decodedRgbaSha256') for c in candidates]
    if any(not isinstance(key, str) for key in keys) or len(keys) != len(set(keys)):
        raise ValueError('Invalid/duplicate painted battle identities')
    scenes, masters, empty = battle.census(files)
    ledger = json.loads(battle.terrain.bounded_read(root/'integrations/envhd/production/battle-material-artwork.json', 32 * 1024 * 1024))
    if ledger['scenes'] != scenes or ledger['emptyModelSlots'] != empty:
        raise ValueError('Fresh battle source differs from reviewed usage')
    history = {a['decodedRgbaSha256']: a for a in ledger['assets']}
    verified = []
    for candidate in candidates:
        key = candidate['decodedRgbaSha256']; item = masters.get(key); previous = history.get(key)
        if (item is None or previous is None or previous['reviewStatus'] != 'intent-revision-needed'
                or candidate.get('sourceOwners') != ['envhd']
                or candidate.get('sourceBindings') != item['bindings']
                or candidate.get('sourceSize') != item['sourceSize']
                or candidate.get('priorCandidateSha256') != previous['outputSha256']):
            raise ValueError('Painted battle source missing, protected, rebound or already selected')
        source = item['image']; repair.bounds.validate_dimensions(source.size, 'source')
        if battle.terrain.fingerprint(source) != key:
            raise ValueError('Painted battle decoded source identity changed')
        note = candidate.get('review')
        if (not isinstance(note, dict) or set(note) != REVIEW_KEYS
                or any(not isinstance(v, str) or not v.strip() for v in note.values())
                or note['verdict'] != 'visual-reviewed-runtime-pending'
                or note['nativeAcceptance'] != 'pending' or note['finalQualityAcceptance'] != 'pending'
                or note['outputSha256'] != candidate.get('outputSha256')
                or candidate.get('method') != recipe.METHOD
                or candidate.get('runtimeSelected') is not False):
            raise ValueError('Painted battle explicit review changed')
        prefix = f'integrations/envhd/production/bespoke-battle-candidates/{key}/'
        if candidate.get('generatedPath') != prefix+'painted-input-v1.png' or candidate.get('outputPath') != prefix+'image-v1.png':
            raise ValueError('Painted battle candidate path changed')
        raw_data = repair.bounds.bounded_read(battle.staging_path(root, candidate['generatedPath']))
        output_data = repair.bounds.bounded_read(battle.staging_path(root, candidate['outputPath']))
        if battle.terrain.digest(raw_data) != candidate.get('generatedSha256') or battle.terrain.digest(output_data) != candidate.get('outputSha256'):
            raise ValueError('Painted battle artwork bytes changed')
        raw_size = repair.bounds.validate_png_header(raw_data, 'painted', source.size, allow_rgb=True)
        output_size = repair.bounds.validate_png_header(output_data, 'output', source.size)
        with Image.open(io.BytesIO(raw_data)) as raw:
            repair.bounds.validate_opened_png(raw, raw_size, 'painted', source.size, 'RGB' if raw_data[25] == 2 else 'RGBA')
            if candidate.get('generatedInputMode') != raw.mode:
                raise ValueError('Painted battle input mode changed')
            if recipe.METHOD == repair.METHOD:
                expected, _, metrics = recipe.repair(source, raw, candidate.get('motifRgba'), candidate.get('lowerBandStart'))
            elif recipe.METHOD == circle.METHOD:
                expected, _, metrics = recipe.repair(source, raw, candidate.get('motifRgba'), candidate.get('sourceMaskCircle'), candidate.get('lightingRadius'))
            else:
                expected, _, metrics = recipe.repair(source, raw)
        with Image.open(io.BytesIO(output_data)) as output:
            repair.bounds.validate_opened_png(output, output_size, 'output', source.size)
            if not np.array_equal(np.asarray(output), np.asarray(expected)):
                raise ValueError('Painted battle output differs from its source-locked recipe')
        if any(candidate.get(k) != v for k, v in metrics.items()) or any(candidate.get(k) != v for k, v in pins.items()):
            raise ValueError('Painted battle operation receipt changed')
        verified.append(dict(candidate=candidate, image=np.asarray(expected).copy()))
    return dict(manifestSha256=battle.terrain.digest(data), verified=verified)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    args = parser.parse_args()
    print('Source-verified painted battle candidates:', len(preflight(args.files, args.manifest)['verified']))
