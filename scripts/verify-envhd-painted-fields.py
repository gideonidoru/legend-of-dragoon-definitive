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
opaque_border = module('painted_field_opaque_border', 'repair-envhd-painted-opaque-border.py')
canvas_fit = module('painted_field_canvas_fit', 'normalize-envhd-painted-canvas.py')
surround = module('painted_field_surround', 'repair-envhd-painted-surround.py')
authoring_crop = module('painted_field_authoring_crop', 'crop-envhd-painted-authoring-canvas.py')
REPAIR_SHA256 = 'd94733e201ae2450dabbe0a0aa3915f9c60833672ad3d06b14621c8e70adcd2a'
REGISTRATION_SHA256 = '7b0458bef46ae0fe5dacd90243951dcb155e58529f23252aacc36f06db949784'
OPAQUE_BORDER_SHA256 = 'd5a6b3b9b9177b2292863ee3391720065e7e9155f943b221ee1edcd2b73365bc'
CANVAS_FIT_SHA256 = '375f8284c4ca6a558e099c018f58c21f419e428d15e3662f6c81d051f87ef739'
SURROUND_SHA256 = '11017bae4bf16c21bdd6e923b36dc46dae63a023ed0c8d0a26dfe70da1e3d767'
AUTHORING_CROP_SHA256 = '939125d63c15725d6c91637eb981ccbf8c667b5b13c52061d5a91345c3ba7676'
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
        if source is None:
            raise ValueError('Painted field decoded source identity changed')
        repair.validate_dimensions(source.size, 'source')
        if batch.terrain.fingerprint(source) != key:
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
        cropping = 'sourceAuthoringCrop' in candidate
        if cropping:
            recorded_crop = candidate['sourceAuthoringCrop']
            if (not isinstance(recorded_crop, dict)
                    or any(k in candidate for k in ('registration', 'opaqueBorderRepair', 'sourceCanvasNormalization', 'sourceSurroundRestore'))
                    or recorded_crop.get('algorithmPath') != 'scripts/crop-envhd-painted-authoring-canvas.py'
                    or recorded_crop.get('algorithmSha256') != AUTHORING_CROP_SHA256
                    or batch.terrain.digest(Path(authoring_crop.__file__).read_bytes()) != AUTHORING_CROP_SHA256):
                raise ValueError('Painted field authoring crop identity or exclusive operation changed')
            authoring_crop.validate_layout(source.size, recorded_crop.get('sourceCanvasSize'), recorded_crop.get('sourceOriginNative'))
        restoring_surround = 'sourceSurroundRestore' in candidate
        if restoring_surround:
            recorded_surround = candidate['sourceSurroundRestore']
            if (not isinstance(recorded_surround, dict)
                    or any(k in candidate for k in ('registration', 'opaqueBorderRepair', 'sourceCanvasNormalization', 'sourceAuthoringCrop'))
                    or recorded_surround.get('algorithmPath') != 'scripts/repair-envhd-painted-surround.py'
                    or recorded_surround.get('algorithmSha256') != SURROUND_SHA256
                    or batch.terrain.digest(Path(surround.__file__).read_bytes()) != SURROUND_SHA256):
                raise ValueError('Painted field surround identity or exclusive operation changed')
        fitting = 'sourceCanvasNormalization' in candidate
        if fitting:
            recorded = candidate['sourceCanvasNormalization']
            if (not isinstance(recorded, dict) or 'registration' in candidate or 'opaqueBorderRepair' in candidate or cropping
                    or recorded.get('algorithmPath') != 'scripts/normalize-envhd-painted-canvas.py'
                    or recorded.get('algorithmSha256') != CANVAS_FIT_SHA256
                    or batch.terrain.digest(Path(canvas_fit.__file__).read_bytes()) != CANVAS_FIT_SHA256):
                raise ValueError('Painted field canvas normalization identity or exclusive operation changed')
        # An explicit, reproducible fit has its own bounded aspect/silhouette
        # checks. Legacy inputs still go through the original aspect gate.
        raw_source_size = None if fitting else recorded_crop['sourceCanvasSize'] if cropping else source.size
        raw_size = repair.validate_png_header(raw_data, 'painted', raw_source_size, allow_rgb=not (fitting or cropping))
        output_size = repair.validate_png_header(output_data, 'output', source.size)
        with Image.open(io.BytesIO(raw_data)) as raw:
            repair.validate_opened_png(raw, raw_size, 'painted', raw_source_size,
                                      'RGB' if raw_data[25] == 2 else 'RGBA')
            if candidate.get('generatedInputMode', 'RGBA') != raw.mode:
                raise ValueError('Painted field input colour mode changed')
            if raw.mode == 'RGB':
                if source.getchannel('A').getextrema() != (255, 255):
                    raise ValueError('Opaque generated RGB cannot replace a source cutout')
                # Opaque generated patches need a constant alpha channel for
                # the same pinned RGBA repair; the RGB artwork is unchanged.
                raw = raw.convert('RGBA')
            if cropping:
                raw, operation = authoring_crop.crop(source, raw, recorded_crop['sourceCanvasSize'], recorded_crop['sourceOriginNative'])
                stream = io.BytesIO()
                raw.save(stream, format='PNG')
                expected_crop = dict(operation, algorithmPath='scripts/crop-envhd-painted-authoring-canvas.py',
                                     algorithmSha256=AUTHORING_CROP_SHA256,
                                     croppedInputSha256=batch.terrain.digest(stream.getvalue()))
                if recorded_crop != expected_crop:
                    raise ValueError('Painted field authoring crop differs from recorded source placement')
            if fitting:
                raw, operation = canvas_fit.normalize(source, raw)
                stream = io.BytesIO()
                raw.save(stream, format='PNG')
                expected_fit = dict(operation, algorithmPath='scripts/normalize-envhd-painted-canvas.py',
                                    algorithmSha256=CANVAS_FIT_SHA256,
                                    normalizedInputSha256=batch.terrain.digest(stream.getvalue()))
                if recorded != expected_fit:
                    raise ValueError('Painted field canvas normalization differs from its recorded source silhouette')
            if restoring_surround:
                raw, operation = surround.restore_surround(source, raw,
                    recorded_surround.get('sourceFeaturePolygons'), recorded_surround.get('featherNativePixels'))
                stream = io.BytesIO()
                raw.save(stream, format='PNG')
                expected_surround = dict(operation, algorithmPath='scripts/repair-envhd-painted-surround.py',
                                         algorithmSha256=SURROUND_SHA256,
                                         restoredInputSha256=batch.terrain.digest(stream.getvalue()))
                if recorded_surround != expected_surround:
                    raise ValueError('Painted field surround differs from its recorded source geometry')
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
            if 'opaqueBorderRepair' in candidate:
                recorded = candidate['opaqueBorderRepair']
                if ('registration' in candidate or not isinstance(recorded, dict)
                        or recorded.get('algorithmPath') != 'scripts/repair-envhd-painted-opaque-border.py'
                        or recorded.get('algorithmSha256') != OPAQUE_BORDER_SHA256
                        or batch.terrain.digest(Path(opaque_border.__file__).read_bytes()) != OPAQUE_BORDER_SHA256):
                    raise ValueError('Painted field opaque border repair identity changed')
                raw, operation = opaque_border.repair_border(source, raw)
                expected_border = dict(operation, algorithmPath='scripts/repair-envhd-painted-opaque-border.py',
                                       algorithmSha256=OPAQUE_BORDER_SHA256)
                if recorded != expected_border:
                    raise ValueError('Painted field opaque border differs from its recorded source colour')
            expected, operation = repair.repair(source, raw)
        with Image.open(io.BytesIO(output_data)) as actual:
            repair.validate_opened_png(actual, output_size, 'output', source.size)
            if actual.mode != 'RGBA' or actual.size != expected.size or not np.array_equal(np.asarray(actual), np.asarray(expected)):
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
