#!/usr/bin/env python3
"""Preflight source-bound painted field candidates before optional selection."""
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


batch = module('painted_field_sources', 'batch-envhd-fields.py')
repair = module('painted_field_repair', 'repair-envhd-painted-cutout.py')
registration = module('painted_field_registration', 'register-envhd-painted-cutout.py')
REPAIR_SHA256 = 'f99e27c27f3a5eeac6d18d38fc0ea998ed82f91a467c794feb869414dc01c73a'
REGISTRATION_SHA256 = '7b0458bef46ae0fe5dacd90243951dcb155e58529f23252aacc36f06db949784'
METHOD = 'python-painted-cutout-nearest-colour-exact-visibility-1'
REVIEW_KEYS = {'intent', 'style', 'layout', 'verdict', 'outputSha256', 'nativeAcceptance', 'finalQualityAcceptance'}
ROOT = Path(__file__).resolve().parents[1]


def preflight(files, manifest_path, root=ROOT):
    manifest_data = batch.terrain.bounded_read(manifest_path, 32 * 1024 * 1024)
    manifest = json.loads(manifest_data)
    if (manifest.get('schema') != 1 or manifest.get('algorithmPath') != 'scripts/repair-envhd-painted-cutout.py'
            or manifest.get('algorithmSha256') != REPAIR_SHA256
            or batch.terrain.digest(Path(repair.__file__).read_bytes()) != REPAIR_SHA256
            or any(manifest.get(f) != 0 for f in ('runtimeSelected', 'nativeAccepted', 'finalQualityAccepted'))):
        raise ValueError('Painted field repair identity or candidate acceptance changed')
    candidates = manifest.get('candidates')
    if not isinstance(candidates, list) or not candidates or not all(isinstance(c, dict) for c in candidates):
        raise ValueError('Explicit painted field candidate list required')
    keys = [c.get('decodedRgbaSha256') for c in candidates]
    if len(set(keys)) != len(keys) or manifest.get('visualReviewedBespokeCandidates') != len(keys):
        raise ValueError('Duplicate or incomplete painted field reviews')
    report = batch.fields.census(files)
    assets = {a['decodedRgbaSha256']: a for a in report['assets']}
    scenes = {s['renderKey']: s for s in report['renders']}
    rendered = {}
    verified = []
    for candidate in candidates:
        key = candidate['decodedRgbaSha256']
        asset = assets.get(key)
        if (asset is None or asset['owners'] != ['envhd'] or candidate.get('sourceOwners') != ['envhd']
                or candidate.get('sourceBindings') != asset['bindings']
                or candidate.get('sourceSize') != asset['sourceSize']):
            raise ValueError('Painted field source is missing, protected or rebound')
        bindings = asset['bindings']
        if not bindings or any(b['kind'] != 'foreground' for b in bindings):
            raise ValueError('This painted cutout pipeline supports independent foregrounds only')
        binding = bindings[0]
        scene = scenes[binding['renderKey']]
        route = scene['representative']
        if scene['owner'] != 'envhd':
            raise ValueError('Protected scene cannot supply painted field pixels')
        if binding['renderKey'] not in rendered:
            data = batch.fields.entries(files / 'SECT' / f"DRGN{route['bank']}.BIN" / str(route['directory']))
            identities = [dict(index=i, sha256=batch.terrain.digest(d), size=len(d)) for i, d in data.items()]
            if identities != scene['sourceFiles']:
                raise ValueError('Field source changed after census')
            _, layers, metadata = batch.fields.render(data, route['cut'])
            if metadata['canvasRect'] != scene['canvasRect'] or metadata['foregrounds'] != scene['foregrounds']:
                raise ValueError('Field layout changed after census')
            rendered[binding['renderKey']] = layers
        source = rendered[binding['renderKey']][binding['slot']]
        if source is None or batch.terrain.fingerprint(source) != key:
            raise ValueError('Painted field decoded source identity changed')
        if (candidate.get('representativeCut') != route['cut']
                or candidate.get('representativeLocation') != route['location']):
            raise ValueError('Painted field usage context changed')
        prefix = f'integrations/envhd/production/bespoke-field-candidates/{key}/'
        if candidate.get('generatedPath') != prefix + 'painted-input-v1.png' or candidate.get('outputPath') != prefix + 'image-v1.png':
            raise ValueError('Painted field candidate path changed')
        raw_path = batch.battle.staging_path(root, candidate['generatedPath'])
        output_path = batch.battle.staging_path(root, candidate['outputPath'])
        raw_data = batch.terrain.bounded_read(raw_path, 32 * 1024 * 1024)
        output_data = batch.terrain.bounded_read(output_path, 32 * 1024 * 1024)
        if (batch.terrain.digest(raw_data) != candidate.get('generatedSha256')
                or batch.terrain.digest(output_data) != candidate.get('outputSha256')):
            raise ValueError('Painted field artwork bytes changed')
        for data, types in ((raw_data, (2, 6)), (output_data, (6,))):
            if len(data) < 33 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[24] != 8 or data[25] not in types:
                raise ValueError('Painted field artwork must be bounded 8-bit PNG with RGBA output')
        with Image.open(io.BytesIO(raw_data)) as raw:
            if candidate.get('generatedInputMode', 'RGBA') != raw.mode:
                raise ValueError('Painted field input colour mode changed')
            if raw.mode == 'RGB':
                if source.getchannel('A').getextrema() != (255, 255):
                    raise ValueError('Opaque generated RGB cannot replace a source cutout')
                # Opaque generated patches need a constant alpha channel for
                # the same pinned RGBA repair; the RGB artwork is unchanged.
                raw = raw.convert('RGBA')
            if 'registration' in candidate:
                recorded = candidate['registration']
                if (not isinstance(recorded, dict) or recorded.get('algorithmPath') != 'scripts/register-envhd-painted-cutout.py'
                        or recorded.get('algorithmSha256') != REGISTRATION_SHA256
                        or batch.terrain.digest(Path(registration.__file__).read_bytes()) != REGISTRATION_SHA256):
                    raise ValueError('Painted field registration identity changed')
                raw, operation = registration.register(source, raw, recorded.get('sourceQuad'), recorded.get('paintedQuad'))
                stream = io.BytesIO()
                raw.save(stream, format='PNG')
                expected_registration = dict(operation, algorithmPath='scripts/register-envhd-painted-cutout.py',
                                             registeredInputSha256=batch.terrain.digest(stream.getvalue()))
                if recorded != expected_registration:
                    raise ValueError('Painted field registration differs from its recorded source geometry')
            expected, operation = repair.repair(source, raw)
        with Image.open(io.BytesIO(output_data)) as actual:
            if actual.mode != 'RGBA' or not np.array_equal(np.asarray(actual), np.asarray(expected)):
                raise ValueError('Painted field differs from its exact recorded repair')
        if any(candidate.get(k) != v for k, v in operation.items()):
            raise ValueError('Painted field repair measurements changed')
        if (candidate.get('targetSize') != list(expected.size) or candidate.get('algorithmSha256') != REPAIR_SHA256
                or candidate.get('method') != METHOD or candidate.get('generatedBy') != 'builtin-imagegen'
                or not isinstance(candidate.get('prompt'), str) or not candidate['prompt'].strip()
                or candidate.get('nativeAcceptance') != 'pending' or candidate.get('finalQualityAcceptance') != 'pending'
                or candidate.get('runtimeSelected') is not False):
            raise ValueError('Painted field provenance or acceptance changed')
        note = candidate.get('review')
        if (not isinstance(note, dict) or set(note) != REVIEW_KEYS
                or any(not isinstance(v, str) or not v.strip() for v in note.values())
                or note['verdict'] != 'visual-reviewed-bespoke-candidate-runtime-pending'
                or note['outputSha256'] != candidate['outputSha256']
                or note['nativeAcceptance'] != 'pending' or note['finalQualityAcceptance'] != 'pending'):
            raise ValueError('Explicit painted field intent, style and layout review required')
        verified.append(dict(decodedRgbaSha256=key, outputSha256=candidate['outputSha256'],
                             sourceSize=asset['sourceSize'], bindings=bindings, sourcePath=output_path))
    if batch.terrain.bounded_read(manifest_path, 32 * 1024 * 1024) != manifest_data:
        raise ValueError('Painted field manifest changed during preflight')
    return verified, batch.terrain.digest(manifest_data)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    args = parser.parse_args()
    candidates, receipt = preflight(args.files, args.manifest)
    print(json.dumps(dict(verifiedPaintedCandidates=len(candidates), manifestSha256=receipt,
                          runtimeSelected=0, nativeAccepted=0, finalQualityAccepted=0)))
