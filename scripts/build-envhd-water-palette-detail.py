#!/usr/bin/env python3
"""Build an unselected 4x water detail field from eight pinned batch inferences.

Original indices remain the primary sample. One bounded local index mixture is
fitted against all eight palettes together. The current shared indexed runtime
supports 2x only; these 4x resources require a separately reviewed adapter.
"""
import argparse
import importlib.util
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA = '217c564be395af93cc2d0ef0d9841b306af64234a1b7f2baf9037ca51d414861'
AUDIT_SHA = 'b9339bbf59cb3518e32766ca4f5f52d69d08ce490ae30e4dc4bdef246a6bdb0b'
METHOD = 'envhd-water-all-eight-palettes-local-index-mixture-4x-v1'
SCALE = 4


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


fx = module('water_neural_tools', 'fxhd-full-production.py')
terrain = module('water_source_tools', 'batch-envhd-terrain.py')
png = module('water_png_bounds', 'repair-envhd-painted-cutout.py')
writer = module('water_candidate_writer', 'write-envhd-painted-candidate.py')
publication = module('water_metadata_writer', 'discover-envhd-battle-families.py')


def validate_indices(indices):
    if (not isinstance(indices, np.ndarray) or indices.dtype != np.uint8
            or indices.ndim != 2 or min(indices.shape) < 1 or max(indices.shape) > 64
            or np.any(indices > 15)):
        raise ValueError('Bounded 4-bit source indices required')


def validate(indices, detail):
    validate_indices(indices)
    expected = np.repeat(np.repeat(indices, SCALE, axis=0), SCALE, axis=1)
    if (not isinstance(detail, np.ndarray) or detail.dtype != np.uint8
            or detail.shape != (*expected.shape, 4) or not np.array_equal(detail[..., 0], expected)
            or np.any(detail[..., 1] > 15) or np.any(detail[..., 2] > 127)
            or np.any(detail[..., 3] != 255)):
        raise ValueError('Water index/mixture controls changed')
    neighbors = np.pad(indices, 1, mode='wrap')
    allowed = np.zeros(expected.shape, dtype=bool)
    for dy in range(3):
        for dx in range(3):
            candidate = np.repeat(np.repeat(neighbors[dy:dy + indices.shape[0], dx:dx + indices.shape[1]], SCALE, axis=0), SCALE, axis=1)
            allowed |= candidate == detail[..., 1]
    if not allowed.all():
        raise ValueError('Secondary index is outside the wrapped local neighborhood')


def fit(indices, palettes, targets):
    validate_indices(indices)
    if (not isinstance(palettes, np.ndarray) or palettes.dtype != np.uint16
            or palettes.shape != (8, 16)):
        raise ValueError('All eight native palette rows required')
    original = np.repeat(np.repeat(indices, SCALE, axis=0), SCALE, axis=1)
    if (not isinstance(targets, np.ndarray) or targets.shape != (8, *original.shape, 3)
            or not np.isfinite(targets).all() or np.any(targets < 0) or np.any(targets > 255)):
        raise ValueError('Eight bounded finite 4x inference targets required')
    colours = np.stack([fx.colours(p) for p in palettes])
    base = colours[:, original]
    best_error = ((base - targets) ** 2).sum(axis=(0, 3))
    second = original.copy()
    amount = np.zeros(original.shape, dtype=np.uint8)
    neighbors = np.pad(indices, 1, mode='wrap')
    for dy in range(3):
        for dx in range(3):
            candidate = np.repeat(np.repeat(neighbors[dy:dy + indices.shape[0], dx:dx + indices.shape[1]], SCALE, axis=0), SCALE, axis=1)
            delta = colours[:, candidate] - base
            norm = (delta * delta).sum(axis=(0, 3))
            fraction = np.clip(((targets - base) * delta).sum(axis=(0, 3)) / np.maximum(norm, 1), 0, 0.5)
            quantized = np.rint(fraction * 254).astype(np.uint8)
            reconstructed = base + delta * (quantized.astype(np.float32) / 254)[None, :, :, None]
            error = ((targets - reconstructed) ** 2).sum(axis=(0, 3))
            better = error + 0.0001 < best_error
            second[better] = candidate[better]
            amount[better] = quantized[better]
            best_error[better] = error[better]
    result = np.stack([original, second, amount, np.full(original.shape, 255, dtype=np.uint8)], axis=-1)
    validate(indices, result)
    return result


def preview(indices, palette, detail):
    """Opaque RGB review only. Runtime retains native STP/discard/pass behavior."""
    validate(indices, detail)
    if not isinstance(palette, np.ndarray) or palette.dtype != np.uint16 or palette.shape != (16,):
        raise ValueError('Native palette row required')
    colours = fx.colours(palette)
    base = colours[detail[..., 0]]
    result = base + (colours[detail[..., 1]] - base) * (detail[..., 2:3].astype(np.float32) / 254)
    result[~np.any(base, axis=-1)] = 0
    return np.rint(result).astype(np.uint8)


def source_snapshot(files):
    audit_path = ROOT/'integrations/envhd/production/world-water-source-audit.json'
    data = png.bounded_read(audit_path)
    if png.digest(data) != AUDIT_SHA:
        raise ValueError('Water source audit identity changed')
    audit = json.loads(data)
    snapshots = {}
    for scene in [audit['uploadAudit'], *audit['uploadAudit']['continents']]:
        directory = scene.get('commonBankDirectory') or scene.get('commonBankSourceDirectory') or scene.get('bankSourceDirectory')
        if directory is None:
            directory = audit['uploadAudit']['commonBankDirectory']
        records = scene.get('commonOrderedSourceFiles', scene.get('orderedSourceFiles'))
        paths = sorted((p for p in (files/directory).iterdir() if p.name.isdigit()), key=lambda p: int(p.name))
        if [int(p.name) for p in paths] != [r['file'] for r in records]:
            raise ValueError('Water source bank file set changed')
        for path, record in zip(paths, records):
            snapshot = png.bounded_read(path)
            if len(snapshot) != record['bytes'] or png.digest(snapshot) != record['sha256']:
                raise ValueError('Water source bank changed')
            snapshots[path] = snapshot
        if 'modelSourceFile' in scene:
            path = files/scene['modelSourceFile']
            snapshot = png.bounded_read(path)
            if png.digest(snapshot) != scene['modelSha256']:
                raise ValueError('Water part-zero source model changed')
            snapshots[path] = snapshot
    source = snapshots[files/'SECT/DRGN0.BIN/5697/22']
    if png.digest(source) != SOURCE_SHA:
        raise ValueError('Shared water TIM identity changed')
    tim = terrain.decode_tim(source)
    indices, palettes, rectangle, identity = fx.decode(source)
    if (indices.shape != (64, 64) or palettes.shape != (8, 16) or rectangle != [448, 0, 16, 64]
            or {k: v for k, v in tim['palette'].items() if k != 'words'} != dict(x=1008, y=485, w=16, h=8)):
        raise ValueError('Shared water upload contract changed')
    return indices, palettes, identity, snapshots


def replay(files, inference_dir, output):
    if output.exists() or output.is_symlink():
        raise ValueError('Use a fresh candidate output directory')
    if output.resolve().is_relative_to(ROOT) or output.resolve().is_relative_to(files.resolve()):
        raise ValueError('Private authoring output must stay separate from checkout and extraction')
    indices, palettes, identity, snapshots = source_snapshot(files)
    origin_path = inference_dir/'candidate-receipt-v1.json'
    origin_data = png.bounded_read(origin_path)
    origin = json.loads(origin_data)
    if (origin.get('method') != METHOD or origin.get('sourceTimSha256') != SOURCE_SHA
            or origin.get('decodedIndexedPaletteSha256') != identity or origin.get('tools') != fx.PINS
            or len(origin.get('inferences', [])) != 8):
        raise ValueError('Captured water batch inference identity changed')
    colours = np.stack([fx.colours(p) for p in palettes])
    original = np.repeat(np.repeat(indices, SCALE, axis=0), SCALE, axis=1)
    targets = []
    captured_inferences = {}
    for row, record in enumerate(origin['inferences']):
        if record['row'] != row:
            raise ValueError('Inference palette rows rebound')
        source_rgb = np.rint(colours[row][indices]).astype(np.uint8)
        expected_input = Image.fromarray(np.pad(source_rgb, ((16, 16), (16, 16), (0, 0)), mode='wrap'))
        stream = io.BytesIO()
        expected_input.save(stream, format='PNG')
        if png.digest(stream.getvalue()) != record['inputSha256']:
            raise ValueError('Inference input differs from source/palette/wrap contract')
        path = inference_dir/f'private-inference-row-{row}.png'
        data = png.bounded_read(path)
        captured_inferences[path] = data
        if png.digest(data) != record['inferenceSha256']:
            raise ValueError('Captured neural inference changed')
        size = png.validate_png_header(data, 'painted', allow_rgb=True)
        if size != (384, 384):
            raise ValueError('Inference canvas changed')
        with Image.open(io.BytesIO(data)) as image:
            png.validate_opened_png(image, size, 'painted', expected_mode='RGB')
            target = np.asarray(image.crop((64, 64, 320, 320)), dtype=np.float32)
        targets.append(0.65 * target + 0.35 * colours[row][original])
    detail = fit(indices, palettes, np.stack(targets))
    # Publication starts only after every captured input and source is verified.
    if any(png.bounded_read(path) != data for path, data in snapshots.items()):
        raise ValueError('Source changed during candidate replay')
    encoded = writer.write_candidate(Image.fromarray(detail), output/'indexed-detail-4x-v1.png')
    previews = []
    for row, palette in enumerate(palettes):
        rgb = preview(indices, palette, detail)
        rgba = np.concatenate([rgb, np.full((*rgb.shape[:2], 1), 255, dtype=np.uint8)], axis=-1)
        name = f'palette-row-{row}-custom-preview-v1.png'
        data = writer.write_candidate(Image.fromarray(rgba), output/name)
        previews.append(dict(row=row, clutY=485 + row, name=name, outputSha256=png.digest(data)))
    receipt = dict(schema=1, method=METHOD, sourceTimSha256=SOURCE_SHA, decodedIndexedPaletteSha256=identity,
                   sourceAuditSha256=AUDIT_SHA, producerSha256=png.digest(Path(__file__).read_bytes()),
                   inferenceProducerSha256=origin['producerSha256'], tools=fx.PINS, inferences=origin['inferences'],
                   sourceSize=[64, 64], targetSize=[256, 256], scale=4, maximumSecondaryMix=0.5,
                   secondaryIndexDomain='wrapped 3x3 native texel neighborhood', primaryIndicesExact=True,
                   originalIndicesRecoverableFromPrimaryChannel=True, originalInputAndPaletteFilesAdded=False,
                   paletteFit='One coefficient field jointly fitted against all eight palette targets',
                   inferenceContext='16 native pixels of toroidal padding per edge',
                   changedSubtexels=int(np.count_nonzero(detail[..., 2])), totalSubtexels=65536,
                   encodedOutputSha256=png.digest(encoded), previews=previews,
                   previewInterpretation='Opaque RGB custom diagnostics; not runtime STP/alpha interpretation',
                   dependencySha256={Path(m.__file__).name:png.digest(Path(m.__file__).read_bytes()) for m in (fx, terrain, png, writer, publication)},
                   runtimeSelected=False, currentIndexedRuntimeSupportsThis4xResource=False,
                   nativeAcceptance='pending', finalQualityAcceptance='pending', holdsCleared=0,
                   remaining='Independent style, periodic borders, live state/consumer ownership and reviewed 4x renderer support')
    def verify_inputs():
        if (png.bounded_read(origin_path) != origin_data
                or any(png.bounded_read(path) != data for path, data in snapshots.items())
                or any(png.bounded_read(path) != data for path, data in captured_inferences.items())):
            raise ValueError('Source or inference changed before candidate receipt publication')
    publication.write_new(output/'candidate-receipt-v1.json', receipt, before_publish=verify_inputs)
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('files', 'inference-dir', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    result = replay(args.files, args.inference_dir, args.output)
    print(json.dumps(dict(encodedOutputSha256=result['encodedOutputSha256'], changedSubtexels=result['changedSubtexels'], runtimeSelected=False)))
