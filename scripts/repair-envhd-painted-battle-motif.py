#!/usr/bin/env python3
"""Paint a material beneath an exact source-palette motif; preserve native STP.

Inputs remain private. Outputs are custom candidates, never runtime selection.
The motif colour and lower band boundary must be verified against actual usage.
"""
import argparse
import hashlib
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


bounds = module('battle_painted_bounds', 'repair-envhd-painted-cutout.py')
terrain = module('battle_painted_stp', 'batch-envhd-terrain.py')
METHOD = 'python-painted-material-exact-source-palette-motif-1'


def repair(source, painted, motif_rgba, band_start):
    bounds.validate_dimensions(source.size, 'source')
    bounds.validate_dimensions(painted.size, 'painted', source.size)
    if source.mode != 'RGBA' or painted.mode not in ('RGB', 'RGBA'):
        raise ValueError('RGBA source and RGB/RGBA painted material required')
    native = np.asarray(source)
    if (not isinstance(motif_rgba, (list, tuple)) or len(motif_rgba) != 4
            or any(type(v) is not int or not 0 <= v <= 255 for v in motif_rgba)
            or type(band_start) is not int or not 0 < band_start < source.height):
        raise ValueError('Explicit palette motif and lower band boundary required')
    mask = np.all(native == motif_rgba, axis=2)
    if not mask[:band_start].any() or not mask[band_start:].all():
        raise ValueError('Source does not match the reviewed motif/band classification')
    if painted.mode == 'RGBA' and painted.getchannel('A').getextrema() != (255, 255):
        raise ValueError('Painted material must cover the entire source canvas')
    size = (source.width * 4, source.height * 4)
    grain = np.asarray(painted.convert('RGB').resize(size, Image.Resampling.LANCZOS), dtype=np.float32)
    # Material-only variation adds no independent marks. Keep background and
    # incised motif tones anchored to their actual source palette classes.
    grain -= grain.mean(axis=(0, 1))
    grain = np.clip(grain, -30, 30)
    background = np.median(native[~mask, :3], axis=0).astype(np.float32)
    rgb = np.clip(background + grain * 0.75, 1, 255)
    target_mask = np.repeat(np.repeat(mask, 4, axis=0), 4, axis=1)
    motif = target_mask.copy()
    motif[band_start * 4:] = False
    carved = np.clip(np.asarray(motif_rgba[:3], dtype=np.float32) + grain * 0.22, 1, 255)
    # One target-pixel bevel stays INSIDE the exact palette-class silhouette.
    # It does not create strokes, close holes or alter disconnected endpoints.
    padded = np.pad(motif, 1, mode='constant')
    upper_left = motif & (~padded[:-2, 1:-1] | ~padded[1:-1, :-2])
    lower_right = motif & (~padded[2:, 1:-1] | ~padded[1:-1, 2:])
    carved[upper_left] = np.clip(carved[upper_left] + 9, 1, 255)
    carved[lower_right] = np.clip(carved[lower_right] - 12, 1, 255)
    rgb[target_mask] = carved[target_mask]
    # Unlike field alpha, STP=0 coloured texels are visible in these materials.
    # Restore exact discard/STP/visible-black classes through the terrain helper.
    output, preview = terrain.preserve_stp(source, rgb.astype(np.uint8))
    expected = np.repeat(np.repeat(native, 4, axis=0), 4, axis=1)
    actual = np.asarray(output)
    if not np.array_equal(actual[:, :, 3], expected[:, :, 3]):
        raise ValueError('Native STP classes changed')
    # For this reviewed pale stone/grey motif, no painted background texel may
    # cross the midpoint between the two palette classes. This proves the final
    # output retains the exact original motif mask, not just the construction.
    split = (float(background.mean()) + float(np.mean(motif_rgba[:3]))) / 2
    coloured = np.any(expected[:, :, :3] != 0, axis=2)
    if not np.array_equal((actual[:, :, :3].mean(axis=2) < split) & coloured, target_mask):
        raise ValueError('Material contrast obscures the exact source motif')
    metrics = dict(scale=4, sourceSize=list(source.size), targetSize=list(size),
                   motifRgba=motif_rgba, lowerBandStart=band_start,
                   sourceMotifPixels=int(mask[:band_start].sum()),
                   sourceBandPixels=int(mask[band_start:].sum()),
                   motifSilhouetteExact=True, sourceStpExact=True,
                   discardAndVisibleBlackExact=True, bevelInsideSourceMask=True,
                   edgeAveraging=False, runtimeSelected=False,
                   nativeAcceptance='pending', finalQualityAcceptance='pending')
    return output, preview, metrics


def execute(source_path, painted_path, output_path, source_key, motif_rgba, band_start):
    source_data, raw_data = bounds.bounded_read(source_path), bounds.bounded_read(painted_path)
    source_size = bounds.validate_png_header(source_data, 'source')
    raw_size = bounds.validate_png_header(raw_data, 'painted', source_size, allow_rgb=True)
    with Image.open(io.BytesIO(source_data)) as source:
        bounds.validate_opened_png(source, source_size, 'source')
        if terrain.fingerprint(source) != source_key:
            raise ValueError('Decoded source identity changed')
        with Image.open(io.BytesIO(raw_data)) as raw:
            bounds.validate_opened_png(raw, raw_size, 'painted', source_size,
                                       'RGB' if raw_data[25] == 2 else 'RGBA')
            output, preview, record = repair(source, raw, motif_rgba, band_start)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output.save(output_path)
    record.update(decodedRgbaSha256=source_key, method=METHOD,
                  algorithmSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  boundsAlgorithmSha256=hashlib.sha256(Path(bounds.__file__).read_bytes()).hexdigest(),
                  stpAlgorithmSha256=hashlib.sha256(Path(terrain.__file__).read_bytes()).hexdigest(),
                  generatedSha256=hashlib.sha256(raw_data).hexdigest(),
                  outputSha256=hashlib.sha256(output_path.read_bytes()).hexdigest())
    return record, preview


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--painted', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--source-key', required=True)
    parser.add_argument('--motif-rgba', required=True, type=int, nargs=4)
    parser.add_argument('--band-start', required=True, type=int)
    args = parser.parse_args()
    receipt, _ = execute(args.source, args.painted, args.output, args.source_key, args.motif_rgba, args.band_start)
    print(json.dumps(receipt, indent=2))
